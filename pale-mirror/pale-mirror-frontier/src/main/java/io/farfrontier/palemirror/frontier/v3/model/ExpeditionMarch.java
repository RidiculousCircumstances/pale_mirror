package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The single retained approach (or withdrawal) edge for one exact hive formation.
 *
 * <p>Unlike the historical assault attacker routes, this record has one cursor: COLD and HOT
 * can commit an edge only when every surviving member reaches its distinct next body.  The
 * per-member surveyed corridors are geometry, not independent strategic progress.</p>
 */
public record ExpeditionMarch(SubjectId overseerId, int cursor, Map<SubjectId, TraversalTopology> memberTopologies) {
    public static final int MAX_MEMBERS = SettlementAssault.MAX_ATTACKERS;

    public ExpeditionMarch {
        overseerId = Objects.requireNonNull(overseerId, "expedition overseer");
        Map<SubjectId, TraversalTopology> copy = new LinkedHashMap<>();
        Objects.requireNonNull(memberTopologies, "expedition member topologies").forEach((member, topology) -> {
            SubjectId exactMember = Objects.requireNonNull(member, "expedition member");
            TraversalTopology exactTopology = Objects.requireNonNull(topology, "expedition topology");
            requireGroundBioform(exactTopology);
            if (copy.put(exactMember, exactTopology) != null) throw new IllegalArgumentException("duplicate expedition member");
        });
        if (!copy.containsKey(overseerId) || copy.isEmpty() || copy.size() > MAX_MEMBERS) {
            throw new IllegalArgumentException("expedition must retain its exact Overseer-led roster");
        }
        int edges = copy.values().stream().mapToInt(topology -> topology.edges().size()).max().orElseThrow();
        if (cursor < 0 || cursor > edges) {
            throw new IllegalArgumentException("expedition cursor must address one complete formation edge");
        }
        if (bodiesAt(copy, cursor).size() != copy.size()) {
            throw new IllegalArgumentException("expedition formation bodies must remain distinct");
        }
        memberTopologies = Map.copyOf(copy);
    }

    public Set<SubjectId> memberIds() { return Set.copyOf(memberTopologies.keySet()); }
    public boolean complete() { return cursor == edgeCount(); }
    public int edgeCount() { return memberTopologies.values().stream().mapToInt(topology -> topology.edges().size()).max().orElseThrow(); }
    public Map<SubjectId, BodyPosition> bodies() { return bodiesAt(memberTopologies, cursor); }
    public Map<SubjectId, BodyPosition> nextBodies() {
        if (complete()) throw new IllegalStateException("completed expedition has no next retained formation edge");
        return bodiesAt(memberTopologies, cursor + 1);
    }
    public ExpeditionMarch advanceFormation() {
        if (complete()) throw new IllegalArgumentException("completed expedition cannot advance");
        for (TraversalTopology topology : memberTopologies.values()) {
            if (cursor < topology.edges().size() && !topology.edgeAfterCursor(cursor).traversableBy(TraversalCapability.GROUND_BIOFORM)) {
                throw new IllegalArgumentException("expedition next retained edge is not open for ground bioforms");
            }
        }
        return new ExpeditionMarch(overseerId, cursor + 1, memberTopologies);
    }

    private static Map<SubjectId, BodyPosition> bodiesAt(Map<SubjectId, TraversalTopology> topologies, int cursor) {
        Map<SubjectId, BodyPosition> bodies = new LinkedHashMap<>();
        topologies.forEach((member, topology) -> bodies.put(member, BodyPosition.above(topology.linearCorridorSurfaces().get(Math.min(cursor, topology.edges().size())))));
        return Map.copyOf(bodies);
    }
    private static void requireGroundBioform(TraversalTopology topology) {
        List<TraversalTopology.Edge> edges = topology.edges();
        if (topology.linearCorridorSurfaces().size() < 2 || edges.stream().anyMatch(edge -> edge.kind() != TraversalKind.GROUND_BIOFORM
                || !edge.capabilities().contains(TraversalCapability.GROUND_BIOFORM))) {
            throw new IllegalArgumentException("expedition requires bounded GROUND_BIOFORM topologies");
        }
    }
}
