package com.vibecraft.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.Property;

/** Reads actual vanilla and modded blocks, including legal state property values. */
public final class BlockRegistry {
    private BlockRegistry() {}
    public static JsonObject describe() {
        JsonArray blocks = new JsonArray();
        for (Block block : BuiltInRegistries.BLOCK) {
            JsonObject entry = new JsonObject();
            entry.addProperty("name", BuiltInRegistries.BLOCK.getKey(block).toString());
            entry.addProperty("displayName", block.getName().getString());
            JsonObject properties = new JsonObject();
            for (Property<?> property : block.getStateDefinition().getProperties()) {
                properties.add(property.getName(), values(property));
            }
            entry.add("properties", properties);
            blocks.add(entry);
        }
        JsonObject result = new JsonObject();
        result.add("blocks", blocks);
        return result;
    }
    private static <T extends Comparable<T>> JsonArray values(Property<T> property) {
        JsonArray values = new JsonArray();
        for (T value : property.getPossibleValues()) values.add(property.getName(value));
        return values;
    }
}
