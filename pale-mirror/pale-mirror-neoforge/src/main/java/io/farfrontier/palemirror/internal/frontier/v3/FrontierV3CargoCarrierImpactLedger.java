package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.InventoryCustody;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.MinecartChest;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Durable pre-impact evidence for an externally caused effect that can destroy a released HOT
 * cargo cart. This ledger owns no canonical inventory state: it retains the old cart identity and
 * its exact stacks only until a later loaded-chunk postcondition can prove a surviving cart, one
 * exact drop/player transfer, or destruction. It never force-loads or recreates a cart.
 */
final class FrontierV3CargoCarrierImpactLedger extends SavedData {
    private static final String NAME = "pale_mirror_frontier_v3_cargo_carrier_impacts";
    private static final int FORMAT = 1, MAX_PENDING_CARRIERS = 4_096, MAX_ITEMS_PER_CARRIER = 54;
    private final LinkedHashMap<UUID, Pending> pending;

    private FrontierV3CargoCarrierImpactLedger() { this(new LinkedHashMap<>()); }
    private FrontierV3CargoCarrierImpactLedger(LinkedHashMap<UUID, Pending> pending) { this.pending = pending; }

    static FrontierV3CargoCarrierImpactLedger get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(FrontierV3CargoCarrierImpactLedger::new,
                FrontierV3CargoCarrierImpactLedger::load, DataFixTypes.SAVED_DATA_COMMAND_STORAGE), NAME);
    }

    /**
     * Retains every exact stack before vanilla resolves the external effect. Repeated observation
     * of the same carrier is idempotent only when its original physical anchor and item set match.
     */
    void capture(long gameTime, Entity entity, FrontierWorldState state) {
        Objects.requireNonNull(entity, "carrier entity"); Objects.requireNonNull(state, "world state");
        UUID carrierId = entity.getUUID(); List<SubjectId> items = state.inventory().worldCarrierItems().get(carrierId);
        if (items == null || items.isEmpty()) throw new IllegalStateException("cargo impact has no exact released carrier custody");
        Pending observed = new Pending(carrierId, entity.blockPosition().asLong(), gameTime, items);
        Pending existing = pending.get(carrierId);
        if (existing != null) {
            if (!existing.equals(observed)) throw new IllegalStateException("cargo carrier impact identity changed before reconciliation");
            return;
        }
        if (pending.size() >= MAX_PENDING_CARRIERS) throw new IllegalStateException("v3 cargo carrier impact retention exceeded");
        pending.put(carrierId, observed); setDirty();
    }

    Optional<Ready> nextReady(long gameTime) {
        return pending.values().stream().sorted(Comparator.comparing(Pending::carrierId))
                .filter(value -> value.capturedAtGameTime() < gameTime).filter(value -> !value.itemIds().isEmpty())
                .findFirst().map(value -> new Ready(value.carrierId(), value.position(), value.itemIds().getFirst()));
    }

    void resolve(Ready ready) {
        Pending value = pending.get(ready.carrierId());
        if (value == null || value.position() != ready.position() || value.itemIds().isEmpty() || !value.itemIds().getFirst().equals(ready.itemId())) {
            throw new IllegalStateException("cargo carrier impact resolution is not the retained queue head");
        }
        List<SubjectId> remaining = value.itemIds().subList(1, value.itemIds().size());
        if (remaining.isEmpty()) pending.remove(value.carrierId());
        else pending.put(value.carrierId(), new Pending(value.carrierId(), value.position(), value.capturedAtGameTime(), remaining));
        setDirty();
    }

    static FrontierV3CargoCarrierImpactLedger load(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.getInt("format") != FORMAT) throw new IllegalStateException("incompatible v3 cargo carrier impact ledger");
        ListTag values = tag.getList("pending", Tag.TAG_COMPOUND);
        if (values.size() > MAX_PENDING_CARRIERS) throw new IllegalStateException("v3 cargo carrier impact retention exceeded");
        LinkedHashMap<UUID, Pending> restored = new LinkedHashMap<>();
        for (Tag raw : values) {
            Pending value = Pending.load((CompoundTag) raw);
            if (restored.putIfAbsent(value.carrierId(), value) != null) throw new IllegalStateException("duplicate cargo carrier impact identity");
        }
        return new FrontierV3CargoCarrierImpactLedger(restored);
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("format", FORMAT); ListTag values = new ListTag();
        pending.values().stream().sorted(Comparator.comparing(Pending::carrierId)).forEach(value -> values.add(value.save()));
        tag.put("pending", values); return tag;
    }

    record Ready(UUID carrierId, long position, SubjectId itemId) { }

    record Pending(UUID carrierId, long position, long capturedAtGameTime, List<SubjectId> itemIds) {
        Pending {
            Objects.requireNonNull(carrierId, "carrier id"); Objects.requireNonNull(itemIds, "item ids");
            if (capturedAtGameTime < 0L || itemIds.isEmpty() || itemIds.size() > MAX_ITEMS_PER_CARRIER) {
                throw new IllegalArgumentException("invalid v3 cargo carrier impact");
            }
            itemIds = List.copyOf(itemIds);
            if (itemIds.stream().distinct().count() != itemIds.size()) throw new IllegalArgumentException("duplicate exact cargo impact item");
        }
        CompoundTag save() {
            CompoundTag tag = new CompoundTag(); tag.putUUID("carrier", carrierId); tag.putLong("pos", position); tag.putLong("capturedAt", capturedAtGameTime);
            ListTag values = new ListTag(); itemIds.forEach(id -> { CompoundTag item = new CompoundTag(); item.putString("item", id.value()); values.add(item); });
            tag.put("items", values); return tag;
        }
        static Pending load(CompoundTag tag) {
            if (!tag.hasUUID("carrier") || !tag.contains("pos", Tag.TAG_LONG) || !tag.contains("capturedAt", Tag.TAG_LONG) || !tag.contains("items", Tag.TAG_LIST)) {
                throw new IllegalStateException("incomplete cargo carrier impact");
            }
            ListTag values = tag.getList("items", Tag.TAG_COMPOUND); ArrayList<SubjectId> items = new ArrayList<>(values.size());
            for (Tag raw : values) {
                CompoundTag item = (CompoundTag) raw;
                if (!item.contains("item", Tag.TAG_STRING)) throw new IllegalStateException("incomplete cargo carrier impact item");
                items.add(new SubjectId(item.getString("item")));
            }
            try { return new Pending(tag.getUUID("carrier"), tag.getLong("pos"), tag.getLong("capturedAt"), items); }
            catch (IllegalArgumentException invalid) { throw new IllegalStateException("invalid cargo carrier impact", invalid); }
        }
    }
}
