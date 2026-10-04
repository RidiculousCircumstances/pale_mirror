package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import org.junit.jupiter.api.Test;
import java.util.LinkedHashMap;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ResidentStarvationProcessTest {
    @Test void healthFactIsExactReplayFencedAndDoesNotMutateFoodWorkOrVitality() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:starvation-owner"), 419L));
        var resident = state.humanPopulation().residents().keySet().stream().sorted().findFirst().orElseThrow();
        var fact = (ResidentStarvationIntegrated) ResidentStarvationProcess.planBeforeNutrition(state, resident, 96_000).getFirst().payload();
        assertEquals(fact, FrontierWorldRuntimeDefinition.payloadCodecs().decode(fact.type(),
                FrontierWorldRuntimeDefinition.payloadCodecs().encode(fact)));
        var next = ResidentStarvationProcess.reduce(state, resident, fact);
        assertEquals(state.humanPopulation().nutrition(), next.humanPopulation().nutrition());
        assertEquals(state.inventory(), next.inventory()); assertEquals(state.actorLocations(), next.actorLocations());
        assertEquals(state.productionJobs(), next.productionJobs());
        assertThrows(IllegalArgumentException.class, () -> ResidentStarvationProcess.reduce(next, resident, fact));
        assertThrows(IllegalArgumentException.class, () -> ResidentStarvationProcess.reduce(state,
                new SubjectId("resident:missing"), fact));
        assertThrows(IllegalArgumentException.class, () -> ResidentStarvationProcess.reduce(state, resident,
                new ResidentStarvationIntegrated(resident, 1, 96_000, fact.previous(), fact.next())));
        assertThrows(IllegalArgumentException.class, () -> ResidentStarvationProcess.reduce(state, resident,
                new ResidentStarvationIntegrated(resident, 0, 96_000, fact.previous(),
                    new ResidentStarvation(fact.next().severityUnits() + 1, 0, 0))));
        var nutritionAtEnd = state.humanPopulation().nutrition(resident).accrueThrough(96_000,
                state.bootstrap().ruleset().residentLife(), state.humanPopulation().resident(resident)
                    .characteristics().effectiveMetabolismPermille(96_000));
        var settled = ResidentNeedProcess.reduce(next, resident, new ResidentNeedIntegrated(resident, 96_000, 0,
                nutritionAtEnd.satietyUnits(), nutritionAtEnd.fractionalProgress()));
        assertEquals(settled.humanPopulation().health(), new FrontierWorldStateCodec().decode(
                new FrontierWorldStateCodec().encode(settled)).humanPopulation().health());
    }

    @Test void ordinaryNeedClockCommitsHealthAndNutritionTogetherAndRecoveryKeepsThatClock() {
        var base = FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:starvation-engine"), 419L);
        var state = base.initialState();
        var resident = state.humanPopulation().residents().keySet().stream().sorted().findFirst().orElseThrow();
        var people = state.humanPopulation(); var nutrition = new LinkedHashMap<>(people.nutrition());
        nutrition.put(resident, new ResidentNutrition(ResidentNutritionStatus.STARVING, 0, 0, 0));
        state = state.withHumanPopulation(new HumanPopulation(people.households(), people.residents(), people.birthJobs(),
                people.health(), people.quarantines(), people.migrations(), people.provisions(), nutrition,
                people.medicalOperations(), people.schedules(), people.meals(), people.mealResourceObligations()));
        var configuration = new FrontierEngineConfiguration<>(base.worldId(), state, new SimInstant(0),
                base.commandPlanner(), base.scheduledPlanner(), base.reducer(), base.stateCodec(), base.projectionMapper(),
                base.limits(), List.of(ResidentNeedProcess.review(resident, 24_000)), base.transactionCommitter());
        var engine = FrontierEngines.create(configuration);
        engine.advanceTo(new SimInstant(24_000), new WorkBudget(100, 1_000));
        assertEquals(EngineStatus.Kind.ACTIVE, engine.status().kind(), engine.status().failureDetail().orElse(""));
        var recovered = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        assertEquals(100, recovered.humanPopulation().health(resident).starvation().severityUnits());
        assertEquals(24_000, recovered.humanPopulation().nutrition(resident).lastEvaluatedTick());
        assertEquals(List.of(ResidentNeedProcess.review(resident, 48_000)), engine.checkpoint().schedules());
    }
}
