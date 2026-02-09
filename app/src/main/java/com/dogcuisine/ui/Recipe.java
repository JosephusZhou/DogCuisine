package com.dogcuisine.ui;

import androidx.annotation.Nullable;

public class Recipe {
    private final String name;
    private final String lastEdited;
    @Nullable
    private final String imageUri;

    public Recipe(String name, String lastEdited, @Nullable String imageUri) {
        this.name = name;
        this.lastEdited = lastEdited;
        this.imageUri = imageUri;
    }

    public String getName() {
        return name;
    }

    public String getLastEdited() {
        return lastEdited;
    }

    @Nullable
    public String getImageUri() {
        return imageUri;
    }
}
