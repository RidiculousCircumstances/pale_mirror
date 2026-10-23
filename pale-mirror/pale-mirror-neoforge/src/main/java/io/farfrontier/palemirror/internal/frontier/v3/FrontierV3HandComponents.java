package io.farfrontier.palemirror.internal.frontier.v3;

import java.util.*;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

/** Exact supported component patch, shared by live hand capture and native saved evidence.
 * Missing entries mean native item defaults, not inferred canonical condition. */
final class FrontierV3HandComponents {
    private static final Set<String> KEYS = Set.of("minecraft:damage", "minecraft:max_damage", "minecraft:max_stack_size");
    // Offline save inspection must not initialize native registries.
    private static final class Live {
        private static final Map<DataComponentType<Integer>, String> TYPES = Map.of(
            DataComponents.DAMAGE, "minecraft:damage", DataComponents.MAX_DAMAGE, "minecraft:max_damage",
            DataComponents.MAX_STACK_SIZE, "minecraft:max_stack_size");
    }
    static Set<String> keys() { return KEYS; }
    static Map<String, Integer> validate(Map<String, Integer> values) {
        values = Map.copyOf(values);
        if (!keys().containsAll(values.keySet()) || values.entrySet().stream().anyMatch(e ->
                e.getValue() < (e.getKey().equals("minecraft:damage") ? 0 : 1)
                || e.getKey().equals("minecraft:max_stack_size") && e.getValue() > 99))
            throw new IllegalArgumentException("unsupported hand condition patch");
        return values;
    }
    static Map<String, Integer> patch(ItemStack stack) {
        var result = new TreeMap<String, Integer>();
        Live.TYPES.forEach((type, key) -> {
            var value = stack.getComponentsPatch().get(type);
            if (value != null) result.put(key, value.orElseThrow(() -> new IllegalArgumentException("removed hand condition component")));
        });
        return validate(result);
    }
    static void copy(ItemStack source, ItemStack target) {
        var values = patch(source);
        Live.TYPES.forEach((type, key) -> { if (values.containsKey(key)) target.set(type, values.get(key)); });
    }
    static void write(CompoundTag tag, Map<String, Integer> values) {
        if (values.isEmpty()) return;
        var patch = new CompoundTag(); values.forEach(patch::putInt); tag.put("integerComponents", patch);
    }
    static Map<String, Integer> read(CompoundTag tag) {
        if (!tag.contains("integerComponents")) return Map.of();
        if (!tag.contains("integerComponents", Tag.TAG_COMPOUND)) throw new IllegalArgumentException("invalid hand component patch");
        var patch = tag.getCompound("integerComponents"); var values = new TreeMap<String, Integer>();
        for (var key : patch.getAllKeys()) {
            if (!patch.contains(key, Tag.TAG_INT)) throw new IllegalArgumentException("invalid hand component value");
            values.put(key, patch.getInt(key));
        }
        return validate(values);
    }
    private FrontierV3HandComponents() { }
}
