package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import net.minecraft.nbt.*;
import java.util.*;

/** One versioned codec shared by the physical journal and same-object native receipts. */
final class FrontierV3OperationReceiptCodec {
    private FrontierV3OperationReceiptCodec() { }
    static CompoundTag write(FrontierV3OperationReceipt value) {
        var tag = new CompoundTag(); var declaration = value.declaration();
        tag.putInt("format", 2); tag.putUUID("ticket", value.ticket());
        tag.putLong("sequence", value.sequence());
        tag.putString("protocol", declaration.protocol().wire()); tag.putString("owner", declaration.owner().value());
        tag.putString("operation", declaration.operation()); tag.putString("fingerprint", declaration.fingerprint());
        tag.put("participants", participants(declaration.participants())); tag.put("applied", participants(value.applied()));
        tag.put("saved", participants(value.saved())); tag.putBoolean("cancelled", value.cancelled());
        tag.putBoolean("closed", value.ownerClosed()); return tag;
    }
    static FrontierV3OperationReceipt read(CompoundTag tag) {
        var declaration = new FrontierV3OperationReceipt.Declaration(FrontierV3OperationReceipt.Protocol.fromWire(tag.getString("protocol")), new SubjectId(tag.getString("owner")),
                tag.getString("operation"), tag.getString("fingerprint"), participants(tag, "participants"));
        var result = new FrontierV3OperationReceipt(declaration, tag.getUUID("ticket"), tag.getLong("sequence"), participants(tag, "applied"),
                participants(tag, "saved"), tag.getBoolean("cancelled"), tag.getBoolean("closed"));
        if (!write(result).equals(tag)) throw new IllegalArgumentException("invalid physical operation encoding");
        return result;
    }
    static CompoundTag nativeReceipt(FrontierV3OperationReceipt.NativeReceipt value) {
        var tag = new CompoundTag(); tag.putInt("format", 1); tag.putUUID("ticket", value.ticket());
        tag.putLong("sequence", value.sequence());
        tag.putString("fingerprint", value.fingerprint()); tag.put("participant", participant(value.participant())); return tag;
    }
    static FrontierV3OperationReceipt.NativeReceipt nativeReceipt(CompoundTag tag) {
        var result = new FrontierV3OperationReceipt.NativeReceipt(tag.getUUID("ticket"), tag.getLong("sequence"), participant(tag.getCompound("participant")), tag.getString("fingerprint"));
        if (!nativeReceipt(result).equals(tag)) throw new IllegalArgumentException("invalid native operation receipt encoding");
        return result;
    }
    static CompoundTag participant(FrontierV3OperationReceipt.Participant value) {
        var tag = new CompoundTag();
        tag.putString("medium", value.medium().name()); tag.putString("identity", value.identity()); tag.putLong("incarnation", value.incarnation()); return tag;
    }
    static FrontierV3OperationReceipt.Participant participant(CompoundTag tag) {
        var result = new FrontierV3OperationReceipt.Participant(FrontierV3OperationReceipt.Medium.valueOf(tag.getString("medium")),
                tag.getString("identity"), tag.getLong("incarnation"));
        if (!participant(result).equals(tag)) throw new IllegalArgumentException("invalid native participant encoding");
        return result;
    }
    private static ListTag participants(Set<FrontierV3OperationReceipt.Participant> values) {
        var result = new ListTag(); values.stream().sorted(Comparator.comparing((FrontierV3OperationReceipt.Participant p) -> p.medium().name())
                .thenComparing(FrontierV3OperationReceipt.Participant::identity).thenComparingLong(FrontierV3OperationReceipt.Participant::incarnation))
                .map(FrontierV3OperationReceiptCodec::participant).forEach(result::add); return result;
    }
    private static Set<FrontierV3OperationReceipt.Participant> participants(CompoundTag tag, String key) {
        var values = tag.getList(key, Tag.TAG_COMPOUND);
        if (!tag.contains(key, Tag.TAG_LIST) || !values.equals(tag.get(key)) || values.size() > 64)
            throw new IllegalArgumentException("invalid native participant list");
        var result = new HashSet<FrontierV3OperationReceipt.Participant>();
        for (int i = 0; i < values.size(); i++) if (!result.add(participant(values.getCompound(i))))
            throw new IllegalArgumentException("duplicate native participant");
        return Set.copyOf(result);
    }
}
