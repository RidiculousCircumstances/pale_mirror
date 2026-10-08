package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.AdvanceResult;
import io.farfrontier.palemirror.frontier.v3.api.CheckpointImage;
import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
import io.farfrontier.palemirror.frontier.v3.api.FrontierCanonicalState;
import io.farfrontier.palemirror.frontier.v3.api.FrontierCanonicalStateAccess;
import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.FrontierProjection;
import io.farfrontier.palemirror.frontier.v3.api.Revision;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierExecutionMetrics;
import io.farfrontier.palemirror.frontier.v3.kernel.TransactionCommitter;
import io.farfrontier.palemirror.frontier.v3.kernel.WorkBudget;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierStore;
import io.farfrontier.palemirror.frontier.v3.persistence.RecoveryImage;
import io.farfrontier.palemirror.frontier.v3.persistence.SnapshotReceipt;
import io.farfrontier.palemirror.frontier.v3.persistence.SnapshotRecord;
import io.farfrontier.palemirror.frontier.v3.model.DiagnosticCaptureScope;
import io.farfrontier.palemirror.frontier.v3.model.DiagnosticRuntimeIdentity;

import java.util.Objects;
import java.util.Optional;

/**
 * Server-thread lifecycle owner for one v3 world.
 *
 * <p>It is intentionally independent from the frozen V2 runtime. A real NeoForge event bridge
 * will call {@link #tick(WorkBudget)} once per running server tick; this host owns fresh creation,
 * verified recovery, bounded checkpointing and orderly shutdown.</p>
 */
final class FrontierV3ServerRuntime<S, P extends FrontierProjection> {
    private final FrontierEngineConfiguration<S, P> configuration;
    private final FrontierStore store;
    private final int checkpointIntervalTicks;
    /** Explicit host-owned identity, deliberately carried across every reducer entry. */
    private final DiagnosticRuntimeIdentity diagnosticRuntimeIdentity;
    private FrontierCanonicalStateAccess<S, P> engine;
    private FrontierV3RuntimeStatus status;
    private SimInstant instant;
    private int ticksSinceCheckpoint;
    /**
     * One immutable defensive image for the current canonical tick/revision.  Physical adapters
     * routinely inspect it several times in one server tick; rebuilding it would clone the full
     * canonical byte snapshot for every read without creating any new canonical evidence.
     */
    private CheckpointImage cachedCheckpoint;

    private FrontierV3ServerRuntime(
            FrontierEngineConfiguration<S, P> configuration, FrontierStore store, TransactionCommitter committer, int checkpointIntervalTicks, RecoveryImage recovered,
            RuntimeException startupFailure, DiagnosticRuntimeIdentity diagnosticRuntimeIdentity
    ) {
        this.configuration = Objects.requireNonNull(configuration, "configuration")
                .withTransactionCommitter(Objects.requireNonNull(committer, "transaction committer"));
        this.store = Objects.requireNonNull(store, "store");
        this.diagnosticRuntimeIdentity = Objects.requireNonNull(diagnosticRuntimeIdentity, "diagnostic runtime identity");
        if (checkpointIntervalTicks < 1) throw new IllegalArgumentException("checkpoint interval must be positive");
        this.checkpointIntervalTicks = checkpointIntervalTicks;
        if (startupFailure != null) {
            status = FrontierV3RuntimeStatus.quarantined(startupFailure);
            return;
        }
        try {
            RecoveryImage image = recovered == null ? store.recover(configuration.worldId()) : recovered;
            if (!configuration.worldId().equals(image.worldId())) throw new IllegalArgumentException("recovery image belongs to a different Frontier world");
            try (DiagnosticCaptureScope ignored = DiagnosticCaptureScope.open(diagnosticRuntimeIdentity)) {
                engine = image.checkpoint().isEmpty() && image.walTail().isEmpty()
                        ? FrontierEngines.createCanonicalStateAccess(this.configuration)
                        : FrontierEngines.recoverCanonicalStateAccess(this.configuration, image);
            }
            cachedCheckpoint = engine.checkpoint();
            instant = cachedCheckpoint.instant();
            status = FrontierV3RuntimeStatus.active();
        } catch (RuntimeException error) {
            status = FrontierV3RuntimeStatus.quarantined(error);
        }
    }

    static <S, P extends FrontierProjection> FrontierV3ServerRuntime<S, P> start(
            FrontierEngineConfiguration<S, P> configuration, FrontierStore store, int checkpointIntervalTicks
    ) {
        return start(configuration, store, checkpointIntervalTicks, DiagnosticRuntimeIdentity.unavailable());
    }

    /** Host composition must pass a factual identity; unscoped fixture callers remain visibly degraded. */
    static <S, P extends FrontierProjection> FrontierV3ServerRuntime<S, P> start(
            FrontierEngineConfiguration<S, P> configuration, FrontierStore store, int checkpointIntervalTicks, DiagnosticRuntimeIdentity identity
    ) {
        return new FrontierV3ServerRuntime<>(configuration, store, new FrontierStoreTransactionCommitter(store), checkpointIntervalTicks, null, null, identity);
    }

    /** Starts from one already-verified recovery image, avoiding a second store read after profile selection. */
    static <S, P extends FrontierProjection> FrontierV3ServerRuntime<S, P> startRecovered(
            FrontierEngineConfiguration<S, P> configuration, FrontierStore store, RecoveryImage recovered, int checkpointIntervalTicks
    ) {
        return startRecovered(configuration, store, recovered, checkpointIntervalTicks, DiagnosticRuntimeIdentity.unavailable());
    }

    static <S, P extends FrontierProjection> FrontierV3ServerRuntime<S, P> startRecovered(
            FrontierEngineConfiguration<S, P> configuration, FrontierStore store, RecoveryImage recovered, int checkpointIntervalTicks, DiagnosticRuntimeIdentity identity
    ) {
        return new FrontierV3ServerRuntime<>(configuration, store, new FrontierStoreTransactionCommitter(store), checkpointIntervalTicks,
                Objects.requireNonNull(recovered, "recovered image"), null, identity);
    }

    /** Host composition supplies its physical-admission write-ahead participant explicitly. */
    static <S, P extends FrontierProjection> FrontierV3ServerRuntime<S, P> startRecovered(
            FrontierEngineConfiguration<S, P> configuration, FrontierStore store, RecoveryImage recovered,
            int checkpointIntervalTicks, DiagnosticRuntimeIdentity identity, TransactionCommitter committer
    ) {
        return new FrontierV3ServerRuntime<>(configuration, store, committer, checkpointIntervalTicks,
                Objects.requireNonNull(recovered, "recovered image"), null, identity);
    }

    /** Preserves visible fail-closed lifecycle state when recovery selection itself is invalid. */
    static <S, P extends FrontierProjection> FrontierV3ServerRuntime<S, P> failedStart(
            FrontierEngineConfiguration<S, P> configuration, FrontierStore store, int checkpointIntervalTicks, RuntimeException failure
    ) {
        return failedStart(configuration, store, checkpointIntervalTicks, failure, DiagnosticRuntimeIdentity.unavailable());
    }

    static <S, P extends FrontierProjection> FrontierV3ServerRuntime<S, P> failedStart(
            FrontierEngineConfiguration<S, P> configuration, FrontierStore store, int checkpointIntervalTicks, RuntimeException failure, DiagnosticRuntimeIdentity identity
    ) {
        return new FrontierV3ServerRuntime<>(configuration, store, new FrontierStoreTransactionCommitter(store), checkpointIntervalTicks, null,
                Objects.requireNonNull(failure, "startup failure"), identity);
    }

    FrontierV3RuntimeStatus status() { return status; }

    FrontierExecutionMetrics executionMetrics() { return configuration.executionMetrics(); }

    /** Last authoritative instant remains readable when execution is stopped or quarantined; no snapshot encoding. */
    java.util.OptionalLong calendarInstant() {
        return engine == null ? java.util.OptionalLong.empty()
                : java.util.OptionalLong.of(engine.canonicalState().instant().ticks());
    }

    /**
     * Returns an encoded image for persistence or explicit diagnostic/export boundaries.
     *
     * <p>Ordinary server-thread adapters use {@link #canonicalState()} or {@link #executionView()}
     * without encoding. All mutations still enter {@link #submit(FrontierCommand)}.</p>
     */
    Optional<CheckpointImage> checkpointImage() {
        if (status.kind() != FrontierV3RuntimeStatus.Kind.ACTIVE) return Optional.empty();
        if (cachedCheckpoint == null) cachedCheckpoint = engine.checkpoint();
        return Optional.of(cachedCheckpoint);
    }

    /** Read-only continuation metadata without snapshot encoding or resource-image copying. */
    Optional<io.farfrontier.palemirror.frontier.v3.api.FrontierExecutionView> executionView() {
        if (status.kind() != FrontierV3RuntimeStatus.Kind.ACTIVE) return Optional.empty();
        return Optional.of(engine.executionView());
    }

    boolean retainsScheduledAction(io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction action) {
        return status.kind() == FrontierV3RuntimeStatus.Kind.ACTIVE && engine.retainsScheduledAction(action);
    }

    Optional<io.farfrontier.palemirror.frontier.v3.api.CommandAdmissionCapacity> commandAdmissionCapacity() {
        if (status.kind() != FrontierV3RuntimeStatus.Kind.ACTIVE) return Optional.empty();
        return Optional.of(engine.commandAdmissionCapacity());
    }

    /** Returns the exact immutable state/revision/instant for an owning server-thread adapter. */
    Optional<FrontierCanonicalState<S>> canonicalState() {
        if (status.kind() != FrontierV3RuntimeStatus.Kind.ACTIVE) return Optional.empty();
        return Optional.of(engine.canonicalState());
    }

    /**
     * Compatibility name for physical executors that need only the immutable current state.
     * It is intentionally not decoded from a checkpoint: snapshots are persistence boundaries,
     * not the ordinary materialization read path.
     */
    Optional<S> decodedState() {
        return canonicalState().map(FrontierCanonicalState::state);
    }

    /**
     * Read-only retained ownership for the native crop-growth veto/post-veto, including quarantine.
     * Quarantine suspends execution; it does not surrender already-owned physical cells
     * to a second simulation. The paired post-veto may undo a forced native growth event;
     * never use this view to resume commands or advance canonical projection writes.
     * Failed startup without a recovered engine supplies no invented ownership.
     */
    Optional<S> stateForNativeGrowthFence() {
        return engine == null ? Optional.empty() : Optional.of(engine.canonicalState().state());
    }

    Optional<CommandResult> submit(FrontierCommand command) {
        Objects.requireNonNull(command, "command");
        if (status.kind() != FrontierV3RuntimeStatus.Kind.ACTIVE) return Optional.empty();
        CommandResult result;
        try (DiagnosticCaptureScope ignored = DiagnosticCaptureScope.open(diagnosticRuntimeIdentity)) {
            result = engine.submit(command);
        }
        // Even a rejected command may have quarantined the engine.  Discarding a read-only
        // image is harmless; successful commands must never leave a stale snapshot visible to a
        // later physical executor in the same server tick.
        cachedCheckpoint = null;
        if (engine.status().kind() == io.farfrontier.palemirror.frontier.v3.api.EngineStatus.Kind.QUARANTINED) {
            status = new FrontierV3RuntimeStatus(FrontierV3RuntimeStatus.Kind.QUARANTINED, engine.status().failureDetail());
        }
        return Optional.of(result);
    }

    Optional<P> projection(io.farfrontier.palemirror.frontier.v3.api.ProjectionQuery query) {
        if (status.kind() != FrontierV3RuntimeStatus.Kind.ACTIVE) return Optional.empty();
        return Optional.of(engine.projection(query));
    }

    Optional<AdvanceResult> tick(WorkBudget budget) {
        return advanceOne(budget, true);
    }

    void beginPersistenceTurn() { if (store instanceof FrontierFileStore fileStore) fileStore.beginTurn(); }
    void endPersistenceTurn() { if (store instanceof FrontierFileStore fileStore) fileStore.endTurn(); }

    /**
     * Advances the ordinary ordered due-action engine by a bounded operator-requested interval.
     * Every unit retains its normal WAL-backed transition. Checkpoints remain periodic within
     * a long request so bounded retained history cannot turn an operator fast-forward into a
     * different, self-quarantining execution path.
     */
    Optional<AdvanceResult> advance(int ticks, WorkBudget budget) {
        if (ticks < 1) throw new IllegalArgumentException("advance ticks must be positive");
        Objects.requireNonNull(budget, "budget");
        AdvanceResult latest = null;
        for (int index = 0; index < ticks; index++) {
            Optional<AdvanceResult> result = advanceOne(budget, true);
            if (result.isEmpty()) return Optional.empty();
            latest = result.orElseThrow();
            if (status.kind() != FrontierV3RuntimeStatus.Kind.ACTIVE) return Optional.of(latest);
        }
        return Optional.of(Objects.requireNonNull(latest, "advanced result"));
    }

    private Optional<AdvanceResult> advanceOne(WorkBudget budget, boolean checkpointWhenDue) {
        return advanceInterval(1, budget, checkpointWhenDue);
    }

    /** Caller has excluded executable physical work on the owning thread. Never crosses due/audit/save boundaries. */
    Optional<AdvanceResult> advanceColdInterval(int maxTicks, WorkBudget budget) {
        if (maxTicks < 1) throw new IllegalArgumentException("cold interval must be positive");
        if (status.kind() != FrontierV3RuntimeStatus.Kind.ACTIVE) return Optional.empty();
        long boundary = engine.nextExecutionBoundary().map(value -> Math.max(1L, value.ticks() - instant.ticks()))
                .orElse((long) maxTicks);
        int interval = Math.toIntExact(Math.min(Math.min(maxTicks, boundary),
                Math.max(1, checkpointIntervalTicks - ticksSinceCheckpoint)));
        return advanceInterval(interval, budget, true);
    }

    private Optional<AdvanceResult> advanceInterval(int intervalTicks, WorkBudget budget, boolean checkpointWhenDue) {
        Objects.requireNonNull(budget, "budget");
        if (status.kind() != FrontierV3RuntimeStatus.Kind.ACTIVE) return Optional.empty();
        try {
            AdvanceResult result;
            try (DiagnosticCaptureScope ignored = DiagnosticCaptureScope.open(diagnosticRuntimeIdentity)) {
                result = engine.advanceTo(instant.plus(intervalTicks), budget);
            }
            // SimInstant advances even when no due action mutates the aggregate, so each server
            // tick has a distinct immutable checkpoint image for adapter observation.
            cachedCheckpoint = null;
            instant = result.instant();
            ticksSinceCheckpoint = Math.addExact(ticksSinceCheckpoint, intervalTicks);
            if (result.status().kind() == io.farfrontier.palemirror.frontier.v3.api.EngineStatus.Kind.QUARANTINED) {
                status = new FrontierV3RuntimeStatus(FrontierV3RuntimeStatus.Kind.QUARANTINED, result.status().failureDetail());
                return Optional.of(result);
            }
            if (checkpointWhenDue && ticksSinceCheckpoint >= checkpointIntervalTicks) {
                try (FrontierExecutionMetrics.Span ignored = FrontierExecutionMetrics.safelyBegin(configuration.executionMetrics(),
                        FrontierExecutionMetrics.Stage.TRANSACTION, "runtime.checkpoint", configuration.worldId().value())) {
                    checkpoint();
                }
            }
            return Optional.of(result);
        } catch (RuntimeException error) {
            quarantine(error);
            return Optional.empty();
        }
    }

    Optional<SnapshotReceipt> checkpoint() {
        if (status.kind() != FrontierV3RuntimeStatus.Kind.ACTIVE) return Optional.empty();
        try {
            CheckpointImage checkpoint = checkpointImage().orElseThrow();
            RecoveryImage durable = store instanceof FrontierFileStore fileStore
                    ? fileStore.recoverOwned(configuration.worldId()) : store.recover(configuration.worldId());
            Revision persistedRevision = durable.walTail().isEmpty()
                    ? durable.checkpoint().map(value -> value.checkpoint().revision()).orElse(Revision.ZERO)
                    : durable.walTail().getLast().revision();
            if (!checkpoint.revision().equals(persistedRevision)) {
                throw new IllegalStateException("v3 checkpoint revision is not fully represented in WAL");
            }
            long coveredSequence = durable.checkpoint().map(SnapshotRecord::coveredWalSequence).orElse(0L)
                    + durable.walTail().size();
            SnapshotReceipt receipt = store.installSnapshot(new SnapshotRecord(checkpoint, coveredSequence));
            if (!checkpoint.revision().equals(receipt.revision()) || coveredSequence != receipt.snapshotSequence()) {
                throw new IllegalStateException("v3 store returned a mismatched snapshot receipt");
            }
            store.compact(configuration.worldId(), checkpoint.revision());
            // The checksum-bound snapshot and its WAL compaction have succeeded. Only now may
            // the running engine release the same retained transaction history.
            engine.compact(checkpoint.revision());
            ticksSinceCheckpoint = 0;
            return Optional.of(receipt);
        } catch (RuntimeException error) {
            quarantine(error);
            return Optional.empty();
        }
    }

    void shutdown() {
        try {
            if (status.kind() == FrontierV3RuntimeStatus.Kind.ACTIVE) {
                checkpoint();
                if (status.kind() == FrontierV3RuntimeStatus.Kind.ACTIVE) status = FrontierV3RuntimeStatus.stopped();
            }
        } finally {
            if (store instanceof FrontierFileStore files) files.close();
        }
    }

    void quarantine(RuntimeException error) {
        status = FrontierV3RuntimeStatus.quarantined(error);
    }
}
