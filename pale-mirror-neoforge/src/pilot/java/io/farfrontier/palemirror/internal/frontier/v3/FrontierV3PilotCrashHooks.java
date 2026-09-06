package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.kernel.TransactionRecord;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestJob;

/** Pilot-only lifecycle owner for the optional crash-window mixin and rendezvous. */
public final class FrontierV3PilotCrashHooks {
    private static final FrontierV3CrashBoundaryProbe PROBE = FrontierV3CrashBoundaryProbe.fromSystemProperties();

    private FrontierV3PilotCrashHooks() { }

    public static void afterDurableAppend(TransactionRecord transaction) {
        PROBE.afterDurableAppend(transaction);
    }

    public static void cropEffectBecameVisible(ResourceSiteHarvestJob job, BlockPosition cropSlot) {
        PROBE.cropEffectBecameVisible(job, cropSlot);
    }

    public static void afterVisibleCropEffectBeforeObservation(Object runtime, ResourceSiteHarvestJob job) {
        if (!(runtime instanceof FrontierV3ServerRuntime<?, ?>)) {
            throw new IllegalArgumentException("pilot crash hook requires the Frontier v3 server runtime");
        }
        @SuppressWarnings("unchecked")
        FrontierV3ServerRuntime<FrontierWorldState, ?> typedRuntime = (FrontierV3ServerRuntime<FrontierWorldState, ?>) runtime;
        PROBE.afterVisibleCropEffectBeforeObservation(typedRuntime, job);
    }
}
