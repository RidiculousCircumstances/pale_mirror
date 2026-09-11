package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.kernel.TransactionRecord;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.CustodyAccount;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestJob;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/** Pilot-only lifecycle owner for the optional crash-window mixin and rendezvous. */
public final class FrontierV3PilotCrashHooks {
    private static final FrontierV3CrashBoundaryProbe PROBE = FrontierV3CrashBoundaryProbe.fromSystemProperties();

    private FrontierV3PilotCrashHooks() { }

    public static void afterDurableAppend(TransactionRecord transaction) {
        PROBE.afterDurableAppend(transaction);
    }

    /**
     * Publishes the pilot-only stop receipt only after Minecraft has returned from its
     * ordinary server-stop path.  The event-bus {@code ServerStoppedEvent} is not a
     * dependable boundary for the direct moddev launcher: it can be observed only
     * after that launcher has already released the server callback thread.  The
     * {@code MinecraftServer.stopServer} tail is the exact post-save boundary used
     * by the disposable supervisor.
     */
    public static void afterMinecraftServerDurablyStopped(MinecraftServer server) {
        FrontierV3PilotLifecycleSignal.durableServerSave(server);
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

    /** Pilot-only observation of the existing player-departure adapter immediately before submit. */
    public static void afterVisibleFungiblePlayerDeparture(ServerLevel level, Object runtime, CustodyAccount account) {
        if (!(runtime instanceof FrontierV3ServerRuntime<?, ?>)) {
            throw new IllegalArgumentException("pilot crash hook requires the Frontier v3 server runtime");
        }
        @SuppressWarnings("unchecked")
        FrontierV3ServerRuntime<FrontierWorldState, ?> typedRuntime = (FrontierV3ServerRuntime<FrontierWorldState, ?>) runtime;
        PROBE.afterVisibleFungiblePlayerDeparture(level, typedRuntime, account);
    }
}
