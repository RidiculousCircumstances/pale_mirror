package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.CheckpointImage;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldRuntimeDefinition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class FrontierV3DiagnosticJsonTest {
    @Test
    void rendersBoundedStableReadOnlyViewsForRealCanonicalSubjects(@TempDir Path directory) {
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = FrontierV3ServerRuntime.start(
                FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:diagnostic-json-test"), 91L),
                new FrontierFileStore(directory, FrontierWorldRuntimeDefinition.payloadCodecs()), 10_000);
        CheckpointImage checkpoint = runtime.checkpointImage().orElseThrow();
        FrontierWorldState state = runtime.decodedState().orElseThrow();
        SubjectId site = state.resourceSites().sites().keySet().stream().sorted().findFirst().orElseThrow();
        SubjectId actor = state.actorLocations().keySet().stream().sorted().findFirst().orElseThrow();
        SubjectId item = state.inventory().items().keySet().stream().sorted().findFirst().orElseThrow();
        SubjectId settlement = state.bootstrap().settlements().getFirst().id();
        SubjectId container = state.inventory().containers().keySet().stream().sorted().findFirst().orElseThrow();

        String summary = FrontierV3DiagnosticJson.render("summary", "", checkpoint, state, Optional.empty());
        String siteJson = FrontierV3DiagnosticJson.render("site", site.value(), checkpoint, state, Optional.empty());
        String actorJson = FrontierV3DiagnosticJson.render("actor", actor.value(), checkpoint, state, Optional.empty());
        String itemJson = FrontierV3DiagnosticJson.render("item", item.value(), checkpoint, state, Optional.empty());
        String settlementJson = FrontierV3DiagnosticJson.render("settlement", settlement.value(), checkpoint, state, Optional.empty());
        String containerJson = FrontierV3DiagnosticJson.render("container", container.value(), checkpoint, state, Optional.empty());
        String missing = FrontierV3DiagnosticJson.render("site", "site:missing", checkpoint, state, Optional.empty());

        assertTrue(summary.startsWith(FrontierV3DiagnosticJson.PREFIX + "{\"schema\":1,\"kind\":\"summary\""));
        assertTrue(summary.contains("\"settlements\":12"));
        assertTrue(siteJson.contains("\"id\":\"" + site.value() + "\""));
        assertTrue(siteJson.contains("\"firstCrop\":{"));
        assertTrue(actorJson.contains("\"position\":{"));
        assertTrue(itemJson.contains("\"custody\":{"));
        assertTrue(settlementJson.contains("\"harvestAdmission\":\"NO_READY_SITE\""));
        assertTrue(settlementJson.contains("\"strategic\":{"));
        assertTrue(containerJson.contains("\"surface\":") && containerJson.contains("\"occupiedCount\":") && containerJson.contains("\"occupied\":["),
                "one diagnostic must expose only the exact occupied slot projection of one named container");
        assertTrue(missing.contains("\"status\":\"not_found\""));
        assertTrue(summary.length() < 8_192 && siteJson.length() < 8_192 && actorJson.length() < 8_192 && itemJson.length() < 8_192 && settlementJson.length() < 8_192 && containerJson.length() < 8_192);
    }

    @Test
    void exposesBoundedReadOnlyAmbientAdmissionEvidence(@TempDir Path directory) {
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = FrontierV3ServerRuntime.start(
                FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:diagnostic-admission-test"), 93L),
                new FrontierFileStore(directory, FrontierWorldRuntimeDefinition.payloadCodecs()), 10_000);
        CheckpointImage checkpoint = runtime.checkpointImage().orElseThrow();
        FrontierWorldState state = runtime.decodedState().orElseThrow();
        SubjectId actor = state.actorLocations().keySet().stream().sorted().findFirst().orElseThrow();
        var evidence = new FrontierV3AmbientActorExecutor.AdmissionDiagnostic("BLOCKED", UUID.fromString("6e6a062d-a182-469c-9de8-2de3f3703ee1"), false,
                new io.farfrontier.palemirror.frontier.v3.model.BlockPosition(4, 65, 8));

        String actorJson = FrontierV3DiagnosticJson.render("actor", actor.value(), checkpoint, state, Optional.empty(), Optional.of(evidence));

        assertTrue(actorJson.contains("\"physicalAdmission\":{\"status\":\"BLOCKED\""));
        assertTrue(actorJson.contains("\"placement\":{\"x\":4,\"y\":65,\"z\":8}"));
    }

    @Test
    void exposesOnlyOneExplicitTraceAndNeverInventsAMissingCorrelation(@TempDir Path directory) {
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = FrontierV3ServerRuntime.start(
                FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:diagnostic-trace-test"), 92L),
                new FrontierFileStore(directory, FrontierWorldRuntimeDefinition.payloadCodecs()), 10_000);
        CheckpointImage checkpoint = runtime.checkpointImage().orElseThrow();
        FrontierWorldState state = runtime.decodedState().orElseThrow();
        FrontierV3DiagnosticTrace.Entry trace = new FrontierV3DiagnosticTrace.Entry("player:test", "resource_site_conflict",
                "site:1-wheat-field", "executor:command", "transaction:one", 7L);

        String known = FrontierV3DiagnosticJson.render("trace", "player:test", checkpoint, state, Optional.of(trace));
        String missing = FrontierV3DiagnosticJson.render("trace", "player:other", checkpoint, state, Optional.empty());

        assertTrue(known.contains("\"correlation\":\"player:test\""));
        assertTrue(known.contains("\"acceptedRevision\":7"));
        assertTrue(missing.contains("\"status\":\"not_found\""));
    }

    @Test
    void exposesOneReadOnlyEngagementSceneWithoutCreatingOrAdvancingIt(@TempDir Path directory) {
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = FrontierV3ServerRuntime.start(
                FrontierWorldRuntimeDefinition.developmentHotSceneStrikeConfiguration(new WorldId("frontier:diagnostic-scene-test"), 41L),
                new FrontierFileStore(directory, FrontierWorldRuntimeDefinition.payloadCodecs()), 10_000);
        CheckpointImage checkpoint = runtime.checkpointImage().orElseThrow();
        FrontierWorldState state = runtime.decodedState().orElseThrow();
        String id = state.coldEngagementSceneCandidates().getFirst().engagementId().value();

        String scene = FrontierV3DiagnosticJson.render("scene", id, checkpoint, state, Optional.empty());

        assertTrue(scene.contains("\"status\":\"not_found\""), "without a materialized scene lease the read-only view must not create one");
        assertTrue(runtime.decodedState().orElseThrow().sceneLeases().isEmpty(), "diagnostics never mutate the canonical scene state");
    }

    @Test
    void keepsPhysicalReadinessScopedToHarvestIntents(@TempDir Path directory) {
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = FrontierV3ServerRuntime.start(
                FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:diagnostic-readiness-test"), 94L),
                new FrontierFileStore(directory, FrontierWorldRuntimeDefinition.payloadCodecs()), 10_000);
        CheckpointImage checkpoint = runtime.checkpointImage().orElseThrow();
        FrontierWorldState state = runtime.decodedState().orElseThrow();
        var readiness = new FrontierV3ResourceSiteHarvestExecutor.Readiness(true, false, "ACTIVE", false, true, false,
                7, true, FrontierV3ResourceSiteHarvestExecutor.Precondition.READY);

        String nonHarvest = FrontierV3DiagnosticJson.render("intent", "intent:missing", checkpoint, state, Optional.empty(),
                Optional.empty(), Optional.of(readiness));

        assertTrue(nonHarvest.contains("\"status\":\"not_found\""));
        assertFalse(nonHarvest.contains("physicalReadiness"));
    }
}
