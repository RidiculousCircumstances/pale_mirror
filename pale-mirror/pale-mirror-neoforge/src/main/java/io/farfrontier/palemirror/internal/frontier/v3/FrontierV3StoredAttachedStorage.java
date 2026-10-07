package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import net.minecraft.nbt.*;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.horse.Donkey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import java.util.*;

/** Bounded physical inventory evidence, compared with the actual serialized entity save. No stock authority. */
record FrontierV3StoredAttachedStorage(SubjectId containerId, String provenance, List<ReferenceContainerCustody.ObservedSlot> slots) {
    FrontierV3StoredAttachedStorage {
        Objects.requireNonNull(containerId); Objects.requireNonNull(provenance); slots = List.copyOf(slots);
        if (provenance.isBlank() || provenance.length() > 512 || slots.size() != 15)
            throw new IllegalArgumentException("incomplete native pack inventory evidence");
        for (int i = 0; i < slots.size(); i++) if (slots.get(i).slot() != i || !slots.get(i).empty() && !slots.get(i).fungible())
            throw new IllegalArgumentException("unclassified native pack inventory evidence");
    }
    String fingerprint(FrontierWorldState state) { return ReferenceContainerCustody.observedFingerprint(state, containerId, slots); }

    static Optional<FrontierV3StoredAttachedStorage> capture(FrontierWorldState state, SubjectId actor, Mob body) {
        var asset = state.transportFleet().assets().get(actor);
        if (asset == null) return Optional.empty();
        if (!(body instanceof Donkey donkey) || !donkey.hasChest() || donkey.getInventory().getContainerSize() != asset.stackSlots() + 1)
            return Optional.empty();
        var metadata = donkey.getPersistentData();
        if (!asset.containerId().value().equals(metadata.getString(FrontierV3ExactItemPresentation.CONTAINER_ID_KEY))) return Optional.empty();
        var slots = new ArrayList<ReferenceContainerCustody.ObservedSlot>();
        for (int i = 0; i < asset.stackSlots(); i++) {
            var stack = donkey.getInventory().getItem(i + 1);
            if (!stack.isEmpty() && !ItemStack.isSameItemSameComponents(stack, new ItemStack(stack.getItem(), stack.getCount()))) return Optional.empty();
            slots.add(stack.isEmpty() ? ReferenceContainerCustody.ObservedSlot.empty(i) : ReferenceContainerCustody.ObservedSlot.fungible(i,
                    BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), stack.getCount()));
        }
        String provenance = metadata.getString(FrontierV3ReferenceContainerCustodyExecutor.REPLICA_PROVENANCE_KEY);
        if (provenance.isBlank()) return Optional.empty();
        return Optional.of(new FrontierV3StoredAttachedStorage(asset.containerId(), provenance, slots));
    }

    static Optional<FrontierV3StoredAttachedStorage> fromEntitySave(CompoundTag entity) {
        if (!entity.getString("id").equals("minecraft:donkey") || !entity.getBoolean("ChestedHorse")
                || !entity.contains("NeoForgeData", Tag.TAG_COMPOUND)) return Optional.empty();
        var metadata = entity.getCompound("NeoForgeData");
        String container = metadata.getString(FrontierV3ExactItemPresentation.CONTAINER_ID_KEY);
        String provenance = metadata.getString(FrontierV3ReferenceContainerCustodyExecutor.REPLICA_PROVENANCE_KEY);
        if (container.isBlank() || provenance.isBlank()) return Optional.empty();
        var raw = entity.getList("Items", Tag.TAG_COMPOUND);
        if (entity.contains("Items") && !raw.equals(entity.get("Items")) || raw.size() > 15) return Optional.empty();
        var slots = new ArrayList<ReferenceContainerCustody.ObservedSlot>();
        for (int slot = 0; slot < 15; slot++) slots.add(ReferenceContainerCustody.ObservedSlot.empty(slot));
        var used = new HashSet<Integer>();
        try {
            for (Tag entry : raw) {
                var stack = (CompoundTag) entry;
                // Vanilla writes cargo index i - 1: saved Slot already excludes the saddle.
                int slot = Byte.toUnsignedInt(stack.getByte("Slot"));
                if (!stack.contains("Slot", Tag.TAG_BYTE) || !stack.contains("id", Tag.TAG_STRING) || !stack.contains("count", Tag.TAG_INT)
                        || slot < 0 || slot >= 15 || !used.add(slot) || stack.contains("components")
                            && (!stack.contains("components", Tag.TAG_COMPOUND) || !stack.getCompound("components").isEmpty())) return Optional.empty();
                slots.set(slot, ReferenceContainerCustody.ObservedSlot.fungible(slot, stack.getString("id"), stack.getInt("count")));
            }
            return Optional.of(new FrontierV3StoredAttachedStorage(new SubjectId(container), provenance, slots));
        } catch (IllegalArgumentException malformed) { return Optional.empty(); }
    }

    CompoundTag save() {
        var tag = new CompoundTag(); tag.putString("container", containerId.value()); tag.putString("provenance", provenance);
        var rows = new ListTag();
        for (var slot : slots) if (!slot.empty()) {
            var row = new CompoundTag(); row.putByte("slot", (byte) slot.slot()); row.putString("kind", slot.itemKind()); row.putInt("count", slot.count()); rows.add(row);
        }
        tag.put("slots", rows); return tag;
    }
    static FrontierV3StoredAttachedStorage load(CompoundTag tag) {
        var slots = new ArrayList<ReferenceContainerCustody.ObservedSlot>();
        for (int i = 0; i < 15; i++) slots.add(ReferenceContainerCustody.ObservedSlot.empty(i));
        var raw = tag.getList("slots", Tag.TAG_COMPOUND); var used = new HashSet<Integer>();
        if (!raw.equals(tag.get("slots")) || raw.size() > 15) throw new IllegalArgumentException("invalid saved pack slot evidence");
        for (Tag entry : raw) {
            var row = (CompoundTag) entry; int slot = Byte.toUnsignedInt(row.getByte("slot"));
            if (slot >= 15 || !used.add(slot)) throw new IllegalArgumentException("invalid saved pack slot address");
            slots.set(slot, ReferenceContainerCustody.ObservedSlot.fungible(slot, row.getString("kind"), row.getInt("count")));
        }
        return new FrontierV3StoredAttachedStorage(new SubjectId(tag.getString("container")), tag.getString("provenance"), slots);
    }
}
