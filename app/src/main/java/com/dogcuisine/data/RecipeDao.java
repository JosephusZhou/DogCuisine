package com.dogcuisine.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface RecipeDao {

    @Query("SELECT * FROM recipes ORDER BY updated_at DESC")
    List<RecipeEntity> getAll();

    @Query("SELECT * FROM recipes WHERE ((:categoryId IS NULL AND category_id IS NULL) OR category_id = :categoryId) ORDER BY updated_at DESC")
    List<RecipeEntity> getByCategoryId(Long categoryId);

    @Query("SELECT * FROM recipes WHERE id = :id LIMIT 1")
    RecipeEntity getById(long id);

    @Query("SELECT * FROM recipes WHERE name LIKE :keyword OR content LIKE :keyword ORDER BY updated_at DESC")
    List<RecipeEntity> searchByKeyword(String keyword);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<RecipeEntity> recipes);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(RecipeEntity recipe);

    @Query("DELETE FROM recipes WHERE id = :id")
    void deleteById(long id);

    @Query("SELECT COUNT(*) FROM recipes")
    long count();
}
