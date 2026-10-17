package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.OptionalLong;

/** Bakery-owned conservative supply estimate. No route search, assignment mutation or market recursion. */
final class BakeryProcessingForecast {
    record Capacity(SubjectId station, long firstOutputDelay, long batchTicks, long labourTicks,
                    java.util.Map<String, Long> labourCapacity) { }
    static OptionalLong firstOutputDelay(FrontierWorldState state, SubjectId home) {
        return capacity(state, home).map(c -> OptionalLong.of(c.firstOutputDelay())).orElseGet(OptionalLong::empty);
    }
    /** Long-run buffer timing does not mistake a meal, occupied machine or current batch for lost capacity. */
    static long reserveLeadTicks(FrontierWorldState state, SubjectId home) {
        var recipe = BakeryRecipes.bread(state);
        var definition = state.bootstrap().ruleset().workCatalog().require(recipe.product().itemKind(), WorkOperation.PRODUCE);
        if (recipe.processingMode() == ProductionRecipe.ProcessingMode.AUTOMATIC) return definition.workUnits();
        int speed = SettlementWorkPolicy.permissions(state, home).workers(ResidentWorkKind.BAKING).stream()
                .map(id -> state.humanPopulation().resident(id))
                .filter(r -> state.actorLocations().get(r.id()).condition().status() == ActorLifeStatus.ALIVE && r.capability(definition.capability()) > 0)
                .mapToInt(r -> ResidentWorkStatistics.speedPermille(r, definition, state.bootstrap().ruleset().workCatalog()))
                .min().orElse(0);
        if (speed == 0) return state.bootstrap().ruleset().goodsTrade().supplyPlanning().require(recipe.product().itemKind()).leadTicks();
        var schedule = state.humanPopulation().schedule(home);
        long dailyWork = schedule.segments().stream().filter(s -> s.window() == SettlementDailySchedule.Window.WORK)
                .mapToLong(s -> s.endExclusive() - s.startInclusive()).sum();
        long ticks = Math.ceilDiv(Math.multiplyExact(definition.workUnits(), 1000L), speed);
        return Math.multiplyExact(Math.addExact(1L, Math.ceilDiv(Math.multiplyExact(ticks, 2L), dailyWork)), schedule.dayTicks());
    }
    static java.util.Optional<Capacity> capacity(FrontierWorldState state, SubjectId home) {
        var recipe = BakeryRecipes.bread(state);
        var facility = FrontierWorldStateSupport.settlement(state.bootstrap(), home).structures().stream()
                .filter(value -> value.kind() == StructureKind.WORKSHOP).findFirst().orElseThrow();
        if (state.structureConditions().get(facility.id()) != StructureCondition.INTACT) return java.util.Optional.empty();
        var selected = ProductionStationSelection.usable(state, facility.id(), recipe.id());
        if (selected.isEmpty()) return java.util.Optional.empty();
        var station = selected.orElseThrow(); var machine = state.productionStations().require(station.id());
        if (machine.pendingFuel().isPresent() || machine.pendingRecipe().isPresent()
                || machine.operation().filter(operation -> operation.initiator() instanceof ProductionOperation.PlayerInitiator).isPresent())
            return java.util.Optional.empty();
        var definition = state.bootstrap().ruleset().workCatalog().require(recipe.product().itemKind(), WorkOperation.PRODUCE);
        if (recipe.processingMode() == ProductionRecipe.ProcessingMode.AUTOMATIC)
            return java.util.Optional.of(new Capacity(station.id(), definition.workUnits(), definition.workUnits(), 0, java.util.Map.of()));
        var assignments = HumanAssignmentProjection.compile(state);
        java.util.function.Predicate<ResidentProfile> eligible = resident -> {
                    var assignment = assignments.assignment(resident.id());
                    if (!assignment.active()) return true;
                    if (assignment.kind() != HumanAssignmentKind.PRODUCTION) return false;
                    var job = state.productionJobs().get(assignment.ownerId().orElseThrow());
                    return job != null && job.facilityId().equals(facility.id()) && job.bakeryWork().isPresent()
                            && job.bakeryWork().orElseThrow().block().isEmpty();
                };
        var workers = SupplyLabourCapacity.candidates(state, home, ResidentWorkKind.BAKING, definition.capability(), eligible)
                .stream().filter(eligible).toList();
        int speed = workers.stream().mapToInt(resident -> ResidentWorkStatistics.speedPermille(resident, definition,
                state.bootstrap().ruleset().workCatalog())).min().orElse(0);
        if (speed == 0) return java.util.Optional.empty();
        var schedule = state.humanPopulation().schedule(home);
        long dailyWork = schedule.segments().stream().filter(segment -> segment.window() == SettlementDailySchedule.Window.WORK)
                .mapToLong(segment -> segment.endExclusive() - segment.startInclusive()).sum();
        if (dailyWork == 0) return java.util.Optional.empty();
        long workTicks = Math.ceilDiv(Math.multiplyExact(definition.workUnits(), 1_000L), speed);
        // Include the worst daily phase and one preceding batch; actual admission still proves travel/access.
        long days = Math.addExact(1L, Math.ceilDiv(Math.multiplyExact(workTicks, 2L), dailyWork));
        long batchTicks = Math.ceilDiv(Math.multiplyExact(workTicks, schedule.dayTicks()), dailyWork);
        return java.util.Optional.of(new Capacity(station.id(), Math.multiplyExact(days, schedule.dayTicks()), batchTicks, workTicks,
                SupplyLabourCapacity.day(state, home, workers.stream().map(ResidentProfile::id).toList())));
    }
    private BakeryProcessingForecast() { }
}
