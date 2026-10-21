package io.farfrontier.palemirror.internal.frontier.v3;

import net.minecraft.server.level.ServerLevel;
import java.util.*;

/** Bounded log suppression only. Removing a diagnostic never resolves an operation. */
final class FrontierV3OperationDiagnostics {
    private static final Map<ServerLevel, LinkedHashMap<UUID, FrontierV3MinecraftOperation.Status>> REPORTED = new WeakHashMap<>();
    enum Backpressure { JOURNAL_CAPACITY, NATIVE_RECEIPT_CAPACITY }
    private record WaitKey(FrontierV3OperationReceipt.Protocol protocol, io.farfrontier.palemirror.frontier.v3.api.SubjectId owner, String operation) {
        static WaitKey of(FrontierV3OperationReceipt.Declaration value) { return new WaitKey(value.protocol(), value.owner(), value.operation()); }
    }
    private record Wait(Backpressure reason, long since) { }
    private static final Map<ServerLevel, LinkedHashMap<WaitKey, Wait>> WAITING = new WeakHashMap<>();
    static FrontierV3MinecraftOperation.Result waiting(ServerLevel level, FrontierV3OperationReceipt.Declaration declaration, Backpressure reason) {
        var waits = WAITING.computeIfAbsent(level, ignored -> new LinkedHashMap<>());
        var key = WaitKey.of(declaration); var previous = waits.get(key);
        if (previous == null || previous.reason() != reason) {
            waits.put(key, new Wait(reason, System.nanoTime()));
            io.farfrontier.palemirror.PaleMirrorMod.LOGGER.warn(
                    "PMV3_OPERATION_WAIT protocol={} owner={} operation={} reason={} pendingOperations={}; no physical effect attempted",
                    declaration.protocol(), declaration.owner(), declaration.operation(), reason, FrontierV3OperationLedger.get(level).pendingCount());
            while (waits.size() > 1024) waits.remove(waits.keySet().iterator().next());
        }
        return FrontierV3MinecraftOperation.Result.waiting();
    }
    static FrontierV3MinecraftOperation.Result result(ServerLevel level, FrontierV3OperationReceipt operation,
            FrontierV3MinecraftOperation.Status status) {
        var waits = WAITING.get(level);
        var waited = waits == null ? null : waits.remove(WaitKey.of(operation.declaration()));
        if (waited != null) io.farfrontier.palemirror.PaleMirrorMod.LOGGER.info(
                "PMV3_OPERATION_WAIT_ENDED ticket={} owner={} reason={} observedWaitMs={} result={}", operation.ticket(),
                operation.declaration().owner(), waited.reason(), (System.nanoTime() - waited.since()) / 1_000_000, status);
        var reported = REPORTED.computeIfAbsent(level, ignored -> new LinkedHashMap<>());
        if (status == FrontierV3MinecraftOperation.Status.AMBIGUOUS || status == FrontierV3MinecraftOperation.Status.SOURCE_CHANGED) {
            var previous = reported.put(operation.ticket(), status);
            if (previous != status) io.farfrontier.palemirror.PaleMirrorMod.LOGGER.warn(
                    "PMV3_OPERATION_UNRESOLVED ticket={} protocol={} owner={} operation={} status={} participants={} applied={} saved={} ownerClosed={}; automatic replay refused",
                    operation.ticket(), operation.declaration().protocol(), operation.declaration().owner(), operation.declaration().operation(),
                    status, operation.declaration().participants(), operation.applied(), operation.saved(), operation.ownerClosed());
            while (reported.size() > 2048) reported.remove(reported.keySet().iterator().next());
        } else reported.remove(operation.ticket());
        return new FrontierV3MinecraftOperation.Result(status, operation.ticket());
    }
    private FrontierV3OperationDiagnostics() { }
}
