package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.extraction.*;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.tooling.*;
import io.farfrontier.palemirror.frontier.v3.model.geometry.KnownBlockClearance;
import io.farfrontier.palemirror.frontier.v3.model.supply.FiniteWorkForecast;
import java.util.*;

/** The extraction owner describes known sequential work, never hypothetical unexplored geology. */
final class ExtractionProductionForecast {
    private static final ImmutableKeyedInputView<SubjectId,
            Map<BlockExtraction.Definition, FiniteWorkForecast.Estimate>> ESTIMATES = new ImmutableKeyedInputView<>(256);
    private record Availability(Map<ToolRequirement, List<ExactItemStack>> equipment,
                                Set<Long> reserved) {
        private Availability { equipment = Map.copyOf(equipment); reserved = Set.copyOf(reserved); }
    }
    private static final BoundedValueMemo<Availability, Availability> AVAILABILITY = new BoundedValueMemo<>(256);
    private static final ImmutableKeyedInputView<SubjectId,
            Set<ToolRequirement>> REQUIREMENTS = new ImmutableKeyedInputView<>(256);
    static FiniteWorkForecast.Estimate assess(FrontierWorldState state, ExtractionDeposit deposit,
            BlockExtraction.Definition product, List<ResidentProfile> workers) {
        var requirements = REQUIREMENTS.get(state.bootstrap(), deposit.site().id(), List.of(deposit.site().layout()),
                () -> deposit.site().layout().cells().stream().map(cell -> cell.definition().toolRequirement())
                        .collect(java.util.stream.Collectors.toUnmodifiableSet()));
        var workerIds = workers.stream().map(ResidentProfile::id).collect(java.util.stream.Collectors.toSet());
        var equipment = new HashMap<ToolRequirement, List<ExactItemStack>>();
        for (var requirement : requirements) equipment.put(requirement, EquipmentProductiveCapacity.items(state,
                deposit.site().settlementId(), workerIds,
                EquipmentRequirements.allOf(Set.of(requirement))));
        var facts = new Availability(equipment, state.extractionSites().reservedCells(deposit.site().id()));
        var availability = AVAILABILITY.get(facts, () -> facts);
        // Project current custody/permission first. Movement of unrelated actors or
        // speculative cash reservations cannot change this finite work graph.
        var inputs = new ArrayList<Object>();
        inputs.add(state.bootstrap().ruleset()); inputs.add(deposit); inputs.add(availability); inputs.addAll(workers);
        var estimates = ESTIMATES.get(state.bootstrap(), deposit.site().id(), inputs, HashMap::new);
        return estimates.computeIfAbsent(product, ignored -> project(state, deposit, product, workers, availability));
    }
    private static FiniteWorkForecast.Estimate project(FrontierWorldState state, ExtractionDeposit deposit,
            BlockExtraction.Definition product, List<ResidentProfile> workers, Availability availability) {
        var reserved = availability.reserved();
        var catalog = state.bootstrap().ruleset().workCatalog();
        var nodes = new ArrayList<FiniteWorkForecast.Node>();
        var durations = new HashMap<BlockExtraction.Definition, OptionalLong>();
        for (var cell : deposit.site().layout().cells()) {
            var known = deposit.cells().get(cell.id());
            if (known.knownBlock().equals(cell.definition().after()) || KnownBlockClearance.emptyCollision(known.knownBlock().kind())) {
                nodes.add(new FiniteWorkForecast.Node(cell.id(), Set.of(), OptionalLong.of(0), false)); continue;
            }
            var tools = state.bootstrap().ruleset().tools();
            var usable = availability.equipment().get(cell.definition().toolRequirement());
            boolean eligible = known.disposition() == ExtractionDeposit.Disposition.PRESENT && !usable.isEmpty()
                    && (deposit.development().openedCells().contains(cell.id())
                        || state.bootstrap().ruleset().extraction().developmentBatchCells() > 0);
            OptionalLong work = OptionalLong.empty();
            if (eligible) work = durations.computeIfAbsent(cell.definition(), block -> {
                var definition = catalog.require(cell.definition().before().kind(), WorkOperation.EXTRACT);
                if (workers.stream().allMatch(r -> r.capability(definition.capability()) > 0)) {
                    int workerSpeed = workers.stream().mapToInt(r -> ResidentWorkStatistics.speedPermille(r, definition, catalog)).min().orElseThrow();
                    int speed = usable.stream().mapToInt(tool -> ToolWorkRate.speedPermille(
                            workerSpeed, tools, tool, cell.definition().toolRequirement())).min().orElseThrow();
                    return OptionalLong.of(Math.ceilDiv(Math.multiplyExact(definition.workUnits(), 1000L), speed));
                }
                return OptionalLong.empty();
            });
            nodes.add(new FiniteWorkForecast.Node(cell.id(), cell.prerequisites(), work,
                    eligible && !reserved.contains(cell.id()) && cell.definition().equals(product)));
        }
        return FiniteWorkForecast.assess(nodes);
    }
    private ExtractionProductionForecast() { }
}
