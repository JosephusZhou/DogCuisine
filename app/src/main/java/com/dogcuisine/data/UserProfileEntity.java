package com.dogcuisine.data;

import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "user_profile")
public class UserProfileEntity {

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    private long id;

    @Nullable
    @ColumnInfo(name = "current_level")
    private String currentLevel;

    public UserProfileEntity(long id, @Nullable String currentLevel) {
        this.id = id;
        this.currentLevel = currentLevel;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    @Nullable
    public String getCurrentLevel() {
        return currentLevel;
    }

    public void setCurrentLevel(@Nullable String currentLevel) {
        this.currentLevel = currentLevel;
    }
}
