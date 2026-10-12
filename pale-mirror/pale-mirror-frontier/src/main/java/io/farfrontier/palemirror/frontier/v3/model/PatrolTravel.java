package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Optional;
import io.farfrontier.palemirror.frontier.v3.model.navigation.TraversalRejoin;

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
    /** Bounded background catch-up; HOT still commits only one observed arrival. */
    public static final int MAX_COLD_ADVANCES = 64;

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
        if (copy.values().stream().map(member -> member.corridor().get(member.cursor()).standingBody()).distinct().count() != copy.size()) {
            throw new IllegalArgumentException("patrol formation bodies must be distinct");
        }
        if (copy.values().stream().anyMatch(member -> member.spatial().pending())
                && copy.values().stream().anyMatch(member -> !member.arrived() && !member.spatial().pending()))
            throw new IllegalArgumentException("a rejoining column must retain every unfinished member's approach");
        members = Map.copyOf(copy);
    }

    public int routeCursor() { return leader().cursor(); }
    public boolean leaderArrived() { return leader().arrived(); }
    public boolean complete() { return members.values().stream().allMatch(Member::arrived); }
    public boolean planningRequired() { return members.values().stream().anyMatch(member -> member.spatial().waitingOrigin().isPresent()); }
    public Member leader() { return members.get(leaderId); }
    public Map<SubjectId, BodyPosition> bodies() {
        Map<SubjectId, BodyPosition> result = new LinkedHashMap<>();
        members.forEach((member, travel) -> result.put(member, travel.currentBody()));
        return Map.copyOf(result);
    }

    /** Members whose exact next retained body is not currently occupied. */
    public List<SubjectId> safeAdvances() {
        // Rejoin has a single whole-column acknowledgement, never individual route credit.
        if (members.values().stream().anyMatch(member -> member.spatial().pending())) return List.of();
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
        if (members.values().stream().anyMatch(member -> member.spatial().pending()))
            throw new IllegalArgumentException("patrol rejoin must settle through its coordinated formation boundary");
        SubjectId exactMember = Objects.requireNonNull(memberId, "patrol advance member");
        if (!safeAdvances().contains(exactMember)) {
            throw new IllegalArgumentException("patrol member next retained body is occupied or unavailable");
        }
        Map<SubjectId, Member> next = new LinkedHashMap<>(members);
        next.put(exactMember, next.get(exactMember).advanceOne());
        return new PatrolTravel(leaderId, leaderRoute, next);
    }

    /** One retained formation edge.  A patrol never promotes an arbitrary first safe guard
     * into an independent route cursor: every non-arrived member must have one legal,
     * distinct next pedestrian body before the column advances together. */
    public PatrolTravel advanceFormation() {
        if (planningRequired()) throw new IllegalArgumentException("patrol column is waiting for selected approaches");
        if (complete()) throw new IllegalStateException("complete patrol formation has no next edge");
        if (members.values().stream().anyMatch(member -> member.rejoin().isPresent())) return advanceRejoin();
        Set<BodyPosition> nextBodies = new LinkedHashSet<>();
        Map<SubjectId, Member> next = new LinkedHashMap<>();
        for (Map.Entry<SubjectId, Member> entry : members.entrySet()) {
            Member member = entry.getValue();
            Member advanced = member.arrived() ? member : member.advanceOne();
            if (!nextBodies.add(advanced.currentBody())) throw new IllegalArgumentException("patrol formation edge overlaps");
            next.put(entry.getKey(), advanced);
        }
        return new PatrolTravel(leaderId, leaderRoute, next);
    }

    /** Legal approach steps are serialized; no inspection cursor advances before the whole column rejoins. */
    private PatrolTravel advanceRejoin() {
        var next = new LinkedHashMap<>(members);
        if (!members.values().stream().allMatch(Member::checkpointReached)) {
            var order = members.keySet().stream().sorted(java.util.Comparator
                    .comparingInt((SubjectId actor) -> actor.equals(leaderId) ? 0 : 1).thenComparing(actor -> actor)).toList();
            var selected = order.stream().filter(actor -> {
                var member = members.get(actor);
                return !member.checkpointReached() && member.openRetainedEdge() && members.entrySet().stream()
                        .noneMatch(other -> !other.getKey().equals(actor) && other.getValue().currentBody().equals(member.nextBody()));
            }).findFirst().orElseThrow(() -> new IllegalArgumentException("patrol rejoin has no safe retained approach edge"));
            next.put(selected, members.get(selected).advanceApproach());
        }
        if (next.values().stream().allMatch(Member::checkpointReached)) next.replaceAll((actor, member) -> member.commitCheckpoint());
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
            if (current.planningRequired()) break;
            if (current.members().values().stream().anyMatch(member -> member.rejoin().isPresent())) {
                current = current.advanceFormation(); continue;
            }
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
        if (topology.linearCorridorSurfaces().size() < 2 || topology.linearCorridorSurfaces().size() > TraversalTopology.MAX_NODES) {
            throw new IllegalArgumentException("patrol travel corridor is outside bounded profile");
        }
        return topology;
    }

    /** One member's immutable body corridor and sole cursor. */
    public record Member(TraversalTopology topology, int cursor, StationApproachState spatial) {
        public Member(TraversalTopology topology, int cursor) { this(topology, cursor, StationApproachState.initial()); }
        public Member {
            topology = requirePedestrian(Objects.requireNonNull(topology, "patrol member topology"));
            if (cursor < 0 || cursor >= topology.linearCorridorSurfaces().size()) {
                throw new IllegalArgumentException("patrol member cursor is outside retained corridor");
            }
            spatial = Objects.requireNonNull(spatial, "patrol member spatial state");
            var surfaces = topology.linearCorridorSurfaces();
            var target = surfaces.get(Math.min(cursor + 1, surfaces.size() - 1));
            if (spatial.approach().isPresent() && !spatial.approach().orElseThrow().target().equals(target))
                throw new IllegalArgumentException("patrol rejoin changes its retained inspection checkpoint");
        }

        public List<SurfaceAnchor> corridor() { return topology.linearCorridorSurfaces(); }
        public long routeRevision() { return spatial.revision(); }
        public Optional<TraversalRejoin> rejoin() { return spatial.approach(); }
        public SurfaceAnchor currentSurface() { return spatial.current(corridor().get(cursor)); }
        public SurfaceAnchor checkpointSurface() { return corridor().get(Math.min(cursor + 1, corridor().size() - 1)); }
        public BodyPosition currentBody() { return BodyPosition.above(currentSurface()); }
        public boolean arrived() { return !spatial.pending() && cursor == corridor().size() - 1; }
        public SurfaceAnchor nextSurface() {
            if (arrived()) throw new IllegalStateException("arrived patrol member has no next surface");
            if (spatial.waitingOrigin().isPresent()) throw new IllegalArgumentException("patrol member is waiting for its selected approach");
            if (rejoin().isPresent()) {
                var approach = rejoin().orElseThrow(); return approach.path().get(approach.nextCursor(1));
            }
            return corridor().get(cursor + 1);
        }
        public BodyPosition nextBody() { return BodyPosition.above(nextSurface()); }
        public boolean openRetainedEdge() { return cursor == corridor().size() - 1
                || topology.edgeAfterCursor(cursor).traversableBy(TraversalCapability.PEDESTRIAN); }
        public Member observedAt(SurfaceAnchor actual) { return new Member(topology, cursor, spatial.observedAt(actual)); }
        public Member withPlannedApproach(TraversalRejoin approach) {
            if (spatial.waitingOrigin().isEmpty()) throw new IllegalArgumentException("patrol approach requires its retained waiting origin");
            return new Member(topology, cursor, new StationApproachState(routeRevision(), Optional.of(approach), Optional.empty()));
        }
        boolean checkpointReached() { return arrived() || rejoin().filter(TraversalRejoin::arrived).isPresent(); }
        Member advanceApproach() {
            if (checkpointReached() || !openRetainedEdge()) throw new IllegalArgumentException("patrol approach cannot advance");
            var approach = rejoin().orElseThrow();
            return new Member(topology, cursor, new StationApproachState(routeRevision(),
                    Optional.of(approach.advance(approach.nextCursor(1), 1)), Optional.empty()));
        }
        Member commitCheckpoint() {
            if (!checkpointReached()) throw new IllegalArgumentException("patrol column has not rejoined its checkpoint");
            return rejoin().isEmpty() ? this : new Member(topology, Math.min(cursor + 1, corridor().size() - 1),
                    new StationApproachState(routeRevision(), Optional.empty(), Optional.empty()));
        }
        public Member advanceOne() {
            if (spatial.pending()) throw new IllegalArgumentException("a rejoining patrol member needs coordinated checkpoint settlement");
            if (arrived() || !openRetainedEdge()) {
                throw new IllegalArgumentException("patrol member cannot advance unavailable retained edge");
            }
            return new Member(topology, cursor + 1, spatial);
        }
    }
}
