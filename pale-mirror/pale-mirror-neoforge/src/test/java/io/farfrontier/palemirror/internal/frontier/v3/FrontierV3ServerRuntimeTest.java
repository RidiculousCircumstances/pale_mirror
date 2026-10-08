package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.CauseChain;
import io.farfrontier.palemirror.frontier.v3.api.CheckpointImage;
import io.farfrontier.palemirror.frontier.v3.api.CommandId;
import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.FrontierEvent;
import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.FrontierProjection;
import io.farfrontier.palemirror.frontier.v3.api.FixedPosition;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind;
import io.farfrontier.palemirror.frontier.v3.api.ProjectionQuery;
import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.RejectionCode;
import io.farfrontier.palemirror.frontier.v3.api.Revision;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.CommandPlan;
import io.farfrontier.palemirror.frontier.v3.kernel.EngineLimits;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration;
import io.farfrontier.palemirror.frontier.v3.kernel.PayloadCodec;
import io.farfrontier.palemirror.frontier.v3.kernel.PayloadCodecs;
import io.farfrontier.palemirror.frontier.v3.kernel.StateCodec;
import io.farfrontier.palemirror.frontier.v3.kernel.TransactionCommitter;
import io.farfrontier.palemirror.frontier.v3.kernel.WorkBudget;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierStore;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.ActorBodyAuthority;
import io.farfrontier.palemirror.frontier.v3.model.ModeledActorBodyFacts;
import io.farfrontier.palemirror.frontier.v3.model.BodyPosition;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyPresent;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyInspected;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyUnloaded;
import io.farfrontier.palemirror.frontier.v3.model.FrontierV3FixtureCatalog;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.model.FrontierSceneAdmission;
import io.farfrontier.palemirror.frontier.v3.model.AmbientActorLease;
import io.farfrontier.palemirror.frontier.v3.process.AmbientActorProcess;
import io.farfrontier.palemirror.frontier.v3.model.AmbientLeasePrepared;
import io.farfrontier.palemirror.frontier.v3.model.AmbientLeaseStatus;
import io.farfrontier.palemirror.frontier.v3.model.AmbientLeaseTransition;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalIntentTransition;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalPostcondition;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalIntentPrepared;
import io.farfrontier.palemirror.frontier.v3.model.SceneLease;
import io.farfrontier.palemirror.frontier.v3.model.SceneLeaseHandoff;
import io.farfrontier.palemirror.frontier.v3.model.SceneLeaseReleased;
import io.farfrontier.palemirror.frontier.v3.model.SceneLeaseStatus;
import io.farfrontier.palemirror.frontier.v3.model.SceneMemberPosition;
import io.farfrontier.palemirror.frontier.v3.model.SceneLeaseTransition;
import io.farfrontier.palemirror.frontier.v3.model.SceneLeaseRecoveryUnresolved;
import io.farfrontier.palemirror.frontier.v3.model.CustodyAccount;
import io.farfrontier.palemirror.frontier.v3.model.FungiblePhysicalHandoff;
import io.farfrontier.palemirror.frontier.v3.model.FungiblePhysicalObservation;
import io.farfrontier.palemirror.frontier.v3.model.FungibleResourceHandoffObserved;
import io.farfrontier.palemirror.frontier.v3.model.FungibleResourceLedger;
import io.farfrontier.palemirror.frontier.v3.model.InventoryCustody;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalStackAddress;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalStackBinding;
import io.farfrontier.palemirror.frontier.v3.model.ResourceCustody;
import io.farfrontier.palemirror.frontier.v3.model.ResourceLot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrontierV3ServerRuntimeTest {
    private static final WorldId WORLD = new WorldId("frontier:runtime");
    private static final SubjectId SUBJECT = new SubjectId("settlement:runtime");

    @Test
    void boundedColdIntervalsMatchOrdinaryTicksAndRecoverTheSameWorld(@TempDir Path directory) {
        WorldId world = new WorldId("frontier:cold-interval-equivalence");
        var definition = FrontierWorldRuntimeDefinition.configuration(world, 91L);
        var ordinary = FrontierV3ServerRuntime.start(definition,
                new FrontierFileStore(directory.resolve("ordinary"), FrontierWorldRuntimeDefinition.payloadCodecs()), 37);
        var store = new FrontierFileStore(directory.resolve("interval"), FrontierWorldRuntimeDefinition.payloadCodecs());
        var interval = FrontierV3ServerRuntime.start(definition, store, 37);
        var budget = new WorkBudget(4, 64);
        for (int tick = 0; tick < 120; tick++) ordinary.tick(budget).orElseThrow();
        while (interval.canonicalState().orElseThrow().instant().ticks() < 120) {
            int remaining = Math.toIntExact(120 - interval.canonicalState().orElseThrow().instant().ticks());
            interval.advanceColdInterval(remaining, budget).orElseThrow();
        }
        assertEquals(ordinary.checkpointImage().orElseThrow(), interval.checkpointImage().orElseThrow(),
                "skipping only empty intervals must preserve exact events, due/backlog order and current state");
        var expected = interval.checkpointImage().orElseThrow();
        ordinary.shutdown(); interval.shutdown();
        var recovered = FrontierV3ServerRuntime.start(definition, store, 37);
        assertEquals(expected, recovered.checkpointImage().orElseThrow());
        recovered.shutdown();
    }

    @Test
    void rejectedBakeryPhysicalCommandCanBeClassifiedWithoutQuarantiningRuntime(@TempDir Path directory) {
        WorldId world = new WorldId("frontier:rejected-bakery-observation-local");
        var runtime = FrontierV3ServerRuntime.start(FrontierWorldRuntimeDefinition.configuration(world, 91L),
                new FrontierFileStore(directory, FrontierWorldRuntimeDefinition.payloadCodecs()), 200);
        var foreign = new io.farfrontier.palemirror.frontier.v3.model.BakeryHotEffectPrepared(
                new SubjectId("job:foreign-bakery"), new SceneLeaseId("lease:foreign-bakery"),
                io.farfrontier.palemirror.frontier.v3.model.BakeryWorkState.Phase.DEPOT_DELIVERY, 0);
        assertInstanceOf(CommandResult.Rejected.class, FrontierV3CommandSubmission.submitResult(
                runtime, "bakery-effect-prepare", "lease:foreign-bakery", foreign));
        assertEquals(FrontierV3RuntimeStatus.Kind.ACTIVE, runtime.status().kind());
        assertEquals(0L, runtime.checkpointImage().orElseThrow().revision().value());
    }

    @Test
    void failedRecoverySelectionRemainsAVisibleQuarantinedV3Runtime(@TempDir Path directory) {
        var cause = new IllegalArgumentException("recovery header does not match selected physical world");
        FrontierV3ServerRuntime<Counter, CounterProjection> runtime = FrontierV3ServerRuntime.failedStart(
                configuration(), new FrontierFileStore(directory, codecs()), 20,
                cause);

        assertEquals(FrontierV3RuntimeStatus.Kind.QUARANTINED, runtime.status().kind());
        assertTrue(runtime.status().detail().orElseThrow().contains("recovery header"));
        assertTrue(runtime.checkpointImage().isEmpty());
        assertTrue(runtime.submit(command("command:must-not-run", Revision.ZERO, SimInstant.ZERO, 1)).isEmpty());
        var failure = assertThrows(IllegalStateException.class,
                () -> FrontierV3StartupExposure.requireActive(runtime.status(), cause));
        assertSame(cause, failure.getCause(), "failed startup must stop the host with the original recovery trace");
        assertTrue(failure.getMessage().contains("refusing world exposure"));
        FrontierV3StartupExposure.requireActive(FrontierV3RuntimeStatus.active(), null);
    }

    @Test
    void explosionObservationCommandsNormalizeNestedIdsIntoOneStrictCommandPath() {
        long packedPosition = -1L;

        CommandId external = FrontierV3CommandIds.externalExplosionObservation("effect:external-explosion:0", packedPosition);
        CommandId managed = FrontierV3CommandIds.managedExplosionObservation(
                new PhysicalIntentId("intent:managed-explosion-command-id-test"), packedPosition);

        assertEquals("executor:external-explosion-effect-external-explosion-0-p" + Long.toUnsignedString(packedPosition), external.value());
        assertEquals("executor:managed-explosion-intent-managed-explosion-command-id-test-p" + Long.toUnsignedString(packedPosition), managed.value());
        assertThrows(IllegalArgumentException.class, () -> new CommandId("executor:effect:external-explosion:0:" + Long.toUnsignedString(packedPosition)));
    }

    @Test
    void resourceSiteConflictRuntimeRestartRetainsTheFirstCanonicalIncident(@TempDir Path directory) {
        WorldId world = new WorldId("frontier:resource-site-conflict-runtime-restart");
        FrontierStore store = new FrontierFileStore(directory, FrontierWorldRuntimeDefinition.payloadCodecs());
        var configuration = FrontierWorldRuntimeDefinition.configuration(world, 91L);
        var firstHostSession = new io.farfrontier.palemirror.frontier.v3.model.DiagnosticRuntimeIdentity(
                "neoforge", "tree:runtime-carrier", "sha256:runtime-carrier", "server-session:first");
        FrontierV3ServerRuntime<FrontierWorldState, io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection> runtime =
                FrontierV3ServerRuntime.start(configuration, store, 10_000, firstHostSession);
        FrontierWorldState initial = worldState(runtime);
        SubjectId site = new SubjectId("site:1-wheat-field");
        BlockPosition crop = io.farfrontier.palemirror.frontier.v3.model.FrontierResourceSitePlan.compile(initial.bootstrap())
                .get(site).cropSlots().getFirst();
        submitWorld(runtime, "resource-site-conflict-first", new io.farfrontier.palemirror.frontier.v3.model.ResourceSiteConflictObserved(site, crop,
                io.farfrontier.palemirror.frontier.v3.model.ResourceSiteDiagnosticProducer.PLAYER_REMOVED));
        var first = worldState(runtime).resourceSites().site(site).conflictDisposition().orElseThrow().incident();
        assertEquals("incident:resource-site:1-wheat-field", first.id());
        assertEquals("PLAYER_WORLD_OBSERVATION", first.source());
        var firstContext = worldState(runtime).diagnosticIncidents().why(first.diagnostic().subject()).orElseThrow().context();
        assertTrue(firstContext.complete(), "the server-runtime reducer boundary carries supplied host provenance");
        assertEquals(firstHostSession.sourceTree(), firstContext.sourceTree());
        assertEquals(firstHostSession.jar(), firstContext.jar());
        assertEquals(firstHostSession.restartIdentity(), firstContext.restartIdentity());
        runtime.shutdown();

        var recoveredHostSession = new io.farfrontier.palemirror.frontier.v3.model.DiagnosticRuntimeIdentity(
                "neoforge", "tree:runtime-carrier", "sha256:runtime-carrier", "server-session:recovered");
        FrontierV3ServerRuntime<FrontierWorldState, io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection> recovered =
                FrontierV3ServerRuntime.start(configuration, store, 10_000, recoveredHostSession);
        assertEquals(first, worldState(recovered).resourceSites().site(site).conflictDisposition().orElseThrow().incident(),
                "the runtime WAL recovery retains the first accepted canonical source and trace join");
        assertEquals(firstContext, worldState(recovered).diagnosticIncidents().why(first.diagnostic().subject()).orElseThrow().context(),
                "persisted bundle provenance remains the original factual server session after restart");
        recovered.shutdown();
    }


    @Test
    void freshLifecycleWritesAheadTicksPersistsAndRecoversWithoutWorldTimeInput(@TempDir Path directory) {
        FrontierStore store = new FrontierFileStore(directory, codecs());
        FrontierV3ServerRuntime<Counter, CounterProjection> runtime = FrontierV3ServerRuntime.start(configuration(), store, 2);
        assertEquals(FrontierV3RuntimeStatus.Kind.ACTIVE, runtime.status().kind());

        FrontierCommand command = command("command:increment", Revision.ZERO, SimInstant.ZERO, 5);
        CommandResult result = runtime.submit(command).orElseThrow();
        assertInstanceOf(CommandResult.Accepted.class, result, result::toString);
        runtime.tick(new WorkBudget(4, 8));
        runtime.tick(new WorkBudget(4, 8));
        runtime.tick(new WorkBudget(4, 8));
        runtime.shutdown();

        assertEquals(FrontierV3RuntimeStatus.Kind.STOPPED, runtime.status().kind());
        FrontierV3ServerRuntime<Counter, CounterProjection> recovered = FrontierV3ServerRuntime.start(configuration(), store, 2);
        CounterProjection projection = recovered.projection(ProjectionQuery.summary()).orElseThrow();
        assertEquals(5, projection.value());
        assertEquals(new Revision(1L), projection.revision());
        assertEquals(new SimInstant(3L), projection.instant());
        CommandResult duplicate = recovered.submit(command("command:increment", new Revision(1L), new SimInstant(3L), 5)).orElseThrow();
        assertEquals(RejectionCode.DUPLICATE_COMMAND, assertInstanceOf(CommandResult.Rejected.class, duplicate).rejection().code());
    }

    @Test
    void boundedAdvanceUsesTheSameWalBackedTickEngineAndRecovers(@TempDir Path directory) {
        FrontierStore store = new FrontierFileStore(directory, codecs());
        FrontierV3PerformanceMetrics metrics = new FrontierV3PerformanceMetrics();
        FrontierV3ServerRuntime<Counter, CounterProjection> runtime = FrontierV3ServerRuntime.start(configuration().withExecutionMetrics(metrics), store, 2);
        assertInstanceOf(CommandResult.Accepted.class,
                runtime.submit(command("command:advance", Revision.ZERO, SimInstant.ZERO, 7)).orElseThrow());

        assertEquals(new SimInstant(3L), runtime.advance(3, new WorkBudget(4, 8)).orElseThrow().instant());
        assertEquals(new Revision(1L), runtime.checkpointImage().orElseThrow().revision());
        assertTrue(metrics.snapshot().stages().stream().anyMatch(sample -> sample.stage() == io.farfrontier.palemirror.frontier.v3.kernel.FrontierExecutionMetrics.Stage.TRANSACTION
                && sample.kind().equals("runtime.checkpoint") && sample.owner().equals(WORLD.value()) && sample.samples() == 1L),
                "periodic WAL/snapshot/compaction work must remain attributable outside the canonical planner spans");
        assertTrue(metrics.snapshot().stages().stream().anyMatch(sample -> sample.stage() == io.farfrontier.palemirror.frontier.v3.kernel.FrontierExecutionMetrics.Stage.PERSISTENCE
                && sample.kind().equals("write-ahead") && sample.owner().equals(WORLD.value()) && sample.samples() >= 1L),
                "each accepted canonical transaction must expose its write-ahead boundary separately from planning and reduction");
        runtime.shutdown();

        FrontierV3ServerRuntime<Counter, CounterProjection> recovered = FrontierV3ServerRuntime.start(configuration(), store, 2);
        assertEquals(new SimInstant(3L), recovered.checkpointImage().orElseThrow().instant());
        assertEquals(7, recovered.projection(ProjectionQuery.summary()).orElseThrow().value());
    }

    @Test
    void partialFungibleHandoffRetainsItsOldHotSourceBeforeCommitAndExactlyReplaysAfterCommit(@TempDir Path directory) {
        WorldId world = new WorldId("frontier:fungible-partial-recovery");
        FrontierEngineConfiguration<FrontierWorldState, ?> base = FrontierWorldRuntimeDefinition.configuration(world, 91L);
        SubjectId settlement = new SubjectId("settlement:1"), depot = FrontierWorldState.depotId(settlement);
        SubjectId sourceId = new SubjectId("custody:partial-recovery"), lotId = new SubjectId("lot:partial-recovery");
        FungibleResourceLedger cold = FungibleResourceLedger.empty().issue(new ResourceLot(lotId, settlement, "minecraft:wheat", 64,
                "test", List.of()), new CustodyAccount(sourceId, new ResourceCustody.Container(depot), Map.of(lotId, 64), Map.of()));
        List<FungiblePhysicalObservation.Stack> layout = List.of(new FungiblePhysicalObservation.Stack(
                new PhysicalStackAddress.ContainerSlot(new InventoryCustody.ContainerSlot(depot, 0)), "minecraft:wheat", 64));
        FungibleResourceLedger hot = cold.rebind(sourceId, 3L, FungiblePhysicalObservation.bind(cold, sourceId, 3L, layout));
        FrontierWorldState initial = base.initialState().withInventory(base.initialState().inventory().withFungibleResources(hot));
        FrontierEngineConfiguration<FrontierWorldState, ?> configuration = new FrontierEngineConfiguration<>(base.worldId(), initial,
                base.initialInstant(), base.commandPlanner(), base.scheduledPlanner(), base.reducer(), base.stateCodec(), base.projectionMapper(),
                base.limits(), base.initialSchedules(), base.transactionCommitter(), base.stateValidator(), base.executionMetrics());
        FrontierFileStore store = new FrontierFileStore(directory, FrontierWorldRuntimeDefinition.payloadCodecs());

        FrontierV3ServerRuntime<FrontierWorldState, ?> beforeCommit = FrontierV3ServerRuntime.start(configuration, store, 10_000);
        beforeCommit.shutdown();
        FrontierV3ServerRuntime<FrontierWorldState, ?> preserved = FrontierV3ServerRuntime.start(configuration, store, 10_000);
        assertEquals(Map.of(lotId, 64), worldState(preserved).inventory().fungibleResources().accounts().get(sourceId).lotQuantities(),
                "a physical save before canonical confirmation keeps the old fenced source; it does not invent a player balance");
        preserved.shutdown();

        FrontierV3ServerRuntime<FrontierWorldState, ?> committed = FrontierV3ServerRuntime.start(configuration, store, 10_000);
        PhysicalStackBinding source = committed.decodedState().orElseThrow().inventory().fungibleResources().bindings().values().iterator().next();
        java.util.UUID player = java.util.UUID.fromString("00000000-0000-0000-0000-000000000155");
        FungibleResourceHandoffObserved handoff = FungiblePhysicalHandoff.departToNew(committed.decodedState().orElseThrow().inventory()
                        .fungibleResources(), sourceId, 3L, source, 32, new SubjectId("custody:player-partial-recovery"),
                new ResourceCustody.Player(player), 1L, new PhysicalStackAddress.PlayerSlot(player, 0));
        submitWorld(committed, "fungible-partial-recovery", handoff);
        FrontierWorldState afterCommit = worldState(committed);
        committed.shutdown();

        FrontierV3ServerRuntime<FrontierWorldState, ?> recovered = FrontierV3ServerRuntime.start(
                FrontierWorldRuntimeDefinition.configuration(world, 91L), store, 10_000);
        assertEquals(afterCommit, worldState(recovered));
        assertEquals(64, worldState(recovered).inventory().fungibleResources().totalQuantity(settlement, "minecraft:wheat"));
        CheckpointImage checkpoint = recovered.checkpointImage().orElseThrow(); CommandId commandId = new CommandId("test:fungible-partial-recovery");
        FrontierCommand duplicate = new FrontierCommand(1, commandId, checkpoint.worldId(), checkpoint.revision(), checkpoint.instant(),
                FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(commandId), handoff);
        assertEquals(RejectionCode.DUPLICATE_COMMAND, assertInstanceOf(CommandResult.Rejected.class,
                recovered.submit(duplicate).orElseThrow()).rejection().code());
        recovered.shutdown();
    }

    @Test
    void installedSnapshotsReleaseCoveredEngineHistoryBeforeTheBoundedTransactionCap(@TempDir Path directory) {
        FrontierEngineConfiguration<Counter, CounterProjection> base = configuration();
        FrontierEngineConfiguration<Counter, CounterProjection> bounded = new FrontierEngineConfiguration<>(base.worldId(), base.initialState(), base.initialInstant(),
                base.commandPlanner(), base.scheduledPlanner(), base.reducer(), base.stateCodec(), base.projectionMapper(), new EngineLimits(128, 100L, 2),
                base.initialSchedules(), base.transactionCommitter());
        FrontierV3ServerRuntime<Counter, CounterProjection> runtime = FrontierV3ServerRuntime.start(bounded, new FrontierFileStore(directory, codecs()), 1);
        for (int value = 0; value < 12; value++) {
            CheckpointImage checkpoint = runtime.checkpointImage().orElseThrow();
            assertInstanceOf(CommandResult.Accepted.class, runtime.submit(command("command:compact-" + value, checkpoint.revision(), checkpoint.instant(), 1)).orElseThrow());
            runtime.tick(new WorkBudget(4, 8));
        }
        assertEquals(FrontierV3RuntimeStatus.Kind.ACTIVE, runtime.status().kind());
        assertEquals(12, runtime.projection(ProjectionQuery.summary()).orElseThrow().value());
        assertEquals(0, new FrontierFileStore(directory, codecs()).recover(WORLD).walTail().size());
    }

    /**
     * Mirrors the operator's maximum COLD interval at the actual runtime checkpoint cadence.
     * The bare kernel is intentionally allowed to exhaust its bounded transaction history when
     * no persistence owner compacts it; the server runtime must never take that non-production
     * path while advancing an admitted operator request.
     */
    @Test
    void maximumColdOperatorIntervalCompactsBeforeTransactionRetentionCanQuarantine(@TempDir Path directory) {
        WorldId world = new WorldId("frontier:runtime-maximum-cold-interval");
        FrontierV3ServerRuntime<FrontierWorldState, io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection> runtime =
                FrontierV3ServerRuntime.start(FrontierWorldRuntimeDefinition.configuration(world, 47L),
                        new FrontierFileStore(directory, FrontierWorldRuntimeDefinition.payloadCodecs()), 200);
        WorkBudget budget = FrontierV3RuntimeBudgets.fastForwardTick();

        for (int tick = 0; tick < FrontierV3ServerLifecycle.MAX_FAST_FORWARD_TICKS; tick++) {
            runtime.advance(1, budget).orElseThrow();
            assertEquals(FrontierV3RuntimeStatus.Kind.ACTIVE, runtime.status().kind(), "cold interval quarantined at tick " + (tick + 1));
        }

        assertEquals(new SimInstant(FrontierV3ServerLifecycle.MAX_FAST_FORWARD_TICKS), runtime.checkpointImage().orElseThrow().instant());
        assertEquals(FrontierV3RuntimeStatus.Kind.ACTIVE, runtime.status().kind());
    }

    @Test
    void quarantineRetainsPassiveOwnershipWithoutReopeningExecution(@TempDir Path directory) {
        var runtime = FrontierV3ServerRuntime.start(configuration(), new FrontierFileStore(directory, codecs()), 20);
        assertInstanceOf(CommandResult.Accepted.class,
                runtime.submit(command("command:before-quarantine", Revision.ZERO, SimInstant.ZERO, 3)).orElseThrow());
        Counter retained = runtime.decodedState().orElseThrow();
        runtime.quarantine(new IllegalStateException("test physical executor failure"));
        assertTrue(runtime.decodedState().isEmpty());
        assertTrue(runtime.canonicalState().isEmpty());
        assertSame(retained, runtime.passiveOwnershipState().orElseThrow());
        assertTrue(runtime.submit(command("command:after-quarantine", new Revision(1), SimInstant.ZERO, 7)).isEmpty());
        assertSame(retained, runtime.passiveOwnershipState().orElseThrow());
        assertEquals(FrontierV3RuntimeStatus.Kind.QUARANTINED, runtime.status().kind());
    }

    @Test
    void decodedStateIsSharedUntilACommittedRevisionChangesIt(@TempDir Path directory) {
        FrontierV3ServerRuntime<Counter, CounterProjection> runtime = FrontierV3ServerRuntime.start(configuration(), new FrontierFileStore(directory, codecs()), 20);
        Counter first = runtime.decodedState().orElseThrow();
        assertSame(first, runtime.decodedState().orElseThrow());

        assertInstanceOf(CommandResult.Accepted.class, runtime.submit(command("command:decoded-state-cache", Revision.ZERO, SimInstant.ZERO, 3)).orElseThrow());

        Counter changed = runtime.decodedState().orElseThrow();
        assertNotSame(first, changed);
        assertEquals(3, changed.value());
        assertSame(changed, runtime.decodedState().orElseThrow());
    }

    @Test
    void physicalCanonicalReadsDoNotEncodeOrDecodePersistenceSnapshots(@TempDir Path directory) {
        CountingCounterCodec codec = new CountingCounterCodec();
        FrontierV3ServerRuntime<Counter, CounterProjection> runtime = FrontierV3ServerRuntime.start(
                configuration(codec), new FrontierFileStore(directory, codecs()), 20);
        codec.reset();

        Counter first = runtime.decodedState().orElseThrow();
        assertSame(first, runtime.canonicalState().orElseThrow().state());
        assertEquals(0, codec.encodeCalls);
        assertEquals(0, codec.decodeCalls);

        assertInstanceOf(CommandResult.Accepted.class,
                runtime.submit(command("command:direct-canonical-read", Revision.ZERO, SimInstant.ZERO, 3)).orElseThrow());
        Counter changed = runtime.decodedState().orElseThrow();
        assertEquals(3, changed.value());
        assertEquals(0, codec.encodeCalls, "ordinary physical reads and commands must not create a full snapshot");
        assertEquals(0, codec.decodeCalls, "ordinary physical reads must not rehydrate a full snapshot");

        runtime.checkpointImage().orElseThrow();
        assertEquals(1, codec.encodeCalls, "only the explicit checkpoint boundary serializes the changed world");
        assertEquals(0, codec.decodeCalls);
    }

    @Test
    void checkpointImageIsSharedForReadOnlyAdaptersAndInvalidatedAfterCanonicalMutation(@TempDir Path directory) {
        FrontierV3ServerRuntime<Counter, CounterProjection> runtime = FrontierV3ServerRuntime.start(configuration(), new FrontierFileStore(directory, codecs()), 20);
        CheckpointImage first = runtime.checkpointImage().orElseThrow();
        assertSame(first, runtime.checkpointImage().orElseThrow(), "one server tick must not clone a canonical checkpoint per physical read");

        assertInstanceOf(CommandResult.Accepted.class,
                runtime.submit(command("command:checkpoint-cache", first.revision(), first.instant(), 3)).orElseThrow());

        CheckpointImage changed = runtime.checkpointImage().orElseThrow();
        assertNotSame(first, changed, "a successful canonical command must invalidate the read-only image");
        assertEquals(new Revision(1L), changed.revision());
        assertSame(changed, runtime.checkpointImage().orElseThrow());
    }

    @Test
    void corruptDurableHistoryQuarantinesStartupInsteadOfCreatingAReplacementWorld(@TempDir Path directory) throws Exception {
        FrontierStore store = new FrontierFileStore(directory, codecs());
        FrontierV3ServerRuntime<Counter, CounterProjection> runtime = FrontierV3ServerRuntime.start(configuration(), store, 20);
        assertInstanceOf(CommandResult.Accepted.class,
                runtime.submit(command("command:corrupt", Revision.ZERO, SimInstant.ZERO, 1)).orElseThrow());
        Path wal = directory.resolve("frontier-v3/frontier_runtime/wal-segment-00000000000000000001.bin");
        byte[] bytes = Files.readAllBytes(wal);
        bytes[bytes.length - 1] ^= 1;
        Files.write(wal, bytes);

        FrontierV3ServerRuntime<Counter, CounterProjection> failed = FrontierV3ServerRuntime.start(configuration(), store, 20);

        assertEquals(FrontierV3RuntimeStatus.Kind.QUARANTINED, failed.status().kind());
        assertTrue(failed.projection(ProjectionQuery.summary()).isEmpty());
        assertTrue(failed.passiveOwnershipState().isEmpty(),
                "failed recovery must not fabricate field ownership from initial state");
    }


    @Test
    void deferredColdAftermathRecoversItsRunningBoundaryWithoutMintingASecondCanonicalLoss(@TempDir Path directory) {
        WorldId world = new WorldId("frontier:deferred-aftermath-restart");
        FrontierStore store = new FrontierFileStore(directory, FrontierWorldRuntimeDefinition.payloadCodecs());
        var base = FrontierWorldRuntimeDefinition.configuration(world, 91L);
        FrontierWorldState initial = base.initialState();
        io.farfrontier.palemirror.frontier.v3.model.Settlement settlement = initial.bootstrap().settlements().getFirst();
        io.farfrontier.palemirror.frontier.v3.model.SettlementStructure hall = settlement.structures().stream()
                .filter(value -> value.kind() == io.farfrontier.palemirror.frontier.v3.model.StructureKind.HALL).findFirst().orElseThrow();
        io.farfrontier.palemirror.frontier.v3.model.GrayboxCell cell = io.farfrontier.palemirror.frontier.v3.model.FrontierGrayboxPlan
                .intactStructureCell(initial.bootstrap().terrain(), hall, hall.anchor());
        io.farfrontier.palemirror.frontier.v3.api.SubjectId aftermathId = new io.farfrontier.palemirror.frontier.v3.api.SubjectId("aftermath:runtime-recovery");
        io.farfrontier.palemirror.frontier.v3.model.DeferredAftermath aftermath = new io.farfrontier.palemirror.frontier.v3.model.DeferredAftermath(
                aftermathId, initial.bootstrap().hive().id(), new SubjectId("bioform:east-2"), 0L, java.util.OptionalLong.empty(),
                "test:runtime-recovery", io.farfrontier.palemirror.frontier.v3.model.DeferredAftermathKnowledge.KNOWN_CLEAR, 0L,
                List.of(new io.farfrontier.palemirror.frontier.v3.model.DeferredAftermathCell(hall.anchor(), cell.semanticTarget(), cell.material(), cell.semanticPart(),
                        io.farfrontier.palemirror.frontier.v3.model.DeferredAftermathCellStatus.PENDING)), 0);
        var configuration = new io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration<>(base.worldId(),
                initial.withChanges(io.farfrontier.palemirror.frontier.v3.model.FrontierWorldStateUpdate.begin()
                        .deferredAftermath(initial.deferredAftermath().prepare(aftermath))), base.initialInstant(), base.commandPlanner(),
                base.scheduledPlanner(), base.reducer(), base.stateCodec(), base.projectionMapper(), base.limits(), base.initialSchedules(),
                base.transactionCommitter(), base.stateValidator(), base.executionMetrics());
        FrontierV3ServerRuntime<FrontierWorldState, io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection> runtime =
                FrontierV3ServerRuntime.start(configuration, store, 10_000);
        submitWorld(runtime, "deferred-aftermath-running", new io.farfrontier.palemirror.frontier.v3.model.DeferredAftermathResolved(aftermathId, 0L, 1L, 0,
                io.farfrontier.palemirror.frontier.v3.model.DeferredAftermathCellStatus.RUNNING));
        runtime.shutdown();

        FrontierV3ServerRuntime<FrontierWorldState, io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection> recovered =
                FrontierV3ServerRuntime.start(configuration, store, 10_000);
        assertEquals(io.farfrontier.palemirror.frontier.v3.model.DeferredAftermathCellStatus.RUNNING,
                worldState(recovered).deferredAftermath().entries().get(aftermathId).nextPending().status(),
                "recovery retains the durable before-effect boundary rather than replaying the COLD cause");
        submitWorld(recovered, "deferred-aftermath-realized", new io.farfrontier.palemirror.frontier.v3.model.DeferredAftermathResolved(aftermathId, 0L, 2L, 0,
                io.farfrontier.palemirror.frontier.v3.model.DeferredAftermathCellStatus.REALIZED));
        FrontierWorldState realized = worldState(recovered);
        assertEquals(io.farfrontier.palemirror.frontier.v3.model.DeferredAftermathCellStatus.REALIZED,
                realized.deferredAftermath().entries().get(aftermathId).cells().getFirst().status());
        assertFalse(realized.physicalDeltas().containsKey(hall.anchor()),
                "a recovered physical receipt cannot mint a second semantic consequence without its exact causal preparation");
        recovered.shutdown();
    }

    @Test
    void restartSafetyLeavesRunningHarvestForItsLoadedFieldAndDepotInspector() {
        PhysicalIntent harvest = new PhysicalIntent(new PhysicalIntentId("intent:restart-harvest"), PhysicalIntentKind.RESOURCE_SITE_HARVEST,
                PhysicalIntentStatus.RUNNING, new SubjectId("site:1-wheat-field"),
                io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentRoleBinding.siteHarvest(new SubjectId("site:1-wheat-field"), new SubjectId("job:site-harvest-1-wheat-field-1"),
                        new SubjectId("resident:1-1"), new SubjectId("custody:field-actor-site-harvest-1-wheat-field-1"),
                        new SubjectId("custody:container-1-depot")),
                new FixedPosition(FixedScalar.ZERO, FixedScalar.ZERO, FixedScalar.ZERO), 0,
                PhysicalPostcondition.RESOURCE_SITE_HARVESTED_OBSERVED,
                io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentLifecycleOwner.RESOURCE_SITE_HARVEST);
        PhysicalIntent unsupported = new PhysicalIntent(new PhysicalIntentId("intent:restart-unsupported"), PhysicalIntentKind.SCENE_STRIKE,
                PhysicalIntentStatus.RUNNING, new SubjectId("assault:restart-unsupported"),
                io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentRoleBinding.assaultSceneStrike(new SubjectId("bioform:west-1"), new SubjectId("resident:1-1"), new
                        io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId("lease:runtime-test"), 0L),
                new FixedPosition(FixedScalar.ZERO, FixedScalar.ZERO, FixedScalar.ZERO), 0, PhysicalPostcondition.SCENE_STRIKE_OBSERVED,
                io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentLifecycleOwner.SETTLEMENT_ASSAULT);

        assertTrue(FrontierV3PhysicalIntentRestartSafety.hasLoadedPostconditionInspector(harvest, ignored -> false),
                "the harvest executor verifies all owned crop cells and the exact depot stack without replay");
        assertFalse(FrontierV3PhysicalIntentRestartSafety.hasLoadedPostconditionInspector(unsupported, ignored -> false),
                "an effect without an exact loaded-world inspector must still fail closed as UNKNOWN");
    }

    @Test
    void filesystemRestartRetainsExactProductionWorkerProgressUntilLoadedRecovery(@TempDir Path directory) {
        WorldId world = new WorldId("frontier:production-work-restart");
        FrontierStore store = new FrontierFileStore(directory, FrontierWorldRuntimeDefinition.payloadCodecs());
        var configuration = FrontierV3FixtureCatalog.productionWorkConfiguration(world, 41L);
        FrontierV3ServerRuntime<FrontierWorldState, io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection> runtime =
                FrontierV3ServerRuntime.start(configuration, store, 10_000);
        FrontierWorldState initial = worldState(runtime);
        io.farfrontier.palemirror.frontier.v3.model.ProductionJob job = initial.productionJobs().get(
                new SubjectId("job:production-development-input-theft"));
        var candidate = io.farfrontier.palemirror.frontier.v3.model.FrontierProductionWorkSceneSupport.candidates(initial).stream().findFirst().orElseThrow();
        SceneLeaseId leaseId = new SceneLeaseId("lease:production-work-restart");
        CheckpointImage checkpoint = runtime.checkpointImage().orElseThrow();
        SceneLease lease = SceneLease.forCause(leaseId, world, new io.farfrontier.palemirror.frontier.v3.model.ProductionWorkSceneCause(job.id()),
                candidate.handoffPosition(), checkpoint.instant(), checkpoint.revision().value(), SceneLeaseStatus.PREPARED,
                List.of(new io.farfrontier.palemirror.frontier.v3.model.SceneMember(job.workerId(), SceneLease.deterministicEntityId(world, job.workerId()))), Set.of(), java.util.Optional.empty());
        submitWorld(runtime, "production-restart-prepare", new io.farfrontier.palemirror.frontier.v3.model.ProductionWorkSceneLeasePrepared(lease));
        var preparedState = worldState(runtime); var location = preparedState.actorLocations().get(job.workerId());
        submitWorld(runtime, "production-restart-modeled-presence", new ActorBodyPresent(
                ActorBodyAuthority.current(preparedState, job.workerId()), location.body(), location.condition().health(),
                location.body(), location.condition().health()));
        submitWorld(runtime, "production-restart-hot", new SceneLeaseTransition(leaseId, SceneLeaseStatus.HOT));
        int inputCursor = job.workTraversal().linearCorridorSurfaces().size() - 2;
        while (job.traversalCursor() < inputCursor) {
            int cursor = job.traversalCursor() + 1;
            var body = job.workTraversal().linearCorridorSurfaces().get(cursor).standingBody();
            var observation = ModeledActorBodyFacts.productionObservation(worldState(runtime), job, leaseId);
            inspectModeledBody(runtime, job.workerId(), body, "production-restart-inspected-" + cursor);
            submitWorld(runtime, "production-restart-advance-" + cursor,
                    new io.farfrontier.palemirror.frontier.v3.model.ProductionWorkTraversalAdvanced(job.id(), leaseId, body, cursor, observation));
            job = worldState(runtime).productionJobs().get(job.id());
        }
        var inputBody = job.workTraversal().linearCorridorSurfaces().get(inputCursor).standingBody();
        submitWorld(runtime, "production-restart-input", new io.farfrontier.palemirror.frontier.v3.model.ProductionWorkProgressed(
                job.id(), leaseId, inputBody, io.farfrontier.palemirror.frontier.v3.model.ProductionWorkProgress.inputReady(),
                ModeledActorBodyFacts.productionObservation(worldState(runtime), job, leaseId)));
        job = worldState(runtime).productionJobs().get(job.id());
        int workCursor = inputCursor + 1;
        var workBody = job.workTraversal().linearCorridorSurfaces().get(workCursor).standingBody();
        var arrival = ModeledActorBodyFacts.productionObservation(worldState(runtime), job, leaseId);
        inspectModeledBody(runtime, job.workerId(), workBody, "production-restart-work-inspected");
        submitWorld(runtime, "production-restart-work-arrival", new io.farfrontier.palemirror.frontier.v3.model.ProductionWorkTraversalAdvanced(
                job.id(), leaseId, workBody, workCursor, arrival));
        job = worldState(runtime).productionJobs().get(job.id());
        for (int completed = 0; completed <= 17; completed++) {
            if (completed > 0) runtime.advance(20, new WorkBudget(64, 512));
            job = worldState(runtime).productionJobs().get(job.id());
            FrontierV3CommandSubmission.submitBound(runtime, "production-restart-processing-" + completed, job.id().value(),
                    new io.farfrontier.palemirror.frontier.v3.model.ProductionWorkProgressed(job.id(), leaseId, workBody,
                            io.farfrontier.palemirror.frontier.v3.model.ProductionWorkProgress.processing(completed),
                            ModeledActorBodyFacts.productionObservation(worldState(runtime), job, leaseId)),
                    FrontierV3ContinuationBinding.require(runtime.checkpointImage().orElseThrow(), job.id(), "frontier.settlement.production.task.complete"));
        }
        var retainedWorkAction = FrontierV3ContinuationBinding.require(runtime.checkpointImage().orElseThrow(), job.id(),
                "frontier.settlement.production.task.complete");
        runtime.shutdown();

        FrontierV3ServerRuntime<FrontierWorldState, io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection> recovered =
                FrontierV3ServerRuntime.start(configuration, store, 10_000);
        FrontierWorldState persisted = worldState(recovered);
        assertEquals(retainedWorkAction, FrontierV3ContinuationBinding.require(recovered.checkpointImage().orElseThrow(), job.id(),
                "frontier.settlement.production.task.complete"), "restart must preserve the exact labor deadline, not only progress count");
        assertEquals(io.farfrontier.palemirror.frontier.v3.model.ProductionWorkProgress.processing(17), persisted.productionJobs().get(job.id()).workProgress());
        assertEquals(workCursor, persisted.productionJobs().get(job.id()).traversalCursor());
        assertEquals(SceneLeaseStatus.HOT, persisted.sceneLeases().get(leaseId).status());
        assertEquals(1, FrontierV3SceneLeaseRestartSafety.quarantineActiveLeases(recovered));
        assertEquals(0, FrontierV3SceneLeaseRestartSafety.quarantineActiveLeases(recovered));
        FrontierWorldState unknown = worldState(recovered);
        assertEquals(io.farfrontier.palemirror.frontier.v3.model.ProductionWorkProgress.processing(17), unknown.productionJobs().get(job.id()).workProgress());
        assertEquals(workCursor, unknown.productionJobs().get(job.id()).traversalCursor());
        assertEquals(SceneLeaseStatus.UNKNOWN_AFTER_RESTART, unknown.sceneLeases().get(leaseId).status());
        assertTrue(unknown.productionJobs().containsKey(job.id()), "restart must not fabricate a receipt or release the exact worker job");
        recovered.shutdown();
    }


    @Test
    void restartQuarantinesAmbientHotLeaseIdempotently(@TempDir Path directory) {
        WorldId world = new WorldId("frontier:ambient-lease-recovery");
        FrontierStore store = new FrontierFileStore(directory, FrontierWorldRuntimeDefinition.payloadCodecs());
        var configuration = FrontierWorldRuntimeDefinition.configuration(world, 91L);
        FrontierV3ServerRuntime<FrontierWorldState, io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection> runtime =
                FrontierV3ServerRuntime.start(configuration, store, 10_000);
        SubjectId resident = new SubjectId("resident:1-1");
        CheckpointImage before = runtime.checkpointImage().orElseThrow();
        FrontierWorldState state = new FrontierWorldStateCodec().decode(before.canonicalState());
        AmbientActorLease lease = AmbientActorProcess.nextLease(state, resident, before.instant());
        submitAmbient(runtime, world, new AmbientLeasePrepared(lease), "command:ambient-recovery-prepare");
        presentModeledBody(runtime, resident, "ambient-recovery-body");
        submitAmbient(runtime, world, admittedAmbientFixture(worldState(runtime), resident), "command:ambient-recovery-hot");
        runtime.shutdown();

        FrontierV3ServerRuntime<FrontierWorldState, io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection> recovered =
                FrontierV3ServerRuntime.start(configuration, store, 10_000);
        assertEquals(1, FrontierV3AmbientLeaseRestartSafety.quarantineActiveLeases(recovered));
        assertEquals(0, FrontierV3AmbientLeaseRestartSafety.quarantineActiveLeases(recovered));
        FrontierWorldState unknown = new FrontierWorldStateCodec().decode(recovered.checkpointImage().orElseThrow().canonicalState());
        assertEquals(AmbientLeaseStatus.UNKNOWN_AFTER_RESTART, unknown.ambientLeases().get(resident).status());
        assertEquals(1, recovered.projection(ProjectionQuery.summary()).orElseThrow().unknownAmbientLeaseCount());
        recovered.shutdown();
    }

    @Test
    void restartRetainsPreparedAmbientLeaseForLoadedChunkMaterialization(@TempDir Path directory) {
        WorldId world = new WorldId("frontier:ambient-prepared-recovery");
        FrontierStore store = new FrontierFileStore(directory, FrontierWorldRuntimeDefinition.payloadCodecs());
        var configuration = FrontierWorldRuntimeDefinition.configuration(world, 91L);
        FrontierV3ServerRuntime<FrontierWorldState, io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection> runtime =
                FrontierV3ServerRuntime.start(configuration, store, 10_000);
        SubjectId resident = new SubjectId("resident:1-1");
        CheckpointImage before = runtime.checkpointImage().orElseThrow();
        FrontierWorldState state = new FrontierWorldStateCodec().decode(before.canonicalState());
        AmbientActorLease lease = AmbientActorProcess.nextLease(state, resident, before.instant());
        submitAmbient(runtime, world, new AmbientLeasePrepared(lease), "command:ambient-prepared-recovery");
        runtime.shutdown();

        FrontierV3ServerRuntime<FrontierWorldState, io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection> recovered =
                FrontierV3ServerRuntime.start(configuration, store, 10_000);
        assertEquals(0, FrontierV3AmbientLeaseRestartSafety.quarantineActiveLeases(recovered));
        FrontierWorldState prepared = new FrontierWorldStateCodec().decode(recovered.checkpointImage().orElseThrow().canonicalState());
        assertEquals(AmbientLeaseStatus.PREPARED, prepared.ambientLeases().get(resident).status());
        assertEquals(0, recovered.projection(ProjectionQuery.summary()).orElseThrow().unknownAmbientLeaseCount());
        recovered.shutdown();
    }

    private static void transitionScene(FrontierV3ServerRuntime<FrontierWorldState, io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection> runtime,
                                        WorldId world, SceneLeaseId leaseId, SceneLeaseStatus status, String command) {
        CheckpointImage checkpoint = runtime.checkpointImage().orElseThrow();
        CommandId commandId = new CommandId(command);
        assertInstanceOf(CommandResult.Accepted.class, runtime.submit(new FrontierCommand(1, commandId, world, checkpoint.revision(), checkpoint.instant(),
                FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(commandId), new SceneLeaseTransition(leaseId, status))).orElseThrow());
    }
    private static io.farfrontier.palemirror.frontier.v3.model.AmbientBodyConfirmed admittedAmbientFixture(FrontierWorldState state, SubjectId actor) {
        AmbientActorLease lease = state.ambientLeases().get(actor);
        return new io.farfrontier.palemirror.frontier.v3.model.AmbientBodyConfirmed(actor, lease.revision(),
                io.farfrontier.palemirror.frontier.v3.model.AmbientBodyConfirmed.Boundary.ADMISSION,
                lease.handoffBody(), lease.handoffBody(), io.farfrontier.palemirror.frontier.v3.model.ActorBodyAuthority.current(state, actor));
    }
    private static void submitAmbient(FrontierV3ServerRuntime<FrontierWorldState, io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection> runtime,
                                      WorldId world, FrontierPayload payload, String command) {
        CheckpointImage checkpoint = runtime.checkpointImage().orElseThrow(); CommandId commandId = new CommandId(command);
        CommandResult result = runtime.submit(new FrontierCommand(1, commandId, world, checkpoint.revision(), checkpoint.instant(),
                FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(commandId), payload)).orElseThrow();
        assertInstanceOf(CommandResult.Accepted.class, result, result::toString);
    }

    private static FrontierEngineConfiguration<Counter, CounterProjection> configuration() {
        return configuration(new StateCodec<>() {
            @Override public byte[] encode(Counter state) { return ByteBuffer.allocate(4).putInt(state.value()).array(); }
            @Override public Counter decode(byte[] bytes) {
                if (bytes.length != 4) throw new IllegalArgumentException("counter state is malformed");
                return new Counter(ByteBuffer.wrap(bytes).getInt());
            }
        });
    }

    private static FrontierEngineConfiguration<Counter, CounterProjection> configuration(StateCodec<Counter> codec) {
        return new FrontierEngineConfiguration<>(WORLD, new Counter(0), SimInstant.ZERO,
                (state, command) -> new CommandPlan.Accepted(List.of(new ProposedEvent(SUBJECT, command.payload()))),
                (state, action) -> List.of(),
                (state, event) -> new Counter(Math.addExact(state.value(), ((Delta) event.payload()).value())),
                codec, (state, world, revision, instant, query) -> new CounterProjection(world, revision, instant, state.value()),
                new EngineLimits(8, 100L, 8), List.of(), TransactionCommitter.noOp());
    }

    private static FrontierCommand command(String id, Revision revision, SimInstant instant, int value) {
        CommandId commandId = new CommandId(id);
        return new FrontierCommand(1, commandId, WORLD, revision, instant, SUBJECT, CauseChain.root(commandId), new Delta(value));
    }

    private static PayloadCodecs codecs() {
        return new PayloadCodecs(List.of(new PayloadCodec() {
            @Override public String type() { return "test.runtime_delta"; }
            @Override public byte[] encode(FrontierPayload payload) {
                return ByteBuffer.allocate(4).putInt(((Delta) payload).value()).array();
            }
            @Override public FrontierPayload decode(byte[] bytes) {
                if (bytes.length != 4) throw new IllegalArgumentException("delta payload is malformed");
                return new Delta(ByteBuffer.wrap(bytes).getInt());
            }
        }));
    }

    private static FrontierWorldState worldState(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        return new FrontierWorldStateCodec().decode(runtime.checkpointImage().orElseThrow(() -> new AssertionError(
                "runtime has no readable active state at " + runtime.calendarInstant() + ": " + runtime.status())).canonicalState());
    }


    /** Explicit modeled body event for filesystem recovery coverage, not a native movement claim. */
    private static void presentModeledBody(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                         SubjectId actor, String phase) {
        var state = worldState(runtime); var location = state.actorLocations().get(actor);
        submitWorld(runtime, phase, new ActorBodyPresent(ActorBodyAuthority.current(state, actor),
                location.body(), location.condition().health(), location.body(), location.condition().health()));
    }

    /** Explicit modeled body event for filesystem recovery coverage, not a native movement claim. */
    private static void inspectModeledBody(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                         SubjectId actor, BodyPosition observed, String phase) {
        var state = worldState(runtime); var location = state.actorLocations().get(actor);
        submitWorld(runtime, phase, new ActorBodyInspected(ActorBodyAuthority.current(state, actor),
                ActorBodyInspected.Source.INDEXED_LIVING, location.body(), location.condition().health(),
                observed, location.condition().health(), state.actorExecutions().actors().get(actor).current()));
    }

    private static void submitWorld(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, String phase, FrontierPayload payload) {
        CheckpointImage checkpoint = runtime.checkpointImage().orElseThrow(); CommandId commandId = new CommandId("test:" + phase);
        FrontierCommand command = new FrontierCommand(1, commandId, checkpoint.worldId(), checkpoint.revision(), checkpoint.instant(),
                FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(commandId), payload);
        CommandResult result = runtime.submit(command).orElseThrow();
        assertInstanceOf(CommandResult.Accepted.class, result, result::toString);
    }

    private record Counter(int value) { }
    private record CounterProjection(WorldId worldId, Revision revision, SimInstant instant, int value) implements FrontierProjection { }
    private record Delta(int value) implements FrontierPayload {
        @Override public String type() { return "test.runtime_delta"; }
    }

    private static final class CountingCounterCodec implements StateCodec<Counter> {
        private int encodeCalls;
        private int decodeCalls;

        @Override public byte[] encode(Counter state) {
            encodeCalls++;
            return ByteBuffer.allocate(4).putInt(state.value()).array();
        }

        @Override public Counter decode(byte[] bytes) {
            decodeCalls++;
            if (bytes.length != 4) throw new IllegalArgumentException("counter state is malformed");
            return new Counter(ByteBuffer.wrap(bytes).getInt());
        }

        private void reset() {
            encodeCalls = 0;
            decodeCalls = 0;
        }
    }
}
