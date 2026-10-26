package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import io.farfrontier.palemirror.frontier.v3.model.navigation.KnownPedestrianNavigation;
import java.util.List;
import java.util.function.Supplier;

/** COLD knowledge is an advisory HOT path, never the native permission or verdict.
 * Callers must validate the exact goal, activity and task restrictions separately.
 * The shared native provider still proves loaded geometry, collision and arrival. */
final class FrontierV3NativeRouteHint {
    private FrontierV3NativeRouteHint() { }
    static List<SurfaceAnchor> optional(Supplier<List<SurfaceAnchor>> query) {
        try { return List.copyOf(query.get()); }
        catch (KnownPedestrianNavigation.RouteUnavailable unavailable) {
            return switch (unavailable.status()) {
                case PLANNING, NO_PATH, UNKNOWN_GEOMETRY, SAFETY_LIMIT -> List.of();
                case FOUND, SEGMENT_READY, ESTIMATED -> throw new IllegalStateException(
                        "route hint reported a successful status as unavailable", unavailable);
            };
        }
    }
}
