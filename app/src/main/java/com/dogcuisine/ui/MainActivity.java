package com.dogcuisine.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.dogcuisine.App;
import com.dogcuisine.R;
import com.dogcuisine.data.AppDatabase;
import com.dogcuisine.data.RecipeEntity;
import com.dogcuisine.data.StepItem;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

public class MainActivity extends AppCompatActivity {

    private static final int MENU_ID_ADD = 1001;

    private RecipeAdapter adapter;
    private AppDatabase database;
    private ExecutorService ioExecutor;
    private final Gson gson = new Gson();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        SystemBarHelper.applyEdgeToEdge(this,
                new View[]{findViewById(R.id.toolbar)},
                new View[]{findViewById(R.id.rvRecipes)});

        App app = App.getInstance();
        database = app.getDatabase();
        ioExecutor = app.ioExecutor();

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(getString(R.string.recipes_title));
        toolbar.setTitleTextColor(ContextCompat.getColor(this, R.color.teal_200));
        // 动态添加菜单项，避免缺失 menu_main 资源导致崩溃
        Menu menu = toolbar.getMenu();
        menu.clear();
        MenuItem addItem = menu.add(Menu.NONE, MENU_ID_ADD, Menu.NONE, "添加");
        addItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        addItem.setIcon(R.drawable.ic_add_gold);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == MENU_ID_ADD) {
                Intent intent = new Intent(MainActivity.this, AddRecipeActivity.class);
                startActivity(intent);
                return true;
            }
            return false;
        });

        RecyclerView recyclerView = findViewById(R.id.rvRecipes);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new RecipeAdapter(new ArrayList<>(), recipe -> {
            Intent intent = new Intent(MainActivity.this, RecipeDetailActivity.class);
            intent.putExtra(RecipeDetailActivity.EXTRA_RECIPE_ID, recipe.getId());
            startActivity(intent);
        }, this::confirmDelete);
        recyclerView.setAdapter(adapter);

        loadRecipesFromDb();
    }

    private void confirmDelete(RecipeEntity recipe) {
        new AlertDialog.Builder(this)
                .setTitle("删除菜谱")
                .setMessage("确定删除“" + recipe.getName() + "”吗？")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (d, which) -> deleteRecipe(recipe))
                .show();
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
                loadRecipesFromDb();
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
        loadRecipesFromDb();
    }

    private void loadRecipesFromDb() {
        ioExecutor.execute(() -> {
            List<RecipeEntity> list = database.recipeDao().getAll();
            runOnUiThread(() -> adapter.setRecipes(list));
        });
    }
}
