package io.farfrontier.palemirror.frontier.v3.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.farfrontier.palemirror.frontier.v3.model.GoodsTradeTest.*;

/** Unchanged declared obligations must follow either endpoint changing or disappearing. */
class GoodsTradeReferenceValidationTest {
    @Test void indexedCourierReferencesStillRejectEitherUnchangedSideLosingItsExactPartner() {
        var initial = reserved(); var parcel = shipment(initial);
        var dispatched = shipmentFact(initial, SELLER, new ShipmentDispatched(parcel));
        var shipments = dispatched.shipments(); var executions = dispatched.actorExecutions();
        ShipmentExecutionCapability.validateReferences(shipments, executions);
        var vacant = executions.finish(parcel.execution());
        assertThrows(IllegalArgumentException.class, () -> ShipmentExecutionCapability.validateReferences(shipments, vacant),
                "an unchanged parcel cannot borrow validation after its actor relinquishes execution");
        ShipmentExecutionCapability.validateReferences(shipments, executions);
        assertThrows(IllegalArgumentException.class, () -> ShipmentExecutionCapability.validateReferences(ShipmentState.empty(), executions),
                "an unchanged courier cannot lose its exact parcel");
        assertDoesNotThrow(() -> ShipmentExecutionCapability.validateReferences(shipments, executions));
        assertDoesNotThrow(() -> ImmutableInvariantCheck.full(() -> {
            ShipmentExecutionCapability.validateReferences(shipments, executions); return null;
        }));
    }

    @Test void unchangedContractStillRejectsLostFinancialAndResourceReferencesAfterIndexedValidation() {
        var state = reserved();
        GoodsTradeStateSupport.validate(state);
        var lostHold = state.withInventory(state.inventory().withEconomics(state.inventory().economics().release(HOLD)));
        assertThrows(IllegalArgumentException.class, () -> GoodsTradeStateSupport.validate(lostHold),
                "unchanged contracts must be revisited when their exact financial hold disappears");
        GoodsTradeStateSupport.validate(state);
        var lostClaim = state.withInventory(state.inventory().withFungibleResources(
                state.inventory().fungibleResources().releaseClaim(ReferenceContainerCustody.scopeId(FrontierWorldState.depotId(SELLER)), CLAIM)));
        assertThrows(IllegalArgumentException.class, () -> GoodsTradeStateSupport.validate(lostClaim),
                "unchanged contracts must be revisited when their exact resource claim disappears");
        GoodsTradeStateSupport.validate(state);
        assertDoesNotThrow(() -> ImmutableInvariantCheck.full(() -> { GoodsTradeStateSupport.validate(state); return null; }));
    }

}
