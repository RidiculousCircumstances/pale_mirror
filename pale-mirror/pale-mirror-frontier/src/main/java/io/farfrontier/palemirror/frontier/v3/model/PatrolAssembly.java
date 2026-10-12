package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import io.farfrontier.palemirror.frontier.v3.model.navigation.TraversalRejoin;

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
        if (copy.values().stream().map(member -> member.corridor().get(member.cursor()).standingBody()).distinct().count() != copy.size()
                || copy.values().stream().map(Member::destinationBody).distinct().count() != copy.size()) {
            throw new IllegalArgumentException("patrol assembly bodies or destinations must be distinct");
        }
        members = Map.copyOf(copy);
    }

    public boolean complete() { return members.values().stream().allMatch(Member::arrived); }
    public boolean planningRequired() { return members.values().stream().anyMatch(member -> member.spatial().waitingOrigin().isPresent()); }
    public Map<SubjectId, BodyPosition> bodies() {
        Map<SubjectId, BodyPosition> result = new LinkedHashMap<>();
        members.forEach((actor, member) -> result.put(actor, member.currentBody()));
        return Map.copyOf(result);
    }
    public List<SubjectId> safeAdvances() {
        if (planningRequired()) return List.of();
        List<SubjectId> safe = new ArrayList<>();
        members.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            Member member = entry.getValue();
            if (!member.arrived() && member.openRetainedEdge()
                    && members.entrySet().stream().noneMatch(other -> !other.getKey().equals(entry.getKey())
                    && other.getValue().currentBody().equals(member.nextBody()))) safe.add(entry.getKey());
        });
        return List.copyOf(safe);
    }
    public PatrolAssembly advanceOne(SubjectId actorId) {
        SubjectId actor = Objects.requireNonNull(actorId, "patrol assembly actor");
        if (!safeAdvances().contains(actor)) throw new IllegalArgumentException("patrol assembly next body is occupied or unavailable");
        Map<SubjectId, Member> next = new LinkedHashMap<>(members); next.put(actor, next.get(actor).advanceOne());
        return new PatrolAssembly(next);
    }
    /**
     * Advances one deterministic safe retained ingress edge.  Assembly is not
     * the later inspection column: residents start at independently retained
     * homes and can share apron cells only after a prior exact body has left.
     * A one-edge reducer preserves that custody ordering for every approach,
     * including homes selected from opposite sides of the public apron.
     */
    public PatrolAssembly advanceFormation() {
        if (complete()) throw new IllegalArgumentException("completed patrol assembly cannot advance");
        List<SubjectId> safe = safeAdvances();
        if (safe.isEmpty()) throw new IllegalArgumentException("patrol assembly formation has no safe retained edge");
        // The leader's retained destination is beyond the shared Hall port; the scout's is the
        // port itself.  Letting lexical subject-id order choose the scout first can park it at
        // that shared terminal and falsely make an otherwise legal ingress look blocked.  Prefer
        // a safe member whose terminal body is not another member's remaining corridor, falling
        // back to the stable subject order only when the two paths are independent.
        SubjectId selected = safe.stream().filter(this::clearsAnotherMembersCorridor).findFirst().orElse(safe.getFirst());
        return advanceOne(selected);
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

    private boolean clearsAnotherMembersCorridor(SubjectId actor) {
        Member member = members.get(actor);
        return members.entrySet().stream().filter(entry -> !entry.getKey().equals(actor))
                .noneMatch(entry -> entry.getValue().corridor().stream()
                        .map(BodyPosition::above).anyMatch(member.destinationBody()::equals));
    }

    /** One resident's immutable ingress topology and cursor. */
    public record Member(TraversalTopology topology, int cursor, StationApproachState spatial) {
        public Member(TraversalTopology topology, int cursor) { this(topology, cursor, StationApproachState.initial()); }
        public Member {
            topology = Objects.requireNonNull(topology, "patrol assembly topology");
            if (topology.edges().stream().anyMatch(edge -> edge.kind() != TraversalKind.PEDESTRIAN
                    || !edge.capabilities().contains(TraversalCapability.PEDESTRIAN)) || topology.linearCorridorSurfaces().size() < 2
                    || topology.linearCorridorSurfaces().size() > TraversalTopology.MAX_NODES) {
                throw new IllegalArgumentException("patrol assembly requires bounded pedestrian topology");
            }
            if (cursor < 0 || cursor >= topology.linearCorridorSurfaces().size()) {
                throw new IllegalArgumentException("patrol assembly cursor is outside topology");
            }
            spatial = Objects.requireNonNull(spatial, "patrol assembly spatial state");
            var surfaces = topology.linearCorridorSurfaces();
            var target = surfaces.get(Math.min(cursor + 1, surfaces.size() - 1));
            if (spatial.approach().isPresent() && !spatial.approach().orElseThrow().target().equals(target))
                throw new IllegalArgumentException("patrol assembly rejoin changes its retained checkpoint");
        }
        public List<SurfaceAnchor> corridor() { return topology.linearCorridorSurfaces(); }
        public long routeRevision() { return spatial.revision(); }
        public Optional<TraversalRejoin> rejoin() { return spatial.approach(); }
        public SurfaceAnchor currentSurface() { return spatial.current(corridor().get(cursor)); }
        public SurfaceAnchor checkpointSurface() { return corridor().get(Math.min(cursor + 1, corridor().size() - 1)); }
        public BodyPosition currentBody() { return currentSurface().standingBody(); }
        public BodyPosition destinationBody() { return BodyPosition.above(corridor().getLast()); }
        public boolean arrived() { return !spatial.pending() && cursor == corridor().size() - 1; }
        public boolean openRetainedEdge() { return cursor == corridor().size() - 1
                || topology.edgeAfterCursor(cursor).traversableBy(TraversalCapability.PEDESTRIAN); }
        public Member observedAt(SurfaceAnchor actual) { return new Member(topology, cursor, spatial.observedAt(actual)); }
        public Member withPlannedApproach(TraversalRejoin approach) {
            if (spatial.waitingOrigin().isEmpty()) throw new IllegalArgumentException("patrol approach requires its retained waiting origin");
            return new Member(topology, cursor, new StationApproachState(routeRevision(), Optional.of(approach), Optional.empty()));
        }
        public BodyPosition nextBody() {
            if (arrived()) throw new IllegalStateException("arrived patrol assembly member has no next body");
            if (spatial.waitingOrigin().isPresent()) throw new IllegalArgumentException("patrol assembly is waiting for its selected route");
            if (rejoin().isPresent()) {
                var path = rejoin().orElseThrow();
                return path.path().get(path.nextCursor(1)).standingBody();
            }
            return BodyPosition.above(corridor().get(cursor + 1));
        }
        public Member advanceOne() {
            if (arrived() || !openRetainedEdge() || spatial.waitingOrigin().isPresent()) {
                throw new IllegalArgumentException("patrol assembly cannot advance unavailable retained edge");
            }
            if (rejoin().isPresent()) {
                var approach = rejoin().orElseThrow();
                var next = approach.advance(approach.nextCursor(1), 1);
                if (!next.arrived()) return new Member(topology, cursor,
                        new StationApproachState(routeRevision(), Optional.of(next), Optional.empty()));
            }
            return new Member(topology, Math.min(cursor + 1, corridor().size() - 1),
                    new StationApproachState(routeRevision(), Optional.empty(), Optional.empty()));
        }
    }
}
