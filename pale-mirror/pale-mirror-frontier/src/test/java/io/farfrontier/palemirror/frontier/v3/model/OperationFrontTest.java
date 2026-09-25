package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OperationFrontTest {
    @Test
    void activeRouteSegmentDerivesDisjointDirectivesThatCannotEscapeOrOutliveTheirDecisionTurn() {
        var engine = FrontierEngines.create(FrontierV3FixtureCatalog.routeSceneReturnConfiguration(
                new WorldId("frontier:operation-front"), 91L));
        FrontierWorldState state = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        RouteOperation operation = FrontierDevelopmentScenarios.initialNorthwatchShipment(state).orElseThrow();

        OperationFront front = OperationFront.logistics(operation);
        assertEquals(operation.participantIds().stream().sorted().toList(), front.movements().keySet().stream().sorted().toList());
        assertEquals(operation.activeTravel().orElseThrow().frontId(), front.id());
        assertTrue(front.cargoId().isPresent());
        for (SubjectId actor : operation.participantIds()) {
            ActorDirective directive = front.directive(operation, new SceneLeaseId("lease:operation-front"), actor);
            assertEquals(actor, directive.actorId());
            assertEquals(operation.tacticalPlan().id(), directive.tacticalPlanId());
            assertTrue(directive.movement().permitsObservedSupport(directive.movement().nextCheckpoint()));
            assertFalse(directive.movement().permitsObservedSupport(new BlockPosition(99_999, 64, 99_999)));
        }

        FrontierWorldState reconsidered = state.withStrategicPlans(state.strategicPlans().reconsider(operation.settlementId(),
                state.strategicPlans().requireDecisionAuthority(operation.settlementId()).commitmentIds(), List.of()));
        assertFalse(FrontierSceneAdmission.coldInterceptionAvailable(reconsidered, operation.id()),
                "a stale tactical owner must not admit a new front/scene");
        IllegalArgumentException stale = assertThrows(IllegalArgumentException.class, () -> reconsidered.createOperation(operation));
        assertTrue(stale.getMessage().contains("stale decision authority"));
    }
}
