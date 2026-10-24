package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.market.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GoodsProcurementTransportTest {
    @Test void hungryBuyerCanQuoteProvisionedSellerButCannotInventItsOwnProvisionsOrUnfundedFreight() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:funded-delivery"),
                125L, FrontierRulesets.marketQuarryGraybox()));
        var seller = state.companies().goodsTrade().participants().participants().get(new SubjectId("settlement:6"));
        var buyer = state.companies().goodsTrade().participants().participants().get(new SubjectId("settlement:7"));
        var source = FrontierWorldState.depotId(seller.settlementId());
        var empty = state.withInventory(state.inventory().withFungibleResources(FungibleResourceLedger.empty()));
        long now = 1_000_000;
        assertTrue(GoodsProcurementTransport.best(empty, seller, source, buyer, "minecraft:coal", 16, 1_000_000, 100_000_000, now).isEmpty());
        var lot = new SubjectId("lot:seller-provisions");
        var resources = FungibleResourceLedger.empty().issue(new ResourceLot(lot, seller.party().id(), "minecraft:bread", 64,
                "fixture:provisions", List.of()), new CustodyAccount(ReferenceContainerCustody.scopeId(source),
                new ResourceCustody.Container(source), Map.of(lot, 64), Map.of()));
        var stocked = empty.withInventory(empty.inventory().withFungibleResources(resources));
        var quote = GoodsProcurementTransport.best(stocked, seller, source, buyer, "minecraft:coal", 16, 1_000_000, 100_000_000, now).orElseThrow();
        assertEquals(GoodsTradeFulfillment.Mode.SELLER_DELIVERY, quote.mode());
        assertEquals(seller, quote.organizer()); assertTrue(quote.cashFreight() > 0);
        assertTrue(quote.quantity() * 1_000_000L + quote.cashFreight() <= 100_000_000);
        assertTrue(GoodsProcurementTransport.best(stocked, seller, source, buyer, "minecraft:coal", 16, 1_000_000, 1_000_000, now).isEmpty());
        assertSame(resources, stocked.inventory().fungibleResources(), "a quote cannot reserve or consume stock");
        assertTrue(stocked.shipments().missions().isEmpty());
    }
    @Test void pastExpensiveFoodDoesNotBecomeTheCostOfLocallyReproducibleJourneyProvisions() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:provision-value"),
                125L, FrontierRulesets.marketQuarryGraybox()));
        var owner = state.companies().goodsTrade().participants().participants().get(new SubjectId("settlement:6"));
        var trade = state.companies().goodsTrade();
        var history = trade.priceHistory().record("minecraft:bread", new MarketPriceHistory.Sale(new SubjectId("receipt:expensive-food"),
                new SubjectId("settlement:7"), owner.party().id(), 100, 1, FixedScalar.whole(40)));
        state = state.withCompanies(state.companies().withGoodsTrade(new GoodsTradeState(trade.orders(), trade.contracts(),
                trade.participants(), history, trade.demandHistory())));
        var seller = trade.participants().participants().get(new SubjectId("settlement:7"));
        var offer = new GoodsTradeOrder(new SubjectId("order:valuation-live-offer"), seller.party(), owner.party(),
                GoodsTradeOrder.Side.SELL, FrontierWorldState.depotId(seller.settlementId()), "minecraft:bread", 32, 0,
                FixedScalar.ONE, 10000);
        state = state.withCompanies(state.companies().withGoodsTrade(state.companies().goodsTrade().place(offer)));
        var resident = state.humanPopulation().residents().values().stream()
                .filter(r -> r.settlementId().equals(owner.settlementId())).findFirst().orElseThrow();
        state = state.withHumanPopulation(state.humanPopulation().accrueHunger(resident.id(), 100, state.bootstrap().ruleset().residentLife()));
        assertFalse(GoodsParticipantView.observe(state, owner).stocks().isEmpty(),
                "retained reserves with live offers must not recursively re-enter market/valuation or rewind nutrition");
        var price = GoodsResourceValuation.replacement(state, owner, "minecraft:bread", 100);
        assertTrue(price.raw() > 0 && price.compareTo(FixedScalar.whole(40)) < 0, price.toString());
        assertEquals(FixedScalar.whole(40), state.companies().goodsTrade().priceHistory().reference("minecraft:bread", 24000).orElseThrow());
        var view = GoodsParticipantAssessment.domestic(state, owner, 100).view();
        var economic = GoodsEconomicPricing.assess(state, view, "minecraft:bread", Map.of("minecraft:bread", FixedScalar.whole(40)),
                new GoodsProductionPricing.Assessment(Map.of(), Map.of(), List.of()),
                Optional.of(new AcquisitionQuote(FixedScalar.ONE, FixedScalar.ONE)), 100);
        assertTrue(economic.acquisition().compareTo(FixedScalar.whole(2)) <= 0,
                "a currently cheaper acquisition must bound willingness, not inflate it from history");
    }
}
