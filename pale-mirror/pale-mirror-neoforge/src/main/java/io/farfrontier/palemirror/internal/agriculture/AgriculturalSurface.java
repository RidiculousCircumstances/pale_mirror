package io.farfrontier.palemirror.internal.agriculture;

import io.farfrontier.palemirror.frontier.v3.model.agriculture.GrayboxAgriculture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.block.state.BlockState;

/** Native decoding of the same authored surface facts consumed by unloaded land discovery. */
final class AgriculturalSurface {
    private AgriculturalSurface() { }
    static BlockState at(int x, int z) {
        return at(GrayboxAgriculture.rules(), x, z);
    }
    static BlockState at(io.farfrontier.palemirror.frontier.v3.model.agriculture.TerritoryAgriculture agriculture, int x, int z) {
        return STATES.computeIfAbsent(agriculture.surfaceAt(x, z).orElseThrow(), fact -> {
            var tag = new CompoundTag(); tag.putString("Name", fact.kind());
            var properties = new CompoundTag(); fact.properties().forEach(properties::putString);
            tag.put("Properties", properties);
            return NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), tag);
        });
    }
    private static final java.util.Map<io.farfrontier.palemirror.frontier.v3.model.geometry.NavigationBlockState, BlockState> STATES =
            new java.util.concurrent.ConcurrentHashMap<>();
}
