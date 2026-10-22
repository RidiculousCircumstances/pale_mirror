package io.farfrontier.palemirror.frontier.v3.model;

import java.util.*;

/** Read-only machine presentation; no recipe admission or economic planning in a board renderer. */
final class AutomaticStationReadability {
    static String text(FrontierWorldState state, SettlementStructure facility, long tick) {
        var record = state.inventory().containers().values().stream().filter(value -> value.productionStation()
                .filter(spec -> spec.facilityId().equals(facility.id())).isPresent())
                .filter(value -> state.inventory().surfaces().get(value.id()).status() != ContainerSurfaceStatus.RETIRED)
                .sorted(Comparator.comparing(ContainerRecord::id)).findFirst().orElse(null);
        if (record == null) return "EQUIPMENT NEEDED";
        if (ReferenceContainerCustody.blocksCanonicalUse(state, record.id())) return "STATION UNAVAILABLE";
        var spec = record.productionStation().orElseThrow(); var machine = state.productionStations().require(spec.id());
        if (machine.pendingRecipe().isPresent() || machine.pendingFuel().isPresent()) return "CONFIRMING TRANSFER";
        var operation = machine.operation().orElse(null);
        if (operation != null) {
            if (operation.transformed()) return "OUTPUT READY";
            var clock = operation.progress().orElse(null);
            if (clock == null) return "PROCESSING START PENDING";
            long percent = clock.completedAt(Math.max(tick, clock.evaluatedAtTick())) * 100 / clock.requiredMilliWork();
            return "PROCESSING " + percent + "% · " + operation.outputQuantity() + " ITEMS";
        }
        if (!ProductionStationRecipe.portVacant(state, spec, ActorContainerItemOrder.StationPort.OUTPUT)) return "OUTPUT READY · AWAITING COLLECTION";
        if (ProductionStationRecipe.portVacant(state, spec, ActorContainerItemOrder.StationPort.INPUT)) return "IDLE · NO INPUT";
        if (machine.storedHeat() == 0) return "FUEL NEEDED";
        return "IDLE · AWAITING PRODUCTION DEMAND";
    }
    private AutomaticStationReadability() { }
}
