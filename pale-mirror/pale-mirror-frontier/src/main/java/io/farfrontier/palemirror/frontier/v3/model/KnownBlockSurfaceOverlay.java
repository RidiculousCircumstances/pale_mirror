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
        return new BoundedPedestrianApproach.SurveyedSurface() {
            private Optional<NavigationCell> current(BlockPosition position) {
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
            @Override public boolean blockKnown(BlockPosition position) { return base.blockKnown(position); }
            @Override public boolean clearanceKnown(SurfaceAnchor surface) {
                for (int y = 0; y <= 2; y++) if (!blockKnown(surface.support().offset(0, y, 0))) return false;
                return base.clearanceKnown(surface);
            }
            @Override public Optional<NavigationCell> collisionAt(BlockPosition position) { return current(position); }
            @Override public boolean detailedCollisionAt(int x, int z) {
                return !columns.apply(new TerrainColumn(x, z)).isEmpty() || base.detailedCollisionAt(x, z);
            }
        };
    }
    private KnownBlockSurfaceOverlay() { }
}
