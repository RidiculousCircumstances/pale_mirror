package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.extraction.*;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.tooling.*;
import io.farfrontier.palemirror.frontier.v3.model.geometry.KnownBlockClearance;
import io.farfrontier.palemirror.frontier.v3.model.supply.FiniteWorkForecast;
import java.util.*;

/** The extraction owner describes known sequential work, never hypothetical unexplored geology. */
final class ExtractionProductionForecast {
    private record Query(SubjectId site, boolean equipmentBacked) { }
    private static final ImmutableKeyedInputView<Query,
            Map<BlockExtraction.Definition, FiniteWorkForecast.Estimate>> ESTIMATES = new ImmutableKeyedInputView<>(256);
    private record Availability(Map<ToolRequirement, List<ExactItemStack>> equipment,
                                Set<Long> reserved, boolean equipmentBacked) {
        private Availability { equipment = Map.copyOf(equipment); reserved = Set.copyOf(reserved); }
    }
    private static final BoundedValueMemo<Availability, Availability> AVAILABILITY = new BoundedValueMemo<>(256);
    private static final ImmutableKeyedInputView<SubjectId,
            Set<ToolRequirement>> REQUIREMENTS = new ImmutableKeyedInputView<>(256);
    static FiniteWorkForecast.Estimate assess(FrontierWorldState state, ExtractionDeposit deposit,
            BlockExtraction.Definition product, List<ResidentProfile> workers) {
        return assess(state, deposit, product, workers, true);
    }
    static FiniteWorkForecast.Estimate valuation(FrontierWorldState state, ExtractionDeposit deposit,
            BlockExtraction.Definition product, List<ResidentProfile> workers) {
        return assess(state, deposit, product, workers, false);
    }
    private static FiniteWorkForecast.Estimate assess(FrontierWorldState state, ExtractionDeposit deposit,
            BlockExtraction.Definition product, List<ResidentProfile> workers, boolean equipmentBacked) {
        var requirements = REQUIREMENTS.get(state.bootstrap(), deposit.site().id(), List.of(deposit.site().layout()),
                () -> deposit.site().layout().cells().stream().map(cell -> cell.definition().toolRequirement())
                        .collect(java.util.stream.Collectors.toUnmodifiableSet()));
        var workerIds = workers.stream().map(ResidentProfile::id).collect(java.util.stream.Collectors.toSet());
        var equipment = new HashMap<ToolRequirement, List<ExactItemStack>>();
        for (var requirement : requirements) equipment.put(requirement, equipmentBacked ? EquipmentProductiveCapacity.items(state,
                deposit.site().settlementId(), workerIds,
                EquipmentRequirements.allOf(Set.of(requirement))) : List.of());
        var facts = new Availability(equipment, equipmentBacked ? state.extractionSites().reservedCells(deposit.site().id()) : Set.of(), equipmentBacked);
        var availability = AVAILABILITY.get(facts, () -> facts);
        // Project current custody/permission first. Movement of unrelated actors or
        // speculative cash reservations cannot change this finite work graph.
        var inputs = new ArrayList<Object>();
        inputs.add(state.bootstrap().ruleset()); inputs.add(deposit); inputs.add(availability); inputs.addAll(workers);
        var estimates = ESTIMATES.get(state.bootstrap(), new Query(deposit.site().id(), equipmentBacked), inputs, HashMap::new);
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
            boolean supported = availability.equipmentBacked() ? !usable.isEmpty()
                    : tools.definitions().values().stream().anyMatch(tool -> tool.supports(cell.definition().toolRequirement()));
            boolean eligible = known.disposition() == ExtractionDeposit.Disposition.PRESENT && supported
                    && (deposit.development().openedCells().contains(cell.id())
                        || state.bootstrap().ruleset().extraction().developmentBatchCells() > 0);
            OptionalLong work = OptionalLong.empty();
            if (eligible) work = durations.computeIfAbsent(cell.definition(), block -> {
                var definition = catalog.require(cell.definition().before().kind(), WorkOperation.EXTRACT);
                if (workers.stream().allMatch(r -> r.capability(definition.capability()) > 0)) {
                    int workerSpeed = workers.stream().mapToInt(r -> ResidentWorkStatistics.speedPermille(r, definition, catalog)).min().orElseThrow();
                    int speed = availability.equipmentBacked() ? usable.stream().mapToInt(tool -> ToolWorkRate.speedPermille(
                            workerSpeed, tools, tool, cell.definition().toolRequirement())).min().orElseThrow()
                            : tools.definitions().values().stream().filter(tool -> tool.supports(cell.definition().toolRequirement()))
                                .mapToInt(tool -> Math.toIntExact(Math.max(1, (long) workerSpeed * tool.speedPermille() / 1000))).min().orElseThrow();
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
