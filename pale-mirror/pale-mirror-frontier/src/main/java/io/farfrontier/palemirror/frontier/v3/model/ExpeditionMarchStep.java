package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Exact bounded spatial predecessor, independent of physical poses and execution versions. */
public record ExpeditionMarchStep(int cursor, long spatialRevision, Map<SubjectId, Member> members) {
    public record Member(TraversalTopologyId topologyId, long topologyRevision, int approachCursor) {
        public Member {
            Objects.requireNonNull(topologyId, "expedition predecessor topology");
            if (topologyRevision < 0L || approachCursor < -1) throw new IllegalArgumentException("invalid expedition predecessor member");
        }
    }
    public ExpeditionMarchStep {
        members = Map.copyOf(Objects.requireNonNull(members, "expedition predecessor members"));
        if (cursor < 0 || spatialRevision < 1L || members.isEmpty() || members.size() > ExpeditionMarch.MAX_MEMBERS)
            throw new IllegalArgumentException("invalid expedition predecessor");
    }
    public static ExpeditionMarchStep capture(ExpeditionMarch march) {
        var members = new LinkedHashMap<SubjectId, Member>();
        march.memberTopologies().forEach((actor, topology) -> members.put(actor, new Member(topology.id(), topology.revision(),
                march.rejoins().containsKey(actor) ? march.rejoins().get(actor).cursor() : -1)));
        return new ExpeditionMarchStep(march.cursor(), march.spatialRevision(), members);
    }
    public void requireCurrent(ExpeditionMarch march) {
        if (!equals(capture(march))) throw new IllegalArgumentException("expedition receipt has an obsolete spatial predecessor");
    }
}
