package com.dogcuisine.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.dogcuisine.App;
import com.dogcuisine.R;
import com.dogcuisine.data.RecipeDao;
import com.dogcuisine.data.RecipeEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

public class SearchRecipeActivity extends AppCompatActivity {

    private static final long SEARCH_DEBOUNCE_MS = 500L;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private RecyclerView rvSearchRecipes;
    private RecipeAdapter recipeAdapter;
    private ExecutorService ioExecutor;
    private RecipeDao recipeDao;
    private Runnable pendingSearchTask;
    private int searchVersion = 0;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search_recipe);

        View topBarSearch = findViewById(R.id.topBarSearch);
        rvSearchRecipes = findViewById(R.id.rvSearchRecipes);
        ImageButton btnSearchBack = findViewById(R.id.btnSearchBack);
        TextView tvSearchCancel = findViewById(R.id.tvSearchCancel);
        EditText etSearchRecipe = findViewById(R.id.etSearchRecipe);

        SystemBarHelper.applyEdgeToEdge(this,
                new View[]{topBarSearch},
                new View[]{rvSearchRecipes});

        App app = App.getInstance();
        ioExecutor = app.ioExecutor();
        recipeDao = app.getDatabase().recipeDao();

        rvSearchRecipes.setLayoutManager(new LinearLayoutManager(this));
        recipeAdapter = new RecipeAdapter(new ArrayList<>(), recipe -> {
            Intent intent = new Intent(this, RecipeDetailActivity.class);
            intent.putExtra(RecipeDetailActivity.EXTRA_RECIPE_ID, recipe.getId());
            startActivity(intent);
            finishWithoutAnimation();
        }, recipe -> {});
        rvSearchRecipes.setAdapter(recipeAdapter);

        btnSearchBack.setOnClickListener(v -> finishWithoutAnimation());
        tvSearchCancel.setOnClickListener(v -> finishWithoutAnimation());

        etSearchRecipe.requestFocus();
        etSearchRecipe.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                scheduleSearch(s != null ? s.toString().trim() : "");
            }
        });
    }

    private void scheduleSearch(String keyword) {
        if (pendingSearchTask != null) {
            mainHandler.removeCallbacks(pendingSearchTask);
        }
        searchVersion++;
        if (keyword.isEmpty()) {
            recipeAdapter.setRecipes(new ArrayList<>());
            rvSearchRecipes.setVisibility(View.GONE);
            return;
        }
        final int currentVersion = searchVersion;
        pendingSearchTask = () -> doSearch(keyword, currentVersion);
        mainHandler.postDelayed(pendingSearchTask, SEARCH_DEBOUNCE_MS);
    }

    private void doSearch(String keyword, int currentVersion) {
        final String fuzzy = "%" + keyword + "%";
        ioExecutor.execute(() -> {
            List<RecipeEntity> list = recipeDao.searchByKeyword(fuzzy);
            if (list == null) {
                list = new ArrayList<>();
            }
            List<RecipeEntity> results = list;
            runOnUiThread(() -> {
                if (isFinishing() || currentVersion != searchVersion) {
                    return;
                }
                recipeAdapter.setRecipes(results);
                rvSearchRecipes.setVisibility(View.VISIBLE);
            });
        });
    }

    private void finishWithoutAnimation() {
        finish();
        overridePendingTransition(0, 0);
    }

    @Override
    public void onBackPressed() {
        finishWithoutAnimation();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mainHandler.removeCallbacksAndMessages(null);
    }
}
