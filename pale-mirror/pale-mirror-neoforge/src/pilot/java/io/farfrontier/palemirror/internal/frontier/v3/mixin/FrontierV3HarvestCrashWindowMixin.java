package io.farfrontier.palemirror.internal.frontier.v3.mixin;

import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestJob;
import io.farfrontier.palemirror.internal.frontier.v3.FrontierV3PilotCrashHooks;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Pilot-classpath-only probe of the one harvest effect/observation gap.
 *
 * <p>The production executor has no test flag, callback or altered branch.  This mixin merely
 * records the exact Minecraft write after it succeeds, then gives the already-existing pilot
 * rendezvous its production runtime revision immediately before the typed observation command.</p>
 */
@Mixin(targets = "io.farfrontier.palemirror.internal.frontier.v3.FrontierV3ResourceSiteHarvestSceneExecutor")
abstract class FrontierV3HarvestCrashWindowMixin {
    @Inject(method = "observePreparedCrop", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z",
            shift = At.Shift.AFTER), require = 1)
    private static void recordVisibleCropEffect(ServerLevel level, FrontierWorldState state,
                                                ResourceSiteHarvestJob acceptedJob,
                                                io.farfrontier.palemirror.frontier.v3.model.BlockPosition cropSlot,
                                                CallbackInfoReturnable<Boolean> ignored) {
        FrontierV3PilotCrashHooks.cropEffectBecameVisible(acceptedJob, cropSlot);
    }

    @Inject(method = "work", at = @At(value = "INVOKE",
            target = "Lio/farfrontier/palemirror/internal/frontier/v3/FrontierV3ResourceSiteHarvestSceneExecutor;"
                    + "observePreparedCrop(Lnet/minecraft/server/level/ServerLevel;"
                    + "Lio/farfrontier/palemirror/frontier/v3/model/FrontierWorldState;"
                    + "Lio/farfrontier/palemirror/frontier/v3/model/ResourceSiteHarvestJob;"
                    + "Lio/farfrontier/palemirror/frontier/v3/model/BlockPosition;)Z",
            shift = At.Shift.AFTER), require = 1)
    private static void stopAfterVisibleEffect(ServerLevel level, @Coerce Object runtime,
                                               FrontierWorldState state,
                                               io.farfrontier.palemirror.frontier.v3.model.SceneLease lease,
                                               CallbackInfo ignored) {
        ResourceSiteHarvestJob job = io.farfrontier.palemirror.frontier.v3.model.FrontierResourceSiteHarvestSceneSupport
                .require(state, io.farfrontier.palemirror.frontier.v3.model.FrontierSceneBehaviors.resourceSiteHarvest(lease));
        FrontierV3PilotCrashHooks.afterVisibleCropEffectBeforeObservation(runtime, job);
    }
}
