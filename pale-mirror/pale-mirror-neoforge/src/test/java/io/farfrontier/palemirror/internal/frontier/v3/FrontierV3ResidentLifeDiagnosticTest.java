package io.farfrontier.palemirror.internal.frontier.v3;

import com.google.gson.JsonParser;
import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.ModeledActorBodyFacts;
import io.farfrontier.palemirror.frontier.v3.model.ResidentNutritionStatus;
import io.farfrontier.palemirror.frontier.v3.process.ResidentNeedProcess;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class FrontierV3ResidentLifeDiagnosticTest {
    @Test void settlementFoodCountsOnlyLivingResidentsAndIgnoresRetainedDeadNeedTimers() {
        var config = FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:dead-food-counts"), 47L);
        var state = config.initialState();
        var settlement = state.bootstrap().settlements().getFirst();
        var residents = settlement.residents();
        var pending = new java.util.ArrayList<io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction>();
        var statuses = List.of(ResidentNutritionStatus.NOURISHED, ResidentNutritionStatus.HUNGRY, ResidentNutritionStatus.STARVING);
        for (int index = 0; index < statuses.size(); index++) {
            var resident = residents.get(index).id();
            var nutrition = state.humanPopulation().nutrition(resident);
            var rules = state.bootstrap().ruleset().residentLife();
            int rate = state.humanPopulation().resident(resident).characteristics().effectiveMetabolismPermille(0L);
            long tick = switch (statuses.get(index)) {
                case NOURISHED -> 1L;
                case HUNGRY -> nutrition.nextThresholdTick(rules, rate);
                case STARVING -> nutrition.ticksUntilEmpty(rules, rate);
            };
            state = state.withHumanPopulation(state.humanPopulation().accrueHunger(resident, tick, rules));
            assertEquals(statuses.get(index), state.humanPopulation().nutrition(resident).status());
            state = ModeledActorBodyFacts.present(state, resident);
            state = ModeledActorBodyFacts.died(state, resident, state.actorLocations().get(resident).body(), "environment", tick);
            pending.add(ResidentNeedProcess.review(resident, 100_000L + index));
        }
        var livingNeed = ResidentNeedProcess.review(residents.get(3).id(), 200_000L);
        pending.add(livingNeed);
        var checkpoint = new CheckpointImage(config.worldId(), new Revision(3L), new SimInstant(100_000L),
                new byte[] {1}, pending, List.of());
        var bytes = config.stateCodec().encode(state);
        var food = JsonParser.parseString(FrontierV3DiagnosticJson.render("settlement", settlement.id().value(),
                checkpoint, state, Optional.empty()).substring(FrontierV3DiagnosticJson.PREFIX.length()))
                .getAsJsonObject().getAsJsonObject("food");
        assertEquals(residents.size() - 3, food.get("nourished").getAsInt());
        assertEquals(0, food.get("hungry").getAsInt());
        assertEquals(0, food.get("starving").getAsInt());
        assertEquals(livingNeed.dueAt().ticks(), food.get("nextNeedReviewAt").getAsLong());
        assertArrayEquals(bytes, config.stateCodec().encode(state), "diagnostics must not repair retained state");
    }

    @Test void deceasedMealDiagnosticsSeparateRetainedResourcesFromActiveEating() {
        var fixture = io.farfrontier.palemirror.frontier.v3.model.FrontierV3FixtureCatalog.configuration(
                "resident-meal-after-cold-take", new WorldId("frontier:dead-meal-diagnostic"), 41L);
        var resident = new SubjectId("resident:6-1");
        var before = io.farfrontier.palemirror.frontier.v3.model.ModeledActorBodyFacts.present(fixture.initialState(), resident);
        var body = io.farfrontier.palemirror.frontier.v3.model.ActorBodyAuthority.current(before, resident);
        var actor = before.actorLocations().get(resident);
        var meal = before.humanPopulation().meals().get(resident);
        long tick = fixture.initialInstant().ticks() + 1L;
        var state = io.farfrontier.palemirror.frontier.v3.model.ActorBodyAuthority.died(before,
                new io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyDied(body, actor.body(), actor.condition().health(),
                        Optional.empty(), Optional.of(meal.executionId()), "environment"),
                io.farfrontier.palemirror.frontier.v3.model.FrontierActorDeathConsequences.INSTANCE, tick);
        var checkpoint = new CheckpointImage(fixture.worldId(), new Revision(3L), new SimInstant(tick),
                new byte[] {1}, List.of(), List.of());
        var json = JsonParser.parseString(FrontierV3DiagnosticJson.render("resident_life", resident.value(),
                checkpoint, state, Optional.empty()).substring(FrontierV3DiagnosticJson.PREFIX.length())).getAsJsonObject();
        assertEquals("DEAD", json.get("activity").getAsString());
        assertEquals("NONE", json.get("mealPhase").getAsString());
        assertTrue(json.get("mealActionDueAt").isJsonNull());
        var retained = json.getAsJsonObject("mealResourceObligation");
        assertEquals("ACTOR_PORTION", retained.get("custodyState").getAsString());
        assertEquals(meal.claimId().value(), retained.get("claim").getAsString());
        assertEquals(body.physicalEpoch(), retained.get("physicalEpoch").getAsLong());
        assertEquals(meal.executionId().generation(), retained.get("executionGeneration").getAsLong());
        assertEquals(meal.portion().quantity(), retained.get("quantity").getAsInt());
        assertEquals(tick, retained.get("retiredAtTick").getAsLong());
        assertFalse(retained.get("pendingPhysicalStep").getAsBoolean());
        assertEquals(before.inventory(), state.inventory());
    }
    @Test
    void residentLifeDiagnosticShowsEffectiveNeedAndFeasibleActivityWithoutMutation() {
        var configuration = FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:resident-life-diagnostic"), 47L);
        FrontierWorldState state = configuration.initialState();
        SubjectId resident = state.humanPopulation().residents().keySet().stream().sorted().findFirst().orElseThrow();
        CheckpointImage checkpoint = new CheckpointImage(configuration.worldId(),
                new io.farfrontier.palemirror.frontier.v3.api.Revision(3L),
                new SimInstant(24_000L), new byte[] {1}, List.of(), List.of());
        var json = JsonParser.parseString(FrontierV3DiagnosticJson.render("resident_life", resident.value(),
                checkpoint, state, Optional.empty()).substring(FrontierV3DiagnosticJson.PREFIX.length())).getAsJsonObject();
        assertEquals("ok", json.get("status").getAsString());
        var profile = state.humanPopulation().resident(resident);
        assertEquals(state.humanPopulation().nutrition(resident).accrueThrough(24_000L,
                state.bootstrap().ruleset().residentLife(), profile.characteristics().effectiveMetabolismPermille(24_000L))
                .satietyUnits(), json.get("satietyUnits").getAsInt());
        assertEquals(state.bootstrap().ruleset().residentLife().satietyCapacityUnits(), json.get("storedSatietyUnits").getAsInt());
        assertEquals("WORK", json.get("scheduleWindow").getAsString());
        assertEquals("IDLE", json.get("activity").getAsString(),
                "hunger remains visible, but an unavailable meal is not an executable activity");
        assertEquals("NONE", json.get("mealPhase").getAsString());
        assertEquals("WAIT", json.get("activityAdmission").getAsString());
        assertEquals("FOOD_STOCK", json.get("activityWait").getAsString());
        assertTrue(json.getAsJsonArray("activityWakeKeys").asList().stream()
                .anyMatch(key -> key.getAsString().equals(profile.settlementId().value())));
        assertTrue(json.get("mealActionDueAt").isJsonNull());
        assertTrue(json.get("mealTravelArrivalAt").isJsonNull());
        assertFalse(json.get("mealActionHeld").getAsBoolean());
        assertFalse(json.get("physicalBodyCustody").getAsBoolean());
        assertEquals("NONE", json.get("ambientStatus").getAsString());
        assertEquals(0, json.get("mealOverdueTicks").getAsLong());
        assertEquals(configuration.initialState(), state);
    }

}
