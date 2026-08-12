package io.farfrontier.palemirror.visuals.genesis;

import io.farfrontier.palemirror.api.AuthoredRegionSeed;
import io.farfrontier.palemirror.api.VisualModulePlacement;
import io.farfrontier.palemirror.api.VisualPoint;
import io.farfrontier.palemirror.visuals.PaleMirrorVisualsMod;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.RailShape;

/** Compiles global manifests once into independent chunk-local worldgen slices. */
public final class FrontierGenesisCompiler {
    public static final int CATALOG_VERSION = 5;
    static final int SETTLEMENT_VEGETATION_HALO = 6;

    public CompiledGenesisCatalog compile(List<AuthoredRegionSeed> manifests) {
        Map<Long, MutableSlice> slices = new LinkedHashMap<>();
        manifests.stream().sorted(Comparator.comparing(AuthoredRegionSeed::planId))
                .forEach(seed -> compileRegion(seed, slices));
        String catalogHash = sha256(CATALOG_VERSION + ":" + manifests.stream()
                .map(value -> value.planId() + "=" + value.contentHash()).sorted().toList());
        Map<Long, CompiledChunkSlice> compiled = new LinkedHashMap<>();
        slices.forEach((key, value) -> compiled.put(key, value.freeze(catalogHash)));
        return new CompiledGenesisCatalog(CATALOG_VERSION, catalogHash, manifests, compiled);
    }

    private static void compileRegion(AuthoredRegionSeed seed, Map<Long, MutableSlice> slices) {
        FrontierPalette palette = FrontierPalette.forClimate(
                FrontierClimate.valueOf(seed.climate().toUpperCase(Locale.ROOT)));
        compileSettlement(seed, palette, slices);
        for (VisualModulePlacement module : seed.modules()) compileModule(module, slices);
        compileMine(seed.primaryMine(), slices);
        compileMine(seed.alternateMine(), slices);
        compileRail(seed.baselineRailNodes(), slices);
    }

    private static void compileSettlement(AuthoredRegionSeed seed, FrontierPalette palette,
                                          Map<Long, MutableSlice> slices) {
        int y = seed.anchor().y();
        int radius = FrontierRegionPlanner.SETTLEMENT_RADIUS;
        int cleanupRadius = radius + SETTLEMENT_VEGETATION_HALO;
        for (int x = seed.anchor().x() - cleanupRadius; x <= seed.anchor().x() + cleanupRadius; x++) {
            for (int z = seed.anchor().z() - cleanupRadius; z <= seed.anchor().z() + cleanupRadius; z++) {
                int dx = x - seed.anchor().x();
                int dz = z - seed.anchor().z();
                int distanceSquared = dx * dx + dz * dz;
                if (distanceSquared > cleanupRadius * cleanupRadius) continue;
                MutableSlice slice = slice(slices, x, z);
                slice.vegetation.putIfAbsent(ChunkPos.asLong(x, z),
                        new CompiledChunkSlice.VegetationColumn(x, z, y));
                if (distanceSquared > radius * radius) continue;
                slice.terrain.add(new CompiledChunkSlice.TerrainColumn(x, z, y,
                        Blocks.GRASS_BLOCK.defaultBlockState(), palette.foundation()));
                double distance = Math.sqrt(dx * dx + dz * dz);
                boolean road = Math.abs(dx) <= 2 || Math.abs(dz) <= 2 || distance >= 28 && distance <= 32;
                if (road) slice.blocks.put(new BlockPos(x, y, z), (Math.floorMod(x + z, 5) == 0
                        ? Blocks.COARSE_DIRT : Blocks.DIRT_PATH).defaultBlockState());
                int gateDx = Integer.signum(seed.freightGate().x() - seed.anchor().x());
                int gateDz = Integer.signum(seed.freightGate().z() - seed.anchor().z());
                boolean opening = Math.abs(dx - gateDx * 82) <= 5 && Math.abs(dz - gateDz * 82) <= 5;
                if (distance >= 81.4 && distance <= 82.6 && !opening) {
                    for (int h = 1; h <= 5; h++) put(slices, new BlockPos(x, y + h, z), palette.log());
                }
            }
        }
        VisualPoint gate = seed.freightGate();
        for (int dx = -4; dx <= 4; dx += 8) for (int h = 1; h <= 7; h++)
            put(slices, new BlockPos(gate.x() + dx, gate.y() + h, gate.z()), palette.log());
        for (int dx = -4; dx <= 4; dx++)
            put(slices, new BlockPos(gate.x() + dx, gate.y() + 7, gate.z()), palette.log());
    }

    private static void compileModule(VisualModulePlacement module, Map<Long, MutableSlice> slices) {
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
            for (Tag value : root.getList("blocks", Tag.TAG_COMPOUND)) {
                CompoundTag block = (CompoundTag) value;
                ListTag pos = block.getList("pos", Tag.TAG_INT);
                int[] rotated = rotate(pos.getInt(0), pos.getInt(2), sx, sz, turns);
                BlockState state = rotate(states.get(block.getInt("state")), turns);
                if (state.is(Blocks.STRUCTURE_BLOCK) || state.is(Blocks.JIGSAW)) continue;
                put(slices, origin.offset(rotated[0], pos.getInt(1), rotated[1]), state);
            }
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot read authored module " + id, failure);
        }
    }

    private static BlockState readState(CompoundTag value) {
        Block block = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(
                ResourceLocation.parse(value.getString("Name")));
        BlockState state = block.defaultBlockState();
        if (!value.contains("Properties", Tag.TAG_COMPOUND)) return state;
        CompoundTag properties = value.getCompound("Properties");
        for (String name : properties.getAllKeys()) {
            Property<?> property = block.getStateDefinition().getProperty(name);
            if (property != null) state = setValue(state, property, properties.getString(name));
        }
        return state;
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

    private static void compileMine(VisualPoint point, Map<Long, MutableSlice> slices) {
        BlockPos surface = new BlockPos(point.x(), point.y(), point.z());
        for (int x = -7; x <= 7; x++) for (int z = -6; z <= 1; z++) {
            put(slices, surface.offset(x, -1, z), Blocks.GRAVEL.defaultBlockState());
            for (int y = 0; y <= 4; y++) put(slices, surface.offset(x, y, z), Blocks.AIR.defaultBlockState());
        }
        for (int x : List.of(-3, 3)) for (int y = 0; y <= 4; y++)
            put(slices, surface.offset(x, y, 2), Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState());
        for (int x = -3; x <= 3; x++) put(slices, surface.offset(x, 4, 2), Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState());
        put(slices, surface.offset(-2, 3, 2), Blocks.LANTERN.defaultBlockState());
        put(slices, surface.offset(2, 3, 2), Blocks.LANTERN.defaultBlockState());
        for (int x = -5; x <= 5; x++) put(slices, surface.offset(x, 0, -3), Blocks.SMOOTH_STONE.defaultBlockState());
        for (int z = 2; z <= 72; z++) {
            int drop = Math.min(18, z / 4); BlockPos floor = surface.offset(0, -drop, z);
            for (int x = -2; x <= 2; x++) put(slices, floor.offset(x, -1, 0), Blocks.DEEPSLATE_BRICKS.defaultBlockState());
            for (int x = -1; x <= 1; x++) for (int y = 0; y <= 3; y++) put(slices, floor.offset(x, y, 0), Blocks.AIR.defaultBlockState());
            if (z % 8 == 0) {
                for (int y = 0; y <= 3; y++) {
                    put(slices, floor.offset(-2, y, 0), Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState());
                    put(slices, floor.offset(2, y, 0), Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState());
                }
                for (int x = -2; x <= 2; x++) put(slices, floor.offset(x, 3, 0), Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState());
                put(slices, floor.above(2), Blocks.LANTERN.defaultBlockState());
            }
        }
        BlockPos chamber = surface.offset(0, -18, 72);
        for (int x = -6; x <= 6; x++) for (int y = -1; y <= 6; y++) for (int z = -6; z <= 6; z++) {
            boolean shell = Math.abs(x) == 6 || Math.abs(z) == 6 || y == -1 || y == 6;
            put(slices, chamber.offset(x, y, z), shell ? Blocks.DEEPSLATE_BRICKS.defaultBlockState()
                    : Blocks.AIR.defaultBlockState());
        }
        put(slices, chamber.above(), Blocks.SOUL_LANTERN.defaultBlockState());
    }

    private static void compileRail(List<VisualPoint> nodes, Map<Long, MutableSlice> slices) {
        for (int index = 0; index < nodes.size(); index++) {
            VisualPoint point = nodes.get(index); BlockPos rail = new BlockPos(point.x(), point.y(), point.z());
            Direction previous = direction(nodes, Math.max(0, index - 1), index == 0 ? 1 : index);
            Direction next = direction(nodes, index == nodes.size() - 1 ? index - 1 : index,
                    index == nodes.size() - 1 ? index : index + 1);
            int nextY = nodes.get(Math.min(nodes.size() - 1, index + 1)).y();
            int previousY = nodes.get(Math.max(0, index - 1)).y();
            RailShape shape = railShape(previous, next, point.y(), nextY, previousY);
            boolean powered = index > 0 && index % 12 == 0 && supportsPoweredRail(shape);
            BlockState state = powered ? Blocks.POWERED_RAIL.defaultBlockState()
                    .setValue(PoweredRailBlock.SHAPE, shape) : Blocks.RAIL.defaultBlockState().setValue(RailBlock.SHAPE, shape);
            slice(slices, rail.getX(), rail.getZ()).rails.add(new CompiledChunkSlice.RailColumn(rail, state,
                    powered ? Blocks.REDSTONE_BLOCK.defaultBlockState() : Blocks.GRAVEL.defaultBlockState()));
        }
    }

    private static Direction direction(List<VisualPoint> nodes, int from, int to) {
        VisualPoint a = nodes.get(from); VisualPoint b = nodes.get(to);
        if (b.x() > a.x()) return Direction.EAST; if (b.x() < a.x()) return Direction.WEST;
        return b.z() > a.z() ? Direction.SOUTH : Direction.NORTH;
    }

    private static RailShape railShape(Direction previous, Direction next, int y, int nextY, int previousY) {
        if (nextY > y) return ascending(next);
        if (previousY > y) return ascending(previous.getOpposite());
        return previous.getAxis() == next.getAxis() ? (next.getAxis() == Direction.Axis.X
                ? RailShape.EAST_WEST : RailShape.NORTH_SOUTH) : corner(previous, next);
    }

    private static RailShape ascending(Direction direction) {
        return switch (direction) {
            case EAST -> RailShape.ASCENDING_EAST; case WEST -> RailShape.ASCENDING_WEST;
            case SOUTH -> RailShape.ASCENDING_SOUTH; default -> RailShape.ASCENDING_NORTH;
        };
    }

    private static boolean supportsPoweredRail(RailShape shape) {
        return switch (shape) {
            case NORTH_SOUTH, EAST_WEST, ASCENDING_EAST, ASCENDING_WEST, ASCENDING_NORTH,
                    ASCENDING_SOUTH -> true;
            default -> false;
        };
    }

    private static RailShape corner(Direction a, Direction b) {
        boolean north = a == Direction.NORTH || b == Direction.NORTH;
        boolean south = a == Direction.SOUTH || b == Direction.SOUTH;
        boolean east = a == Direction.EAST || b == Direction.EAST;
        if (north && east) return RailShape.NORTH_EAST;
        if (north) return RailShape.NORTH_WEST;
        return south && east ? RailShape.SOUTH_EAST : RailShape.SOUTH_WEST;
    }

    private static void put(Map<Long, MutableSlice> slices, BlockPos position, BlockState state) {
        slice(slices, position.getX(), position.getZ()).blocks.put(position.immutable(), state);
    }

    private static MutableSlice slice(Map<Long, MutableSlice> slices, int x, int z) {
        long key = ChunkPos.asLong(x >> 4, z >> 4);
        return slices.computeIfAbsent(key, MutableSlice::new);
    }

    private static String sha256(Object value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private static final class MutableSlice {
        private final long key;
        private final List<CompiledChunkSlice.TerrainColumn> terrain = new ArrayList<>();
        private final Map<Long, CompiledChunkSlice.VegetationColumn> vegetation = new LinkedHashMap<>();
        private final List<CompiledChunkSlice.RailColumn> rails = new ArrayList<>();
        private final Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        private MutableSlice(long key) { this.key = key; }
        private CompiledChunkSlice freeze(String catalogHash) {
            String stamp = catalogHash.substring(0, 16) + ":" + Long.toUnsignedString(key, 16) + ":"
                    + terrain.size() + ":" + vegetation.size() + ":" + rails.size() + ":" + blocks.size();
            return new CompiledChunkSlice(key, stamp, terrain, vegetation.values().stream().toList(), rails, blocks);
        }
    }
}
