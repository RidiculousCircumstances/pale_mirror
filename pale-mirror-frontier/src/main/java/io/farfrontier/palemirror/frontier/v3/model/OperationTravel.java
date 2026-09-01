package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The exact spatial truth for one in-progress logistics operation.
 *
 * <p>Strategic route milestones remain on {@link RouteOperation}; this value is the bounded
 * adjacent-cell corridor between two such milestones.  Its cursor, member formation and cargo
 * anchor survive a HOT/COLD hand-off as one immutable fact.  It deliberately contains no
 * Minecraft identity or physics policy.</p>
 */
public record OperationTravel(TraversalTopology topology, int cursor, Map<SubjectId, BlockPosition> formation,
                              BlockPosition cargoAnchor) {
    public static final int MAX_CELLS = 4_096;
    public static final int MAX_COLD_ADVANCE = 32;

    public OperationTravel {
        topology = Objects.requireNonNull(topology, "operation travel topology");
        List<BlockPosition> corridor = corridor(topology);
        if (topology.edges().stream().anyMatch(edge -> edge.kind() != TraversalKind.PEDESTRIAN
                || !edge.traversableBy(TraversalCapability.PEDESTRIAN))) {
            throw new IllegalArgumentException("operation travel requires an open pedestrian topology");
        }
        if (corridor.size() < 2 || corridor.size() > MAX_CELLS) {
            throw new IllegalArgumentException("operation travel corridor must contain 2.." + MAX_CELLS + " cells");
        }
        for (int index = 1; index < corridor.size(); index++) {
            BlockPosition previous = Objects.requireNonNull(corridor.get(index - 1), "operation travel cell");
            BlockPosition next = Objects.requireNonNull(corridor.get(index), "operation travel cell");
            if (Math.abs(previous.y() - next.y()) > 1 || Math.abs(previous.x() - next.x()) + Math.abs(previous.z() - next.z()) != 1) {
                throw new IllegalArgumentException("operation travel corridor must use adjacent surfaces with grade at most one");
            }
        }
        if (cursor < 0 || cursor >= corridor.size()) throw new IllegalArgumentException("operation travel cursor is outside corridor");
        Map<SubjectId, BlockPosition> copy = new LinkedHashMap<>();
        formation.forEach((actor, position) -> {
            if (copy.put(Objects.requireNonNull(actor, "operation travel actor"), Objects.requireNonNull(position, "operation travel formation position")) != null) {
                throw new IllegalArgumentException("operation travel has a duplicate actor");
            }
        });
        if (copy.isEmpty() || copy.size() > 8) throw new IllegalArgumentException("operation travel formation must contain 1..8 actors");
        if (copy.values().stream().distinct().count() != copy.size()) {
            throw new IllegalArgumentException("operation travel formation positions must be distinct");
        }
        formation = Map.copyOf(copy);
        cargoAnchor = Objects.requireNonNull(cargoAnchor, "operation travel cargo anchor");
        if (formation.containsValue(cargoAnchor)) throw new IllegalArgumentException("operation travel cargo anchor must remain distinct from every exact actor");
    }

    /** Explicit migration constructor for historic persisted horizontal corridors. */
    public OperationTravel(List<BlockPosition> corridor, int cursor, Map<SubjectId, BlockPosition> formation, BlockPosition cargoAnchor) {
        this(legacyTopology(corridor), cursor, formation, cargoAnchor);
    }

    /** Historical call sites can read surfaces during migration, but no longer own this value. */
    public List<BlockPosition> corridor() { return corridor(topology); }

    public BlockPosition currentPosition() { return corridor().get(cursor); }
    public boolean arrived() { return cursor == corridor().size() - 1; }
    public int nextHotCursor() { return Math.min(cursor + 1, corridor().size() - 1); }
    public int nextColdCursor() { return Math.min(cursor + MAX_COLD_ADVANCE, corridor().size() - 1); }

    /** A loaded physical caravan may certify only its immediately adjacent cell. */
    public boolean isExactHotAdvanceFrom(OperationTravel prior) {
        Objects.requireNonNull(prior, "prior operation travel");
        if (!topology.equals(prior.topology()) || cursor != prior.cursor() + 1 || !formation.keySet().equals(prior.formation.keySet())) return false;
        BlockPosition from = prior.currentPosition(), to = currentPosition();
        int deltaX = to.x() - from.x(), deltaY = to.y() - from.y(), deltaZ = to.z() - from.z();
        if (Math.abs(deltaX) + Math.abs(deltaZ) != 1 || Math.abs(deltaY) > 1) return false;
        boolean formationTranslated = formation.entrySet().stream().allMatch(entry -> entry.getValue().equals(prior.formation.get(entry.getKey()).offset(deltaX, deltaY, deltaZ)));
        return formationTranslated && cargoAnchor.equals(prior.cargoAnchor.offset(deltaX, deltaY, deltaZ));
    }

    public OperationTravel advance(int nextCursor, Map<SubjectId, BlockPosition> nextFormation, BlockPosition nextCargoAnchor) {
        if (nextCursor <= cursor || nextCursor > nextColdCursor()) {
            throw new IllegalArgumentException("operation travel cursor must advance by one bounded COLD step");
        }
        return new OperationTravel(topology, nextCursor, nextFormation, nextCargoAnchor);
    }

    private static List<BlockPosition> corridor(TraversalTopology topology) {
        return topology.linearCorridorSurfaces().stream().map(SurfaceAnchor::support).toList();
    }

    private static TraversalTopology legacyTopology(List<BlockPosition> corridor) {
        corridor = List.copyOf(Objects.requireNonNull(corridor, "legacy operation travel corridor"));
        if (corridor.size() < 2 || corridor.size() > MAX_CELLS) throw new IllegalArgumentException("operation travel corridor size is invalid");
        long revision = 0xcbf29ce484222325L;
        for (BlockPosition position : corridor) revision = (revision ^ position.hashCode()) * 0x100000001b3L;
        return TraversalTopology.corridor(new TraversalTopologyId("topology:legacy-operation:" + Long.toUnsignedString(revision, 36)),
                revision & Long.MAX_VALUE, FrontierRouteNetwork.OWNER, TraversalKind.PEDESTRIAN,
                java.util.Set.of(TraversalCapability.PEDESTRIAN, TraversalCapability.GROUND_BIOFORM),
                corridor.stream().map(SurfaceAnchor::new).toList());
    }
}
