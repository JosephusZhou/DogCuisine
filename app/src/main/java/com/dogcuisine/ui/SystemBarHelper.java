package com.dogcuisine.ui;

import android.app.Activity;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

public final class SystemBarHelper {

    private SystemBarHelper() {}

    public static void applyEdgeToEdge(@NonNull Activity activity,
                                       @Nullable View[] topInsetViews,
                                       @Nullable View[] bottomInsetViews) {
        WindowCompat.setDecorFitsSystemWindows(activity.getWindow(), false);
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(activity.getWindow(), activity.getWindow().getDecorView());
        if (controller != null) {
            controller.setAppearanceLightStatusBars(true);
        }
        final View root = activity.findViewById(android.R.id.content);

        final Padding[] topBase = capturePadding(topInsetViews);
        final Padding[] bottomBase = capturePadding(bottomInsetViews);

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            if (topInsetViews != null && topBase != null) {
                for (int i = 0; i < topInsetViews.length; i++) {
                    View view = topInsetViews[i];
                    if (view == null) continue;
                    Padding p = topBase[i];
                    view.setPadding(p.left + systemBars.left,
                            p.top + systemBars.top,
                            p.right + systemBars.right,
                            p.bottom);
                }
            }
            if (bottomInsetViews != null && bottomBase != null) {
                for (int i = 0; i < bottomInsetViews.length; i++) {
                    View view = bottomInsetViews[i];
                    if (view == null) continue;
                    Padding p = bottomBase[i];
                    view.setPadding(p.left + systemBars.left,
                            p.top,
                            p.right + systemBars.right,
                            p.bottom + systemBars.bottom);
                }
            }
            return insets;
        });
    }

    private static Padding[] capturePadding(@Nullable View[] views) {
        if (views == null) return null;
        Padding[] pads = new Padding[views.length];
        for (int i = 0; i < views.length; i++) {
            View v = views[i];
            if (v == null) {
                pads[i] = new Padding(0,0,0,0);
            } else {
                pads[i] = new Padding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), v.getPaddingBottom());
            }
        }
        return pads;
    }

    private static class Padding {
        final int left;
        final int top;
        final int right;
        final int bottom;

        Padding(int left, int top, int right, int bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }
    }
}
