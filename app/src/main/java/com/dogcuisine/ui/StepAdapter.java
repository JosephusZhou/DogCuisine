package com.dogcuisine.ui;

import android.app.Dialog;
import android.content.Context;
import android.net.Uri;
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
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class StepAdapter extends RecyclerView.Adapter<StepAdapter.StepViewHolder> {

    public interface StepImageAddListener {
        void onAddImages(int position);
    }

    public interface StepDeleteListener {
        void onDelete(int position);
    }

    public interface StepTextClickListener {
        void onStepTextClick(int position, @NonNull String currentText);
    }

    public interface DragStartListener {
        void onDragStart(@NonNull RecyclerView.ViewHolder viewHolder);
    }

    private final List<StepItem> steps;
    private final StepImageAddListener imageAddListener;
    private final StepDeleteListener deleteListener;
    private final StepTextClickListener textClickListener;
    private DragStartListener dragStartListener;

    public StepAdapter(List<StepItem> steps,
                       StepImageAddListener imageAddListener,
                       StepDeleteListener deleteListener,
                       StepTextClickListener textClickListener) {
        this.steps = steps;
        this.imageAddListener = imageAddListener;
        this.deleteListener = deleteListener;
        this.textClickListener = textClickListener;
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

        String currentText = item.getText() == null ? "" : item.getText();
        holder.text.setText(currentText);
        holder.text.setFocusable(false);
        holder.text.setFocusableInTouchMode(false);
        holder.text.setCursorVisible(false);
        holder.text.setOnClickListener(v -> {
            int adapterPos = holder.getBindingAdapterPosition();
            if (adapterPos >= 0 && adapterPos < steps.size() && textClickListener != null) {
                String text = steps.get(adapterPos).getText();
                textClickListener.onStepTextClick(adapterPos, text == null ? "" : text);
            }
        });

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
        
        // 设置拖拽图标的长按监听
        holder.ivDragHandle.setOnLongClickListener(v -> {
            if (dragStartListener != null) {
                int adapterPos = holder.getBindingAdapterPosition();
                if (adapterPos != RecyclerView.NO_POSITION) {
                    dragStartListener.onDragStart(holder);
                }
            }
            return true;
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
            FrameLayout.LayoutParams closeLp = new FrameLayout.LayoutParams(56, 56);
            closeLp.gravity = Gravity.END | Gravity.TOP;
            close.setLayoutParams(closeLp);
            close.setBackground(null);
            close.setImageResource(R.drawable.ic_close_red);
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

    public void moveItem(int fromPosition, int toPosition) {
        if (fromPosition < 0 || fromPosition >= steps.size() || toPosition < 0 || toPosition >= steps.size()) {
            return;
        }
        Collections.swap(steps, fromPosition, toPosition);
        notifyItemMoved(fromPosition, toPosition);
        // 更新所有受影响项的步骤编号
        int start = Math.min(fromPosition, toPosition);
        int end = Math.max(fromPosition, toPosition);
        notifyItemRangeChanged(start, end - start + 1);
    }

    public void setDragStartListener(DragStartListener listener) {
        this.dragStartListener = listener;
    }

    static class StepViewHolder extends RecyclerView.ViewHolder {
        final TextView title;
        final EditText text;
        final LinearLayout imageContainer;
        final Button btnAddImages;
        final Button btnDelete;
        final ImageView ivDragHandle;

        StepViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.tvStepTitle);
            text = itemView.findViewById(R.id.etStepText);
            imageContainer = itemView.findViewById(R.id.llImages);
            btnAddImages = itemView.findViewById(R.id.btnAddImages);
            btnDelete = itemView.findViewById(R.id.btnDeleteStep);
            ivDragHandle = itemView.findViewById(R.id.ivDragHandle);
        }
    }
}
