package com.dogcuisine.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface CategoryDao {

    @Query("SELECT * FROM categories ORDER BY sort_order ASC, id ASC")
    List<CategoryEntity> getAll();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    List<Long> insertAll(List<CategoryEntity> categories);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(CategoryEntity category);

    @Query("SELECT * FROM categories WHERE id = :id LIMIT 1")
    CategoryEntity getById(long id);

    @Query("DELETE FROM categories WHERE id IN (:ids)")
    void deleteByIds(List<Long> ids);

    @Query("SELECT COUNT(*) FROM categories")
    long count();
}
