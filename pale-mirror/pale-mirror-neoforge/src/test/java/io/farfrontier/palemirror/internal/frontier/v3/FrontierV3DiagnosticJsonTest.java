package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.HarvestFixtureOwners;

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
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestGoal;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestJob;
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

class FrontierV3DiagnosticJsonTest {

    @Test
    void authored65CellHarvestDiagnosticReportsItsSemanticGoalAndYield() {
        var configuration = FrontierV3FixtureCatalog.configuration("resource-site-harvest-65",
                new WorldId("frontier:diagnostic-65-cell-field"), 125L);
        var checkpoint = FrontierEngines.create(configuration).checkpoint();
        var state = configuration.initialState();
        var job = (ResourceSiteHarvestJob) state.resourceSites().site(new SubjectId("site:1-wheat-field"))
                .harvestJobs().values().stream().reduce(HarvestFixtureOwners::rejectMultiple).orElseThrow();
        var json = JsonParser.parseString(FrontierV3DiagnosticJson.render("process",
                "job:site-harvest-1-wheat-field-1", checkpoint, state, Optional.empty())
                .substring(FrontierV3DiagnosticJson.PREFIX.length())).getAsJsonObject();
        var conservation = json.getAsJsonObject("conservation");
        assertEquals(65, conservation.get("totalCropSlots").getAsInt());
        assertEquals(0, conservation.get("deliveredYield").getAsInt());
        assertEquals("WORK_CELL", json.getAsJsonObject("semanticGoal").get("kind").getAsString());
        assertEquals(ResourceSiteHarvestGoal.current(state, job).nextWorkSlot(),
                json.getAsJsonObject("semanticGoal").get("nextWorkSlot").getAsInt());
        assertFalse(conservation.get("returningForBatch").getAsBoolean());
    }

    @Test
    void referenceCompositionCannotMasqueradeAsAnArbitraryContainer() {
        var configuration = FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:reference-view-scope"), 91L);
        var engine = FrontierEngines.create(configuration);
        var checkpoint = engine.checkpoint();
        var state = configuration.initialState();
        var accepted = JsonParser.parseString(FrontierV3DiagnosticJson.render("reference_container", "f02b",
                checkpoint, state, Optional.empty()).substring(FrontierV3DiagnosticJson.PREFIX.length())).getAsJsonObject();
        assertEquals("ok", accepted.get("status").getAsString());
        for (String id : List.of("container:7-depot", "container:1-depot", "unknown")) {
            var rejected = JsonParser.parseString(FrontierV3DiagnosticJson.render("reference_container", id,
                    checkpoint, state, Optional.empty()).substring(FrontierV3DiagnosticJson.PREFIX.length())).getAsJsonObject();
            assertEquals("not_found", rejected.get("status").getAsString());
            assertFalse(rejected.has("tasks"));
        }
        assertEquals(checkpoint, engine.checkpoint());
    }

    @Test
    void playerCustodyConflictKeepsTheSameStampedCauseForWhyIncidentAndBlockedAggregate() {
        var configuration = FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:diagnostic-player-conflict"), 91L);
        FrontierWorldState baseline = configuration.initialState();
        SubjectId item = new SubjectId("item:bootstrap-1-engineering-tool-1");
        var slot = (InventoryCustody.ContainerSlot) baseline.inventory().items().get(item).custody();
        SubjectId incident = new SubjectId("conflict:diagnostic-player-custody");
        var conflict = io.farfrontier.palemirror.frontier.v3.model.InventoryDiagnosticProducer.PLAYER_EXPECTED_SLOT_MISSING
                .create(incident, item, slot.containerId(), slot.slot());
        var tuple = conflict.diagnostic();
        FrontierWorldState conflicted = baseline.withInventory(baseline.inventory().recordConflict(conflict)).withDiagnosticIncidents(
                baseline.diagnosticIncidents().retain(tuple, "event:fixture", "command:fixture", 4, 0));
        CheckpointImage checkpoint = new CheckpointImage(configuration.worldId(), new io.farfrontier.palemirror.frontier.v3.api.Revision(4L),
                SimInstant.ZERO, new byte[]{1}, List.of(), List.of());
        String why = FrontierV3DiagnosticJson.render("why", "INVENTORY_SLOT/" + incident.value(), checkpoint, conflicted, Optional.empty());
        String retained = FrontierV3DiagnosticJson.render("incident", io.farfrontier.palemirror.frontier.v3.model.DiagnosticIncident.idFor(tuple), checkpoint, conflicted, Optional.empty());
        String summary = FrontierV3DiagnosticJson.render("summary", "", checkpoint, conflicted, Optional.empty());
        assertTrue(why.contains("\"reason\":\"INVENTORY_CONFLICT\"") && why.contains("\"owner\":\"" + slot.containerId().value()));
        assertTrue(retained.contains("\"subject\":\"" + incident.value()) && retained.contains("\"disposition\":\"INSPECT\""));
        assertTrue(retained.contains("\"bundle\":{") && retained.contains("\"event\":\"event:fixture\""),
                "one incident lookup must automatically render its identity-complete bounded bundle");
        assertTrue(summary.contains("\"diagnosticVerdict\":\"blocked\"") && summary.contains("\"inventoryConflicts\":1"));
    }

    @Test
    void settlementPopulationReceiptIsBoundedCompleteAndReadOnlyBeforeAClientVisit() {
        var configuration = FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:diagnostic-first-ingress"), 47L);
        FrontierWorldState state = configuration.initialState();
        SubjectId settlement = new SubjectId("settlement:7");
        var residents = state.humanPopulation().residents().values().stream()
                .filter(resident -> resident.settlementId().equals(settlement)).toList();
        var admissions = new java.util.LinkedHashMap<SubjectId, FrontierV3AmbientAdmissionDiagnostic>();
        residents.forEach(resident -> admissions.put(resident.id(), FrontierV3AmbientAdmissionDiagnostic.unloaded(
                FrontierV3AmbientActorExecutor.entityId(state, resident.id()))));
        CheckpointImage checkpoint = new CheckpointImage(configuration.worldId(), new io.farfrontier.palemirror.frontier.v3.api.Revision(3L),
                new SimInstant(120L), new byte[] {1}, List.of(), List.of());

        var population = JsonParser.parseString(FrontierV3DiagnosticJson.settlementPopulation(checkpoint, state, settlement.value(), admissions)
                .substring(FrontierV3DiagnosticJson.PREFIX.length())).getAsJsonObject();
        assertEquals(residents.size(), population.get("residentCount").getAsInt());
        assertTrue(population.get("residentCount").getAsInt() >= 20 && population.get("residentCount").getAsInt() <= 40);
        assertEquals(residents.size(), population.getAsJsonArray("residents").size());
        assertTrue(population.getAsJsonArray("residents").asList().stream().allMatch(value -> value.getAsJsonObject()
                .get("entityUuid").getAsString().matches("[0-9a-f-]{36}")));
        assertEquals(configuration.initialState(), state, "the pre-visit population census is a rendering-only receipt");
    }

    @Test
    void operatorStatusKeepsTheLatestFastForwardReceiptRecoverableWithoutMutatingCanonicalState() {
        var configuration = FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:operator-status"), 91L);
        FrontierWorldState state = configuration.initialState();
        CheckpointImage checkpoint = new CheckpointImage(configuration.worldId(), new io.farfrontier.palemirror.frontier.v3.api.Revision(3L),
                new SimInstant(120L), new byte[] {1}, List.of(), List.of());
        var receipt = new FrontierV3ServerLifecycle.FastForwardRequestOutcome(7L, "RELATIVE", 1_000, 1_120L,
                120L, 120L, "REJECTED", "physical work became pending during the relative interval: resource-site-projection:site:1-wheat-field");

        String value = FrontierV3DiagnosticJson.operatorStatus(checkpoint, state, List.of(receipt));

        assertTrue(value.contains("\"kind\":\"status\"") && value.contains("\"requestId\":7")
                        && value.contains("\"status\":\"REJECTED\"") && value.contains("resource-site-projection:site:1-wheat-field"),
                "one permission-gated status read must recover the causal terminal receipt instead of treating queue acknowledgement as elapsed time");
        assertEquals(state, configuration.initialState(), "status rendering may not become a second mutable clock or process owner");

        String selected = FrontierV3DiagnosticJson.operatorStatus(checkpoint, state, List.of(receipt), "site:1-wheat-field");
        assertTrue(selected.contains("\"selectedSubject\":{") && selected.contains("\"kind\":\"site\"")
                        && selected.contains("\"growthEpoch\":"),
                "one selected status query must retain the field lifecycle rather than require a world scan or a second status authority");
    }

    @Test
    void terminalMarketOrderDiagnosticRetainsItsExactInputOutputLineage() {
        // Ordinary route admission follows completed bread production; use that semantic
        // boundary instead of the old pre-labor assumption that work finishes at tick2200.
        var engine = FrontierEngines.create(FrontierV3FixtureCatalog.routeSceneReturnConfiguration(
                new WorldId("frontier:diagnostic-terminal-order"), 91L));
        FrontierWorldState state = new io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec()
                .decode(engine.checkpoint().canonicalState());
        var order = state.companies().market().workOrders().values().stream()
                .filter(value -> value.terminalReceipt().isPresent()).findFirst().orElseThrow();

        var json = JsonParser.parseString(FrontierV3DiagnosticJson.render("market_order", order.id().value(), engine.checkpoint(), state,
                Optional.empty()).substring(FrontierV3DiagnosticJson.PREFIX.length())).getAsJsonObject();
        var receipt = json.getAsJsonObject("terminalReceipt");
        assertEquals("FULFILLED", json.get("orderStatus").getAsString());
        assertEquals(order.terminalReceipt().orElseThrow().inputId().value(), receipt.get("inputItem").getAsString());
        assertEquals(order.terminalReceipt().orElseThrow().outputId().value(), receipt.get("outputItem").getAsString());
        assertEquals(order.terminalReceipt().orElseThrow().outputCount(), receipt.get("outputCount").getAsInt());
        assertEquals(Long.toString(order.terminalReceipt().orElseThrow().topologyRevision()),
                receipt.getAsJsonObject("topology").get("revision").getAsString(),
                "the read-only JSON receipt must preserve an exact signed-64 topology revision without JavaScript rounding");
    }

    @Test
    void siteDiagnosticExposesTheDurableFirstConflictIncidentRatherThanGenericConflict() {
        var configuration = FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:diagnostic-site-incident"), 91L);
        FrontierWorldState initial = configuration.initialState();
        SubjectId siteId = initial.resourceSites().sites().keySet().stream().sorted().findFirst().orElseThrow();
        var site = FrontierResourceSitePlan.compile(initial.bootstrap()).get(siteId);
        var conflict = new io.farfrontier.palemirror.frontier.v3.model.ResourceSiteConflictObserved(siteId, site.cropSlots().getFirst(),
                io.farfrontier.palemirror.frontier.v3.model.ResourceSiteDiagnosticProducer.ORDINARY_OBSERVATION_MISMATCH);
        FrontierWorldState conflicted = io.farfrontier.palemirror.frontier.v3.process.ResourceSiteProcess.reduceConflict(initial, siteId, conflict);
        String retainedId = conflicted.resourceSites().site(siteId).conflictDisposition().orElseThrow().incident().id();
        conflicted = conflicted.withDiagnosticIncidents(conflicted.diagnosticIncidents().retain(retainedId, conflict.diagnostic(), "event:fixture", "command:fixture", 4, 9));
        CheckpointImage checkpoint = new CheckpointImage(configuration.worldId(), new io.farfrontier.palemirror.frontier.v3.api.Revision(4L),
                new SimInstant(9L), new byte[] {1}, List.of(), List.of());

        var json = JsonParser.parseString(FrontierV3DiagnosticJson.render("site", siteId.value(), checkpoint, conflicted, Optional.empty())
                .substring(FrontierV3DiagnosticJson.PREFIX.length())).getAsJsonObject();
        var incident = json.getAsJsonObject("conflictDisposition").getAsJsonObject("incident");
        assertEquals("incident:resource-site:" + siteId.value().substring("site:".length()), incident.get("id").getAsString());
        assertEquals("CANONICAL_INVARIANT_FAILURE", incident.get("category").getAsString());
        assertEquals(siteId.value(), incident.get("owner").getAsString());
        assertEquals("RESOURCE_SITE", incident.get("ownerKind").getAsString());
        assertEquals("RESOURCE_SITE_CELL", incident.get("subjectKind").getAsString());
        assertTrue(incident.get("traceCorrelation").getAsString().startsWith("conflict:incident:resource-site:"));
        var summary = JsonParser.parseString(FrontierV3DiagnosticJson.render("summary", "", checkpoint, conflicted, Optional.empty())
                .substring(FrontierV3DiagnosticJson.PREFIX.length())).getAsJsonObject();
        assertEquals("blocked", summary.get("diagnosticVerdict").getAsString(),
                "a retained required conflict may not be summarized as green");
        String restartTrace = FrontierV3DiagnosticJson.render("trace", incident.get("traceCorrelation").getAsString(), checkpoint,
                conflicted, Optional.empty());
        assertTrue(restartTrace.contains("\"status\":\"trace_incomplete\"") && restartTrace.contains("\"history\":\"compacted_or_restart_local\""),
                "after volatile detail is gone, the persisted canonical incident remains queryable and explicitly incomplete");
        assertTrue(FrontierV3DiagnosticJson.render("why", "RESOURCE_SITE_CELL/" + siteId.value(), checkpoint, conflicted, Optional.empty())
                        .contains("\"owner\":\"" + siteId.value() + "\""),
                "why reads the exact supplied subject instead of discovering another owner");
        assertTrue(FrontierV3DiagnosticJson.render("incident", incident.get("id").getAsString(), checkpoint, conflicted, Optional.empty())
                        .contains("\"status\":\"ok\""), "incident reads the retained stable identity");
    }

    @Test
    void recoveryDiagnosticReadsOneExactCurrentOrRetiredFenceWithoutChangingCanonicalState() {
        var configuration = FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:diagnostic-recovery"), 41L);
        FrontierWorldState initial = configuration.initialState();
        SubjectId bindingId = new SubjectId("recovery:intent_diagnostic");
        FencedRecoveryState prepared = FencedRecoveryState.empty().prepare(FencedRecoveryBinding.prepared(bindingId,
                FencedRecoveryAsset.EFFECT, new SubjectId("settlement:1"), 7L, 1L, false));
        FrontierWorldState fenced = initial.withChanges(FrontierWorldStateUpdate.begin().fencedRecovery(prepared));
        CheckpointImage checkpoint = new CheckpointImage(configuration.worldId(), new io.farfrontier.palemirror.frontier.v3.api.Revision(4L),
                new SimInstant(9L), new byte[] {1}, List.of(), List.of());

        String current = FrontierV3DiagnosticJson.render("recovery", bindingId.value(), checkpoint, fenced, Optional.empty());
        FrontierWorldState retired = fenced.withChanges(FrontierWorldStateUpdate.begin().fencedRecovery(
                prepared.running(bindingId, 1L).observed(bindingId, 1L).confirm(bindingId, 1L)));
        String tombstone = FrontierV3DiagnosticJson.render("recovery", bindingId.value(), checkpoint, retired, Optional.empty());

        assertTrue(current.contains("\"status\":\"ok\"") && current.contains("\"asset\":\"EFFECT\"")
                && current.contains("\"ownerRevision\":7") && current.contains("\"epoch\":1") && current.contains("\"phase\":\"PREPARED\""));
        assertTrue(tombstone.contains("\"status\":\"retired\"") && tombstone.contains("\"disposition\":\"REJECT_STALE\""));
        assertEquals(prepared, fenced.fencedRecovery(), "a diagnostic receipt is never recovery authority or a mutation path");
    }

    @Test
    void returningHiveDiagnosticIsAParseablePilotDocument() {
        var configuration = FrontierV3FixtureCatalog.hiveReturnConfiguration(new WorldId("frontier:diagnostic-hive-return"), 41L);
        FrontierWorldState state = configuration.initialState();
        HiveMobilization mobilization = state.hiveColony().mobilizations().values().stream().findFirst().orElseThrow();
        HiveReturnAssembly returning = mobilization.returnAssembly().orElseThrow();
        SubjectId next = returning.safeAdvances().stream().min(java.util.Comparator.naturalOrder()).orElseThrow();
        HiveTaskAssembly.Member member = returning.members().get(next);
        CheckpointImage checkpoint = new CheckpointImage(configuration.worldId(), new io.farfrontier.palemirror.frontier.v3.api.Revision(0L),
                configuration.initialInstant(), new byte[] {1}, List.of(), List.of());

        String json = FrontierV3HiveMobilizationDiagnostic.returningJson(checkpoint, state, mobilization, returning, next, member,
                0L, 0L, new FrontierV3PhysicalDemand.Readiness(false, false, false, 0, 0), false, "[]");

        var parsed = JsonParser.parseString(json.substring(FrontierV3DiagnosticJson.PREFIX.length())).getAsJsonObject();
        assertEquals("RETURNING", parsed.get("mobilizationStatus").getAsString());
        assertEquals(4, parsed.get("survivors").getAsInt());
        assertEquals(next.value(), parsed.get("nextMember").getAsString());
        assertEquals(member.cursor(), parsed.get("cursor").getAsInt());
        assertEquals(4, parsed.getAsJsonArray("survivorPositions").size(),
                "the exact native-pilot diagnostic remains a single parseable JSON document");
    }

    @Test
    void exposesTheExactPendingAftermathOwnerPartAndAuthorityRevisionWithoutMutatingIt() {
        var configuration = FrontierV3FixtureCatalog.coldBomberAftermathConfiguration(new WorldId("frontier:diagnostic-cold-aftermath"), 41L);
        var engine = (io.farfrontier.palemirror.frontier.v3.api.FrontierCanonicalStateAccess<FrontierWorldState, FrontierWorldProjection>) FrontierEngines.createCanonicalStateAccess(configuration);
        long dueAt = configuration.initialSchedules().getFirst().dueAt().ticks();
        engine.advanceTo(new SimInstant(dueAt), new WorkBudget(64, 512));
        FrontierWorldState state = engine.canonicalState().state();
        var aftermath = state.deferredAftermath().entries().values().stream().findFirst().orElseThrow();
        CheckpointImage checkpoint = new CheckpointImage(configuration.worldId(), engine.canonicalState().revision(), new SimInstant(dueAt), new byte[]{1}, List.of(), List.of());

        String json = FrontierV3DiagnosticJson.render("aftermath", aftermath.causeId().value(), checkpoint, state, Optional.empty());

        assertTrue(json.contains("\"status\":\"ok\"") && json.contains("\"aftermathId\":\"" + aftermath.id().value() + "\""));
        assertTrue(json.contains("\"expectedOwner\":\"" + aftermath.cells().getFirst().expectedOwner().value() + "\"")
                        && json.contains("\"expectedPart\":\"" + aftermath.cells().getFirst().expectedPart() + "\"")
                        && json.contains("\"authorityRevision\":-1"),
                "the read-only COLD diagnostic retains the pending owner, semantic part and pre-effect revision");
        assertEquals(state, engine.canonicalState().state(), "diagnostic exposure cannot manufacture an aftermath transition");
    }

    @Test
    void derivesTypedProductionWorkTraceInsteadOfFallingBackToLogistics() {
        SubjectId worker = new SubjectId("resident:1-15");
        SubjectId job = new SubjectId("job:production-development-input-theft");
        WorldId world = new WorldId("frontier:production-trace-test");
        SceneLease lease = SceneLease.forCause(new SceneLeaseId("lease:production-work-development-input-theft-r1"), world,
                new ProductionWorkSceneCause(job), new BlockPosition(-340, 64, -329), new SimInstant(1L), 1L,
                SceneLeaseStatus.PREPARED, List.of(new SceneMember(worker, SceneLease.deterministicEntityId(world, worker))), java.util.Set.of(), Optional.empty());

        FrontierV3DiagnosticTrace.SceneTrace trace = FrontierV3DiagnosticTrace.sceneTrace(lease);

        assertEquals("production-work:" + job.value(), trace.correlation());
        assertEquals(job, trace.subject());
        assertEquals(lease.id().value(), trace.context().leaseId());
        assertTrue(trace.context().operationId().isEmpty() && trace.context().cargoId().isEmpty());
        assertEquals(List.of(worker.value()), trace.context().actorIds());
    }

    @Test
    void rendersBoundedReadOnlyPerformanceAttribution() {
        FrontierV3PerformanceMetrics metrics = new FrontierV3PerformanceMetrics();
        metrics.begin(FrontierExecutionMetrics.Stage.PHYSICAL, "scenes", "scene").close();
        metrics.observeQueue(new SimInstant(12L), 7, Optional.of(new ScheduledAction(
                new io.farfrontier.palemirror.frontier.v3.api.ScheduleId("schedule:performance"), new SimInstant(4L), 0,
                new SubjectId("settlement:1"), "process.performance", 1)));
        CheckpointImage checkpoint = new CheckpointImage(new WorldId("frontier:performance-diagnostic"),
                new io.farfrontier.palemirror.frontier.v3.api.Revision(3L), new SimInstant(12L), new byte[]{1}, List.of(), List.of());

        String value = FrontierV3PerformanceDiagnostic.render(checkpoint, metrics.snapshot(), null, 17, null, null,
                new FrontierV3ServerLifecycle.FastForwardTargetOutcome(4L, 12L, 11L, null, "REJECTED", "physical work is pending at admission"),
                new FrontierV3ServerLifecycle.FastForwardSliceTelemetry(2L, 40L, 23L, 12L, 7L, 20L, 11L, 7L));

        assertTrue(value.startsWith(FrontierV3DiagnosticJson.PREFIX + "{\"schema\":1,\"kind\":\"performance\""));
        assertTrue(value.contains("\"stage\":\"PHYSICAL\"") && value.contains("\"maxLagTicks\":8")
                && value.contains("\"fastForwardRemaining\":17") && value.contains("\"instant\":12")
                && value.contains("\"world\":\"frontier:performance-diagnostic\"")
                && value.contains("\"requestId\":4") && value.contains("\"targetInstant\":12") && value.contains("\"admittedCheckpointInstant\":11")
                && value.contains("\"status\":\"REJECTED\"") && value.contains("\"fastForwardSlice\":{\"samples\":2,\"advancedTicks\":7")
                && value.contains("\"safetyNanos\":12") && value.contains("\"advanceNanos\":20")
                && value.contains("\"worstSpan\":{\"stage\":\"PHYSICAL\",\"kind\":\"scenes\",\"owner\":\"scene\""));
        assertTrue(value.length() < 8_192, "performance diagnostics retain the ordinary bounded operator response limit");
    }

    @Test
    void retainsAUsefulPerformancePressureCutAtMaximumAttributionCardinality() {
        List<FrontierExecutionMetrics.StageSample> stages = new ArrayList<>();
        List<FrontierExecutionMetrics.QueueSample> queues = new ArrayList<>();
        String padding = "x".repeat(100);
        for (int index = 0; index < 256; index++) {
            stages.add(new FrontierExecutionMetrics.StageSample(FrontierExecutionMetrics.Stage.PHYSICAL,
                    "stage-" + index + '-' + padding, "owner-" + index + '-' + padding,
                    1L, 2L, 2L, 2L, 2L, 2L));
            queues.add(new FrontierExecutionMetrics.QueueSample("queue-" + index + '-' + padding,
                    "owner-" + index + '-' + padding, 1L, 1, 1, 1L, 1L));
        }
        CheckpointImage checkpoint = new CheckpointImage(new WorldId("frontier:performance-cardinality"),
                new io.farfrontier.palemirror.frontier.v3.api.Revision(3L), new SimInstant(12L), new byte[]{1}, List.of(), List.of());

        String value = FrontierV3PerformanceDiagnostic.render(checkpoint,
                new FrontierExecutionMetrics.Snapshot(stages, queues, 0L));

        assertTrue(value.contains("\"status\":\"ok\""), "a bounded pressure cut must remain readable");
        assertTrue(value.length() < 8_192, "maximum retained telemetry cannot turn into response_limit");
        assertEquals(13, value.split("\\\"stage\\\":").length - 1,
                "the pressure cut retains twelve cumulative spans plus one independent worst-span caller");
        assertEquals(8, value.split("\\\"currentDepth\\\":").length - 1);
    }

    @Test
    void reportsOnlyBoundedCanonicalPressureFactsWithoutLevelEnumeration() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:performance-pressure"), 91L));
        CheckpointImage checkpoint = new CheckpointImage(state.bootstrap().worldId(), new io.farfrontier.palemirror.frontier.v3.api.Revision(3L),
                new SimInstant(12L), new byte[]{1, 2, 3}, List.of(), List.of());

        String value = FrontierV3PerformanceDiagnostic.render(checkpoint, new FrontierV3PerformanceMetrics().snapshot(), state,
                0, null, null, null);

        assertTrue(value.contains("\"frontier\":{") && value.contains("\"settlements\":12") && value.contains("\"seedNests\":2")
                && value.contains("\"settlementDecisionAuthorities\":12") && value.contains("\"hivemindDecisionAuthorities\":1")
                && value.contains("\"queues\":[],\"frontier\":{") && value.contains("\"sceneActorBindings\":0")
                && value.contains("\"managedActorBindings\":0")
                && value.contains("\"checkpointBytes\":3"), "the pressure cut is a bounded canonical count, not a level census");
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
        SubjectId dormantBioform = state.hiveColony().bioformLifecycles().entrySet().stream()
                .filter(entry -> entry.getValue().phase().occupiesCocoon()).map(java.util.Map.Entry::getKey).sorted().findFirst().orElseThrow();
        SubjectId item = state.inventory().items().keySet().stream().sorted().findFirst().orElseThrow();
        SubjectId settlement = state.bootstrap().settlements().getFirst().id();
        SubjectId hive = state.bootstrap().hive().id();
        SubjectId container = state.inventory().containers().keySet().stream().sorted().findFirst().orElseThrow();
        SubjectId resource = state.inventory().fungibleResources().accounts().keySet().stream().sorted().findFirst().orElseThrow();

        String summary = FrontierV3DiagnosticJson.render("summary", "", checkpoint, state, Optional.empty());
        String physicalLifecycle = FrontierV3DiagnosticJson.render("physical_lifecycle", "", checkpoint, state, Optional.empty());
        String inventoryJson = FrontierV3DiagnosticJson.render("process_inventory", "settlements", checkpoint, state, Optional.empty());
        String siteJson = FrontierV3DiagnosticJson.render("site", site.value(), checkpoint, state, Optional.empty());
        String actorJson = FrontierV3DiagnosticJson.render("actor", actor.value(), checkpoint, state, Optional.empty());
        String dormantBioformJson = FrontierV3DiagnosticJson.render("actor", dormantBioform.value(), checkpoint, state, Optional.empty());
        String itemJson = FrontierV3DiagnosticJson.render("item", item.value(), checkpoint, state, Optional.empty());
        String settlementJson = FrontierV3DiagnosticJson.render("settlement", settlement.value(), checkpoint, state, Optional.empty());
        String hiveJson = FrontierV3DiagnosticJson.render("hive", hive.value(), checkpoint, state, Optional.empty());
        String containerJson = FrontierV3DiagnosticJson.render("container", container.value(), checkpoint, state, Optional.empty());
        String resourceJson = FrontierV3DiagnosticJson.render("resource", resource.value(), checkpoint, state, Optional.empty());
        String missing = FrontierV3DiagnosticJson.render("site", "site:missing", checkpoint, state, Optional.empty());

        assertTrue(summary.startsWith(FrontierV3DiagnosticJson.PREFIX + "{\"schema\":1,\"kind\":\"summary\""));
        assertTrue(summary.contains("\"settlements\":12"));
        assertTrue(physicalLifecycle.contains("\"kind\":\"physical_lifecycle\"")
                        && physicalLifecycle.contains("\"owner\":\"frontier.resource-site-harvest\"")
                        && physicalLifecycle.contains("\"pressure\":\"OPEN\""),
                "composition diagnostics must expose the installed owner/schema declaration and derived retention pressure");
        assertTrue(inventoryJson.contains("\"kind\":\"process_inventory\"") && inventoryJson.contains("\"count\":12")
                        && inventoryJson.contains("\"site\":\"site:1-wheat-field\"") && inventoryJson.contains("\"waitReason\":\"AWAITING_PREPARATION\""),
                "the all-current-process inventory must be bounded, read-only, and say why an ineligible site is waiting");
        assertTrue(siteJson.contains("\"id\":\"" + site.value() + "\""));
        assertTrue(siteJson.contains("\"firstCrop\":{"));
        assertTrue(siteJson.contains("\"boardPosition\":{"),
                "the current site diagnostic must publish the immutable board-plan anchor used by player-visible verification");
        assertTrue(actorJson.contains("\"position\":{"));
        assertTrue(actorJson.contains("\"nutrition\":\"NOURISHED\""));
        assertTrue(JsonParser.parseString(actorJson.substring(FrontierV3DiagnosticJson.PREFIX.length()))
                        .getAsJsonObject().has("assignmentOwner"),
                "the ordinary actor query must remain parseable when it carries assignment ownership");
        assertTrue(dormantBioformJson.contains("\"actorKind\":\"BIOFORM\"") && dormantBioformJson.contains("\"lifecycle\":\"DORMANT\""),
                "an operator and test-pilot must be able to distinguish an occupied cocoon from an ambient bioform");
        assertTrue(dormantBioformJson.contains("\"cocoonHome\":{\"hibernaculum\":\"organ:"),
                "the exact durable cocoon custody must be inspectable without mutating canonical state");
        assertTrue(itemJson.contains("\"custody\":{"));
        assertTrue(settlementJson.contains("\"harvestAdmission\":\"NO_READY_SITE\""));
        assertTrue(settlementJson.contains("\"strategic\":{"));
        assertTrue(settlementJson.contains("\"quarantine\":\"NORMAL\"") && settlementJson.contains("\"activeCases\":0"),
                "one named settlement view exposes bounded health policy facts without resident histories");
        var food = JsonParser.parseString(settlementJson.substring(FrontierV3DiagnosticJson.PREFIX.length()))
                .getAsJsonObject().getAsJsonObject("food");
        assertEquals("SHORTAGE", food.get("status").getAsString());
        assertEquals(0, food.get("stock").getAsInt());
        assertEquals(0, food.get("available").getAsInt());
        assertTrue(food.get("reserve").getAsInt() > 0,
                "one named settlement view distinguishes current stock from COLD-usable bread and derived reserve");
        long due = checkpoint.schedules().stream().filter(action -> action.kind().equals("frontier.resident.need.review"))
                .filter(action -> state.humanPopulation().resident(action.subject()).settlementId().equals(settlement))
                .mapToLong(action -> action.dueAt().ticks()).min().orElseThrow();
        assertEquals(due, food.get("nextNeedReviewAt").getAsLong());
        var noReview = new CheckpointImage(checkpoint.worldId(), checkpoint.revision(), checkpoint.instant(),
                checkpoint.canonicalState(), checkpoint.schedules().stream().filter(action ->
                    !action.kind().equals("frontier.resident.need.review")
                    || !state.humanPopulation().resident(action.subject()).settlementId().equals(settlement)).toList(), checkpoint.receipts());
        var missingReviewFood = JsonParser.parseString(FrontierV3DiagnosticJson.render("settlement", settlement.value(),
                noReview, state, Optional.empty()).substring(FrontierV3DiagnosticJson.PREFIX.length())).getAsJsonObject().getAsJsonObject("food");
        assertTrue(missingReviewFood.get("nextNeedReviewAt").isJsonNull(),
                "never invent a need deadline when the exact resident schedule is missing");
        assertTrue(settlementJson.contains("\"nourished\":" + state.bootstrap().settlements().getFirst().residents().size()
                        + ",\"hungry\":0,\"starving\":0"),
                "one named settlement view exposes bounded individual nutrition totals without a separate aggregate owner");
        assertTrue(hiveJson.contains("\"infectionCells\":18") && hiveJson.contains("\"addedOrgans\":0"),
                "one named hive diagnostic exposes bounded canonical expansion state without materializing it");
        assertTrue(containerJson.contains("\"surface\":") && containerJson.contains("\"position\":{") && containerJson.contains("\"occupiedCount\":") && containerJson.contains("\"occupied\":[")
                        && containerJson.contains("\"fungibleOccupiedCount\":") && containerJson.contains("\"fungibleOccupied\":["),
                "one diagnostic must distinguish exact occupied slots from currently bound fungible stock");
        assertTrue(resourceJson.contains("\"account\":\"" + resource.value() + "\"") && resourceJson.contains("\"custody\":{\"kind\":\"CONTAINER\"")
                        && resourceJson.contains("\"lots\":[{\"id\":\"lot:bootstrap-") && resourceJson.contains("\"bindingCount\":0")
                        && resourceJson.contains("\"bindings\":[]"),
                "one exact resource account exposes its canonical quantity and no invented physical binding");
        assertTrue(missing.contains("\"status\":\"not_found\""));
        assertTrue(summary.length() < 8_192 && physicalLifecycle.length() < 8_192 && inventoryJson.length() < 8_192 && siteJson.length() < 8_192 && actorJson.length() < 8_192 && itemJson.length() < 8_192
                && settlementJson.length() < 8_192 && hiveJson.length() < 8_192 && containerJson.length() < 8_192 && resourceJson.length() < 8_192);
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
    void rendersAmbientWorkAsOwnedPostHarvestDutyRatherThanUnobserved(@TempDir Path directory) {
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = FrontierV3ServerRuntime.start(
                FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:diagnostic-ambient-return"), 91L),
                new FrontierFileStore(directory, FrontierWorldRuntimeDefinition.payloadCodecs()), 10_000);
        CheckpointImage checkpoint = runtime.checkpointImage().orElseThrow();
        FrontierWorldState state = runtime.decodedState().orElseThrow();
        SubjectId actor = state.humanPopulation().residentIds().stream().sorted().findFirst().orElseThrow();
        var leases = new LinkedHashMap<>(state.ambientLeases());
        BodyPosition body = state.actorLocations().get(actor).body();
        leases.put(actor, new io.farfrontier.palemirror.frontier.v3.model.AmbientActorLease(actor, body,
                io.farfrontier.palemirror.frontier.v3.api.SimInstant.ZERO, 1L,
                io.farfrontier.palemirror.frontier.v3.model.AmbientLeaseStatus.HOT,
                io.farfrontier.palemirror.frontier.v3.model.AmbientGoalKind.WORK, body));

        String json = FrontierV3DiagnosticJson.render("actor", actor.value(), checkpoint,
                state.withChanges(FrontierWorldStateUpdate.begin().ambientLeases(leases)), Optional.empty());

        assertTrue(json.contains("\"dutyPhase\":\"AMBIENT:WORK:HOT\""),
                "the exact actor query must retain a typed shared-custody phase between harvest receipt and successor assignment");
        assertFalse(json.contains("\"dutyPhase\":\"UNOBSERVED\""));
        runtime.shutdown();
    }

    @Test
    void exposesBoundedReadOnlyPhysicalContainerSocketFacts(@TempDir Path directory) {
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = FrontierV3ServerRuntime.start(
                FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:diagnostic-container-socket"), 91L),
                new FrontierFileStore(directory, FrontierWorldRuntimeDefinition.payloadCodecs()), 10_000);
        CheckpointImage checkpoint = runtime.checkpointImage().orElseThrow();
        FrontierWorldState state = runtime.decodedState().orElseThrow();
        SubjectId container = state.inventory().containers().keySet().stream().sorted().findFirst().orElseThrow();
        var readiness = new FrontierV3ContainerSurfaceExecutor.Readiness("LOADED", "CONFLICT", "READY",
                "minecraft:chest", "FOREIGN_OR_UNTAGGED", "UNAVAILABLE", "", false, false, 0, 0);

        String json = FrontierV3DiagnosticJson.render("container", container.value(), checkpoint, state, Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(readiness));

        assertTrue(json.contains("\"physicalSocket\":{\"chunk\":\"LOADED\",\"freshSocket\":\"CONFLICT\""));
        assertTrue(json.contains("\"targetBlock\":\"minecraft:chest\"") && json.contains("\"chest\":\"FOREIGN_OR_UNTAGGED\""));
        assertTrue(json.contains("\"presentationDemand\":false,\"eligibleObserverCount\":0,\"presentationObserverCount\":0"));
        assertTrue(runtime.decodedState().orElseThrow().equals(state), "container socket diagnostics must not mutate canonical inventory or load work");
        runtime.shutdown();
    }

    @Test
    void ownedContainerDoesNotPresentThePreProjectionFreshSocketProbeAsAConflict() {
        assertEquals("NOT_APPLICABLE_OWNED", FrontierV3ContainerSurfaceExecutor.freshSocketDiagnostic(
                FrontierV3ContainerSurfaceExecutor.SocketReadiness.CONFLICT, true),
                "a chest occupying its own active socket makes the fresh-air probe inapplicable, not conflicting");
        assertEquals("CONFLICT", FrontierV3ContainerSurfaceExecutor.freshSocketDiagnostic(
                FrontierV3ContainerSurfaceExecutor.SocketReadiness.CONFLICT, false),
                "foreign or untagged occupancy remains truthful conflict evidence");
    }

    @Test
    void exposesBoundedReadOnlyAmbientAdmissionEvidence(@TempDir Path directory) {
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = FrontierV3ServerRuntime.start(
                FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:diagnostic-admission-test"), 93L),
                new FrontierFileStore(directory, FrontierWorldRuntimeDefinition.payloadCodecs()), 10_000);
        CheckpointImage checkpoint = runtime.checkpointImage().orElseThrow();
        FrontierWorldState state = runtime.decodedState().orElseThrow();
        SubjectId actor = state.actorLocations().keySet().stream().sorted().findFirst().orElseThrow();
        var evidence = new FrontierV3AmbientAdmissionDiagnostic("BLOCKED", UUID.fromString("6e6a062d-a182-469c-9de8-2de3f3703ee1"), false,
                new io.farfrontier.palemirror.frontier.v3.model.BlockPosition(4, 65, 8), null, null);

        String actorJson = FrontierV3DiagnosticJson.render("actor", actor.value(), checkpoint, state, Optional.empty(), Optional.of(evidence));

        assertTrue(actorJson.contains("\"physicalAdmission\":{\"status\":\"BLOCKED\""));
        assertTrue(actorJson.contains("\"placement\":{\"x\":4,\"y\":65,\"z\":8}"));
        assertTrue(actorJson.contains("\"observedExact\":null"));
        assertTrue(actorJson.contains("\"motionStatus\":\"IDLE\",\"motionTarget\":null,\"motionAcceptedMoves\":0"));
    }

    @Test
    void rendersSceneOwnedBodyEvidenceAsASeparateAdmissionState(@TempDir Path directory) {
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = FrontierV3ServerRuntime.start(
                FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:diagnostic-scene-owner-test"), 93L),
                new FrontierFileStore(directory, FrontierWorldRuntimeDefinition.payloadCodecs()), 10_000);
        CheckpointImage checkpoint = runtime.checkpointImage().orElseThrow();
        FrontierWorldState state = runtime.decodedState().orElseThrow();
        SubjectId actor = state.actorLocations().keySet().stream().sorted().findFirst().orElseThrow();
        var evidence = new FrontierV3AmbientAdmissionDiagnostic("SCENE_OWNED", UUID.fromString("6e6a062d-a182-469c-9de8-2de3f3703ee2"), false,
                null, new io.farfrontier.palemirror.frontier.v3.model.BlockPosition(4, 65, 8),
                new FrontierV3AmbientActorExecutor.ObservedPosition(4.5D, 65.0D, 8.5D));

        String actorJson = FrontierV3DiagnosticJson.render("actor", actor.value(), checkpoint, state, Optional.empty(), Optional.of(evidence));

        assertTrue(actorJson.contains("\"physicalAdmission\":{\"status\":\"SCENE_OWNED\""));
        assertTrue(actorJson.contains("\"observedPosition\":{\"x\":4,\"y\":65,\"z\":8}"));
        runtime.shutdown();
    }

    @Test
    void rendersTerminalActorEvidenceWithoutCallingItsResidualDeathBodyAUuidConflict(@TempDir Path directory) {
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = FrontierV3ServerRuntime.start(
                FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:diagnostic-terminal-owner-test"), 93L),
                new FrontierFileStore(directory, FrontierWorldRuntimeDefinition.payloadCodecs()), 10_000);
        CheckpointImage checkpoint = runtime.checkpointImage().orElseThrow();
        FrontierWorldState state = runtime.decodedState().orElseThrow();
        SubjectId actor = state.actorLocations().keySet().stream().sorted().findFirst().orElseThrow();
        var evidence = new FrontierV3AmbientAdmissionDiagnostic("TERMINAL", UUID.fromString("6e6a062d-a182-469c-9de8-2de3f3703ee3"), false,
                null, null, null);

        String actorJson = FrontierV3DiagnosticJson.render("actor", actor.value(), checkpoint, state, Optional.empty(), Optional.of(evidence));

        assertTrue(actorJson.contains("\"physicalAdmission\":{\"status\":\"TERMINAL\""));
        assertFalse(actorJson.contains("UUID_CONFLICT"));
        runtime.shutdown();
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
        var readiness = new FrontierV3OperationAssemblyDiagnostic.Readiness(java.util.List.of(
                new FrontierV3OperationAssemblyDiagnostic.MemberReadiness(entry.getKey(), member.currentSurface().support(),
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
        var operations = state.operations().values().stream()
                .filter(value -> value.settlementId().equals(new SubjectId("settlement:1")) && value.activeTravel().isPresent()).toList();
        assertEquals(1, operations.size(), "fixture must expose one exact initial shipment");
        var operation = operations.getFirst();
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
        assertTrue(known.contains("\"chain\":[\"resource_site_conflict\"]"));
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
    void exposesExactProductionLeaseAndActorWithoutAnOperation(@TempDir Path directory) {
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = FrontierV3ServerRuntime.start(
                FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:diagnostic-production-trace-context"), 92L),
                new FrontierFileStore(directory, FrontierWorldRuntimeDefinition.payloadCodecs()), 10_000);
        CheckpointImage checkpoint = runtime.checkpointImage().orElseThrow();
        FrontierWorldState state = runtime.decodedState().orElseThrow();
        FrontierV3DiagnosticTrace.Entry trace = new FrontierV3DiagnosticTrace.Entry("production-work:job:production-1-1", "scene_released",
                "job:production-1-1", "executor:scene-release", "transaction:scene-release", 8L,
                new FrontierV3DiagnosticTrace.Context("", "lease:production-work-1-1-r7", "", List.of("resident:1-15")));

        String rendered = FrontierV3DiagnosticJson.render("trace", trace.correlation(), checkpoint, state, Optional.of(trace));

        assertTrue(rendered.contains("\"causal\":{\"operation\":\"\",\"lease\":\"lease:production-work-1-1-r7\""));
        assertTrue(rendered.contains("\"actors\":[\"resident:1-15\"]"));
        assertTrue(!rendered.contains("position") && !rendered.contains("uuid"), "trace context must remain bounded evidence only");
    }

    @Test
    void derivesOneStableTraceCorrelationForTheCompleteRouteRepairWorkOrder() {
        assertTrue(FrontierV3DiagnosticTrace.routeConstructionCorrelation(new SubjectId("construction:route-reroute-settlement-1--380-64--304"))
                .equals("route-construction:construction:route-reroute-settlement-1--380-64--304"));
        assertTrue(FrontierV3DiagnosticTrace.routeMaintenanceCorrelation(new SubjectId("maintenance:route--380-64--304"))
                .equals("route-maintenance:maintenance:route--380-64--304"));
    }

    @Test
    void exposesOneRetainedInPlaceRouteMaintenanceWithoutInventingABypass(@TempDir Path directory) {
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = FrontierV3ServerRuntime.start(
                FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:diagnostic-route-maintenance-test"), 95L),
                new FrontierFileStore(directory, FrontierWorldRuntimeDefinition.payloadCodecs()), 10_000);
        CheckpointImage checkpoint = runtime.checkpointImage().orElseThrow();
        FrontierWorldState state = runtime.decodedState().orElseThrow();
        SubjectId settlement = state.bootstrap().settlements().getFirst().id();
        BlockPosition position = state.routeTopology().supplyWaypoints(state.bootstrap(), settlement).get(1);
        FrontierWorldState damaged = state.recordPhysicalDelta(new PhysicalDelta(position, PhysicalDeltaKind.KNOWN_SEMANTIC_LOSS,
                Optional.of(new io.farfrontier.palemirror.frontier.v3.model.PhysicalDeltaSemanticTarget(io.farfrontier.palemirror.frontier.v3.model.PhysicalDeltaSemanticTargetKind.ROUTE_NETWORK,
                        FrontierRouteNetwork.OWNER)), Optional.of(GrayboxSemanticPart.ROUTE_SURFACE), "player:test"));
        RouteMaintenanceStarted started = RouteMaintenanceProcess.plan(damaged, RouteMaintenanceProcess.scan(1, 100L)).stream()
                .map(io.farfrontier.palemirror.frontier.v3.api.ProposedEvent::payload).filter(RouteMaintenanceStarted.class::isInstance)
                .map(RouteMaintenanceStarted.class::cast).findFirst().orElseThrow();
        FrontierWorldState admitted = RouteMaintenanceStateSupport.reduceStarted(damaged, FrontierRouteNetwork.OWNER, started);

        String value = FrontierV3DiagnosticJson.render("route_maintenance", settlement.value(), checkpoint, admitted, Optional.empty());

        assertTrue(value.contains("\"status\":\"ok\"") && value.contains("\"maintenance\":\"" + started.maintenance().id().value() + "\""));
        assertTrue(value.contains("\"repairCell\":{\"x\":" + position.x()) && value.contains("\"semanticPart\":\"ROUTE_SURFACE\""));
        assertTrue(value.contains("\"cargoPresent\":false") && value.contains("\"teamPresent\":true"));
        assertTrue(value.contains("\"toolReturnRequired\":false") && value.contains("\"toolReturnReadiness\":\"NOT_READY_FOR_RETURN\""),
                "the read-only maintenance view must expose the planner's terminal-return precondition without preparing an intent");
        assertTrue(admitted.routeConstructions().isEmpty(), "the read-only maintenance view may not create a bypass project");
        runtime.shutdown();
    }

    @Test
    void exposesOneExactPhysicalDeltaWithoutLeakingThePlayerIdentity(@TempDir Path directory) {
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = FrontierV3ServerRuntime.start(
                FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:diagnostic-delta-test"), 95L),
                new FrontierFileStore(directory, FrontierWorldRuntimeDefinition.payloadCodecs()), 10_000);
        CheckpointImage checkpoint = runtime.checkpointImage().orElseThrow();
        BlockPosition position = new BlockPosition(-380, 64, -304);
        FrontierWorldState changed = runtime.decodedState().orElseThrow().recordPhysicalDelta(new PhysicalDelta(position,
                PhysicalDeltaKind.KNOWN_SEMANTIC_LOSS,
                Optional.of(new io.farfrontier.palemirror.frontier.v3.model.PhysicalDeltaSemanticTarget(
                        io.farfrontier.palemirror.frontier.v3.model.PhysicalDeltaSemanticTargetKind.ROUTE_NETWORK,
                        new SubjectId("route:frontier-network"))),
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
        var candidate = state.coldEngagementSceneCandidates().getFirst();
        String id = candidate.engagementId().value();

        String scene = FrontierV3DiagnosticJson.render("scene", id, checkpoint, state, Optional.empty());
        String operation = FrontierV3DiagnosticJson.render("operation", candidate.operationId().value(), checkpoint, state, Optional.empty());

        assertTrue(scene.contains("\"status\":\"not_found\""), "without a materialized scene lease the read-only view must not create one");
        assertTrue(operation.contains("\"hiveEngagement\":{\"status\":\"COLD_COMBAT\",\"command\":{\"kind\":\"OVERSEER\"")
                && operation.contains("\"signal\":\"CONNECTED\""),
                "the player-facing operation diagnostic retains its exact command-admission fact without creating a scene");
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
                members, java.util.Set.of(), Optional.empty());
        FrontierWorldState hot = before.prepareSceneLease(lease).transitionSceneLease(lease.id(), io.farfrontier.palemirror.frontier.v3.model.SceneLeaseStatus.HOT);

        String scene = FrontierV3DiagnosticJson.render("scene", candidate.assaultId().value(), checkpoint, hot, Optional.empty());

        assertTrue(scene.contains("\"status\":\"ok\"") && scene.contains("\"sceneKind\":\"SETTLEMENT_ASSAULT\""));
        assertTrue(scene.contains("\"operation\":\"\"") && scene.contains("\"assault\":\"" + candidate.assaultId().value() + "\""));
        assertTrue(scene.contains("\"strikeStatus\":\"NONE\"") && scene.contains("\"strikeCause\":\"\"")
                        && scene.contains("\"strikeAttacker\":\"\"") && scene.contains("\"strikeTarget\":\"\"")
                        && scene.contains("\"carrier\":\"NOT_APPLICABLE\"") == false,
                "the pure formatter preserves typed scene facts without querying a cargo carrier");
        assertTrue(scene.contains("\"strikeEpoch\":-1") && scene.contains("\"nextStrikeEpoch\":0")
                        && scene.contains("\"strikeReceiptExact\":false") && scene.contains("\"strikeHealthChanged\":false"),
                "an admitted lease without a real receipt must not be rendered as an old confirmed assault strike");
        runtime.shutdown();
    }

    @Test
    void rendersTheCurrentAssaultEpochInsteadOfAnOlderConfirmedReceipt(@TempDir Path directory) {
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = FrontierV3ServerRuntime.start(
                FrontierV3FixtureCatalog.settlementAssaultConfiguration(new WorldId("frontier:diagnostic-assault-current-epoch"), 41L),
                new FrontierFileStore(directory, FrontierWorldRuntimeDefinition.payloadCodecs()), 10_000);
        CheckpointImage checkpoint = runtime.checkpointImage().orElseThrow();
        FrontierWorldState state = runtime.decodedState().orElseThrow();
        var candidate = state.coldSettlementAssaultSceneCandidates().getFirst();
        var assault = state.strategicPlans().settlementAssaults().get(candidate.assaultId());
        var firstMembers = candidate.memberPositions().keySet().stream().sorted().map(actor -> new SceneMember(actor,
                SceneLease.deterministicEntityId(checkpoint.worldId(), actor))).toList();
        SceneLeaseId firstLeaseId = new SceneLeaseId("lease:diagnostic-assault-current-r0");
        SceneLease firstLease = SceneLease.forCause(firstLeaseId, checkpoint.worldId(),
                new io.farfrontier.palemirror.frontier.v3.model.SettlementAssaultSceneCause(candidate.assaultId(), candidate.settlementId()),
                candidate.handoffPosition(), checkpoint.instant(), checkpoint.revision().value(), SceneLeaseStatus.PREPARED, firstMembers, java.util.Set.of(), Optional.empty());
        state = state.prepareSceneLease(firstLease).transitionSceneLease(firstLeaseId, SceneLeaseStatus.HOT);
        SubjectId firstAttacker = assault.combatantAttackerIds().stream().sorted().findFirst().orElseThrow();
        SubjectId firstTarget = assault.defenderIds().stream().sorted().findFirst().orElseThrow();
        SubjectId firstCause = io.farfrontier.palemirror.frontier.v3.model.SettlementAssaultCauseIdentity.strike(assault.id(), firstAttacker, 0L);
        var firstIntent = new io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent(FrontierV3SettlementAssaultReceiptBinding.intentId(state, firstLease, firstCause),
                io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.SCENE_STRIKE, io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.PREPARED,
                firstCause, io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentRoleBinding.assaultSceneStrike(firstAttacker, firstTarget, firstLease.id(), firstLease.revision()), new io.farfrontier.palemirror.frontier.v3.api.FixedPosition(
                io.farfrontier.palemirror.frontier.v3.api.FixedScalar.ZERO, io.farfrontier.palemirror.frontier.v3.api.FixedScalar.ZERO,
                io.farfrontier.palemirror.frontier.v3.api.FixedScalar.ZERO), 0, io.farfrontier.palemirror.frontier.v3.api.PhysicalPostcondition.SCENE_STRIKE_OBSERVED,
                io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentLifecycleOwner.SETTLEMENT_ASSAULT);
        SubjectId firstOwner = io.farfrontier.palemirror.frontier.v3.model.SceneStrikeStateSupport.owner(state, firstIntent);
        state = PhysicalIntentLifecycleFixture.prepare(state, firstOwner, firstIntent);
        state = PhysicalIntentLifecycleFixture.transition(state, firstOwner, firstIntent,
                io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.RUNNING, Optional.empty());
        state = PhysicalIntentLifecycleFixture.transition(state, firstOwner, firstIntent,
                io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.CONFIRMED, Optional.of(
                        new io.farfrontier.palemirror.frontier.v3.model.SceneStrikeObservation(new io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId("observation:diagnostic-assault-old"),
                                firstIntent.id(), firstAttacker, firstTarget, io.farfrontier.palemirror.frontier.v3.api.FixedScalar.whole(20),
                                io.farfrontier.palemirror.frontier.v3.api.FixedScalar.whole(18))));
        FrontierWorldState draining = state.transitionSceneLease(firstLeaseId, SceneLeaseStatus.DRAINING);
        state = draining.releaseSceneLease(firstLeaseId, firstMembers.stream()
                .map(member -> new io.farfrontier.palemirror.frontier.v3.model.SceneMemberPosition(member.actorId(), draining.actorLocations().get(member.actorId()).body(),
                        draining.actorLocations().get(member.actorId()).condition().health())).toList());
        var secondCandidate = state.coldSettlementAssaultSceneCandidates().getFirst();
        var secondMembers = secondCandidate.memberPositions().keySet().stream().sorted().map(actor -> new SceneMember(actor,
                SceneLease.deterministicEntityId(checkpoint.worldId(), actor))).toList();
        SceneLeaseId secondLeaseId = new SceneLeaseId("lease:diagnostic-assault-current-r1");
        SceneLease secondLease = SceneLease.forCause(secondLeaseId, checkpoint.worldId(),
                new io.farfrontier.palemirror.frontier.v3.model.SettlementAssaultSceneCause(secondCandidate.assaultId(), secondCandidate.settlementId()),
                secondCandidate.handoffPosition(), checkpoint.instant(), checkpoint.revision().value() + 1L, SceneLeaseStatus.PREPARED, secondMembers, java.util.Set.of(), Optional.empty());
        state = state.prepareSceneLease(secondLease).transitionSceneLease(secondLeaseId, SceneLeaseStatus.HOT);
        SubjectId secondAttacker = assault.defenderIds().stream().sorted().skip(1L % assault.defenderIds().size()).findFirst().orElseThrow();
        SubjectId secondTarget = assault.combatantAttackerIds().stream().sorted().skip(1L % assault.combatantAttackerIds().size()).findFirst().orElseThrow();
        SubjectId secondCause = io.farfrontier.palemirror.frontier.v3.model.SettlementAssaultCauseIdentity.strike(assault.id(), secondAttacker, 1L);
        var secondIntent = new io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent(FrontierV3SettlementAssaultReceiptBinding.intentId(state, secondLease, secondCause),
                io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.SCENE_STRIKE, io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.PREPARED,
                secondCause, io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentRoleBinding.assaultSceneStrike(secondAttacker, secondTarget, secondLease.id(), secondLease.revision()), new io.farfrontier.palemirror.frontier.v3.api.FixedPosition(
                io.farfrontier.palemirror.frontier.v3.api.FixedScalar.ZERO, io.farfrontier.palemirror.frontier.v3.api.FixedScalar.ZERO,
                io.farfrontier.palemirror.frontier.v3.api.FixedScalar.ZERO), 0, io.farfrontier.palemirror.frontier.v3.api.PhysicalPostcondition.SCENE_STRIKE_OBSERVED,
                io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentLifecycleOwner.SETTLEMENT_ASSAULT);
        state = state.preparePhysicalIntent(secondIntent);

        String scene = FrontierV3DiagnosticJson.render("scene", assault.id().value(), checkpoint, state, Optional.empty());

        assertTrue(scene.contains("\"strikeEpoch\":1") && scene.contains("\"strikeStatus\":\"PREPARED\"")
                        && scene.contains("\"strikeCause\":\"" + secondCause.value() + "\"")
                        && scene.contains("\"strikeIntent\":\"" + secondIntent.id().value() + "\"")
                        && scene.contains("\"strikeReceipt\":\"\"")
                        && scene.contains("\"strikeReceiptExact\":false"),
                "a newer in-flight assault epoch must retain its own exact intent and empty receipt slot rather than borrowing an older receipt");
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
                    sourceState.actorLocations().get(member).condition(), sourceState.actorLocations().get(member).kind()));
        });
        assembly = new io.farfrontier.palemirror.frontier.v3.model.EngineeringWorkAssembly(
                io.farfrontier.palemirror.frontier.v3.model.EngineeringJourneyPurpose.WORKSITE, completedMembers);
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
                members, java.util.Set.of(), Optional.empty());
        FrontierWorldState diagnosticState = state.withChanges(io.farfrontier.palemirror.frontier.v3.model.FrontierWorldStateUpdate.begin()
                .sceneLeases(java.util.Map.of(lease.id(), lease)));

        String scene = FrontierV3DiagnosticJson.render("scene", project.id().value(), checkpoint, diagnosticState, Optional.empty());

        assertTrue(scene.contains("\"status\":\"ok\"") && scene.contains("\"sceneKind\":\"ENGINEERING_WORKSITE\""));
        assertTrue(scene.contains("\"project\":\"" + project.id().value() + "\"")
                && scene.contains("\"operation\":\"\"") && scene.contains("\"assault\":\"\""));
        runtime.shutdown();
    }

}
