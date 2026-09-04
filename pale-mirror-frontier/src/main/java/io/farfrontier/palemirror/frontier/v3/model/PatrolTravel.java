package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Exact retained movement state for one patrol column.
 *
 * <p>The leader's surveyed route is the only inspection cursor. Every other
 * member nevertheless owns a separate bounded surveyed pedestrian corridor
 * and cursor, because a real column cannot occupy one body cell or cut a
 * corner diagonally. A COLD or HOT transition advances one named member on one
 * retained edge; callers may not relocate the complete formation to a
 * strategic waypoint.</p>
 */
public record PatrolTravel(SubjectId leaderId, TraversalTopology leaderRoute,
                           Map<SubjectId, Member> members) {
    public static final int MAX_MEMBERS = 4;
    public static final int MAX_COLD_ADVANCES = 32;

    public PatrolTravel {
        leaderId = Objects.requireNonNull(leaderId, "patrol leader");
        leaderRoute = requirePedestrian(Objects.requireNonNull(leaderRoute, "patrol leader route"));
        Map<SubjectId, Member> copy = new LinkedHashMap<>();
        Objects.requireNonNull(members, "patrol members").forEach((actor, member) -> {
            SubjectId memberId = Objects.requireNonNull(actor, "patrol member");
            if (copy.put(memberId, Objects.requireNonNull(member, "patrol member state")) != null) {
                throw new IllegalArgumentException("patrol has duplicate member");
            }
        });
        if (!copy.containsKey(leaderId) || copy.size() < 2 || copy.size() > MAX_MEMBERS) {
            throw new IllegalArgumentException("patrol needs its leader and 2.." + MAX_MEMBERS + " members");
        }
        Member leader = copy.get(leaderId);
        if (!leader.topology().equals(leaderRoute)) {
            throw new IllegalArgumentException("patrol leader must own the inspection route");
        }
        if (copy.values().stream().map(Member::currentBody).distinct().count() != copy.size()) {
            throw new IllegalArgumentException("patrol formation bodies must be distinct");
        }
        members = Map.copyOf(copy);
    }

    public int routeCursor() { return leader().cursor(); }
    public boolean leaderArrived() { return leader().arrived(); }
    public boolean complete() { return members.values().stream().allMatch(Member::arrived); }
    public Member leader() { return members.get(leaderId); }
    public Map<SubjectId, BodyPosition> bodies() {
        Map<SubjectId, BodyPosition> result = new LinkedHashMap<>();
        members.forEach((member, travel) -> result.put(member, travel.currentBody()));
        return Map.copyOf(result);
    }

    /** Members whose exact next retained body is not currently occupied. */
    public List<SubjectId> safeAdvances() {
        Set<BodyPosition> occupied = new LinkedHashSet<>(bodies().values());
        List<SubjectId> safe = new ArrayList<>();
        members.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            Member member = entry.getValue();
            if (!member.arrived() && !occupied.contains(member.nextBody()) && maintainsColumnSpacing(entry.getKey())) safe.add(entry.getKey());
        });
        return List.copyOf(safe);
    }

    /** A patrol is a column, not several independently marching residents. */
    private boolean maintainsColumnSpacing(SubjectId advancingMember) {
        int nextCursor = members.get(advancingMember).cursor() + 1;
        int minimum = Integer.MAX_VALUE, maximum = Integer.MIN_VALUE;
        for (Map.Entry<SubjectId, Member> entry : members.entrySet()) {
            int cursor = entry.getKey().equals(advancingMember) ? nextCursor : entry.getValue().cursor();
            minimum = Math.min(minimum, cursor); maximum = Math.max(maximum, cursor);
        }
        return maximum - minimum <= 1;
    }

    /**
     * Advances one named member by one exact OPEN topology edge. This is used
     * for an observed HOT arrival and is also the primitive for bounded COLD
     * progression; it deliberately does not accept arbitrary body coordinates.
     */
    public PatrolTravel advanceOne(SubjectId memberId) {
        SubjectId exactMember = Objects.requireNonNull(memberId, "patrol advance member");
        if (!safeAdvances().contains(exactMember)) {
            throw new IllegalArgumentException("patrol member next retained body is occupied or unavailable");
        }
        Map<SubjectId, Member> next = new LinkedHashMap<>(members);
        next.put(exactMember, next.get(exactMember).advanceOne());
        return new PatrolTravel(leaderId, leaderRoute, next);
    }

    /**
     * Bounded deterministic COLD progression. Each inner transition is the
     * same retained one-edge transition as HOT; it stops rather than crossing
     * an occupied or unavailable next body.
     */
    public PatrolTravel advanceCold() {
        PatrolTravel current = this;
        for (int advance = 0; advance < MAX_COLD_ADVANCES && !current.complete(); advance++) {
            List<SubjectId> safe = current.safeAdvances();
            if (safe.isEmpty()) break;
            current = current.advanceOne(safe.getFirst());
        }
        return current;
    }

    private static TraversalTopology requirePedestrian(TraversalTopology topology) {
        if (topology.edges().stream().anyMatch(edge -> edge.kind() != TraversalKind.PEDESTRIAN
                || !edge.capabilities().contains(TraversalCapability.PEDESTRIAN))) {
            throw new IllegalArgumentException("patrol travel requires pedestrian topology");
        }
        if (topology.linearCorridorSurfaces().size() < 2 || topology.linearCorridorSurfaces().size() > OperationTravel.MAX_CELLS) {
            throw new IllegalArgumentException("patrol travel corridor is outside bounded profile");
        }
        return topology;
    }

    /** One member's immutable body corridor and sole cursor. */
    public record Member(TraversalTopology topology, int cursor) {
        public Member {
            topology = requirePedestrian(Objects.requireNonNull(topology, "patrol member topology"));
            if (cursor < 0 || cursor >= topology.linearCorridorSurfaces().size()) {
                throw new IllegalArgumentException("patrol member cursor is outside retained corridor");
            }
        }

        public List<SurfaceAnchor> corridor() { return topology.linearCorridorSurfaces(); }
        public SurfaceAnchor currentSurface() { return corridor().get(cursor); }
        public BodyPosition currentBody() { return BodyPosition.above(currentSurface()); }
        public boolean arrived() { return cursor == corridor().size() - 1; }
        public SurfaceAnchor nextSurface() {
            if (arrived()) throw new IllegalStateException("arrived patrol member has no next surface");
            return corridor().get(cursor + 1);
        }
        public BodyPosition nextBody() { return BodyPosition.above(nextSurface()); }
        public Member advanceOne() {
            if (arrived() || !topology.edgeAfterCursor(cursor).traversableBy(TraversalCapability.PEDESTRIAN)) {
                throw new IllegalArgumentException("patrol member cannot advance unavailable retained edge");
            }
            return new Member(topology, cursor + 1);
        }
    }
}
