package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind;
import io.farfrontier.palemirror.frontier.v3.process.*;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConcurrentMealAccessTest {
    @Test void simultaneousHungryVisitorsHaveOneEntranceWinnerAndAllFinishThroughTheRealSchedule() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:concurrent-pantry"),139,
                FrontierRulesets.installed("frontier-v3-clinical-care-r35")));
        var home = state.bootstrap().settlements().getFirst();
        var source = CommunalDiningPlan.initialFoodStorage(state.bootstrap(),home.id());
        var point = ServiceBoundaryComposition.geometry(state, ServiceBoundaryComposition.declaration(state,source).identity());
        var actors = home.residents().stream().limit(Math.min(4, point.waitingSurfaces().size())).map(Resident::id).toList();
        assertTrue(actors.size() >= 2, "the authored kitchen must provide a genuinely concurrent waiting cohort");
        state = AutomaticStationProductionTest.supply(state, state.inventory().containers().get(source),
                "minecraft:bread", 8, "concurrent-pantry");
        int initialBread = state.inventory().fungibleResources().totalQuantity(home.id(), "minecraft:bread");
        // The precondition is simultaneous hunger, independent of each generated metabolism.
        var people = state.humanPopulation();
        var nutrition = new HashMap<>(people.nutrition());
        for (var id : actors) nutrition.put(id, new ResidentNutrition(ResidentNutritionStatus.HUNGRY,
                state.bootstrap().ruleset().residentLife().eatBelowUnits() - 1, 27000, 0));
        state = state.withHumanPopulation(new HumanPopulation(people.households(), people.residents(), people.birthJobs(),
                people.health(), people.quarantines(), people.migrations(), people.provisions(), nutrition,
                people.medicalOperations(), people.schedules(), people.meals(), people.mealResourceObligations(),
                people.clinical(), people.restPlaces(), people.seats()));
        for (int index=0; index<actors.size(); index++) {
            var id=actors.get(index);
            state=state.withActorBody(id,point.waitingSurfaces().get(index).standingBody());
            var selected=ResidentMealProcess.selectSourceAtYield(state,id,27000);
            if(selected.isPresent())state=ResidentMealProcess.reduceStarted(state,id,selected.orElseThrow());
        }
        var snapshot=state;
        var started=actors.stream().filter(id->snapshot.humanPopulation().meals().containsKey(id)).toList();
        assertTrue(started.size()>=2,"at least two actual meal owners must contend for the same entrance");
        assertEquals(started.size(),started.stream().filter(id->ResidentMealServiceAccess.available(snapshot,source,id)).count(),
                "independent COLD approaches retain their accepted timing; atomic service stays exclusive");
        var physical=state;
        for(var id:actors)physical=ModeledActorBodyFacts.present(physical,id);
        var physicalSnapshot=physical;
        var admitted=started.stream().filter(id->ResidentMealServiceAccess.available(physicalSnapshot,source,id)).toList();
        assertEquals(1,admitted.size(),"one physical doorway cannot grant every hungry visitor simultaneous entry");
        var other=started.stream().filter(id->!id.equals(admitted.getFirst())).findFirst().orElseThrow();
        var moved=state.withActorBody(other,point.waitingSurfaces().getLast().standingBody());
        assertTrue(ServiceAccessCoordinator.wakePoints(state,moved).contains(source),"waiting arrivals must wake parked distance arbitration");
        var after=finishMeals(state,actors);
        for(var id:actors) {
            assertFalse(after.humanPopulation().meals().containsKey(id),"queued meal must reach nutrition and release: "+id);
            assertEquals(ResidentNutritionStatus.NOURISHED,after.humanPopulation().nutrition(id).status());
            assertTrue(ServiceAccessCoordinator.boundary(after,source).cleared(after.actorLocations().get(id).body()));
        }
        assertTrue(after.inventory().fungibleResources().claims().values().stream().noneMatch(claim->actors.contains(claim.claimantId())));
        assertEquals(initialBread - actors.size(), after.inventory().fungibleResources().totalQuantity(home.id(), "minecraft:bread"),
                "each visitor consumes exactly one finite supplied portion");
    }
    static FrontierWorldState finishMeals(FrontierWorldState state,List<SubjectId> actors) {
        var codec=new FrontierWorldStateCodec();state=codec.decode(codec.encode(state));
        var base=FrontierWorldRuntimeDefinition.configuration(state.bootstrap());
        var schedules=new ArrayList<ScheduledAction>();
        for(var id:actors) {
            var meal=state.humanPopulation().meals().get(id);
            if(meal!=null)schedules.add(ResidentMealProcess.progress(meal,27001));
            schedules.add(ResidentActivityProcess.review(id,27001));
            var resident=state.humanPopulation().resident(id);
            schedules.add(ResidentNeedProcess.review(id,state.humanPopulation().nutrition(id).nextThresholdTick(
                    state.bootstrap().ruleset().residentLife(),resident.characteristics().effectiveMetabolismPermille(27000))));
        }
        var engine=FrontierEngines.createCanonicalStateAccess(new FrontierEngineConfiguration<>(base.worldId(),state,new SimInstant(27000),
                base.commandPlanner(),base.scheduledPlanner(),base.reducer(),base.stateCodec(),base.projectionMapper(),base.limits(),
                schedules,base.transactionCommitter(),base.stateValidator(),base.executionMetrics(),base.kernelQuarantineReporter()));
        for(long tick=27001; tick<33000; tick+=20) {
            var result=engine.advanceTo(new SimInstant(tick),new WorkBudget(1000,1000));
            assertEquals(EngineStatus.Kind.ACTIVE,result.status().kind(),result.status()+" initial="+schedules+" current="+engine.checkpoint().schedules().stream().filter(action->action.kind().equals(ResidentNeedProcess.REVIEW)).toList());
            var current=engine.canonicalState().state();
            if(actors.stream().allMatch(id->!current.humanPopulation().meals().containsKey(id)
                    &&current.humanPopulation().nutrition(id).status()==ResidentNutritionStatus.NOURISHED))break;
        }
        return engine.canonicalState().state();
    }

}
