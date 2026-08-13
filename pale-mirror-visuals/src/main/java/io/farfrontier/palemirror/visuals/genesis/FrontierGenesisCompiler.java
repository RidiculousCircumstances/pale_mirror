package io.farfrontier.palemirror.visuals.genesis;

import io.farfrontier.palemirror.api.AuthoredRegionSeed;
import io.farfrontier.palemirror.api.AuthoredMineSitePlan;
import io.farfrontier.palemirror.api.MineFoundationPlan;
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
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;

/** Compiles global manifests once into independent chunk-local worldgen slices. */
public final class FrontierGenesisCompiler {
    public static final int CATALOG_VERSION = 10;

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
        SettlementGenesisCompiler.compile(seed, palette, new SettlementGenesisCompiler.Sink() {
            @Override public void terrain(int x, int z, int targetY, BlockState surface, BlockState foundation) {
                MutableSlice slice = slice(slices, x, z);
                slice.terrain.add(new CompiledChunkSlice.TerrainColumn(x, z, targetY, surface, foundation));
            }
            @Override public void cleanup(int x, int z, int baseY) {
                slice(slices, x, z).vegetation.putIfAbsent(ChunkPos.asLong(x, z),
                        new CompiledChunkSlice.VegetationColumn(x, z, baseY));
            }
            @Override public void block(BlockPos position, BlockState state) { put(slices, position, state); }
        });
        for (VisualModulePlacement module : seed.modules()) compileModule(module, slices);
        compileMine(seed.primaryMineSite(), slices);
        compileMine(seed.alternateMineSite(), slices);
        compileRail(seed.baselineRailNodes(), slices);
    }

    private static void compileMineKinetics(AuthoredMineSitePlan mine, Map<Long, MutableSlice> slices) {
        MineFoundationPlan power = mine.foundations().stream().filter(value -> value.id().equals("power"))
                .findFirst().orElse(null);
        if (power == null) return;
        BlockState motor = optionalCreate("creative_motor", mine.inwardQuarterTurns());
        BlockState shaft = optionalCreate("shaft", mine.inwardQuarterTurns());
        BlockState fan = optionalCreate("encased_fan", mine.inwardQuarterTurns());
        if (motor == null || shaft == null || fan == null) return;
        BlockPos center = new BlockPos((power.footprint().min().x() + power.footprint().max().x()) / 2,
                power.targetY() + 2, (power.footprint().min().z() + power.footprint().max().z()) / 2);
        Direction facing = direction(mine.inwardQuarterTurns());
        put(slices, center, motor); put(slices, center.relative(facing), shaft);
        put(slices, center.relative(facing, 2), fan);
        put(slices, center.relative(facing.getOpposite()), Blocks.BRICKS.defaultBlockState());
    }

    private static BlockState optionalCreate(String path, int turns) {
        return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getOptional(
                        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("create", path))
                .map(block -> {
                    BlockState state = block.defaultBlockState();
                    for (int turn = 0; turn < Math.floorMod(turns, 4); turn++) {
                        state = state.rotate(net.minecraft.world.level.block.Rotation.CLOCKWISE_90);
                    }
                    return state;
                }).orElse(null);
    }

    private static Direction direction(int quarterTurns) {
        return switch (Math.floorMod(quarterTurns, 4)) {
            case 0 -> Direction.EAST; case 1 -> Direction.SOUTH; case 2 -> Direction.WEST; default -> Direction.NORTH;
        };
    }

    private static void compileModule(VisualModulePlacement module, Map<Long, MutableSlice> slices) {
        AuthoredModuleCompiler.compile(module).blocks().forEach(block -> put(slices,
                new BlockPos(block.position().x(), block.position().y(), block.position().z()), block.state()));
    }

    private static void compileMine(AuthoredMineSitePlan mine, Map<Long, MutableSlice> slices) {
        compileMineFoundations(mine, slices);
        compileMinePaths(mine, slices);
        compileMineDrift(mine, slices);
        mine.initialModules().forEach(module -> compileModule(module, slices));
        mine.stagedModules().forEach(stage -> compileReservationFootprint(stage.module(), slices));
        compileMineIndustrialDetails(mine, slices);
        compileMineKinetics(mine, slices);
        put(slices, block(mine.controllerAnchor()).above(), Blocks.SOUL_LANTERN.defaultBlockState());
    }

    private static void compileMineIndustrialDetails(AuthoredMineSitePlan mine, Map<Long, MutableSlice> slices) {
        for (int side : new int[]{-3, 3}) for (int up = 1; up <= 8; up++) {
            put(slices, local(mine.portal(), side, 0, up, mine.inwardQuarterTurns()),
                    Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState());
        }
        for (int right = -3; right <= 3; right++) {
            put(slices, local(mine.portal(), right, 0, 8, mine.inwardQuarterTurns()),
                    Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState());
        }
        for (int up = 3; up <= 7; up++) {
            put(slices, local(mine.portal(), 2, 0, up, mine.inwardQuarterTurns()),
                    Blocks.CHAIN.defaultBlockState());
        }
        MineFoundationPlan power = mine.foundations().stream().filter(value -> value.id().equals("power"))
                .findFirst().orElse(null);
        if (power != null) {
            int x = power.footprint().max().x() + 1;
            int z = power.footprint().max().z() + 1;
            for (int dx = 0; dx <= 1; dx++) for (int dz = 0; dz <= 1; dz++) {
                for (int up = 1; up <= 12; up++) put(slices,
                        new BlockPos(x + dx, power.targetY() + up, z + dz),
                        up > 9 ? Blocks.BRICKS.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState());
            }
        }
        VisualPoint loading = mine.loadingEndpoint();
        for (int right = -4; right <= 4; right++) for (int inward = -2; inward <= 2; inward++) {
            if (right == 0) continue;
            put(slices, local(loading, right, inward, -1, mine.inwardQuarterTurns()),
                    Blocks.OAK_PLANKS.defaultBlockState());
        }
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

    private static void compileMineFoundations(AuthoredMineSitePlan mine, Map<Long, MutableSlice> slices) {
        for (MineFoundationPlan foundation : mine.foundations()) {
            for (int x = foundation.footprint().min().x() - foundation.apron();
                 x <= foundation.footprint().max().x() + foundation.apron(); x++) {
                for (int z = foundation.footprint().min().z() - foundation.apron();
                     z <= foundation.footprint().max().z() + foundation.apron(); z++) {
                    MutableSlice slice = slice(slices, x, z);
                    boolean building = x >= foundation.footprint().min().x()
                            && x <= foundation.footprint().max().x()
                            && z >= foundation.footprint().min().z()
                            && z <= foundation.footprint().max().z();
                    slice.terrain.add(new CompiledChunkSlice.TerrainColumn(x, z, foundation.targetY(),
                            (building ? Blocks.COBBLESTONE : Blocks.COARSE_DIRT).defaultBlockState(),
                            Blocks.COBBLESTONE.defaultBlockState()));
                    slice.vegetation.putIfAbsent(ChunkPos.asLong(x, z),
                            new CompiledChunkSlice.VegetationColumn(x, z, foundation.targetY()));
                }
            }
        }
    }

    private static void compileMinePaths(AuthoredMineSitePlan mine, Map<Long, MutableSlice> slices) {
        MineFoundationPlan hub = mine.foundations().stream().filter(value -> value.id().equals("crew"))
                .findFirst().orElseThrow();
        VisualPoint start = center(hub);
        compileMinePath(start, mine.portal(), slices);
        for (MineFoundationPlan destination : mine.foundations()) {
            if (destination == hub) continue;
            compileMinePath(start, center(destination), slices);
        }
    }

    private static void compileMinePath(VisualPoint start, VisualPoint destination,
                                        Map<Long, MutableSlice> slices) {
        List<VisualPoint> path = FrontierRegionPlanner.cardinalRail(start, destination, 0);
        int segments = Math.max(1, path.size() - 1);
        for (int index = 0; index < path.size(); index++) {
            VisualPoint point = path.get(index);
            int y = start.y() + (destination.y() - start.y()) * index / segments;
            for (int offset = -1; offset <= 1; offset++) {
                int x = point.x();
                int z = point.z();
                if (index + 1 < path.size() && path.get(index + 1).x() != point.x()) z += offset;
                else x += offset;
                MutableSlice slice = slice(slices, x, z);
                slice.terrain.add(new CompiledChunkSlice.TerrainColumn(x, z, y,
                        Blocks.GRAVEL.defaultBlockState(), Blocks.COBBLESTONE.defaultBlockState()));
                slice.vegetation.putIfAbsent(ChunkPos.asLong(x, z),
                        new CompiledChunkSlice.VegetationColumn(x, z, y));
            }
        }
    }

    private static VisualPoint center(MineFoundationPlan foundation) {
        return new VisualPoint((foundation.footprint().min().x() + foundation.footprint().max().x()) / 2,
                foundation.targetY(),
                (foundation.footprint().min().z() + foundation.footprint().max().z()) / 2);
    }

    private static void compileMineDrift(AuthoredMineSitePlan mine, Map<Long, MutableSlice> slices) {
        int section = 0;
        for (List<MineUndergroundLayout.Node> corridor : MineUndergroundLayout.corridors()) {
            for (int segment = 1; segment < corridor.size(); segment++) {
                MineUndergroundLayout.Node from = corridor.get(segment - 1);
                MineUndergroundLayout.Node to = corridor.get(segment);
                int rightDelta = to.right() - from.right();
                int inwardDelta = to.inward() - from.inward();
                int upDelta = to.up() - from.up();
                int steps = Math.max(1, Math.max(Math.abs(rightDelta), Math.max(Math.abs(inwardDelta), Math.abs(upDelta))));
                boolean transverseAlongRight = Math.abs(inwardDelta) >= Math.abs(rightDelta);
                for (int step = segment == 1 ? 0 : 1; step <= steps; step++) {
                    MineUndergroundLayout.Node node = new MineUndergroundLayout.Node(
                            from.right() + rightDelta * step / steps,
                            from.inward() + inwardDelta * step / steps,
                            from.up() + upDelta * step / steps);
                    compileDriftSection(mine, node, transverseAlongRight, section++, slices);
                }
            }
        }
        compileMineJunction(mine, slices);
    }

    private static void compileDriftSection(AuthoredMineSitePlan mine, MineUndergroundLayout.Node node,
                                            boolean transverseAlongRight, int section,
                                            Map<Long, MutableSlice> slices) {
        for (int offset = -2; offset <= 2; offset++) {
            putMineLocal(slices, mine, transverse(node, offset, -1, transverseAlongRight),
                    Math.floorMod(section + offset, 5) == 0
                            ? Blocks.COBBLED_DEEPSLATE.defaultBlockState()
                            : Blocks.DEEPSLATE_BRICKS.defaultBlockState());
        }
        for (int up = 0; up <= 2; up++) for (int offset = -2; offset <= 2; offset++) {
            putMineLocal(slices, mine, transverse(node, offset, up, transverseAlongRight),
                    Blocks.AIR.defaultBlockState());
        }
        for (int offset = -1; offset <= 1; offset++) {
            putMineLocal(slices, mine, transverse(node, offset, 3, transverseAlongRight),
                    Blocks.AIR.defaultBlockState());
        }
        putMineLocal(slices, mine, transverse(node, -2, 3, transverseAlongRight),
                Blocks.COBBLED_DEEPSLATE.defaultBlockState());
        putMineLocal(slices, mine, transverse(node, 2, 3, transverseAlongRight),
                Blocks.COBBLED_DEEPSLATE.defaultBlockState());
        for (int offset = -1; offset <= 1; offset++) {
            putMineLocal(slices, mine, transverse(node, offset, 4, transverseAlongRight),
                    section % 4 == 0 ? Blocks.DEEPSLATE_TILES.defaultBlockState()
                            : Blocks.DEEPSLATE_BRICKS.defaultBlockState());
        }
        if (section % 7 == 0) {
            for (int up = 0; up <= 2; up++) {
                putMineLocal(slices, mine, transverse(node, -2, up, transverseAlongRight),
                        Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState());
                putMineLocal(slices, mine, transverse(node, 2, up, transverseAlongRight),
                        Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState());
            }
            for (int offset = -2; offset <= 2; offset++) {
                putMineLocal(slices, mine, transverse(node, offset, 3, transverseAlongRight),
                        Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState());
            }
            if (section % 14 == 0) {
                putMineLocal(slices, mine, transverse(node, 0, 2, transverseAlongRight),
                        Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
            }
        }
    }

    private static MineUndergroundLayout.Node transverse(MineUndergroundLayout.Node node, int offset, int up,
                                                          boolean alongRight) {
        return new MineUndergroundLayout.Node(node.right() + (alongRight ? offset : 0),
                node.inward() + (alongRight ? 0 : offset), node.up() + up);
    }

    private static void compileMineJunction(AuthoredMineSitePlan mine, Map<Long, MutableSlice> slices) {
        MineUndergroundLayout.Node junction = MineUndergroundLayout.JUNCTION;
        for (int right = -4; right <= 4; right++) for (int inward = -4; inward <= 4; inward++) {
            int radius = right * right + inward * inward;
            if (radius > 20) continue;
            MineUndergroundLayout.Node floor = new MineUndergroundLayout.Node(
                    junction.right() + right, junction.inward() + inward, junction.up() - 1);
            putMineLocal(slices, mine, floor, radius % 5 == 0
                    ? Blocks.POLISHED_ANDESITE.defaultBlockState() : Blocks.DEEPSLATE_BRICKS.defaultBlockState());
            int height = radius <= 10 ? 4 : 3;
            for (int up = 0; up < height; up++) {
                putMineLocal(slices, mine, new MineUndergroundLayout.Node(
                        floor.right(), floor.inward(), junction.up() + up), Blocks.AIR.defaultBlockState());
            }
        }
        for (int right : new int[]{-3, 3}) for (int inward : new int[]{-2, 2}) {
            for (int up = 0; up <= 3; up++) {
                putMineLocal(slices, mine, new MineUndergroundLayout.Node(
                        junction.right() + right, junction.inward() + inward, junction.up() + up),
                        Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState());
            }
        }
        putMineLocal(slices, mine, new MineUndergroundLayout.Node(
                junction.right(), junction.inward(), junction.up() + 3),
                Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
    }

    private static void putMineLocal(Map<Long, MutableSlice> slices, AuthoredMineSitePlan mine,
                                     MineUndergroundLayout.Node node, BlockState state) {
        put(slices, local(mine.portal(), node.right(), node.inward(), node.up(),
                mine.inwardQuarterTurns()), state);
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
            if (index % 3 == 0) {
                boolean xAxis = next.getAxis() == Direction.Axis.X;
                for (int side : new int[]{-1, 1}) {
                    BlockPos sleeper = rail.below().offset(xAxis ? 0 : side, 0, xAxis ? side : 0);
                    put(slices, sleeper, Blocks.STRIPPED_OAK_LOG.defaultBlockState());
                }
            }
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
