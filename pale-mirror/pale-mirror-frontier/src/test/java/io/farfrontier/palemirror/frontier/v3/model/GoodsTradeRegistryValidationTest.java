package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static io.farfrontier.palemirror.frontier.v3.model.GoodsTradeTest.*;
import static org.junit.jupiter.api.Assertions.*;

/** Commercial registry invariants; end-to-end custody and payment remain in GoodsTradeTest. */
class GoodsTradeRegistryValidationTest {
    @Test void warmedCommercialTablesRejectChangedIdentitiesAndCompetingClaimsOrMoneyHolds() {
        var state = reserved(); var trade = state.companies().goodsTrade(); var contract = trade.contracts().get(CONTRACT);
        for (boolean duplicateClaim : List.of(false, true)) {
            assertDoesNotThrow(() -> trade.withParticipants(trade.participants()));
            var other = new GoodsTradeContract(id("contract:competing"), contract.sellOrderId(), contract.buyOrderId(),
                    contract.seller(), contract.buyer(), contract.fulfillment(), contract.sourceContainerId(), contract.receiverContainerId(),
                    contract.itemKind(), contract.quantity(), contract.goodsUnitPrice(),
                    duplicateClaim ? id("reservation:other") : contract.financialReservationId(), 0,
                    duplicateClaim ? contract.outstandingClaims() : Map.of(id("claim:other"), contract.quantity()), Map.of());
            var contracts = ImmutableLookupMap.withEntry(trade.contracts(), other.id(), other);
            assertThrows(IllegalArgumentException.class, () -> new GoodsTradeState(trade.orders(), contracts, trade.participants()));
            assertThrows(IllegalArgumentException.class, () -> ImmutableInvariantCheck.full(() ->
                    new GoodsTradeState(trade.orders(), contracts, trade.participants())));
            assertDoesNotThrow(() -> trade.withParticipants(trade.participants()), "rejected tables cannot poison the valid immutable predecessor");
        }
        var other = new GoodsTradeContract(id("contract:other-receipt"), contract.sellOrderId(), contract.buyOrderId(),
                contract.seller(), contract.buyer(), contract.fulfillment(), contract.sourceContainerId(), contract.receiverContainerId(),
                contract.itemKind(), contract.quantity(), contract.goodsUnitPrice(), id("reservation:other-receipt"), 0,
                Map.of(id("claim:other-receipt"), contract.quantity()), Map.of());
        var sharedReceipt = id("receipt:competing");
        var first = contract.dispose(new GoodsTradeDisposition(sharedReceipt, contract.id(), 0,
                contract.outstandingClaims().keySet().iterator().next(), contract.quantity(), GoodsTradeDisposition.Reason.CANCELLED_BEFORE_LOADING));
        var second = other.dispose(new GoodsTradeDisposition(sharedReceipt, other.id(), 0,
                id("claim:other-receipt"), other.quantity(), GoodsTradeDisposition.Reason.CANCELLED_BEFORE_LOADING));
        var receipts = ImmutableLookupMap.withEntry(ImmutableLookupMap.withEntry(trade.contracts(), first.id(), first), second.id(), second);
        assertThrows(IllegalArgumentException.class, () -> new GoodsTradeState(trade.orders(), receipts, trade.participants()));
        assertThrows(IllegalArgumentException.class, () -> ImmutableInvariantCheck.full(() ->
                new GoodsTradeState(trade.orders(), receipts, trade.participants())));
        assertDoesNotThrow(() -> trade.withParticipants(trade.participants()));
        var orders = ImmutableLookupMap.withEntry(trade.orders(), id("order:foreign-key"), trade.orders().get(contract.sellOrderId()));
        assertThrows(IllegalArgumentException.class, () -> new GoodsTradeState(orders, trade.contracts(), trade.participants()));
        assertThrows(IllegalArgumentException.class, () -> ImmutableInvariantCheck.full(() ->
                new GoodsTradeState(orders, trade.contracts(), trade.participants())));
        var codec = new FrontierWorldStateCodec(); assertEquals(state, codec.decode(codec.encode(state)));
    }
    private static SubjectId id(String value) { return new SubjectId(value); }
}
