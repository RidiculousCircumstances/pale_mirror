package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.agriculture.CultivationAreaPlanner;
import java.util.Objects;

/** Last bounded economic-to-land decision, not a promise of harvested stock. One retained record per home. */
public record CultivationReview(long atTick, long targetOutput, long expectedOutput, long firstHarvestAfterTicks,
        long horizonTicks, int selectedCells, int plantedCells, int reserveCells, CultivationAreaPlanner.Reason reason) {
    public CultivationReview {
        Objects.requireNonNull(reason);
        if (atTick < 0 || targetOutput < 0 || expectedOutput < 0 || firstHarvestAfterTicks < 0 || horizonTicks < 1
                || selectedCells < 0 || plantedCells < 0 || reserveCells < 0)
            throw new IllegalArgumentException("invalid cultivation decision receipt");
    }
}
