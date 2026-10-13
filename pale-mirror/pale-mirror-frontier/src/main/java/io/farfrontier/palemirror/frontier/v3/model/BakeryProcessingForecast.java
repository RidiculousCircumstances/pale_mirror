package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.OptionalLong;

/** Bakery-owned conservative supply estimate. No route search, assignment mutation or market recursion. */
final class BakeryProcessingForecast {
    static OptionalLong firstOutputDelay(FrontierWorldState state, SubjectId home) {
        var recipe = BakeryRecipes.bread(state);
        var facility = FrontierWorldStateSupport.settlement(state.bootstrap(), home).structures().stream()
                .filter(value -> value.kind() == StructureKind.WORKSHOP).findFirst().orElseThrow();
        if (state.structureConditions().get(facility.id()) != StructureCondition.INTACT) return OptionalLong.empty();
        var selected = ProductionStationSelection.usable(state, facility.id(), recipe.id());
        if (selected.isEmpty()) return OptionalLong.empty();
        var station = selected.orElseThrow(); var machine = state.productionStations().require(station.id());
        if (machine.pendingFuel().isPresent() || machine.pendingRecipe().isPresent()
                || machine.operation().filter(operation -> operation.initiator() instanceof ProductionOperation.PlayerInitiator).isPresent())
            return OptionalLong.empty();
        var definition = state.bootstrap().ruleset().workCatalog().require(recipe.product().itemKind(), WorkOperation.PRODUCE);
        if (recipe.processingMode() == ProductionRecipe.ProcessingMode.AUTOMATIC)
            return OptionalLong.of(definition.workUnits());
        var assignments = HumanAssignmentProjection.compile(state);
        int speed = SettlementWorkPolicy.permissions(state, home).workers(ResidentWorkKind.BAKING).stream()
                .map(id -> state.humanPopulation().resident(id))
                .filter(resident -> state.actorLocations().get(resident.id()).condition().status() == ActorLifeStatus.ALIVE
                        && resident.capability(definition.capability()) > 0)
                .filter(resident -> {
                    var assignment = assignments.assignment(resident.id());
                    if (!assignment.active()) return true;
                    if (assignment.kind() != HumanAssignmentKind.PRODUCTION) return false;
                    var job = state.productionJobs().get(assignment.ownerId().orElseThrow());
                    return job != null && job.facilityId().equals(facility.id()) && job.bakeryWork().isPresent()
                            && job.bakeryWork().orElseThrow().block().isEmpty();
                })
                .mapToInt(resident -> ResidentWorkStatistics.speedPermille(resident, definition, state.bootstrap().ruleset().workCatalog()))
                .max().orElse(0);
        if (speed == 0) return OptionalLong.empty();
        var schedule = state.humanPopulation().schedule(home);
        long dailyWork = schedule.segments().stream().filter(segment -> segment.window() == SettlementDailySchedule.Window.WORK)
                .mapToLong(segment -> segment.endExclusive() - segment.startInclusive()).sum();
        long workTicks = Math.ceilDiv(Math.multiplyExact(definition.workUnits(), 1_000L), speed);
        // Include the worst daily phase and one preceding batch; actual admission still proves travel/access.
        long days = Math.addExact(1L, Math.ceilDiv(Math.multiplyExact(workTicks, 2L), dailyWork));
        return OptionalLong.of(Math.multiplyExact(days, schedule.dayTicks()));
    }
    private BakeryProcessingForecast() { }
}
