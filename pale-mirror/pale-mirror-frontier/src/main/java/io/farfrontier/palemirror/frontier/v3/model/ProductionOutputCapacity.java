package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Read-only storage admission shared by bakery execution and player-facing explanation. */
public final class ProductionOutputCapacity {
    private ProductionOutputCapacity() { }

    /** Do not admit another bread batch when its destination cannot hold it now. */
    public static boolean canAdmitBreadBatch(FrontierWorldState state, SubjectId settlementId) {
        Objects.requireNonNull(state, "production output state");
        return state.inventory().canReceiveFungible(FrontierWorldState.depotId(
                Objects.requireNonNull(settlementId, "settlement id")), "minecraft:bread", 64);
    }

    public static boolean depotDeliveryUnavailable(FrontierWorldState state, ProductionJob job) {
        Objects.requireNonNull(state, "production output state");
        Objects.requireNonNull(job, "production job");
        if (job.bakeryWork().orElseThrow().phase() != BakeryWorkState.Phase.DEPOT_DELIVERY) return false;
        SubjectId depot = FrontierWorldState.depotId(job.settlementId());
        return job.inputHold() instanceof ProductionInputHold.Materialized
                ? state.firstFreeContainerSlot(depot).isEmpty()
                : !state.inventory().canReceiveFungible(depot, job.outputItemKind(), job.outputCount());
    }
}
