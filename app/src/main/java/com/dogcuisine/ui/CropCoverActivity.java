package com.dogcuisine.ui;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.dogcuisine.R;
import com.google.android.material.appbar.MaterialToolbar;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class CropCoverActivity extends AppCompatActivity {

    public static final String EXTRA_SOURCE_URI = "source_uri";
    public static final String EXTRA_CROPPED_URI = "cropped_uri";

    private static final int MENU_ID_DONE = 4001;
    private static final int OUTPUT_SIZE = 1080;

    private ImageView ivCrop;
    private CropMaskView cropMaskView;
    private Bitmap bitmap;
    private final Matrix matrix = new Matrix();
    private ScaleGestureDetector scaleDetector;
    private float minScale = 1f;
    private float maxScale = 5f;
    private float currentScale = 1f;
    private float lastX;
    private float lastY;
    private boolean dragging;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cover_crop);

        MaterialToolbar toolbar = findViewById(R.id.toolbarCrop);
        toolbar.setTitle("裁剪封面");
        toolbar.setTitleTextColor(ContextCompat.getColor(this, R.color.teal_200));
        toolbar.setNavigationIcon(R.drawable.ic_back_gold);
        toolbar.setNavigationOnClickListener(v -> finish());
        Menu menu = toolbar.getMenu();
        menu.clear();
        MenuItem done = menu.add(Menu.NONE, MENU_ID_DONE, Menu.NONE, "完成");
        done.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == MENU_ID_DONE) {
                finishCrop();
                return true;
            }
            return false;
        });

        View cropContainer = findViewById(R.id.flCropContainer);
        ivCrop = findViewById(R.id.ivCropCover);
        cropMaskView = findViewById(R.id.viewCropMask);
        SystemBarHelper.applyEdgeToEdge(this, new View[]{toolbar}, new View[]{cropContainer});

        String raw = getIntent().getStringExtra(EXTRA_SOURCE_URI);
        if (raw == null || raw.trim().isEmpty()) {
            Toast.makeText(this, "图片无效", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        Uri uri = Uri.parse(raw);
        bitmap = loadBitmap(uri);
        if (bitmap == null) {
            Toast.makeText(this, "读取图片失败", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        ivCrop.setImageBitmap(bitmap);
        scaleDetector = new ScaleGestureDetector(this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                float target = currentScale * detector.getScaleFactor();
                float clamped = Math.max(minScale, Math.min(maxScale, target));
                float factor = clamped / currentScale;
                matrix.postScale(factor, factor, detector.getFocusX(), detector.getFocusY());
                currentScale = clamped;
                fixBounds();
                applyMatrix();
                return true;
            }
        });

        cropContainer.post(this::initMatrix);
        ivCrop.setOnTouchListener(this::onTouchCrop);
    }

    private boolean onTouchCrop(View v, MotionEvent event) {
        scaleDetector.onTouchEvent(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastX = event.getX();
                lastY = event.getY();
                dragging = true;
                break;
            case MotionEvent.ACTION_MOVE:
                if (dragging && event.getPointerCount() == 1 && !scaleDetector.isInProgress()) {
                    float dx = event.getX() - lastX;
                    float dy = event.getY() - lastY;
                    matrix.postTranslate(dx, dy);
                    fixBounds();
                    applyMatrix();
                    lastX = event.getX();
                    lastY = event.getY();
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                break;
            default:
                break;
        }
        return true;
    }

    private void initMatrix() {
        if (bitmap == null || cropMaskView == null) return;
        float vw = ivCrop.getWidth();
        float vh = ivCrop.getHeight();
        if (vw <= 0 || vh <= 0) return;
        RectF cropRect = cropMaskView.getCropRect();
        if (cropRect.width() <= 0 || cropRect.height() <= 0) return;
        float bw = bitmap.getWidth();
        float bh = bitmap.getHeight();
        matrix.reset();
        // 默认优先按裁剪框高度贴合；若宽度不足，再按宽度补齐，保证裁剪框内始终有图。
        minScale = cropRect.height() / bh;
        minScale = Math.max(minScale, cropRect.width() / bw);
        maxScale = minScale * 8f;
        currentScale = minScale;
        float dx = (vw - bw * minScale) * 0.5f;
        float dy = (vh - bh * minScale) * 0.5f;
        matrix.postScale(minScale, minScale);
        matrix.postTranslate(dx, dy);
        fixBounds();
        applyMatrix();
    }

    private void fixBounds() {
        if (bitmap == null || cropMaskView == null) return;
        RectF rect = getBitmapRect();
        RectF cropRect = cropMaskView.getCropRect();
        float dx = 0f;
        float dy = 0f;
        if (rect.left > cropRect.left) dx = cropRect.left - rect.left;
        if (rect.right < cropRect.right) dx = cropRect.right - rect.right;
        if (rect.top > cropRect.top) dy = cropRect.top - rect.top;
        if (rect.bottom < cropRect.bottom) dy = cropRect.bottom - rect.bottom;
        matrix.postTranslate(dx, dy);
    }

    @NonNull
    private RectF getBitmapRect() {
        RectF rect = new RectF(0, 0, bitmap.getWidth(), bitmap.getHeight());
        matrix.mapRect(rect);
        return rect;
    }

    private void applyMatrix() {
        ivCrop.setImageMatrix(matrix);
    }

    private void finishCrop() {
        if (bitmap == null || cropMaskView == null) {
            finish();
            return;
        }
        try {
            RectF cropRect = cropMaskView.getCropRect();
            if (cropRect.width() <= 0 || cropRect.height() <= 0) {
                Toast.makeText(this, "裁剪失败", Toast.LENGTH_SHORT).show();
                return;
            }
            Bitmap out = Bitmap.createBitmap(OUTPUT_SIZE, OUTPUT_SIZE, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(out);
            canvas.drawColor(Color.BLACK);
            Matrix outMatrix = new Matrix(matrix);
            outMatrix.postTranslate(-cropRect.left, -cropRect.top);
            float factor = OUTPUT_SIZE / cropRect.width();
            outMatrix.postScale(factor, factor);
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            canvas.drawBitmap(bitmap, outMatrix, paint);

            File dir = new File(getCacheDir(), "crop");
            if (!dir.exists()) dir.mkdirs();
            File file = new File(dir, "cover_crop_" + System.currentTimeMillis() + ".jpg");
            try (FileOutputStream fos = new FileOutputStream(file)) {
                out.compress(Bitmap.CompressFormat.JPEG, 95, fos);
                fos.flush();
            }
            out.recycle();

            Uri uri = Uri.fromFile(file);
            getIntent().putExtra(EXTRA_CROPPED_URI, uri.toString());
            setResult(RESULT_OK, getIntent());
            finish();
        } catch (Exception e) {
            Toast.makeText(this, "裁剪失败", Toast.LENGTH_SHORT).show();
        }
    }

    @Nullable
    private Bitmap loadBitmap(@NonNull Uri uri) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                if (in == null) return null;
                BitmapFactory.decodeStream(in, null, bounds);
            }
            int inSample = 1;
            int maxSide = Math.max(bounds.outWidth, bounds.outHeight);
            while (maxSide / inSample > 2048) {
                inSample *= 2;
            }
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            options.inSampleSize = Math.max(1, inSample);
            Bitmap src;
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                if (in == null) return null;
                src = BitmapFactory.decodeStream(in, null, options);
            }
            if (src == null) return null;
            return rotateByExifIfNeeded(src, uri);
        } catch (Exception e) {
            return null;
        }
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

        Matrix rotate = new Matrix();
        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90:
                rotate.postRotate(90);
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                rotate.postRotate(180);
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                rotate.postRotate(270);
                break;
            default:
                return source;
        }
        Bitmap out = Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), rotate, true);
        if (out != source) source.recycle();
        return out;
    }

    @Override
    protected void onDestroy() {
        if (bitmap != null && !bitmap.isRecycled()) {
            bitmap.recycle();
            bitmap = null;
        }
        super.onDestroy();
    }
}
