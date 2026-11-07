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
        var actors = home.residents().stream().limit(4).map(Resident::id).toList();
        assertTrue(point.waitingSurfaces().size() >= actors.size());
        for (int index=0; index<actors.size(); index++) {
            var id=actors.get(index);
            state=state.withActorBody(id,point.waitingSurfaces().get(index).standingBody());
            state=state.withHumanPopulation(state.humanPopulation().accrueHunger(id,27000));
            var selected=ResidentMealProcess.selectSourceAtYield(state,id,27000).orElseThrow();
            state=ResidentMealProcess.reduceStarted(state,id,selected);
        }
        var snapshot=state;
        assertEquals(4,actors.stream().filter(id->ResidentMealServiceAccess.available(snapshot,source,id)).count(),
                "independent COLD approaches retain their accepted timing; atomic service stays exclusive");
        var physical=state;
        for(var id:actors)physical=ModeledActorBodyFacts.present(physical,id);
        var physicalSnapshot=physical;
        var admitted=actors.stream().filter(id->ResidentMealServiceAccess.available(physicalSnapshot,source,id)).toList();
        assertEquals(1,admitted.size(),"one physical doorway cannot grant every hungry visitor simultaneous entry");
        var other=actors.stream().filter(id->!id.equals(admitted.getFirst())).findFirst().orElseThrow();
        var moved=state.withActorBody(other,point.waitingSurfaces().getLast().standingBody());
        assertTrue(ServiceAccessCoordinator.wakePoints(state,moved).contains(source),"waiting arrivals must wake parked distance arbitration");
        var after=finishMeals(state,actors);
        for(var id:actors) {
            assertFalse(after.humanPopulation().meals().containsKey(id),"queued meal must reach nutrition and release: "+id);
            assertEquals(ResidentNutritionStatus.NOURISHED,after.humanPopulation().nutrition(id).status());
            assertTrue(ServiceAccessCoordinator.boundary(after,source).cleared(after.actorLocations().get(id).body()));
        }
        assertTrue(after.inventory().fungibleResources().claims().values().stream().noneMatch(claim->actors.contains(claim.claimantId())));
    }
    static FrontierWorldState finishMeals(FrontierWorldState state,List<SubjectId> actors) {
        var codec=new FrontierWorldStateCodec();state=codec.decode(codec.encode(state));
        var base=FrontierWorldRuntimeDefinition.configuration(state.bootstrap());
        var schedules=new ArrayList<ScheduledAction>();
        for(var id:actors) {
            schedules.add(ResidentMealProcess.progress(state.humanPopulation().meals().get(id),27001));
            schedules.add(ResidentActivityProcess.review(id,60000));
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
            if(actors.stream().noneMatch(engine.canonicalState().state().humanPopulation().meals()::containsKey))break;
        }
        return engine.canonicalState().state();
    }

}
