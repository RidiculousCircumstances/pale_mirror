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
 * a loaded HOT lease blocks that advance rather than competing with Minecraft movement.</p>
 */
public record ResidentMigrationJourney(
        SubjectId residentId,
        SubjectId originSettlementId,
        SubjectId destinationHouseholdId,
        SubjectId destinationSettlementId,
        List<BlockPosition> route,
        int routeIndex,
        ResidentMigrationStatus status,
        Optional<ResidentMigrationBlockReason> blockReason
) {
    public static final int MAX_WAYPOINTS = 4_096;
    public static final int MAX_COLD_ADVANCE_BLOCKS = 4;

    public ResidentMigrationJourney {
        Objects.requireNonNull(residentId, "migration resident"); Objects.requireNonNull(originSettlementId, "migration origin");
        Objects.requireNonNull(destinationHouseholdId, "migration destination household"); Objects.requireNonNull(destinationSettlementId, "migration destination");
        route = List.copyOf(Objects.requireNonNull(route, "migration route")); Objects.requireNonNull(status, "migration status");
        blockReason = Objects.requireNonNull(blockReason, "migration block reason");
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
    }

    public BlockPosition currentPosition() { return route.get(routeIndex); }
    public boolean arriving() { return routeIndex == route.size() - 1; }
    public BlockPosition nextPosition() {
        if (arriving()) throw new IllegalStateException("migration journey is already at its final hand-off");
        return route.get(routeIndex + 1);
    }
    public int nextRouteIndex() { return Math.min(routeIndex + MAX_COLD_ADVANCE_BLOCKS, route.size() - 1); }
    public BlockPosition nextColdPosition() { return route.get(nextRouteIndex()); }
    public ResidentMigrationJourney advanceTo(int nextRouteIndex) {
        if (status != ResidentMigrationStatus.EN_ROUTE || arriving() || nextRouteIndex <= routeIndex
                || nextRouteIndex > routeIndex + MAX_COLD_ADVANCE_BLOCKS || nextRouteIndex >= route.size()) {
            throw new IllegalStateException("migration journey cannot make that COLD advance");
        }
        return new ResidentMigrationJourney(residentId, originSettlementId, destinationHouseholdId, destinationSettlementId, route,
                nextRouteIndex, ResidentMigrationStatus.EN_ROUTE, Optional.empty());
    }
    public ResidentMigrationJourney block(ResidentMigrationBlockReason reason) {
        return new ResidentMigrationJourney(residentId, originSettlementId, destinationHouseholdId, destinationSettlementId, route,
                routeIndex, ResidentMigrationStatus.BLOCKED, Optional.of(Objects.requireNonNull(reason, "migration block reason")));
    }
    public ResidentMigrationJourney resume() {
        if (status != ResidentMigrationStatus.BLOCKED) throw new IllegalStateException("only a blocked migration journey may resume");
        return new ResidentMigrationJourney(residentId, originSettlementId, destinationHouseholdId, destinationSettlementId, route,
                routeIndex, ResidentMigrationStatus.EN_ROUTE, Optional.empty());
    }
}
