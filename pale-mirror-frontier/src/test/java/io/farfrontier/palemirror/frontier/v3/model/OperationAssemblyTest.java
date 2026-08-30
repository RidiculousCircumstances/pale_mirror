package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OperationAssemblyTest {
    private static final SubjectId HAULER = new SubjectId("resident:1-1");
    private static final SubjectId GUARD = new SubjectId("resident:1-2");

    @Test
    void advancesOnlyTheDeclaredBoundedApproachesWithoutChangingItsFormation() {
        OperationAssembly initial = new OperationAssembly(Map.of(
                HAULER, member(0, 0), GUARD, member(0, 1)), HAULER);
        OperationAssembly advanced = initial.advance(Map.of(HAULER, member(1, 0), GUARD, member(1, 1)));

        assertEquals(new BlockPosition(1, 64, 0), advanced.positions().get(HAULER));
        assertEquals(new BlockPosition(1, 64, 0), initial.advance(Map.of(HAULER, member(1, 0), GUARD, member(0, 1))).positions().get(HAULER));
        assertThrows(IllegalArgumentException.class, () -> initial.advance(Map.of(HAULER, member(0, 0), GUARD, member(0, 1))));
        assertThrows(IllegalArgumentException.class, () -> initial.advance(Map.of(HAULER, member(1, 0))));
    }

    @Test
    void routeOperationStartsTravelOnlyFromTheCompleteExactAssembly() {
        OperationAssembly complete = new OperationAssembly(Map.of(HAULER, member(1, 0), GUARD, member(1, 1)), HAULER);
        RouteOperation operation = new RouteOperation(new SubjectId("operation:supply-1"), new SubjectId("settlement:1"), new SubjectId("cargo:supply-1"),
                new SubjectId("hive:frontier"), List.of(HAULER, GUARD), List.of(new BlockPosition(0, 64, 0), new BlockPosition(1, 64, 0)), 0,
                OperationStage.ASSEMBLING, java.util.Optional.of(complete), java.util.Optional.empty());
        OperationTravel travel = new OperationTravel(List.of(new BlockPosition(0, 64, 0), new BlockPosition(1, 64, 0)), 0, complete.positions(), complete.cargoAnchor());

        assertEquals(OperationStage.EN_ROUTE, operation.startTravel(travel).stage());
        assertThrows(IllegalArgumentException.class, () -> operation.startTravel(new OperationTravel(travel.corridor(), 0, travel.formation(), new BlockPosition(0, 64, 0))));
    }

    @Test
    void completeAssemblyReservesASeparateCargoAnchorAndRejectsCollidingTravel() {
        OperationAssembly complete = new OperationAssembly(Map.of(HAULER, member(1, 0), GUARD, member(1, 1)), HAULER);

        assertEquals(new BlockPosition(1, 64, 2), complete.cargoAnchor());
        assertThrows(IllegalArgumentException.class, () -> new OperationTravel(
                List.of(new BlockPosition(0, 64, 0), new BlockPosition(1, 64, 0)), 0,
                complete.positions(), complete.positions().get(HAULER)));
        assertThrows(IllegalStateException.class, () -> new OperationAssembly(Map.of(
                HAULER, member(0, 0), GUARD, member(0, 1)), HAULER).cargoAnchor());
    }

    @Test
    void durableExactDeferralPinsOnlyTheNextUnarrivedCursorAndClearsOnRealAdvance() {
        OperationAssembly initial = new OperationAssembly(Map.of(HAULER, member(0, 0), GUARD, member(0, 1)), HAULER);
        OperationAssemblyDeferral deferral = new OperationAssemblyDeferral(HAULER, new BlockPosition(1, 64, 0),
                new BlockPosition(1, 64, 0), OperationAssemblyDeferral.Reason.LOADED_WORLD_OBSTRUCTION);

        OperationAssembly deferred = initial.defer(deferral);
        assertEquals(deferral, deferred.deferral().orElseThrow());
        assertEquals(java.util.Optional.empty(), deferred.advance(Map.of(HAULER, member(1, 0), GUARD, member(0, 1))).deferral(),
                "one accepted cursor observation is the only recovery path and clears the stale loaded-world observation");
        assertThrows(IllegalArgumentException.class, () -> deferred.advance(Map.of(HAULER, member(0, 0), GUARD, member(1, 1))),
                "a different member cannot clear the shared loaded-world deferral");
        assertThrows(IllegalArgumentException.class, () -> initial.defer(new OperationAssemblyDeferral(HAULER, new BlockPosition(9, 64, 0),
                new BlockPosition(9, 64, 0), OperationAssemblyDeferral.Reason.LOADED_WORLD_OBSTRUCTION)));
        assertThrows(IllegalArgumentException.class, () -> deferred.defer(new OperationAssemblyDeferral(GUARD, new BlockPosition(1, 64, 1),
                new BlockPosition(1, 64, 1), OperationAssemblyDeferral.Reason.LOADED_WORLD_OBSTRUCTION)),
                "a second actor cannot overwrite the physical fact that currently holds the shared operation");
        assertThrows(IllegalArgumentException.class, () -> new OperationAssembly(Map.of(HAULER, member(1, 0), GUARD, member(0, 1)), HAULER,
                java.util.Optional.of(deferral)));
    }

    @Test
    void compiledApproachTreatsSemanticHallFloorAsSupportButKeepsTheBodyClearance() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new io.farfrontier.palemirror.frontier.v3.api.WorldId("frontier:assembly-floor"), 91L));
        Settlement settlement = state.bootstrap().settlements().getFirst();
        SettlementAccessPort access = SettlementAccessPort.forHall(settlement.structures().stream()
                .filter(structure -> structure.kind() == StructureKind.HALL).findFirst().orElseThrow());
        SubjectId hauler = FrontierWorldStateSupport.availableRouteResident(state, settlement.id(), ResidentRole.HAULER).orElseThrow().id();

        List<BlockPosition> corridor = OperationAssemblyCorridor.compile(state, hauler, access.assemblyFloor());

        assertEquals(state.actorLocations().get(hauler).position(), corridor.getFirst());
        assertEquals(access.assemblyFloor(), corridor.getLast());
    }

    @Test
    void everyCompiledOperationApproachCellRetainsTwoClearSemanticBodyCells() {
        FrontierWorldState state = FrontierDevelopmentScenarios.operationAssemblyFixture(
                new io.farfrontier.palemirror.frontier.v3.api.WorldId("frontier:assembly-clearance"), 91L).state();
        RouteOperation operation = state.operations().get(new SubjectId("operation:supply-1-2"));
        java.util.Set<BlockPosition> geometry = FrontierGrayboxPlan.compile(state).cells().keySet();

        operation.activeAssembly().orElseThrow().members().forEach((actor, member) -> member.corridor().forEach(floor -> {
            org.junit.jupiter.api.Assertions.assertFalse(geometry.contains(floor.offset(0, 1, 0)) || geometry.contains(floor.offset(0, 2, 0)),
                    () -> "assembly corridor must not enter authored geometry: actor=" + actor + " floor=" + floor);
        }));
    }

    private static OperationAssembly.Member member(int cursor, int z) {
        return new OperationAssembly.Member(List.of(new BlockPosition(0, 64, z), new BlockPosition(1, 64, z)), cursor);
    }
}
