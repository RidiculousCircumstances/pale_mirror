package io.farfrontier.palemirror.internal.frontier.v3.mixin;

import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.internal.frontier.v3.FrontierV3PilotCrashHooks;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Pilot-classpath-only probe of the one harvest effect/observation gap.
 *
 * <p>The production executor has no test flag, callback or altered branch. The pilot probes the
 * persisted cell witness after the active writer returns and before the caller can submit the
 * typed crop observation; merely entering a pending turn is not a physical-effect witness.</p>
 */
@Mixin(targets = "io.farfrontier.palemirror.internal.frontier.v3.FrontierV3ResourceSiteHarvestSceneExecutor")
abstract class FrontierV3HarvestCrashWindowMixin {
    @Inject(method = "work", at = @At(value = "INVOKE",
            target = "Lio/farfrontier/palemirror/internal/frontier/v3/FrontierV3ResourceFieldWorkExecutor;"
                    + "advance(Lnet/minecraft/server/level/ServerLevel;"
                    + "Lio/farfrontier/palemirror/frontier/v3/model/FrontierWorldState;"
                    + "Lio/farfrontier/palemirror/frontier/v3/model/SceneLease;"
                    + "Lio/farfrontier/palemirror/frontier/v3/model/ResourceSiteHarvestJob;"
                    + "Lnet/minecraft/world/entity/Mob;)"
                    + "Lio/farfrontier/palemirror/internal/frontier/v3/FrontierV3ResourceFieldWorkExecutor$Result;",
            shift = At.Shift.AFTER), require = 1)
    private static void stopAfterWitnessedCellEffect(ServerLevel level, @Coerce Object runtime,
                                               FrontierWorldState state,
                                               io.farfrontier.palemirror.frontier.v3.model.SceneLease lease,
                                               CallbackInfo ignored) {
        var job = io.farfrontier.palemirror.frontier.v3.model.FrontierResourceSiteHarvestSceneSupport
                .require(state, io.farfrontier.palemirror.frontier.v3.model.FrontierSceneBehaviors.resourceSiteHarvest(lease));
        FrontierV3PilotCrashHooks.afterResourceFieldWorkStep(level, runtime, state, job);
    }
}
