package io.farfrontier.palemirror.internal.frontier.v3;

import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import java.util.*;

/** Bounded opaque receipt retention. Format adapters, not this collection, prove
 * the exact native object's removal from its serialized owner. */
record FrontierV3NativeRetirementBag(String key, FrontierV3OperationReceipt.Medium medium) {
    FrontierV3NativeRetirementBag { Objects.requireNonNull(key); Objects.requireNonNull(medium); }
    List<FrontierV3OperationReceipt.NativeReceipt> read(CompoundTag bag) {
        if (!bag.contains(key)) return List.of();
        var rows = bag.getList(key, Tag.TAG_COMPOUND);
        if (!bag.contains(key, Tag.TAG_LIST) || !rows.equals(bag.get(key))
                || rows.size() > FrontierV3OperationLedger.MAX_NATIVE_RECEIPTS)
            throw new IllegalStateException("invalid native removal receipts: " + key);
        var result = rows.stream().map(CompoundTag.class::cast).map(FrontierV3OperationReceiptCodec::nativeReceipt).toList();
        if (new HashSet<>(result).size() != result.size() || result.stream().anyMatch(value -> value.participant().medium() != medium))
            throw new IllegalStateException("duplicate or foreign native removal receipt: " + key);
        return result;
    }
    void add(CompoundTag bag, Collection<FrontierV3OperationReceipt.NativeReceipt> incoming) {
        var retained = new LinkedHashSet<>(read(bag)); retained.addAll(incoming);
        if (retained.size() > FrontierV3OperationLedger.MAX_NATIVE_RECEIPTS
                || retained.stream().anyMatch(value -> value.participant().medium() != medium))
            throw new IllegalStateException("native removal obligations exceed their declaration");
        write(bag, retained);
    }
    void prune(ServerLevel level, CompoundTag bag) {
        if (!bag.contains(key) || !FrontierV3ServerLifecycle.acceptsNativeOperationSaves(level)) return;
        var ledger = FrontierV3OperationLedger.get(level);
        if (ledger.isDirty()) throw new IllegalStateException("native removal retirement precedes journal durability");
        write(bag, read(bag).stream().filter(receipt -> !ledger.prunable(receipt)).toList());
    }
    private void write(CompoundTag bag, Collection<FrontierV3OperationReceipt.NativeReceipt> receipts) {
        if (receipts.isEmpty()) { bag.remove(key); return; }
        var rows = new ListTag(); receipts.forEach(receipt -> rows.add(FrontierV3OperationReceiptCodec.nativeReceipt(receipt)));
        bag.put(key, rows);
    }
}
