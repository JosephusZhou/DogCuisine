package com.dogcuisine.data;

public final class LevelConfig {

    private static final int[] LEVEL_COUNTS = new int[]{10, 200, 300, 500, 800, 1000};

    private LevelConfig() {
    }

    public static int size() {
        return LEVEL_COUNTS.length;
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

    public static int getRequiredCount(int index) {
        int size = size();
        if (index >= 0 && index < size) {
            return LEVEL_COUNTS[index];
        }
        return Integer.MAX_VALUE;
    }
}
