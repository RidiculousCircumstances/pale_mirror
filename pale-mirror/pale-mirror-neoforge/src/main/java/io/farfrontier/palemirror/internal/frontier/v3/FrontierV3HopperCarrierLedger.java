package io.farfrontier.palemirror.internal.frontier.v3;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Durable one-to-one provenance for hopper WorldCarrier identities. */
final class FrontierV3HopperCarrierLedger extends SavedData {
    private static final String NAME = "pale_mirror_frontier_v3_hopper_carriers";
    private static final int FORMAT = 1;
    static final int MAX_CARRIERS = 4_096;
    private final Map<UUID, Long> positionsByCarrier;
    private final Map<Long, UUID> carriersByPosition;

    private FrontierV3HopperCarrierLedger() { this(new HashMap<>()); }
    private FrontierV3HopperCarrierLedger(Map<UUID, Long> positionsByCarrier) {
        this.positionsByCarrier = positionsByCarrier; carriersByPosition = new HashMap<>();
        positionsByCarrier.forEach((carrier, position) -> {
            if (carriersByPosition.put(position, carrier) != null) throw new IllegalStateException("duplicate hopper carrier position");
        });
    }

    static FrontierV3HopperCarrierLedger get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(FrontierV3HopperCarrierLedger::new,
                FrontierV3HopperCarrierLedger::load, DataFixTypes.SAVED_DATA_COMMAND_STORAGE), NAME);
    }

    /** Claims only a stable one-to-one physical hopper address; false is terminal conflict evidence. */
    boolean claim(UUID carrierId, BlockPos position) {
        Long priorPosition = positionsByCarrier.get(carrierId); UUID priorCarrier = carriersByPosition.get(position.asLong());
        if ((priorPosition != null && priorPosition.longValue() != position.asLong()) || (priorCarrier != null && !priorCarrier.equals(carrierId))) return false;
        if (priorPosition != null) return true;
        if (positionsByCarrier.size() >= MAX_CARRIERS) throw new IllegalStateException("v3 hopper carrier retention limit exceeded");
        positionsByCarrier.put(carrierId, position.asLong()); carriersByPosition.put(position.asLong(), carrierId); setDirty(); return true;
    }

    static FrontierV3HopperCarrierLedger load(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.getInt("format") != FORMAT) throw new IllegalStateException("incompatible v3 hopper carrier ledger");
        ListTag entries = tag.getList("carriers", Tag.TAG_COMPOUND);
        if (entries.size() > MAX_CARRIERS) throw new IllegalStateException("v3 hopper carrier retention limit exceeded");
        Map<UUID, Long> values = new HashMap<>();
        for (Tag raw : entries) {
            CompoundTag entry = (CompoundTag) raw;
            if (!entry.hasUUID("id") || !entry.contains("pos", Tag.TAG_LONG)) throw new IllegalStateException("incomplete hopper carrier claim");
            if (values.put(entry.getUUID("id"), entry.getLong("pos")) != null) throw new IllegalStateException("duplicate hopper carrier identity");
        }
        return new FrontierV3HopperCarrierLedger(values);
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("format", FORMAT); ListTag entries = new ListTag();
        positionsByCarrier.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            CompoundTag value = new CompoundTag(); value.putUUID("id", entry.getKey()); value.putLong("pos", entry.getValue()); entries.add(value);
        });
        tag.put("carriers", entries); return tag;
    }
}
