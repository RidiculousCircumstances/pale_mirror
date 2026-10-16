package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActuationId;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BooleanSupplier;

/** Ephemeral native feasibility pass. A time-sliced budget is not evidence of unreachable terrain. */
final class FrontierV3ClearanceProbe {
    static final int PROBES_PER_TURN = 8, MAX_RETAINED_REJECTIONS = 4096;
    static final class Pending extends RuntimeException {
        Pending(String reason) { super(reason, null, false, false); }
    }
    private final ActorActuationId authority;
    private final SurfaceAnchor origin;
    private final Object geometry;
    private final Set<SurfaceAnchor> rejected = new HashSet<>();
    private int probes;

    FrontierV3ClearanceProbe(ActorActuationId authority, SurfaceAnchor origin, Object geometry) {
        this.authority = authority; this.origin = origin; this.geometry = geometry;
    }
    boolean matches(ActorActuationId authority, SurfaceAnchor origin, Object geometry) {
        return this.authority.equals(authority) && this.origin.equals(origin) && this.geometry == geometry;
    }
    void nextTurn() { probes = 0; }
    boolean rejected(SurfaceAnchor station) { return rejected.contains(station); }
    int probes() { return probes; }
    int rejectedCount() { return rejected.size(); }
    boolean test(SurfaceAnchor station, BooleanSupplier reachable) {
        if (rejected(station)) return false;
        if (probes == PROBES_PER_TURN) throw new Pending("native-probe-budget;rejected=" + rejected.size());
        if (rejected.size() == MAX_RETAINED_REJECTIONS) {
            rejected.clear();
            throw new Pending("native-search-safety-bound");
        }
        probes++;
        if (reachable.getAsBoolean()) return true;
        rejected.add(station);
        return false;
    }
}
