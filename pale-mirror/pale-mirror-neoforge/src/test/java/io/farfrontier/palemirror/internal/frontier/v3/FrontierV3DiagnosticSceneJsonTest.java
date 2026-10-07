package io.farfrontier.palemirror.internal.frontier.v3;

import com.google.gson.JsonParser;
import io.farfrontier.palemirror.frontier.v3.api.CheckpointImage;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.BodyPosition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection;
import io.farfrontier.palemirror.frontier.v3.model.FrontierBootstrapper;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.FrontierResourceSitePlan;
import io.farfrontier.palemirror.frontier.v3.model.FrontierV3FixtureCatalog;
import io.farfrontier.palemirror.frontier.v3.model.GrayboxSemanticPart;
import io.farfrontier.palemirror.frontier.v3.model.HiveMobilization;
import io.farfrontier.palemirror.frontier.v3.model.HiveReturnAssembly;
import io.farfrontier.palemirror.frontier.v3.model.HiveTaskAssembly;
import io.farfrontier.palemirror.frontier.v3.model.HiveMobilizationStatus;
import io.farfrontier.palemirror.frontier.v3.model.ExactInventory;
import io.farfrontier.palemirror.frontier.v3.model.ExactItemStack;
import io.farfrontier.palemirror.frontier.v3.model.FencedRecoveryAsset;
import io.farfrontier.palemirror.frontier.v3.model.FencedRecoveryBinding;
import io.farfrontier.palemirror.frontier.v3.model.FencedRecoveryState;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldStateUpdate;
import io.farfrontier.palemirror.frontier.v3.model.InventoryCustody;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalDelta;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalDeltaKind;
import io.farfrontier.palemirror.frontier.v3.model.ProductionJob;
import io.farfrontier.palemirror.frontier.v3.model.ProductionWorkSceneCause;
import io.farfrontier.palemirror.frontier.v3.model.RouteConstruction;
import io.farfrontier.palemirror.frontier.v3.model.RouteConstructionStarted;
import io.farfrontier.palemirror.frontier.v3.model.RouteConstructionStateSupport;
import io.farfrontier.palemirror.frontier.v3.model.RouteConstructionStatus;
import io.farfrontier.palemirror.frontier.v3.model.RouteMaintenanceStarted;
import io.farfrontier.palemirror.frontier.v3.model.RouteMaintenanceStateSupport;
import io.farfrontier.palemirror.frontier.v3.model.FrontierRouteNetwork;
import io.farfrontier.palemirror.frontier.v3.model.ResidentMigrationJourney;
import io.farfrontier.palemirror.frontier.v3.model.SceneLease;
import io.farfrontier.palemirror.frontier.v3.model.SceneLeaseStatus;
import io.farfrontier.palemirror.frontier.v3.model.SceneMember;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierExecutionMetrics;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;
import io.farfrontier.palemirror.frontier.v3.kernel.WorkBudget;
import io.farfrontier.palemirror.frontier.v3.process.RouteMaintenanceProcess;
import io.farfrontier.palemirror.frontier.v3.process.PhysicalIntentLifecycleFixture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Typed production, medical and route diagnostics have an independent test owner. */
class FrontierV3DiagnosticSceneJsonTest {
    @Test
    void exposesOneTypedProductionWorkSceneWithoutCallingItMedical(@TempDir Path directory) {
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = FrontierV3ServerRuntime.start(
                FrontierV3FixtureCatalog.productionInputTheftConfiguration(new WorldId("frontier:diagnostic-production-scene-test"), 41L),
                new FrontierFileStore(directory, FrontierWorldRuntimeDefinition.payloadCodecs()), 10_000);
        CheckpointImage checkpoint = runtime.checkpointImage().orElseThrow();
        FrontierWorldState state = runtime.decodedState().orElseThrow();
        var candidate = io.farfrontier.palemirror.frontier.v3.model.FrontierProductionWorkSceneSupport.candidates(state).stream().findFirst().orElseThrow();
        var leaseId = new io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId("lease:diagnostic-production-r0");
        var members = candidate.memberPositions().keySet().stream().sorted().map(actor -> new io.farfrontier.palemirror.frontier.v3.model.SceneMember(actor,
                io.farfrontier.palemirror.frontier.v3.model.SceneLease.deterministicEntityId(checkpoint.worldId(), leaseId, actor))).toList();
        var lease = io.farfrontier.palemirror.frontier.v3.model.SceneLease.forCause(leaseId, checkpoint.worldId(),
                new io.farfrontier.palemirror.frontier.v3.model.ProductionWorkSceneCause(candidate.jobId()), candidate.handoffPosition(), checkpoint.instant(),
                checkpoint.revision().value(), io.farfrontier.palemirror.frontier.v3.model.SceneLeaseStatus.PREPARED, members, java.util.Set.of(), Optional.empty());
        FrontierWorldState hot = state.prepareSceneLease(lease);
        for (var member : lease.members()) hot = io.farfrontier.palemirror.frontier.v3.model.ModeledActorBodyFacts.present(hot, member.actorId());
        hot = hot.transitionSceneLease(lease.id(), io.farfrontier.palemirror.frontier.v3.model.SceneLeaseStatus.HOT);
        var historical = SceneLease.forCause(new SceneLeaseId("lease:diagnostic-production-r-1"), checkpoint.worldId(),
                lease.cause(), lease.handoffPosition(), new SimInstant(0L), 0L, SceneLeaseStatus.CLOSED, lease.members(), lease.ambientHandoffActorIds(), Optional.empty());
        FrontierWorldState withHistoricalReceipt = hot.withChanges(io.farfrontier.palemirror.frontier.v3.model.FrontierWorldStateUpdate.begin()
                .sceneLeases(java.util.Map.of(historical.id(), historical, lease.id(), hot.sceneLeases().get(lease.id()))));

        String scene = FrontierV3DiagnosticJson.render("scene", candidate.jobId().value(), checkpoint, withHistoricalReceipt, Optional.empty());

        assertTrue(scene.contains("\"status\":\"ok\"") && scene.contains("\"sceneKind\":\"PRODUCTION_WORK\""));
        assertTrue(scene.contains("\"productionJob\":\"" + candidate.jobId().value() + "\"")
                        && scene.contains("\"medical\":\"\""),
                "a named workshop job must retain its own public scene identity rather than inherit an unrelated scene family");
        assertTrue(scene.contains("\"productionFutureBody\":"),
                "a route-obstruction pilot may inspect one immutable future worker body without selecting that worker");
        assertTrue(scene.contains("\"leaseId\":\"" + lease.id().value() + "\"") && scene.contains("\"leaseStatus\":\"HOT\""),
                "a current scene diagnostic must prefer the live lease over an equally-owned closed receipt");
        runtime.shutdown();
    }

    @Test
    void productionProcessDiagnosticKeepsTheColdCursorAndExactWorkerBodyReadableBeforeSceneAdmission(@TempDir Path directory) {
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = FrontierV3ServerRuntime.start(
                FrontierV3FixtureCatalog.productionWorkConfiguration(new WorldId("frontier:diagnostic-production-process-test"), 41L),
                new FrontierFileStore(directory, FrontierWorldRuntimeDefinition.payloadCodecs()), 10_000);
        CheckpointImage checkpoint = runtime.checkpointImage().orElseThrow();
        FrontierWorldState state = runtime.decodedState().orElseThrow();
        ProductionJob job = state.productionJobs().get(new SubjectId("job:production-development-input-theft"));
        assertTrue(job != null, "the production fixture must retain one exact active worker before scene admission");

        String process = FrontierV3DiagnosticJson.render("process", job.id().value(), checkpoint, state, Optional.empty());

        assertTrue(process.contains("\"status\":\"ok\"") && process.contains("\"family\":\"frontier.production-work\""));
        assertTrue(process.contains("\"worker\":\"" + job.workerId().value() + "\"")
                        && process.contains("\"facility\":\"" + job.facilityId().value() + "\"")
                        && process.contains("\"inputItem\":\"" + job.consumedItemId().value() + "\""),
                "the read-only receipt must retain the exact worker/facility/input ownership chain");
        assertTrue(process.contains("\"retainedBody\":") && process.contains("\"actorBody\":")
                        && process.contains("\"lease\":null") && process.contains("\"workStage\":\"APPROACH\""),
                "COLD ingress evidence needs the retained current cursor rather than a synthetic HOT scene");
        assertEquals(state, runtime.decodedState().orElseThrow(), "process rendering must not admit or advance the worker");
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
        var candidate = io.farfrontier.palemirror.frontier.v3.model.FrontierMedicalTreatmentSceneSupport.candidates(state).stream().findFirst().orElseThrow();
        var leaseId = new io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId("lease:diagnostic-medical-r0");
        var members = candidate.memberPositions().keySet().stream().sorted().map(actor -> new io.farfrontier.palemirror.frontier.v3.model.SceneMember(actor,
                io.farfrontier.palemirror.frontier.v3.model.SceneLease.deterministicEntityId(checkpoint.worldId(), leaseId, actor))).toList();
        var lease = io.farfrontier.palemirror.frontier.v3.model.SceneLease.forCause(leaseId, checkpoint.worldId(),
                new io.farfrontier.palemirror.frontier.v3.model.MedicalTreatmentSceneCause(operation.id()), candidate.infirmaryAnchor(),
                checkpoint.instant(), checkpoint.revision().value(), io.farfrontier.palemirror.frontier.v3.model.SceneLeaseStatus.PREPARED,
                members, java.util.Set.of(), Optional.empty());
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
                7, true, FrontierV3ResourceSiteHarvestExecutor.Precondition.READY, false, false);

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
        java.util.List<BlockPosition> waypoints = baseline.routeTopology().settlementWaypoints(baseline.bootstrap(), settlement);
        java.util.List<BlockPosition> bypass = java.util.List.of(waypoints.get(0), waypoints.get(1),
                waypoints.get(1).offset(-10, 0, 0), waypoints.get(2).offset(-10, 0, 0),
                waypoints.get(2), waypoints.get(3));
        java.util.List<BlockPosition> workCells = FrontierRouteNetwork.constructionCells(
                baseline.bootstrap(), baseline.routeTopology(), settlement, bypass);
        RouteConstruction project = new RouteConstruction(new SubjectId("construction:diagnostic-route"), settlement,
                bypass, workCells, 0, RouteConstructionStatus.BUILDING, Optional.empty(), Optional.empty(), Optional.empty());
        FrontierWorldState changed = RouteConstructionStateSupport.reduceStarted(baseline, FrontierRouteNetwork.OWNER,
                io.farfrontier.palemirror.frontier.v3.model.EngineeringExecutionEvents.constructionStarted(baseline, project));

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
