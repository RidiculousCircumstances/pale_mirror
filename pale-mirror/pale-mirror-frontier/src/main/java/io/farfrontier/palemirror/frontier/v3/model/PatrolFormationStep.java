package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Small exact predecessor receipt; never copies topology paths or awards arrival. */
public record PatrolFormationStep(RoutePatrolStatus phase, Map<SubjectId, Member> members) {
    public PatrolFormationStep {
        if (phase != RoutePatrolStatus.ASSEMBLING && phase != RoutePatrolStatus.EN_ROUTE)
            throw new IllegalArgumentException("patrol step requires its explicit advancing phase");
        members = Map.copyOf(Objects.requireNonNull(members, "patrol predecessor members"));
        if (members.size() < 2 || members.size() > PatrolTravel.MAX_MEMBERS)
            throw new IllegalArgumentException("patrol predecessor requires its bounded exact cohort");
    }
    public record Member(TraversalTopologyId topologyId, long topologyRevision, long routeRevision, int cursor, int approachCursor) {
        public Member {
            Objects.requireNonNull(topologyId, "patrol predecessor topology");
            if (topologyRevision < 0L || routeRevision < 1L || cursor < 0 || approachCursor < -1)
                throw new IllegalArgumentException("invalid patrol predecessor version");
        }
    }
    public static PatrolFormationStep capture(RoutePatrol patrol) {
        Map<SubjectId, Member> members = new LinkedHashMap<>();
        if (patrol.status() == RoutePatrolStatus.ASSEMBLING) patrol.assembly().members().forEach((actor, value) -> members.put(actor,
                new Member(value.topology().id(), value.topology().revision(), value.routeRevision(), value.cursor(),
                        value.rejoin().map(io.farfrontier.palemirror.frontier.v3.model.navigation.TraversalRejoin::cursor).orElse(-1))));
        else if (patrol.status() == RoutePatrolStatus.EN_ROUTE) patrol.travel().members().forEach((actor, value) -> members.put(actor,
                new Member(value.topology().id(), value.topology().revision(), value.routeRevision(), value.cursor(),
                        value.rejoin().map(io.farfrontier.palemirror.frontier.v3.model.navigation.TraversalRejoin::cursor).orElse(-1))));
        return new PatrolFormationStep(patrol.status(), members);
    }
    public void requireCurrent(RoutePatrol patrol) {
        if (!equals(capture(patrol))) throw new IllegalArgumentException("patrol step has stale topology, phase or spatial progress");
    }
}
