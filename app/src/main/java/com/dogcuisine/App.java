package com.dogcuisine;

import android.app.Application;
import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.dogcuisine.data.AppDatabase;
import com.dogcuisine.data.CategoryDao;
import com.dogcuisine.data.CategoryEntity;
import com.dogcuisine.data.RecipeEntity;
import com.dogcuisine.data.StepItem;
import com.dogcuisine.data.UserProfileEntity;
import com.dogcuisine.sync.WebDavSyncConfig;
import com.dogcuisine.sync.WebDavSyncManager;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class App extends Application {

    private static App instance;
    private AppDatabase database;
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService autoSyncExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService autoBackupExecutor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean autoSyncRunning = new AtomicBoolean(false);
    private final AtomicBoolean autoSyncRequested = new AtomicBoolean(false);

    private static final String PREF_AUTO_BACKUP = "auto_backup";
    private static final String KEY_LAST_AUTO_BACKUP_TIME = "last_auto_backup_time";
    private static final String AUTO_BACKUP_PREFIX = "dogcuisine_autobackup_";
    private static final long AUTO_BACKUP_INTERVAL_MS = 24 * 60 * 60 * 1000L; // 1 day
    private static final int AUTO_BACKUP_KEEP_COUNT = 3;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        database = AppDatabase.getInstance(this);

        // 确保数据库文件创建，并在首次运行时填充示例数据（仅当表为空）
        ioExecutor.execute(() -> {
            database.getOpenHelper().getWritableDatabase();
            CategoryDao categoryDao = database.categoryDao();
            Long defaultCategoryId = ensureDefaultCategories(categoryDao);
            ensureUserProfile(database);

            long count = database.recipeDao().count();
            if (count == 0) {
                long now = System.currentTimeMillis();
                List<RecipeEntity> seeds = Arrays.asList(
                        new RecipeEntity(
                                null,
                                getString(R.string.sample_recipe_1_name),
                                now,
                                now,
                                getString(R.string.sample_recipe_1_desc),
                                null,
                                "[]",
                                "",
                                defaultCategoryId,
                                0,
                                0
                        ),
                        new RecipeEntity(
                                null,
                                getString(R.string.sample_recipe_2_name),
                                now,
                                now,
                                getString(R.string.sample_recipe_2_desc),
                                null,
                                "[]",
                                "",
                                defaultCategoryId,
                                0,
                                0
                        ),
                        new RecipeEntity(
                                null,
                                getString(R.string.sample_recipe_3_name),
                                now,
                                now,
                                getString(R.string.sample_recipe_3_desc),
                                null,
                                "[]",
                                "",
                                defaultCategoryId,
                                0,
                                0
                        )
                );
                database.recipeDao().insertAll(seeds);
            }
            
            // 执行图片清理操作
            cleanupUnusedImages();

            // 数据库初始化完成后，检查并执行自动本地备份
            triggerAutoBackupIfNeeded();
        });
    }

    @Nullable
    private Long ensureDefaultCategories(@NonNull CategoryDao categoryDao) {
        try {
            long categoryCount = categoryDao.count();
            if (categoryCount == 0) {
                List<CategoryEntity> defaults = Arrays.asList(
                        new CategoryEntity(null, getString(R.string.category_default), 0),
                        new CategoryEntity(null, getString(R.string.category_staple), 1),
                        new CategoryEntity(null, getString(R.string.category_snack), 2)
                );
                List<Long> ids = categoryDao.insertAll(defaults);
                if (ids != null && !ids.isEmpty()) {
                    return ids.get(0);
                }
            } else {
                List<CategoryEntity> categories = categoryDao.getAll();
                if (categories != null && !categories.isEmpty()) {
                    return categories.get(0).getId();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
    
    /**
     * 清理App私有目录中不在数据库中的图片
     */
    private void cleanupUnusedImages() {
        try {
            // 1. 获取数据库中所有图片路径
            Set<String> databaseImagePaths = new HashSet<>();
            List<RecipeEntity> recipes = database.recipeDao().getAll();
            Gson gson = new Gson();
            Type stepListType = new TypeToken<List<StepItem>>() {}.getType();
            
            for (RecipeEntity recipe : recipes) {
                // 添加封面图片路径
                if (recipe.getCoverImagePath() != null && !recipe.getCoverImagePath().isEmpty()) {
                    databaseImagePaths.add(recipe.getCoverImagePath());
                }
                
                // 添加步骤图片路径
                String stepsJson = recipe.getStepsJson();
                if (stepsJson != null && !stepsJson.isEmpty()) {
                    try {
                        List<StepItem> steps = gson.fromJson(stepsJson, stepListType);
                        if (steps != null) {
                            for (StepItem step : steps) {
                                List<String> stepImagePaths = step.getImagePaths();
                                if (stepImagePaths != null) {
                                    databaseImagePaths.addAll(stepImagePaths);
                                }
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                // 添加食材图片路径
                String ingredientJson = recipe.getIngredientJson();
                if (ingredientJson != null && !ingredientJson.isEmpty()) {
                    try {
                        StepItem ingredient = gson.fromJson(ingredientJson, StepItem.class);
                        if (ingredient != null && ingredient.getImagePaths() != null) {
                            databaseImagePaths.addAll(ingredient.getImagePaths());
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
            
            // 2. 扫描App私有目录中的图片
            File imagesDir = new File(getFilesDir(), "images");
            if (!imagesDir.exists() || !imagesDir.isDirectory()) {
                return; // 目录不存在，无需清理
            }
            
            File[] imageFiles = imagesDir.listFiles();
            if (imageFiles == null) {
                return; // 目录为空，无需清理
            }
            
            // 3. 比对并删除不在数据库中的图片
            int deletedCount = 0;
            for (File imageFile : imageFiles) {
                if (imageFile.isFile()) {
                    String imagePath = imageFile.getAbsolutePath();
                    if (!databaseImagePaths.contains(imagePath)) {
                        if (imageFile.delete()) {
                            deletedCount++;
                        }
                    }
                }
            }
            
            if (deletedCount > 0) {
                // 可以选择在这里添加日志，记录删除的图片数量
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void ensureUserProfile(@NonNull AppDatabase database) {
        try {
            if (database.userProfileDao().count() == 0) {
                database.userProfileDao().insert(new UserProfileEntity(0, null));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @NonNull
    public static App getInstance() {
        return instance;
    }

    @NonNull
    public AppDatabase getDatabase() {
        return database;
    }

    public synchronized void reloadDatabase() {
        database = AppDatabase.resetInstance(this);
    }

    public void requestAutoWebDavUploadIfConfigured() {
        WebDavSyncConfig config = WebDavSyncConfig.load(this);
        if (config == null) {
            return;
        }
        autoSyncRequested.set(true);
        autoSyncExecutor.execute(() -> {
            if (!autoSyncRunning.compareAndSet(false, true)) {
                return;
            }
            try {
                while (autoSyncRequested.getAndSet(false)) {
                    WebDavSyncConfig latestConfig = WebDavSyncConfig.load(this);
                    if (latestConfig == null) {
                        break;
                    }
                    try {
                        new WebDavSyncManager(this).upload(latestConfig.url, latestConfig.user, latestConfig.pass);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            } finally {
                autoSyncRunning.set(false);
            }
        });
    }

    public boolean isAutoWebDavUploading() {
        return autoSyncRunning.get();
    }

    public ExecutorService ioExecutor() {
        return ioExecutor;
    }

    // ==================== 自动本地备份 ====================

    /**
     * 检查是否需要自动备份，如果需要则在 autoBackupExecutor 上异步执行
     */
    private void triggerAutoBackupIfNeeded() {
        long lastBackupTime = getSharedPreferences(PREF_AUTO_BACKUP, MODE_PRIVATE)
                .getLong(KEY_LAST_AUTO_BACKUP_TIME, 0);
        long now = System.currentTimeMillis();
        if (now - lastBackupTime < AUTO_BACKUP_INTERVAL_MS) {
            return;
        }
        autoBackupExecutor.execute(() -> {
            try {
                performAutoBackup();
                cleanupOldAutoBackups();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    /**
     * 执行自动备份：通过 WAL checkpoint 确保数据完整，将数据库文件和图片打包为 zip，
     * 写入公共下载目录，成功后记录备份时间
     */
    private void performAutoBackup() throws Exception {
        String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA).format(new Date());
        String fileName = AUTO_BACKUP_PREFIX + ts + ".zip";

        File dbFile = getDatabasePath("dogcuisine.db");
        File imagesDir = new File(getFilesDir(), "images");

        // 将 WAL 内容刷入主数据库文件，不关闭数据库，避免并发访问风险
        // 注意：Android 的 execSQL 不支持 PRAGMA，必须用 query 方式执行
        database.getOpenHelper().getWritableDatabase()
                .query("PRAGMA wal_checkpoint(TRUNCATE)").close();

        ContentValues values = new ContentValues();
        values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
        values.put(MediaStore.Downloads.MIME_TYPE, "application/zip");
        values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
        values.put(MediaStore.Downloads.IS_PENDING, 1);

        Uri uri = getContentResolver().insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        if (uri == null) {
            throw new IllegalStateException("Failed to create auto backup entry in Downloads");
        }

        try {
            try (java.io.OutputStream out = getContentResolver().openOutputStream(uri)) {
                if (out == null) {
                    throw new IllegalStateException("Failed to open output stream for auto backup");
                }
                try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(out))) {
                    if (dbFile.exists()) {
                        zipFileToStream(zos, dbFile, "db/dogcuisine.db");
                    }
                    if (imagesDir.exists()) {
                        zipDirToStream(zos, imagesDir, "images");
                    }
                }
            }

            // 标记写入完成
            ContentValues done = new ContentValues();
            done.put(MediaStore.Downloads.IS_PENDING, 0);
            getContentResolver().update(uri, done, null, null);
        } catch (Exception e) {
            // 备份失败时清理 MediaStore 中的残留条目
            getContentResolver().delete(uri, null, null);
            throw e;
        }

        // 记录备份时间
        getSharedPreferences(PREF_AUTO_BACKUP, MODE_PRIVATE)
                .edit()
                .putLong(KEY_LAST_AUTO_BACKUP_TIME, System.currentTimeMillis())
                .apply();
    }

    /**
     * 清理旧的自动备份文件，只保留最新的 AUTO_BACKUP_KEEP_COUNT 份。
     * 通过 MediaStore 查询以 AUTO_BACKUP_PREFIX 开头的 .zip 文件，
     * 不影响手动备份文件（手动备份使用 "dogcuisine_backup_" 前缀）。
     */
    private void cleanupOldAutoBackups() {
        try {
            String[] projection = {
                    MediaStore.Downloads._ID,
                    MediaStore.Downloads.DISPLAY_NAME
            };
            String selection = MediaStore.Downloads.DISPLAY_NAME + " LIKE ? AND "
                    + MediaStore.Downloads.DISPLAY_NAME + " LIKE ?";
            String[] selectionArgs = {AUTO_BACKUP_PREFIX + "%", "%.zip"};
            String sortOrder = MediaStore.Downloads.DISPLAY_NAME + " DESC";

            List<Uri> autoBackupUris = new ArrayList<>();
            List<String> autoBackupNames = new ArrayList<>();
            try (Cursor cursor = getContentResolver().query(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    projection, selection, selectionArgs, sortOrder)) {
                if (cursor != null) {
                    int idCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID);
                    int nameCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME);
                    while (cursor.moveToNext()) {
                        long id = cursor.getLong(idCol);
                        autoBackupUris.add(ContentUris.withAppendedId(
                                MediaStore.Downloads.EXTERNAL_CONTENT_URI, id));
                        autoBackupNames.add(cursor.getString(nameCol));
                    }
                }
            }

            // 显式按文件名降序排序，确保最新备份在前
            Integer[] indices = new Integer[autoBackupNames.size()];
            for (int i = 0; i < indices.length; i++) indices[i] = i;
            Arrays.sort(indices, (a, b) -> autoBackupNames.get(b).compareTo(autoBackupNames.get(a)));

            List<Uri> sorted = new ArrayList<>(autoBackupUris.size());
            for (int idx : indices) {
                sorted.add(autoBackupUris.get(idx));
            }

            // 前 KEEP_COUNT 个保留，其余删除
            if (sorted.size() > AUTO_BACKUP_KEEP_COUNT) {
                for (int i = AUTO_BACKUP_KEEP_COUNT; i < sorted.size(); i++) {
                    getContentResolver().delete(sorted.get(i), null, null);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void zipFileToStream(ZipOutputStream zos, File file, String entryName) throws Exception {
        zos.putNextEntry(new ZipEntry(entryName));
        try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file))) {
            byte[] buffer = new byte[4096];
            int len;
            while ((len = bis.read(buffer)) != -1) {
                zos.write(buffer, 0, len);
            }
        }
        zos.closeEntry();
    }

    private void zipDirToStream(ZipOutputStream zos, File dir, String baseName) throws Exception {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File file : files) {
            String entryName = baseName + "/" + file.getName();
            if (file.isDirectory()) {
                zipDirToStream(zos, file, entryName);
            } else {
                zipFileToStream(zos, file, entryName);
            }
        }
    }
}
