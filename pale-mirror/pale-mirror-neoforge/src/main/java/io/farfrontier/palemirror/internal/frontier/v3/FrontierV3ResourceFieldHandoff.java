package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import java.util.ArrayList;
import java.util.List;
import static io.farfrontier.palemirror.internal.frontier.v3.FrontierV3HotHandoff.Status.*;

/** Field owner proves the actual current cells, not a projection executor's visit. */
final class FrontierV3ResourceFieldHandoff {
    private FrontierV3ResourceFieldHandoff() { }
    static List<FrontierV3HotHandoff.Check> inspect(ServerLevel level, FrontierWorldState state, ChunkPos chunk) {
        var checks = new ArrayList<FrontierV3HotHandoff.Check>();
        var ledger = FrontierV3ResourceSiteLedger.get(level);
        for (var siteId : FrontierV3HandoffIndex.fields(level, state.resourceSites(), chunk)) {
            var entry = java.util.Map.entry(siteId, state.resourceSites().cycles().get(siteId));
            var phase = state.resourceSites().site(entry.getKey()).phase();
            if (phase != ResourceSitePhase.GROWING && phase != ResourceSitePhase.READY
                    && phase != ResourceSitePhase.HARVESTING) continue;
            var cycle = entry.getValue();
            var cells = cycle.layout().cellsIn(new ResourceFieldLayout.ChunkColumn(chunk.x, chunk.z));
            if (cells.isEmpty()) continue;
            if (state.resourceSites().hasPendingWorldChange(entry.getKey()) || !cycle.pendingPlayerBreaks().isEmpty()
                    || ledger.hasPendingFieldMutation(entry.getKey())) {
                checks.add(new FrontierV3HotHandoff.Check(entry.getKey(), WAITING, "field_effect_recovery"));
                continue;
            }
            var claim = ledger.fieldClaim(entry.getKey());
            var status = WAITING;
            String reason = "field_initialization";
            if (claim instanceof FrontierV3ResourceSiteLedger.FieldOwnership owner) {
                if (owner.status() == FrontierV3ResourceSiteLedger.Status.CONFLICT) {
                    status = CONFLICT; reason = "field_ownership_conflict";
                } else if (owner.status() == FrontierV3ResourceSiteLedger.Status.ACTIVE
                        && owner.witness().matchesCycle(cycle)) {
                    checks.add(inspectCells(cycle, owner.witness(), cells, id ->
                            FrontierV3ResourceFieldObservation.read(level, cycle.layout().requireCell(id), "handoff:field")));
                    continue;
                } else reason = "field_epoch_reconciliation";
            }
            checks.add(new FrontierV3HotHandoff.Check(entry.getKey(), status, reason));
        }
        return List.copyOf(checks);
    }
    static FrontierV3HotHandoff.Check inspectCells(ResourceFieldCycle cycle, FrontierV3ResourceFieldWitness witness,
            List<ResourceFieldLayout.Cell> cells,
            java.util.function.Function<ResourceFieldLayout.CellId, FrontierV3ResourceFieldObservation.Reading> observation) {
        if (!witness.matchesCycle(cycle))
            return new FrontierV3HotHandoff.Check(cycle.siteId(), WAITING, "field_epoch_reconciliation");
        for (var cell : cells) {
            var retained = witness.cell(cell.id());
            var canonical = cycle.cell(cell.id());
            var actual = observation.apply(cell.id());
            if (retained.foreign().isPresent()) {
                var incident = retained.foreign().orElseThrow();
                if (retained.pending().isEmpty()
                        && (canonical.soil() == ResourceFieldCycle.Soil.OBSTRUCTED || canonical.crop() == ResourceFieldCycle.Crop.OBSTRUCTED)
                        && actual instanceof FrontierV3ResourceFieldObservation.Foreign foreign
                        && incident.observedSoil().equals(foreign.incident().observedSoil())
                        && incident.observedCrop().equals(foreign.incident().observedCrop())) continue;
                return new FrontierV3HotHandoff.Check(cycle.siteId(), CONFLICT, "field_foreign_change");
            }
            if (canonical.soil() == ResourceFieldCycle.Soil.OBSTRUCTED || canonical.crop() == ResourceFieldCycle.Crop.OBSTRUCTED)
                return new FrontierV3HotHandoff.Check(cycle.siteId(), WAITING, "field_obstruction_recovery");
            var target = ResourceFieldPhysicalSurface.Condition.of(canonical);
            if (retained.pending().isPresent() || !retained.committed().equals(target)
                    || !(actual instanceof FrontierV3ResourceFieldObservation.Owned owned) || !owned.condition().equals(target))
                return new FrontierV3HotHandoff.Check(cycle.siteId(), WAITING, "field_projection_or_observation");
        }
        return new FrontierV3HotHandoff.Check(cycle.siteId(), READY, "current_field");
    }
}
