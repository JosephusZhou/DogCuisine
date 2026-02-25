package com.dogcuisine.sync;

import android.content.pm.ApplicationInfo;
import android.content.Context;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.dogcuisine.App;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WebDavSyncManager {

    private static final String TAG = "WebDavSync";
    private static final String REMOTE_MANIFEST = "manifest.json";
    private static final String REMOTE_DATA_DIR = "data";
    private static final int CONNECT_TIMEOUT_MS = 60000; // 60s 连接超时
    private static final int READ_TIMEOUT_MS = 120000;   // 120s 读超时
    private static final int WRITE_TIMEOUT_MS = 120000;  // 120s 写超时（HttpURLConnection 无独立写超时，使用读超时兜底）

    private final Context context;
    private final Gson gson = new Gson();
    private final boolean debuggable;

    public WebDavSyncManager(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.debuggable = (this.context.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
    }

    public void upload(@NonNull String baseUrl, @Nullable String username, @Nullable String password) throws Exception {
        d("upload start, baseUrl=" + baseUrl);
        File workspace = new File(context.getCacheDir(), "sync_incremental");
        if (!workspace.exists()) workspace.mkdirs();

        File dbSnapshotDir = new File(workspace, "db_snapshot");
        recreateDir(dbSnapshotDir);
        snapshotDatabaseFiles(dbSnapshotDir);

        List<LocalFileEntry> localFiles = buildLocalFileEntries(dbSnapshotDir);
        SyncManifest remoteManifest = downloadManifestIfExists(baseUrl, username, password);
        if (remoteManifest == null) {
            d("manifest not found, treat as first full upload");
        }
        Map<String, ManifestFile> remoteMap = toManifestMap(remoteManifest);

        // 上传新增/变更文件
        for (LocalFileEntry entry : localFiles) {
            ManifestFile remote = remoteMap.get(entry.path);
            if (remote == null || !entry.sha256.equals(remote.sha256) || entry.size != remote.size) {
                String fileUrl = buildDataFileUrl(baseUrl, entry.path);
                putFile(fileUrl, entry.file, username, password);
            }
        }

        // 删除远端多余文件
        Map<String, LocalFileEntry> localMap = new HashMap<>();
        for (LocalFileEntry file : localFiles) {
            localMap.put(file.path, file);
        }
        for (ManifestFile remoteFile : remoteMap.values()) {
            if (!localMap.containsKey(remoteFile.path)) {
                String fileUrl = buildDataFileUrl(baseUrl, remoteFile.path);
                deleteFileIfExists(fileUrl, username, password);
            }
        }

        // 上传最新 manifest
        SyncManifest latest = new SyncManifest();
        latest.version = 1;
        latest.updatedAt = System.currentTimeMillis();
        latest.files = new ArrayList<>();
        for (LocalFileEntry entry : localFiles) {
            ManifestFile item = new ManifestFile();
            item.path = entry.path;
            item.size = entry.size;
            item.sha256 = entry.sha256;
            latest.files.add(item);
        }
        uploadManifest(baseUrl, latest, username, password);
        d("upload done, files=" + localFiles.size());
    }

    public void downloadAndRestore(@NonNull String baseUrl, @Nullable String username, @Nullable String password) throws Exception {
        d("restore start, baseUrl=" + baseUrl);
        SyncManifest manifest = downloadManifest(baseUrl, username, password);
        if (manifest.files == null) {
            manifest.files = Collections.emptyList();
        }

        File workspace = new File(context.getCacheDir(), "sync_incremental_restore");
        recreateDir(workspace);

        // 全量下载 manifest 中列出的所有文件
        for (ManifestFile item : manifest.files) {
            File outFile = new File(workspace, item.path);
            if (!isChildPath(workspace, outFile)) {
                throw new IOException("非法远端路径: " + item.path);
            }
            File parent = outFile.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            downloadFile(buildDataFileUrl(baseUrl, item.path), outFile, username, password);

            // 完整性校验
            String actual = sha256(outFile);
            if (!actual.equals(item.sha256)) {
                throw new IOException("文件校验失败: " + item.path);
            }
        }

        restoreFromWorkspace(workspace);
        d("restore done, files=" + manifest.files.size());
    }

    private void snapshotDatabaseFiles(@NonNull File dbSnapshotDir) throws Exception {
        App app = App.getInstance();
        app.reloadDatabase();
        app.getDatabase().close();
        try {
            File dbMain = context.getDatabasePath("dogcuisine.db");
            copyFileIfExists(dbMain, new File(dbSnapshotDir, "dogcuisine.db"));
            copyFileIfExists(new File(dbMain.getAbsolutePath() + "-wal"), new File(dbSnapshotDir, "dogcuisine.db-wal"));
            copyFileIfExists(new File(dbMain.getAbsolutePath() + "-shm"), new File(dbSnapshotDir, "dogcuisine.db-shm"));
        } finally {
            app.reloadDatabase();
        }
    }

    @NonNull
    private List<LocalFileEntry> buildLocalFileEntries(@NonNull File dbSnapshotDir) throws Exception {
        List<LocalFileEntry> entries = new ArrayList<>();

        addLocalFile(entries, new File(dbSnapshotDir, "dogcuisine.db"), "db/dogcuisine.db");
        addLocalFile(entries, new File(dbSnapshotDir, "dogcuisine.db-wal"), "db/dogcuisine.db-wal");
        addLocalFile(entries, new File(dbSnapshotDir, "dogcuisine.db-shm"), "db/dogcuisine.db-shm");

        File imagesDir = new File(context.getFilesDir(), "images");
        if (imagesDir.exists() && imagesDir.isDirectory()) {
            collectImageFiles(entries, imagesDir, "images/");
        }

        return entries;
    }

    private void collectImageFiles(@NonNull List<LocalFileEntry> out,
                                   @NonNull File dir,
                                   @NonNull String prefix) throws Exception {
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File child : children) {
            String childPath = prefix + child.getName();
            if (child.isDirectory()) {
                collectImageFiles(out, child, childPath + "/");
            } else if (child.isFile()) {
                addLocalFile(out, child, childPath);
            }
        }
    }

    private void addLocalFile(@NonNull List<LocalFileEntry> out,
                              @NonNull File file,
                              @NonNull String path) throws Exception {
        if (!file.exists() || !file.isFile()) return;
        LocalFileEntry entry = new LocalFileEntry();
        entry.file = file;
        entry.path = path;
        entry.size = file.length();
        entry.sha256 = sha256(file);
        out.add(entry);
    }

    private void restoreFromWorkspace(@NonNull File workspace) throws Exception {
        App app = App.getInstance();
        app.getDatabase().close();

        deleteLocalDbFiles();
        clearImagesDir();

        // 恢复 db
        File dbMain = context.getDatabasePath("dogcuisine.db");
        File dbParent = dbMain.getParentFile();
        if (dbParent != null && !dbParent.exists()) dbParent.mkdirs();

        copyFileIfExists(new File(workspace, "db/dogcuisine.db"), dbMain);
        copyFileIfExists(new File(workspace, "db/dogcuisine.db-wal"), new File(dbMain.getAbsolutePath() + "-wal"));
        copyFileIfExists(new File(workspace, "db/dogcuisine.db-shm"), new File(dbMain.getAbsolutePath() + "-shm"));

        // 恢复 images
        File imagesSrc = new File(workspace, "images");
        File imagesDst = new File(context.getFilesDir(), "images");
        if (!imagesDst.exists()) imagesDst.mkdirs();
        copyDirIfExists(imagesSrc, imagesDst);

        app.reloadDatabase();
    }

    private void deleteLocalDbFiles() {
        File dbMain = context.getDatabasePath("dogcuisine.db");
        deleteIfExists(dbMain);
        deleteIfExists(new File(dbMain.getAbsolutePath() + "-wal"));
        deleteIfExists(new File(dbMain.getAbsolutePath() + "-shm"));
    }

    private void clearImagesDir() {
        File imagesDir = new File(context.getFilesDir(), "images");
        deleteRecursively(imagesDir);
        if (!imagesDir.exists()) imagesDir.mkdirs();
    }

    private void copyDirIfExists(@NonNull File src, @NonNull File dst) throws IOException {
        if (!src.exists() || !src.isDirectory()) return;
        File[] files = src.listFiles();
        if (files == null) return;
        for (File file : files) {
            File target = new File(dst, file.getName());
            if (file.isDirectory()) {
                if (!target.exists()) target.mkdirs();
                copyDirIfExists(file, target);
            } else if (file.isFile()) {
                copyFileIfExists(file, target);
            }
        }
    }

    private void copyFileIfExists(@NonNull File from, @NonNull File to) throws IOException {
        if (!from.exists() || !from.isFile()) return;
        File parent = to.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        try (InputStream in = new FileInputStream(from);
             OutputStream out = new FileOutputStream(to)) {
            copy(in, out);
        }
    }

    @NonNull
    private SyncManifest downloadManifest(@NonNull String baseUrl,
                                          @Nullable String username,
                                          @Nullable String password) throws Exception {
        SyncManifest manifest = downloadManifestIfExists(baseUrl, username, password);
        if (manifest == null) {
            throw new IOException("远端不存在 manifest.json，请先执行一次上传同步");
        }
        return manifest;
    }

    @Nullable
    private SyncManifest downloadManifestIfExists(@NonNull String baseUrl,
                                                  @Nullable String username,
                                                  @Nullable String password) throws Exception {
        String url = buildManifestUrl(baseUrl);
        d("request GET " + url);
        HttpURLConnection conn = openConnection(url, "GET", username, password);
        int code = conn.getResponseCode();
        d("response GET " + url + " code=" + code);
        if (code == 404) {
            conn.disconnect();
            return null;
        }
        if (code != 200) {
            d("manifest GET failed body=" + readErrorBodySafe(conn));
            conn.disconnect();
            throw new IOException("下载 manifest 失败，HTTP " + code);
        }
        try (InputStream in = conn.getInputStream()) {
            byte[] data = readAllBytes(in);
            Type type = new TypeToken<SyncManifest>() {}.getType();
            SyncManifest manifest = gson.fromJson(new String(data, StandardCharsets.UTF_8), type);
            return manifest != null ? manifest : null;
        } finally {
            conn.disconnect();
        }
    }

    private void uploadManifest(@NonNull String baseUrl,
                                @NonNull SyncManifest manifest,
                                @Nullable String username,
                                @Nullable String password) throws Exception {
        File temp = File.createTempFile("manifest", ".json", context.getCacheDir());
        try (OutputStream out = new FileOutputStream(temp)) {
            out.write(gson.toJson(manifest).getBytes(StandardCharsets.UTF_8));
        }
        putFile(buildManifestUrl(baseUrl), temp, username, password);
        temp.delete();
    }

    @NonNull
    private Map<String, ManifestFile> toManifestMap(@Nullable SyncManifest manifest) {
        Map<String, ManifestFile> map = new HashMap<>();
        if (manifest == null || manifest.files == null) return map;
        for (ManifestFile file : manifest.files) {
            if (file != null && file.path != null) {
                map.put(file.path, file);
            }
        }
        return map;
    }

    private void putFile(@NonNull String url,
                         @NonNull File file,
                         @Nullable String username,
                         @Nullable String password) throws Exception {
        d("request PUT " + url + " size=" + file.length());
        HttpURLConnection conn = openConnection(url, "PUT", username, password);
        conn.setDoOutput(true);
        conn.setFixedLengthStreamingMode(file.length());
        try (OutputStream out = conn.getOutputStream();
             InputStream in = new FileInputStream(file)) {
            copy(in, out);
        }
        int code = conn.getResponseCode();
        d("response PUT " + url + " code=" + code);
        if (!(code == 200 || code == 201 || code == 204)) {
            d("PUT failed body=" + readErrorBodySafe(conn));
            conn.disconnect();
            throw new IOException("WebDAV 上传失败，HTTP " + code + " - " + url);
        }
        conn.disconnect();
    }

    private void downloadFile(@NonNull String url,
                              @NonNull File outFile,
                              @Nullable String username,
                              @Nullable String password) throws Exception {
        d("request GET " + url);
        HttpURLConnection conn = openConnection(url, "GET", username, password);
        int code = conn.getResponseCode();
        d("response GET " + url + " code=" + code);
        if (code != 200) {
            d("GET failed body=" + readErrorBodySafe(conn));
            conn.disconnect();
            throw new IOException("WebDAV 下载失败，HTTP " + code + " - " + url);
        }
        try (InputStream in = conn.getInputStream();
             OutputStream out = new FileOutputStream(outFile)) {
            copy(in, out);
        }
        conn.disconnect();
    }

    private void deleteFileIfExists(@NonNull String url,
                                    @Nullable String username,
                                    @Nullable String password) throws Exception {
        d("request DELETE " + url);
        HttpURLConnection conn = openConnection(url, "DELETE", username, password);
        int code = conn.getResponseCode();
        d("response DELETE " + url + " code=" + code);
        conn.disconnect();
        if (!(code == 200 || code == 202 || code == 204 || code == 404)) {
            throw new IOException("WebDAV 删除失败，HTTP " + code + " - " + url);
        }
    }

    @NonNull
    private String buildManifestUrl(@NonNull String baseUrl) {
        return trimTrailingSlash(baseUrl) + "/" + REMOTE_MANIFEST;
    }

    @NonNull
    private String buildDataFileUrl(@NonNull String baseUrl, @NonNull String relativePath) {
        return trimTrailingSlash(baseUrl) + "/" + encodePathSegment(toRemoteFileName(relativePath));
    }

    @NonNull
    private String trimTrailingSlash(@NonNull String baseUrl) {
        String trimmed = baseUrl.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    @NonNull
    private String encodePathSegment(@NonNull String seg) {
        return URLEncoder.encode(seg, StandardCharsets.UTF_8).replace("+", "%20");
    }

    @NonNull
    private String toRemoteFileName(@NonNull String relativePath) {
        String encoded = Base64.encodeToString(
                relativePath.getBytes(StandardCharsets.UTF_8),
                Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
        return REMOTE_DATA_DIR + "_" + encoded + ".bin";
    }

    @NonNull
    private HttpURLConnection openConnection(@NonNull String rawUrl,
                                             @NonNull String method,
                                             @Nullable String username,
                                             @Nullable String password) throws Exception {
        URL url = new URL(rawUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
        // HttpURLConnection 没有单独的写超时，使用较大的读超时覆盖长时间上传/下载
        conn.setReadTimeout(Math.max(READ_TIMEOUT_MS, WRITE_TIMEOUT_MS));
        conn.setRequestMethod(method);
        conn.setUseCaches(false);
        if (username != null && !username.isEmpty()) {
            String pass = password == null ? "" : password;
            String token = username + ":" + pass;
            String basic = "Basic " + Base64.encodeToString(token.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
            conn.setRequestProperty("Authorization", basic);
        }
        return conn;
    }

    @NonNull
    private byte[] readAllBytes(@NonNull InputStream in) throws IOException {
        byte[] buffer = new byte[8192];
        int len;
        try (java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
            while ((len = in.read(buffer)) != -1) {
                out.write(buffer, 0, len);
            }
            return out.toByteArray();
        }
    }

    @NonNull
    private String sha256(@NonNull File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = in.read(buffer)) != -1) {
                digest.update(buffer, 0, len);
            }
        }
        byte[] hash = digest.digest();
        StringBuilder sb = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private void copy(@NonNull InputStream in, @NonNull OutputStream out) throws IOException {
        byte[] buffer = new byte[8192];
        int len;
        while ((len = in.read(buffer)) != -1) {
            out.write(buffer, 0, len);
        }
    }

    private void recreateDir(@NonNull File dir) {
        deleteRecursively(dir);
        if (!dir.exists()) dir.mkdirs();
    }

    private void deleteIfExists(@NonNull File file) {
        if (file.exists()) file.delete();
    }

    private void deleteRecursively(@NonNull File file) {
        if (!file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        file.delete();
    }

    private boolean isChildPath(@NonNull File parent, @NonNull File child) throws IOException {
        String parentPath = parent.getCanonicalPath();
        String childPath = child.getCanonicalPath();
        return childPath.equals(parentPath) || childPath.startsWith(parentPath + File.separator);
    }

    private static class LocalFileEntry {
        String path;
        String sha256;
        long size;
        File file;
    }

    private static class SyncManifest {
        int version;
        long updatedAt;
        List<ManifestFile> files;
    }

    private static class ManifestFile {
        String path;
        String sha256;
        long size;
    }

    private void d(@NonNull String msg) {
        if (debuggable) {
            Log.d(TAG, msg);
        }
    }

    @NonNull
    private String readErrorBodySafe(@NonNull HttpURLConnection conn) {
        try (InputStream err = conn.getErrorStream()) {
            if (err == null) return "";
            byte[] bytes = readAllBytes(err);
            String text = new String(bytes, StandardCharsets.UTF_8);
            if (text.length() > 500) {
                return text.substring(0, 500) + "...";
            }
            return text;
        } catch (Exception ignore) {
            return "";
        }
    }
}
