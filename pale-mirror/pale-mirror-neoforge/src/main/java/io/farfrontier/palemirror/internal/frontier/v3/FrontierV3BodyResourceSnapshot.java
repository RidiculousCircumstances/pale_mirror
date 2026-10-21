package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import java.io.*;
import java.util.*;

/** Exact pre-loot native evidence, not an inventory backup or permission to restore
 * a stack. Retained in the fatality owner's common operation outbox. */
final class FrontierV3BodyResourceSnapshot {
    private final CompoundTag image;
    private FrontierV3BodyResourceSnapshot(CompoundTag image) {
        this.image = image.copy();
        if (image.getInt("format") != 2 || !image.contains("slots", Tag.TAG_COMPOUND)
                || !image.contains("attached", Tag.TAG_COMPOUND) || image.getAllKeys().size() != 4)
            throw new IllegalArgumentException("invalid pre-loot body resource image");
        var known = UnitInventoryPresentation.slots().stream().map(FrontierV3BodyResourceSnapshot::key).collect(java.util.stream.Collectors.toSet());
        if (!known.containsAll(image.getCompound("slots").getAllKeys())) throw new IllegalArgumentException("unknown pre-loot body slot");
        effects(); // Validate the closed typed consequence envelope on hydration.
    }
    static FrontierV3BodyResourceSnapshot capture(ServerLevel level, FrontierWorldState state, Mob body) {
        return capture(level, state, body, List.of());
    }
    static FrontierV3BodyResourceSnapshot capture(ServerLevel level, FrontierWorldState state, Mob body,
            List<FrontierV3OperationAftermath> effects) {
        var slots = new CompoundTag();
        for (var slot : FrontierV3ActorResourceSlots.supportedSlots(body))
            slots.put(key(slot), FrontierV3ActorResourceSlots.get(body, slot).saveOptional(level.registryAccess()));
        var image = new CompoundTag(); image.putInt("format", 2); image.put("slots", slots);
        image.put("attached", FrontierV3AttachedStorageDeathResources.capture(level, state, body));
        var plans = new ListTag(); effects.forEach(effect -> plans.add(effect.save())); image.put("effects", plans);
        return new FrontierV3BodyResourceSnapshot(image);
    }
    List<FrontierV3OperationAftermath> effects() {
        var tags = image.getList("effects", Tag.TAG_COMPOUND);
        if (!image.contains("effects", Tag.TAG_LIST) || !tags.equals(image.get("effects")) || tags.size() > 64)
            throw new IllegalArgumentException("invalid or unbounded pre-loot effect plan");
        var result = new ArrayList<FrontierV3OperationAftermath>();
        for (int i = 0; i < tags.size(); i++) result.add(FrontierV3OperationAftermath.load(tags.getCompound(i)));
        if (result.stream().map(effect -> effect.child().ticket()).distinct().count() != result.size())
            throw new IllegalArgumentException("competing pre-loot child outcomes");
        return List.copyOf(result);
    }
    void retain(FrontierV3OperationLedger ledger, UUID parent) { effects().forEach(effect -> effect.retain(ledger, parent)); }
    void settle(ServerLevel level, FrontierV3ServerRuntime<?, ?> runtime, UUID parent) {
        for (var effect : effects()) effect.settle(level, runtime, parent);
    }
    Map<ActorItemSlot, ItemStack> slots(ServerLevel level) {
        var result = new LinkedHashMap<ActorItemSlot, ItemStack>(); var slots = image.getCompound("slots");
        for (var slot : UnitInventoryPresentation.slots()) if (slots.contains(key(slot))) {
            if (!slots.contains(key(slot), Tag.TAG_COMPOUND)) throw new IllegalArgumentException("invalid pre-loot stack image");
            var tag = slots.getCompound(key(slot));
            result.put(slot, tag.isEmpty() ? ItemStack.EMPTY : ItemStack.parse(level.registryAccess(), tag).orElseThrow(
                    () -> new IllegalArgumentException("unreadable pre-loot stack image")));
        }
        return Collections.unmodifiableMap(result);
    }
    CompoundTag attached() { return image.getCompound("attached").copy(); }
    byte[] encode() {
        try { return FrontierV3JournalNbt.encode(image); }
        catch (IOException failure) { throw new UncheckedIOException(failure); }
    }
    static FrontierV3BodyResourceSnapshot decode(byte[] bytes) {
        try { return new FrontierV3BodyResourceSnapshot(FrontierV3JournalNbt.decode(bytes)); }
        catch (IOException failure) { throw new UncheckedIOException(failure); }
    }
    private static String key(ActorItemSlot slot) {
        return switch (slot) {
            case ActorItemSlot.Hand hand -> switch (hand.hand()) { case MAIN -> "main"; case OFF -> "off"; };
            case ActorItemSlot.Pocket pocket -> "pocket-" + pocket.index();
            case ActorItemSlot.AttachedStorage ignored -> throw new IllegalArgumentException("attached storage owns its snapshot");
        };
    }
}
