package io.farfrontier.palemirror.frontier.v3.model.security;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.*;
/** Coverage demand, distinct from workforce approval and armed readiness. */
public final class SecurityCoverage {
    public static int required(FrontierWorldState state, SubjectId home) {
        var rules = state.bootstrap().ruleset().labour().entries().get(ResidentWorkKind.GUARD);
        if (rules == null) return 0;
        var centres = posts(state,home).stream().map(ProtectedTarget::observationAnchor).toList();
        // One patrol can cover adjacent obligations. Remote obligations add travel/watch workload.
        long workload = centres.size() * state.bootstrap().ruleset().security().watchTicks();
        var anchor = new SurfaceAnchor(FrontierWorldStateSupport.settlement(state.bootstrap(), home).anchor());
        for (var centre : centres) workload += distance(anchor, centre) * state.bootstrap().ruleset().resourceHarvestColdTravelTicksPerEdge();
        int desired = centres.isEmpty() ? 0 : (int)Math.max(1, Math.floorDiv(Math.addExact(workload, state.bootstrap().ruleset().security().coverageCycleTicks()-1),state.bootstrap().ruleset().security().coverageCycleTicks()));
        return desired;
    }
    public static List<ProtectedTarget> posts(FrontierWorldState state,SubjectId home) {
        var posts=new ArrayList<ProtectedTarget>();
        for(var target:ProtectionObligations.read(state,home))
            if(posts.stream().noneMatch(post->distance(post.observationAnchor(),target.observationAnchor())<=state.bootstrap().ruleset().security().observationRadius()/2)) posts.add(target);
        return List.copyOf(posts);
    }
    /** Same current protected-area contract for selection, retained posts and admission. */
    public static boolean permitsPost(ProtectedTarget target, SurfaceAnchor station) {
        var anchor = target.observationAnchor();
        return Math.abs((long) station.x() - anchor.x()) <= 6
                && Math.abs((long) station.z() - anchor.z()) <= 6
                && Math.abs((long) station.y() - anchor.y()) <= 4;
    }
    public static long distance(SurfaceAnchor a, SurfaceAnchor b) {
        return a.manhattanDistance(b);
    }
    private SecurityCoverage() { }
}
