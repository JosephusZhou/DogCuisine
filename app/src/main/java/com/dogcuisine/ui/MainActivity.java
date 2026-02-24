package com.dogcuisine.ui;

import android.content.Intent;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.splashscreen.SplashScreen;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.dogcuisine.App;
import com.dogcuisine.R;
import com.dogcuisine.data.AppDatabase;
import com.dogcuisine.data.CategoryEntity;
import com.dogcuisine.data.RecipeEntity;
import com.dogcuisine.data.StepItem;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.color.MaterialColors;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

public class MainActivity extends AppCompatActivity {

    private static final int MENU_ID_CATEGORY = 1000;
    private static final int MENU_ID_ADD = 1001;
    private static final int MENU_ID_SEARCH = 1002;
    private static final int MENU_ID_SYNC = 1003;
    private static final int MENU_ID_BACKUP = 1004;
    private static final long SPLASH_DURATION_MS = 3000L;

    private RecyclerView rvRecipes;
    private View tvRecipesEmpty;
    private RecipeAdapter adapter;
    private CategoryAdapter categoryAdapter;
    private AppDatabase database;
    private ExecutorService ioExecutor;
    private final Gson gson = new Gson();
    private Long selectedCategoryId;
    private ActivityResultLauncher<Intent> categoryManageLauncher;
    private ActivityResultLauncher<Intent> syncLauncher;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        final long splashStartTime = System.currentTimeMillis();
        SplashScreen splashScreen = SplashScreen.installSplashScreen(this);
        splashScreen.setKeepOnScreenCondition(() ->
                System.currentTimeMillis() - splashStartTime < SPLASH_DURATION_MS);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        SystemBarHelper.applyEdgeToEdge(this,
                new View[]{findViewById(R.id.toolbar)},
                new View[]{findViewById(R.id.rvRecipes), findViewById(R.id.rvCategories)});

        App app = App.getInstance();
        database = app.getDatabase();
        ioExecutor = app.ioExecutor();

        categoryManageLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK) {
                loadCategoriesFromDb();
            }
        });
        syncLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK) {
                database = App.getInstance().getDatabase();
                loadCategoriesFromDb();
            }
        });

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(getString(R.string.recipes_title));
        toolbar.setTitleTextColor(ContextCompat.getColor(this, R.color.teal_200));
        if (toolbar.getOverflowIcon() != null) {
            toolbar.getOverflowIcon().setTint(ContextCompat.getColor(this, R.color.teal_200));
        }
        // 动态添加菜单项，避免缺失 menu_main 资源导致崩溃
        Menu menu = toolbar.getMenu();
        menu.clear();
        MenuItem searchItem = menu.add(Menu.NONE, MENU_ID_SEARCH, Menu.NONE, "搜索");
        searchItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        searchItem.setIcon(R.drawable.ic_search_gold);
        MenuItem categoryItem = menu.add(Menu.NONE, MENU_ID_CATEGORY, Menu.NONE, "分类");
        categoryItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        categoryItem.setIcon(R.drawable.ic_category_gold);
        MenuItem addItem = menu.add(Menu.NONE, MENU_ID_ADD, Menu.NONE, "添加");
        addItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        addItem.setIcon(R.drawable.ic_add_gold);
        MenuItem syncItem = menu.add(Menu.NONE, MENU_ID_SYNC, Menu.NONE, "同步");
        syncItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        MenuItem backupItem = menu.add(Menu.NONE, MENU_ID_BACKUP, Menu.NONE, "备份与恢复");
        backupItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == MENU_ID_SEARCH) {
                Intent intent = new Intent(MainActivity.this, SearchRecipeActivity.class);
                startActivity(intent);
                overridePendingTransition(0, 0);
                return true;
            } else if (item.getItemId() == MENU_ID_CATEGORY) {
                Intent intent = new Intent(MainActivity.this, CategoryManageActivity.class);
                categoryManageLauncher.launch(intent);
                return true;
            } else if (item.getItemId() == MENU_ID_ADD) {
                Intent intent = new Intent(MainActivity.this, AddRecipeActivity.class);
                startActivity(intent);
                return true;
            } else if (item.getItemId() == MENU_ID_SYNC) {
                Intent intent = new Intent(MainActivity.this, WebDavSyncActivity.class);
                syncLauncher.launch(intent);
                return true;
            } else if (item.getItemId() == MENU_ID_BACKUP) {
                Intent intent = new Intent(MainActivity.this, BackupRestoreActivity.class);
                syncLauncher.launch(intent);
                return true;
            }
            return false;
        });

        RecyclerView categoryList = findViewById(R.id.rvCategories);
        categoryList.setLayoutManager(new LinearLayoutManager(this));
        categoryAdapter = new CategoryAdapter(new ArrayList<>(), this::onCategorySelected);
        categoryList.setAdapter(categoryAdapter);

        rvRecipes = findViewById(R.id.rvRecipes);
        tvRecipesEmpty = findViewById(R.id.tvRecipesEmpty);
        rvRecipes.setLayoutManager(new LinearLayoutManager(this));
        adapter = new RecipeAdapter(new ArrayList<>(), recipe -> {
            Intent intent = new Intent(MainActivity.this, RecipeDetailActivity.class);
            intent.putExtra(RecipeDetailActivity.EXTRA_RECIPE_ID, recipe.getId());
            startActivity(intent);
        }, this::confirmDelete);
        rvRecipes.setAdapter(adapter);

        loadCategoriesFromDb();
    }

    private void confirmDelete(RecipeEntity recipe) {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("删除菜谱")
                .setMessage("确定删除“" + recipe.getName() + "”吗？")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (d, which) -> deleteRecipe(recipe))
                .create();
        dialog.setOnShowListener(d -> {
            int secondaryColor = MaterialColors.getColor(
                    dialog.getContext(),
                    com.google.android.material.R.attr.colorSecondary,
                    ContextCompat.getColor(this, R.color.teal_200));
            dialog.getButton(DialogInterface.BUTTON_NEGATIVE).setTextColor(secondaryColor);
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setTextColor(secondaryColor);
        });
        dialog.show();
    }

    private void deleteRecipe(RecipeEntity recipe) {
        if (recipe.getId() == null) {
            Toast.makeText(this, "无法删除：ID 缺失", Toast.LENGTH_SHORT).show();
            return;
        }
        ioExecutor.execute(() -> {
            deleteImages(recipe);
            database.recipeDao().deleteById(recipe.getId());
            runOnUiThread(() -> {
                Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show();
                loadRecipesForSelectedCategory();
            });
        });
    }

    private void deleteImages(RecipeEntity recipe) {
        // 封面
        deleteFileSafe(recipe.getCoverImagePath());
        // 步骤图
        List<StepItem> steps = parseSteps(recipe.getStepsJson());
        for (StepItem step : steps) {
            for (String path : step.getImagePaths()) {
                deleteFileSafe(path);
            }
        }
    }

    private List<StepItem> parseSteps(String json) {
        if (json == null || json.isEmpty()) return new ArrayList<>();
        try {
            Type type = new TypeToken<List<StepItem>>() {}.getType();
            List<StepItem> list = gson.fromJson(json, type);
            return list != null ? list : new ArrayList<>();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private void deleteFileSafe(String path) {
        if (path == null || path.isEmpty()) return;
        try {
            File file = new File(path);
            if (file.exists()) {
                file.delete();
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadCategoriesFromDb();
    }

    private void onCategorySelected(CategoryEntity category) {
        selectedCategoryId = category != null ? category.getId() : null;
        categoryAdapter.setSelectedCategoryId(selectedCategoryId);
        loadRecipesForSelectedCategory();
    }

    private void loadCategoriesFromDb() {
        ioExecutor.execute(() -> {
            List<CategoryEntity> categories = database.categoryDao().getAll();
            Long nextSelected = selectedCategoryId;
            if (categories != null && !categories.isEmpty()) {
                boolean hasSelected = false;
                if (nextSelected != null) {
                    for (CategoryEntity c : categories) {
                        if (nextSelected.equals(c.getId())) {
                            hasSelected = true;
                            break;
                        }
                    }
                }
                if (!hasSelected) {
                    nextSelected = categories.get(0).getId();
                }
            } else {
                nextSelected = null;
            }
            Long finalNextSelected = nextSelected;
            List<CategoryEntity> safeList = categories != null ? categories : new ArrayList<>();
            runOnUiThread(() -> {
                selectedCategoryId = finalNextSelected;
                categoryAdapter.setCategories(safeList);
                categoryAdapter.setSelectedCategoryId(selectedCategoryId);
                loadRecipesForSelectedCategory();
            });
        });
    }

    private void loadRecipesForSelectedCategory() {
        ioExecutor.execute(() -> {
            List<RecipeEntity> list = database.recipeDao().getByCategoryId(selectedCategoryId);
            runOnUiThread(() -> {
                adapter.setRecipes(list);
                boolean isEmpty = list == null || list.isEmpty();
                rvRecipes.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
                tvRecipesEmpty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
            });
        });
    }
}
