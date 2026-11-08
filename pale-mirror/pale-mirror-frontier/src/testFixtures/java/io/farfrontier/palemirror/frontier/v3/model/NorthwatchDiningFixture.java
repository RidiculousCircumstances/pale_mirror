package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.process.ResidentNeedProcess;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import java.util.*;

/** Three nearby unassigned visitors with ordinary hunger clocks. No seat, meal, route or food outcome. */
final class NorthwatchDiningFixture {
    static FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> configuration(WorldId world, long seed) {
        return configuration(world,seed,false);
    }
    static FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> configuration(WorldId world,long seed,boolean routines) {
        var base = FrontierWorldRuntimeDefinition.configuration(world, seed, FrontierRulesets.installed(routines?"frontier-v3-living-residents-r36":"frontier-v3-clinical-care-r35"));
        var state = base.initialState(); var home = state.bootstrap().settlements().getFirst();
        var pantry = CommunalDiningPlan.initialFoodStorage(state.bootstrap(), home.id());
        var boundary = ServiceBoundaryComposition.geometry(state, ServiceBoundaryComposition.declaration(state, pantry).identity());
        var kitchen = home.structures().stream().filter(building -> building.kind() == StructureKind.KITCHEN).findFirst().orElseThrow();
        var initialPlaces = new LinkedHashSet<SurfaceAnchor>(boundary.waitingSurfaces());
        boundary.egressSurfaces().stream().sorted(Comparator.comparingInt(SurfaceAnchor::x).thenComparingInt(SurfaceAnchor::z)).forEach(initialPlaces::add);
        io.farfrontier.palemirror.frontier.v3.model.buildings.BuildingSpatialPlan.of(kitchen).publicEntrances()
                .forEach(port -> initialPlaces.addAll(port.exteriorApproach()));
        var waiting = List.copyOf(initialPlaces);
        var actors = routines?List.of(new SubjectId("resident:1-11"),new SubjectId("resident:1-12"),new SubjectId("resident:1-14"),new SubjectId("resident:1-17"))
                :List.of(new SubjectId("resident:1-11"), new SubjectId("resident:1-14"), new SubjectId("resident:1-17"));
        var permissions = SettlementWorkPolicy.permissions(state, home.id());
        for (int index = 0; index < actors.size(); index++) {
            var actor = actors.get(index); state = state.withActorBody(actor, waiting.get(index).standingBody());
            permissions = permissions.withoutResident(actor);
            var prior = state.humanPopulation().resident(actor).characteristics();
            state = state.withHumanPopulation(state.humanPopulation().changeMetabolism(actor, prior, prior.withBaseMetabolism(4000), 0,
                    state.bootstrap().ruleset().residentLife()));
        }
        state = state.withStrategicPlans(state.strategicPlans().withWorkPermissions(home.id(), permissions));
        var people = state.humanPopulation(); var nutrition = new LinkedHashMap<>(people.nutrition());
        for (var actor : actors) nutrition.put(actor, new ResidentNutrition(ResidentNutritionStatus.NOURISHED,
                state.bootstrap().ruleset().residentLife().eatBelowUnits() + 100, 0, 0));
        people = new HumanPopulation(people.households(), people.residents(), people.birthJobs(), people.health(), people.quarantines(),
                people.migrations(), people.provisions(), nutrition, people.medicalOperations(), people.schedules(), people.meals(), people.mealResourceObligations(),
                people.clinical(), people.restPlaces(), people.seats());
        state = state.withHumanPopulation(people);
        var schedules = new ArrayList<>(base.initialSchedules());
        schedules.removeIf(action -> actors.contains(action.subject()) && action.kind().equals(ResidentNeedProcess.REVIEW));
        for (var actor : actors) schedules.add(ResidentNeedProcess.review(actor,
                people.nutrition(actor).nextThresholdTick(state.bootstrap().ruleset().residentLife(), 4000)));
        return new FrontierEngineConfiguration<>(base.worldId(), state, base.initialInstant(), base.commandPlanner(), base.scheduledPlanner(), base.reducer(),
                base.stateCodec(), base.projectionMapper(), base.limits(), schedules, base.transactionCommitter(), base.stateValidator(), base.executionMetrics(), base.kernelQuarantineReporter());
    }
    private NorthwatchDiningFixture() { }
}
