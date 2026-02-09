package com.dogcuisine.ui;

import android.app.Dialog;
import android.content.Context;
import android.net.Uri;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.dogcuisine.R;
import com.dogcuisine.data.StepItem;

import java.io.File;
import java.util.Iterator;
import java.util.List;

public class StepAdapter extends RecyclerView.Adapter<StepAdapter.StepViewHolder> {

    public interface StepImageAddListener {
        void onAddImages(int position);
    }

    public interface StepDeleteListener {
        void onDelete(int position);
    }

    private final List<StepItem> steps;
    private final StepImageAddListener imageAddListener;
    private final StepDeleteListener deleteListener;

    public StepAdapter(List<StepItem> steps, StepImageAddListener imageAddListener, StepDeleteListener deleteListener) {
        this.steps = steps;
        this.imageAddListener = imageAddListener;
        this.deleteListener = deleteListener;
    }

    @NonNull
    @Override
    public StepViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_step, parent, false);
        return new StepViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull StepViewHolder holder, int position) {
        StepItem item = steps.get(position);
        holder.title.setText(holder.itemView.getContext().getString(R.string.step_title, position + 1));

        holder.text.removeTextChangedListener(holder.watcher);
        holder.text.setText(item.getText());
        holder.watcher = new SimpleTextWatcher(item);
        holder.text.addTextChangedListener(holder.watcher);

        bindImages(holder, item);

        holder.btnAddImages.setOnClickListener(v -> {
            if (imageAddListener != null) {
                imageAddListener.onAddImages(holder.getBindingAdapterPosition());
            }
        });

        holder.btnDelete.setOnClickListener(v -> {
            if (deleteListener != null) {
                deleteListener.onDelete(holder.getBindingAdapterPosition());
            }
        });
    }

    private void bindImages(@NonNull StepViewHolder holder, StepItem item) {
        holder.imageContainer.removeAllViews();
        List<String> images = item.getImagePaths();
        for (int i = 0; i < images.size(); i++) {
            String path = images.get(i);

            FrameLayout container = new FrameLayout(holder.itemView.getContext());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(160, 160);
            lp.setMargins(8, 0, 8, 0);
            container.setLayoutParams(lp);

            ImageView iv = new ImageView(holder.itemView.getContext());
            FrameLayout.LayoutParams ivLp = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT);
            iv.setLayoutParams(ivLp);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setImageURI(Uri.fromFile(new File(path)));

            ImageButton close = new ImageButton(holder.itemView.getContext());
            FrameLayout.LayoutParams closeLp = new FrameLayout.LayoutParams(48, 48);
            closeLp.gravity = Gravity.END | Gravity.TOP;
            close.setLayoutParams(closeLp);
            close.setBackground(null);
            close.setImageResource(R.drawable.ic_close_gold);
            close.setOnClickListener(v -> removeImage(item, path, holder.getBindingAdapterPosition()));

            container.setOnClickListener(v -> showImageDialog(holder.itemView.getContext(), path));

            container.addView(iv);
            container.addView(close);
            holder.imageContainer.addView(container);
        }
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

    private void removeImage(@NonNull StepItem item, @NonNull String path, int adapterPos) {
        Iterator<String> it = item.getImagePaths().iterator();
        boolean removed = false;
        while (it.hasNext()) {
            String p = it.next();
            if (p.equals(path)) {
                it.remove();
                removed = true;
                break;
            }
        }
        if (removed && adapterPos >= 0) {
            notifyItemChanged(adapterPos);
        }
    }

    @Override
    public int getItemCount() {
        return steps.size();
    }

    static class StepViewHolder extends RecyclerView.ViewHolder {
        final TextView title;
        final EditText text;
        final LinearLayout imageContainer;
        final Button btnAddImages;
        final Button btnDelete;
        TextWatcher watcher;

        StepViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.tvStepTitle);
            text = itemView.findViewById(R.id.etStepText);
            imageContainer = itemView.findViewById(R.id.llImages);
            btnAddImages = itemView.findViewById(R.id.btnAddImages);
            btnDelete = itemView.findViewById(R.id.btnDeleteStep);
        }
    }

    static class SimpleTextWatcher implements TextWatcher {
        private final StepItem item;

        SimpleTextWatcher(StepItem item) {
            this.item = item;
        }

        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
        @Override public void afterTextChanged(Editable s) {
            item.setText(s.toString());
        }
    }
}
