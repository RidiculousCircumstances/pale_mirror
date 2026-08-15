package io.farfrontier.palemirror.visuals.foundry;

import io.farfrontier.palemirror.api.AuthoredMineSitePlan;
import io.farfrontier.palemirror.api.AuthoredRegionSeed;
import io.farfrontier.palemirror.api.SiteSurfaceColumn;
import io.farfrontier.palemirror.api.VisualBounds;
import io.farfrontier.palemirror.api.VisualModulePlacement;
import io.farfrontier.palemirror.api.VisualPoint;
import io.farfrontier.palemirror.visuals.genesis.CompiledChunkSlice;
import io.farfrontier.palemirror.visuals.genesis.CompiledGenesisCatalog;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;

/** Region-local projection of the immutable global genesis catalog. */
final class FoundryRegionIndex {
    private final AuthoredRegionSeed region;
    private final Map<BlockPos, ExpectedCell> expected;
    private final Map<Long, SiteSurfaceColumn> surface;
    private final Map<Long, CompiledChunkSlice.RailColumn> rails;
    private final List<VisualModulePlacement> modules;

    private FoundryRegionIndex(AuthoredRegionSeed region, Map<BlockPos, ExpectedCell> expected,
                               Map<Long, SiteSurfaceColumn> surface,
                               Map<Long, CompiledChunkSlice.RailColumn> rails,
                               List<VisualModulePlacement> modules) {
        this.region = region;
        this.expected = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(expected));
        this.surface = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(surface));
        this.rails = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(rails));
        this.modules = List.copyOf(modules);
    }

    static FoundryRegionIndex build(AuthoredRegionSeed region, CompiledGenesisCatalog catalog) {
        Map<Long, SiteSurfaceColumn> surface = new LinkedHashMap<>();
        region.settlementSite().surfacePlan().columns().forEach(value -> surface.put(column(value.x(), value.z()), value));
        List<VisualModulePlacement> modules = new ArrayList<>();
        modules.addAll(region.modules());
        modules.addAll(region.primaryMineSite().initialModules());
        modules.addAll(region.alternateMineSite().initialModules());
        java.util.Set<Long> railEnvelope = railEnvelope(region, 4);

        Map<BlockPos, ExpectedCell> expected = new LinkedHashMap<>();
        Map<Long, CompiledChunkSlice.RailColumn> rails = new LinkedHashMap<>();
        catalog.chunks().values().forEach(slice -> {
            slice.blocks().forEach((position, state) -> {
                if (inside(region, railEnvelope, position.getX(), position.getZ())) {
                    expected.put(position, new ExpectedCell(state, owner(region, modules, surface, position), "block"));
                }
            });
            slice.decorations().forEach(value -> {
                BlockPos position = value.position();
                if (inside(region, railEnvelope, position.getX(), position.getZ())) {
                    expected.put(position, new ExpectedCell(value.state(), owner(region, modules, surface, position),
                            "decoration"));
                }
            });
            slice.rails().forEach(value -> {
                if (!railEnvelope.contains(column(value.rail().getX(), value.rail().getZ()))) return;
                expected.put(value.rail().below(), new ExpectedCell(value.support(), "baseline_railway", "rail_support"));
                expected.put(value.rail(), new ExpectedCell(value.railState(), "baseline_railway", "rail"));
                rails.put(column(value.rail().getX(), value.rail().getZ()), value);
            });
        });
        return new FoundryRegionIndex(region, expected, surface, rails, modules);
    }

    AuthoredRegionSeed region() { return region; }
    Map<BlockPos, ExpectedCell> expected() { return expected; }
    Map<Long, SiteSurfaceColumn> surface() { return surface; }
    Map<Long, CompiledChunkSlice.RailColumn> rails() { return rails; }
    List<VisualModulePlacement> modules() { return modules; }

    ExpectedCell expected(BlockPos position) { return expected.get(position); }
    SiteSurfaceColumn surface(int x, int z) { return surface.get(column(x, z)); }

    int plannedGroundY(int x, int z) {
        SiteSurfaceColumn exact = surface(x, z);
        if (exact != null) return exact.groundY();
        Integer mine = mineGround(region.primaryMineSite(), x, z);
        if (mine == null) mine = mineGround(region.alternateMineSite(), x, z);
        return mine == null ? region.anchor().y() : mine;
    }

    String owner(BlockPos position) {
        ExpectedCell cell = expected.get(position);
        if (cell != null) return cell.ownerId();
        SiteSurfaceColumn column = surface(position.getX(), position.getZ());
        return column == null ? "unowned" : column.ownerId();
    }

    private static Integer mineGround(AuthoredMineSitePlan mine, int x, int z) {
        return mine.foundations().stream().filter(value -> horizontal(value.footprint(), x, z))
                .map(value -> value.targetY() + 1).findFirst().orElse(null);
    }

    private static String owner(AuthoredRegionSeed region, List<VisualModulePlacement> modules,
                                Map<Long, SiteSurfaceColumn> surface, BlockPos position) {
        VisualPoint point = new VisualPoint(position.getX(), position.getY(), position.getZ());
        for (VisualModulePlacement module : modules) if (module.footprint().contains(point)) return module.instanceId();
        SiteSurfaceColumn column = surface.get(column(position.getX(), position.getZ()));
        if (column != null) return column.ownerId();
        if (region.primaryMineSite().bounds().contains(point)) return region.primaryMineSite().siteId();
        if (region.alternateMineSite().bounds().contains(point)) return region.alternateMineSite().siteId();
        return region.planId();
    }

    private static boolean inside(AuthoredRegionSeed region, java.util.Set<Long> railEnvelope, int x, int z) {
        return region.settlementSite().environment().insideTransition(x, z)
                || expanded(region.primaryMineSite().bounds(), x, z, 20)
                || expanded(region.alternateMineSite().bounds(), x, z, 20)
                || railEnvelope.contains(column(x, z));
    }

    private static java.util.Set<Long> railEnvelope(AuthoredRegionSeed region, int radius) {
        java.util.Set<Long> result = new java.util.HashSet<>();
        int squared = radius * radius;
        for (VisualPoint node : region.baselineRailNodes()) {
            for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz <= squared) result.add(column(node.x() + dx, node.z() + dz));
            }
        }
        return java.util.Set.copyOf(result);
    }

    private static boolean expanded(VisualBounds bounds, int x, int z, int amount) {
        return x >= bounds.min().x() - amount && x <= bounds.max().x() + amount
                && z >= bounds.min().z() - amount && z <= bounds.max().z() + amount;
    }

    private static boolean horizontal(VisualBounds bounds, int x, int z) {
        return x >= bounds.min().x() && x <= bounds.max().x()
                && z >= bounds.min().z() && z <= bounds.max().z();
    }

    static long column(int x, int z) { return ChunkPos.asLong(x, z); }

    record ExpectedCell(BlockState state, String ownerId, String kind) { }
}
