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

    @Query("SELECT COUNT(*) FROM recipes WHERE category_id = :categoryId")
    long countByCategoryId(Long categoryId);

    @Query("SELECT * FROM recipes WHERE name LIKE :keyword OR content LIKE :keyword OR ingredient_json LIKE :keyword ORDER BY updated_at DESC")
    List<RecipeEntity> searchByKeyword(String keyword);

    @Query("SELECT * FROM recipes WHERE is_favorite = 1 ORDER BY updated_at DESC")
    List<RecipeEntity> getFavorites();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<RecipeEntity> recipes);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(RecipeEntity recipe);

    @Query("DELETE FROM recipes WHERE id = :id")
    void deleteById(long id);

    @Query("UPDATE recipes SET is_favorite = :favorite WHERE id = :id")
    void updateFavorite(long id, int favorite);

    @Query("SELECT COUNT(*) FROM recipes")
    long count();
}
