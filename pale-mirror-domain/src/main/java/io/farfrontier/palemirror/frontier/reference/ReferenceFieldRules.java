package io.farfrontier.palemirror.frontier.reference;

/** Source-pinned {@code balance.strategy.field_warfare} rules used before operation execution. */
final class ReferenceFieldRules {
    static final int MAX_CAMPAIGNS_PER_SETTLEMENT = 1;
    static final int MAX_ACTIVE_CAMPAIGNS_WORLD = 2;
    static final double POST_INTERACTION_RADIUS = 1.25d;
    static final double SETTLEMENT_SUPPORT_RADIUS = 6.0d;
    static final double AMMO_PER_GARRISON = 1.2d;
    static final double ISOLATION_POWER_LOSS = 0.10d;
    static final double POST_MAXIMUM_INFECTION = 0.42d;
    static final double MINIMUM_POST_SPACING = 4.0d;
    static final int CAMPAIGN_RETRY_DAYS = 45;
    static final double CHECKPOINT_ROUTE_RADIUS = 2.0d;
    static final double CHECKPOINT_INFECTION_MULTIPLIER = 0.68d;
    static final double CHECKPOINT_CAPACITY_MULTIPLIER = 0.84d;

    private ReferenceFieldRules() { }

    static double postIntegrity(ReferenceFieldPostKind kind) {
        return switch (kind) {
            case OBSERVATION -> 42.0d;
            case CHECKPOINT -> 78.0d;
            case STRONGPOINT -> 135.0d;
            case FORWARD_BASE -> 105.0d;
        };
    }

    static int postBuildDays(ReferenceFieldPostKind kind) {
        return switch (kind) {
            case OBSERVATION -> 2;
            case CHECKPOINT -> 3;
            case STRONGPOINT, FORWARD_BASE -> 5;
        };
    }

    static int moduleSlots(ReferenceFieldPostKind kind) {
        return switch (kind) {
            case OBSERVATION, CHECKPOINT -> 1;
            case STRONGPOINT -> 2;
            case FORWARD_BASE -> 4;
        };
    }

    static double garrisonPower(ReferenceFieldPostKind kind) {
        return switch (kind) {
            case OBSERVATION -> 0.55d;
            case CHECKPOINT -> 0.82d;
            case STRONGPOINT -> 1.18d;
            case FORWARD_BASE -> 0.98d;
        };
    }

    static double storageCapacity(ReferenceFieldPostKind kind) {
        return switch (kind) {
            case OBSERVATION -> 90.0d;
            case CHECKPOINT -> 140.0d;
            case STRONGPOINT -> 220.0d;
            case FORWARD_BASE -> 300.0d;
        };
    }

    static double storageVolume(ReferenceResource resource) {
        return switch (resource) {
            case FOOD, TIMBER -> 1.0d;
            case SEEDS, ENERGY -> 0.25d;
            case ORE -> 1.30d;
            case TOOLS -> 0.35d;
            case MEDICINE -> 0.05d;
            case WEAPONS -> 0.80d;
            case AMMO -> 0.35d;
        };
    }

    static int moduleBuildDays(ReferenceFieldModuleKind module) {
        return switch (module) {
            case DEPOT, DECONTAMINATION -> 2;
            case FIELD_HOSPITAL, FIRE_SUPPORT, FORTIFICATION -> 3;
        };
    }

    static double depotStorageMultiplier() { return 2.2d; }

    static double fortificationPower() { return 22.0d; }

    static double linkMaximumLength(ReferenceFieldLinkKind kind) {
        return kind == ReferenceFieldLinkKind.SUPPLY_CORRIDOR ? 10.0d : 7.0d;
    }

    static double linkIntegrity(ReferenceFieldLinkKind kind) {
        return kind == ReferenceFieldLinkKind.SUPPLY_CORRIDOR ? 55.0d : 90.0d;
    }

    static int linkBuildDays(ReferenceFieldLinkKind kind) {
        return kind == ReferenceFieldLinkKind.SUPPLY_CORRIDOR ? 2 : 3;
    }

    static double supplyCorridorSpeedMultiplier() { return 1.22d; }
}
