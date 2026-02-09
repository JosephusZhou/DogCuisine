package com.dogcuisine.ui;

import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.dogcuisine.R;
import com.dogcuisine.data.RecipeEntity;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public class RecipeAdapter extends RecyclerView.Adapter<RecipeAdapter.RecipeViewHolder> {

    private final List<RecipeEntity> recipes;
    private final SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA);

    public interface OnRecipeClickListener {
        void onClick(@NonNull RecipeEntity recipe);
    }

    public interface OnRecipeLongClickListener {
        void onLongClick(@NonNull RecipeEntity recipe);
    }

    private final OnRecipeClickListener listener;
    private final OnRecipeLongClickListener longClickListener;

    public RecipeAdapter(@NonNull List<RecipeEntity> recipes, OnRecipeClickListener listener, OnRecipeLongClickListener longClickListener) {
        this.recipes = recipes;
        this.listener = listener;
        this.longClickListener = longClickListener;
    }

    public void setRecipes(List<RecipeEntity> newData) {
        recipes.clear();
        if (newData != null) {
            recipes.addAll(newData);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public RecipeViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_recipe, parent, false);
        return new RecipeViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecipeViewHolder holder, int position) {
        RecipeEntity recipe = recipes.get(position);
        holder.nameText.setText(recipe.getName());
        holder.lastEditedText.setText(formatter.format(recipe.getUpdatedAt()));

        String imagePath = recipe.getCoverImagePath();
        if (imagePath != null && !imagePath.isEmpty()) {
            try {
                holder.thumbnail.setImageURI(Uri.fromFile(new File(imagePath)));
            } catch (Exception e) {
                holder.thumbnail.setImageResource(R.drawable.ic_launcher_foreground);
            }
        } else {
            holder.thumbnail.setImageResource(R.drawable.ic_launcher_foreground);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onClick(recipe);
            }
        });
        holder.itemView.setOnLongClickListener(v -> {
            if (longClickListener != null) {
                longClickListener.onLongClick(recipe);
                return true;
            }
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return recipes.size();
    }

    static class RecipeViewHolder extends RecyclerView.ViewHolder {
        final ImageView thumbnail;
        final TextView nameText;
        final TextView lastEditedText;

        RecipeViewHolder(@NonNull View itemView) {
            super(itemView);
            thumbnail = itemView.findViewById(R.id.ivThumbnail);
            nameText = itemView.findViewById(R.id.tvName);
            lastEditedText = itemView.findViewById(R.id.tvLastEdited);
        }
    }
}
