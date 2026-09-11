package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.InventoryCustody;
import io.farfrontier.palemirror.frontier.v3.model.CustodyAccount;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalStackAddress;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalStackBinding;
import io.farfrontier.palemirror.frontier.v3.model.ResourceCustody;
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
    private static final int FORMAT = 2, MAX_PENDING_CARRIERS = 4_096, MAX_ITEMS_PER_CARRIER = 54;
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
        Fungible fungible = fungible(state, carrierId);
        if ((items == null || items.isEmpty()) && fungible == null) throw new IllegalStateException("cargo impact has no released carrier custody");
        if (items != null && !items.isEmpty() && fungible != null) throw new IllegalStateException("cargo impact mixes exact and fungible carrier custody");
        Pending observed = new Pending(carrierId, entity.blockPosition().asLong(), gameTime, items == null ? List.of() : items, Optional.ofNullable(fungible));
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
                .filter(value -> value.capturedAtGameTime() < gameTime)
                .findFirst().map(value -> new Ready(value.carrierId(), value.position(),
                        value.itemIds().isEmpty() ? Optional.empty() : Optional.of(value.itemIds().getFirst()), value.fungible()));
    }

    void resolve(Ready ready) {
        Pending value = pending.get(ready.carrierId());
        if (value == null || value.position() != ready.position() || !value.fungible().equals(ready.fungible())
                || value.itemIds().isEmpty() != ready.itemId().isEmpty()
                || ready.itemId().isPresent() && !value.itemIds().getFirst().equals(ready.itemId().orElseThrow())) {
            throw new IllegalStateException("cargo carrier impact resolution is not the retained queue head");
        }
        if (ready.fungible().isPresent()) { pending.remove(value.carrierId()); setDirty(); return; }
        List<SubjectId> remaining = value.itemIds().subList(1, value.itemIds().size());
        if (remaining.isEmpty()) pending.remove(value.carrierId());
        else pending.put(value.carrierId(), new Pending(value.carrierId(), value.position(), value.capturedAtGameTime(), remaining, Optional.empty()));
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

    record Ready(UUID carrierId, long position, Optional<SubjectId> itemId, Optional<Fungible> fungible) { }

    record Pending(UUID carrierId, long position, long capturedAtGameTime, List<SubjectId> itemIds, Optional<Fungible> fungible) {
        Pending {
            Objects.requireNonNull(carrierId, "carrier id"); Objects.requireNonNull(itemIds, "item ids"); Objects.requireNonNull(fungible, "fungible impact");
            if (capturedAtGameTime < 0L || itemIds.size() > MAX_ITEMS_PER_CARRIER || itemIds.isEmpty() == fungible.isEmpty()) {
                throw new IllegalArgumentException("invalid v3 cargo carrier impact");
            }
            itemIds = List.copyOf(itemIds);
            if (itemIds.stream().distinct().count() != itemIds.size()) throw new IllegalArgumentException("duplicate exact cargo impact item");
        }
        CompoundTag save() {
            CompoundTag tag = new CompoundTag(); tag.putUUID("carrier", carrierId); tag.putLong("pos", position); tag.putLong("capturedAt", capturedAtGameTime);
            ListTag values = new ListTag(); itemIds.forEach(id -> { CompoundTag item = new CompoundTag(); item.putString("item", id.value()); values.add(item); });
            tag.put("items", values); fungible.ifPresent(value -> value.save(tag)); return tag;
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
            Optional<Fungible> fungible = tag.contains("fungibleAccount", Tag.TAG_STRING) ? Optional.of(Fungible.load(tag)) : Optional.empty();
            try { return new Pending(tag.getUUID("carrier"), tag.getLong("pos"), tag.getLong("capturedAt"), items, fungible); }
            catch (IllegalArgumentException invalid) { throw new IllegalStateException("invalid cargo carrier impact", invalid); }
        }
    }

    record Fungible(SubjectId accountId, SubjectId bindingId, long authorityEpoch, String itemKind, int quantity) {
        Fungible {
            Objects.requireNonNull(accountId, "fungible account"); Objects.requireNonNull(bindingId, "fungible binding"); Objects.requireNonNull(itemKind, "fungible kind");
            if (authorityEpoch < 1L || quantity < 1 || quantity > 64) throw new IllegalArgumentException("invalid fungible cargo impact");
        }
        void save(CompoundTag tag) { tag.putString("fungibleAccount", accountId.value()); tag.putString("fungibleBinding", bindingId.value());
            tag.putLong("fungibleEpoch", authorityEpoch); tag.putString("fungibleKind", itemKind); tag.putInt("fungibleQuantity", quantity); }
        static Fungible load(CompoundTag tag) {
            if (!tag.contains("fungibleBinding", Tag.TAG_STRING) || !tag.contains("fungibleEpoch", Tag.TAG_LONG)
                    || !tag.contains("fungibleKind", Tag.TAG_STRING) || !tag.contains("fungibleQuantity", Tag.TAG_INT)) throw new IllegalStateException("incomplete fungible cargo impact");
            return new Fungible(new SubjectId(tag.getString("fungibleAccount")), new SubjectId(tag.getString("fungibleBinding")),
                    tag.getLong("fungibleEpoch"), tag.getString("fungibleKind"), tag.getInt("fungibleQuantity"));
        }
    }

    private static Fungible fungible(FrontierWorldState state, UUID carrierId) {
        List<CustodyAccount> accounts = state.inventory().fungibleResources().accounts().values().stream()
                .filter(account -> account.custody().equals(new ResourceCustody.WorldCarrier(carrierId))).toList();
        if (accounts.size() != 1) return null;
        List<PhysicalStackBinding> bindings = state.inventory().fungibleResources().bindings().values().stream()
                .filter(binding -> binding.accountId().equals(accounts.getFirst().id())).toList();
        if (bindings.size() != 1 || !(bindings.getFirst().address() instanceof PhysicalStackAddress.WorldEntity address)
                || !address.entityId().equals(carrierId)) return null;
        PhysicalStackBinding binding = bindings.getFirst();
        return new Fungible(accounts.getFirst().id(), binding.id(), binding.authorityEpoch(), binding.itemKind(), binding.quantity());
    }
}
