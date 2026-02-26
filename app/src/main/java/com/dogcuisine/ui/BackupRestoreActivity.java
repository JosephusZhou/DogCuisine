package com.dogcuisine.ui;

import android.content.ContentValues;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.dogcuisine.App;
import com.dogcuisine.R;
import com.dogcuisine.data.AppDatabase;
import com.google.android.material.appbar.MaterialToolbar;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class BackupRestoreActivity extends AppCompatActivity {

    private Button btnBackup;
    private Button btnRestore;
    private TextView tvBackupStatus;
    private TextView tvRestoreStatus;
    private ExecutorService ioExecutor;
    private LoadingDialogHelper loadingDialogHelper;
    private boolean isWorking = false;
    private ActivityResultLauncher<String[]> restorePicker;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_backup_restore);

        MaterialToolbar toolbar = findViewById(R.id.toolbarBackup);
        toolbar.setTitle("备份与恢复");
        toolbar.setTitleTextColor(ContextCompat.getColor(this, R.color.teal_200));
        toolbar.setNavigationIcon(R.drawable.ic_back_gold);
        toolbar.setNavigationOnClickListener(v -> {
            if (!isWorking) {
                finish();
            }
        });

        SystemBarHelper.applyEdgeToEdge(this,
                new View[]{toolbar},
                new View[]{findViewById(android.R.id.content)});

        App app = App.getInstance();
        ioExecutor = app.ioExecutor();
        loadingDialogHelper = new LoadingDialogHelper(this);

        btnBackup = findViewById(R.id.btnBackup);
        btnRestore = findViewById(R.id.btnRestore);
        tvBackupStatus = findViewById(R.id.tvBackupStatus);
        tvRestoreStatus = findViewById(R.id.tvRestoreStatus);

        restorePicker = registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
            if (uri != null) {
                startRestore(uri);
            }
        });

        btnBackup.setOnClickListener(v -> startBackup());
        btnRestore.setOnClickListener(v -> pickRestoreFile());
    }

    private void pickRestoreFile() {
        if (isWorking) return;
        restorePicker.launch(new String[]{"application/zip", "application/octet-stream"});
    }

    private void startBackup() {
        if (isWorking) return;
        setLoading(true);
        ioExecutor.execute(() -> {
            try {
                String backupPath = createBackupZip();
                runOnUiThread(() -> {
                    setLoading(false);
                    tvBackupStatus.setText("备份完成，备份路径为：" + backupPath);
                });
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    setLoading(false);
                    Toast.makeText(this, "备份失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void startRestore(Uri uri) {
        if (isWorking) return;
        setLoading(true);
        ioExecutor.execute(() -> {
            try {
                File tempZip = copyUriToCache(uri);
                restoreFromZip(tempZip);
                runOnUiThread(() -> {
                    setLoading(false);
                    tvRestoreStatus.setText("恢复完成");
                    setResult(RESULT_OK);
                });
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    setLoading(false);
                    Toast.makeText(this, "恢复失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private String createBackupZip() throws Exception {
        String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA).format(new Date());
        String fileName = "dogcuisine_backup_" + ts + ".zip";

        File dbFile = getDatabasePath("dogcuisine.db");
        File imagesDir = new File(getFilesDir(), "images");

        ContentValues values = new ContentValues();
        values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
        values.put(MediaStore.Downloads.MIME_TYPE, "application/zip");
        values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
        values.put(MediaStore.Downloads.IS_PENDING, 1);

        Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        if (uri == null) {
            throw new IllegalStateException("无法创建下载目录文件");
        }

        OutputStream out = getContentResolver().openOutputStream(uri);
        if (out == null) {
            throw new IllegalStateException("无法写入下载目录文件");
        }
        try (OutputStream outStream = out;
             ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(outStream))) {
            if (dbFile.exists()) {
                zipFile(zos, dbFile, "db/dogcuisine.db");
            }
            if (imagesDir.exists()) {
                zipDir(zos, imagesDir, "images");
            }
        }

        ContentValues done = new ContentValues();
        done.put(MediaStore.Downloads.IS_PENDING, 0);
        getContentResolver().update(uri, done, null, null);

        File publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        return new File(publicDownloads, fileName).getAbsolutePath();
    }

    private void restoreFromZip(File zipFile) throws Exception {
        // 关闭数据库
        AppDatabase current = App.getInstance().getDatabase();
        current.close();

        File tempDir = new File(getCacheDir(), "backup_restore");
        if (tempDir.exists()) deleteRecursively(tempDir);
        tempDir.mkdirs();

        unzipToDir(zipFile, tempDir);

        File dbSrc = new File(tempDir, "db/dogcuisine.db");
        File dbDest = getDatabasePath("dogcuisine.db");
        if (dbSrc.exists()) {
            copyFile(dbSrc, dbDest);
        }

        File imagesSrc = new File(tempDir, "images");
        File imagesDest = new File(getFilesDir(), "images");
        if (imagesDest.exists()) deleteRecursively(imagesDest);
        if (imagesSrc.exists()) {
            copyDir(imagesSrc, imagesDest);
        }

        // 重新初始化数据库实例
        AppDatabase.resetInstance(this);
    }

    private void zipFile(ZipOutputStream zos, File file, String entryName) throws Exception {
        ZipEntry entry = new ZipEntry(entryName);
        zos.putNextEntry(entry);
        try (InputStream in = new BufferedInputStream(new FileInputStream(file))) {
            byte[] buffer = new byte[4096];
            int len;
            while ((len = in.read(buffer)) != -1) {
                zos.write(buffer, 0, len);
            }
        }
        zos.closeEntry();
    }

    private void zipDir(ZipOutputStream zos, File dir, String baseName) throws Exception {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            String entryName = baseName + "/" + f.getName();
            if (f.isDirectory()) {
                zipDir(zos, f, entryName);
            } else {
                zipFile(zos, f, entryName);
            }
        }
    }

    private void unzipToDir(File zipFile, File targetDir) throws Exception {
        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(zipFile)))) {
            ZipEntry entry;
            byte[] buffer = new byte[4096];
            while ((entry = zis.getNextEntry()) != null) {
                File out = new File(targetDir, entry.getName());
                if (entry.isDirectory()) {
                    out.mkdirs();
                } else {
                    File parent = out.getParentFile();
                    if (parent != null && !parent.exists()) parent.mkdirs();
                    try (OutputStream os = new BufferedOutputStream(new FileOutputStream(out))) {
                        int len;
                        while ((len = zis.read(buffer)) != -1) {
                            os.write(buffer, 0, len);
                        }
                    }
                }
                zis.closeEntry();
            }
        }
    }

    private File copyUriToCache(Uri uri) throws Exception {
        File out = new File(getCacheDir(), "restore.zip");
        try (InputStream in = getContentResolver().openInputStream(uri);
             OutputStream os = new FileOutputStream(out)) {
            if (in == null) throw new IllegalStateException("无法读取文件");
            byte[] buffer = new byte[4096];
            int len;
            while ((len = in.read(buffer)) != -1) {
                os.write(buffer, 0, len);
            }
        }
        return out;
    }

    private void copyFile(File src, File dest) throws Exception {
        File parent = dest.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        try (InputStream in = new BufferedInputStream(new FileInputStream(src));
             OutputStream os = new BufferedOutputStream(new FileOutputStream(dest))) {
            byte[] buffer = new byte[4096];
            int len;
            while ((len = in.read(buffer)) != -1) {
                os.write(buffer, 0, len);
            }
        }
    }

    private void copyDir(File srcDir, File destDir) throws Exception {
        if (!destDir.exists()) destDir.mkdirs();
        File[] files = srcDir.listFiles();
        if (files == null) return;
        for (File f : files) {
            File dest = new File(destDir, f.getName());
            if (f.isDirectory()) {
                copyDir(f, dest);
            } else {
                copyFile(f, dest);
            }
        }
    }

    private void deleteRecursively(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (File f : files) {
                    deleteRecursively(f);
                }
            }
        }
        file.delete();
    }

    private void setLoading(boolean loading) {
        isWorking = loading;
        btnBackup.setEnabled(!loading);
        btnRestore.setEnabled(!loading);
        if (loading) {
            loadingDialogHelper.show();
        } else {
            loadingDialogHelper.dismiss();
        }
    }

    @Override
    protected void onDestroy() {
        if (loadingDialogHelper != null) {
            loadingDialogHelper.dismiss();
        }
        super.onDestroy();
    }
}
