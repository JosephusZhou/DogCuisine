package com.dogcuisine.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class CropMaskView extends View {

    private final RectF cropRect = new RectF();
    private final Paint maskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final float horizontalMarginPx;
    private final float minVerticalMarginPx;

    public CropMaskView(Context context) {
        this(context, null);
    }

    public CropMaskView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CropMaskView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        maskPaint.setColor(0x88000000);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setColor(Color.WHITE);
        borderPaint.setStrokeWidth(dp(2f));
        horizontalMarginPx = dp(24f);
        minVerticalMarginPx = dp(24f);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateCropRect(w, h);
    }

    private void updateCropRect(int width, int height) {
        float side = width - horizontalMarginPx * 2f;
        float maxByHeight = height - minVerticalMarginPx * 2f;
        if (maxByHeight > 0f) {
            side = Math.min(side, maxByHeight);
        }
        side = Math.max(0f, side);
        float left = (width - side) * 0.5f;
        float top = (height - side) * 0.5f;
        cropRect.set(left, top, left + side, top + side);
        invalidate();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();

        canvas.drawRect(0f, 0f, w, cropRect.top, maskPaint);
        canvas.drawRect(0f, cropRect.bottom, w, h, maskPaint);
        canvas.drawRect(0f, cropRect.top, cropRect.left, cropRect.bottom, maskPaint);
        canvas.drawRect(cropRect.right, cropRect.top, w, cropRect.bottom, maskPaint);
        canvas.drawRect(cropRect, borderPaint);
    }

    @NonNull
    public RectF getCropRect() {
        return new RectF(cropRect);
    }

    private float dp(float value) {
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                getResources().getDisplayMetrics()
        );
    }
}
