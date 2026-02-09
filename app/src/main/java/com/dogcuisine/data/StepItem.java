package com.dogcuisine.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

public class StepItem {
    @Nullable
    private String text;
    @NonNull
    private final List<String> imagePaths = new ArrayList<>();

    public StepItem() {
    }

    public StepItem(@Nullable String text, @NonNull List<String> images) {
        this.text = text;
        if (images != null) {
            imagePaths.addAll(images);
        }
    }

    @Nullable
    public String getText() {
        return text;
    }

    public void setText(@Nullable String text) {
        this.text = text;
    }

    @NonNull
    public List<String> getImagePaths() {
        return imagePaths;
    }

    public void addImagePath(@NonNull String path) {
        imagePaths.add(path);
    }

    public void addImagePaths(@NonNull List<String> paths) {
        imagePaths.addAll(paths);
    }
}
