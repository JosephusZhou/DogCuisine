package com.dogcuisine.ui;

import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.dogcuisine.R;
import com.dogcuisine.data.CategoryEntity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CategoryManageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface OnItemInteractionListener {
        void onEdit(@NonNull CategoryEntity category, int position);
        void onDelete(@NonNull CategoryEntity category, int position);
        void onAddNew();
        void onStartDrag(@NonNull RecyclerView.ViewHolder holder);
    }

    private static final int TYPE_ITEM = 0;
    private static final int TYPE_ADD = 1;

    private final List<CategoryEntity> data = new ArrayList<>();
    private final OnItemInteractionListener listener;

    public CategoryManageAdapter(@NonNull OnItemInteractionListener listener) {
        this.listener = listener;
    }

    public void setData(List<CategoryEntity> list) {
        data.clear();
        if (list != null) {
            data.addAll(list);
        }
        notifyDataSetChanged();
    }

    public List<CategoryEntity> getData() {
        return data;
    }

    @Override
    public int getItemViewType(int position) {
        if (position == data.size()) {
            return TYPE_ADD;
        }
        return TYPE_ITEM;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_ADD) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_category_manage_add, parent, false);
            return new AddViewHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_category_manage, parent, false);
            return new ItemViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof AddViewHolder) {
            ((AddViewHolder) holder).bind(listener);
        } else if (holder instanceof ItemViewHolder) {
            CategoryEntity entity = data.get(position);
            ((ItemViewHolder) holder).bind(entity, listener);
        }
    }

    @Override
    public int getItemCount() {
        return data.size() + 1; // plus add row
    }

    public void onItemMove(int fromPosition, int toPosition) {
        if (fromPosition < 0 || toPosition < 0) return;
        if (fromPosition >= data.size() || toPosition >= data.size()) return; // exclude add row
        if (fromPosition == toPosition) return;
        Collections.swap(data, fromPosition, toPosition);
        notifyItemMoved(fromPosition, toPosition);
    }

    static class ItemViewHolder extends RecyclerView.ViewHolder {
        final TextView tvName;
        final ImageView ivEdit;
        final ImageView ivDelete;
        final ImageView ivDrag;

        ItemViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvName);
            ivEdit = itemView.findViewById(R.id.ivEdit);
            ivDelete = itemView.findViewById(R.id.ivDelete);
            ivDrag = itemView.findViewById(R.id.ivDrag);
        }

        void bind(CategoryEntity entity, OnItemInteractionListener listener) {
            tvName.setText(entity.getName());
            ivEdit.setOnClickListener(v -> {
                if (listener != null) listener.onEdit(entity, getBindingAdapterPosition());
            });
            ivDelete.setOnClickListener(v -> {
                if (listener != null) listener.onDelete(entity, getBindingAdapterPosition());
            });
            ivDrag.setOnTouchListener((v, event) -> {
                if (event.getAction() == MotionEvent.ACTION_DOWN && listener != null) {
                    listener.onStartDrag(this);
                }
                return false;
            });
        }
    }

    static class AddViewHolder extends RecyclerView.ViewHolder {
        final ImageView ivAdd;

        AddViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAdd = itemView.findViewById(R.id.ivAdd);
        }

        void bind(OnItemInteractionListener listener) {
            itemView.setOnClickListener(v -> {
                if (listener != null) listener.onAddNew();
            });
            ivAdd.setOnClickListener(v -> {
                if (listener != null) listener.onAddNew();
            });
        }
    }
}
