package com.dogcuisine;

import android.app.Application;

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

import java.io.File;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class App extends Application {

    private static App instance;
    private AppDatabase database;
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService autoSyncExecutor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean autoSyncRunning = new AtomicBoolean(false);
    private final AtomicBoolean autoSyncRequested = new AtomicBoolean(false);

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
                        new RecipeEntity(null, "宫保鸡丁", now, now, "花生脆香，微辣下饭。", null, "[]", "", defaultCategoryId, 0),
                        new RecipeEntity(null, "红烧肉", now, now, "酱香浓郁，肥而不腻。", null, "[]", "", defaultCategoryId, 0),
                        new RecipeEntity(null, "清炒西兰花", now, now, "清爽脆嫩，保留蔬菜本味。", null, "[]", "", defaultCategoryId, 0)
                );
                database.recipeDao().insertAll(seeds);
            }
            
            // 执行图片清理操作
            cleanupUnusedImages();
        });
    }

    @Nullable
    private Long ensureDefaultCategories(@NonNull CategoryDao categoryDao) {
        try {
            long categoryCount = categoryDao.count();
            if (categoryCount == 0) {
                List<CategoryEntity> defaults = Arrays.asList(
                        new CategoryEntity(null, "未分类", 0),
                        new CategoryEntity(null, "主食", 1),
                        new CategoryEntity(null, "小食", 2)
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
}
