package io.farfrontier.palemirror.frontier.v3.model;

/** Stable symbolic palette; NeoForge chooses the corresponding graybox block states. */
public enum GrayboxMaterial {
    HALL,
    HOUSING,
    FARM,
    WORKSHOP,
    DEPOT,
    INFIRMARY,
    HIVE_HEART,
    HIVE_BROOD,
    HIVE_STORE,
    ROUTE,
    ROUTE_FOUNDATION,
    WORKSITE,
    INFECTION;

    /** One repair unit is one ordinary Minecraft concrete item, never an aggregate resource. */
    public String repairItemKind() {
        return switch (this) {
            case HALL -> "minecraft:white_concrete";
            case HOUSING -> "minecraft:orange_concrete";
            case FARM -> "minecraft:lime_concrete";
            case WORKSHOP -> "minecraft:blue_concrete";
            case DEPOT -> "minecraft:yellow_concrete";
            case INFIRMARY -> "minecraft:pink_concrete";
            case HIVE_HEART -> "minecraft:red_concrete";
            case HIVE_BROOD -> "minecraft:purple_concrete";
            case HIVE_STORE -> "minecraft:magenta_concrete";
            case ROUTE, ROUTE_FOUNDATION -> "minecraft:gray_concrete";
            case WORKSITE, INFECTION -> throw new IllegalArgumentException("temporary/overlay geometry is not a structural repair material");
        };
    }
}
