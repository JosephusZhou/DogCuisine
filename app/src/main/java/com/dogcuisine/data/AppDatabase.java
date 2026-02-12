package com.dogcuisine.data;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(entities = {RecipeEntity.class, CategoryEntity.class}, version = 3, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase INSTANCE;

    public abstract RecipeDao recipeDao();
    public abstract CategoryDao categoryDao();

    public static AppDatabase getInstance(@NonNull Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = create(context);
                }
            }
        }
        return INSTANCE;
    }

    @NonNull
    public static synchronized AppDatabase resetInstance(@NonNull Context context) {
        if (INSTANCE != null) {
            INSTANCE.close();
            INSTANCE = null;
        }
        INSTANCE = create(context);
        return INSTANCE;
    }

    @NonNull
    private static AppDatabase create(@NonNull Context context) {
        return Room.databaseBuilder(context.getApplicationContext(),
                        AppDatabase.class, "dogcuisine.db")
                .fallbackToDestructiveMigration()
                .build();
    }
}
