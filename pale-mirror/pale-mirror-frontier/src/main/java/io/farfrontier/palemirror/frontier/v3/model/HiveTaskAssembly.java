package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Persisted exact bioform assembly at one semantic Ganglion departure port.
 *
 * <p>This deliberately has no cargo-carrier rule: a hive task group is not a
 * logistics convoy.  Every member retains its own immutable ground-bioform
 * topology and sole cursor, so COLD and a later HOT movement provider share
 * the same route rather than exchanging coordinates or inventing a local
 * sidestep.</p>
 */
public record HiveTaskAssembly(SubjectId ganglionId, Map<SubjectId, Member> members) {
    public static final int MAX_MEMBERS = HiveMobilization.MAX_MEMBERS;

    public HiveTaskAssembly {
        ganglionId = Objects.requireNonNull(ganglionId, "hive assembly Ganglion");
        Map<SubjectId, Member> copy = new LinkedHashMap<>();
        Objects.requireNonNull(members, "hive assembly members").forEach((actor, member) -> {
            if (copy.put(Objects.requireNonNull(actor, "hive assembly actor"),
                    Objects.requireNonNull(member, "hive assembly member")) != null) {
                throw new IllegalArgumentException("duplicate hive assembly actor");
            }
        });
        if (copy.isEmpty() || copy.size() > MAX_MEMBERS
                || copy.values().stream().map(Member::destinationSurface).distinct().count() != copy.size()
                || copy.values().stream().map(Member::currentSurface).distinct().count() != copy.size()) {
            throw new IllegalArgumentException("hive assembly members or staging surfaces are invalid");
        }
        members = Map.copyOf(copy);
    }

    public boolean complete() { return members.values().stream().allMatch(Member::arrived); }

    public Map<SubjectId, SurfaceAnchor> positions() {
        Map<SubjectId, SurfaceAnchor> positions = new LinkedHashMap<>();
        members.forEach((actor, member) -> positions.put(actor, member.currentSurface()));
        return Map.copyOf(positions);
    }

    /** Exact collision-free COLD candidates; a later HOT provider checks the same next edge. */
    public List<SubjectId> safeAdvances() {
        List<SubjectId> result = new ArrayList<>();
        members.keySet().stream().sorted().forEach(actor -> {
            Member member = members.get(actor);
            if (!member.arrived() && members.entrySet().stream().noneMatch(other -> !other.getKey().equals(actor)
                    && other.getValue().currentSurface().equals(member.nextSurface()))) result.add(actor);
        });
        return List.copyOf(result);
    }

    /** Advances one named retained cursor; neither destination nor topology can be replaced. */
    public HiveTaskAssembly advance(SubjectId actorId) {
        SubjectId actor = Objects.requireNonNull(actorId, "hive assembly actor");
        if (!safeAdvances().contains(actor)) throw new IllegalArgumentException("hive assembly next cursor is occupied");
        Map<SubjectId, Member> next = new LinkedHashMap<>(members);
        Member current = next.get(actor);
        next.put(actor, new Member(current.topology(), current.cursor() + 1));
        return new HiveTaskAssembly(ganglionId, next);
    }

    /** One exact member's bounded immutable ground-bioform topology and cursor. */
    public record Member(TraversalTopology topology, int cursor) {
        public Member {
            topology = Objects.requireNonNull(topology, "hive assembly topology");
            if (topology.nodes().size() > TraversalTopology.MAX_NODES
                    || topology.edges().stream().anyMatch(edge -> edge.kind() != TraversalKind.GROUND_BIOFORM
                    || !edge.traversableBy(TraversalCapability.GROUND_BIOFORM))) {
                throw new IllegalArgumentException("hive assembly requires one open ground-bioform topology");
            }
            if (cursor < 0 || cursor >= topology.linearCorridorSurfaces().size()) {
                throw new IllegalArgumentException("hive assembly cursor is outside topology");
            }
        }
        public List<SurfaceAnchor> corridor() { return topology.linearCorridorSurfaces(); }
        public SurfaceAnchor currentSurface() { return corridor().get(cursor); }
        public SurfaceAnchor nextSurface() {
            if (arrived()) throw new IllegalStateException("arrived hive assembly member has no next surface");
            return corridor().get(cursor + 1);
        }
        public SurfaceAnchor destinationSurface() { return corridor().getLast(); }
        public boolean arrived() { return cursor == corridor().size() - 1; }
    }
}
