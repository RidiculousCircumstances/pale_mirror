package io.farfrontier.palemirror.internal.frontier.v3;

import net.minecraft.server.level.ServerLevel;
import java.util.Optional;
import java.util.function.BooleanSupplier;

/** Adopts an existing exact write+sync/read proof; never performs or replays an effect.
 * The producer owns snapshot matching and freshness. The common ledger owns the
 * canonical acknowledgement and retirement, exactly as for a co-saved native token. */
final class FrontierV3DurableNativeObservation {
    private FrontierV3DurableNativeObservation() { }
    static Optional<FrontierV3OperationReceipt> record(ServerLevel level,
            FrontierV3OperationReceipt.Declaration declaration, BooleanSupplier currentSavedEvidence) {
        if (!level.getServer().isSameThread()) throw new IllegalStateException("durable native observation outside server thread");
        return record(FrontierV3OperationLedger.get(level), declaration, currentSavedEvidence);
    }
    static Optional<FrontierV3OperationReceipt> record(FrontierV3OperationLedger ledger,
            FrontierV3OperationReceipt.Declaration declaration, BooleanSupplier currentSavedEvidence) {
        if (declaration.participants().stream().anyMatch(p -> p.medium() != FrontierV3OperationReceipt.Medium.ENTITY_SNAPSHOT))
            throw new IllegalArgumentException("durable observation requires an explicit saved-snapshot participant");
        if (!currentSavedEvidence.getAsBoolean() || !ledger.canPrepare(declaration)) return Optional.empty();
        var operation = ledger.prepare(declaration);
        for (var participant : declaration.participants()) ledger.saved(operation.nativeReceipt(participant));
        return Optional.of(ledger.require(operation.ticket()));
    }
}
