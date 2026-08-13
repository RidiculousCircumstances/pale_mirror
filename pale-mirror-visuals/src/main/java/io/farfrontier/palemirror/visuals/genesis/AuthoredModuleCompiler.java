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
                if (surfaceMachinery(id) && naturalEnclosure(state)) continue;
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

    /** Authored, bounded overlay selection shared by damage and pristine reconstruction. */
    public static VisualModuleSnapshot compileState(VisualModulePlacement module, String state) {
        VisualModuleSnapshot baseline = compile(module);
        if (state.equals("INTACT")) return baseline;
        if (!state.equals("DAMAGED") && !state.equals("RUINED")) {
            throw new IllegalArgumentException("Unsupported authored module state " + state);
        }
        int budget = state.equals("RUINED") ? 48 : 24;
        List<VisualBlockPlacement> candidates = baseline.blocks().stream()
                .filter(value -> stateCell(module, value))
                .sorted(java.util.Comparator.comparingInt((VisualBlockPlacement value) -> -value.position().y())
                        .thenComparingInt(value -> value.position().x())
                        .thenComparingInt(value -> value.position().z()))
                .limit(budget).map(value -> new VisualBlockPlacement(value.position(),
                        damagedState(value.state(), value.position(), state))).toList();
        if (candidates.isEmpty()) throw new IllegalStateException("Authored state has no semantic cells: "
                + module.instanceId());
        return new VisualModuleSnapshot(module.templateId() + "#" + state.toLowerCase(java.util.Locale.ROOT),
                module.footprint(), candidates);
    }

    private static boolean stateCell(VisualModulePlacement module, VisualBlockPlacement value) {
        VisualPoint position = value.position();
        VisualPoint min = module.footprint().min(); VisualPoint max = module.footprint().max();
        boolean perimeter = position.x() <= min.x() + 1 || position.x() >= max.x() - 1
                || position.z() <= min.z() + 1 || position.z() >= max.z() - 1;
        boolean upper = position.y() >= min.y() + Math.max(2, (max.y() - min.y()) * 2 / 3);
        int cadence = module.visualStateProfile().equals("frontier_defence") ? 3
                : module.visualStateProfile().equals("frontier_freight") ? 4 : 5;
        return (perimeter || upper) && Math.floorMod(module.instanceId().hashCode()
                + position.x() * 31 + position.y() * 17 + position.z(), cadence) == 0;
    }

    private static BlockState damagedState(BlockState baseline, VisualPoint position, String state) {
        int roll = Math.floorMod(position.x() * 31 + position.y() * 17 + position.z(), 11);
        if (state.equals("RUINED") && roll <= 3) return Blocks.AIR.defaultBlockState();
        if (roll == 4) return Blocks.COBWEB.defaultBlockState();
        if (baseline.is(Blocks.STONE_BRICKS)) return Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
        if (baseline.is(Blocks.COBBLESTONE)) return Blocks.MOSSY_COBBLESTONE.defaultBlockState();
        if (baseline.is(net.minecraft.tags.BlockTags.PLANKS) && state.equals("RUINED")) {
            return Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState();
        }
        return baseline;
    }

    private static boolean surfaceMachinery(ResourceLocation id) {
        return id.getNamespace().equals(PaleMirrorVisualsMod.MOD_ID)
                && id.getPath().endsWith("/mine/dispatch_machinery");
    }

    private static boolean naturalEnclosure(BlockState state) {
        return state.is(Blocks.STONE) || state.is(Blocks.DEEPSLATE) || state.is(Blocks.COBBLED_DEEPSLATE)
                || state.is(Blocks.TUFF) || state.is(Blocks.CALCITE) || state.is(Blocks.DRIPSTONE_BLOCK)
                || state.is(Blocks.DIRT) || state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.GRAVEL)
                || state.is(Blocks.ANDESITE) || state.is(Blocks.DIORITE) || state.is(Blocks.GRANITE);
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
