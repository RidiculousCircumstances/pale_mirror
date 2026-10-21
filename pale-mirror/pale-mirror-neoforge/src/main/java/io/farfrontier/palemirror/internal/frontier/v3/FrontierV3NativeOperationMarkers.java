package io.farfrontier.palemirror.internal.frontier.v3;

import net.minecraft.nbt.*;
import java.util.*;

/** Opaque same-object receipts. Neither item counts nor a nearby matching block create one. */
final class FrontierV3NativeOperationMarkers {
    private static final String KEY = "pm_v3_operation_receipts";
    static final String LIFETIME = "pm_v3_operation_lifetime";
    private static final int MAX_RECEIPTS = 4096;
    private record Key(UUID ticket, FrontierV3OperationReceipt.Participant participant) { }
    private FrontierV3NativeOperationMarkers() { }
    static long lifetime(CompoundTag persistent) {
        if (!persistent.contains(LIFETIME)) {
            long value;
            do { value = UUID.randomUUID().getLeastSignificantBits() & Long.MAX_VALUE; } while (value == 0);
            persistent.putLong(LIFETIME, value);
        }
        if (!persistent.contains(LIFETIME, Tag.TAG_LONG) || persistent.getLong(LIFETIME) < 1)
            throw new IllegalStateException("invalid native operation lifetime");
        return persistent.getLong(LIFETIME);
    }
    static List<FrontierV3OperationReceipt.NativeReceipt> read(CompoundTag persistent) {
        if (!persistent.contains(KEY)) return List.of();
        var rows = persistent.getList(KEY, Tag.TAG_COMPOUND);
        if (!persistent.contains(KEY, Tag.TAG_LIST) || !rows.equals(persistent.get(KEY)) || rows.size() > MAX_RECEIPTS)
            throw new IllegalArgumentException("invalid native operation receipt list");
        var values = new LinkedHashMap<Key, FrontierV3OperationReceipt.NativeReceipt>();
        for (int i = 0; i < rows.size(); i++) {
            var value = FrontierV3OperationReceiptCodec.nativeReceipt(rows.getCompound(i));
            if (values.putIfAbsent(new Key(value.ticket(), value.participant()), value) != null) throw new IllegalArgumentException("duplicate native operation marker");
        }
        return List.copyOf(values.values());
    }
    static boolean contains(CompoundTag persistent, FrontierV3OperationReceipt.NativeReceipt receipt) {
        for (var value : read(persistent)) if (value.ticket().equals(receipt.ticket()) && value.participant().equals(receipt.participant())) {
            if (!value.equals(receipt)) throw new IllegalStateException("native operation marker changed binding");
            return true;
        }
        return false;
    }
    static boolean canStamp(CompoundTag persistent, int additionalReceipts) {
        if (additionalReceipts < 0) throw new IllegalArgumentException("negative native receipt reservation");
        return read(persistent).size() <= MAX_RECEIPTS - additionalReceipts;
    }
    static void stamp(CompoundTag persistent, FrontierV3OperationReceipt.NativeReceipt receipt) {
        if (contains(persistent, receipt)) return;
        var values = new ArrayList<>(read(persistent));
        if (values.size() >= MAX_RECEIPTS) throw new IllegalStateException("native operation receipts await durability; refusing evidence eviction");
        values.add(receipt); write(persistent, values);
    }
    /** Only a positive DURABLE retirement fence can erase a marker. Absence is not proof. */
    static boolean prune(CompoundTag persistent, FrontierV3OperationLedger ledger) {
        if (ledger.isDirty()) throw new IllegalStateException("cannot prune native markers before journal acknowledgement");
        var values = read(persistent); var retained = values.stream().filter(value -> !ledger.prunable(value)).toList();
        if (retained.size() != values.size()) write(persistent, retained);
        return retained.size() != values.size();
    }
    private static void write(CompoundTag persistent, List<FrontierV3OperationReceipt.NativeReceipt> values) {
        if (values.isEmpty()) { persistent.remove(KEY); return; }
        var rows = new ListTag(); values.stream().map(FrontierV3OperationReceiptCodec::nativeReceipt).forEach(rows::add);
        persistent.put(KEY, rows);
    }
}
