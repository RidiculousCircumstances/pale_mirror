package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The single retained approach (or withdrawal) edge for one exact hive formation.
 *
 * <p>Unlike the historical assault attacker routes, this record has one cursor: COLD and HOT
 * can commit an edge only when every surviving member reaches its distinct next body.  The
 * per-member surveyed corridors are geometry, not independent strategic progress.</p>
 */
public record ExpeditionMarch(SubjectId overseerId, int cursor, Map<SubjectId, TraversalTopology> memberTopologies,
                              Optional<ExpeditionMarchIssue> issue, long spatialRevision,
                              Map<SubjectId, io.farfrontier.palemirror.frontier.v3.model.navigation.TraversalRejoin> rejoins) {
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
        issue = Objects.requireNonNull(issue, "expedition issue");
        int edges = copy.values().stream().mapToInt(topology -> topology.edges().size()).max().orElseThrow();
        if (cursor < 0 || cursor > edges) {
            throw new IllegalArgumentException("expedition cursor must address one complete formation edge");
        }
        if (bodiesAt(copy, cursor).values().stream().distinct().count() != copy.size()) {
            throw new IllegalArgumentException("expedition formation bodies must remain distinct");
        }
        if (spatialRevision < 1L) throw new IllegalArgumentException("expedition spatial revision must be positive");
        rejoins = Map.copyOf(Objects.requireNonNull(rejoins, "expedition rejoin"));
        if (!rejoins.isEmpty() && (!rejoins.keySet().equals(copy.keySet()) || cursor == edges))
            throw new IllegalArgumentException("advancing expedition rejoin requires its complete exact roster");
        for (var entry : rejoins.entrySet()) {
            var path = copy.get(entry.getKey());
            var target = path.linearCorridorSurfaces().get(Math.min(cursor + 1, path.edges().size()));
            if (!entry.getValue().target().equals(target)) throw new IllegalArgumentException("expedition rejoin changes its semantic checkpoint");
        }
        issue.ifPresent(value -> validateIssue(copy, cursor, value));
        memberTopologies = Map.copyOf(copy);
    }

    public ExpeditionMarch(SubjectId overseerId, int cursor, Map<SubjectId, TraversalTopology> memberTopologies) {
        this(overseerId, cursor, memberTopologies, Optional.empty());
    }
    public ExpeditionMarch(SubjectId overseerId, int cursor, Map<SubjectId, TraversalTopology> memberTopologies,
                           Optional<ExpeditionMarchIssue> issue) {
        this(overseerId, cursor, memberTopologies, issue, 1L, Map.of());
    }

    public Set<SubjectId> memberIds() { return Set.copyOf(memberTopologies.keySet()); }
    public boolean complete() { return cursor == edgeCount(); }
    public int edgeCount() { return memberTopologies.values().stream().mapToInt(topology -> topology.edges().size()).max().orElseThrow(); }
    public Map<SubjectId, BodyPosition> bodies() {
        if (rejoins.isEmpty()) return bodiesAt(memberTopologies, cursor);
        var bodies = new LinkedHashMap<SubjectId, BodyPosition>();
        rejoins.forEach((actor, rejoin) -> bodies.put(actor, rejoin.current().standingBody()));
        return Map.copyOf(bodies);
    }
    public Map<SubjectId, BodyPosition> nextBodies() {
        return advanceFormation().bodies();
    }
    public SurfaceAnchor checkpoint(SubjectId actor) {
        var topology = memberTopologies.get(Objects.requireNonNull(actor, "expedition member"));
        if (topology == null) throw new IllegalArgumentException("expedition checkpoint has a foreign member");
        return topology.linearCorridorSurfaces().get(Math.min(cursor + 1, topology.edges().size()));
    }
    public ExpeditionMarch withRejoins(Map<SubjectId, io.farfrontier.palemirror.frontier.v3.model.navigation.TraversalRejoin> approaches) {
        if (complete() || issue.isPresent()) throw new IllegalArgumentException("frozen expedition has no advancing rejoin");
        return new ExpeditionMarch(overseerId, cursor, memberTopologies, issue, Math.incrementExact(spatialRevision), approaches);
    }
    public ExpeditionMarch advanceFormation() {
        if (complete() || issue.isPresent()) throw new IllegalArgumentException("completed or blocked expedition cannot advance");
        for (TraversalTopology topology : memberTopologies.values()) {
            if (cursor < topology.edges().size() && !topology.edgeAfterCursor(cursor).traversableBy(TraversalCapability.GROUND_BIOFORM)) {
                throw new IllegalArgumentException("expedition next retained edge is not open for ground bioforms");
            }
        }
        if (!rejoins.isEmpty()) {
            var next = new LinkedHashMap<>(rejoins);
            if (!rejoins.values().stream().allMatch(io.farfrontier.palemirror.frontier.v3.model.navigation.TraversalRejoin::arrived)) {
                var selected = memberIds().stream().sorted(java.util.Comparator.comparingInt((SubjectId actor) -> actor.equals(overseerId) ? 0 : 1)
                        .thenComparing(actor -> actor)).filter(actor -> {
                    var approach = rejoins.get(actor);
                    return !approach.arrived() && rejoins.entrySet().stream().noneMatch(other -> !other.getKey().equals(actor)
                            && other.getValue().current().equals(approach.path().get(approach.nextCursor(1))));
                }).findFirst().orElseThrow(() -> new IllegalArgumentException("expedition rejoin has no clear approach edge"));
                var approach = rejoins.get(selected);
                next.put(selected, approach.advance(approach.nextCursor(1), 1));
            }
            if (!next.values().stream().allMatch(io.farfrontier.palemirror.frontier.v3.model.navigation.TraversalRejoin::arrived))
                return new ExpeditionMarch(overseerId, cursor, memberTopologies, issue, spatialRevision, next);
        }
        return new ExpeditionMarch(overseerId, cursor + 1, memberTopologies, Optional.empty(), spatialRevision, Map.of());
    }
    public ExpeditionMarch recordIssue(ExpeditionMarchIssue observed) {
        if (issue.isPresent()) throw new IllegalArgumentException("expedition already retains one unresolved issue");
        return new ExpeditionMarch(overseerId, cursor, memberTopologies, Optional.of(observed), spatialRevision, rejoins);
    }

    private static Map<SubjectId, BodyPosition> bodiesAt(Map<SubjectId, TraversalTopology> topologies, int cursor) {
        Map<SubjectId, BodyPosition> bodies = new LinkedHashMap<>();
        topologies.forEach((member, topology) -> bodies.put(member, BodyPosition.above(topology.linearCorridorSurfaces().get(Math.min(cursor, topology.edges().size())))));
        return Map.copyOf(bodies);
    }
    private static void requireGroundBioform(TraversalTopology topology) {
        List<TraversalTopology.Edge> edges = topology.edges();
        if (topology.linearCorridorSurfaces().isEmpty() || edges.stream().anyMatch(edge -> edge.kind() != TraversalKind.GROUND_BIOFORM
                || !edge.capabilities().contains(TraversalCapability.GROUND_BIOFORM))) {
            throw new IllegalArgumentException("expedition requires bounded GROUND_BIOFORM topologies");
        }
    }
    private static void validateIssue(Map<SubjectId, TraversalTopology> topologies, int cursor, ExpeditionMarchIssue issue) {
        if (!topologies.containsKey(issue.memberId()) || issue.expectedCursor() != cursor
                || topologies.values().stream().noneMatch(topology -> cursor < topology.edges().size()
                && topology.edgeAfterCursor(cursor).id().equals(issue.edgeId()))) {
            throw new IllegalArgumentException("expedition issue does not name its exact retained next edge");
        }
    }
}
