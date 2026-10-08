package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSite;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Passive physical ownership survives quarantine without retaining an engine or executing work. */
final class FrontierV3NativeFieldOwnership {
    private static final Map<MinecraftServer, List<ResourceSite>> RETAINED = new IdentityHashMap<>();

    private FrontierV3NativeFieldOwnership() { }

    static void retain(MinecraftServer server, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        if (runtime.status().kind() == FrontierV3RuntimeStatus.Kind.QUARANTINED)
            runtime.passiveOwnershipState().ifPresent(state -> RETAINED.put(server,
                    List.copyOf(state.resourceSiteDescriptors().values())));
    }

    static void forget(MinecraftServer server) { RETAINED.remove(server); }

    static boolean blocksCropGrowth(ServerLevel level, BlockPos position) {
        if (!FrontierV3PhysicalWorld.isPhysical(level)) return false;
        var runtime = FrontierV3ServerLifecycle.runtimeFor(level.getServer());
        if (runtime != null) return FrontierV3ResourceSiteExecutor.blocksNativeCropGrowth(runtime, level, position);
        return siteAt(level, position).map(site -> FrontierV3ResourceSiteExecutor.blocksNativeCropGrowth(
                level, FrontierV3ResourceSiteLedger.get(level), site, position)).orElse(false);
    }

    static boolean blocksSoilReversion(ServerLevel level, BlockPos position) {
        if (!FrontierV3PhysicalWorld.isPhysical(level)) return false;
        var runtime = FrontierV3ServerLifecycle.runtimeFor(level.getServer());
        if (runtime != null) return FrontierV3ResourceSiteExecutor.blocksNativeSoilReversion(runtime, level, position);
        return siteAt(level, position).map(site -> FrontierV3ResourceSiteExecutor.blocksNativeSoilReversion(
                level, FrontierV3ResourceSiteLedger.get(level), site, position)).orElse(false);
    }

    static boolean restoreCropGrowth(ServerLevel level, BlockPos position) {
        if (!FrontierV3PhysicalWorld.isPhysical(level)) return false;
        var runtime = FrontierV3ServerLifecycle.runtimeFor(level.getServer());
        if (runtime != null) return FrontierV3ResourceSiteExecutor.restoreNativeGrowthPostcondition(runtime, level, position);
        return siteAt(level, position).map(site -> FrontierV3ResourceSiteExecutor.restoreNativeGrowthPostcondition(
                level, FrontierV3ResourceSiteLedger.get(level), site, position)).orElse(false);
    }

    private static Optional<ResourceSite> siteAt(ServerLevel level, BlockPos position) {
        return RETAINED.getOrDefault(level.getServer(), List.of()).stream()
                .filter(site -> FrontierV3ResourceSiteExecutor.contains(site, position)).findFirst();
    }
}
