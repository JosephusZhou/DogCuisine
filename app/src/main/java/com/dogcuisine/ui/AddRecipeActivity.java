package com.dogcuisine.ui;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
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
import com.dogcuisine.data.CategoryDao;
import com.dogcuisine.data.CategoryEntity;
import com.dogcuisine.data.RecipeDao;
import com.dogcuisine.data.RecipeEntity;
import com.dogcuisine.data.StepItem;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.gson.Gson;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

public class AddRecipeActivity extends AppCompatActivity implements StepAdapter.StepImageAddListener, StepAdapter.StepDeleteListener, StepAdapter.StepTextClickListener {

    public static final String EXTRA_RECIPE_ID = "recipe_id";
    private static final int MENU_ID_SAVE = 2001;
    private static final int MAX_IMAGE_LONG_EDGE = 1600;
    private static final int TARGET_IMAGE_BYTES = 600 * 1024;
    private static final int JPEG_QUALITY_START = 85;
    private static final int JPEG_QUALITY_MIN = 65;

    private ImageView ivCover;
    private TextView tvCoverHint;
    private EditText etName;
    private EditText etIngredientText;
    private Spinner spCategory;
    private NestedScrollView nsvAddRecipe;
    private RecyclerView rvSteps;
    private Button btnAddStep;
    private Button btnAddIngredientImages;
    private LinearLayout llIngredientImages;
    private StepAdapter stepAdapter;
    private final StepItem ingredient = new StepItem();
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
    private ActivityResultLauncher<android.content.Intent> coverCropLauncher;
    private ActivityResultLauncher<String[]> ingredientImagesPicker;
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
        etIngredientText = findViewById(R.id.etIngredientText);
        spCategory = findViewById(R.id.spCategory);
        nsvAddRecipe = findViewById(R.id.nsvAddRecipe);
        rvSteps = findViewById(R.id.rvSteps);
        btnAddStep = findViewById(R.id.btnAddStep);
        btnAddIngredientImages = findViewById(R.id.btnAddIngredientImages);
        llIngredientImages = findViewById(R.id.llIngredientImages);

        setupCategorySpinner();

        rvSteps.setLayoutManager(new LinearLayoutManager(this));
        rvSteps.setNestedScrollingEnabled(false);
        steps.add(new StepItem());
        stepAdapter = new StepAdapter(steps, this, this, this);
        rvSteps.setAdapter(stepAdapter);

        FrameLayout flCover = findViewById(R.id.flCover);
        flCover.setOnClickListener(v -> pickCover());
        btnAddStep.setOnClickListener(v -> addStep());
        configureInlineTextProxy(etIngredientText, () ->
                showBottomTextEditor("编辑食材", etIngredientText.getText().toString(), text -> {
                    ingredient.setText(text);
                    etIngredientText.setText(text);
                }));
        btnAddIngredientImages.setOnClickListener(v -> {
            if (ingredientImagesPicker != null) {
                ingredientImagesPicker.launch(new String[]{"image/*"});
            }
        });

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
                startCoverCrop(uri);
            }
        });

        coverCropLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() != RESULT_OK || result.getData() == null) {
                return;
            }
            String raw = result.getData().getStringExtra(CropCoverActivity.EXTRA_CROPPED_URI);
            if (raw == null || raw.isEmpty()) {
                return;
            }
            Uri uri = Uri.parse(raw);
            String path = copyAndCompressCroppedCover(uri);
            if (path != null) {
                coverPath = path;
                ivCover.setImageURI(Uri.fromFile(new File(path)));
                tvCoverHint.setText("");
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

        ingredientImagesPicker = registerForActivityResult(new ActivityResultContracts.OpenMultipleDocuments(), uris -> {
            if (uris == null || uris.isEmpty()) {
                return;
            }
            List<String> paths = new ArrayList<>();
            for (Uri uri : uris) {
                String p = copyAndCompressImage(uri, "ingredient");
                if (p != null) paths.add(p);
            }
            ingredient.addImagePaths(paths);
            bindIngredientImages();
        });
    }

    private void pickCover() {
        coverPicker.launch(new String[]{"image/*"});
    }

    private void startCoverCrop(@NonNull Uri sourceUri) {
        android.content.Intent intent = new android.content.Intent(this, CropCoverActivity.class);
        intent.putExtra(CropCoverActivity.EXTRA_SOURCE_URI, sourceUri.toString());
        coverCropLauncher.launch(intent);
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

    private void configureInlineTextProxy(@NonNull EditText editText, @NonNull Runnable onClick) {
        editText.setFocusable(false);
        editText.setFocusableInTouchMode(false);
        editText.setCursorVisible(false);
        editText.setLongClickable(false);
        editText.setTextIsSelectable(false);
        editText.setOnClickListener(v -> onClick.run());
    }

    @Override
    public void onStepTextClick(int position, @NonNull String currentText) {
        showBottomTextEditor("编辑步骤", currentText, text -> {
            if (position < 0 || position >= steps.size()) return;
            steps.get(position).setText(text);
            stepAdapter.notifyItemChanged(position);
        });
    }

    private void showBottomTextEditor(@NonNull String title,
                                      @Nullable String initialText,
                                      @NonNull TextConfirmCallback callback) {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View content = getLayoutInflater().inflate(R.layout.dialog_bottom_text_editor, null, false);
        TextView tvTitle = content.findViewById(R.id.tvBottomEditorTitle);
        EditText etInput = content.findViewById(R.id.etBottomEditorInput);
        Button btnCancel = content.findViewById(R.id.btnBottomEditorCancel);
        Button btnConfirm = content.findViewById(R.id.btnBottomEditorConfirm);

        tvTitle.setText(title);
        etInput.setText(initialText == null ? "" : initialText);
        etInput.setSelection(etInput.getText().length());

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnConfirm.setOnClickListener(v -> {
            callback.onConfirm(etInput.getText() == null ? "" : etInput.getText().toString());
            dialog.dismiss();
        });

        dialog.setContentView(content);
        dialog.setOnShowListener(d -> {
            Window window = dialog.getWindow();
            if (window != null) {
                window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                        | WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
            }
            FrameLayout bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                BottomSheetBehavior<FrameLayout> behavior = BottomSheetBehavior.from(bottomSheet);
                behavior.setSkipCollapsed(true);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                behavior.setDraggable(false);
            }
            etInput.post(() -> {
                etInput.setFocusableInTouchMode(true);
                etInput.requestFocus();
                android.view.inputmethod.InputMethodManager imm =
                        (android.view.inputmethod.InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showSoftInput(etInput, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
                }
            });
            etInput.postDelayed(() -> {
                if (!etInput.hasFocus()) {
                    etInput.requestFocus();
                }
                android.view.inputmethod.InputMethodManager imm =
                        (android.view.inputmethod.InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showSoftInput(etInput, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
                }
            }, 80);
        });
        dialog.show();
    }

    private interface TextConfirmCallback {
        void onConfirm(@NonNull String text);
    }

    private void saveRecipe() {
        String name = etName.getText().toString().trim();
        if (name.isEmpty()) {
            Toast.makeText(this, "请输入菜谱名称", Toast.LENGTH_SHORT).show();
            return;
        }

        final long now = System.currentTimeMillis();
        ingredient.setText(etIngredientText.getText().toString());
        final String ingredientJson = gson.toJson(ingredient);
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
                entity = new RecipeEntity(editingIdSnapshot, name, existingCreatedAtSnapshot > 0 ? existingCreatedAtSnapshot : now, now, "", coverPathSnapshot, stepsJson, ingredientJson, categoryId);
            } else {
                entity = new RecipeEntity(null, name, now, now, "", coverPathSnapshot, stepsJson, ingredientJson, categoryId);
            }
            recipeDao.insert(entity);
            runOnUiThread(() -> {
                App.getInstance().requestAutoWebDavUploadIfConfigured();
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
                StepItem loadedIngredient = parseIngredient(entity.getIngredientJson());
                ingredient.setText(loadedIngredient.getText());
                ingredient.getImagePaths().clear();
                ingredient.addImagePaths(new ArrayList<>(loadedIngredient.getImagePaths()));
                etIngredientText.setText(ingredient.getText());
                bindIngredientImages();
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

    private void bindIngredientImages() {
        if (llIngredientImages == null) return;
        llIngredientImages.removeAllViews();
        List<String> imagePaths = ingredient.getImagePaths();
        if (imagePaths == null || imagePaths.isEmpty()) {
            return;
        }
        for (String path : imagePaths) {
            ImageView iv = new ImageView(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(160, 160);
            lp.setMargins(8, 0, 8, 0);
            iv.setLayoutParams(lp);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setImageURI(Uri.fromFile(new File(path)));
            iv.setOnClickListener(v -> showImageDialog(path));
            llIngredientImages.addView(iv);
        }
    }

    private void showImageDialog(@NonNull String path) {
        Dialog dialog = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        ImageView iv = new ImageView(this);
        iv.setImageURI(Uri.fromFile(new File(path)));
        iv.setBackgroundColor(0xCC000000);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        iv.setOnClickListener(v -> dialog.dismiss());
        dialog.setContentView(iv);
        dialog.show();
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

    @NonNull
    private StepItem parseIngredient(@Nullable String json) {
        if (json == null || json.isEmpty()) return new StepItem();
        try {
            StepItem item = gson.fromJson(json, StepItem.class);
            return item != null ? item : new StepItem();
        } catch (Exception e) {
            return new StepItem();
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
    private String copyAndCompressCroppedCover(@NonNull Uri croppedUri) {
        try {
            Bitmap bitmap = decodeBitmapWithSample(croppedUri, MAX_IMAGE_LONG_EDGE);
            if (bitmap == null) return null;
            Bitmap square = cropCenterSquareIfNeeded(bitmap);
            Bitmap scaled = scaleBitmapIfNeeded(square, MAX_IMAGE_LONG_EDGE);
            byte[] compressed = compressToJpegBytes(scaled);

            File dir = new File(getFilesDir(), "images");
            if (!dir.exists()) dir.mkdirs();
            String fileName = "cover_" + System.currentTimeMillis() + ".jpg";
            File outFile = new File(dir, fileName);
            try (FileOutputStream fos = new FileOutputStream(outFile)) {
                fos.write(compressed);
                fos.flush();
            }

            if (!scaled.isRecycled()) scaled.recycle();
            if (square != scaled && !square.isRecycled()) square.recycle();
            if (bitmap != square && bitmap != scaled && !bitmap.isRecycled()) bitmap.recycle();
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

    @NonNull
    private Bitmap cropCenterSquareIfNeeded(@NonNull Bitmap source) {
        int width = source.getWidth();
        int height = source.getHeight();
        if (width <= 0 || height <= 0 || width == height) {
            return source;
        }
        int side = Math.min(width, height);
        int left = (width - side) / 2;
        int top = (height - side) / 2;
        Bitmap square = Bitmap.createBitmap(source, left, top, side, side);
        if (square != source) {
            source.recycle();
        }
        return square;
    }
}
