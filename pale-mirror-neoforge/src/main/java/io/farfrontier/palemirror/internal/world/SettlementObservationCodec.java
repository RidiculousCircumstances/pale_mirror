package io.farfrontier.palemirror.internal.world;

import java.util.LinkedHashMap;
import java.util.Map;

import io.farfrontier.palemirror.domain.WorldObjectId;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** Codec for bounded, read-only physical settlement evidence. */
final class SettlementObservationCodec {
    private SettlementObservationCodec() { }

    static void write(CompoundTag tag, Map<WorldObjectId, SettlementObservationRecord> records) {
        ListTag values = new ListTag();
        records.values().forEach(record -> {
            CompoundTag value = new CompoundTag();
            value.putString("id", record.id().value());
            value.putString("dimension", record.dimensionId());
            value.putLong("anchor", record.anchor().asLong());
            value.putLong("min", record.minBounds().asLong());
            value.putLong("max", record.maxBounds().asLong());
            value.putString("provenance", record.provenance());
            value.putInt("population", record.observedPopulation());
            value.putInt("guards", record.observedGuards());
            value.putLong("lastObserved", record.lastObservedGameTime());
            value.putInt("residentDeaths", record.observedResidentDeaths());
            value.putInt("guardDeaths", record.observedGuardDeaths());
            values.add(value);
        });
        tag.put("settlementObservations", values);
    }

    static Map<WorldObjectId, SettlementObservationRecord> read(CompoundTag tag) {
        Map<WorldObjectId, SettlementObservationRecord> result = new LinkedHashMap<>();
        for (Tag element : tag.getList("settlementObservations", Tag.TAG_COMPOUND)) {
            CompoundTag value = (CompoundTag) element;
            WorldObjectId id = new WorldObjectId(value.getString("id"));
            result.put(id, new SettlementObservationRecord(id, value.getString("dimension"),
                    BlockPos.of(value.getLong("anchor")), BlockPos.of(value.getLong("min")),
                    BlockPos.of(value.getLong("max")), value.getInt("population"), value.getInt("guards"),
                    value.getLong("lastObserved"), value.getString("provenance"),
                    value.getInt("residentDeaths"), value.getInt("guardDeaths")));
        }
        return result;
    }
}
