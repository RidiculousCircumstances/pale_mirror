package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestJob;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Synchronize the one owned cell before vanilla reads its bone-meal predecessor. */
public final class FrontierV3FieldInteractionAdmission {
    private FrontierV3FieldInteractionAdmission() { }

    public static boolean prepareBonemeal(ServerLevel level, BlockPos position) {
        if (!FrontierV3PhysicalWorld.isPhysical(level)) return true;
        var runtime = FrontierV3ServerLifecycle.runtimeFor(level.getServer());
        if (runtime == null) return true;
        var state = runtime.decodedState().orElse(null);
        if (state == null) return false;
        var block = new BlockPosition(position.getX(), position.getY(), position.getZ());
        for (var cycle : state.resourceSites().cycles().values()) {
            var cell = cycle.layout().cropAt(block).orElse(null);
            if (cell == null) continue;
            var ledger = FrontierV3ResourceSiteLedger.get(level);
            if (state.resourceSites().hasPendingCellMutation(cycle.siteId(), cell.id())
                    || ledger.hasPendingFieldMutation(cycle.siteId(), cell.id())) return false;
            if (state.resourceSites().growthProtectedCells(cycle.siteId()).contains(cell.id())) return false;
            if (!(ledger.fieldClaim(cycle.siteId()) instanceof FrontierV3ResourceSiteLedger.FieldOwnership)) return false;
            var projected = FrontierV3ResourceFieldGrowthProjector.projectCurrentOne(level, runtime, cycle.siteId(), cell.id());
            if (projected != FrontierV3ResourceFieldGrowthProjector.Result.CURRENT
                    && projected != FrontierV3ResourceFieldGrowthProjector.Result.ADVANCED) return false;
            var owner = (FrontierV3ResourceSiteLedger.FieldOwnership) ledger.fieldClaim(cycle.siteId());
            var retained = owner.witness().cell(cell.id());
            return retained.pending().isEmpty() && retained.foreign().isEmpty()
                    && retained.committed().equals(io.farfrontier.palemirror.frontier.v3.model.ResourceFieldPhysicalSurface.Condition.of(
                            cycle.cell(cell.id())))
                    && FrontierV3ResourceFieldObservation.observe(level, cycle, owner.witness(), cell.id(),
                            "bonemeal:current-predecessor").disposition() == FrontierV3ResourceFieldObservation.Disposition.CURRENT;
        }
        return true;
    }
}
