package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RouteOperationTest {
    @Test void operationKeepsExactCargoPeopleAndFiniteRoute() {
        assertDoesNotThrow(() -> operation(List.of(new SubjectId("resident:1-1"), new SubjectId("resident:1-6")), 0, OperationStage.ASSEMBLING));
        assertThrows(IllegalArgumentException.class, () -> operation(List.of(new SubjectId("resident:1-1"), new SubjectId("resident:1-1")), 0, OperationStage.ASSEMBLING));
    }

    @Test void operationCannotClaimArrivalBeforeItsFinalRoutePoint() {
        assertThrows(IllegalArgumentException.class, () -> operation(List.of(new SubjectId("resident:1-1")), 0, OperationStage.ARRIVED));
        assertThrows(IllegalArgumentException.class, () -> operation(List.of(new SubjectId("resident:1-1")), 1, OperationStage.EN_ROUTE));
    }

    @Test void operationMayOwnOnlyTheNextExactTravelSegment() {
        SubjectId hauler = new SubjectId("resident:1-1");
        RouteOperation operation = operation(List.of(hauler), 0, OperationStage.EN_ROUTE);
        OperationTravel travel = new OperationTravel(List.of(new BlockPosition(0, 64, 0), new BlockPosition(1, 64, 0)), 0,
                Map.of(hauler, new BlockPosition(0, 64, 1)), new BlockPosition(0, 64, 0));
        assertDoesNotThrow(() -> operation.withTravel(travel));
    }

    private static RouteOperation operation(List<SubjectId> participants, int routeIndex, OperationStage stage) {
        java.util.Optional<OperationAssembly> assembly = stage == OperationStage.ASSEMBLING ? java.util.Optional.of(assembly(participants)) : java.util.Optional.empty();
        return new RouteOperation(new SubjectId("operation:supply-1"), new SubjectId("settlement:1"), new SubjectId("cargo:supply-1-1"),
                new SubjectId("hive:frontier"), participants, List.of(new BlockPosition(0, 64, 0), new BlockPosition(1, 64, 0)), routeIndex, stage, assembly, java.util.Optional.empty());
    }

    private static OperationAssembly assembly(List<SubjectId> participants) {
        java.util.Map<SubjectId, OperationAssembly.Member> members = new java.util.LinkedHashMap<>();
        for (int index = 0; index < participants.size(); index++) {
            members.put(participants.get(index), new OperationAssembly.Member(List.of(new BlockPosition(index, 64, 0)), 0));
        }
        return new OperationAssembly(members, participants.getFirst());
    }
}
