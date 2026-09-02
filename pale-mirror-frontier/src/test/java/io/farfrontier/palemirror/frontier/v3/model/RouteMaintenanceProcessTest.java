package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.process.RouteMaintenanceProcess;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure admission regressions for the distinct in-place route-maintenance owner. */
class RouteMaintenanceProcessTest {
    @Test
    void knownRouteLossAdmitsOneExactLocalMaintenanceOwnerWithoutCreatingABypass() {
        FrontierBootstrap bootstrap = FrontierBootstrapper.create(new WorldId("frontier:route-maintenance-admission"), 41L);
        FrontierWorldState state = FrontierWorldState.initial(bootstrap);
        SubjectId settlement = bootstrap.settlements().getFirst().id();
        BlockPosition loss = state.routeTopology().supplyWaypoints(bootstrap, settlement).get(2);
        state = state.recordPhysicalDelta(new PhysicalDelta(loss, PhysicalDeltaKind.KNOWN_SEMANTIC_LOSS,
                Optional.of(FrontierRouteNetwork.OWNER), Optional.of(GrayboxSemanticPart.ROUTE_SURFACE), "player:test"));

        RouteMaintenanceStarted started = RouteMaintenanceProcess.plan(state, RouteMaintenanceProcess.scan(1, 100L)).stream()
                .map(ProposedEvent::payload).filter(RouteMaintenanceStarted.class::isInstance).map(RouteMaintenanceStarted.class::cast)
                .findFirst().orElseThrow();
        assertEquals(loss, started.maintenance().repairCell());
        assertEquals(settlement, started.maintenance().settlementId());
        assertEquals(GrayboxSemanticPart.ROUTE_SURFACE, started.maintenance().semanticPart());
        assertFalse(started.maintenance().team().memberIds().isEmpty());

        FrontierWorldState admitted = RouteMaintenanceStateSupport.reduceStarted(state, FrontierRouteNetwork.OWNER, started);
        assertEquals(1, admitted.routeMaintenances().size());
        assertTrue(admitted.routeConstructions().isEmpty(), "one lost cell is in-place maintenance, never a hidden bypass project");
        assertTrue(admitted.physicalDeltas().containsKey(loss), "admission retains the original loss until an observed repair receipt");
        assertEquals(state.routeTopology().supplyWaypoints(bootstrap, settlement), admitted.routeTopology().supplyWaypoints(bootstrap, settlement));
    }

    @Test
    void nonRouteOrUnknownLossCannotBecomeAControlledRouteRepair() {
        FrontierBootstrap bootstrap = FrontierBootstrapper.create(new WorldId("frontier:route-maintenance-foreign"), 41L);
        FrontierWorldState state = FrontierWorldState.initial(bootstrap);
        GrayboxCell nonRouteCell = FrontierGrayboxPlan.compile(state).cells().values().stream()
                .filter(cell -> !cell.ownerId().equals(FrontierRouteNetwork.OWNER)).findFirst().orElseThrow();
        BlockPosition ordinaryRouteCell = state.routeTopology().supplyWaypoints(bootstrap, bootstrap.settlements().getFirst().id()).get(2);
        FrontierWorldState foreign = state.recordPhysicalDelta(new PhysicalDelta(nonRouteCell.position(), PhysicalDeltaKind.KNOWN_SEMANTIC_LOSS,
                Optional.of(nonRouteCell.ownerId()), Optional.of(nonRouteCell.semanticPart()), "player:test"));
        FrontierWorldState unknown = state.recordPhysicalDelta(new PhysicalDelta(ordinaryRouteCell, PhysicalDeltaKind.UNKNOWN_SCAR,
                Optional.empty(), Optional.empty(), "player:test"));

        assertTrue(RouteMaintenanceProcess.plan(foreign, RouteMaintenanceProcess.scan(1, 100L)).stream()
                .noneMatch(event -> event.payload() instanceof RouteMaintenanceStarted));
        assertTrue(RouteMaintenanceProcess.plan(unknown, RouteMaintenanceProcess.scan(1, 100L)).stream()
                .noneMatch(event -> event.payload() instanceof RouteMaintenanceStarted));
    }

    @Test
    void terminalReadyMaintenanceCompactsItsBoundedOwnerInsteadOfRetainingItForever() {
        FrontierBootstrap bootstrap = FrontierBootstrapper.create(new WorldId("frontier:route-maintenance-close"), 41L);
        FrontierWorldState initial = FrontierWorldState.initial(bootstrap);
        SubjectId settlement = bootstrap.settlements().getFirst().id();
        BlockPosition loss = initial.routeTopology().supplyWaypoints(bootstrap, settlement).get(2);
        FrontierWorldState damaged = initial.recordPhysicalDelta(new PhysicalDelta(loss, PhysicalDeltaKind.KNOWN_SEMANTIC_LOSS,
                Optional.of(FrontierRouteNetwork.OWNER), Optional.of(GrayboxSemanticPart.ROUTE_SURFACE), "player:test"));
        RouteMaintenanceStarted started = RouteMaintenanceProcess.plan(damaged, RouteMaintenanceProcess.scan(1, 100L)).stream()
                .map(ProposedEvent::payload).filter(RouteMaintenanceStarted.class::isInstance).map(RouteMaintenanceStarted.class::cast)
                .findFirst().orElseThrow();
        FrontierWorldState admitted = RouteMaintenanceStateSupport.reduceStarted(damaged, FrontierRouteNetwork.OWNER, started);
        RouteMaintenance completed = started.maintenance().ready();
        FrontierWorldState ready = admitted.withChanges(FrontierWorldStateUpdate.begin()
                .routeMaintenances(Map.of(completed.id(), completed)).physicalDeltas(Map.of()));

        RouteMaintenanceClosed closed = RouteMaintenanceProcess.plan(ready, RouteMaintenanceProcess.scan(2, 200L)).stream()
                .map(ProposedEvent::payload).filter(RouteMaintenanceClosed.class::isInstance).map(RouteMaintenanceClosed.class::cast)
                .findFirst().orElseThrow();
        FrontierWorldState compacted = RouteMaintenanceStateSupport.reduceClosed(ready, FrontierRouteNetwork.OWNER, closed);

        assertTrue(compacted.routeMaintenances().isEmpty());
        assertTrue(compacted.physicalIntents().isEmpty());
        assertTrue(compacted.physicalObservations().isEmpty());
    }
}
