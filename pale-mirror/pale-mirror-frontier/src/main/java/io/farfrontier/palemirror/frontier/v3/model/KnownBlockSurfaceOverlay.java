package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.geometry.NavigationCell;
import java.util.*;
import java.util.function.Function;

/** Shared multi-level surface projection of declared block facts. Observation availability
 * stays with the underlying geometry; a declaration never clears an unknown cell. */
public final class KnownBlockSurfaceOverlay {
    public enum Precedence { OBSERVATION_FIRST, DECLARATION_FIRST }
    public record Cell(NavigationCell value, Precedence precedence) {
        public Cell { Objects.requireNonNull(value); Objects.requireNonNull(precedence); }
    }
    public static BoundedPedestrianApproach.SurveyedSurface apply(BoundedPedestrianApproach.SurveyedSurface base,
            Function<BlockPosition, Optional<Cell>> facts, Function<TerrainColumn, List<BlockPosition>> columns) {
        return apply(base, facts, columns, area -> true);
    }
    /** Producer-declared complete coverage, not a sampled absence of blocks. */
    public static BoundedPedestrianApproach.SurveyedSurface apply(BoundedPedestrianApproach.SurveyedSurface base,
            Function<BlockPosition, Optional<Cell>> facts, Function<TerrainColumn, List<BlockPosition>> columns,
            java.util.function.Predicate<WorldBounds> touches) {
        return apply(base, facts, columns, touches, base::blockKnown);
    }
    public static BoundedPedestrianApproach.SurveyedSurface apply(BoundedPedestrianApproach.SurveyedSurface base,
            Function<BlockPosition, Optional<Cell>> facts, Function<TerrainColumn, List<BlockPosition>> columns,
            java.util.function.Predicate<WorldBounds> touches, java.util.function.Predicate<BlockPosition> known) {
        return new BoundedPedestrianApproach.SurveyedSurface() {
            @Override public OptionalInt uniformSupport(WorldBounds area) {
                return touches.test(area) ? OptionalInt.empty() : base.uniformSupport(area);
            }
            private Optional<NavigationCell> current(BlockPosition position) {
                if (!known.test(position)) return Optional.empty();
                var fact = facts.apply(position);
                if (fact.isEmpty()) return base.collisionAt(position);
                var cell = fact.orElseThrow();
                return cell.precedence() == Precedence.DECLARATION_FIRST ? Optional.of(cell.value())
                        : base.collisionAt(position).or(() -> Optional.of(cell.value()));
            }
            @Override public SurfaceAnchor at(int x, int z) { var levels = surfacesAt(x, z); return levels.isEmpty() ? null : levels.getLast(); }
            @Override public List<SurfaceAnchor> surfacesAt(int x, int z) {
                var positions = columns.apply(new TerrainColumn(x, z));
                if (positions.isEmpty()) return base.surfacesAt(x, z);
                var levels = new TreeSet<SurfaceAnchor>(Comparator.comparingInt(SurfaceAnchor::y)); levels.addAll(base.surfacesAt(x, z));
                for (var position : positions) {
                    var surface = new SurfaceAnchor(position); levels.remove(surface);
                    if (current(position).filter(cell -> cell.dry() && !cell.shape().boxes().isEmpty()).isPresent()) levels.add(surface);
                }
                return List.copyOf(levels);
            }
            @Override public boolean excluded(SurfaceAnchor surface) {
                if (facts.apply(surface.support()).isEmpty()) return base.excluded(surface);
                return current(surface.support()).filter(cell -> !cell.dry() || cell.shape().boxes().isEmpty()).isPresent();
            }
            @Override public boolean blockKnown(BlockPosition position) { return known.test(position); }
            @Override public boolean clearanceKnown(SurfaceAnchor surface) {
                for (int y = 0; y <= 2; y++) if (!blockKnown(surface.support().offset(0, y, 0))) return false;
                return base.clearanceKnown(surface);
            }
            @Override public Optional<NavigationCell> collisionAt(BlockPosition position) { return current(position); }
            @Override public boolean detailedCollisionAt(int x, int z) {
                // A wide body or a protruding native shape can intersect a neighbouring
                // column. Do not switch to the cube-only fast path beside that shape.
                if (touches.test(new WorldBounds(x-3,z-3,7,7)))
                    for(int dx=-3;dx<=3;dx++)for(int dz=-3;dz<=3;dz++)
                        if(!columns.apply(new TerrainColumn(x+dx,z+dz)).isEmpty())return true;
                return base.detailedCollisionAt(x, z);
            }
        };
    }
    private KnownBlockSurfaceOverlay() { }
}
