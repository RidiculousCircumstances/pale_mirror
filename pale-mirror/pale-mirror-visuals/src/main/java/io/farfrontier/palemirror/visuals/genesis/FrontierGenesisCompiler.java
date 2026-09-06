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
import net.minecraft.world.level.block.state.BlockState;

/** Compiles global manifests once into independent chunk-local worldgen slices. */
public final class FrontierGenesisCompiler {
    public static final int CATALOG_VERSION = 36;

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
        // Curated modules own their complete true footprints. Settlement
        // circulation is compiled afterwards but explicitly skips those
        // columns and terminates at the module's real semantic threshold.
        for (VisualModulePlacement module : seed.modules()) compileModule(module, slices);
        Map<Long, Integer> settlementDatums = new java.util.HashMap<>(
                seed.settlementSite().surfacePlan().columns().size() * 4 / 3 + 1);
        seed.settlementSite().surfacePlan().columns().forEach(value ->
                settlementDatums.put(ChunkPos.asLong(value.x(), value.z()), value.groundY()));
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
        BlockState casing = optionalCreate("andesite_casing", mine.inwardQuarterTurns());
        BlockState shaft = optionalCreate("shaft", mine.inwardQuarterTurns());
        BlockState fan = optionalCreate("encased_fan", mine.inwardQuarterTurns());
        if (casing == null || shaft == null || fan == null) return;
        BlockPos center = new BlockPos((power.footprint().min().x() + power.footprint().max().x()) / 2,
                power.targetY() + 2, (power.footprint().min().z() + power.footprint().max().z()) / 2);
        Direction facing = direction(mine.inwardQuarterTurns());
        put(slices, center, casing); put(slices, center.relative(facing), shaft);
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
                new BlockPos(block.position().x(), block.position().y(), block.position().z()),
                new CompiledChunkSlice.CompiledBlock(block.state(), block.blockEntityData())));
    }

    private static void compileMine(AuthoredMineSitePlan mine, FrontierPalette palette,
                                    Map<Long, MutableGenesisSlice> slices) {
        compileMineFoundations(mine, slices);
        List<MineAccessGenesisCompiler.AccessRoute> accessRoutes = MineAccessGenesisCompiler.routes(mine);
        java.util.Set<Long> access = MineAccessGenesisCompiler.occupied(accessRoutes);
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
        compileMineIndustrialDetails(mine, palette, access, slices);
        // Entrance clearance is deliberately the final MineSite geometry
        // writer. Curated NBT, the underground grammar and decorative frames
        // may shape the portal, but none may close its public throat.
        compileMineEntrances(mine, accessRoutes, slices);
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
                    route.minePortal() ? mine.portal().y() : foundation.targetY() + 1,
                    outside.z() + route.entryStepZ());
            int tangentX = -route.entryStepZ();
            int tangentZ = route.entryStepX();
            int halfWidth = route.minePortal() ? 2 : 0;
            int outsideDepth = route.minePortal() ? -5 : -2;
            int insideDepth = route.minePortal() ? 4 : 2;
            int clearHeight = route.minePortal() ? 3 : 2;
            for (int depth = outsideDepth; depth <= insideDepth; depth++) for (int across = -halfWidth;
                    across <= halfWidth; across++) for (int up = 0; up <= clearHeight; up++) {
                put(slices, threshold.offset(route.entryStepX() * depth + tangentX * across, up,
                        route.entryStepZ() * depth + tangentZ * across), Blocks.AIR.defaultBlockState());
            }
            // The final throat writer owns one continuous floor on both sides
            // of the threshold.  Depending on the adit's initial slope, the
            // underground grammar alone cannot guarantee every inner lane at
            // the surface datum.
            for (int depth = outsideDepth; depth <= insideDepth; depth++) for (int across = -halfWidth;
                    across <= halfWidth; across++) {
                put(slices, threshold.offset(route.entryStepX() * depth + tangentX * across, -1,
                                route.entryStepZ() * depth + tangentZ * across),
                        route.minePortal() && Math.abs(across) <= 1
                                ? Blocks.POLISHED_ANDESITE.defaultBlockState()
                                : Blocks.COBBLESTONE.defaultBlockState());
            }
        }
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
                    int blendDistance = distance;
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

    private static BlockPos block(VisualPoint point) { return new BlockPos(point.x(), point.y(), point.z()); }

    private static void put(Map<Long, MutableGenesisSlice> slices, BlockPos position, BlockState state) {
        put(slices, position, new CompiledChunkSlice.CompiledBlock(state));
    }

    private static void put(Map<Long, MutableGenesisSlice> slices, BlockPos position,
                            CompiledChunkSlice.CompiledBlock block) {
        slice(slices, position.getX(), position.getZ()).blocks.put(position.immutable(), block);
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
