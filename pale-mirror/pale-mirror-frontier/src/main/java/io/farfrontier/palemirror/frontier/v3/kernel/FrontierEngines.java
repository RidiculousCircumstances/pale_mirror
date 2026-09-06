package io.farfrontier.palemirror.frontier.v3.kernel;

import io.farfrontier.palemirror.frontier.v3.api.CommandReceipt;
import io.farfrontier.palemirror.frontier.v3.api.FrontierCanonicalStateAccess;
import io.farfrontier.palemirror.frontier.v3.api.FrontierEngine;
import io.farfrontier.palemirror.frontier.v3.api.FrontierProjection;
import io.farfrontier.palemirror.frontier.v3.api.Revision;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.persistence.RecoveryImage;
import io.farfrontier.palemirror.frontier.v3.persistence.SnapshotRecord;

import java.util.List;
import java.util.Objects;

/** Public pure factory; recovery never falls back to a new world after durable facts exist. */
public final class FrontierEngines {
    private FrontierEngines() { }

    public static <S, P extends FrontierProjection> FrontierEngine<P> create(FrontierEngineConfiguration<S, P> configuration) {
        return createCanonicalStateAccess(configuration);
    }

    /**
     * Creates the exact immutable-state adapter view used by an owning physical runtime.
     * Persistence remains explicit through {@link FrontierEngine#checkpoint()}.
     */
    public static <S, P extends FrontierProjection> FrontierCanonicalStateAccess<S, P> createCanonicalStateAccess(
            FrontierEngineConfiguration<S, P> configuration
    ) {
        Objects.requireNonNull(configuration, "configuration");
        return new InMemoryFrontierEngine<>(configuration.worldId(), configuration.initialState(), configuration.initialInstant(),
                configuration.commandPlanner(), configuration.scheduledPlanner(), configuration.reducer(), configuration.stateCodec(),
                configuration.projectionMapper(), configuration.limits(), configuration.initialSchedules(), configuration.transactionCommitter(),
                configuration.stateValidator(), configuration.executionMetrics());
    }

    public static <S, P extends FrontierProjection> FrontierEngine<P> recover(
            FrontierEngineConfiguration<S, P> configuration, RecoveryImage image
    ) {
        return recoverCanonicalStateAccess(configuration, image);
    }

    /** Restores the exact immutable-state adapter view from already-verified durable facts. */
    public static <S, P extends FrontierProjection> FrontierCanonicalStateAccess<S, P> recoverCanonicalStateAccess(
            FrontierEngineConfiguration<S, P> configuration, RecoveryImage image
    ) {
        Objects.requireNonNull(configuration, "configuration");
        Objects.requireNonNull(image, "recovery image");
        if (!configuration.worldId().equals(image.worldId())) throw new IllegalArgumentException("recovery image world does not match engine configuration");
        if (image.walTail().size() > configuration.limits().maxTransactions()) {
            throw new IllegalStateException("recovery WAL tail exceeds bounded engine transaction retention");
        }
        SnapshotRecord snapshot = image.checkpoint().orElse(null);
        S state = snapshot == null ? configuration.initialState() : configuration.stateCodec().decode(snapshot.checkpoint().canonicalState());
        Revision revision = snapshot == null ? Revision.ZERO : snapshot.checkpoint().revision();
        SimInstant instant = snapshot == null ? configuration.initialInstant() : snapshot.checkpoint().instant();
        List<ScheduledAction> schedules = snapshot == null ? configuration.initialSchedules() : snapshot.checkpoint().schedules();
        List<CommandReceipt> receipts = new java.util.ArrayList<>(snapshot == null ? List.of() : snapshot.checkpoint().receipts());
        image.walTail().stream().map(TransactionRecord::acceptedCommandReceipt).flatMap(java.util.Optional::stream).forEach(receipts::add);
        if (receipts.stream().map(CommandReceipt::commandId).distinct().count() != receipts.size()) {
            throw new IllegalArgumentException("recovery contains duplicate command receipts");
        }
        if (receipts.size() > configuration.limits().maxReceipts()) throw new IllegalStateException("recovery checkpoint exceeds bounded receipt retention");

        TransactionReplayer.ReplayResult<S> replay = TransactionReplayer.replayFrom(configuration.worldId(), state, revision, instant,
                schedules, image.walTail(), configuration.reducer(), configuration.stateCodec(), configuration.stateValidator());
        return InMemoryFrontierEngine.recovered(configuration, replay, receipts, image.walTail());
    }
}
