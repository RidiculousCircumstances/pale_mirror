package io.farfrontier.palemirror.visuals.genesis;

import io.farfrontier.palemirror.api.AuthoredRegionSeed;
import io.farfrontier.palemirror.api.AuthoredMineRole;
import io.farfrontier.palemirror.api.AuthoredMineSitePlan;
import io.farfrontier.palemirror.api.MineFoundationPlan;
import io.farfrontier.palemirror.api.VisualModulePlacement;
import io.farfrontier.palemirror.api.VisualPoint;
import io.farfrontier.palemirror.api.SiteSurfaceColumn;
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
    public static final int CATALOG_VERSION = 24;

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
        // Module shells are written first. Public entrances and their clear
        // throats are the final settlement writer, so a source NBT wall can
        // never overwrite the declared road/door contract.
        for (VisualModulePlacement module : seed.modules()) compileModule(module, slices);
        Map<Long, Integer> settlementDatums = seed.settlementSite().surfacePlan().columns().stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        value -> ChunkPos.asLong(value.x(), value.z()), SiteSurfaceColumn::groundY));
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
                // Worldgen exclusion owns authored-site ecology before PM placement.
            }
            @Override public void surfaceBlock(int x, int z, int offsetY, BlockState state) {
                Integer datum = settlementDatums.get(ChunkPos.asLong(x, z));
                if (datum == null) throw new IllegalStateException("Decoration escaped SiteSurfacePlan at "
                        + x + "," + z);
                BlockPos position = new BlockPos(x, datum + offsetY, z);
                slice(slices, x, z).decoration(new CompiledChunkSlice.AuthoredDecoration(position, state));
            }
            @Override public void block(BlockPos position, BlockState state) { put(slices, position, state); }
        });
        compileMine(seed.primaryMineSite(), palette, slices);
        compileMine(seed.alternateMineSite(), palette, slices);
        FrontierRailGenesisCompiler.compile(seed.baselineRailNodes(), new FrontierRailGenesisCompiler.Sink() {
            @Override public void rail(BlockPos rail, BlockState state, BlockState support, boolean supportPier) {
                slice(slices, rail.getX(), rail.getZ()).rails.add(
                        new CompiledChunkSlice.RailColumn(rail, state, support, supportPier));
                compileRailVegetationEnvelope(slices, rail);
            }
            @Override public void block(BlockPos position, BlockState state) { put(slices, position, state); }
        });
    }

    /** Keeps a cart-width safety corridor free of late trees without grading the route. */
    private static void compileRailVegetationEnvelope(Map<Long, MutableGenesisSlice> slices, BlockPos rail) {
        final int radius = 3;
        for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
            if (dx * dx + dz * dz > radius * radius + 1) continue;
            int x = rail.getX() + dx;
            int z = rail.getZ() + dz;
            slice(slices, x, z).vegetation.putIfAbsent(ChunkPos.asLong(x, z),
                    new CompiledChunkSlice.VegetationColumn(x, z, rail.getY()));
        }
    }

    private static void compileMineKinetics(AuthoredMineSitePlan mine, Map<Long, MutableGenesisSlice> slices) {
        boolean powerBuilt = mine.initialModules().stream()
                .anyMatch(value -> value.foundationId().equals("power"));
        if (!powerBuilt) return;
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
        compileMineFoundations(mine, slices);
        List<MineAccessGenesisCompiler.AccessRoute> accessRoutes = MineAccessGenesisCompiler.routes(mine);
        java.util.Set<Long> access = MineAccessGenesisCompiler.occupied(accessRoutes);
        compileMineWorkingYard(mine, access, slices);
        MineAccessGenesisCompiler.compile(accessRoutes, palette, new MineAccessGenesisCompiler.Sink() {
            @Override public void terrain(int x, int z, int targetY, BlockState surface, BlockState foundation) {
                slice(slices, x, z).terrain(new CompiledChunkSlice.TerrainColumn(
                        x, z, targetY, surface, foundation));
            }
            @Override public void cleanup(int x, int z, int baseY) {
                // Worldgen exclusion owns authored-site ecology before PM placement.
            }
            @Override public void block(BlockPos position, BlockState state) { put(slices, position, state); }
        });
        mine.initialModules().forEach(module -> compileModule(module, slices));
        MineUndergroundGenesisCompiler.compile(mine, (position, state) -> put(slices, position, state));
        compileMineEntrances(mine, accessRoutes, slices);
        compileMineIndustrialDetails(mine, palette, access, slices);
        compileMineKinetics(mine, slices);
        put(slices, block(mine.controllerAnchor()).above(), Blocks.SOUL_LANTERN.defaultBlockState());
    }

    private static void compileMineIndustrialDetails(AuthoredMineSitePlan mine, FrontierPalette palette,
                                                     java.util.Set<Long> access,
                                                     Map<Long, MutableGenesisSlice> slices) {
        MineSurfaceGenesisCompiler.compile(mine, palette, access, new MineSurfaceGenesisCompiler.Sink() {
            @Override public void put(BlockPos position, BlockState state) {
                FrontierGenesisCompiler.put(slices, position, state);
            }
            @Override public void surface(int x, int z, int offsetY, BlockState state) {
                int datum = mineDatum(mine, x, z);
                BlockPos position = new BlockPos(x, datum + offsetY, z);
                slice(slices, x, z).replaceDecoration(
                        new CompiledChunkSlice.AuthoredDecoration(position, state));
            }
        });
    }

    /** The physical route, not a guessed NBT doorway, owns the final walkable threshold. */
    private static void compileMineEntrances(AuthoredMineSitePlan mine,
                                             List<MineAccessGenesisCompiler.AccessRoute> routes,
                                             Map<Long, MutableGenesisSlice> slices) {
        java.util.Set<String> built = mine.initialModules().stream()
                .map(VisualModulePlacement::foundationId).collect(java.util.stream.Collectors.toSet());
        for (MineAccessGenesisCompiler.AccessRoute route : routes) {
            if (!built.contains(route.foundationId())) continue;
            MineFoundationPlan foundation = mine.foundations().stream()
                    .filter(value -> value.id().equals(route.foundationId())).findFirst().orElseThrow();
            VisualPoint outside = route.points().getLast();
            BlockPos threshold = new BlockPos(outside.x() + route.entryStepX(),
                    foundation.targetY() + 1, outside.z() + route.entryStepZ());
            for (int depth = 0; depth <= 2; depth++) for (int up = 0; up <= 2; up++) {
                put(slices, threshold.offset(route.entryStepX() * depth, up,
                        route.entryStepZ() * depth), Blocks.AIR.defaultBlockState());
            }
            put(slices, threshold.below(), Blocks.COBBLESTONE.defaultBlockState());
        }
    }

    /**
     * Paints an irregular, terrain-following working surface between the
     * primary mine buildings. Foundations still own their exact pads and the
     * natural relief remains intact; this layer only makes the separate pads
     * read as one industrial campus.
     */
    private static void compileMineWorkingYard(AuthoredMineSitePlan mine, java.util.Set<Long> access,
                                               Map<Long, MutableGenesisSlice> slices) {
        if (mine.role() != AuthoredMineRole.PRIMARY) return;
        for (int inward = -64; inward <= -18; inward++) {
            for (int right = -34; right <= 34; right++) {
                int radial = right * right * 9 + (inward + 41) * (inward + 41) * 16;
                int edgeNoise = Math.floorMod(right * 31 + inward * 17 + mine.portal().x(), 97) * 16;
                if (radial + edgeNoise > 34 * 34 * 9) continue;
                VisualPoint point = MineSurfaceLayout.local(
                        mine.portal(), right, inward, 0, mine.inwardQuarterTurns());
                if (insideFoundationApron(mine, point.x(), point.z())
                        || access.contains(ChunkPos.asLong(point.x(), point.z()))) continue;
                int pattern = Math.floorMod(point.x() * 17 + point.z() * 29, 23);
                BlockState surface = switch (pattern) {
                    case 0, 1, 2, 3 -> Blocks.COARSE_DIRT.defaultBlockState();
                    case 4, 5, 6 -> Blocks.ANDESITE.defaultBlockState();
                    case 7 -> Blocks.TUFF.defaultBlockState();
                    default -> Blocks.GRAVEL.defaultBlockState();
                };
                mineSurface(mine, slices, point, -1, surface);
            }
        }
        // Stone drainage seams and freight wear lines articulate the yard
        // without imposing another geometric plaza on the mountain foot.
        for (int inward = -58; inward <= -25; inward++) {
            for (int right : new int[]{-10, 10}) {
                if (Math.floorMod(inward, 5) == 0) continue;
                VisualPoint point = MineSurfaceLayout.local(mine.portal(), right, inward, 0,
                        mine.inwardQuarterTurns());
                if (!insideFoundationApron(mine, point.x(), point.z())
                        && !access.contains(ChunkPos.asLong(point.x(), point.z()))) {
                    mineSurface(mine, slices, point, -1, Blocks.POLISHED_ANDESITE.defaultBlockState());
                }
            }
        }
        compileMineYardFurniture(mine, access, slices);
    }

    private static void compileMineYardFurniture(AuthoredMineSitePlan mine, java.util.Set<Long> access,
                                                  Map<Long, MutableGenesisSlice> slices) {
        for (int[] local : new int[][]{{-28, -29}, {28, -29}, {-30, -55}, {30, -55}}) {
            VisualPoint point = MineSurfaceLayout.local(mine.portal(), local[0], local[1], 0,
                    mine.inwardQuarterTurns());
            if (access.contains(ChunkPos.asLong(point.x(), point.z()))) continue;
            mineSurface(mine, slices, point, 0, Blocks.COBBLESTONE_WALL.defaultBlockState());
            mineSurface(mine, slices, point, 1, Blocks.IRON_BARS.defaultBlockState());
            mineSurface(mine, slices, point, 2, Blocks.LANTERN.defaultBlockState());
        }
        // Open ore-sort bins: deliberately non-valuable rock communicates
        // function without becoming a free strategic-resource spawn.
        for (int bin = 0; bin < 3; bin++) {
            int centerRight = -25 + bin * 5;
            for (int right = centerRight - 2; right <= centerRight + 2; right++) {
                VisualPoint point = MineSurfaceLayout.local(mine.portal(), right, -46, 0,
                        mine.inwardQuarterTurns());
                if (!access.contains(ChunkPos.asLong(point.x(), point.z()))) {
                    mineSurface(mine, slices, point, 0, Blocks.COBBLED_DEEPSLATE.defaultBlockState());
                }
            }
            for (int inward = -45; inward <= -42; inward++) {
                for (int right : new int[]{centerRight - 2, centerRight + 2}) {
                    VisualPoint point = MineSurfaceLayout.local(mine.portal(), right, inward, 0,
                            mine.inwardQuarterTurns());
                    if (!access.contains(ChunkPos.asLong(point.x(), point.z()))) {
                        mineSurface(mine, slices, point, 0, Blocks.COBBLESTONE_WALL.defaultBlockState());
                    }
                }
            }
            for (int right = centerRight - 1; right <= centerRight + 1; right++) {
                VisualPoint point = MineSurfaceLayout.local(mine.portal(), right, -43, 0,
                        mine.inwardQuarterTurns());
                if (!access.contains(ChunkPos.asLong(point.x(), point.z()))) {
                    mineSurface(mine, slices, point, 0, Math.floorMod(right + bin, 2) == 0
                            ? Blocks.TUFF.defaultBlockState() : Blocks.ANDESITE.defaultBlockState());
                }
            }
        }
        for (int offset = 0; offset < 6; offset++) {
            VisualPoint timber = MineSurfaceLayout.local(mine.portal(), 23 + offset, -49, 0,
                    mine.inwardQuarterTurns());
            if (access.contains(ChunkPos.asLong(timber.x(), timber.z()))) continue;
            mineSurface(mine, slices, timber, 0, Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState());
            if (offset < 3) mineSurface(mine, slices, timber, 1, Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState());
        }
        for (int[] local : new int[][]{{22, -42}, {25, -42}, {22, -39}}) {
            VisualPoint point = MineSurfaceLayout.local(mine.portal(), local[0], local[1], 0,
                    mine.inwardQuarterTurns());
            if (!access.contains(ChunkPos.asLong(point.x(), point.z()))) {
                mineSurface(mine, slices, point, 0, Blocks.STRIPPED_SPRUCE_WOOD.defaultBlockState());
            }
        }
    }

    private static void mineSurface(AuthoredMineSitePlan mine, Map<Long, MutableGenesisSlice> slices, VisualPoint point,
                                    int offsetY, BlockState state) {
        BlockPos position = new BlockPos(point.x(), mineDatum(mine, point.x(), point.z()) + offsetY, point.z());
        slice(slices, point.x(), point.z()).replaceDecoration(
                new CompiledChunkSlice.AuthoredDecoration(position, state));
    }

    private static int mineDatum(AuthoredMineSitePlan mine, int x, int z) {
        return mine.foundations().stream().min(java.util.Comparator.comparingInt(value -> distanceFrom(value, x, z)))
                .map(value -> value.targetY() + 1).orElseThrow();
    }

    private static void compileMineFoundations(AuthoredMineSitePlan mine,
                                               Map<Long, MutableGenesisSlice> slices) {
        java.util.Set<String> activeIds = MineSurfaceLayout.materializedFoundationIds(mine);
        for (MineFoundationPlan foundation : mine.foundations().stream()
                .filter(value -> activeIds.contains(value.id())).toList()) {
            // Pads remain locally buildable, while roads make the campus read
            // as one place. A wider halo used to pre-grade Red Valley's future
            // processing and power parcels into large empty rectangles.
            int transition = Math.max(foundation.maximumCut(), foundation.maximumFill()) + 4;
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
                }
            }
        }
    }

    private static int distanceFrom(MineFoundationPlan foundation, int x, int z) {
        int dx = Math.max(foundation.footprint().min().x() - x, x - foundation.footprint().max().x());
        int dz = Math.max(foundation.footprint().min().z() - z, z - foundation.footprint().max().z());
        return Math.max(0, Math.max(dx, dz));
    }

    private static boolean insideFoundationApron(AuthoredMineSitePlan mine, int x, int z) {
        return mine.foundations().stream().anyMatch(value -> distanceFrom(value, x, z) <= value.apron());
    }

    private static BlockPos block(VisualPoint point) { return new BlockPos(point.x(), point.y(), point.z()); }

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
