package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.forestry.*;
import io.farfrontier.palemirror.frontier.v3.model.geometry.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianGeometryRevision.Region;
import java.util.*;

/** Forest geometry provider only. No routes, extraction progress, resource issue or native chunk reads. */
final class ForestryKnownGeometry implements KnownNaturalGeometry.Provider {
    private final ImmutableInputView<KnownNaturalGeometry.Snapshot> snapshots = new ImmutableInputView<>();
    private final ImmutableInputView<Set<NavigationBlockState>> palettes = new ImmutableInputView<>();
    @Override public KnownNaturalGeometry.Key key() { return KnownNaturalGeometry.Key.FOREST; }
    @Override public KnownNaturalGeometry.Snapshot snapshot(FrontierWorldState state) {
        return snapshots.get(List.of(state.bootstrap(), state.forest().regions()), () -> new Snapshot(state.bootstrap(), state.forest()));
    }
    @Override public Set<NavigationBlockState> palette(FrontierWorldState state) {
        return palettes.get(List.of(state.bootstrap().ruleset().forestry()), () -> paletteFor(state));
    }
    private Set<NavigationBlockState> paletteFor(FrontierWorldState state) {
        if (!state.bootstrap().ruleset().forestry().enabled()) return Set.of();
        var values = new HashSet<NavigationBlockState>();
        for (var rule : state.bootstrap().ruleset().forestry().harvests()) for (var axis : List.of("x", "y", "z"))
            values.add(new NavigationBlockState(rule.logKind(), Map.of("axis", axis)));
        for (int distance = 1; distance <= 7; distance++) values.add(new NavigationBlockState("minecraft:oak_leaves",
                Map.of("distance", Integer.toString(distance), "persistent", "false", "waterlogged", "false")));
        return Set.copyOf(values);
    }
    private record Cell(NavigationCell value, boolean aftermath) { }
    private record Image(Map<BlockPosition, Cell> cells, Map<TerrainColumn, List<BlockPosition>> columns) { }
    private static final class Snapshot implements KnownNaturalGeometry.Snapshot {
        private final FrontierBootstrap bootstrap;
        private final ForestState forest;
        private final GrayboxForestPlan plan;
        private final BoundedValueMemo<ForestRegion, Image> images = new BoundedValueMemo<>(128);
        Snapshot(FrontierBootstrap bootstrap, ForestState forest) { this.bootstrap = bootstrap; this.forest = forest;
            this.plan = GrayboxForestPlan.compile(bootstrap); }
        private Image image(ForestRegion region) {
            return images.get(region, () -> {
                var cells = new HashMap<BlockPosition, Cell>();
                plan.blocksIn(region).forEach((position, block) -> cells.put(position, new Cell(new NavigationCell(NavigationShape.FULL, true,
                        Optional.of(new NavigationBlockState(block.kind(), block.properties()))), false)));
                var changedTrees = forest.touching(region);
                // Withdraw the old generation first. Its tombstone may share a position
                // with a newly captured tree and must never erase the successor image.
                for (var tree : changedTrees) for (int i = 0; i < tree.declaration().logs().size(); i++)
                    if (!tree.present(i) && !tree.extracted(i)) cells.remove(tree.declaration().logs().position(tree.declaration().origin(), i));
                for (var tree : changedTrees) {
                    var declaration = tree.declaration();
                    for (int i = 0; i < declaration.logs().size(); i++) {
                        var position = declaration.logs().position(declaration.origin(), i);
                        if (!ForestRegion.containing(position).equals(region)) continue;
                        if (tree.present(i)) {
                            var block = declaration.logs().block(i);
                            cells.put(position, new Cell(new NavigationCell(NavigationShape.FULL, true,
                                    Optional.of(new NavigationBlockState(block.kind(), block.properties()))), false));
                        } else if (tree.extracted(i)) cells.put(position, new Cell(NavigationCell.AIR, true));
                    }
                }
                var columns = new HashMap<TerrainColumn, List<BlockPosition>>();
                cells.keySet().forEach(position -> columns.computeIfAbsent(new TerrainColumn(position.x(), position.z()), ignored -> new ArrayList<>()).add(position));
                columns.replaceAll((key, values) -> List.copyOf(values));
                return new Image(Collections.unmodifiableMap(cells), Collections.unmodifiableMap(columns));
            });
        }
        @Override public BoundedPedestrianApproach.SurveyedSurface overlay(BoundedPedestrianApproach.SurveyedSurface base) {
            if (!bootstrap.ruleset().forestry().enabled()) return base;
            return new BoundedPedestrianApproach.SurveyedSurface() {
                private Cell cell(BlockPosition position) { return image(ForestRegion.containing(position)).cells().get(position); }
                private Optional<NavigationCell> current(BlockPosition position, Cell fact) {
                    if (fact == null) return base.collisionAt(position);
                    return fact.aftermath() ? Optional.of(fact.value()) : base.collisionAt(position).or(() -> Optional.of(fact.value()));
                }
                @Override public SurfaceAnchor at(int x, int z) { var values = surfacesAt(x, z); return values.isEmpty() ? null : values.getLast(); }
                @Override public List<SurfaceAnchor> surfacesAt(int x, int z) {
                    var image = image(new ForestRegion(x >> 4, z >> 4));
                    var positions = image.columns().get(new TerrainColumn(x, z));
                    if (positions == null) return base.surfacesAt(x, z);
                    var values = new TreeSet<SurfaceAnchor>(Comparator.comparingInt(SurfaceAnchor::y)); values.addAll(base.surfacesAt(x, z));
                    for (var position : positions) {
                        var surface = new SurfaceAnchor(position); values.remove(surface);
                        var cell = current(position, image.cells().get(position));
                        if (cell.filter(value -> value.dry() && !value.shape().boxes().isEmpty()).isPresent()) values.add(surface);
                    }
                    return List.copyOf(values);
                }
                @Override public boolean excluded(SurfaceAnchor surface) {
                    var fact = cell(surface.support()); if (fact == null) return base.excluded(surface);
                    var current = current(surface.support(), fact);
                    return current.isPresent() && (!current.orElseThrow().dry() || current.orElseThrow().shape().boxes().isEmpty());
                }
                @Override public boolean blockKnown(BlockPosition position) { return base.blockKnown(position); }
                @Override public boolean clearanceKnown(SurfaceAnchor surface) {
                    for (int y = 0; y <= 2; y++) if (!blockKnown(surface.support().offset(0, y, 0))) return false;
                    return base.clearanceKnown(surface);
                }
                @Override public Optional<NavigationCell> collisionAt(BlockPosition position) { return current(position, cell(position)); }
                @Override public boolean detailedCollisionAt(int x, int z) {
                    return image(new ForestRegion(x >> 4, z >> 4)).columns().containsKey(new TerrainColumn(x, z)) || base.detailedCollisionAt(x, z);
                }
            };
        }
        @Override public Set<Region> changesFrom(KnownNaturalGeometry.Snapshot previous) {
            if (!(previous instanceof Snapshot old) || old.bootstrap != bootstrap)
                throw new IllegalArgumentException("natural geometry comparison requires one captured world lineage");
            if (forest.regions() == old.forest.regions()) return Set.of();
            var regions = new HashSet<>(forest.regions().keySet()); regions.addAll(old.forest.regions().keySet());
            var changed = new HashSet<Region>();
            for (var region : regions) {
                var before = old.forest.regions().getOrDefault(region, Map.of()); var after = forest.regions().getOrDefault(region, Map.of());
                if (before == after) continue;
                var ids = new HashSet<>(before.keySet()); ids.addAll(after.keySet());
                for (var id : ids) if (!Objects.equals(before.get(id), after.get(id))) {
                    var tree = after.containsKey(id) ? after.get(id) : before.get(id); var declaration = tree.declaration();
                    for (int i = 0; i < declaration.logs().size(); i++) {
                        var p = declaration.logs().position(declaration.origin(), i);
                        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) changed.add(Region.at(p.offset(dx, 0, dz)));
                    }
                }
            }
            return Set.copyOf(changed);
        }
    }
}
