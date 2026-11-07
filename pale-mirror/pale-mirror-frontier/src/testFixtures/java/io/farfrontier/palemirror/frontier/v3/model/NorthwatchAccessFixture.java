package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.process.ResidentNeedProcess;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import java.util.*;

/** Production kitchen and historical stacked-barrel contact; four ordinary hunger clocks.
 * No sword, appointment, meal, path, effect or completion is seeded. */
final class NorthwatchAccessFixture {
    static FrontierEngineConfiguration<FrontierWorldState,FrontierWorldProjection> configuration(WorldId world,long seed) {
        var base=FrontierWorldRuntimeDefinition.configuration(world,seed,FrontierRulesets.installed("frontier-v3-clinical-care-r35"));
        var state=base.initialState();var home=state.bootstrap().settlements().getFirst();
        var kitchen=home.structures().stream().filter(building->building.kind()==StructureKind.KITCHEN).findFirst().orElseThrow();
        var position=kitchen.placement().world(new BlockPosition(15,3,18));
        var upper=state.inventory().surfaces().values().stream().filter(surface->surface.fixed()&&surface.position().equals(position))
                .map(ContainerSurface::containerId).findFirst().orElseThrow();
        var inventory=state.inventory();var record=inventory.containers().get(upper);var policy=record.storage().orElseThrow();
        var historical=new SurfaceAnchor(kitchen.placement().world(new BlockPosition(14,2,18)));
        var records=ImmutableLookupMap.withEntry(inventory.containers(),upper,record.withStorage(new ResourceStoragePolicy(
                policy.endpoint().withStation(historical),policy.filter(),policy.priority(),policy.role())));
        state=state.withInventory(new ExactInventory(records,inventory.items(),inventory.cargo(),inventory.playerItems(),
                inventory.worldCarrierItems(),inventory.conflicts(),inventory.surfaces(),inventory.economics(),inventory.fungibleResources()));
        var actors=Set.of(new SubjectId("resident:1-11"),new SubjectId("resident:1-14"),new SubjectId("resident:1-17"),new SubjectId("resident:1-20"));
        var pantry=CommunalDiningPlan.initialFoodStorage(state.bootstrap(),home.id());
        var point=ServiceBoundaryComposition.geometry(state,ServiceBoundaryComposition.declaration(state,pantry).identity());
        var waiting=point.waitingSurfaces().stream().filter(station->station.y()==kitchen.placement().anchor().y()).toList();
        var visitors=actors.stream().sorted().toList();
        for(int i=0;i<visitors.size();i++)state=state.withActorBody(visitors.get(i),waiting.get(i).standingBody());
        var permissions=SettlementWorkPolicy.permissions(state,home.id());
        for(var actor:actors)permissions=permissions.withoutResident(actor);
        state=state.withStrategicPlans(state.strategicPlans().withWorkPermissions(home.id(),permissions));
        var schedules=new ArrayList<>(base.initialSchedules());
        for(var actor:actors) {
            var previous=state.humanPopulation().resident(actor).characteristics();
            state=state.withHumanPopulation(state.humanPopulation().changeMetabolism(actor,previous,previous.withBaseMetabolism(4000),0,state.bootstrap().ruleset().residentLife()));
        }
        var people=state.humanPopulation();var nutrition=new LinkedHashMap<>(people.nutrition());
        for(var actor:actors)nutrition.put(actor,new ResidentNutrition(ResidentNutritionStatus.NOURISHED,
                state.bootstrap().ruleset().residentLife().eatBelowUnits()+100,0,0));
        people=new HumanPopulation(people.households(),people.residents(),people.birthJobs(),people.health(),people.quarantines(),
                people.migrations(),people.provisions(),nutrition,people.medicalOperations(),people.schedules(),people.meals(),people.mealResourceObligations());
        state=state.withHumanPopulation(people);
        schedules.removeIf(action->actors.contains(action.subject())&&action.kind().equals(ResidentNeedProcess.REVIEW));
        for(var actor:actors)schedules.add(ResidentNeedProcess.review(actor,people.nutrition(actor).nextThresholdTick(state.bootstrap().ruleset().residentLife(),4000)));
        return new FrontierEngineConfiguration<>(base.worldId(),state,base.initialInstant(),base.commandPlanner(),base.scheduledPlanner(),base.reducer(),
                base.stateCodec(),base.projectionMapper(),base.limits(),schedules,base.transactionCommitter(),base.stateValidator(),base.executionMetrics(),base.kernelQuarantineReporter());
    }
    private NorthwatchAccessFixture() { }
}
