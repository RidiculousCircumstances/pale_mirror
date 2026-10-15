package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.geometry.NavigationBlockState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import java.util.TreeMap;

/** Registry translation only. No extraction, navigation or mutation policy. */
final class FrontierV3BlockStates {
    private FrontierV3BlockStates() { }
    static NavigationBlockState describe(BlockState state) {
        var properties = new TreeMap<String, String>();
        state.getProperties().forEach(property -> properties.put(property.getName(), name(state, property)));
        return new NavigationBlockState(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(), properties);
    }
    static BlockState resolve(NavigationBlockState descriptor) {
        var id = ResourceLocation.parse(descriptor.kind());
        if (!BuiltInRegistries.BLOCK.containsKey(id)) throw new IllegalArgumentException("unknown block declaration");
        var state = BuiltInRegistries.BLOCK.get(id).defaultBlockState();
        for (var entry : descriptor.properties().entrySet()) {
            var property = state.getBlock().getStateDefinition().getProperty(entry.getKey());
            if (property == null) throw new IllegalArgumentException("unknown block property");
            state = set(state, property, entry.getValue());
        }
        if (!describe(state).equals(descriptor)) throw new IllegalArgumentException("incomplete block declaration");
        return state;
    }
    private static <T extends Comparable<T>> String name(BlockState state, Property<T> property) {
        return property.getName(state.getValue(property));
    }
    private static <T extends Comparable<T>> BlockState set(BlockState state, Property<T> property, String value) {
        return state.setValue(property, property.getValue(value).orElseThrow(
                () -> new IllegalArgumentException("invalid block property value")));
    }
}
