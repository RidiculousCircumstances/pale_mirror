package io.farfrontier.palemirror.internal.frontier.v3;

import com.google.gson.JsonParser;
import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class FrontierV3ResidentLifeDiagnosticTest {
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
        assertEquals(configuration.initialState(), state);
    }

}
