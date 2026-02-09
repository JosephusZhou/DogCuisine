package com.dogcuisine.data;

import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "recipes")
public class RecipeEntity {

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    private Long id;

    @ColumnInfo(name = "name")
    private String name;

    @ColumnInfo(name = "created_at")
    private long createdAt;

    @ColumnInfo(name = "updated_at")
    private long updatedAt;

    // 图文内容，存储大文本
    @ColumnInfo(name = "content", typeAffinity = ColumnInfo.TEXT)
    private String content;

    // 菜谱封面（本地路径）
    @ColumnInfo(name = "cover_image_path")
    @Nullable
    private String coverImagePath;

    // 步骤列表序列化（JSON），包含文字与多图
    @ColumnInfo(name = "steps_json", typeAffinity = ColumnInfo.TEXT)
    private String stepsJson;

    public RecipeEntity(@Nullable Long id, String name, long createdAt, long updatedAt, String content, @Nullable String coverImagePath, String stepsJson) {
        this.id = id;
        this.name = name;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.content = content;
        this.coverImagePath = coverImagePath;
        this.stepsJson = stepsJson;
    }

    @Nullable
    public Long getId() {
        return id;
    }

    public void setId(@Nullable Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    @Nullable
    public String getCoverImagePath() {
        return coverImagePath;
    }

    public void setCoverImagePath(@Nullable String coverImagePath) {
        this.coverImagePath = coverImagePath;
    }

    public String getStepsJson() {
        return stepsJson;
    }

    public void setStepsJson(String stepsJson) {
        this.stepsJson = stepsJson;
    }
}
