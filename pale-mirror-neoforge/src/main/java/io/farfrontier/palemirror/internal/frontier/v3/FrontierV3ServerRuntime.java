package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.AdvanceResult;
import io.farfrontier.palemirror.frontier.v3.api.CheckpointImage;
import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.FrontierEngine;
import io.farfrontier.palemirror.frontier.v3.api.FrontierProjection;
import io.farfrontier.palemirror.frontier.v3.api.Revision;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines;
import io.farfrontier.palemirror.frontier.v3.kernel.WorkBudget;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierStore;
import io.farfrontier.palemirror.frontier.v3.persistence.RecoveryImage;
import io.farfrontier.palemirror.frontier.v3.persistence.SnapshotReceipt;
import io.farfrontier.palemirror.frontier.v3.persistence.SnapshotRecord;

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
    private FrontierEngine<P> engine;
    private FrontierV3RuntimeStatus status;
    private SimInstant instant;
    private int ticksSinceCheckpoint;
    private Revision decodedStateRevision;
    private S decodedState;

    private FrontierV3ServerRuntime(
            FrontierEngineConfiguration<S, P> configuration, FrontierStore store, int checkpointIntervalTicks
    ) {
        this.configuration = Objects.requireNonNull(configuration, "configuration")
                .withTransactionCommitter(new FrontierStoreTransactionCommitter(store));
        this.store = Objects.requireNonNull(store, "store");
        if (checkpointIntervalTicks < 1) throw new IllegalArgumentException("checkpoint interval must be positive");
        this.checkpointIntervalTicks = checkpointIntervalTicks;
        try {
            RecoveryImage image = store.recover(configuration.worldId());
            engine = image.checkpoint().isEmpty() && image.walTail().isEmpty()
                    ? FrontierEngines.create(this.configuration)
                    : FrontierEngines.recover(this.configuration, image);
            instant = engine.checkpoint().instant();
            status = FrontierV3RuntimeStatus.active();
        } catch (RuntimeException error) {
            status = FrontierV3RuntimeStatus.quarantined(error);
        }
    }

    static <S, P extends FrontierProjection> FrontierV3ServerRuntime<S, P> start(
            FrontierEngineConfiguration<S, P> configuration, FrontierStore store, int checkpointIntervalTicks
    ) {
        return new FrontierV3ServerRuntime<>(configuration, store, checkpointIntervalTicks);
    }

    FrontierV3RuntimeStatus status() { return status; }

    /**
     * Returns an immutable image of the current canonical revision for a server-thread adapter.
     *
     * <p>The image deliberately exposes bytes rather than mutable domain state. Adapters must
     * decode only the data they need and route every resulting mutation back through
     * {@link #submit(FrontierCommand)}.</p>
     */
    Optional<CheckpointImage> checkpointImage() {
        if (status.kind() != FrontierV3RuntimeStatus.Kind.ACTIVE) return Optional.empty();
        return Optional.of(engine.checkpoint());
    }

    /**
     * Returns the one immutable canonical state object for the current revision on the owning
     * server thread. Adapters may inspect it but must route every mutation through {@link #submit}.
     * The cache is revision-bound, so a successful command or due action is immediately observed
     * on the next read without repeatedly decoding a complete world for each executor.
     */
    Optional<S> decodedState() {
        CheckpointImage image = checkpointImage().orElse(null);
        if (image == null) return Optional.empty();
        if (decodedState == null || !image.revision().equals(decodedStateRevision)) {
            decodedState = Objects.requireNonNull(configuration.stateCodec().decode(image.canonicalState()), "decoded state");
            decodedStateRevision = image.revision();
        }
        return Optional.of(decodedState);
    }

    Optional<CommandResult> submit(FrontierCommand command) {
        Objects.requireNonNull(command, "command");
        if (status.kind() != FrontierV3RuntimeStatus.Kind.ACTIVE) return Optional.empty();
        CommandResult result = engine.submit(command);
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
        Objects.requireNonNull(budget, "budget");
        if (status.kind() != FrontierV3RuntimeStatus.Kind.ACTIVE) return Optional.empty();
        try {
            AdvanceResult result = engine.advanceTo(instant.plus(1L), budget);
            instant = result.instant();
            ticksSinceCheckpoint = Math.addExact(ticksSinceCheckpoint, 1);
            if (result.status().kind() == io.farfrontier.palemirror.frontier.v3.api.EngineStatus.Kind.QUARANTINED) {
                status = new FrontierV3RuntimeStatus(FrontierV3RuntimeStatus.Kind.QUARANTINED, result.status().failureDetail());
                return Optional.of(result);
            }
            if (checkpointWhenDue && ticksSinceCheckpoint >= checkpointIntervalTicks) checkpoint();
            return Optional.of(result);
        } catch (RuntimeException error) {
            quarantine(error);
            return Optional.empty();
        }
    }

    Optional<SnapshotReceipt> checkpoint() {
        if (status.kind() != FrontierV3RuntimeStatus.Kind.ACTIVE) return Optional.empty();
        try {
            CheckpointImage checkpoint = engine.checkpoint();
            RecoveryImage durable = store.recover(configuration.worldId());
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
        if (status.kind() != FrontierV3RuntimeStatus.Kind.ACTIVE) return;
        checkpoint();
        if (status.kind() == FrontierV3RuntimeStatus.Kind.ACTIVE) status = FrontierV3RuntimeStatus.stopped();
    }

    void quarantine(RuntimeException error) {
        status = FrontierV3RuntimeStatus.quarantined(error);
    }
}
