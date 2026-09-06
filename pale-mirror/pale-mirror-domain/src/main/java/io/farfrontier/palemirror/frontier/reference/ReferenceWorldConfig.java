package io.farfrontier.palemirror.frontier.reference;

import java.util.Objects;

/** Exact Java shape of Python {@code simulation.world.WorldConfig}. */
public record ReferenceWorldConfig(
        int width,
        int height,
        int settlementCount,
        long seed,
        int infectionSeeds,
        boolean v2,
        ReferenceSimulationProfile profile
) {
    public static final int SOURCE_WIDTH = 64;
    public static final int SOURCE_HEIGHT = 44;
    public static final int SOURCE_SETTLEMENT_COUNT = 12;
    public static final long SOURCE_SEED = 42L;
    public static final int SOURCE_INFECTION_SEEDS = 2;

    public ReferenceWorldConfig {
        if (width < 3 || height < 3) {
            throw new IllegalArgumentException("reference world dimensions must be at least three cells");
        }
        if (settlementCount < 1 || infectionSeeds < 0) {
            throw new IllegalArgumentException("reference settlement and infection-seed counts must be non-negative");
        }
        profile = Objects.requireNonNull(profile, "profile");
    }

    public static ReferenceWorldConfig sourceV2() {
        return new ReferenceWorldConfig(SOURCE_WIDTH, SOURCE_HEIGHT, SOURCE_SETTLEMENT_COUNT,
                SOURCE_SEED, SOURCE_INFECTION_SEEDS, true, ReferenceSimulationProfile.SOURCE_V2);
    }

    public static ReferenceWorldConfig graybox1To40(long seed) {
        return new ReferenceWorldConfig(SOURCE_WIDTH, SOURCE_HEIGHT, SOURCE_SETTLEMENT_COUNT,
                seed, SOURCE_INFECTION_SEEDS, true, ReferenceSimulationProfile.GRAYBOX_1_40);
    }
}
