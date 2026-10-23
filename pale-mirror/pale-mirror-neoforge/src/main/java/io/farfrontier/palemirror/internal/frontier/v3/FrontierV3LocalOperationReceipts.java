package io.farfrontier.palemirror.internal.frontier.v3;

import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import java.util.*;

/** Transactional outbox inside an explicitly declared physical owner's journal.
 * The owner adds a receipt in the SAME append as its own completed transition.
 * Neither this protocol nor its recovery callback interprets that transition. */
final class FrontierV3LocalOperationReceipts {
    private final Map<UUID, FrontierV3OperationReceipt> receipts;
    private Map<UUID, FrontierV3OperationReceipt> completing = Map.of();
    private java.util.concurrent.CompletableFuture<Long> ownerWrite, closureWrite;
    FrontierV3LocalOperationReceipts(Map<UUID, FrontierV3OperationReceipt> receipts) {
        validate(receipts); this.receipts = receipts;
    }
    private static void validate(Map<UUID, FrontierV3OperationReceipt> values) {
        if (values.size() > FrontierV3OperationLedger.MAX_NATIVE_RECEIPTS)
            throw new IllegalArgumentException("unbounded local operation confirmations");
        values.forEach((ticket, operation) -> {
            if (!ticket.equals(operation.ticket()) || operation.ownerClosed()
                    || !operation.applied().equals(operation.declaration().participants()))
                throw new IllegalArgumentException("local confirmation has no exact completed operation");
        });
    }
    void add(FrontierV3OperationReceipt operation) {
        validate(Map.of(operation.ticket(), operation));
        var prior = receipts.putIfAbsent(operation.ticket(), operation);
        if (prior != null && !sameOperation(prior, operation))
            throw new IllegalArgumentException("changed local confirmation identity");
    }
    boolean isEmpty() { return receipts.isEmpty(); }
    void complete(ServerLevel level, Runnable forceOwner) {
        if (isEmpty()) return;
        var ledger = FrontierV3OperationLedger.get(level);
        complete(ledger, forceOwner, () -> ledger.persist(level));
    }
    void complete(FrontierV3OperationLedger ledger, Runnable forceOwner, Runnable forceOperations) {
        if (isEmpty()) return;
        forceOwner.run(); // Also persists the family's transition, not merely this outbox.
        close(ledger, receipts.values());
        forceOperations.run();
        receipts.clear();
        forceOwner.run(); // Crash before this append replays closure only, never the physical action.
        clearContinuation();
    }
    /** The same outbox protocol, polled by its owner. Each stage retains its exact
     * prefix: new owner edits cannot postpone or borrow an older acknowledgement. */
    void completeDeferred(FrontierV3OperationLedger ledger,
            java.util.function.Supplier<java.util.concurrent.CompletableFuture<Long>> persistOwner,
            java.util.function.Supplier<java.util.concurrent.CompletableFuture<Long>> persistOperations) {
        if (isEmpty()) { clearContinuation(); return; }
        if (ownerWrite == null) {
            completing = Map.copyOf(receipts); ownerWrite = persistOwner.get();
        }
        if (!ownerWrite.isDone()) return;
        ownerWrite.join();
        if (closureWrite == null) {
            close(ledger, completing.values()); closureWrite = persistOperations.get();
        }
        if (!closureWrite.isDone()) return;
        closureWrite.join();
        completing.forEach((ticket, proof) -> receipts.remove(ticket, proof));
        persistOwner.get(); // Durable closure permits clearing; stale outbox on restart is harmless.
        clearContinuation();
    }
    private void clearContinuation() { completing = Map.of(); ownerWrite = null; closureWrite = null; }
    private static void close(FrontierV3OperationLedger ledger, Collection<FrontierV3OperationReceipt> proofs) {
        for (var proof : proofs) {
            var current = ledger.find(proof.ticket()).orElse(null);
            if (current == null) {
                if (!ledger.prunable(proof.nativeReceipt(proof.declaration().participants().iterator().next())))
                    throw new IllegalStateException("local owner confirmation lost its issued operation");
                continue; // Positive retirement, never absence as evidence.
            }
            if (!sameOperation(current, proof) || ledger.ownerCommand(current.ticket()).isPresent())
                throw new IllegalStateException("local confirmation competes with another operation/owner");
            ledger.closeOwner(current.ticket());
            if (ledger.require(current.ticket()).retirable()) ledger.retire(current.ticket());
        }
    }
    private static boolean sameOperation(FrontierV3OperationReceipt left, FrontierV3OperationReceipt right) {
        return left.ticket().equals(right.ticket()) && left.sequence() == right.sequence()
                && left.declaration().equals(right.declaration());
    }
    ListTag write() {
        var rows = new ListTag();
        receipts.values().stream().sorted(Comparator.comparing(value -> value.ticket().toString()))
                .map(FrontierV3OperationReceiptCodec::write).forEach(rows::add);
        return rows;
    }
    static Map<UUID, FrontierV3OperationReceipt> read(CompoundTag tag, String key) {
        if (!(tag.get(key) instanceof ListTag rows) || !rows.isEmpty() && rows.getElementType() != Tag.TAG_COMPOUND
                || rows.size() > FrontierV3OperationLedger.MAX_NATIVE_RECEIPTS)
            throw new IllegalArgumentException("missing or invalid local operation receipts");
        var values = new LinkedHashMap<UUID, FrontierV3OperationReceipt>();
        for (var row : rows) {
            var value = FrontierV3OperationReceiptCodec.read((CompoundTag) row);
            if (values.putIfAbsent(value.ticket(), value) != null)
                throw new IllegalArgumentException("duplicate local operation receipt");
        }
        validate(values); return values;
    }
}
