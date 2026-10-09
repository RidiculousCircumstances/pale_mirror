package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import net.minecraft.server.level.ServerLevel;

/** Read-only field-owner evidence for a no-effect immature-cell exclusion. */
final class FrontierV3ResourceFieldExclusionObservation {
    private FrontierV3ResourceFieldExclusionObservation() { }

    static boolean immatureCellCurrent(ServerLevel level, FrontierWorldState state,
            io.farfrontier.palemirror.frontier.v3.model.ResourceSite site,
            io.farfrontier.palemirror.frontier.v3.model.ResourceFieldLayout.CellId id) {
        if (state.resourceSites().hasPendingCellMutation(site.id(), id)) return false;
        var cycle = state.resourceSites().cycle(site.id());
        if (cycle.pendingPlayerBreaks().containsKey(id)) return false;
        var claim = FrontierV3ResourceSiteLedger.get(level).siteClaim(site.id());
        if (!(claim instanceof FrontierV3ResourceSiteLedger.CellSiteClaim cells)
                || !(cells.claim() instanceof FrontierV3ResourceSiteLedger.FieldOwnership owner)
                || owner.status() != FrontierV3ResourceSiteLedger.Status.ACTIVE
                || !owner.witness().matchesCycle(cycle)) return false;
        var retained = owner.witness().cell(id);
        if (retained.pending().isPresent() || retained.foreign().isPresent()
                || !retained.committed().equals(
                    io.farfrontier.palemirror.frontier.v3.model.ResourceFieldPhysicalSurface.Condition.of(cycle.cell(id))))
            return false;
        return FrontierV3ResourceFieldObservation.observe(level, cycle, owner.witness(), id,
                "harvest-scene-immature-exclusion").disposition() == FrontierV3ResourceFieldObservation.Disposition.CURRENT;
    }
}

