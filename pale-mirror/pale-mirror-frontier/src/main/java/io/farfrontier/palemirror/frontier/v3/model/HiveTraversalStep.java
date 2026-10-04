package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId;
import java.util.Objects;
import java.util.Optional;

/** Captured predecessor and optional physical arrival; a cursor alone cannot fence a replanned approach. */
public record HiveTraversalStep(int expectedCursor, long routeRevision, int approachCursor, Optional<HotArrival> hotArrival) {
    public record HotArrival(ActorBodyId body, long leaseRevision) {
        public HotArrival {
            Objects.requireNonNull(body);
            if (leaseRevision < 1) throw new IllegalArgumentException("hive arrival requires a captured scope revision");
        }
    }
    public HiveTraversalStep {
        if (expectedCursor < 0 || expectedCursor >= TraversalTopology.MAX_NODES || routeRevision < 1
                || approachCursor < -1 || approachCursor >= io.farfrontier.palemirror.frontier.v3.model.navigation.TraversalRejoin.MAX_SURFACES)
            throw new IllegalArgumentException("hive progress is outside its bounded versioned route");
        Objects.requireNonNull(hotArrival);
    }
    public static HiveTraversalStep cold(HiveTaskAssembly.Member member) {
        return new HiveTraversalStep(member.cursor(), member.routeRevision(), member.rejoin().map(value -> value.cursor()).orElse(-1), Optional.empty());
    }
    public static HiveTraversalStep hot(HiveTaskAssembly.Member member, ActorBodyId body, long leaseRevision) {
        var cold = cold(member);
        return new HiveTraversalStep(cold.expectedCursor(), cold.routeRevision(), cold.approachCursor(), Optional.of(new HotArrival(body, leaseRevision)));
    }
    public boolean matches(HiveTaskAssembly.Member member) {
        return expectedCursor == member.cursor() && routeRevision == member.routeRevision()
                && approachCursor == member.rejoin().map(value -> value.cursor()).orElse(-1);
    }
}
