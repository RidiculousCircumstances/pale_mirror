package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.CheckpointImage;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldRuntimeDefinition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.GrayboxSemanticPart;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalDelta;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalDeltaKind;
import io.farfrontier.palemirror.frontier.v3.model.RouteConstruction;
import io.farfrontier.palemirror.frontier.v3.model.RouteConstructionStatus;
import io.farfrontier.palemirror.frontier.v3.model.ResidentMigrationJourney;
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
        SubjectId actor = state.humanPopulation().residents().keySet().stream().sorted().findFirst().orElseThrow();
        SubjectId item = state.inventory().items().keySet().stream().sorted().findFirst().orElseThrow();
        SubjectId settlement = state.bootstrap().settlements().getFirst().id();
        SubjectId hive = state.bootstrap().hive().id();
        SubjectId container = state.inventory().containers().keySet().stream().sorted().findFirst().orElseThrow();

        String summary = FrontierV3DiagnosticJson.render("summary", "", checkpoint, state, Optional.empty());
        String siteJson = FrontierV3DiagnosticJson.render("site", site.value(), checkpoint, state, Optional.empty());
        String actorJson = FrontierV3DiagnosticJson.render("actor", actor.value(), checkpoint, state, Optional.empty());
        String itemJson = FrontierV3DiagnosticJson.render("item", item.value(), checkpoint, state, Optional.empty());
        String settlementJson = FrontierV3DiagnosticJson.render("settlement", settlement.value(), checkpoint, state, Optional.empty());
        String hiveJson = FrontierV3DiagnosticJson.render("hive", hive.value(), checkpoint, state, Optional.empty());
        String containerJson = FrontierV3DiagnosticJson.render("container", container.value(), checkpoint, state, Optional.empty());
        String missing = FrontierV3DiagnosticJson.render("site", "site:missing", checkpoint, state, Optional.empty());

        assertTrue(summary.startsWith(FrontierV3DiagnosticJson.PREFIX + "{\"schema\":1,\"kind\":\"summary\""));
        assertTrue(summary.contains("\"settlements\":12"));
        assertTrue(siteJson.contains("\"id\":\"" + site.value() + "\""));
        assertTrue(siteJson.contains("\"firstCrop\":{"));
        assertTrue(actorJson.contains("\"position\":{"));
        assertTrue(actorJson.contains("\"nutrition\":\"NOURISHED\""));
        assertTrue(itemJson.contains("\"custody\":{"));
        assertTrue(settlementJson.contains("\"harvestAdmission\":\"NO_READY_SITE\""));
        assertTrue(settlementJson.contains("\"strategic\":{"));
        assertTrue(settlementJson.contains("\"quarantine\":\"NORMAL\"") && settlementJson.contains("\"activeCases\":0"),
                "one named settlement view exposes bounded health policy facts without resident histories");
        assertTrue(settlementJson.contains("\"food\":{\"status\":\"IDLE\",\"available\":0,\"reserve\":"),
                "one named settlement view exposes exact available food and its derived reserve without creating a second ledger");
        assertTrue(settlementJson.contains("\"nourished\":" + state.bootstrap().settlements().getFirst().residents().size()
                        + ",\"hungry\":0,\"starving\":0"),
                "one named settlement view exposes bounded individual nutrition totals without a separate aggregate owner");
        assertTrue(hiveJson.contains("\"infectionCells\":18") && hiveJson.contains("\"addedOrgans\":0"),
                "one named hive diagnostic exposes bounded canonical expansion state without materializing it");
        assertTrue(containerJson.contains("\"surface\":") && containerJson.contains("\"occupiedCount\":") && containerJson.contains("\"occupied\":["),
                "one diagnostic must expose only the exact occupied slot projection of one named container");
        assertTrue(missing.contains("\"status\":\"not_found\""));
        assertTrue(summary.length() < 8_192 && siteJson.length() < 8_192 && actorJson.length() < 8_192 && itemJson.length() < 8_192 && settlementJson.length() < 8_192 && hiveJson.length() < 8_192 && containerJson.length() < 8_192);
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
                new io.farfrontier.palemirror.frontier.v3.model.BlockPosition(4, 65, 8), null);

        String actorJson = FrontierV3DiagnosticJson.render("actor", actor.value(), checkpoint, state, Optional.empty(), Optional.of(evidence));

        assertTrue(actorJson.contains("\"physicalAdmission\":{\"status\":\"BLOCKED\""));
        assertTrue(actorJson.contains("\"placement\":{\"x\":4,\"y\":65,\"z\":8}"));
    }

    @Test
    void exposesOneBoundedPhysicalAssemblyProbeWithoutChangingItsCursor(@TempDir Path directory) {
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = FrontierV3ServerRuntime.start(
                FrontierWorldRuntimeDefinition.developmentOperationAssemblyConfiguration(new WorldId("frontier:diagnostic-assembly-test"), 41L),
                new FrontierFileStore(directory, FrontierWorldRuntimeDefinition.payloadCodecs()), 10_000);
        CheckpointImage checkpoint = runtime.checkpointImage().orElseThrow();
        FrontierWorldState state = runtime.decodedState().orElseThrow();
        var operation = state.operations().values().iterator().next();
        var entry = operation.activeAssembly().orElseThrow().members().entrySet().iterator().next();
        var member = entry.getValue();
        var readiness = new FrontierV3AmbientActorExecutor.AssemblyReadiness(java.util.List.of(
                new FrontierV3AmbientActorExecutor.AssemblyMemberReadiness(entry.getKey(), member.currentPosition(),
                        member.corridor().get(member.cursor() + 1), member.currentPosition(), new FrontierV3AmbientActorExecutor.ObservedPosition(4.5D, 64.0D, 8.5D), "OCCUPIED",
                        "minecraft:gray_carpet", "minecraft:stone", "minecraft:air", "minecraft:air", java.util.List.of("minecraft:villager"))));

        String operationJson = FrontierV3DiagnosticJson.render("operation", operation.id().value(), checkpoint, state, Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(readiness));

        assertTrue(operationJson.contains("\"physicalAssembly\":[{\"actor\":\"" + entry.getKey().value() + "\""));
        assertTrue(operationJson.contains("\"targetStatus\":\"OCCUPIED\"") && operationJson.contains("\"occupants\":[\"minecraft:villager\"]"));
        assertTrue(operationJson.contains("\"observedExact\":{\"x\":4.5,\"y\":64.0,\"z\":8.5}") && operationJson.contains("\"supportBlock\":\"minecraft:stone\""));
        assertTrue(runtime.decodedState().orElseThrow().equals(state), "a physical assembly probe may not advance or defer the operation");
    }

    @Test
    void exposesOneExactTransitCursorWithoutAdvancingTheJourney(@TempDir Path directory) {
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = FrontierV3ServerRuntime.start(
                FrontierWorldRuntimeDefinition.developmentResidentTransitConfiguration(new WorldId("frontier:diagnostic-transit-test"), 91L),
                new FrontierFileStore(directory, FrontierWorldRuntimeDefinition.payloadCodecs()), 10_000);
        CheckpointImage checkpoint = runtime.checkpointImage().orElseThrow();
        FrontierWorldState state = runtime.decodedState().orElseThrow();
        ResidentMigrationJourney journey = state.humanPopulation().migrations().values().iterator().next();

        String transit = FrontierV3DiagnosticJson.render("transit", journey.residentId().value(), checkpoint, state, Optional.empty());

        assertTrue(transit.contains("\"status\":\"ok\"") && transit.contains("\"journeyStatus\":\"EN_ROUTE\""));
        assertTrue(transit.contains("\"routeIndex\":0") && transit.contains("\"next\":{"));
        assertTrue(runtime.decodedState().orElseThrow().equals(state), "read-only Transit diagnostics may not advance or materialize a resident");
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
    void exposesBoundedSceneCausalityWithoutLeakingPhysicalOrPlayerState(@TempDir Path directory) {
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = FrontierV3ServerRuntime.start(
                FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:diagnostic-scene-trace-test"), 92L),
                new FrontierFileStore(directory, FrontierWorldRuntimeDefinition.payloadCodecs()), 10_000);
        CheckpointImage checkpoint = runtime.checkpointImage().orElseThrow();
        FrontierWorldState state = runtime.decodedState().orElseThrow();
        FrontierV3DiagnosticTrace.Entry trace = new FrontierV3DiagnosticTrace.Entry("operation:operation:supply-1-2", "scene_hot", "operation:supply-1-2",
                "executor:scene-hot", "transaction:scene-hot", 8L, new FrontierV3DiagnosticTrace.Context("operation:supply-1-2", "lease:supply-1-2-r7",
                "cargo:supply-1-2", java.util.List.of("resident:1-16", "resident:1-30")));

        String rendered = FrontierV3DiagnosticJson.render("trace", trace.correlation(), checkpoint, state, Optional.of(trace));

        assertTrue(rendered.contains("\"causal\":{\"operation\":\"operation:supply-1-2\""));
        assertTrue(rendered.contains("\"lease\":\"lease:supply-1-2-r7\"") && rendered.contains("\"cargo\":\"cargo:supply-1-2\""));
        assertTrue(rendered.contains("\"actors\":[\"resident:1-16\",\"resident:1-30\"]"));
        assertTrue(!rendered.contains("position") && !rendered.contains("uuid"), "trace context must not become a player/physical-state dump");
    }

    @Test
    void derivesOneStableTraceCorrelationForTheCompleteRouteRepairWorkOrder() {
        assertTrue(FrontierV3DiagnosticTrace.routeConstructionCorrelation(new SubjectId("construction:route-reroute-settlement-1--380-64--304"))
                .equals("route-construction:construction:route-reroute-settlement-1--380-64--304"));
    }

    @Test
    void exposesOneExactPhysicalDeltaWithoutLeakingThePlayerIdentity(@TempDir Path directory) {
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = FrontierV3ServerRuntime.start(
                FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:diagnostic-delta-test"), 95L),
                new FrontierFileStore(directory, FrontierWorldRuntimeDefinition.payloadCodecs()), 10_000);
        CheckpointImage checkpoint = runtime.checkpointImage().orElseThrow();
        BlockPosition position = new BlockPosition(-380, 64, -304);
        FrontierWorldState changed = runtime.decodedState().orElseThrow().recordPhysicalDelta(new PhysicalDelta(position,
                PhysicalDeltaKind.KNOWN_SEMANTIC_LOSS, Optional.of(new SubjectId("route:frontier-network")),
                Optional.of(GrayboxSemanticPart.ROUTE_SURFACE), "player:123e4567-e89b-12d3-a456-426614174000"));

        String delta = FrontierV3DiagnosticJson.render("physical_delta", "-380,64,-304", checkpoint, changed, Optional.empty());
        String malformed = FrontierV3DiagnosticJson.render("physical_delta", "route:frontier-network", checkpoint, changed, Optional.empty());

        assertTrue(delta.contains("\"status\":\"ok\"") && delta.contains("\"deltaKind\":\"KNOWN_SEMANTIC_LOSS\""));
        assertTrue(delta.contains("\"owner\":\"route:frontier-network\"") && delta.contains("\"semanticPart\":\"ROUTE_SURFACE\""));
        assertTrue(delta.contains("\"causeKind\":\"PLAYER\"") && delta.contains("\"trace\":\"physical-delta:-380,64,-304\""));
        assertFalse(delta.contains("123e4567-e89b-12d3-a456-426614174000"));
        assertTrue(malformed.contains("\"status\":\"not_found\""));
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

    @Test
    void exposesOneSettlementRouteRepairWithoutCreatingOrChangingIt(@TempDir Path directory) {
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = FrontierV3ServerRuntime.start(
                FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:diagnostic-route-construction-test"), 96L),
                new FrontierFileStore(directory, FrontierWorldRuntimeDefinition.payloadCodecs()), 10_000);
        CheckpointImage checkpoint = runtime.checkpointImage().orElseThrow();
        FrontierWorldState baseline = runtime.decodedState().orElseThrow();
        SubjectId settlement = baseline.bootstrap().settlements().getFirst().id();
        java.util.List<BlockPosition> waypoints = baseline.routeTopology().supplyWaypoints(baseline.bootstrap(), settlement);
        RouteConstruction project = new RouteConstruction(new SubjectId("construction:diagnostic-route"), settlement,
                java.util.List.of(waypoints.get(0), waypoints.get(1), waypoints.get(1).offset(-10, 0, 0), waypoints.get(2).offset(-10, 0, 0),
                        waypoints.get(2), waypoints.get(3), waypoints.get(4), waypoints.get(5)), 0, RouteConstructionStatus.BUILDING);
        FrontierWorldState changed = withRouteConstruction(baseline, project);

        String route = FrontierV3DiagnosticJson.render("route_construction", settlement.value(), checkpoint, changed, Optional.empty());
        String missing = FrontierV3DiagnosticJson.render("route_construction", "settlement:missing", checkpoint, changed, Optional.empty());

        assertTrue(route.contains("\"status\":\"ok\"") && route.contains("\"project\":\"construction:diagnostic-route\""));
        assertTrue(route.contains("\"phase\":\"BUILDING\"") && route.contains("\"cargoPresent\":false") && route.contains("\"nextCell\":{"));
        assertTrue(missing.contains("\"status\":\"not_found\""));
        assertTrue(changed.routeConstructions().get(project.id()).equals(project), "read-only route diagnostics never advance a project");
    }

    private static FrontierWorldState withRouteConstruction(FrontierWorldState state, RouteConstruction project) {
        return new FrontierWorldState(state.bootstrap(), state.actorLocations(), state.structureConditions(), state.infection(), state.inventory(),
                state.productionJobs(), state.contracts(), state.operations(), state.logisticsHistory(), state.physicalIntents(), state.physicalObservations(), state.sceneLeases(),
                state.hiveColony(), state.structureDamage(), state.physicalDeltas(), state.ambientLeases(), java.util.Map.of(project.id(), project),
                state.routeTopology(), state.strategicPlans(), state.humanPopulation(), state.resourceSites());
    }
}
