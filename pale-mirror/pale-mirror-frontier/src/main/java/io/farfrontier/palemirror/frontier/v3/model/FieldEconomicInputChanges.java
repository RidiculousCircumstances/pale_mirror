package io.farfrontier.palemirror.frontier.v3.model;

import java.util.*;
import static io.farfrontier.palemirror.frontier.v3.model.SettlementEconomicPlanningState.Change.*;

/** Agriculture owns changes to productive land. Executing already budgeted
 * preparation is progress, not a fresh loss of installed production capacity. */
final class FieldEconomicInputChanges implements EconomicInputChangePort {
    @Override public void collect(FrontierWorldState before, FrontierWorldState after, Sink sink) {
        EconomicInputChangePort.difference(before.resourceSites().declarations(), after.resourceSites().declarations(), (old, next) -> {
            if (old != null) sink.changed(old.settlementId(), CAPACITY, Set.of());
            if (next != null) sink.changed(next.settlementId(), CAPACITY, Set.of());
        });
        EconomicInputChangePort.difference(before.resourceSites().cycles(), after.resourceSites().cycles(), (old, next) -> {
            var reason = change(old, next);
            if (reason.isEmpty()) return;
            var cycle = next == null ? old : next;
            var declaration = after.resourceSites().declarations().get(cycle.siteId());
            if (declaration == null) declaration = before.resourceSites().declarations().get(cycle.siteId());
            if (declaration != null) sink.resource(declaration.settlementId(), reason.orElseThrow(), FieldHarvestExtraction.ITEM);
        });
    }
    private static Optional<SettlementEconomicPlanningState.Change> change(ResourceFieldCycle before, ResourceFieldCycle after) {
        if (before == null || after == null) return Optional.of(CAPACITY);
        boolean commitment = false;
        if (!before.layout().equals(after.layout())) {
            var old = before.layout(); var next = after.layout();
            // Only an explicit retained land-admission predecessor proves growth.
            // Other geometry changes, removals and moved contacts remain urgent.
            if (next.extensions().size() != old.extensions().size() + 1
                    || !next.extensions().getLast().predecessorFingerprint().equals(old.fingerprint())
                    || next.cells().size() <= old.cells().size()
                    || !next.cells().subList(0, old.cells().size()).equals(old.cells())
                    || !next.irrigationSlots().equals(old.irrigationSlots())) return Optional.of(CAPACITY);
            commitment = true;
        }
        if (!before.sowing().equals(after.sowing())) {
            if (!after.sowing().cells().containsAll(before.sowing().cells())) return Optional.of(CAPACITY);
            commitment = true;
        }
        boolean harvested = after.harvestedCount() > before.harvestedCount();
        for (var cell : before.layout().cells()) {
            var old = before.cell(cell.id()); var next = after.cell(cell.id());
            if (old.soil() != next.soil() && !preparationProgress(old, next)
                    || old.workAccessBlocked() != next.workAccessBlocked()
                    || (old.crop() == ResourceFieldCycle.Crop.OBSTRUCTED) != (next.crop() == ResourceFieldCycle.Crop.OBSTRUCTED))
                return Optional.of(CAPACITY);
            if (!harvested && (old.crop() == ResourceFieldCycle.Crop.GROWING || old.crop() == ResourceFieldCycle.Crop.MATURE)
                    && next.crop() == ResourceFieldCycle.Crop.ABSENT) return Optional.of(CAPACITY);
        }
        // More admitted/sown land changes the committed supply forecast at the
        // ordinary review deadline. It cannot revoke its own accepted plan.
        return commitment ? Optional.of(COMMITMENT) : Optional.empty();
    }
    private static boolean preparationProgress(ResourceFieldCycle.CellState before, ResourceFieldCycle.CellState after) {
        return before.crop() == ResourceFieldCycle.Crop.ABSENT && after.crop() == ResourceFieldCycle.Crop.ABSENT
                && before.soil().untilled() && ResourceFieldCycle.Soil.catalog().path(before.soil()).stream()
                    .anyMatch(step -> step.after() == after.soil());
    }
}
