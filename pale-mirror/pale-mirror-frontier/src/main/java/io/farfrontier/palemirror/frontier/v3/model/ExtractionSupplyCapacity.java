package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.extraction.*;
import io.farfrontier.palemirror.frontier.v3.model.supply.SupplyPlanner;
import java.util.*;

/** Only opened, accessible, unclaimed sources are production alternatives; buried geology is not stock. */
final class ExtractionSupplyCapacity implements SupplyCapacityPort {
    @Override public Key key() { return Key.EXTRACTION; }
    @Override public List<SupplyPlanner.Process> read(FrontierWorldState state, SubjectId home, Map<String, SupplyPlanner.Stock> stocks) {
        if (state.bootstrap().ruleset().goodsTrade().supplyPlanning().sourcing().isEmpty()) return List.of();
        var quantities = new HashMap<BlockExtraction.Definition, Integer>();
        for (var deposit : state.extractionSites().deposits().values()) if (deposit.site().settlementId().equals(home))
            for (var cell : deposit.available(state.extractionSites().reservedCells(deposit.site().id())))
                quantities.merge(cell.definition(), 1, Math::addExact);
        var assignments = HumanAssignmentProjection.compile(state);
        var schedule = state.humanPopulation().schedule(home);
        long daily = schedule.segments().stream().filter(s -> s.window() == SettlementDailySchedule.Window.WORK)
                .mapToLong(s -> s.endExclusive() - s.startInclusive()).sum();
        if (daily == 0) return List.of();
        var result = new ArrayList<SupplyPlanner.Process>();
        for (var entry : quantities.entrySet().stream().sorted(Comparator.comparing(e -> e.getKey().id())).toList()) {
            var material = entry.getKey();
            // The current single-output process capability cannot promise independent
            // co-products. Extraction itself still supports its full output vector.
            if (material.coldOutput().size() != 1) continue;
            var product = material.coldOutput().getFirst();
            if (!stocks.containsKey(product.itemKind())) continue;
            var definition = state.bootstrap().ruleset().workCatalog().require(material.before().kind(), WorkOperation.EXTRACT);
            var candidates = SettlementWorkPolicy.permissions(state, home).workers(ResidentWorkKind.EXTRACTION).stream()
                    .map(id -> state.humanPopulation().resident(id))
                    .filter(r -> state.actorLocations().get(r.id()).condition().status() == ActorLifeStatus.ALIVE
                            && r.capability(definition.capability()) > 0)
                    .filter(r -> !assignments.assignment(r.id()).active()
                            || assignments.assignment(r.id()).kind() == HumanAssignmentKind.EXTRACTION)
                    .sorted(Comparator.comparing(ResidentProfile::id)).toList();
            var ids = candidates.stream().map(ResidentProfile::id).collect(java.util.stream.Collectors.toSet());
            long tools = state.inventory().items().values().stream().filter(item -> item.economicOwnerId().equals(home)
                    && item.itemKind().equals(material.toolKind()) && (item.custody() instanceof InventoryCustody.Actor actor && ids.contains(actor.actorId())
                    || item.custody() instanceof InventoryCustody.ContainerSlot slot
                        && !ReferenceContainerCustody.blocksCanonicalUse(state, slot.containerId()))).count();
            var workers = candidates.stream().limit(tools).toList();
            if (workers.isEmpty()) continue;
            int speed = workers.stream().mapToInt(r -> ResidentWorkStatistics.speedPermille(r, definition,
                    state.bootstrap().ruleset().workCatalog())).min().orElseThrow();
            long work = Math.ceilDiv(Math.multiplyExact(definition.workUnits(), 1000L), speed);
            long period = Math.max(1, Math.ceilDiv(Math.multiplyExact(work, schedule.dayTicks()), Math.multiplyExact(daily, workers.size())));
            result.add(new SupplyPlanner.Process("extraction-capacity:" + home.value() + ":" + material.id(),
                    product.itemKind(), product.quantity(), Map.of(), 0, Map.of(), 0,
                    Math.addExact(schedule.dayTicks(), period), period, entry.getValue(), work,
                    SupplyLabourCapacity.day(state, home, workers.stream().map(ResidentProfile::id).toList())));
        }
        return List.copyOf(result);
    }
    @Override public List<SupplyPlanner.Dependency> dependencies(FrontierWorldState state, SubjectId home,
            Map<String, SupplyPlanner.Stock> stocks) { return List.of(); }
}
