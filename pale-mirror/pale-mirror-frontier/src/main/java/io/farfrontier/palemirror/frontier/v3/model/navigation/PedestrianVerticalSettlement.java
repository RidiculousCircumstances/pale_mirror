package io.farfrontier.palemirror.frontier.v3.model.navigation;

import io.farfrontier.palemirror.frontier.v3.model.BodyPosition;
import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import io.farfrontier.palemirror.frontier.v3.model.geometry.NavigationBodyGeometry;
import java.util.Comparator;
import java.util.Optional;

/** A saved airborne pose may settle vertically; it is neither a route nor a supported contact. */
public final class PedestrianVerticalSettlement {
    private PedestrianVerticalSettlement() { }

    /** Geometry candidate only. Native reconstruction must still validate actual physical placement. */
    public static Optional<SurfaceAnchor> candidate(BodyPosition pose, PedestrianRouteGeometry geometry, int maximumDrop) {
        if (!geometry.bounds().contains(pose.supportingSurface().support())
                || geometry.knowsSupport(pose.supportingSurface())) return Optional.empty();
        // Select the highest known floor, never fall through an obstructed/excluded floor to another one.
        return geometry.surfacesAt(pose.x(), pose.z()).stream()
                .filter(surface -> surface.x() == pose.x() && surface.z() == pose.z()
                        && surface.y() < pose.y() - 1 && pose.y() - 1L - surface.y() <= maximumDrop)
                .max(Comparator.comparingInt(SurfaceAnchor::y));
    }

    /** COLD requires the complete swept body to be known, dry and clear, including the landing. */
    public static Optional<SurfaceAnchor> confirmed(BodyPosition pose, PedestrianRouteGeometry geometry,
                                                   UnitNavigationProfile profile) {
        return candidate(pose, geometry, profile.dropBlocks()).filter(geometry::clearStation).filter(surface ->
                geometry.collisions().filter(view -> NavigationBodyGeometry.contacts(view, surface, profile, .5, .5)
                        .stream().anyMatch(contact -> NavigationBodyGeometry.canDescend(view, profile,
                                pose.x() + .5, pose.y(), pose.z() + .5, contact))).isPresent());
    }
}
