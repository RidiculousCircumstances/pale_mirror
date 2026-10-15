package io.farfrontier.palemirror.frontier.v3.model.geometry;

import java.util.Optional;

/** Resolves an explicit block declaration, independent of the work consuming it. */
public final class KnownBlockCollision {
    private KnownBlockCollision() { }
    public static Optional<NavigationCell> resolve(NavigationGeometryState knowledge, NavigationBlockState block) {
        var definition = knowledge.definitions().get(block);
        if (definition != null) return definition;
        // Authored bootstrap facts remain usable before the native registry has
        // published its catalog. This list grants only known state-independent
        // vanilla cubes/air, never a guessed shape for an unregistered block.
        return switch (KnownBlockClearance.navigationCell(block.kind())) {
            case EMPTY -> Optional.of(new NavigationCell(NavigationShape.EMPTY, true, Optional.of(block)));
            case FULL_SUPPORT -> Optional.of(new NavigationCell(NavigationShape.FULL, true, Optional.of(block)));
            case OBSTACLE -> Optional.empty();
        };
    }
    public static KnownBlockNavigationProjection.Cell coarse(NavigationCell cell) {
        if (!cell.dry()) return KnownBlockNavigationProjection.Cell.OBSTACLE;
        if (cell.shape().boxes().isEmpty()) return KnownBlockNavigationProjection.Cell.EMPTY;
        return cell.shape().equals(NavigationShape.FULL) ? KnownBlockNavigationProjection.Cell.FULL_SUPPORT
                : KnownBlockNavigationProjection.Cell.OBSTACLE;
    }
    public static Optional<KnownBlockNavigationProjection.Cell> coarse(NavigationGeometryState knowledge, NavigationBlockState block) {
        var definition = knowledge.definitions().get(block);
        // A declared opaque bootstrap obstacle grants no support or passage.
        // Native registration can refine it; an explicitly unavailable definition stays unknown.
        return definition == null ? Optional.of(KnownBlockClearance.navigationCell(block.kind())) : definition.map(KnownBlockCollision::coarse);
    }
    public static boolean potentialSupport(NavigationGeometryState knowledge, NavigationBlockState block) {
        return resolve(knowledge, block).filter(cell -> cell.dry() && !cell.shape().boxes().isEmpty()).isPresent();
    }
    /** Reference contact on an immutable declared block view, including unpublished candidate sites.
     * Bootstrap's authored cube view is explicit; registered shapes use the shared fractional policy. */
    public static boolean referenceContact(NavigationGeometryState knowledge,
            java.util.function.Function<io.farfrontier.palemirror.frontier.v3.model.BlockPosition, Optional<NavigationBlockState>> blocks,
            io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor support,
            io.farfrontier.palemirror.frontier.v3.model.navigation.UnitNavigationProfile profile) {
        if (knowledge.definitions().isEmpty()) {
            if (profile.footprintRadius() != 0 || !blocks.apply(support.support())
                    .filter(block -> KnownBlockClearance.navigationCell(block.kind()) == KnownBlockNavigationProjection.Cell.FULL_SUPPORT).isPresent()) return false;
            for (int y = 1; y <= profile.bodyLayers(); y++) if (!blocks.apply(support.support().offset(0, y, 0))
                    .filter(block -> KnownBlockClearance.emptyCollision(block.kind())).isPresent()) return false;
            return true;
        }
        return !NavigationBodyGeometry.contacts(position -> blocks.apply(position).flatMap(block -> resolve(knowledge, block)),
                support, profile, .5, .5).isEmpty();
    }
}
