package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.OptionalLong;

/** Bakery-owned processing estimate for a complete operation. Transport/service latency is separate.
 * No route search, assignment mutation or market recursion. */
final class BakeryProcessingForecast {
    private static final ImmutableKeyedInputView<SubjectId, java.util.Optional<Capacity>> VALUATIONS = new ImmutableKeyedInputView<>(SimulationCapacity.MAX_SETTLEMENTS);
    record Capacity(SubjectId station, long firstOutputDelay, long batchTicks, long labourTicks,
                    java.util.Map<String, Long> labourCapacity) { }
    static java.util.Optional<Capacity> valuation(FrontierWorldState state, SubjectId home) {
        var recipe = BakeryRecipes.bread(state);
        var stations = InstalledStationFacts.forRecipe(state, home, recipe);
        var inputs = new java.util.ArrayList<>(SupplyLabourCapacity.potentialInputs(state, home)); inputs.add(stations);
        return VALUATIONS.getPublished(state.bootstrap(), home, inputs, () -> valueInstalled(state, home));
    }
    private static java.util.Optional<Capacity> valueInstalled(FrontierWorldState state, SubjectId home) {
        var recipe = BakeryRecipes.bread(state);
        var stations = InstalledStationFacts.forRecipe(state, home, recipe);
        if (stations.isEmpty()) return java.util.Optional.empty();
        var station = stations.getFirst().station();
        var definition = state.bootstrap().ruleset().workCatalog().require(recipe.product().itemKind(), WorkOperation.PRODUCE);
        long operationWork = operationWork(recipe, definition);
        if (recipe.processingMode() == ProductionRecipe.ProcessingMode.AUTOMATIC)
            return java.util.Optional.of(new Capacity(station.id(), operationWork, operationWork, 0, java.util.Map.of()));
        var workers = SupplyLabourCapacity.potential(state, home, ResidentWorkKind.PRODUCTION, definition.capability());
        int speed = workers.stream().mapToInt(r -> ResidentWorkStatistics.speedPermille(r, definition,
                state.bootstrap().ruleset().workCatalog())).min().orElse(0);
        var schedule = state.humanPopulation().schedule(home); long daily = SupplyPlanningWindow.dailyWork(schedule);
        if (speed == 0 || daily == 0) return java.util.Optional.empty();
        long labour = Math.ceilDiv(Math.multiplyExact(operationWork, 1000L), speed);
        return java.util.Optional.of(new Capacity(station.id(), SupplyPlanningWindow.completionDelay(schedule, labour),
                Math.ceilDiv(Math.multiplyExact(labour, schedule.dayTicks()), daily), labour, java.util.Map.of()));
    }
    static OptionalLong firstOutputDelay(FrontierWorldState state, SubjectId home) {
        return capacity(state, home, new SupplyPlanningWindow(state.humanPopulation().schedule(home).dayTicks()))
                .map(c -> OptionalLong.of(c.firstOutputDelay())).orElseGet(OptionalLong::empty);
    }
    /** Long-run buffer timing does not mistake a meal, occupied machine or current batch for lost capacity. */
    static long reserveLeadTicks(FrontierWorldState state, SubjectId home) {
        var recipe = BakeryRecipes.bread(state);
        var definition = state.bootstrap().ruleset().workCatalog().require(recipe.product().itemKind(), WorkOperation.PRODUCE);
        long operationWork = operationWork(recipe, definition);
        if (recipe.processingMode() == ProductionRecipe.ProcessingMode.AUTOMATIC) return operationWork;
        int speed = SettlementWorkPolicy.permissions(state, home).workers(ResidentWorkKind.PRODUCTION).stream()
                .map(id -> state.humanPopulation().resident(id))
                .filter(r -> state.actorLocations().get(r.id()).condition().status() == ActorLifeStatus.ALIVE && r.capability(definition.capability()) > 0)
                .mapToInt(r -> ResidentWorkStatistics.speedPermille(r, definition, state.bootstrap().ruleset().workCatalog()))
                .min().orElse(0);
        if (speed == 0) return state.bootstrap().ruleset().goodsTrade().supplyPlanning().require(recipe.product().itemKind()).leadTicks();
        var schedule = state.humanPopulation().schedule(home);
        long dailyWork = schedule.segments().stream().filter(s -> s.window() == SettlementDailySchedule.Window.WORK)
                .mapToLong(s -> s.endExclusive() - s.startInclusive()).sum();
        long ticks = Math.ceilDiv(Math.multiplyExact(operationWork, 1000L), speed);
        if (dailyWork == 0) return state.bootstrap().ruleset().goodsTrade().supplyPlanning().require(recipe.product().itemKind()).leadTicks();
        return SupplyPlanningWindow.completionDelay(schedule, ticks);
    }
    static java.util.Optional<Capacity> capacity(FrontierWorldState state, SubjectId home, SupplyPlanningWindow window) {
        var recipe = BakeryRecipes.bread(state);
        var facility = FrontierWorldStateSupport.settlement(state.bootstrap(), home).structures().stream()
                .filter(value -> value.kind() == StructureKind.WORKSHOP).findFirst().orElseThrow();
        if (!FacilityFunctionAvailability.mayUse(state, facility.id())) return java.util.Optional.empty();
        var selected = ProductionStationSelection.usable(state, facility.id(), recipe.id());
        if (selected.isEmpty()) return java.util.Optional.empty();
        var station = selected.orElseThrow(); var machine = state.productionStations().require(station.id());
        if (machine.pendingFuel().isPresent() || machine.pendingRecipe().isPresent()
                || machine.operation().filter(operation -> operation.initiator() instanceof ProductionOperation.PlayerInitiator).isPresent())
            return java.util.Optional.empty();
        var definition = state.bootstrap().ruleset().workCatalog().require(recipe.product().itemKind(), WorkOperation.PRODUCE);
        long operationWork = operationWork(recipe, definition);
        if (recipe.processingMode() == ProductionRecipe.ProcessingMode.AUTOMATIC)
            return java.util.Optional.of(new Capacity(station.id(), operationWork, operationWork, 0, java.util.Map.of()));
        var assignments = HumanAssignmentProjection.compile(state);
        java.util.function.Predicate<ResidentProfile> eligible = resident -> {
                    var assignment = assignments.assignment(resident.id());
                    if (!assignment.active()) return true;
                    if (assignment.kind() != HumanAssignmentKind.PRODUCTION) return false;
                    var job = state.productionJobs().get(assignment.ownerId().orElseThrow());
                    return job != null && job.facilityId().equals(facility.id()) && job.stationWork().isPresent()
                            && job.stationWork().orElseThrow().block().isEmpty();
                };
        var workers = SupplyLabourCapacity.candidates(state, home, ResidentWorkKind.PRODUCTION, definition.capability(), eligible)
                .stream().filter(eligible).toList();
        int speed = workers.stream().mapToInt(resident -> ResidentWorkStatistics.speedPermille(resident, definition,
                state.bootstrap().ruleset().workCatalog())).min().orElse(0);
        if (speed == 0) return java.util.Optional.empty();
        var schedule = state.humanPopulation().schedule(home);
        long dailyWork = schedule.segments().stream().filter(segment -> segment.window() == SettlementDailySchedule.Window.WORK)
                .mapToLong(segment -> segment.endExclusive() - segment.startInclusive()).sum();
        if (dailyWork == 0) return java.util.Optional.empty();
        long workTicks = Math.ceilDiv(Math.multiplyExact(operationWork, 1_000L), speed);
        // Only an existing unfinished operation precedes the new one. Its owner
        // supplies recorded work; no optimistic extrapolation across a suspension.
        long preceding = machine.operation().filter(operation -> !operation.transformed()).map(operation ->
                operation.progress().map(progress -> Math.ceilDiv(progress.requiredMilliWork()
                        - progress.completedMilliWork(), speed)).orElse(workTicks)).orElse(0L);
        long batchTicks = Math.ceilDiv(Math.multiplyExact(workTicks, schedule.dayTicks()), dailyWork);
        return java.util.Optional.of(new Capacity(station.id(), SupplyPlanningWindow.completionDelay(schedule,
                Math.addExact(preceding, workTicks)), batchTicks, workTicks,
                SupplyLabourCapacity.over(state, home, workers.stream().map(ResidentProfile::id).toList(), window)));
    }
    private static long operationWork(ProductionRecipe recipe, WorkCatalog.Definition definition) {
        return Math.multiplyExact(definition.workUnits(), ProductionBatchSizing.maximumRecipeBatches(recipe));
    }
    private BakeryProcessingForecast() { }
}
