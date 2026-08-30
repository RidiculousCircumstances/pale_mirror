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
        OperationTravel travel = new OperationTravel(List.of(new BlockPosition(0, 64, 0), new BlockPosition(1, 64, 0)), 0, complete.positions(), complete.positions().get(HAULER));

        assertEquals(OperationStage.EN_ROUTE, operation.startTravel(travel).stage());
        assertThrows(IllegalArgumentException.class, () -> operation.startTravel(new OperationTravel(travel.corridor(), 0, travel.formation(), new BlockPosition(0, 64, 0))));
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

    private static OperationAssembly.Member member(int cursor, int z) {
        return new OperationAssembly.Member(List.of(new BlockPosition(0, 64, z), new BlockPosition(1, 64, z)), cursor);
    }
}
