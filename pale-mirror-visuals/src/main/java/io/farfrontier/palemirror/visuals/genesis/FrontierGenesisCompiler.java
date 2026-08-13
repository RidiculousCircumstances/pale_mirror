package io.farfrontier.palemirror.visuals.genesis;

import io.farfrontier.palemirror.api.AuthoredRegionSeed;
import io.farfrontier.palemirror.api.AuthoredMineSitePlan;
import io.farfrontier.palemirror.api.VisualModulePlacement;
import io.farfrontier.palemirror.api.VisualPoint;
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
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;

/** Compiles global manifests once into independent chunk-local worldgen slices. */
public final class FrontierGenesisCompiler {
    public static final int CATALOG_VERSION = 7;
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
        compileMine(seed.primaryMineSite(), slices);
        compileMine(seed.alternateMineSite(), slices);
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
        AuthoredModuleCompiler.compile(module).blocks().forEach(block -> put(slices,
                new BlockPos(block.position().x(), block.position().y(), block.position().z()), block.state()));
    }

    private static void compileMine(AuthoredMineSitePlan mine, Map<Long, MutableSlice> slices) {
        compileMineTerrace(mine, slices);
        compileMineDrift(mine, slices);
        mine.initialModules().forEach(module -> compileModule(module, slices));
        mine.stagedModules().forEach(stage -> compileReservationFootprint(stage.module(), slices));
        put(slices, block(mine.controllerAnchor()).above(), Blocks.SOUL_LANTERN.defaultBlockState());
    }

    private static void compileReservationFootprint(VisualModulePlacement module, Map<Long, MutableSlice> slices) {
        for (int x = module.footprint().min().x(); x <= module.footprint().max().x(); x++) {
            for (int z = module.footprint().min().z(); z <= module.footprint().max().z(); z++) {
                MutableSlice slice = slice(slices, x, z);
                slice.vegetation.putIfAbsent(ChunkPos.asLong(x, z), new CompiledChunkSlice.VegetationColumn(
                        x, z, module.footprint().min().y() - 1));
            }
        }
    }

    private static void compileMineTerrace(AuthoredMineSitePlan mine, Map<Long, MutableSlice> slices) {
        int halfWidth = mine.role() == io.farfrontier.palemirror.api.AuthoredMineRole.PRIMARY ? 40 : 22;
        int outward = mine.role() == io.farfrontier.palemirror.api.AuthoredMineRole.PRIMARY ? 50 : 28;
        for (int right = -halfWidth; right <= halfWidth; right++) for (int inward = -outward; inward <= 0; inward++) {
            BlockPos position = local(mine.portal(), right, inward, 0, mine.inwardQuarterTurns());
            MutableSlice slice = slice(slices, position.getX(), position.getZ());
            slice.terrain.add(new CompiledChunkSlice.TerrainColumn(position.getX(), position.getZ(), position.getY(),
                    Blocks.GRAVEL.defaultBlockState(), Blocks.COBBLESTONE.defaultBlockState()));
            slice.vegetation.putIfAbsent(ChunkPos.asLong(position.getX(), position.getZ()),
                    new CompiledChunkSlice.VegetationColumn(position.getX(), position.getZ(), position.getY()));
        }
    }

    private static void compileMineDrift(AuthoredMineSitePlan mine, Map<Long, MutableSlice> slices) {
        for (int inward = 0; inward <= 76; inward++) {
            int drop = Math.min(18, inward / 4);
            BlockPos floor = local(mine.portal(), 0, inward, -drop, mine.inwardQuarterTurns());
            for (int right = -2; right <= 2; right++) {
                put(slices, localBlock(floor, right, 0, -1, mine.inwardQuarterTurns()),
                        Blocks.DEEPSLATE_BRICKS.defaultBlockState());
            }
            for (int right = -1; right <= 1; right++) for (int up = 0; up <= 3; up++) {
                put(slices, localBlock(floor, right, 0, up, mine.inwardQuarterTurns()), Blocks.AIR.defaultBlockState());
            }
            if (inward % 8 == 0) for (int up = 0; up <= 3; up++) {
                put(slices, localBlock(floor, -2, 0, up, mine.inwardQuarterTurns()), Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState());
                put(slices, localBlock(floor, 2, 0, up, mine.inwardQuarterTurns()), Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState());
            }
        }
    }

    private static BlockPos block(VisualPoint point) { return new BlockPos(point.x(), point.y(), point.z()); }

    private static BlockPos local(VisualPoint origin, int right, int inward, int up, int direction) {
        return localBlock(block(origin), right, inward, up, direction);
    }

    private static BlockPos localBlock(BlockPos origin, int right, int inward, int up, int direction) {
        int dx = switch (Math.floorMod(direction, 4)) { case 0 -> inward; case 1 -> -right; case 2 -> -inward; default -> right; };
        int dz = switch (Math.floorMod(direction, 4)) { case 0 -> right; case 1 -> inward; case 2 -> -right; default -> -inward; };
        return origin.offset(dx, up, dz);
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
