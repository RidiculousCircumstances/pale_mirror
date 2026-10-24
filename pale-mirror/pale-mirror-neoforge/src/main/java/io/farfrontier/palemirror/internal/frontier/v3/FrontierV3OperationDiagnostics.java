package io.farfrontier.palemirror.internal.frontier.v3;

import net.minecraft.server.level.ServerLevel;
import java.util.*;

/** Bounded log suppression only. Removing a diagnostic never resolves an operation. */
final class FrontierV3OperationDiagnostics {
    private static final Map<ServerLevel, LinkedHashMap<UUID, FrontierV3MinecraftOperation.Status>> REPORTED = new WeakHashMap<>();
    enum Reason { OWNER_RECONCILIATION, DECLARATION_CHANGED, PRECONDITION_CHANGED,
        NO_LIVE_ATTEMPT_PERMISSION, EFFECT_OUTCOME_UNKNOWN, EFFECT_EXCEPTION, REFUSED_BEFORE_EFFECT, REFUSAL_NOT_SEALED, POSTCONDITION_MISMATCH }
    enum Backpressure { JOURNAL_CAPACITY, NATIVE_RECEIPT_CAPACITY, NATIVE_RECOVERY_EVIDENCE, OWNER_AFTERMATH, EFFECT_PREPARATION }
    private record WaitKey(FrontierV3OperationReceipt.Protocol protocol, io.farfrontier.palemirror.frontier.v3.api.SubjectId owner, String operation) {
        static WaitKey of(FrontierV3OperationReceipt.Declaration value) { return new WaitKey(value.protocol(), value.owner(), value.operation()); }
    }
    private record Wait(Backpressure reason, String detail, long since) { }
    private static final Map<ServerLevel, LinkedHashMap<WaitKey, Wait>> WAITING = new WeakHashMap<>();
    static FrontierV3MinecraftOperation.Result waiting(ServerLevel level, FrontierV3OperationReceipt.Declaration declaration, Backpressure reason) {
        return waiting(level, declaration, reason, "");
    }
    static FrontierV3MinecraftOperation.Result waiting(ServerLevel level, FrontierV3OperationReceipt.Declaration declaration,
            Backpressure reason, String detail) {
        var waits = WAITING.computeIfAbsent(level, ignored -> new LinkedHashMap<>());
        var key = WaitKey.of(declaration); var previous = waits.get(key);
        if (previous == null || previous.reason() != reason || !previous.detail().equals(detail)) {
            waits.put(key, new Wait(reason, detail, previous == null ? System.nanoTime() : previous.since()));
            io.farfrontier.palemirror.PaleMirrorMod.LOGGER.warn(
                    "PMV3_OPERATION_WAIT protocol={} owner={} operation={} reason={} detail={} pendingOperations={}; unresolved obligation retained",
                    declaration.protocol(), declaration.owner(), declaration.operation(), reason, detail, FrontierV3OperationLedger.get(level).pendingCount());
            while (waits.size() > 1024) waits.remove(waits.keySet().iterator().next());
        }
        return reason == Backpressure.EFFECT_PREPARATION ? FrontierV3MinecraftOperation.Result.preparing()
                : FrontierV3MinecraftOperation.Result.waiting();
    }
    static FrontierV3MinecraftOperation.Result result(ServerLevel level, FrontierV3OperationReceipt operation,
            FrontierV3MinecraftOperation.Status status) {
        return result(level, operation, status, Reason.OWNER_RECONCILIATION);
    }
    static FrontierV3MinecraftOperation.Result result(ServerLevel level, FrontierV3OperationReceipt operation,
            FrontierV3MinecraftOperation.Status status, Reason reason) {
        var waits = WAITING.get(level);
        var waited = waits == null ? null : waits.remove(WaitKey.of(operation.declaration()));
        if (waited != null) io.farfrontier.palemirror.PaleMirrorMod.LOGGER.info(
                "PMV3_OPERATION_WAIT_ENDED ticket={} owner={} reason={} observedWaitMs={} result={}", operation.ticket(),
                operation.declaration().owner(), waited.reason(), (System.nanoTime() - waited.since()) / 1_000_000, status);
        var reported = REPORTED.computeIfAbsent(level, ignored -> new LinkedHashMap<>());
        if (status == FrontierV3MinecraftOperation.Status.AMBIGUOUS || status == FrontierV3MinecraftOperation.Status.SOURCE_CHANGED) {
            var previous = reported.put(operation.ticket(), status);
            if (previous != status) io.farfrontier.palemirror.PaleMirrorMod.LOGGER.warn(
                    "PMV3_OPERATION_UNRESOLVED ticket={} protocol={} owner={} operation={} status={} reason={} participants={} applied={} saved={} ownerClosed={}; automatic replay refused",
                    operation.ticket(), operation.declaration().protocol(), operation.declaration().owner(), operation.declaration().operation(),
                    status, reason, operation.declaration().participants(), operation.applied(), operation.saved(), operation.ownerClosed());
            while (reported.size() > 2048) reported.remove(reported.keySet().iterator().next());
        } else reported.remove(operation.ticket());
        return new FrontierV3MinecraftOperation.Result(status, operation.ticket());
    }
    private FrontierV3OperationDiagnostics() { }
}
