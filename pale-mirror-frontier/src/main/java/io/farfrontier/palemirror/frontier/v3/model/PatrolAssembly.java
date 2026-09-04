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
 * Exact ingress state before a patrol may enter its retained inspection route.
 *
 * <p>This is deliberately not an operation cargo assembly and has no carrier.
 * Each exact resident owns one immutable pedestrian approach and cursor. A
 * reducer advances one safe member at a time, retaining real column spacing
 * rather than placing the entire unit at the first route waypoint.</p>
 */
public record PatrolAssembly(Map<SubjectId, Member> members) {
    public static final int MAX_MEMBERS = PatrolTravel.MAX_MEMBERS;
    public static final int MAX_COLD_ADVANCES = 32;

    public PatrolAssembly {
        Map<SubjectId, Member> copy = new LinkedHashMap<>();
        Objects.requireNonNull(members, "patrol assembly members").forEach((actor, member) -> {
            if (copy.put(Objects.requireNonNull(actor, "patrol assembly actor"), Objects.requireNonNull(member, "patrol assembly member")) != null) {
                throw new IllegalArgumentException("patrol assembly has duplicate member");
            }
        });
        if (copy.size() < 2 || copy.size() > MAX_MEMBERS) {
            throw new IllegalArgumentException("patrol assembly requires 2.." + MAX_MEMBERS + " members");
        }
        if (copy.values().stream().map(Member::currentBody).distinct().count() != copy.size()
                || copy.values().stream().map(Member::destinationBody).distinct().count() != copy.size()) {
            throw new IllegalArgumentException("patrol assembly bodies or destinations must be distinct");
        }
        members = Map.copyOf(copy);
    }

    public boolean complete() { return members.values().stream().allMatch(Member::arrived); }
    public Map<SubjectId, BodyPosition> bodies() {
        Map<SubjectId, BodyPosition> result = new LinkedHashMap<>();
        members.forEach((actor, member) -> result.put(actor, member.currentBody()));
        return Map.copyOf(result);
    }
    public List<SubjectId> safeAdvances() {
        Set<BodyPosition> occupied = new LinkedHashSet<>(bodies().values());
        List<SubjectId> safe = new ArrayList<>();
        members.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            Member member = entry.getValue();
            if (!member.arrived() && !occupied.contains(member.nextBody())) safe.add(entry.getKey());
        });
        return List.copyOf(safe);
    }
    public PatrolAssembly advanceOne(SubjectId actorId) {
        SubjectId actor = Objects.requireNonNull(actorId, "patrol assembly actor");
        if (!safeAdvances().contains(actor)) throw new IllegalArgumentException("patrol assembly next body is occupied or unavailable");
        Map<SubjectId, Member> next = new LinkedHashMap<>(members); next.put(actor, next.get(actor).advanceOne());
        return new PatrolAssembly(next);
    }
    public PatrolAssembly advanceCold() {
        PatrolAssembly current = this;
        for (int count = 0; count < MAX_COLD_ADVANCES && !current.complete(); count++) {
            List<SubjectId> safe = current.safeAdvances();
            if (safe.isEmpty()) break;
            current = current.advanceOne(safe.getFirst());
        }
        return current;
    }

    /** One resident's immutable ingress topology and cursor. */
    public record Member(TraversalTopology topology, int cursor) {
        public Member {
            topology = Objects.requireNonNull(topology, "patrol assembly topology");
            if (topology.edges().stream().anyMatch(edge -> edge.kind() != TraversalKind.PEDESTRIAN
                    || !edge.capabilities().contains(TraversalCapability.PEDESTRIAN)) || topology.linearCorridorSurfaces().size() < 2
                    || topology.linearCorridorSurfaces().size() > OperationTravel.MAX_CELLS) {
                throw new IllegalArgumentException("patrol assembly requires bounded pedestrian topology");
            }
            if (cursor < 0 || cursor >= topology.linearCorridorSurfaces().size()) {
                throw new IllegalArgumentException("patrol assembly cursor is outside topology");
            }
        }
        public List<SurfaceAnchor> corridor() { return topology.linearCorridorSurfaces(); }
        public BodyPosition currentBody() { return BodyPosition.above(corridor().get(cursor)); }
        public BodyPosition destinationBody() { return BodyPosition.above(corridor().getLast()); }
        public boolean arrived() { return cursor == corridor().size() - 1; }
        public BodyPosition nextBody() {
            if (arrived()) throw new IllegalStateException("arrived patrol assembly member has no next body");
            return BodyPosition.above(corridor().get(cursor + 1));
        }
        public Member advanceOne() {
            if (arrived() || !topology.edgeAfterCursor(cursor).traversableBy(TraversalCapability.PEDESTRIAN)) {
                throw new IllegalArgumentException("patrol assembly cannot advance unavailable retained edge");
            }
            return new Member(topology, cursor + 1);
        }
    }
}
