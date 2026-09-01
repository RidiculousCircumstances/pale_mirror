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
import io.farfrontier.palemirror.frontier.v3.model.FrontierV3FixtureCatalog;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.model.ContractStatus;
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
import io.farfrontier.palemirror.frontier.v3.model.BioformRole;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalIntentPrepared;
import io.farfrontier.palemirror.frontier.v3.model.RouteOperation;
import io.farfrontier.palemirror.frontier.v3.model.SceneLease;
import io.farfrontier.palemirror.frontier.v3.model.SceneLeaseHandoff;
import io.farfrontier.palemirror.frontier.v3.model.SceneLeasePrepared;
import io.farfrontier.palemirror.frontier.v3.model.SceneLeaseReleased;
import io.farfrontier.palemirror.frontier.v3.model.SceneLeaseStatus;
import io.farfrontier.palemirror.frontier.v3.model.SceneMemberPosition;
import io.farfrontier.palemirror.frontier.v3.model.SceneLeaseTransition;
import io.farfrontier.palemirror.frontier.v3.model.SceneLeaseRecoveryUnresolved;
import io.farfrontier.palemirror.frontier.v3.model.OperationStage;
import io.farfrontier.palemirror.frontier.v3.model.SceneEngagementCandidate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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
    void failedRecoverySelectionRemainsAVisibleQuarantinedV3Runtime(@TempDir Path directory) {
        FrontierV3ServerRuntime<Counter, CounterProjection> runtime = FrontierV3ServerRuntime.failedStart(
                configuration(), new FrontierFileStore(directory, codecs()), 20,
                new IllegalArgumentException("recovery header does not match selected physical world"));

        assertEquals(FrontierV3RuntimeStatus.Kind.QUARANTINED, runtime.status().kind());
        assertTrue(runtime.status().detail().orElseThrow().contains("recovery header"));
        assertTrue(runtime.checkpointImage().isEmpty());
        assertTrue(runtime.submit(command("command:must-not-run", Revision.ZERO, SimInstant.ZERO, 1)).isEmpty());
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
    void sceneAdmissionDefersWhenAnExactParticipantAlreadyHasAnAmbientLease(@TempDir Path directory) {
        WorldId world = new WorldId("frontier:scene-ambient-handoff");
        var runtime = FrontierV3ServerRuntime.start(FrontierV3FixtureCatalog.routeSceneReturnConfiguration(world, 91L),
                new FrontierFileStore(directory, FrontierWorldRuntimeDefinition.payloadCodecs()), 10_000);
        FrontierWorldState before = worldState(runtime);
        RouteOperation operation = before.operations().get(new SubjectId("operation:supply-1-2"));
        SubjectId participant = operation.participantIds().getFirst();
        submitAmbient(runtime, world, new AmbientLeasePrepared(AmbientActorProcess.nextLease(before, participant,
                runtime.checkpointImage().orElseThrow().instant())), "command:ambient-scene-overlap-prepare");
        submitAmbient(runtime, world, new AmbientLeaseTransition(participant, AmbientLeaseStatus.HOT), "command:ambient-scene-overlap-hot");
        FrontierWorldState overlapped = worldState(runtime);

        assertFalse(FrontierSceneAdmission.available(overlapped, operation.participantIds()));
        assertTrue(FrontierSceneAdmission.reserved(overlapped, participant));
        assertEquals(FrontierV3RuntimeStatus.Kind.ACTIVE, runtime.status().kind());
    }

    @Test
    void sceneHandoffAtomicallyCapturesAndClosesTheExactAmbientLease(@TempDir Path directory) {
        WorldId world = new WorldId("frontier:scene-ambient-transfer");
        var runtime = FrontierV3ServerRuntime.start(FrontierV3FixtureCatalog.routeSceneReturnConfiguration(world, 91L),
                new FrontierFileStore(directory, FrontierWorldRuntimeDefinition.payloadCodecs()), 10_000);
        FrontierWorldState before = worldState(runtime);
        RouteOperation operation = before.operations().get(new SubjectId("operation:supply-1-2"));
        SubjectId participant = operation.participantIds().getFirst();
        submitAmbient(runtime, world, new AmbientLeasePrepared(AmbientActorProcess.nextLease(before, participant,
                runtime.checkpointImage().orElseThrow().instant())), "command:ambient-scene-transfer-prepare");
        submitAmbient(runtime, world, new AmbientLeaseTransition(participant, AmbientLeaseStatus.HOT), "command:ambient-scene-transfer-hot");
        FrontierWorldState overlapped = worldState(runtime);
        CheckpointImage checkpoint = runtime.checkpointImage().orElseThrow();
        SceneLease lease = FrontierV3TestSceneLeases.exact(overlapped, checkpoint,
                new SceneLeaseId("lease:ambient-transfer-r" + checkpoint.revision().value()), operation.id(), operation.cargoId(),
                operation.currentPosition(), java.util.Optional.empty(), operation.participantIds());
        SceneMemberPosition capture = new SceneMemberPosition(participant, new io.farfrontier.palemirror.frontier.v3.model.BodyPosition(12, 64, -12),
                overlapped.actorLocations().get(participant).condition().health());
        lease = lease.withAmbientHandoff(java.util.Set.of(participant));
        SceneLeaseHandoff handoff = new SceneLeaseHandoff(lease, List.of(capture));
        SceneLease handoffLease = lease;

        assertEquals(handoff, FrontierWorldRuntimeDefinition.payloadCodecs().decode(handoff.type(), FrontierWorldRuntimeDefinition.payloadCodecs().encode(handoff)));
        assertThrows(IllegalArgumentException.class, () -> overlapped.handoffAmbientScene(new SceneLeaseHandoff(handoffLease,
                List.of(new SceneMemberPosition(operation.participantIds().getLast(), capture.body(), capture.health())))));
        submitAmbient(runtime, world, handoff, "command:ambient-scene-transfer");

        FrontierWorldState transferred = worldState(runtime);
        assertEquals(AmbientLeaseStatus.CLOSED, transferred.ambientLeases().get(participant).status());
        assertEquals(capture.body(),
                transferred.actorLocations().get(participant).body(),
                "a captured Minecraft feet cell must return to its exact canonical body location");
        assertEquals(lease, transferred.sceneLeases().get(lease.id()));
        assertEquals(FrontierV3RuntimeStatus.Kind.ACTIVE, runtime.status().kind());
    }

    @Test
    void loadedMissingRestartSceneBlocksItsDeliveryWithoutReplacingActorsOrCargo(@TempDir Path directory) {
        WorldId world = new WorldId("frontier:scene-recovery-unresolved");
        var runtime = FrontierV3ServerRuntime.start(FrontierV3FixtureCatalog.routeSceneReturnConfiguration(world, 91L),
                new FrontierFileStore(directory, FrontierWorldRuntimeDefinition.payloadCodecs()), 10_000);
        FrontierWorldState state = worldState(runtime);
        RouteOperation operation = state.operations().get(new SubjectId("operation:supply-1-2"));
        CheckpointImage checkpoint = runtime.checkpointImage().orElseThrow();
        SceneLeaseId leaseId = new SceneLeaseId("lease:scene-recovery-unresolved");
        SceneLease lease = FrontierV3TestSceneLeases.exact(state, checkpoint, leaseId, operation.id(), operation.cargoId(),
                operation.currentPosition(), java.util.Optional.empty(), operation.participantIds());
        submitWorld(runtime, "recovery-unresolved-prepare", new SceneLeasePrepared(lease));
        transitionScene(runtime, world, leaseId, SceneLeaseStatus.HOT, "command:recovery-unresolved-hot");
        assertEquals(1, FrontierV3SceneLeaseRestartSafety.quarantineActiveLeases(runtime));

        submitWorld(runtime, "recovery-unresolved-observation", new SceneLeaseRecoveryUnresolved(leaseId, Set.of(operation.participantIds().getFirst()), false));

        FrontierWorldState after = worldState(runtime);
        assertEquals(OperationStage.FAILED, after.operations().get(operation.id()).stage());
        assertEquals(Set.of(operation.participantIds().getFirst()), after.sceneLeases().get(leaseId).recoveryEvidence().orElseThrow().missingActorIds());
        assertEquals(SceneLeaseStatus.UNKNOWN_AFTER_RESTART, after.sceneLeases().get(leaseId).status());
        String operationProgressSchedule = "schedule:operation-progress-" + operation.id().value().substring("operation:".length());
        assertTrue(runtime.checkpointImage().orElseThrow().schedules().stream()
                .noneMatch(action -> action.id().value().equals(operationProgressSchedule)));
        for (int tick = 0; tick < 100; tick++) runtime.tick(new WorkBudget(64, 512));
        assertEquals(FrontierV3RuntimeStatus.Kind.ACTIVE, runtime.status().kind());
        assertTrue(runtime.checkpointImage().orElseThrow().schedules().stream()
                .noneMatch(action -> action.id().value().equals(operationProgressSchedule)));
        SubjectId reservedParticipant = operation.participantIds().getFirst();
        assertTrue(FrontierSceneAdmission.reserved(after, reservedParticipant));
        CheckpointImage rejectedCheckpoint = runtime.checkpointImage().orElseThrow();
        CommandId rejectedCommandId = new CommandId("command:ambient-recovery-evidence-overlap");
        CommandResult rejected = runtime.submit(new FrontierCommand(1, rejectedCommandId, world, rejectedCheckpoint.revision(), rejectedCheckpoint.instant(),
                FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(rejectedCommandId),
                new AmbientLeasePrepared(AmbientActorProcess.nextLease(after, reservedParticipant, rejectedCheckpoint.instant())))).orElseThrow();
        assertEquals(RejectionCode.REJECTED_BY_POLICY, assertInstanceOf(CommandResult.Rejected.class, rejected).rejection().code());
        runtime.shutdown();
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
        FrontierV3ServerRuntime<Counter, CounterProjection> runtime = FrontierV3ServerRuntime.start(configuration(), store, 2);
        assertInstanceOf(CommandResult.Accepted.class,
                runtime.submit(command("command:advance", Revision.ZERO, SimInstant.ZERO, 7)).orElseThrow());

        assertEquals(new SimInstant(3L), runtime.advance(3, new WorkBudget(4, 8)).orElseThrow().instant());
        assertEquals(new Revision(1L), runtime.checkpointImage().orElseThrow().revision());
        runtime.shutdown();

        FrontierV3ServerRuntime<Counter, CounterProjection> recovered = FrontierV3ServerRuntime.start(configuration(), store, 2);
        assertEquals(new SimInstant(3L), recovered.checkpointImage().orElseThrow().instant());
        assertEquals(7, recovered.projection(ProjectionQuery.summary()).orElseThrow().value());
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
        Path wal = directory.resolve("frontier-v3/frontier_runtime/wal-00000000000000000001.bin");
        byte[] bytes = Files.readAllBytes(wal);
        bytes[bytes.length - 1] ^= 1;
        Files.write(wal, bytes);

        FrontierV3ServerRuntime<Counter, CounterProjection> failed = FrontierV3ServerRuntime.start(configuration(), store, 20);

        assertEquals(FrontierV3RuntimeStatus.Kind.QUARANTINED, failed.status().kind());
        assertTrue(failed.projection(ProjectionQuery.summary()).isEmpty());
    }

    @Test
    void restartRetainsAnUnloadedColdCargoDeliveryWithoutAStalledMaterializationIntent(@TempDir Path directory) {
        WorldId world = new WorldId("frontier:restart-safety");
        FrontierStore store = new FrontierFileStore(directory, FrontierWorldRuntimeDefinition.payloadCodecs());
        var configuration = FrontierV3FixtureCatalog.uncontestedSupplyConfiguration(world, 91L);
        FrontierV3ServerRuntime<FrontierWorldState, io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection> runtime =
                FrontierV3ServerRuntime.start(configuration, store, 10_000);
        SubjectId contractId = new SubjectId("contract:supply-1-2");
        for (int tick = 0; tick < 12_000; tick++) {
            var contract = runtime.decodedState().orElseThrow().contracts().get(contractId);
            if (contract != null && contract.status() == ContractStatus.DELIVERED) break;
            runtime.tick(new WorkBudget(64, 512));
        }

        FrontierWorldState delivered = new FrontierWorldStateCodec().decode(runtime.checkpointImage().orElseThrow().canonicalState());
        assertEquals(ContractStatus.DELIVERED, delivered.contracts().get(contractId).status());
        assertFalse(delivered.physicalIntents().containsKey(new PhysicalIntentId("intent:cargo-handoff-supply-1-2")),
                "unloaded COLD delivery may not create an intent that requires a materializer to finish");

        FrontierV3ServerRuntime<FrontierWorldState, io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection> recovered =
                FrontierV3ServerRuntime.start(configuration, store, 10_000);
        FrontierWorldState state = new FrontierWorldStateCodec().decode(recovered.checkpointImage().orElseThrow().canonicalState());
        assertEquals(ContractStatus.DELIVERED, state.contracts().get(contractId).status());
        assertFalse(state.physicalIntents().containsKey(new PhysicalIntentId("intent:cargo-handoff-supply-1-2")));
        assertEquals(0, recovered.projection(ProjectionQuery.summary()).orElseThrow().unknownPhysicalIntentCount());
    }

    @Test
    void restartRetainsManagedExplosionForItsPersistedPostconditionInspector(@TempDir Path directory) {
        WorldId world = new WorldId("frontier:managed-explosion-restart"); FrontierStore store = new FrontierFileStore(directory, FrontierWorldRuntimeDefinition.payloadCodecs());
        var configuration = FrontierV3FixtureCatalog.hotSceneStrikeConfiguration(world, 91L);
        FrontierV3ServerRuntime<FrontierWorldState, io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection> runtime = FrontierV3ServerRuntime.start(configuration, store, 10_000);
        FrontierWorldState initial = worldState(runtime); SceneEngagementCandidate candidate = initial.coldEngagementSceneCandidates().getFirst(); SceneLeaseId leaseId = new SceneLeaseId("lease:managed-explosion-restart");
        CheckpointImage checkpoint = runtime.checkpointImage().orElseThrow();
        SceneLease lease = FrontierV3TestSceneLeases.exact(initial, checkpoint, leaseId, candidate.operationId(), candidate.cargoId(),
                candidate.handoffPosition(), java.util.Optional.of(candidate.engagementId()), candidate.actorIds());
        submitWorld(runtime, "prepare-explosion-lease", new SceneLeasePrepared(lease)); submitWorld(runtime, "hot-explosion-lease", new SceneLeaseTransition(leaseId, SceneLeaseStatus.HOT));
        FrontierWorldState hot = worldState(runtime); SubjectId bomber = hot.bootstrap().hive().bioforms().stream().filter(value -> value.role() == BioformRole.BOMBER)
                .filter(value -> candidate.actorIds().contains(value.id())).findFirst().orElseThrow().id();
        var point = hot.actorLocations().get(bomber).body(); PhysicalIntentId intentId = new PhysicalIntentId("intent:managed-explosion-restart");
        PhysicalIntent intent = new PhysicalIntent(intentId, PhysicalIntentKind.EXPLOSION, PhysicalIntentStatus.PREPARED, bomber, List.of(bomber, candidate.engagementId()),
                new FixedPosition(FixedScalar.whole(point.x()), FixedScalar.whole(point.y()), FixedScalar.whole(point.z())), 4, PhysicalPostcondition.EXPLOSION_OBSERVED);
        submitWorld(runtime, "prepare-explosion", new PhysicalIntentPrepared(intent)); submitWorld(runtime, "run-explosion", new PhysicalIntentTransition(intentId, PhysicalIntentStatus.RUNNING, java.util.Optional.empty()));
        FrontierV3ServerRuntime<FrontierWorldState, io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection> recovered = FrontierV3ServerRuntime.start(configuration, store, 10_000);
        assertEquals(0, FrontierV3PhysicalIntentRestartSafety.quarantineWithManagedPostcondition(recovered, intentId::equals));
        assertEquals(PhysicalIntentStatus.RUNNING, worldState(recovered).physicalIntents().get(intentId).status());
        assertEquals(1, FrontierV3PhysicalIntentRestartSafety.quarantineWithManagedPostcondition(recovered, ignored -> false));
        assertEquals(PhysicalIntentStatus.UNKNOWN_AFTER_RESTART, worldState(recovered).physicalIntents().get(intentId).status());
    }

    @Test
    void restartSafetyLeavesRunningHarvestForItsLoadedFieldAndDepotInspector() {
        PhysicalIntent harvest = new PhysicalIntent(new PhysicalIntentId("intent:restart-harvest"), PhysicalIntentKind.RESOURCE_SITE_HARVEST,
                PhysicalIntentStatus.RUNNING, new SubjectId("site:1-wheat-field"),
                List.of(new SubjectId("site:1-wheat-field"), new SubjectId("job:site-harvest-1-wheat-field-1"),
                        new SubjectId("resident:1-1"), new SubjectId("item:site-harvest-1-wheat-field-1-wheat")),
                new FixedPosition(FixedScalar.ZERO, FixedScalar.ZERO, FixedScalar.ZERO), 0,
                PhysicalPostcondition.RESOURCE_SITE_HARVESTED_OBSERVED);
        PhysicalIntent unsupported = new PhysicalIntent(new PhysicalIntentId("intent:restart-unsupported"), PhysicalIntentKind.SCENE_STRIKE,
                PhysicalIntentStatus.RUNNING, new SubjectId("operation:supply-1-2"),
                List.of(new SubjectId("bioform:west-1"), new SubjectId("resident:1-1")),
                new FixedPosition(FixedScalar.ZERO, FixedScalar.ZERO, FixedScalar.ZERO), 0, PhysicalPostcondition.SCENE_STRIKE_OBSERVED);

        assertTrue(FrontierV3PhysicalIntentRestartSafety.hasLoadedPostconditionInspector(harvest, ignored -> false),
                "the harvest executor verifies all owned crop cells and the exact depot stack without replay");
        assertFalse(FrontierV3PhysicalIntentRestartSafety.hasLoadedPostconditionInspector(unsupported, ignored -> false),
                "an effect without an exact loaded-world inspector must still fail closed as UNKNOWN");
    }

    @Test
    void restartRetainsPreparedSceneLeaseAndKeepsItsColdRouteSuspended(@TempDir Path directory) {
        WorldId world = new WorldId("frontier:scene-lease-recovery");
        FrontierStore store = new FrontierFileStore(directory, FrontierWorldRuntimeDefinition.payloadCodecs());
        var configuration = FrontierV3FixtureCatalog.routeSceneReturnConfiguration(world, 91L);
        FrontierV3ServerRuntime<FrontierWorldState, io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection> runtime =
                FrontierV3ServerRuntime.start(configuration, store, 10_000);
        FrontierWorldState before = new FrontierWorldStateCodec().decode(runtime.checkpointImage().orElseThrow().canonicalState());
        RouteOperation operation = before.operations().get(new SubjectId("operation:supply-1-2"));
        SceneLeaseId leaseId = new SceneLeaseId("lease:recovery-supply-1-2");
        CheckpointImage checkpoint = runtime.checkpointImage().orElseThrow();
        SceneLease lease = FrontierV3TestSceneLeases.exact(before, checkpoint, leaseId, operation.id(), operation.cargoId(),
                operation.currentPosition(), java.util.Optional.empty(), operation.participantIds());
        CommandId commandId = new CommandId("command:scene-lease-recovery");
        assertInstanceOf(CommandResult.Accepted.class, runtime.submit(new FrontierCommand(1, commandId, world, checkpoint.revision(), checkpoint.instant(),
                FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(commandId), new SceneLeasePrepared(lease))).orElseThrow());

        FrontierV3ServerRuntime<FrontierWorldState, io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection> recovered =
                FrontierV3ServerRuntime.start(configuration, store, 10_000);
        FrontierWorldState restored = new FrontierWorldStateCodec().decode(recovered.checkpointImage().orElseThrow().canonicalState());
        assertEquals(lease, restored.sceneLeases().get(leaseId));
        for (int tick = 0; tick < 100; tick++) recovered.tick(new WorkBudget(64, 512));
        FrontierWorldState afterColdDue = new FrontierWorldStateCodec().decode(recovered.checkpointImage().orElseThrow().canonicalState());
        assertEquals(0, afterColdDue.operations().get(operation.id()).routeIndex());

        transitionScene(recovered, world, leaseId, SceneLeaseStatus.HOT, "command:scene-recovery-hot");
        transitionScene(recovered, world, leaseId, SceneLeaseStatus.DRAINING, "command:scene-recovery-draining");
        CheckpointImage draining = recovered.checkpointImage().orElseThrow();
        SceneLeaseReleased released = new SceneLeaseReleased(leaseId, lease.members().stream().map(member -> {
            var body = lease.memberPosition(member.actorId());
            return new SceneMemberPosition(member.actorId(), body);
        }).toList());
        CommandId releaseCommand = new CommandId("command:scene-recovery-release");
        assertInstanceOf(CommandResult.Accepted.class, recovered.submit(new FrontierCommand(1, releaseCommand, world, draining.revision(), draining.instant(),
                FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(releaseCommand), released)).orElseThrow());
        for (int tick = 0; tick < 1_000 && worldState(recovered).operations().get(operation.id()).routeIndex() == 0; tick++) {
            recovered.tick(new WorkBudget(8, 64));
        }
        FrontierWorldState resumed = new FrontierWorldStateCodec().decode(recovered.checkpointImage().orElseThrow().canonicalState());
        assertEquals(1, resumed.operations().get(operation.id()).routeIndex());
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
        submitAmbient(runtime, world, new AmbientLeaseTransition(resident, AmbientLeaseStatus.HOT), "command:ambient-recovery-hot");
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
    private static void submitAmbient(FrontierV3ServerRuntime<FrontierWorldState, io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection> runtime,
                                      WorldId world, FrontierPayload payload, String command) {
        CheckpointImage checkpoint = runtime.checkpointImage().orElseThrow(); CommandId commandId = new CommandId(command);
        CommandResult result = runtime.submit(new FrontierCommand(1, commandId, world, checkpoint.revision(), checkpoint.instant(),
                FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(commandId), payload)).orElseThrow();
        assertInstanceOf(CommandResult.Accepted.class, result, result::toString);
    }

    private static FrontierEngineConfiguration<Counter, CounterProjection> configuration() {
        StateCodec<Counter> codec = new StateCodec<>() {
            @Override public byte[] encode(Counter state) { return ByteBuffer.allocate(4).putInt(state.value()).array(); }
            @Override public Counter decode(byte[] bytes) {
                if (bytes.length != 4) throw new IllegalArgumentException("counter state is malformed");
                return new Counter(ByteBuffer.wrap(bytes).getInt());
            }
        };
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
        return new FrontierWorldStateCodec().decode(runtime.checkpointImage().orElseThrow().canonicalState());
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
}
