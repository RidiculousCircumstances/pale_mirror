package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.CheckpointImage;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.FrontierV3FixtureCatalog;
import io.farfrontier.palemirror.frontier.v3.model.GrayboxSemanticPart;
import io.farfrontier.palemirror.frontier.v3.model.ExactInventory;
import io.farfrontier.palemirror.frontier.v3.model.ExactItemStack;
import io.farfrontier.palemirror.frontier.v3.model.InventoryCustody;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalDelta;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalDeltaKind;
import io.farfrontier.palemirror.frontier.v3.model.RouteConstruction;
import io.farfrontier.palemirror.frontier.v3.model.RouteConstructionStarted;
import io.farfrontier.palemirror.frontier.v3.model.RouteConstructionStateSupport;
import io.farfrontier.palemirror.frontier.v3.model.RouteConstructionStatus;
import io.farfrontier.palemirror.frontier.v3.model.FrontierRouteNetwork;
import io.farfrontier.palemirror.frontier.v3.model.ResidentMigrationJourney;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierExecutionMetrics;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class FrontierV3DiagnosticJsonTest {
    @Test
    void rendersBoundedReadOnlyPerformanceAttribution() {
        FrontierV3PerformanceMetrics metrics = new FrontierV3PerformanceMetrics();
        metrics.begin(FrontierExecutionMetrics.Stage.PHYSICAL, "scenes", "scene").close();
        metrics.observeQueue(new SimInstant(12L), 7, Optional.of(new ScheduledAction(
                new io.farfrontier.palemirror.frontier.v3.api.ScheduleId("schedule:performance"), new SimInstant(4L), 0,
                new SubjectId("settlement:1"), "process.performance", 1)));
        CheckpointImage checkpoint = new CheckpointImage(new WorldId("frontier:performance-diagnostic"),
                new io.farfrontier.palemirror.frontier.v3.api.Revision(3L), new SimInstant(12L), new byte[]{1}, List.of(), List.of());

        String value = FrontierV3PerformanceDiagnostic.render(checkpoint, metrics.snapshot());

        assertTrue(value.startsWith(FrontierV3DiagnosticJson.PREFIX + "{\"schema\":1,\"kind\":\"performance\""));
        assertTrue(value.contains("\"stage\":\"PHYSICAL\"") && value.contains("\"maxLagTicks\":8"));
        assertTrue(value.length() < 8_192, "performance diagnostics retain the ordinary bounded operator response limit");
    }

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
    void rendersExactActorCustodyWithoutInventingASecondEquipmentLedger(@TempDir Path directory) {
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = FrontierV3ServerRuntime.start(
                FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:diagnostic-actor-custody"), 91L),
                new FrontierFileStore(directory, FrontierWorldRuntimeDefinition.payloadCodecs()), 10_000);
        CheckpointImage checkpoint = runtime.checkpointImage().orElseThrow(); FrontierWorldState state = runtime.decodedState().orElseThrow();
        SubjectId itemId = state.inventory().items().keySet().stream().sorted().findFirst().orElseThrow();
        SubjectId actorId = state.humanPopulation().residentIds().stream().sorted().findFirst().orElseThrow();
        ExactItemStack item = state.inventory().items().get(itemId); var items = new LinkedHashMap<>(state.inventory().items());
        items.put(itemId, new ExactItemStack(item.id(), item.economicOwnerId(), item.itemKind(), item.count(), new InventoryCustody.Actor(actorId)));
        ExactInventory inventory = new ExactInventory(state.inventory().containers(), items, state.inventory().cargo(), state.inventory().playerItems(),
                state.inventory().worldCarrierItems(), state.inventory().conflicts(), state.inventory().surfaces(), state.inventory().economics());

        String json = FrontierV3DiagnosticJson.render("item", itemId.value(), checkpoint, state.withInventory(inventory), Optional.empty());

        assertTrue(json.contains("\"custody\":{\"kind\":\"ACTOR\",\"actor\":\"" + actorId.value() + "\"}"));
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
                new io.farfrontier.palemirror.frontier.v3.model.BlockPosition(4, 65, 8), null, null);

        String actorJson = FrontierV3DiagnosticJson.render("actor", actor.value(), checkpoint, state, Optional.empty(), Optional.of(evidence));

        assertTrue(actorJson.contains("\"physicalAdmission\":{\"status\":\"BLOCKED\""));
        assertTrue(actorJson.contains("\"placement\":{\"x\":4,\"y\":65,\"z\":8}"));
        assertTrue(actorJson.contains("\"observedExact\":null"));
    }

    @Test
    void exposesOneBoundedPhysicalAssemblyProbeWithoutChangingItsCursor(@TempDir Path directory) {
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = FrontierV3ServerRuntime.start(
                FrontierV3FixtureCatalog.operationAssemblyConfiguration(new WorldId("frontier:diagnostic-assembly-test"), 41L),
                new FrontierFileStore(directory, FrontierWorldRuntimeDefinition.payloadCodecs()), 10_000);
        CheckpointImage checkpoint = runtime.checkpointImage().orElseThrow();
        FrontierWorldState state = runtime.decodedState().orElseThrow();
        var operation = state.operations().values().iterator().next();
        var entry = operation.activeAssembly().orElseThrow().members().entrySet().iterator().next();
        var member = entry.getValue();
        var readiness = new FrontierV3AmbientActorExecutor.AssemblyReadiness(java.util.List.of(
                new FrontierV3AmbientActorExecutor.AssemblyMemberReadiness(entry.getKey(), member.currentSurface().support(),
                        member.nextSurface().support(), member.currentSurface().support(), new FrontierV3AmbientActorExecutor.ObservedPosition(4.5D, 64.0D, 8.5D), "OCCUPIED",
                        "minecraft:gray_carpet", "minecraft:stone", "minecraft:air", "minecraft:air", java.util.List.of("minecraft:villager"))));

        String operationJson = FrontierV3DiagnosticJson.render("operation", operation.id().value(), checkpoint, state, Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(readiness));

        assertTrue(operationJson.contains("\"physicalAssembly\":[{\"actor\":\"" + entry.getKey().value() + "\""));
        assertTrue(operationJson.contains("\"targetStatus\":\"OCCUPIED\"") && operationJson.contains("\"occupants\":[\"minecraft:villager\"]"));
        assertTrue(operationJson.contains("\"observedExact\":{\"x\":4.5,\"y\":64.0,\"z\":8.5}") && operationJson.contains("\"supportBlock\":\"minecraft:stone\""));
        assertTrue(runtime.decodedState().orElseThrow().equals(state), "a physical assembly probe may not advance or defer the operation");
    }

    @Test
    void exposesTheRetainedTraversalEdgeWithoutChangingTheRoute(@TempDir Path directory) {
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = FrontierV3ServerRuntime.start(
                FrontierV3FixtureCatalog.routeSceneReturnConfiguration(new WorldId("frontier:diagnostic-traversal-test"), 41L),
                new FrontierFileStore(directory, FrontierWorldRuntimeDefinition.payloadCodecs()), 10_000);
        CheckpointImage checkpoint = runtime.checkpointImage().orElseThrow();
        FrontierWorldState state = runtime.decodedState().orElseThrow();
        var operation = state.operations().get(new SubjectId("operation:supply-1-2"));
        var travel = operation.activeTravel().orElseThrow();

        String operationJson = FrontierV3DiagnosticJson.render("operation", operation.id().value(), checkpoint, state, Optional.empty());

        assertTrue(operationJson.contains("\"travelTopology\":\"" + travel.topology().id().value() + "\""));
        assertTrue(operationJson.contains("\"travelNextEdge\":\"" + travel.nextEdge().id().value() + "\""));
        assertTrue(operationJson.contains("\"travelNextEdgeAvailability\":\"OPEN\"")
                        && operationJson.contains("\"settlementTraversalAvailable\":true")
                        && operationJson.contains("\"settlementUnavailableEdges\":0"),
                "the read-only operation view must distinguish an intact retained edge from a globally blocked route");
        assertTrue(runtime.decodedState().orElseThrow().equals(state), "a traversal diagnostic may not replan or advance the operation");
        runtime.shutdown();
    }

    @Test
    void exposesDeclaredGradeFactsWithoutSurveyingOrChangingTheRoute(@TempDir Path directory) {
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = FrontierV3ServerRuntime.start(
                FrontierV3FixtureCatalog.steppedRouteConfiguration(new WorldId("frontier:diagnostic-stepped-route-test"), 41L),
                new FrontierFileStore(directory, FrontierWorldRuntimeDefinition.payloadCodecs()), 10_000);
        CheckpointImage checkpoint = runtime.checkpointImage().orElseThrow();
        FrontierWorldState state = runtime.decodedState().orElseThrow();

        String topology = FrontierV3DiagnosticJson.render("route_topology", "settlement:1", checkpoint, state, Optional.empty());

        assertTrue(topology.contains("\"status\":\"ok\"") && topology.contains("\"gradedEdges\":4")
                        && topology.contains("\"maximumGrade\":1"),
                "one bounded read-only diagnostic exposes the declared topology grade rather than a Minecraft height-map guess");
        assertTrue(topology.contains("\"from\":{\"x\":-370,\"y\":64,\"z\":-343}")
                        && topology.contains("\"to\":{\"x\":-371,\"y\":65,\"z\":-343}"));
        assertTrue(runtime.decodedState().orElseThrow().equals(state), "a route-topology diagnostic may not mutate canonical state");
        runtime.shutdown();
    }

    @Test
    void exposesOneExactTransitCursorWithoutAdvancingTheJourney(@TempDir Path directory) {
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = FrontierV3ServerRuntime.start(
                FrontierV3FixtureCatalog.residentTransitConfiguration(new WorldId("frontier:diagnostic-transit-test"), 91L),
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
                FrontierV3FixtureCatalog.hotSceneStrikeConfiguration(new WorldId("frontier:diagnostic-scene-test"), 41L),
                new FrontierFileStore(directory, FrontierWorldRuntimeDefinition.payloadCodecs()), 10_000);
        CheckpointImage checkpoint = runtime.checkpointImage().orElseThrow();
        FrontierWorldState state = runtime.decodedState().orElseThrow();
        String id = state.coldEngagementSceneCandidates().getFirst().engagementId().value();

        String scene = FrontierV3DiagnosticJson.render("scene", id, checkpoint, state, Optional.empty());

        assertTrue(scene.contains("\"status\":\"not_found\""), "without a materialized scene lease the read-only view must not create one");
        assertTrue(runtime.decodedState().orElseThrow().sceneLeases().isEmpty(), "diagnostics never mutate the canonical scene state");
    }

    @Test
    void exposesOneTypedCargoFreeAssaultSceneWithoutCallingItsLegacyLogisticsView(@TempDir Path directory) {
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = FrontierV3ServerRuntime.start(
                FrontierV3FixtureCatalog.settlementAssaultConfiguration(new WorldId("frontier:diagnostic-assault-scene-test"), 41L),
                new FrontierFileStore(directory, FrontierWorldRuntimeDefinition.payloadCodecs()), 10_000);
        CheckpointImage checkpoint = runtime.checkpointImage().orElseThrow();
        FrontierWorldState before = runtime.decodedState().orElseThrow();
        var candidate = before.coldSettlementAssaultSceneCandidates().getFirst();
        var members = candidate.memberPositions().keySet().stream().sorted().map(actor -> new io.farfrontier.palemirror.frontier.v3.model.SceneMember(actor,
                io.farfrontier.palemirror.frontier.v3.model.SceneLease.deterministicEntityId(checkpoint.worldId(), actor))).toList();
        var lease = io.farfrontier.palemirror.frontier.v3.model.SceneLease.forCause(
                new io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId("lease:diagnostic-assault-r0"), checkpoint.worldId(),
                new io.farfrontier.palemirror.frontier.v3.model.SettlementAssaultSceneCause(candidate.assaultId(), candidate.settlementId()),
                candidate.handoffPosition(), checkpoint.instant(), checkpoint.revision().value(), io.farfrontier.palemirror.frontier.v3.model.SceneLeaseStatus.PREPARED,
                members, io.farfrontier.palemirror.frontier.v3.model.SceneLease.bodiesAboveSupportCells(candidate.memberPositions()), java.util.Set.of(), Optional.empty());
        FrontierWorldState hot = before.prepareSceneLease(lease).transitionSceneLease(lease.id(), io.farfrontier.palemirror.frontier.v3.model.SceneLeaseStatus.HOT);

        String scene = FrontierV3DiagnosticJson.render("scene", candidate.assaultId().value(), checkpoint, hot, Optional.empty());

        assertTrue(scene.contains("\"status\":\"ok\"") && scene.contains("\"sceneKind\":\"SETTLEMENT_ASSAULT\""));
        assertTrue(scene.contains("\"operation\":\"\"") && scene.contains("\"assault\":\"" + candidate.assaultId().value() + "\""));
        assertTrue(scene.contains("\"strikeStatus\":\"NONE\"") && scene.contains("\"carrier\":\"NOT_APPLICABLE\"") == false,
                "the pure formatter preserves typed scene facts without querying a cargo carrier");
        runtime.shutdown();
    }

    @Test
    void exposesOneTypedEngineeringWorksiteWithoutTreatingItAsLogistics(@TempDir Path directory) {
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = FrontierV3ServerRuntime.start(
                FrontierV3FixtureCatalog.engineeringEquipmentConfiguration(new WorldId("frontier:diagnostic-engineering-scene-test"), 41L),
                new FrontierFileStore(directory, FrontierWorldRuntimeDefinition.payloadCodecs()), 10_000);
        CheckpointImage checkpoint = runtime.checkpointImage().orElseThrow();
        FrontierWorldState state = runtime.decodedState().orElseThrow();
        RouteConstruction project = state.routeConstructions().values().iterator().next();
        var assembly = io.farfrontier.palemirror.frontier.v3.model.EngineeringWorksite.compile(state, project);
        FrontierWorldState sourceState = state;
        var completedMembers = new java.util.LinkedHashMap<SubjectId, io.farfrontier.palemirror.frontier.v3.model.EngineeringWorkAssembly.Member>();
        var completedLocations = new java.util.LinkedHashMap<>(state.actorLocations());
        assembly.members().forEach((member, approach) -> {
            var completed = new io.farfrontier.palemirror.frontier.v3.model.EngineeringWorkAssembly.Member(approach.corridor(), approach.corridor().size() - 1);
            completedMembers.put(member, completed);
            completedLocations.put(member, new io.farfrontier.palemirror.frontier.v3.model.ActorLocation(io.farfrontier.palemirror.frontier.v3.model.BodyPosition.above(
                    new io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor(completed.currentPosition())),
                    sourceState.actorLocations().get(member).condition()));
        });
        assembly = new io.farfrontier.palemirror.frontier.v3.model.EngineeringWorkAssembly(completedMembers);
        var projects = new java.util.LinkedHashMap<>(state.routeConstructions());
        projects.put(project.id(), new RouteConstruction(project.id(), project.settlementId(), project.waypoints(), project.workCells(), project.confirmedCells(),
                project.status(), project.cargoId(), project.team(), Optional.of(assembly)));
        state = state.withChanges(io.farfrontier.palemirror.frontier.v3.model.FrontierWorldStateUpdate.begin()
                .actorLocations(completedLocations).routeConstructions(projects));
        project = projects.get(project.id());
        var members = project.team().orElseThrow().memberIds().stream().map(member -> new io.farfrontier.palemirror.frontier.v3.model.SceneMember(member,
                io.farfrontier.palemirror.frontier.v3.model.SceneLease.deterministicEntityId(checkpoint.worldId(), member))).toList();
        var positions = project.assembly().orElseThrow().members().entrySet().stream().collect(java.util.stream.Collectors.toMap(
                java.util.Map.Entry::getKey, entry -> entry.getValue().currentPosition()));
        var lease = io.farfrontier.palemirror.frontier.v3.model.SceneLease.forCause(
                new io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId("lease:diagnostic-engineering-r0"), checkpoint.worldId(),
                new io.farfrontier.palemirror.frontier.v3.model.EngineeringWorkSceneCause(project.id(), 0), project.workCells().getFirst(),
                checkpoint.instant(), checkpoint.revision().value(), io.farfrontier.palemirror.frontier.v3.model.SceneLeaseStatus.PREPARED,
                members, io.farfrontier.palemirror.frontier.v3.model.SceneLease.bodiesAboveSupportCells(positions), java.util.Set.of(), Optional.empty());
        FrontierWorldState diagnosticState = state.withChanges(io.farfrontier.palemirror.frontier.v3.model.FrontierWorldStateUpdate.begin()
                .sceneLeases(java.util.Map.of(lease.id(), lease)));

        String scene = FrontierV3DiagnosticJson.render("scene", project.id().value(), checkpoint, diagnosticState, Optional.empty());

        assertTrue(scene.contains("\"status\":\"ok\"") && scene.contains("\"sceneKind\":\"ENGINEERING_WORKSITE\""));
        assertTrue(scene.contains("\"project\":\"" + project.id().value() + "\"")
                && scene.contains("\"operation\":\"\"") && scene.contains("\"assault\":\"\""));
        runtime.shutdown();
    }

    @Test
    void exposesOneExactMedicalOwnerAndItsTypedSceneWithoutTreatingItAsLogistics(@TempDir Path directory) {
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = FrontierV3ServerRuntime.start(
                FrontierV3FixtureCatalog.medicalTreatmentConfiguration(new WorldId("frontier:diagnostic-medical-scene-test"), 41L),
                new FrontierFileStore(directory, FrontierWorldRuntimeDefinition.payloadCodecs()), 10_000);
        CheckpointImage checkpoint = runtime.checkpointImage().orElseThrow();
        FrontierWorldState initial = runtime.decodedState().orElseThrow();
        SubjectId settlement = initial.bootstrap().settlements().getFirst().id();
        SubjectId depot = FrontierWorldState.depotId(settlement);
        FrontierWorldState state = initial.withInventory(initial.inventory()
                .withSurfaceStatus(depot, io.farfrontier.palemirror.frontier.v3.model.ContainerSurfaceStatus.PREPARED)
                .withSurfaceStatus(depot, io.farfrontier.palemirror.frontier.v3.model.ContainerSurfaceStatus.ACTIVE));
        java.util.List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> planned =
                io.farfrontier.palemirror.frontier.v3.process.MedicalTreatmentProcess.planStart(state, settlement, 1);
        var started = planned.stream().map(io.farfrontier.palemirror.frontier.v3.api.ProposedEvent::payload)
                .filter(io.farfrontier.palemirror.frontier.v3.model.MedicalTreatmentStarted.class::isInstance)
                .map(io.farfrontier.palemirror.frontier.v3.model.MedicalTreatmentStarted.class::cast).findFirst().orElseThrow();
        var prepared = planned.stream().map(io.farfrontier.palemirror.frontier.v3.api.ProposedEvent::payload)
                .filter(io.farfrontier.palemirror.frontier.v3.model.PhysicalIntentPrepared.class::isInstance)
                .map(io.farfrontier.palemirror.frontier.v3.model.PhysicalIntentPrepared.class::cast).findFirst().orElseThrow();
        state = io.farfrontier.palemirror.frontier.v3.process.MedicalTreatmentProcess.reduceStarted(state, settlement, started)
                .preparePhysicalIntent(prepared.intent());
        var operation = state.humanPopulation().medicalOperations().values().iterator().next();
        var candidate = io.farfrontier.palemirror.frontier.v3.model.FrontierMedicalTreatmentSceneSupport.nextCandidate(state).orElseThrow();
        var leaseId = new io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId("lease:diagnostic-medical-r0");
        var members = candidate.memberPositions().keySet().stream().sorted().map(actor -> new io.farfrontier.palemirror.frontier.v3.model.SceneMember(actor,
                io.farfrontier.palemirror.frontier.v3.model.SceneLease.deterministicEntityId(checkpoint.worldId(), leaseId, actor))).toList();
        var lease = io.farfrontier.palemirror.frontier.v3.model.SceneLease.forCause(leaseId, checkpoint.worldId(),
                new io.farfrontier.palemirror.frontier.v3.model.MedicalTreatmentSceneCause(operation.id()), candidate.infirmaryAnchor(),
                checkpoint.instant(), checkpoint.revision().value(), io.farfrontier.palemirror.frontier.v3.model.SceneLeaseStatus.PREPARED,
                members, io.farfrontier.palemirror.frontier.v3.model.SceneLease.bodiesAboveSupportCells(candidate.memberPositions()), java.util.Set.of(), Optional.empty());
        FrontierWorldState diagnosticState = state.prepareSceneLease(lease);

        String medical = FrontierV3DiagnosticJson.render("medical", operation.id().value(), checkpoint, diagnosticState, Optional.empty());
        String scene = FrontierV3DiagnosticJson.render("scene", operation.id().value(), checkpoint, diagnosticState, Optional.empty());

        assertTrue(medical.contains("\"status\":\"ok\"") && medical.contains("\"patientHealth\":\"INFECTED\""));
        assertTrue(medical.contains("\"medicalStatus\":\"PREPARED\"") && medical.contains("\"intentStatus\":\"PREPARED\""));
        assertTrue(scene.contains("\"sceneKind\":\"MEDICAL_TREATMENT\"") && scene.contains("\"medical\":\"" + operation.id().value() + "\""));
        assertTrue(runtime.decodedState().orElseThrow().equals(initial), "read-only medical diagnostics must not create a lease or mutate treatment state");
        runtime.shutdown();
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
        java.util.List<BlockPosition> bypass = java.util.List.of(waypoints.get(0), waypoints.get(1),
                waypoints.get(1).offset(-10, 0, 0), waypoints.get(2).offset(-10, 0, 0),
                waypoints.get(2), waypoints.get(3), waypoints.get(4));
        java.util.List<BlockPosition> workCells = FrontierRouteNetwork.constructionCells(
                baseline.bootstrap(), baseline.routeTopology(), settlement, bypass);
        RouteConstruction project = new RouteConstruction(new SubjectId("construction:diagnostic-route"), settlement,
                bypass, workCells, 0, RouteConstructionStatus.BUILDING, Optional.empty(), Optional.empty(), Optional.empty());
        FrontierWorldState changed = RouteConstructionStateSupport.reduceStarted(baseline, FrontierRouteNetwork.OWNER,
                new RouteConstructionStarted(project));

        String route = FrontierV3DiagnosticJson.render("route_construction", settlement.value(), checkpoint, changed, Optional.empty());
        String missing = FrontierV3DiagnosticJson.render("route_construction", "settlement:missing", checkpoint, changed, Optional.empty());

        assertTrue(route.contains("\"status\":\"ok\"") && route.contains("\"project\":\"construction:diagnostic-route\""));
        assertTrue(route.contains("\"phase\":\"BUILDING\"") && route.contains("\"cargoPresent\":false") && route.contains("\"nextCell\":{"));
        assertTrue(route.contains("\"assemblyPresent\":false"),
                "the route diagnostic must make an absent COLD crew assembly distinguishable from a stalled one");
        assertTrue(route.contains("\"teamPresent\":false") && route.contains("\"teamFullyEquipped\":false"),
                "the route diagnostic must distinguish an autonomous historical fixture from a real unready crew");
        assertTrue(missing.contains("\"status\":\"not_found\""));
        assertTrue(changed.routeConstructions().get(project.id()).confirmedCells() == 0,
                "read-only route diagnostics never advance a project");
    }
}
