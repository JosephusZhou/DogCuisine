package com.dogcuisine.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.dogcuisine.App;
import com.dogcuisine.R;
import com.dogcuisine.data.RecipeDao;
import com.dogcuisine.data.RecipeEntity;
import com.dogcuisine.data.StepItem;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;

public class RecipeDetailActivity extends AppCompatActivity {

    public static final String EXTRA_RECIPE_ID = "recipe_id";
    private static final int MENU_ID_EDIT = 3001;

    private ImageView ivCover;
    private TextView tvName;
    private RecyclerView rvSteps;
    private StepDisplayAdapter stepAdapter;

    private long recipeId;
    private ExecutorService ioExecutor;
    private RecipeDao recipeDao;
    private final Gson gson = new Gson();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recipe_detail);

        SystemBarHelper.applyEdgeToEdge(this,
                new View[]{findViewById(R.id.toolbarDetail)},
                new View[]{findViewById(android.R.id.content)});

        App app = App.getInstance();
        ioExecutor = app.ioExecutor();
        recipeDao = app.getDatabase().recipeDao();

        MaterialToolbar toolbar = findViewById(R.id.toolbarDetail);
        toolbar.setTitle(getString(R.string.recipes_title));
        toolbar.setTitleTextColor(ContextCompat.getColor(this, R.color.teal_200));
        toolbar.setNavigationIcon(R.drawable.ic_back_gold);
        toolbar.setNavigationOnClickListener(v -> finish());

        // 右侧编辑菜单，使用温暖米金配色，与添加/保存统一
        Menu menu = toolbar.getMenu();
        menu.clear();
        MenuItem editItem = menu.add(Menu.NONE, MENU_ID_EDIT, Menu.NONE, "修改");
        editItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        editItem.setIcon(R.drawable.ic_edit_gold);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == MENU_ID_EDIT) {
                Intent intent = new Intent(this, AddRecipeActivity.class);
                intent.putExtra(AddRecipeActivity.EXTRA_RECIPE_ID, recipeId);
                startActivity(intent);
                return true;
            }
            return false;
        });

        ivCover = findViewById(R.id.ivDetailCover);
        tvName = findViewById(R.id.tvDetailName);
        rvSteps = findViewById(R.id.rvDetailSteps);
        rvSteps.setLayoutManager(new LinearLayoutManager(this));
        stepAdapter = new StepDisplayAdapter();
        rvSteps.setAdapter(stepAdapter);

        recipeId = getIntent().getLongExtra(EXTRA_RECIPE_ID, -1);
        if (recipeId <= 0) {
            Toast.makeText(this, "无效的菜谱", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        loadRecipe();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (recipeId > 0) {
            loadRecipe();
        }
    }

    private void loadRecipe() {
        ioExecutor.execute(() -> {
            RecipeEntity entity = recipeDao.getById(recipeId);
            runOnUiThread(() -> {
                if (entity == null) {
                    Toast.makeText(this, "未找到菜谱", Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }
                bindRecipe(entity);
            });
        });
    }

    private void bindRecipe(RecipeEntity entity) {
        tvName.setText(entity.getName());
        String cover = entity.getCoverImagePath();
        if (cover != null && !cover.isEmpty()) {
            ivCover.setImageURI(Uri.fromFile(new File(cover)));
        }
        List<StepItem> stepItems = parseSteps(entity.getStepsJson());
        stepAdapter.setData(stepItems);
    }

    private List<StepItem> parseSteps(String json) {
        if (json == null || json.isEmpty()) return Collections.emptyList();
        try {
            Type type = new TypeToken<List<StepItem>>() {}.getType();
            List<StepItem> list = gson.fromJson(json, type);
            return list != null ? list : Collections.emptyList();
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
