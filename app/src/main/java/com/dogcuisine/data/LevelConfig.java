package com.dogcuisine.data;

import androidx.annotation.Nullable;

public final class LevelConfig {

    private static final String[] LEVEL_NAMES = new String[]{
            "狗狗学徒",
            "初级狗狗厨师",
            "中级狗狗厨霸",
            "高级狗狗厨王",
            "特级狗狗厨神"
    };
    private static final int[] LEVEL_COUNTS = new int[]{10, 30, 50, 100, 200};

    private LevelConfig() {
    }

    public static int size() {
        return Math.min(LEVEL_NAMES.length, LEVEL_COUNTS.length);
    }

    public static int getHighestReachedIndex(long recipeCount) {
        int reached = -1;
        int size = size();
        for (int i = 0; i < size; i++) {
            if (recipeCount >= LEVEL_COUNTS[i]) {
                reached = i;
            } else {
                break;
            }
        }
        return reached;
    }

    public static int getLevelIndex(@Nullable String name) {
        if (name == null) return -1;
        int size = size();
        for (int i = 0; i < size; i++) {
            if (name.equals(LEVEL_NAMES[i])) {
                return i;
            }
        }
        return -1;
    }

    @Nullable
    public static String getLevelName(int index) {
        int size = size();
        if (index >= 0 && index < size) {
            return LEVEL_NAMES[index];
        }
        return null;
    }

    public static int getRequiredCount(int index) {
        int size = size();
        if (index >= 0 && index < size) {
            return LEVEL_COUNTS[index];
        }
        return Integer.MAX_VALUE;
    }
}
