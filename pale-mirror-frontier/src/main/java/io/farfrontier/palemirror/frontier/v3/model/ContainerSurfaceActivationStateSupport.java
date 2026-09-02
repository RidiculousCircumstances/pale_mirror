package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

/** Pure admission rule for moving an exact depot from COLD truth to a physical chest. */
public final class ContainerSurfaceActivationStateSupport {
    private ContainerSurfaceActivationStateSupport() { }

    /**
     * A COLD production hold lives outside {@link ExactInventory}.  Activating its depot would
     * expose a partial physical projection and later let COLD alter an active chest without a
     * physical receipt.  Wait for the exact COLD job's normal terminal transition instead.
     */
    public static boolean blockedByColdProduction(FrontierWorldState state, SubjectId containerId) {
        return state.productionJobs().values().stream().anyMatch(job -> switch (job.inputHold()) {
            case ProductionInputHold.Cold held -> held.item().custody() instanceof InventoryCustody.ContainerSlot slot
                    && slot.containerId().equals(containerId);
            case ProductionInputHold.Materialized ignored -> false;
        });
    }
}
