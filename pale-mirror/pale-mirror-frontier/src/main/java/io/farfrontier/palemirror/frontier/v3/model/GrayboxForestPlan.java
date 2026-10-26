package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.forestry.*;
import io.farfrontier.palemirror.frontier.v3.model.extraction.BlockExtraction;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Immutable authored nature. Chunk generation and COLD knowledge use this exact provider;
 * no mutable runtime, native terrain lookup or retained object per tree/log. */
public final class GrayboxForestPlan {
    public static final String PROVIDER = "pale_mirror:graybox_oak_forest_v1";
    private static final BlockExtraction.Block LOG = new BlockExtraction.Block("minecraft:oak_log", Map.of("axis", "y"));
    private static final BoundedValueMemo<BootstrapKey, GrayboxForestPlan> PLANS = new BoundedValueMemo<>(2);
    private record BootstrapKey(FrontierBootstrap bootstrap) {
        @Override public boolean equals(Object other) { return other instanceof BootstrapKey key && bootstrap == key.bootstrap; }
        @Override public int hashCode() { return System.identityHashCode(bootstrap); }
    }
    private record Patch(ForestryRules.Patch rule, BlockPosition centre) { }
    public record Header(TreeReference tree, BlockPosition origin, int logs) { }
    private final FrontierBootstrap bootstrap;
    private final List<Patch> patches;
    private final Set<TerrainColumn> protectedColumns;
    private final Set<ForestRegion> coverage;
    private final BoundedValueMemo<ForestRegion, List<TreeDescriptor>> regions = new BoundedValueMemo<>(128);
    private final BoundedValueMemo<io.farfrontier.palemirror.frontier.v3.api.SubjectId, List<Header>> headers = new BoundedValueMemo<>(12);

    private GrayboxForestPlan(FrontierBootstrap bootstrap) {
        this.bootstrap = bootstrap;
        var configured = new ArrayList<Patch>();
        for (var rule : bootstrap.ruleset().forestry().patches()) {
            var home = bootstrap.settlements().stream().filter(s -> s.id().equals(rule.knownBy())).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("forest knowledge names an absent settlement"));
            configured.add(new Patch(rule, home.anchor()));
        }
        patches = List.copyOf(configured);
        var covered = new HashSet<ForestRegion>();
        for (var patch : patches) {
            int radius = patch.rule().outerRadius() + 2;
            for (int x = Math.floorDiv(patch.centre().x() - radius, 16); x <= Math.floorDiv(patch.centre().x() + radius, 16); x++)
                for (int z = Math.floorDiv(patch.centre().z() - radius, 16); z <= Math.floorDiv(patch.centre().z() + radius, 16); z++)
                    covered.add(new ForestRegion(x, z));
        }
        coverage = Collections.unmodifiableSet(covered);
        if (patches.isEmpty()) { protectedColumns = Set.of(); return; }
        var excluded = new HashSet<TerrainColumn>();
        FrontierGrayboxPlan.compileInitialSettlementFabric(bootstrap).cells().keySet().forEach(position -> exclude(excluded, position));
        for (var home : bootstrap.settlements()) home.residents().forEach(resident -> exclude(excluded, resident.home()));
        for (var site : FrontierResourceSitePlan.compile(bootstrap).values())
            site.managedSlots().forEach(position -> exclude(excluded, position));
        var roads = FrontierRouteNetwork.footprint(bootstrap, RouteTopology.initial());
        roads.foundationCells().forEach(position -> exclude(excluded, position));
        roads.surfaceCells().forEach(position -> exclude(excluded, position));
        for (var deposit : GrayboxQuarryPlan.compile(bootstrap).sites().deposits().values()) {
            deposit.site().layout().fixedBlocks().keySet().forEach(position -> exclude(excluded, position));
            deposit.site().layout().terrainSupports().keySet().forEach(position -> exclude(excluded, position));
            deposit.site().layout().cells().forEach(cell -> exclude(excluded, cell.source()));
        }
        protectedColumns = Collections.unmodifiableSet(excluded);
    }
    private static void exclude(Set<TerrainColumn> columns, BlockPosition position) { columns.add(new TerrainColumn(position.x(), position.z())); }
    public static GrayboxForestPlan compile(FrontierBootstrap bootstrap) {
        return PLANS.get(new BootstrapKey(Objects.requireNonNull(bootstrap)), () -> new GrayboxForestPlan(bootstrap));
    }
    /** At most this chunk and the two-block canopy margin, not all trees in the world. */
    public boolean mayTouch(ForestRegion region) { return coverage.contains(region); }
    public List<TreeDescriptor> touching(ForestRegion region) {
        return mayTouch(region) ? regions.get(region, () -> inRegion(region)) : List.of();
    }
    private List<TreeDescriptor> inRegion(ForestRegion region) {
        var result = new ArrayList<TreeDescriptor>();
        int minX = region.x() * 16 - 2, maxX = region.x() * 16 + 17;
        int minZ = region.z() * 16 - 2, maxZ = region.z() * 16 + 17;
        for (var patch : patches) {
            var rule = patch.rule(); int spacing = rule.spacing();
            int fromX = Math.max(patch.centre().x() - rule.outerRadius(), Math.floorDiv(minX, spacing) * spacing);
            int fromZ = Math.max(patch.centre().z() - rule.outerRadius(), Math.floorDiv(minZ, spacing) * spacing);
            // Use a global lattice so query boundaries cannot change a tree's position.
            fromX = Math.ceilDiv(fromX, spacing) * spacing; fromZ = Math.ceilDiv(fromZ, spacing) * spacing;
            for (int x = fromX; x <= maxX && x <= patch.centre().x() + rule.outerRadius(); x += spacing)
                for (int z = fromZ; z <= maxZ && z <= patch.centre().z() + rule.outerRadius(); z += spacing) {
                    var header = header(patch, x, z); if (header == null) continue;
                    result.add(describe(header));
                }
        }
        result.sort(Comparator.comparing(TreeDescriptor::id)); return List.copyOf(result);
    }
    /** Small origin catalogue for one explicitly surveyed patch, without per-log storage. */
    public List<Header> knownBy(io.farfrontier.palemirror.frontier.v3.api.SubjectId home) {
        return headers.get(home, () -> {
            var result = new ArrayList<Header>();
            for (var patch : patches) if (patch.rule().knownBy().equals(home)) {
                int spacing = patch.rule().spacing(), radius = patch.rule().outerRadius();
                int minX = Math.ceilDiv(patch.centre().x() - radius, spacing) * spacing;
                int minZ = Math.ceilDiv(patch.centre().z() - radius, spacing) * spacing;
                for (int x = minX; x <= patch.centre().x() + radius; x += spacing)
                    for (int z = minZ; z <= patch.centre().z() + radius; z += spacing) {
                        var header = header(patch, x, z); if (header != null) result.add(header);
                    }
            }
            result.sort(Comparator.comparing(value -> value.tree().id())); return List.copyOf(result);
        });
    }
    public TreeDescriptor describe(Header header) {
        int[] logs = new int[header.logs()]; for (int y = 0; y < logs.length; y++) logs[y] = TreeBlocks.encode(0, y, 0, 0);
        return new TreeDescriptor(header.tree().id(), header.tree().generation(), PROVIDER, "minecraft:oak", header.origin(),
                new TreeBlocks(List.of(LOG), logs));
    }
    private Header header(Patch patch, int x, int z) {
        var rule = patch.rule(); long dx = x - patch.centre().x(), dz = z - patch.centre().z(), d2 = dx * dx + dz * dz;
        if (d2 < (long) rule.innerRadius() * rule.innerRadius() || d2 > (long) rule.outerRadius() * rule.outerRadius()) return null;
        long sample = mix(bootstrap.seed() ^ rule.salt() ^ ((long) x << 32) ^ (z & 0xFFFFFFFFL));
        if (Math.floorMod(sample, 1000) >= rule.densityPermille() || !clear(x, z)) return null;
        int height = 4 + (int) Math.floorMod(sample >>> 12, 3);
        var origin = new BlockPosition(x, bootstrap.terrain().supportYAt(x, z) + 1, z);
        var identity = UUID.nameUUIDFromBytes((bootstrap.worldId().value() + "|" + bootstrap.seed() + "|"
                + PROVIDER + "|" + rule.id() + "|" + x + "|" + z).getBytes(StandardCharsets.UTF_8));
        return new Header(new TreeReference(new TreeId(identity), 1, ForestRegion.containing(origin)), origin, height);
    }
    private boolean clear(int x, int z) {
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++)
            if (!bootstrap.bounds().contains(new BlockPosition(x + dx, 0, z + dz))
                    || protectedColumns.contains(new TerrainColumn(x + dx, z + dz))) return false;
        return true;
    }
    /** Current initial native image only. Runtime depletion is owned elsewhere. */
    public Map<BlockPosition, BlockExtraction.Block> blocksIn(ForestRegion region) {
        var blocks = new HashMap<BlockPosition, BlockExtraction.Block>();
        for (var tree : touching(region)) {
            int height = tree.logs().size();
            for (int y = height - 2; y <= height; y++) {
                int radius = y == height ? 1 : 2;
                for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.abs(dx) == radius && Math.abs(dz) == radius) continue;
                    var p = tree.origin().offset(dx, y, dz);
                    if (!ForestRegion.containing(p).equals(region)) continue;
                    int distance = Math.abs(dx) + Math.abs(dz) + Math.max(0, y - height + 1);
                    if (distance == 0) continue;
                    blocks.putIfAbsent(p, new BlockExtraction.Block("minecraft:oak_leaves", Map.of("distance", Integer.toString(distance),
                            "persistent", "false", "waterlogged", "false")));
                }
            }
            for (int i = 0; i < height; i++) {
                var p = tree.logs().position(tree.origin(), i);
                if (ForestRegion.containing(p).equals(region)) blocks.put(p, tree.logs().block(i));
            }
        }
        return Collections.unmodifiableMap(blocks);
    }
    private static long mix(long value) {
        value = (value ^ value >>> 30) * 0xBF58476D1CE4E5B9L;
        value = (value ^ value >>> 27) * 0x94D049BB133111EBL;
        return value ^ value >>> 31;
    }
}
