package io.farfrontier.palemirror.frontier.v3.model;

/** Stable symbolic palette; NeoForge chooses the corresponding graybox block states. */
public enum GrayboxMaterial {
    HALL,
    HOUSING,
    FARM,
    WORKSHOP,
    /** Distinct floor tile for the exact immutable workshop input station. */
    WORKSHOP_INPUT,
    /** Distinct floor tile for the exact immutable workshop processing station. */
    WORKSHOP_PROCESS,
    DEPOT,
    INFIRMARY,
    HIVE_GANGLION,
    HIVE_RELAY,
    HIVE_BROOD,
    HIVE_STORE,
    HIVE_DIGESTER,
    HIVE_HIBERNACULUM,
    /** A living occupied cocoon, intentionally distinct from the hibernaculum that houses it. */
    HIVE_COCOON,
    HIVE_MORPHER,
    HIVE_SPORULATOR,
    HIVE_SENSOR,
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
            case WORKSHOP_INPUT -> "minecraft:cyan_concrete";
            case WORKSHOP_PROCESS -> "minecraft:magenta_concrete";
            case DEPOT -> "minecraft:yellow_concrete";
            case INFIRMARY -> "minecraft:pink_concrete";
            case HIVE_GANGLION -> "minecraft:red_concrete";
            case HIVE_RELAY -> "minecraft:pink_concrete";
            case HIVE_BROOD -> "minecraft:purple_concrete";
            case HIVE_STORE -> "minecraft:magenta_concrete";
            case HIVE_DIGESTER -> "minecraft:brown_concrete";
            case HIVE_HIBERNACULUM -> "minecraft:cyan_concrete";
            // Cocoon loss wakes its exact occupant; it is not a generic construction repair.
            case HIVE_COCOON -> throw new IllegalArgumentException("a cocoon is a living hive occupant, not a structural repair material");
            case HIVE_MORPHER -> "minecraft:lime_concrete";
            case HIVE_SPORULATOR -> "minecraft:orange_concrete";
            case HIVE_SENSOR -> "minecraft:light_blue_concrete";
            case ROUTE, ROUTE_FOUNDATION -> "minecraft:gray_concrete";
            case WORKSITE, INFECTION -> throw new IllegalArgumentException("temporary/overlay geometry is not a structural repair material");
        };
    }
}
