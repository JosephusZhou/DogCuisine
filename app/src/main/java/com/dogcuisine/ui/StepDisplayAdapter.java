package com.dogcuisine.ui;

import android.app.Dialog;
import android.content.Context;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.dogcuisine.R;
import com.dogcuisine.data.StepItem;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class StepDisplayAdapter extends RecyclerView.Adapter<StepDisplayAdapter.StepViewHolder> {

    private final List<StepItem> steps = new ArrayList<>();

    public void setData(List<StepItem> data) {
        steps.clear();
        if (data != null) {
            steps.addAll(data);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public StepViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_step_display, parent, false);
        return new StepViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull StepViewHolder holder, int position) {
        StepItem item = steps.get(position);
        holder.text.setText(item.getText());
        holder.text.setVisibility(item.getText() == null || item.getText().isEmpty() ? View.GONE : View.VISIBLE);

        holder.imageContainer.removeAllViews();
        for (String path : item.getImagePaths()) {
            ImageView iv = new ImageView(holder.itemView.getContext());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            lp.setMargins(0, 0, 0, 12);
            iv.setLayoutParams(lp);
            iv.setAdjustViewBounds(true);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            iv.setImageURI(Uri.fromFile(new File(path)));
            iv.setOnClickListener(v -> showImageDialog(holder.itemView.getContext(), path));
            holder.imageContainer.addView(iv);
        }
        holder.imageContainer.setVisibility(holder.imageContainer.getChildCount() == 0 ? View.GONE : View.VISIBLE);

        holder.title.setText(holder.itemView.getContext().getString(R.string.step_title, position + 1));
    }

    @Override
    public int getItemCount() {
        return steps.size();
    }

    private void showImageDialog(@NonNull Context context, @NonNull String path) {
        Dialog dialog = new Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        ImageView iv = new ImageView(context);
        iv.setImageURI(Uri.fromFile(new File(path)));
        iv.setBackgroundColor(0xCC000000);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        iv.setOnClickListener(v -> dialog.dismiss());
        dialog.setContentView(iv);
        dialog.show();
    }

    static class StepViewHolder extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView text;
        final LinearLayout imageContainer;

        StepViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.tvStepTitleDisplay);
            text = itemView.findViewById(R.id.tvStepTextDisplay);
            imageContainer = itemView.findViewById(R.id.llImagesDisplay);
        }
    }
}
