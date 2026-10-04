package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One exact person's durable COLD route between two existing settlement households.
 *
 * <p>The route contains every traversable COLD position from its current hand-off through its
 * destination hand-off. A COLD advance may consume only a small bounded run of those positions;
 * independent physical custody blocks that advance rather than competing with Minecraft.
 * A saved off-checkpoint body retains a separately versioned known approach to the next
 * journey checkpoint, without rewriting the original route or resetting semantic progress.</p>
 */
public record ResidentMigrationJourney(
        SubjectId residentId,
        SubjectId originSettlementId,
        SubjectId destinationHouseholdId,
        SubjectId destinationSettlementId,
        List<BlockPosition> route,
        int routeIndex,
        ResidentMigrationStatus status,
        Optional<ResidentMigrationBlockReason> blockReason,
        io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId executionId,
        StationApproachState spatial
) {
    public static final int MAX_WAYPOINTS = 4_096;
    public static final int MAX_COLD_ADVANCE_BLOCKS = 4;

    public ResidentMigrationJourney {
        Objects.requireNonNull(residentId, "migration resident"); Objects.requireNonNull(originSettlementId, "migration origin");
        Objects.requireNonNull(destinationHouseholdId, "migration destination household"); Objects.requireNonNull(destinationSettlementId, "migration destination");
        route = List.copyOf(Objects.requireNonNull(route, "migration route")); Objects.requireNonNull(status, "migration status");
        blockReason = Objects.requireNonNull(blockReason, "migration block reason");
        Objects.requireNonNull(executionId, "migration execution");
        Objects.requireNonNull(spatial, "migration spatial continuation");
        if (!executionId.actorId().equals(residentId) || !executionId.activityOwnerId().equals(residentId)
                || executionId.activityKind() != io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind.TRANSIT)
            throw new IllegalArgumentException("migration requires its exact declared transit execution");
        if (route.size() < 2 || route.size() > MAX_WAYPOINTS || routeIndex < 0 || routeIndex >= route.size()
                || route.stream().anyMatch(Objects::isNull) || originSettlementId.equals(destinationSettlementId)) {
            throw new IllegalArgumentException("migration journey must have a bounded cross-settlement route");
        }
        if (status == ResidentMigrationStatus.BLOCKED != blockReason.isPresent()) {
            throw new IllegalArgumentException("migration block reason must match journey status");
        }
        for (int index = 1; index < route.size(); index++) {
            BlockPosition previous = route.get(index - 1), current = route.get(index);
            if (previous.y() != current.y() || Math.abs(previous.x() - current.x()) + Math.abs(previous.z() - current.z()) != 1) {
                throw new IllegalArgumentException("migration route must contain adjacent horizontal positions");
            }
        }
        if (spatial.approach().isPresent() && !spatial.approach().orElseThrow().target().support().equals(
                route.get(Math.min(routeIndex + MAX_COLD_ADVANCE_BLOCKS, route.size() - 1))))
            throw new IllegalArgumentException("migration rejoin must retain its next exact journey checkpoint");
    }

    public long routeRevision() { return spatial.revision(); }
    public Optional<io.farfrontier.palemirror.frontier.v3.model.navigation.TraversalRejoin> rejoin() { return spatial.approach(); }
    public BlockPosition currentPosition() { return spatial.current(new SurfaceAnchor(route.get(routeIndex))).support(); }
    public boolean arriving() { return routeIndex == route.size() - 1 && !spatial.pending(); }
    public BlockPosition nextPosition() {
        if (arriving()) throw new IllegalStateException("migration journey is already at its final hand-off");
        if (spatial.waitingOrigin().isPresent()) throw new IllegalStateException("migration is waiting for known approach geometry");
        if (rejoin().isPresent()) return rejoin().orElseThrow().path().get(
                rejoin().orElseThrow().nextCursor(1)).support();
        return route.get(routeIndex + 1);
    }
    public int nextRouteIndex() { return Math.min(routeIndex + MAX_COLD_ADVANCE_BLOCKS, route.size() - 1); }
    public BlockPosition nextColdPosition() { return route.get(nextRouteIndex()); }
    public ResidentMigrationJourney advanceTo(int nextRouteIndex) {
        if (status != ResidentMigrationStatus.EN_ROUTE || spatial.pending() || arriving() || nextRouteIndex <= routeIndex
                || nextRouteIndex > routeIndex + MAX_COLD_ADVANCE_BLOCKS || nextRouteIndex >= route.size()) {
            throw new IllegalStateException("migration journey cannot make that COLD advance");
        }
        return new ResidentMigrationJourney(residentId, originSettlementId, destinationHouseholdId, destinationSettlementId, route,
                nextRouteIndex, ResidentMigrationStatus.EN_ROUTE, Optional.empty(), executionId, spatial);
    }
    /** Keeps the original route/cursor/identity; only the approach from actual pose changes. */
    public ResidentMigrationJourney withSpatial(StationApproachState approach) {
        if (approach.revision() != Math.incrementExact(spatial.revision()))
            throw new IllegalArgumentException("migration checkpoint requires its next exact spatial revision");
        return new ResidentMigrationJourney(residentId, originSettlementId, destinationHouseholdId, destinationSettlementId,
                route, routeIndex, status, blockReason, executionId, approach);
    }
    public ResidentMigrationJourney advanceRejoin(io.farfrontier.palemirror.frontier.v3.model.navigation.TraversalRejoin approach, int nextCursor) {
        if (status != ResidentMigrationStatus.EN_ROUTE || !spatial.pending()
                || !approach.current().support().equals(currentPosition())
                || !approach.target().support().equals(nextColdPosition()))
            throw new IllegalArgumentException("migration has no runnable retained rejoin");
        var advanced = approach.advance(nextCursor, MAX_COLD_ADVANCE_BLOCKS);
        return new ResidentMigrationJourney(residentId, originSettlementId, destinationHouseholdId, destinationSettlementId,
                route, advanced.arrived() ? nextRouteIndex() : routeIndex, status, blockReason, executionId,
                new StationApproachState(spatial.revision(), advanced.arrived() ? Optional.empty() : Optional.of(advanced), Optional.empty()));
    }
    /** Positive HOT goal arrival consumes the approach, never an old predecessor pose. */
    public ResidentMigrationJourney arrivedHot(int next) {
        if (status != ResidentMigrationStatus.EN_ROUTE || arriving() || next != nextRouteIndex())
            throw new IllegalArgumentException("migration HOT arrival has no exact current goal");
        return new ResidentMigrationJourney(residentId, originSettlementId, destinationHouseholdId, destinationSettlementId,
                route, next, status, blockReason, executionId,
                new StationApproachState(spatial.revision(), Optional.empty(), Optional.empty()));
    }
    public ResidentMigrationJourney block(ResidentMigrationBlockReason reason) {
        return new ResidentMigrationJourney(residentId, originSettlementId, destinationHouseholdId, destinationSettlementId, route,
                routeIndex, ResidentMigrationStatus.BLOCKED, Optional.of(Objects.requireNonNull(reason, "migration block reason")), executionId, spatial);
    }
    public ResidentMigrationJourney resume() {
        if (status != ResidentMigrationStatus.BLOCKED) throw new IllegalStateException("only a blocked migration journey may resume");
        return new ResidentMigrationJourney(residentId, originSettlementId, destinationHouseholdId, destinationSettlementId, route,
                routeIndex, ResidentMigrationStatus.EN_ROUTE, Optional.empty(), executionId, spatial);
    }
}
