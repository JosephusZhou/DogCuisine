package com.dogcuisine.ui;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.AnimatedImageDrawable;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.NonNull;

import com.dogcuisine.R;

public final class LoadingDialogHelper {

    private final Context context;
    private Dialog loadingDialog;
    private AnimatedImageDrawable loadingDrawable;

    public LoadingDialogHelper(@NonNull Context context) {
        this.context = context;
    }

    public void show() {
        if (loadingDialog != null && loadingDialog.isShowing()) {
            return;
        }
        loadingDialog = new Dialog(context);
        loadingDialog.setContentView(R.layout.dialog_sync_loading);
        loadingDialog.setCancelable(false);
        loadingDialog.setCanceledOnTouchOutside(false);
        if (loadingDialog.getWindow() != null) {
            loadingDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        View ivAnim = loadingDialog.findViewById(R.id.ivSyncLoadingAnim);
        if (ivAnim instanceof ImageView) {
            ImageView imageView = (ImageView) ivAnim;
            imageView.setImageResource(pickRandomLoadingDrawable());
            if (imageView.getDrawable() instanceof AnimatedImageDrawable) {
                loadingDrawable = (AnimatedImageDrawable) imageView.getDrawable();
                loadingDrawable.setRepeatCount(AnimatedImageDrawable.REPEAT_INFINITE);
                loadingDrawable.start();
            }
        }
        loadingDialog.show();
    }

    public void dismiss() {
        if (loadingDialog == null) {
            return;
        }
        if (loadingDrawable != null) {
            loadingDrawable.stop();
            loadingDrawable = null;
        }
        if (loadingDialog.isShowing()) {
            loadingDialog.dismiss();
        }
        loadingDialog = null;
    }

    private int pickRandomLoadingDrawable() {
        return Math.random() < 0.5
                ? R.drawable.sync_loading_dog
                : R.drawable.sync_loading_dog2;
    }
}
