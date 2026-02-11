package com.dogcuisine.ui;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.dogcuisine.R;
import com.dogcuisine.data.CategoryEntity;

import java.util.List;

public class CategoryAdapter extends RecyclerView.Adapter<CategoryAdapter.CategoryViewHolder> {

    public interface OnCategoryClickListener {
        void onClick(@NonNull CategoryEntity category);
    }

    private final List<CategoryEntity> categories;
    private final OnCategoryClickListener listener;
    private Long selectedCategoryId;

    public CategoryAdapter(@NonNull List<CategoryEntity> categories, @NonNull OnCategoryClickListener listener) {
        this.categories = categories;
        this.listener = listener;
    }

    public void setCategories(List<CategoryEntity> newData) {
        categories.clear();
        if (newData != null) {
            categories.addAll(newData);
        }
        notifyDataSetChanged();
    }

    public void setSelectedCategoryId(Long selectedCategoryId) {
        this.selectedCategoryId = selectedCategoryId;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public CategoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_category, parent, false);
        return new CategoryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CategoryViewHolder holder, int position) {
        CategoryEntity category = categories.get(position);
        holder.nameText.setText(category.getName());

        boolean selected = selectedCategoryId != null && selectedCategoryId.equals(category.getId());
        int selectedColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.teal_200);
        int defaultColor = Color.TRANSPARENT;
        holder.itemView.setBackgroundColor(selected ? selectedColor : defaultColor);
        holder.nameText.setTextColor(selected ? Color.WHITE : Color.parseColor("#222222"));

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onClick(category);
            }
        });
    }

    @Override
    public int getItemCount() {
        return categories.size();
    }

    static class CategoryViewHolder extends RecyclerView.ViewHolder {
        final TextView nameText;

        CategoryViewHolder(@NonNull View itemView) {
            super(itemView);
            nameText = itemView.findViewById(R.id.tvCategoryName);
        }
    }
}
