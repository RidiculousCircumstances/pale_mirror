package io.farfrontier.palemirror.visuals.genesis;

import io.farfrontier.palemirror.api.AuthoredRegionSeed;
import io.farfrontier.palemirror.api.AuthoredMineRole;
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
import net.minecraft.world.level.block.state.BlockState;

/** Compiles global manifests once into independent chunk-local worldgen slices. */
public final class FrontierGenesisCompiler {
    public static final int CATALOG_VERSION = 18;

    public CompiledGenesisCatalog compile(List<AuthoredRegionSeed> manifests) {
        Map<Long, MutableGenesisSlice> slices = new LinkedHashMap<>();
        manifests.stream().sorted(Comparator.comparing(AuthoredRegionSeed::planId))
                .forEach(seed -> compileRegion(seed, slices));
        String catalogHash = sha256(CATALOG_VERSION + ":" + manifests.stream()
                .map(value -> value.planId() + "=" + value.contentHash()).sorted().toList());
        Map<Long, CompiledChunkSlice> compiled = new LinkedHashMap<>();
        slices.forEach((key, value) -> compiled.put(key, value.freeze(catalogHash)));
        return new CompiledGenesisCatalog(CATALOG_VERSION, catalogHash, manifests, compiled);
    }

    private static void compileRegion(AuthoredRegionSeed seed, Map<Long, MutableGenesisSlice> slices) {
        FrontierPalette palette = FrontierPalette.forClimate(
                FrontierClimate.valueOf(seed.climate().toUpperCase(Locale.ROOT)));
        SettlementGenesisCompiler.compile(seed, palette, new SettlementGenesisCompiler.Sink() {
            @Override public void terrain(int x, int z, int targetY, BlockState surface, BlockState foundation) {
                MutableGenesisSlice slice = slice(slices, x, z);
                slice.terrain(new CompiledChunkSlice.TerrainColumn(x, z, targetY, surface, foundation));
            }
            @Override public void blend(int x, int z, int targetY, BlockState surface, BlockState foundation,
                                        int blendDistance) {
                MutableGenesisSlice slice = slice(slices, x, z);
                slice.terrain(new CompiledChunkSlice.TerrainColumn(
                        x, z, targetY, surface, foundation, blendDistance));
            }
            @Override public void cleanup(int x, int z, int baseY) {
                slice(slices, x, z).vegetation.putIfAbsent(ChunkPos.asLong(x, z),
                        new CompiledChunkSlice.VegetationColumn(x, z, baseY));
            }
            @Override public void surfaceBlock(int x, int z, int offsetY, BlockState state) {
                slice(slices, x, z).surfaceDecoration(
                        new CompiledChunkSlice.SurfaceDecoration(x, z, offsetY, state));
            }
            @Override public void block(BlockPos position, BlockState state) { put(slices, position, state); }
        });
        for (VisualModulePlacement module : seed.modules()) compileModule(module, slices);
        compileMine(seed.primaryMineSite(), palette, slices);
        compileMine(seed.alternateMineSite(), palette, slices);
        FrontierRailGenesisCompiler.compile(seed.baselineRailNodes(), new FrontierRailGenesisCompiler.Sink() {
            @Override public void rail(BlockPos rail, BlockState state, BlockState support) {
                slice(slices, rail.getX(), rail.getZ()).rails.add(
                        new CompiledChunkSlice.RailColumn(rail, state, support));
            }
            @Override public void block(BlockPos position, BlockState state) { put(slices, position, state); }
        });
    }

    private static void compileMineKinetics(AuthoredMineSitePlan mine, Map<Long, MutableGenesisSlice> slices) {
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

    private static void compileModule(VisualModulePlacement module, Map<Long, MutableGenesisSlice> slices) {
        AuthoredModuleCompiler.compile(module).blocks().forEach(block -> put(slices,
                new BlockPos(block.position().x(), block.position().y(), block.position().z()), block.state()));
    }

    private static void compileMine(AuthoredMineSitePlan mine, FrontierPalette palette,
                                    Map<Long, MutableGenesisSlice> slices) {
        compileMineVegetationEnvelope(mine, slices);
        compileMineFoundations(mine, slices);
        compileMineWorkingYard(mine, slices);
        compileMinePaths(mine, palette, slices);
        compileMineDrift(mine, slices);
        mine.initialModules().forEach(module -> compileModule(module, slices));
        mine.stagedModules().forEach(stage -> compileReservationFootprint(stage.module(), slices));
        compileMineIndustrialDetails(mine, palette, slices);
        compileMineKinetics(mine, slices);
        put(slices, block(mine.controllerAnchor()).above(), Blocks.SOUL_LANTERN.defaultBlockState());
    }

    /** Clears the connected working yard while leaving its terrain untouched. */
    private static void compileMineVegetationEnvelope(AuthoredMineSitePlan mine,
                                                       Map<Long, MutableGenesisSlice> slices) {
        int minimumX = mine.foundations().stream().mapToInt(value -> value.footprint().min().x())
                .min().orElseThrow();
        int maximumX = mine.foundations().stream().mapToInt(value -> value.footprint().max().x())
                .max().orElseThrow();
        int minimumZ = mine.foundations().stream().mapToInt(value -> value.footprint().min().z())
                .min().orElseThrow();
        int maximumZ = mine.foundations().stream().mapToInt(value -> value.footprint().max().z())
                .max().orElseThrow();
        int baseY = mine.foundations().stream().mapToInt(MineFoundationPlan::targetY).min().orElseThrow();
        // Large modded trees routinely carry crowns 10-14 blocks away from
        // their trunks. Use an organic union around the actual pads instead
        // of a rectangular clear-cut, but keep enough clearance for the
        // industrial skyline and fire/safety lanes.
        final int halo = 18;
        for (int z = minimumZ - halo; z <= maximumZ + halo; z++) {
            for (int x = minimumX - halo; x <= maximumX + halo; x++) {
                final int columnX = x;
                final int columnZ = z;
                int nearestPad = mine.foundations().stream()
                        .mapToInt(value -> distanceFrom(value, columnX, columnZ)).min().orElseThrow();
                int edgeVariation = Math.floorMod(x * 31 + z * 19 + mine.portal().x() * 13, 4);
                if (nearestPad > halo + edgeVariation) continue;
                MutableGenesisSlice slice = slice(slices, x, z);
                slice.vegetation.putIfAbsent(ChunkPos.asLong(x, z),
                        new CompiledChunkSlice.VegetationColumn(x, z, baseY));
            }
        }
    }

    private static void compileMineIndustrialDetails(AuthoredMineSitePlan mine, FrontierPalette palette,
                                                     Map<Long, MutableGenesisSlice> slices) {
        MineSurfaceGenesisCompiler.compile(mine, palette, (position, state) -> put(slices, position, state));
    }

    /**
     * Paints an irregular, terrain-following working surface between the
     * primary mine buildings. Foundations still own their exact pads and the
     * natural relief remains intact; this layer only makes the separate pads
     * read as one industrial campus.
     */
    private static void compileMineWorkingYard(AuthoredMineSitePlan mine,
                                               Map<Long, MutableGenesisSlice> slices) {
        if (mine.role() != AuthoredMineRole.PRIMARY) return;
        for (int inward = -64; inward <= -18; inward++) {
            for (int right = -34; right <= 34; right++) {
                int radial = right * right * 9 + (inward + 41) * (inward + 41) * 16;
                int edgeNoise = Math.floorMod(right * 31 + inward * 17 + mine.portal().x(), 97) * 16;
                if (radial + edgeNoise > 34 * 34 * 9) continue;
                VisualPoint point = MineSurfaceLayout.local(
                        mine.portal(), right, inward, 0, mine.inwardQuarterTurns());
                int pattern = Math.floorMod(point.x() * 17 + point.z() * 29, 23);
                BlockState surface = switch (pattern) {
                    case 0, 1, 2, 3 -> Blocks.COARSE_DIRT.defaultBlockState();
                    case 4, 5, 6 -> Blocks.ANDESITE.defaultBlockState();
                    case 7 -> Blocks.TUFF.defaultBlockState();
                    default -> Blocks.GRAVEL.defaultBlockState();
                };
                mineSurface(slices, point, -1, surface);
            }
        }
        // Stone drainage seams and freight wear lines articulate the yard
        // without imposing another geometric plaza on the mountain foot.
        for (int inward = -58; inward <= -25; inward++) {
            for (int right : new int[]{-10, 10}) {
                if (Math.floorMod(inward, 5) == 0) continue;
                mineSurface(slices, MineSurfaceLayout.local(mine.portal(), right, inward, 0,
                        mine.inwardQuarterTurns()), -1, Blocks.POLISHED_ANDESITE_SLAB.defaultBlockState());
            }
        }
        compileMineYardFurniture(mine, slices);
    }

    private static void compileMineYardFurniture(AuthoredMineSitePlan mine,
                                                  Map<Long, MutableGenesisSlice> slices) {
        for (int[] local : new int[][]{{-28, -29}, {28, -29}, {-30, -55}, {30, -55}}) {
            VisualPoint point = MineSurfaceLayout.local(mine.portal(), local[0], local[1], 0,
                    mine.inwardQuarterTurns());
            mineSurface(slices, point, 0, Blocks.COBBLESTONE_WALL.defaultBlockState());
            mineSurface(slices, point, 1, Blocks.IRON_BARS.defaultBlockState());
            mineSurface(slices, point, 2, Blocks.LANTERN.defaultBlockState());
        }
        // Open ore-sort bins: deliberately non-valuable rock communicates
        // function without becoming a free strategic-resource spawn.
        for (int bin = 0; bin < 3; bin++) {
            int centerRight = -25 + bin * 5;
            for (int right = centerRight - 2; right <= centerRight + 2; right++) {
                mineSurface(slices, MineSurfaceLayout.local(mine.portal(), right, -46, 0,
                        mine.inwardQuarterTurns()), 0, Blocks.COBBLED_DEEPSLATE.defaultBlockState());
            }
            for (int inward = -45; inward <= -42; inward++) {
                for (int right : new int[]{centerRight - 2, centerRight + 2}) {
                    mineSurface(slices, MineSurfaceLayout.local(mine.portal(), right, inward, 0,
                            mine.inwardQuarterTurns()), 0, Blocks.COBBLESTONE_WALL.defaultBlockState());
                }
            }
            for (int right = centerRight - 1; right <= centerRight + 1; right++) {
                mineSurface(slices, MineSurfaceLayout.local(mine.portal(), right, -43, 0,
                        mine.inwardQuarterTurns()), 0,
                        Math.floorMod(right + bin, 2) == 0
                                ? Blocks.TUFF.defaultBlockState()
                                : Blocks.ANDESITE.defaultBlockState());
            }
        }
        for (int offset = 0; offset < 6; offset++) {
            VisualPoint timber = MineSurfaceLayout.local(mine.portal(), 23 + offset, -49, 0,
                    mine.inwardQuarterTurns());
            mineSurface(slices, timber, 0, Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState());
            if (offset < 3) mineSurface(slices, timber, 1, Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState());
        }
        for (int[] local : new int[][]{{22, -42}, {25, -42}, {22, -39}}) {
            mineSurface(slices, MineSurfaceLayout.local(mine.portal(), local[0], local[1], 0,
                    mine.inwardQuarterTurns()), 0, Blocks.STRIPPED_SPRUCE_WOOD.defaultBlockState());
        }
    }

    private static void mineSurface(Map<Long, MutableGenesisSlice> slices, VisualPoint point,
                                    int offsetY, BlockState state) {
        slice(slices, point.x(), point.z()).surfaceDecoration(
                new CompiledChunkSlice.SurfaceDecoration(point.x(), point.z(), offsetY, state));
    }

    private static void compileReservationFootprint(VisualModulePlacement module,
                                                    Map<Long, MutableGenesisSlice> slices) {
        for (int x = module.footprint().min().x(); x <= module.footprint().max().x(); x++) {
            for (int z = module.footprint().min().z(); z <= module.footprint().max().z(); z++) {
                MutableGenesisSlice slice = slice(slices, x, z);
                slice.vegetation.putIfAbsent(ChunkPos.asLong(x, z), new CompiledChunkSlice.VegetationColumn(
                        x, z, module.footprint().min().y() - 1));
            }
        }
    }

    private static void compileMineFoundations(AuthoredMineSitePlan mine,
                                               Map<Long, MutableGenesisSlice> slices) {
        for (MineFoundationPlan foundation : mine.foundations()) {
            // A mine is a connected industrial campus, not six buildings lost
            // independently in forest. The wider transition joins nearby pads
            // while resolvedTarget keeps the natural slope whenever it already
            // sits inside the allowed band.
            int transition = Math.max(foundation.maximumCut(), foundation.maximumFill()) + 10;
            int influence = foundation.apron() + transition;
            for (int x = foundation.footprint().min().x() - influence;
                 x <= foundation.footprint().max().x() + influence; x++) {
                for (int z = foundation.footprint().min().z() - influence;
                     z <= foundation.footprint().max().z() + influence; z++) {
                    MutableGenesisSlice slice = slice(slices, x, z);
                    int distance = distanceFrom(foundation, x, z);
                    boolean building = distance == 0;
                    BlockState surface = (building ? Blocks.COBBLESTONE
                            : distance <= foundation.apron() ? Blocks.COARSE_DIRT : Blocks.GRASS_BLOCK)
                            .defaultBlockState();
                    int blendDistance = Math.max(0, distance - foundation.apron());
                    slice.terrain(new CompiledChunkSlice.TerrainColumn(x, z, foundation.targetY(), surface,
                            blendDistance == 0 ? Blocks.COBBLESTONE.defaultBlockState()
                                    : Blocks.DIRT.defaultBlockState(), blendDistance));
                    slice.vegetation.putIfAbsent(ChunkPos.asLong(x, z),
                            new CompiledChunkSlice.VegetationColumn(x, z, foundation.targetY()));
                }
            }
        }
    }

    private static int distanceFrom(MineFoundationPlan foundation, int x, int z) {
        int dx = Math.max(foundation.footprint().min().x() - x, x - foundation.footprint().max().x());
        int dz = Math.max(foundation.footprint().min().z() - z, z - foundation.footprint().max().z());
        return Math.max(0, Math.max(dx, dz));
    }

    private static void compileMinePaths(AuthoredMineSitePlan mine, FrontierPalette palette,
                                         Map<Long, MutableGenesisSlice> slices) {
        MineFoundationPlan hub = mine.foundations().stream().filter(value -> value.id().equals("crew"))
                .findFirst().orElseThrow();
        VisualPoint start = center(hub);
        compileMinePath(start, mine.portal(), palette, slices);
        for (MineFoundationPlan destination : mine.foundations()) {
            if (destination == hub) continue;
            compileMinePath(start, center(destination), palette, slices);
        }
    }

    private static void compileMinePath(VisualPoint start, VisualPoint destination,
                                        FrontierPalette palette, Map<Long, MutableGenesisSlice> slices) {
        List<VisualPoint> path = FrontierRegionPlanner.cardinalRail(start, destination, 0);
        int segments = Math.max(1, path.size() - 1);
        for (int index = 0; index < path.size(); index++) {
            VisualPoint point = path.get(index);
            int y = start.y() + (destination.y() - start.y()) * index / segments;
            // Five-block service lanes visually bind the pads into one mine
            // campus and still leave a one-block shoulder for lamps, drains
            // and terrain transitions.
            for (int offset = -2; offset <= 2; offset++) {
                int x = point.x();
                int z = point.z();
                if (index + 1 < path.size() && path.get(index + 1).x() != point.x()) z += offset;
                else x += offset;
                MutableGenesisSlice slice = slice(slices, x, z);
                BlockState surface = Math.floorMod(index + offset, 7) == 0
                        ? Blocks.POLISHED_ANDESITE.defaultBlockState()
                        : Blocks.GRAVEL.defaultBlockState();
                slice.terrain(new CompiledChunkSlice.TerrainColumn(x, z, y,
                        surface, Blocks.COBBLESTONE.defaultBlockState()));
                if (Math.abs(offset) == 2) {
                    put(slices, new BlockPos(x, y + 1, z), palette.pavingSlab());
                }
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

    private static void compileMineDrift(AuthoredMineSitePlan mine, Map<Long, MutableGenesisSlice> slices) {
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
                                            Map<Long, MutableGenesisSlice> slices) {
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

    private static void compileMineJunction(AuthoredMineSitePlan mine, Map<Long, MutableGenesisSlice> slices) {
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

    private static void putMineLocal(Map<Long, MutableGenesisSlice> slices, AuthoredMineSitePlan mine,
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

    private static void put(Map<Long, MutableGenesisSlice> slices, BlockPos position, BlockState state) {
        slice(slices, position.getX(), position.getZ()).blocks.put(position.immutable(), state);
    }

    private static MutableGenesisSlice slice(Map<Long, MutableGenesisSlice> slices, int x, int z) {
        long key = ChunkPos.asLong(x >> 4, z >> 4);
        return slices.computeIfAbsent(key, MutableGenesisSlice::new);
    }

    private static String sha256(Object value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

}
