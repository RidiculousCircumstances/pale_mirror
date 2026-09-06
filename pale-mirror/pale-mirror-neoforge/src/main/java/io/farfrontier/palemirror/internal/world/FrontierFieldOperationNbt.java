package io.farfrontier.palemirror.internal.world;

import io.farfrontier.palemirror.frontier.FrontierFieldOperation;
import io.farfrontier.palemirror.frontier.FrontierPoint;
import io.farfrontier.palemirror.frontier.FrontierStateHydration;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

/** NBT boundary for individual defensive field responses. */
final class FrontierFieldOperationNbt {
    private FrontierFieldOperationNbt() { }

    static void write(CompoundTag tag, Iterable<FrontierFieldOperation> values) {
        ListTag operations = new ListTag();
        for (FrontierFieldOperation value : values) {
            CompoundTag operation = new CompoundTag();
            operation.putString("id", value.id());
            operation.putString("settlement", value.settlementId());
            operation.putString("assault", value.targetAssaultId());
            operation.putString("kind", value.kind().name());
            operation.putString("state", value.state().name());
            operation.putInt("x", value.position().x());
            operation.putInt("z", value.position().z());
            ListTag participants = new ListTag();
            value.participantIds().forEach(id -> participants.add(StringTag.valueOf(id)));
            operation.put("participants", participants);
            operation.putLong("startedDay", value.startedDay());
            operation.putInt("transitDays", value.transitDays());
            operation.putInt("transitProgress", value.transitProgress());
            operation.putInt("stationDays", value.stationDays());
            operation.putLong("finishedDay", value.finishedDay());
            operation.putLong("revision", value.revision());
            operations.add(operation);
        }
        tag.put("fieldOperations", operations);
    }

    static List<FrontierStateHydration.FieldOperationState> read(CompoundTag tag) {
        if (!tag.contains("fieldOperations", Tag.TAG_LIST)) throw new IllegalStateException("incomplete Frontier field-operation ledger");
        List<FrontierStateHydration.FieldOperationState> values = new ArrayList<>();
        for (Tag value : tag.getList("fieldOperations", Tag.TAG_COMPOUND)) {
            CompoundTag operation = (CompoundTag) value;
            try {
                require(operation, "id", Tag.TAG_STRING); require(operation, "settlement", Tag.TAG_STRING);
                require(operation, "assault", Tag.TAG_STRING); require(operation, "kind", Tag.TAG_STRING);
                require(operation, "state", Tag.TAG_STRING); require(operation, "x", Tag.TAG_INT); require(operation, "z", Tag.TAG_INT);
                require(operation, "participants", Tag.TAG_LIST); require(operation, "startedDay", Tag.TAG_LONG);
                require(operation, "transitDays", Tag.TAG_INT); require(operation, "transitProgress", Tag.TAG_INT);
                require(operation, "stationDays", Tag.TAG_INT); require(operation, "finishedDay", Tag.TAG_LONG);
                require(operation, "revision", Tag.TAG_LONG);
                values.add(new FrontierStateHydration.FieldOperationState(operation.getString("id"),
                        operation.getString("settlement"), operation.getString("assault"),
                        FrontierFieldOperation.Kind.valueOf(operation.getString("kind")),
                        FrontierFieldOperation.State.valueOf(operation.getString("state")),
                        new FrontierPoint(operation.getInt("x"), operation.getInt("z")),
                        operation.getList("participants", Tag.TAG_STRING).stream().map(Tag::getAsString).toList(),
                        operation.getLong("startedDay"), operation.getInt("transitDays"), operation.getInt("transitProgress"),
                        operation.getInt("stationDays"), operation.getLong("finishedDay"), operation.getLong("revision")));
            } catch (IllegalArgumentException invalid) {
                throw new IllegalStateException("invalid Frontier field operation", invalid);
            }
        }
        return List.copyOf(values);
    }

    private static void require(CompoundTag value, String key, int type) {
        if (!value.contains(key, type)) throw new IllegalArgumentException("missing field operation " + key);
    }
}
