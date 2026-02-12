package com.dogcuisine.data;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(entities = {RecipeEntity.class, CategoryEntity.class}, version = 4, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase INSTANCE;
    private static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE recipes ADD COLUMN ingredient_json TEXT NOT NULL DEFAULT ''");
        }
    };

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
                .addMigrations(MIGRATION_3_4)
                .build();
    }
}
