package com.dogcuisine.ui;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.dogcuisine.App;
import com.dogcuisine.R;
import com.dogcuisine.data.AppDatabase;
import com.dogcuisine.data.CategoryDao;
import com.dogcuisine.data.CategoryEntity;
import com.dogcuisine.data.RecipeDao;
import com.dogcuisine.data.RecipeEntity;
import com.dogcuisine.data.StepItem;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.gson.Gson;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

public class AddRecipeActivity extends AppCompatActivity implements StepAdapter.StepImageAddListener, StepAdapter.StepDeleteListener {

    public static final String EXTRA_RECIPE_ID = "recipe_id";
    private static final int MENU_ID_SAVE = 2001;
    private static final int MAX_IMAGE_LONG_EDGE = 1600;
    private static final int TARGET_IMAGE_BYTES = 600 * 1024;
    private static final int JPEG_QUALITY_START = 85;
    private static final int JPEG_QUALITY_MIN = 65;

    private ImageView ivCover;
    private TextView tvCoverHint;
    private EditText etName;
    private Spinner spCategory;
    private NestedScrollView nsvAddRecipe;
    private RecyclerView rvSteps;
    private StepAdapter stepAdapter;
    private final List<StepItem> steps = new ArrayList<>();
    private String coverPath;
    private Long selectedCategoryId;
    private final List<CategoryEntity> categoryOptions = new ArrayList<>();
    private ArrayAdapter<String> categorySpinnerAdapter;

    private ExecutorService ioExecutor;
    private RecipeDao recipeDao;
    private CategoryDao categoryDao;
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
        categoryDao = app.getDatabase().categoryDao();

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
        spCategory = findViewById(R.id.spCategory);
        nsvAddRecipe = findViewById(R.id.nsvAddRecipe);
        rvSteps = findViewById(R.id.rvSteps);
        Button btnAddStep = findViewById(R.id.btnAddStep);

        setupCategorySpinner();

        rvSteps.setLayoutManager(new LinearLayoutManager(this));
        rvSteps.setNestedScrollingEnabled(false);
        steps.add(new StepItem());
        stepAdapter = new StepAdapter(steps, this, this);
        rvSteps.setAdapter(stepAdapter);

        FrameLayout flCover = findViewById(R.id.flCover);
        flCover.setOnClickListener(v -> pickCover());
        btnAddStep.setOnClickListener(v -> addStep());

        registerPickers();
        loadCategoriesForSpinner();

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
        int newPosition = steps.size() - 1;
        stepAdapter.notifyItemInserted(newPosition);
        rvSteps.post(() -> {
            if (nsvAddRecipe != null) {
                nsvAddRecipe.fullScroll(View.FOCUS_DOWN);
            }
        });
    }

    private void saveRecipe() {
        String name = etName.getText().toString().trim();
        if (name.isEmpty()) {
            Toast.makeText(this, "请输入菜谱名称", Toast.LENGTH_SHORT).show();
            return;
        }

        final long now = System.currentTimeMillis();
        final String stepsJson = gson.toJson(steps);
        final long editingIdSnapshot = editingId;
        final long existingCreatedAtSnapshot = existingCreatedAt;
        final String coverPathSnapshot = coverPath;

        ioExecutor.execute(() -> {
            Long categoryId = selectedCategoryId;
            if (categoryId == null) {
                categoryId = fetchDefaultCategoryId();
            }
            RecipeEntity entity;
            if (editingIdSnapshot > 0) {
                entity = new RecipeEntity(editingIdSnapshot, name, existingCreatedAtSnapshot > 0 ? existingCreatedAtSnapshot : now, now, "", coverPathSnapshot, stepsJson, categoryId);
            } else {
                entity = new RecipeEntity(null, name, now, now, "", coverPathSnapshot, stepsJson, categoryId);
            }
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
                selectedCategoryId = entity.getCategoryId();
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
                syncSpinnerSelection();
            });
        });
    }

    @Nullable
    private Long fetchDefaultCategoryId() {
        try {
            List<CategoryEntity> categories = categoryDao.getAll();
            if (categories != null && !categories.isEmpty()) {
                return categories.get(0).getId();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private void setupCategorySpinner() {
        categorySpinnerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new ArrayList<>());
        categorySpinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spCategory.setAdapter(categorySpinnerAdapter);
        spCategory.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < categoryOptions.size()) {
                    selectedCategoryId = categoryOptions.get(position).getId();
                } else {
                    selectedCategoryId = null;
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                selectedCategoryId = null;
            }
        });
    }

    private void loadCategoriesForSpinner() {
        ioExecutor.execute(() -> {
            List<CategoryEntity> all = categoryDao.getAll();
            if (all == null) all = new ArrayList<>();

            List<String> displayNames = new ArrayList<>();
            for (CategoryEntity c : all) {
                displayNames.add(c.getName());
            }

            Long current = selectedCategoryId;
            int selectionIndex = 0;
            if (current != null) {
                for (int i = 0; i < all.size(); i++) {
                    if (current.equals(all.get(i).getId())) {
                        selectionIndex = i;
                        break;
                    }
                }
            } else if (editingId <= 0 && !all.isEmpty()) {
                selectionIndex = 0;
                current = all.get(0).getId();
            }

            Long finalCurrent = current;
            int finalSelectionIndex = selectionIndex;
            List<CategoryEntity> finalAll = all;
            runOnUiThread(() -> {
                categoryOptions.clear();
                categoryOptions.addAll(finalAll);
                categorySpinnerAdapter.clear();
                categorySpinnerAdapter.addAll(displayNames);
                categorySpinnerAdapter.notifyDataSetChanged();
                selectedCategoryId = finalCurrent;
                spCategory.setSelection(finalSelectionIndex, false);
            });
        });
    }

    private void syncSpinnerSelection() {
        loadCategoriesForSpinner();
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
        try {
            Bitmap bitmap = decodeBitmapWithSample(uri, MAX_IMAGE_LONG_EDGE);
            if (bitmap == null) return null;

            Bitmap scaledBitmap = scaleBitmapIfNeeded(bitmap, MAX_IMAGE_LONG_EDGE);
            Bitmap rotatedBitmap = rotateByExifIfNeeded(scaledBitmap, uri);
            byte[] compressed = compressToJpegBytes(rotatedBitmap);

            File dir = new File(getFilesDir(), "images");
            if (!dir.exists()) dir.mkdirs();
            String fileName = prefix + "_" + System.currentTimeMillis() + ".jpg";
            File outFile = new File(dir, fileName);
            try (FileOutputStream fos = new FileOutputStream(outFile)) {
                fos.write(compressed);
                fos.flush();
            }
            if (!rotatedBitmap.isRecycled()) rotatedBitmap.recycle();
            if (scaledBitmap != rotatedBitmap && !scaledBitmap.isRecycled()) scaledBitmap.recycle();
            if (bitmap != scaledBitmap && bitmap != rotatedBitmap && !bitmap.isRecycled()) bitmap.recycle();
            return outFile.getAbsolutePath();
        } catch (Exception e) {
            e.printStackTrace();
            runOnUiThread(() -> Toast.makeText(this, "图片处理失败", Toast.LENGTH_SHORT).show());
            return null;
        }
    }

    @Nullable
    private Bitmap decodeBitmapWithSample(@NonNull Uri uri, int maxLongEdge) throws Exception {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) return null;
            BitmapFactory.decodeStream(in, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null;
        }

        BitmapFactory.Options decode = new BitmapFactory.Options();
        decode.inPreferredConfig = Bitmap.Config.RGB_565;
        decode.inSampleSize = computeInSampleSize(bounds.outWidth, bounds.outHeight, maxLongEdge);
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) return null;
            return BitmapFactory.decodeStream(in, null, decode);
        }
    }

    private int computeInSampleSize(int width, int height, int maxLongEdge) {
        int inSampleSize = 1;
        int longEdge = Math.max(width, height);
        while (longEdge / inSampleSize > maxLongEdge * 2) {
            inSampleSize *= 2;
        }
        return Math.max(1, inSampleSize);
    }

    @NonNull
    private Bitmap scaleBitmapIfNeeded(@NonNull Bitmap source, int maxLongEdge) {
        int width = source.getWidth();
        int height = source.getHeight();
        int longEdge = Math.max(width, height);
        if (longEdge <= maxLongEdge) {
            return source;
        }
        float ratio = maxLongEdge / (float) longEdge;
        int targetW = Math.max(1, Math.round(width * ratio));
        int targetH = Math.max(1, Math.round(height * ratio));
        Bitmap scaled = Bitmap.createScaledBitmap(source, targetW, targetH, true);
        if (scaled != source) {
            source.recycle();
        }
        return scaled;
    }

    @NonNull
    private Bitmap rotateByExifIfNeeded(@NonNull Bitmap source, @NonNull Uri uri) {
        int orientation = ExifInterface.ORIENTATION_UNDEFINED;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in != null) {
                ExifInterface exif = new ExifInterface(in);
                orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            }
        } catch (Exception ignored) {
        }

        Matrix matrix = new Matrix();
        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90:
                matrix.postRotate(90);
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                matrix.postRotate(180);
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                matrix.postRotate(270);
                break;
            default:
                return source;
        }
        Bitmap rotated = Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
        if (rotated != source) {
            source.recycle();
        }
        return rotated;
    }

    @NonNull
    private byte[] compressToJpegBytes(@NonNull Bitmap bitmap) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int quality = JPEG_QUALITY_START;
        while (true) {
            baos.reset();
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos);
            if (baos.size() <= TARGET_IMAGE_BYTES || quality <= JPEG_QUALITY_MIN) {
                break;
            }
            quality -= 5;
        }
        return baos.toByteArray();
    }
}
