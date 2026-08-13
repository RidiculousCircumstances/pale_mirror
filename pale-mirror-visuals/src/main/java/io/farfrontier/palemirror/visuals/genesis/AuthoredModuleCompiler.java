package io.farfrontier.palemirror.visuals.genesis;

import io.farfrontier.palemirror.api.VisualBlockPlacement;
import io.farfrontier.palemirror.api.VisualModulePlacement;
import io.farfrontier.palemirror.api.VisualModuleSnapshot;
import io.farfrontier.palemirror.api.VisualPoint;
import io.farfrontier.palemirror.visuals.PaleMirrorVisualsMod;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/** Single sanitizer/compiler used by fresh-world genesis and later staged construction. */
public final class AuthoredModuleCompiler {
    private AuthoredModuleCompiler() { }

    public static VisualModuleSnapshot compile(VisualModulePlacement module) {
        ResourceLocation id = ResourceLocation.parse(module.templateId());
        String path = "data/" + id.getNamespace() + "/structure/" + id.getPath() + ".nbt";
        try (InputStream input = PaleMirrorVisualsMod.class.getClassLoader().getResourceAsStream(path)) {
            if (input == null) throw new IllegalStateException("Required authored module is missing: " + id);
            CompoundTag root = NbtIo.readCompressed(input, NbtAccounter.unlimitedHeap());
            ListTag size = root.getList("size", Tag.TAG_INT);
            int sx = size.getInt(0); int sy = size.getInt(1); int sz = size.getInt(2);
            if (sx > 48 || sy > 48 || sz > 48) throw new IllegalStateException("Module exceeds 48 blocks: " + id);
            ListTag palette = root.getList("palette", Tag.TAG_COMPOUND);
            List<BlockState> states = new ArrayList<>(palette.size());
            for (Tag value : palette) states.add(readState((CompoundTag) value));
            int turns = Math.floorMod(module.quarterTurns(), 4);
            int rx = turns % 2 == 0 ? sx : sz; int rz = turns % 2 == 0 ? sz : sx;
            BlockPos origin = new BlockPos(module.origin().x() - rx / 2, module.origin().y() + 1,
                    module.origin().z() - rz / 2);
            List<VisualBlockPlacement> blocks = new ArrayList<>();
            for (Tag value : root.getList("blocks", Tag.TAG_COMPOUND)) {
                CompoundTag block = (CompoundTag) value;
                ListTag pos = block.getList("pos", Tag.TAG_INT);
                int[] rotated = rotate(pos.getInt(0), pos.getInt(2), sx, sz, turns);
                BlockState state = rotate(states.get(block.getInt("state")), turns);
                if (state.is(Blocks.STRUCTURE_BLOCK) || state.is(Blocks.JIGSAW)) continue;
                BlockPos world = origin.offset(rotated[0], pos.getInt(1), rotated[1]);
                VisualPoint position = new VisualPoint(world.getX(), world.getY(), world.getZ());
                if (!module.footprint().contains(position)) {
                    throw new IllegalStateException("Authored module escaped its declared footprint: " + id
                            + " at " + position + " outside " + module.footprint());
                }
                blocks.add(new VisualBlockPlacement(position, state));
            }
            return new VisualModuleSnapshot(module.templateId(), module.footprint(), blocks);
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot read authored module " + id, failure);
        }
    }

    static BlockState readState(CompoundTag value) {
        ResourceLocation id = ResourceLocation.parse(value.getString("Name"));
        String path = id.getPath();
        if (path.contains("spawner") || path.equals("jigsaw") || path.equals("structure_block")
                || path.equals("tnt") || path.contains("chest") || path.equals("barrel")
                || path.equals("dispenser") || path.contains("ore") || path.startsWith("raw_")
                || path.contains("crushed_raw")) return replacement(id);
        Block block = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getOptional(id)
                .orElse(Blocks.CUT_COPPER);
        BlockState state = block.defaultBlockState();
        if (!value.contains("Properties", Tag.TAG_COMPOUND)) return state;
        CompoundTag properties = value.getCompound("Properties");
        for (String name : properties.getAllKeys()) {
            Property<?> property = block.getStateDefinition().getProperty(name);
            if (property != null) state = setValue(state, property, properties.getString(name));
        }
        return state;
    }

    private static BlockState replacement(ResourceLocation id) {
        String path = id.getPath();
        if (path.contains("spawner") || path.equals("tnt") || path.equals("jigsaw")
                || path.equals("structure_block")) return Blocks.AIR.defaultBlockState();
        if (path.contains("ore") || path.startsWith("raw_") || path.contains("crushed_raw")) {
            return Blocks.DEEPSLATE.defaultBlockState();
        }
        return id.getNamespace().equals("create") ? Blocks.WAXED_EXPOSED_COPPER.defaultBlockState()
                : Blocks.SPRUCE_PLANKS.defaultBlockState();
    }

    private static <T extends Comparable<T>> BlockState setValue(BlockState state, Property<T> property, String value) {
        return property.getValue(value).map(parsed -> state.setValue(property, parsed)).orElse(state);
    }

    private static int[] rotate(int x, int z, int sx, int sz, int turns) {
        return switch (turns) {
            case 1 -> new int[]{sz - 1 - z, x};
            case 2 -> new int[]{sx - 1 - x, sz - 1 - z};
            case 3 -> new int[]{z, sx - 1 - x};
            default -> new int[]{x, z};
        };
    }

    private static BlockState rotate(BlockState state, int turns) {
        for (int i = 0; i < turns; i++) state = state.rotate(net.minecraft.world.level.block.Rotation.CLOCKWISE_90);
        return state;
    }
}
