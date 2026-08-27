package io.farfrontier.palemirror.internal.world;

import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxLayout;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxSnapshot;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Bounded read-only player-eye camera plan for the source graybox.
 *
 * <p>The plan is deliberately derived from one immutable source frame.  It
 * does not inspect chunks, create audit markers, or make a missing campaign
 * scene look present.  Teleporting an operator to one of these poses is an
 * explicit player action and remains the normal chunk-demand path.</p>
 */
final class SourceGrayboxAuditViews {
    private static final int MAX_VIEWS = 5;
    private static final int OUTER_STANDOFF = 14;
    private static final int ROUTE_STANDOFF = 12;
    private static final int LOCAL_STANDOFF = 12;

    private SourceGrayboxAuditViews() { }

    /** Returns at most one current representative for each player-readable source scene kind. */
    static List<View> from(ReferenceGrayboxSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        List<View> result = new ArrayList<>(MAX_VIEWS);
        bestSettlement(snapshot).ifPresent(value -> result.add(settlement(snapshot, value)));
        bestHiveOrgan(snapshot).ifPresent(value -> result.add(hiveOrgan(snapshot, value)));
        bestRoute(snapshot).ifPresent(value -> result.add(route(snapshot, value)));
        bestActivity(snapshot).ifPresent(value -> result.add(activity(snapshot, value)));
        bestFieldPost(snapshot).ifPresent(value -> result.add(fieldPost(snapshot, value)));
        if (result.size() > MAX_VIEWS) throw new IllegalStateException("source graybox audit view limit exceeded");
        return List.copyOf(result);
    }

    /**
     * Resolves a copied audit id against the last advertised bounded plan
     * before considering the newest source frame.  An operator may take a few
     * seconds to paste an id while the autonomous clock commits a new day; the
     * originally advertised camera is still a valid player-demand pose and
     * must not turn into a spurious command failure.
     */
    static Optional<View> resolve(String requestedId, List<View> advertised, ReferenceGrayboxSnapshot current) {
        Objects.requireNonNull(requestedId, "requestedId");
        Objects.requireNonNull(advertised, "advertised");
        Objects.requireNonNull(current, "current");
        String id = requestedId.trim();
        if (id.isEmpty()) return Optional.empty();
        return advertised.stream().filter(candidate -> candidate.id().equals(id)).findFirst()
                .or(() -> from(current).stream().filter(candidate -> candidate.id().equals(id)).findFirst());
    }

    private static java.util.Optional<ReferenceGrayboxSnapshot.Settlement> bestSettlement(ReferenceGrayboxSnapshot snapshot) {
        return snapshot.settlements().stream().min(Comparator
                .comparing(ReferenceGrayboxSnapshot.Settlement::alive).reversed()
                .thenComparing(Comparator.comparingDouble(SourceGrayboxAuditViews::settlementUrgency).reversed())
                .thenComparingInt(ReferenceGrayboxSnapshot.Settlement::id));
    }

    private static java.util.Optional<ReferenceGrayboxSnapshot.HiveOrgan> bestHiveOrgan(ReferenceGrayboxSnapshot snapshot) {
        return snapshot.hiveOrgans().stream().min(Comparator
                .comparing((ReferenceGrayboxSnapshot.HiveOrgan value) -> value.vitality() > 0.0d).reversed()
                .thenComparing(Comparator.comparingDouble(ReferenceGrayboxSnapshot.HiveOrgan::vitality).reversed())
                .thenComparingInt(ReferenceGrayboxSnapshot.HiveOrgan::id));
    }

    private static java.util.Optional<ReferenceGrayboxSnapshot.Route> bestRoute(ReferenceGrayboxSnapshot snapshot) {
        return snapshot.routes().stream().min(Comparator
                .comparing((ReferenceGrayboxSnapshot.Route value) -> value.disrupted() || value.quarantined()).reversed()
                .thenComparing(Comparator.comparingDouble(SourceGrayboxAuditViews::routeUrgency).reversed())
                .thenComparing(ReferenceGrayboxSnapshot.Route::id));
    }

    private static java.util.Optional<ReferenceGrayboxSnapshot.Activity> bestActivity(ReferenceGrayboxSnapshot snapshot) {
        return snapshot.activities().stream().filter(value -> !value.terminal()).min(Comparator
                .comparing((ReferenceGrayboxSnapshot.Activity value) -> value.family().equals("operation")).reversed()
                .thenComparing(Comparator.comparingDouble(SourceGrayboxAuditViews::activityUrgency).reversed())
                .thenComparing(ReferenceGrayboxSnapshot.Activity::id));
    }

    private static java.util.Optional<ReferenceGrayboxSnapshot.FieldPost> bestFieldPost(ReferenceGrayboxSnapshot snapshot) {
        return snapshot.fieldPosts().stream().min(Comparator
                .comparing((ReferenceGrayboxSnapshot.FieldPost value) -> value.status().equals("active")).reversed()
                .thenComparing(Comparator.comparingDouble(SourceGrayboxAuditViews::postUrgency).reversed())
                .thenComparingInt(ReferenceGrayboxSnapshot.FieldPost::id));
    }

    private static View settlement(ReferenceGrayboxSnapshot snapshot, ReferenceGrayboxSnapshot.Settlement settlement) {
        ReferenceGrayboxLayout.Rectangle area = settlement.rectangle();
        Point camera = bounded(snapshot.bounds(), area.centreX(), area.z() + area.depth() + OUTER_STANDOFF);
        return view("graybox/settlement/" + settlement.id() + "/approach", "GRAYBOX_SETTLEMENT",
                "settlement:" + settlement.id(), snapshot.bounds(), camera, area.centreX(), area.centreZ(), 3);
    }

    private static View hiveOrgan(ReferenceGrayboxSnapshot snapshot, ReferenceGrayboxSnapshot.HiveOrgan organ) {
        ReferenceGrayboxLayout.Rectangle area = organ.rectangle();
        Point camera = bounded(snapshot.bounds(), area.centreX(), area.z() + area.depth() + OUTER_STANDOFF);
        return view("graybox/hive-organ/" + organ.id() + "/approach", "GRAYBOX_HIVE_ORGAN",
                "organ:" + organ.id(), snapshot.bounds(), camera, area.centreX(), area.centreZ(), 10);
    }

    private static View route(ReferenceGrayboxSnapshot snapshot, ReferenceGrayboxSnapshot.Route route) {
        int focusX = midpoint(route.start().x(), route.end().x());
        int focusZ = midpoint(route.start().z(), route.end().z());
        int dx = route.end().x() - route.start().x();
        int dz = route.end().z() - route.start().z();
        double length = Math.hypot(dx, dz);
        int offsetX = length < 0.001d ? ROUTE_STANDOFF : (int) Math.round(-dz * ROUTE_STANDOFF / length);
        int offsetZ = length < 0.001d ? ROUTE_STANDOFF : (int) Math.round(dx * ROUTE_STANDOFF / length);
        Point camera = bounded(snapshot.bounds(), focusX + offsetX, focusZ + offsetZ);
        return view("graybox/route/" + route.id() + "/corridor", "GRAYBOX_ROUTE", "route:" + route.id(),
                snapshot.bounds(), camera, focusX, focusZ, 1);
    }

    private static View activity(ReferenceGrayboxSnapshot snapshot, ReferenceGrayboxSnapshot.Activity activity) {
        Point camera = bounded(snapshot.bounds(), activity.position().x() + LOCAL_STANDOFF,
                activity.position().z() + LOCAL_STANDOFF);
        return view("graybox/activity/" + activity.id() + "/scene", "GRAYBOX_ACTIVITY", "activity:" + activity.id(),
                snapshot.bounds(), camera, activity.position().x(), activity.position().z(), 3);
    }

    private static View fieldPost(ReferenceGrayboxSnapshot snapshot, ReferenceGrayboxSnapshot.FieldPost post) {
        ReferenceGrayboxLayout.Rectangle area = post.rectangle();
        Point camera = bounded(snapshot.bounds(), area.x() - LOCAL_STANDOFF, area.z() + area.depth() + LOCAL_STANDOFF);
        return view("graybox/field-post/" + post.id() + "/approach", "GRAYBOX_FIELD_POST", "field-post:" + post.id(),
                snapshot.bounds(), camera, area.centreX(), area.centreZ(), 3);
    }

    private static View view(String id, String kind, String targetId, ReferenceGrayboxLayout.Bounds bounds,
                             Point camera, int focusX, int focusZ, int focusHeight) {
        int feetY = bounds.groundY() + 1;
        double dx = focusX - camera.x();
        double dz = focusZ - camera.z();
        double horizontal = Math.max(0.001d, Math.hypot(dx, dz));
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) Math.toDegrees(Math.atan2(feetY + 1.62d - (bounds.groundY() + focusHeight), horizontal));
        return new View(id, kind, targetId, camera.x(), feetY, camera.z(), yaw, pitch);
    }

    private static Point bounded(ReferenceGrayboxLayout.Bounds bounds, int x, int z) {
        return new Point(clamp(x, bounds.minX() + 1, bounds.minX() + bounds.width() - 2),
                clamp(z, bounds.minZ() + 1, bounds.minZ() + bounds.depth() - 2));
    }

    private static int midpoint(int first, int second) { return (int) Math.round((first + second) / 2.0d); }
    private static int clamp(int value, int minimum, int maximum) { return Math.max(minimum, Math.min(maximum, value)); }
    private static double settlementUrgency(ReferenceGrayboxSnapshot.Settlement value) {
        return value.threat() + value.illnessBurden() + (1.0d - value.integrity());
    }
    private static double routeUrgency(ReferenceGrayboxSnapshot.Route value) { return value.risk() + value.infection(); }
    private static double activityUrgency(ReferenceGrayboxSnapshot.Activity value) { return value.personnel() + value.indicator(); }
    private static double postUrgency(ReferenceGrayboxSnapshot.FieldPost value) {
        return value.garrison() + value.wounded() + (1.0d - value.integrity());
    }

    record View(String id, String kind, String targetId, int x, int y, int z, float yaw, float pitch) {
        View {
            if (id.isBlank() || kind.isBlank() || targetId.isBlank()) throw new IllegalArgumentException("graybox audit view identity is invalid");
            if (!Float.isFinite(yaw) || !Float.isFinite(pitch)) throw new IllegalArgumentException("graybox audit view rotation is invalid");
        }
    }

    private record Point(int x, int z) { }
}
