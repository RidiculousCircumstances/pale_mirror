package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.extraction.*;
import io.farfrontier.palemirror.frontier.v3.model.supply.SupplyPlanner;
import java.util.*;

/** Sequential declared sources are conditional production capacity, never available stock. */
final class ExtractionSupplyCapacity implements SupplyCapacityPort {
    @Override public Key key() { return Key.EXTRACTION; }
    @Override public List<SupplyPlanner.Process> read(FrontierWorldState state, SubjectId home, Map<String, SupplyPlanner.Stock> stocks,
                                                    SupplyPlanningWindow window) {
        if (state.bootstrap().ruleset().goodsTrade().supplyPlanning().sourcing().isEmpty()) return List.of();
        var materials = new HashSet<BlockExtraction.Definition>();
        for (var deposit : state.extractionSites().deposits().values()) if (deposit.site().settlementId().equals(home))
            for (var cell : deposit.site().layout().cells())
                if (deposit.cells().get(cell.id()).disposition() == ExtractionDeposit.Disposition.PRESENT) materials.add(cell.definition());
        var assignments = HumanAssignmentProjection.compile(state);
        var schedule = state.humanPopulation().schedule(home);
        long daily = schedule.segments().stream().filter(s -> s.window() == SettlementDailySchedule.Window.WORK)
                .mapToLong(s -> s.endExclusive() - s.startInclusive()).sum();
        if (daily == 0) return List.of();
        var result = new ArrayList<SupplyPlanner.Process>();
        for (var material : materials.stream().sorted(Comparator.comparing(BlockExtraction.Definition::id)).toList()) {
            // The current single-output process capability cannot promise independent
            // co-products. Extraction itself still supports its full output vector.
            if (material.coldOutput().size() != 1) continue;
            var product = material.coldOutput().getFirst();
            if (!stocks.containsKey(product.itemKind())) continue;
            var definition = state.bootstrap().ruleset().workCatalog().require(material.before().kind(), WorkOperation.EXTRACT);
            var candidates = SupplyLabourCapacity.candidates(state, home, ResidentWorkKind.EXTRACTION, definition.capability(),
                            r -> assignments.assignment(r.id()).kind() == HumanAssignmentKind.EXTRACTION).stream()
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
            var forecasts = state.extractionSites().deposits().values().stream().filter(d -> d.site().settlementId().equals(home))
                    .map(d -> ExtractionProductionForecast.assess(state, d, material, workers)).filter(f -> f.outputs() > 0).toList();
            if (forecasts.isEmpty()) continue;
            long quantity = forecasts.stream().mapToLong(f -> f.outputs()).sum();
            long work = Math.max(1, Math.ceilDiv(forecasts.stream().mapToLong(f -> f.labourTicks()).sum(), quantity));
            long first = forecasts.stream().mapToLong(f -> f.firstOutputTicks()).min().orElseThrow();
            long period = Math.max(1, Math.ceilDiv(Math.multiplyExact(work, schedule.dayTicks()), Math.multiplyExact(daily, workers.size())));
            long opening = SupplyPlanningWindow.completionDelay(schedule, first);
            result.add(new SupplyPlanner.Process("extraction-capacity:" + home.value() + ":" + material.id(),
                    product.itemKind(), product.quantity(), Map.of(), 0, Map.of(), 0,
                    Math.max(opening, period), period, Math.toIntExact(quantity), work,
                    SupplyLabourCapacity.over(state, home, workers.stream().map(ResidentProfile::id).toList(), window)));
        }
        return List.copyOf(result);
    }
    @Override public List<SupplyPlanner.Dependency> dependencies(FrontierWorldState state, SubjectId home,
            Map<String, SupplyPlanner.Stock> stocks) { return List.of(); }
}
