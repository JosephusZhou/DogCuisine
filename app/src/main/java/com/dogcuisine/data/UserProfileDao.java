package com.dogcuisine.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

@Dao
public interface UserProfileDao {

    @Query("SELECT COUNT(*) FROM user_profile")
    long count();

    @Query("SELECT * FROM user_profile LIMIT 1")
    UserProfileEntity getProfile();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(UserProfileEntity profile);

    @Query("UPDATE user_profile SET current_level = :levelName WHERE id = :id")
    void updateLevel(long id, String levelName);
}
