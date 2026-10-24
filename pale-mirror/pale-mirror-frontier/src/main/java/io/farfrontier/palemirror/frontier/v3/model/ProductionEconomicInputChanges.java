package io.farfrontier.palemirror.frontier.v3.model;

import java.util.*;
import static io.farfrontier.palemirror.frontier.v3.model.SettlementEconomicPlanningState.Change.*;

/** Production owns commitments and capabilities; motion/work-cursor increments are excluded. */
final class ProductionEconomicInputChanges implements EconomicInputChangePort {
    @Override public void collect(FrontierWorldState before, FrontierWorldState after, Sink sink) {
        if (before.structureConditions() != after.structureConditions()) {
            var facilities = new HashSet<io.farfrontier.palemirror.frontier.v3.api.SubjectId>();
            after.structureConditions().forEach((id, condition) -> {
                if (condition != before.structureConditions().get(id)) facilities.add(id);
            });
            for (var home : after.bootstrap().settlements())
                if (home.structures().stream().anyMatch(facility -> facilities.contains(facility.id())))
                    sink.changed(home.id(), CAPACITY, Set.of());
        }
        EconomicInputChangePort.difference(before.productionJobs(), after.productionJobs(), (old, next) -> {
            if (old != null && next != null && old.outputItemKind().equals(next.outputItemKind()) && old.outputCount() == next.outputCount()
                    && old.inputHold().equals(next.inputHold()) && old.rights().equals(next.rights())) return;
            if (old != null) sink.resource(old.rights().resourceOwner().id(), COMMITMENT, old.outputItemKind());
            if (next != null) sink.resource(next.rights().resourceOwner().id(), COMMITMENT, next.outputItemKind());
        });
        EconomicInputChangePort.difference(before.productionStations().machines(), after.productionStations().machines(), (old, next) -> {
            if (old != null && next != null && old.storedHeat() == next.storedHeat()
                    && old.operation().map(ProductionOperation::id).equals(next.operation().map(ProductionOperation::id))) return;
            var station = next == null ? old.stationId() : next.stationId();
            var records = next == null ? before.inventory().containers() : after.inventory().containers();
            records.values().stream().filter(record -> record.productionStation().filter(spec -> spec.id().equals(station)).isPresent())
                    .findFirst().ifPresent(record -> sink.changed(record.ownerId(), COMMITMENT, Set.of()));
        });
        EconomicInputChangePort.difference(before.resourceSites().declarations(), after.resourceSites().declarations(), (old, next) -> {
            if (old != null) sink.changed(old.settlementId(), CAPACITY, Set.of());
            if (next != null) sink.changed(next.settlementId(), CAPACITY, Set.of());
        });
        EconomicInputChangePort.difference(before.resourceSites().cycles(), after.resourceSites().cycles(), (old, next) -> {
            if (old != null && next != null && !fieldCapacityChanged(old, next)) return;
            var cycle = next == null ? old : next;
            var declaration = after.resourceSites().declarations().get(cycle.siteId());
            if (declaration == null) declaration = before.resourceSites().declarations().get(cycle.siteId());
            if (declaration != null) sink.resource(declaration.settlementId(), CAPACITY, FieldHarvestExtraction.ITEM);
        });
        EconomicInputChangePort.difference(before.extractionSites().deposits(), after.extractionSites().deposits(), (old, next) -> {
            if (old != null && next != null && old.site().equals(next.site()) && !externallyChanged(old, next)
                    && (old.cells() == next.cells() || next.cells().values().stream().anyMatch(cell -> cell.disposition()
                        == io.farfrontier.palemirror.frontier.v3.model.extraction.ExtractionDeposit.Disposition.PRESENT))) return;
            if (old != null) sink.changed(old.site().settlementId(), CAPACITY, Set.of());
            if (next != null) sink.changed(next.site().settlementId(), CAPACITY, Set.of());
        });
    }
    private static boolean externallyChanged(io.farfrontier.palemirror.frontier.v3.model.extraction.ExtractionDeposit before,
            io.farfrontier.palemirror.frontier.v3.model.extraction.ExtractionDeposit after) {
        if (before.cells() == after.cells()) return false;
        return after.cells().entrySet().stream().anyMatch(entry -> entry.getValue().disposition()
                == io.farfrontier.palemirror.frontier.v3.model.extraction.ExtractionDeposit.Disposition.EXTERNALLY_CHANGED
                && !entry.getValue().equals(before.cells().get(entry.getKey())));
    }
    private static boolean fieldCapacityChanged(ResourceFieldCycle before, ResourceFieldCycle after) {
        if (!before.layout().equals(after.layout()) || !before.sowing().equals(after.sowing())) return true;
        boolean harvested = after.harvestedCount() > before.harvestedCount();
        for (var cell : after.layout().cells()) {
            var old = before.cell(cell.id()); var next = after.cell(cell.id());
            if (old.soil() != next.soil() || old.workAccessBlocked() != next.workAccessBlocked()
                    || (old.crop() == ResourceFieldCycle.Crop.OBSTRUCTED) != (next.crop() == ResourceFieldCycle.Crop.OBSTRUCTED)) return true;
            if (!harvested && (old.crop() == ResourceFieldCycle.Crop.GROWING || old.crop() == ResourceFieldCycle.Crop.MATURE)
                    && next.crop() == ResourceFieldCycle.Crop.ABSENT) return true;
        }
        return false;
    }
}
