package com.dogcuisine.ui;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.dogcuisine.App;
import com.dogcuisine.R;
import com.dogcuisine.data.AppDatabase;
import com.dogcuisine.data.RecipeDao;
import com.dogcuisine.data.RecipeEntity;
import com.dogcuisine.data.StepItem;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.gson.Gson;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

public class AddRecipeActivity extends AppCompatActivity implements StepAdapter.StepImageAddListener, StepAdapter.StepDeleteListener {

    public static final String EXTRA_RECIPE_ID = "recipe_id";
    private static final int MENU_ID_SAVE = 2001;

    private ImageView ivCover;
    private TextView tvCoverHint;
    private EditText etName;
    private RecyclerView rvSteps;
    private StepAdapter stepAdapter;
    private final List<StepItem> steps = new ArrayList<>();
    private String coverPath;

    private ExecutorService ioExecutor;
    private RecipeDao recipeDao;
    private Gson gson = new Gson();

    private ActivityResultLauncher<String[]> coverPicker;
    private ActivityResultLauncher<String[]> stepImagesPicker;
    private int pendingStepIndex = -1;

    private long editingId = -1;
    private long existingCreatedAt = -1;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_recipe);

        SystemBarHelper.applyEdgeToEdge(this,
                new View[]{findViewById(R.id.toolbarAdd)},
                new View[]{findViewById(android.R.id.content)});

        App app = App.getInstance();
        ioExecutor = app.ioExecutor();
        recipeDao = app.getDatabase().recipeDao();

        MaterialToolbar toolbar = findViewById(R.id.toolbarAdd);
        toolbar.setNavigationIcon(R.drawable.ic_back_gold);
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.setTitleTextColor(ContextCompat.getColor(this, R.color.teal_200));

        // 右侧保存菜单按钮（温暖米金配色，与添加按钮一致）
        Menu menu = toolbar.getMenu();
        menu.clear();
        MenuItem saveItem = menu.add(Menu.NONE, MENU_ID_SAVE, Menu.NONE, "保存");
        saveItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        saveItem.setIcon(R.drawable.ic_save_gold);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == MENU_ID_SAVE) {
                saveRecipe();
                return true;
            }
            return false;
        });

        ivCover = findViewById(R.id.ivCover);
        tvCoverHint = findViewById(R.id.tvCoverHint);
        etName = findViewById(R.id.etName);
        rvSteps = findViewById(R.id.rvSteps);
        Button btnAddStep = findViewById(R.id.btnAddStep);

        rvSteps.setLayoutManager(new LinearLayoutManager(this));
        steps.add(new StepItem());
        stepAdapter = new StepAdapter(steps, this, this);
        rvSteps.setAdapter(stepAdapter);

        FrameLayout flCover = findViewById(R.id.flCover);
        flCover.setOnClickListener(v -> pickCover());
        btnAddStep.setOnClickListener(v -> addStep());

        registerPickers();

        editingId = getIntent().getLongExtra(EXTRA_RECIPE_ID, -1);
        if (editingId > 0) {
            toolbar.setTitle("编辑菜谱");
            loadForEdit(editingId);
        } else {
            toolbar.setTitle(getString(R.string.add_recipe_title));
        }
    }

    private void registerPickers() {
        coverPicker = registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
            if (uri != null) {
                String path = copyAndCompressImage(uri, "cover");
                if (path != null) {
                    coverPath = path;
                    ivCover.setImageURI(Uri.fromFile(new File(path)));
                    tvCoverHint.setText("");
                }
            }
        });

        stepImagesPicker = registerForActivityResult(new ActivityResultContracts.OpenMultipleDocuments(), uris -> {
            if (pendingStepIndex >= 0 && pendingStepIndex < steps.size() && uris != null && !uris.isEmpty()) {
                List<String> paths = new ArrayList<>();
                for (Uri uri : uris) {
                    String p = copyAndCompressImage(uri, "step" + pendingStepIndex);
                    if (p != null) paths.add(p);
                }
                steps.get(pendingStepIndex).addImagePaths(paths);
                stepAdapter.notifyItemChanged(pendingStepIndex);
            }
            pendingStepIndex = -1;
        });
    }

    private void pickCover() {
        coverPicker.launch(new String[]{"image/*"});
    }

    private void addStep() {
        steps.add(new StepItem());
        stepAdapter.notifyItemInserted(steps.size() - 1);
    }

    private void saveRecipe() {
        String name = etName.getText().toString().trim();
        if (name.isEmpty()) {
            Toast.makeText(this, "请输入菜谱名称", Toast.LENGTH_SHORT).show();
            return;
        }

        long now = System.currentTimeMillis();
        String stepsJson = gson.toJson(steps);
        RecipeEntity entity;
        if (editingId > 0) {
            entity = new RecipeEntity(editingId, name, existingCreatedAt > 0 ? existingCreatedAt : now, now, "", coverPath, stepsJson);
        } else {
            entity = new RecipeEntity(null, name, now, now, "", coverPath, stepsJson);
        }

        ioExecutor.execute(() -> {
            recipeDao.insert(entity);
            runOnUiThread(() -> {
                Toast.makeText(this, "保存成功", Toast.LENGTH_SHORT).show();
                setResult(RESULT_OK);
                finish();
            });
        });
    }

    @Override
    public void onAddImages(int position) {
        pendingStepIndex = position;
        stepImagesPicker.launch(new String[]{"image/*"});
    }

    @Override
    public void onDelete(int position) {
        if (position >= 0 && position < steps.size()) {
            steps.remove(position);
            stepAdapter.notifyItemRemoved(position);
            stepAdapter.notifyItemRangeChanged(position, steps.size() - position);
        }
    }

    private void loadForEdit(long id) {
        ioExecutor.execute(() -> {
            RecipeEntity entity = recipeDao.getById(id);
            runOnUiThread(() -> {
                if (entity == null) {
                    Toast.makeText(this, "未找到菜谱", Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }
                existingCreatedAt = entity.getCreatedAt();
                etName.setText(entity.getName());
                coverPath = entity.getCoverImagePath();
                if (coverPath != null && !coverPath.isEmpty()) {
                    ivCover.setImageURI(Uri.fromFile(new File(coverPath)));
                    tvCoverHint.setText("");
                }
                List<StepItem> loaded = parseSteps(entity.getStepsJson());
                steps.clear();
                if (loaded.isEmpty()) {
                    steps.add(new StepItem());
                } else {
                    steps.addAll(loaded);
                }
                stepAdapter.notifyDataSetChanged();
            });
        });
    }

    private List<StepItem> parseSteps(String json) {
        if (json == null || json.isEmpty()) return new ArrayList<>();
        try {
            java.lang.reflect.Type type = new com.google.gson.reflect.TypeToken<List<StepItem>>() {}.getType();
            List<StepItem> list = gson.fromJson(json, type);
            return list != null ? list : new ArrayList<>();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    @Nullable
    private String copyAndCompressImage(@NonNull Uri uri, @NonNull String prefix) {
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) return null;
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
            Bitmap bitmap = BitmapFactory.decodeStream(in, null, opts);
            if (bitmap == null) return null;

            File dir = new File(getFilesDir(), "images");
            if (!dir.exists()) dir.mkdirs();
            String fileName = prefix + "_" + System.currentTimeMillis() + ".jpg";
            File outFile = new File(dir, fileName);
            try (FileOutputStream fos = new FileOutputStream(outFile)) {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, fos);
                fos.flush();
            }
            bitmap.recycle();
            return outFile.getAbsolutePath();
        } catch (Exception e) {
            e.printStackTrace();
            runOnUiThread(() -> Toast.makeText(this, "图片处理失败", Toast.LENGTH_SHORT).show());
            return null;
        }
    }
}
