package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.persistence.*;
import io.farfrontier.palemirror.frontier.v3.process.*;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ConditionalProductionQuoteTest {
    private static final SubjectId SUPPLIER = new SubjectId("settlement:1"), BUYER = new SubjectId("settlement:7");
    private static final String ITEM = "minecraft:cobblestone";
    private record Fixture(FrontierWorldState state, ConditionalProductionQuote quote) { }
    @Test void missingSellStockCanBePricedWithoutCreatingSupplyOrSpendingResources() {
        var f = fixture(); var state = f.state(); var q = f.quote();
        var supplierPlan = state.companies().goodsTrade().participants().economicState(SUPPLIER).accepted().orElseThrow();
        assertEquals(0L, supplierPlan.complete().economy().orElseThrow().additionalProduction().get(ITEM),
                "the buyer's low bid is not consent for export production");
        assertTrue(state.productionJobs().isEmpty());
        var buyer = state.companies().goodsTrade().participants().participants().get(BUYER);
        assertEquals(0, GoodsParticipantStorage.unclaimed(state, buyer, ITEM));
        assertEquals(0, GoodsParticipantView.observe(state, buyer).stocks().get(ITEM).expectedIncoming());
        var snapshot = new FrontierWorldStateCodec(); var recovered = snapshot.decode(snapshot.encode(state));
        assertEquals(state, recovered);
        var payload = new SettlementEconomicPlanPublished(supplierPlan); var codecs = FrontierWorldPayloadCodecs.create();
        assertEquals(payload, codecs.decode(payload.type(), codecs.encode(payload)));
        var before = state.inventory();
        var domestic = SettlementEconomicEvaluation.domestic(state, buyer, 5002);
        var prices = GoodsParticipantPricing.review(state, buyer, 5002, domestic);
        assertTrue(prices.get(ITEM).bid().raw() > 1_810_000,
                "future priced production must reach the requesting buyer: " + prices.get(ITEM) + "; offer=" + q);
        assertSame(before, state.inventory());
        var consenting = review(state, BUYER, 5002);
        var producing = review(consenting, SUPPLIER, 5003);
        assertTrue(producing.companies().goodsTrade().participants().economicState(SUPPLIER).accepted().orElseThrow()
                .complete().economy().orElseThrow().additionalProduction().get(ITEM) > 0,
                "the ordinary buyer review must turn cost feedback into consent before the supplier plans output");
        assertEquals(0, GoodsParticipantStorage.unclaimed(producing,
                producing.companies().goodsTrade().participants().participants().get(SUPPLIER), ITEM));
        assertTrue(ConditionalSupplyPricing.best(state, buyer, ITEM, q.quantity(), 100_000_000, q.expiresAtTick()).isEmpty());
        byte[] oldSchema = snapshot.encode(state); java.nio.ByteBuffer.wrap(oldSchema).putShort(4, (short) 303);
        assertThrows(IllegalArgumentException.class, () -> snapshot.decode(oldSchema));
    }
    @Test void reusedDomesticForecastDoesNotReviveBidsExpiredBeforeItsNewMarketCapture() {
        var fixture = fixture(); var state = fixture.state();
        var supplier = state.companies().goodsTrade().participants().participants().get(SUPPLIER);
        var domestic = SettlementEconomicEvaluation.domestic(state, supplier, 5002);
        long expiry = state.companies().goodsTrade().orders().values().stream()
                .mapToLong(GoodsTradeOrder::expiresAtTick).max().orElseThrow();
        var before = SettlementEconomicEvaluation.complete(state, domestic, 0, 1, expiry + 1000, 5002);
        assertFalse(before.quotes().isEmpty(), "fixture supplies an actual conditional buyer, not an empty-market false pass");
        var expired = SettlementEconomicEvaluation.complete(state, domestic, 0, 1, expiry + 1000, expiry);
        assertTrue(expired.quotes().isEmpty(), "market expiry is evaluated now, even when domestic facts remain reusable");
        assertSame(domestic.domestic().orElseThrow(), expired.assessment().domestic().orElseThrow());
        assertEquals(5002, expired.assessment().atTick(), "the old forecast must not claim to be newly computed");
        assertThrows(IllegalArgumentException.class, () -> SettlementEconomicEvaluation.complete(state, domestic, 0, 1,
                expiry + 1000, 5001));
    }

    @Test void ownedCollectorSuppliesAreNotAnotherCashInvoiceInAcquisitionWillingness() {
        var f = fixture(); var state = f.state();
        var buyer = state.companies().goodsTrade().participants().participants().get(BUYER);
        var assessment = SettlementEconomicEvaluation.domestic(state, buyer, 5002);
        var references = new TreeMap<String, FixedScalar>();
        state.bootstrap().ruleset().goodsTrade().policies().get(buyer.policy()).forEach(c -> references.put(c.itemKind(), c.minimumSellPrice()));
        var production = GoodsProductionPricing.assess(state, assessment, references);
        var qualified = new io.farfrontier.palemirror.frontier.v3.model.market.AcquisitionQuote(FixedScalar.ONE, FixedScalar.whole(200));
        var value = GoodsEconomicPricing.assess(state, assessment.view(), ITEM, references, production, Optional.of(qualified), 5002);
        assertEquals(201_000_000, value.acquisition().raw(), "only the invoice needs cash; qualified own provisions still have economic value");
        assertEquals(FixedScalar.whole(100), state.inventory().economics().require(BUYER).balance());
        var rules = state.bootstrap().ruleset().goodsTrade();
        var prices = new TreeMap<>(GoodsParticipantPricing.review(state, buyer, 5002, assessment));
        var old = prices.get(ITEM);
        prices.put(ITEM, new io.farfrontier.palemirror.frontier.v3.model.market.MarketPricePolicy.Quote(
                old.ask(), value.acquisition(), old.reference(), old.reviewedAt(), old.surplus(), old.lastSale(),
                io.farfrontier.palemirror.frontier.v3.model.market.StockSalePlan.retained()));
        var view = assessment.view();
        var priced = new GoodsParticipantView(buyer.withPrices(prices), view.residents(), view.availableMoney(),
                view.stocks(), view.purchaseLimits(), view.supplyPlan(), view.awaitingReview());
        var budget = GoodsParticipantFunding.purchase(priced, rules, ITEM);
        assertTrue(budget.raw() >= qualified.goods().raw() && budget.raw() < value.acquisition().raw(),
                "cash budget=" + budget + "; value=" + value + "; demand="
                        + assessment.view().supplyPlan().orElseThrow().require(ITEM));
        var intent = GoodsParticipantPolicies.require(priced.participant()).decide(priced, rules).stream()
                .filter(i -> i.itemKind().equals(ITEM) && i.side() == GoodsTradeOrder.Side.BUY).findFirst().orElseThrow();
        assertEquals(value.acquisition(), intent.limit(), "the published economic price must not be truncated to the separate cash budget");

    }
    @Test void foreignOwnerOrOrganizerFailsBeforePublicationAndTechnicalRenewalDoesNotPingPong() {
        var f = fixture(); var state = f.state(); var q = f.quote();
        var at = new SimInstant(5003);
        var advice = FrontierWorldProcessCatalog.adviceScheduled(FrontierWorldRuntimeDefinition.processRegistry(), state,
                GoodsParticipantProcess.review(SUPPLIER, at.ticks()), at).orElseThrow().prepare();
        var exact = advice.revalidate(state, at).orElseThrow().prepare().get();
        assertTrue(exact.stream().map(io.farfrontier.palemirror.frontier.v3.api.ProposedEvent::payload)
                .filter(SettlementEconomicPlanPublished.class::isInstance).map(SettlementEconomicPlanPublished.class::cast)
                .anyMatch(event -> event.plan().peerVersions().containsKey(BUYER)), "the fixture must actually quote this buyer");
        var market = state.companies().goodsTrade(); var capturedRegistry = market.participants();
        var buyerMarketChanged = capturedRegistry.withEconomicState(BUYER, capturedRegistry.economicState(BUYER).invalidate(
                Map.of(SettlementEconomicPlanningState.Change.MARKET, Set.of("minecraft:bread"))));
        assertTrue(advice.revalidate(state.withCompanies(state.companies().withGoodsTrade(market.withParticipants(buyerMarketChanged))), at).isPresent(),
                "a quoted buyer retains the same accepted consent when only incoming market observations changed");
        var buyerCashChanged = capturedRegistry.withEconomicState(BUYER, capturedRegistry.economicState(BUYER).invalidate(
                Map.of(SettlementEconomicPlanningState.Change.CASH, Set.of())));
        assertTrue(advice.revalidate(state.withCompanies(state.companies().withGoodsTrade(market.withParticipants(buyerCashChanged))), at).isEmpty(),
                "dated market observations must not lend stale funding consent from an actually quoted buyer");
        var renewed = next(state, q, 5003, q.goodsUnitPrice());
        var after = SettlementEconomicPublication.apply(state, SUPPLIER, renewed, 5003);
        var changedBuyer = AcceptedEconomyFixture.publish(state, BUYER, 5002);
        assertFalse(SettlementEconomicPublication.peerEnvelopesCurrent(changedBuyer.companies().goodsTrade().participants(), renewed));
        assertThrows(IllegalArgumentException.class, () -> SettlementEconomicPublication.apply(changedBuyer, SUPPLIER, renewed, 5003),
                "a real conditional offer retains its exact buyer envelope even though unrelated advice may proceed");
        assertTrue(SettlementEconomicReviewRequests.changed(state, after, 5003).stream().noneMatch(a -> a.subject().equals(BUYER)),
                "a new source generation/expiry alone is not new economic information");
        var changed = next(state, q, 5003, q.goodsUnitPrice() + 1);
        var repriced = SettlementEconomicPublication.apply(state, SUPPLIER, changed, 5003);
        assertTrue(SettlementEconomicReviewRequests.changed(state, repriced, 5003).stream().anyMatch(a -> a.subject().equals(BUYER)));
        var foreign = state.companies().goodsTrade().participants().participants().get(new SubjectId("settlement:3")).party();
        var wrongQuote = new ConditionalProductionQuote(foreign, q.recipient(), q.itemKind(), q.quantity(), q.goodsUnitPrice(),
                q.resourceFreight(), q.cashFreight(), new GoodsTradeFulfillment(GoodsTradeFulfillment.Mode.BUYER_COLLECTION, q.recipient()),
                q.capacityWindowTicks(), q.deliveryTicks(), renewed.inputRevision(), renewed.revision(), renewed.atTick(), q.expiresAtTick());
        assertThrows(IllegalArgumentException.class, () -> copy(renewed, List.of(q)), "old source revision is never inferred into the successor");
        var invalid = copy(renewed, List.of(wrongQuote));
        assertThrows(IllegalArgumentException.class, () -> SettlementEconomicPublication.apply(state, SUPPLIER, invalid, 5003));
        var falseSupplierKind = new GoodsTradeParty(q.supplier().id(), EconomicOwnerKind.COMPANY);
        var falseRecipientKind = new GoodsTradeParty(q.recipient().id(), EconomicOwnerKind.COMPANY);
        for (boolean supplierKind : new boolean[]{true, false}) {
            var declaredSupplier = supplierKind ? falseSupplierKind : q.supplier();
            var declaredRecipient = supplierKind ? q.recipient() : falseRecipientKind;
            var wrongKind = new ConditionalProductionQuote(declaredSupplier, declaredRecipient, q.itemKind(), q.quantity(),
                    q.goodsUnitPrice(), q.resourceFreight(), q.cashFreight(),
                    new GoodsTradeFulfillment(GoodsTradeFulfillment.Mode.BUYER_COLLECTION, declaredRecipient),
                    q.capacityWindowTicks(), q.deliveryTicks(), renewed.inputRevision(), renewed.revision(), renewed.atTick(), q.expiresAtTick());
            assertThrows(IllegalArgumentException.class, () -> SettlementEconomicPublication.apply(state, SUPPLIER,
                    copy(renewed, List.of(wrongKind)), 5003), "an ID cannot supply a missing/forged legal kind");
            var registry = state.companies().goodsTrade().participants();
            var forged = registry.withEconomicState(SUPPLIER, registry.economicState(SUPPLIER).accept(copy(renewed, List.of(wrongKind))));
            var corrupt = state.withCompanies(state.companies().withGoodsTrade(state.companies().goodsTrade().withParticipants(forged)));
            var codec = new FrontierWorldStateCodec(); byte[] encoded = codec.encode(corrupt);
            assertThrows(IllegalArgumentException.class, () -> codec.decode(encoded), "hydration rejects the same forged nominal kind");
        }
        assertThrows(IllegalArgumentException.class, () -> new ConditionalProductionQuote(q.supplier(), q.recipient(), q.itemKind(),
                q.quantity(), q.goodsUnitPrice(), q.resourceFreight(), q.cashFreight(),
                new GoodsTradeFulfillment(GoodsTradeFulfillment.Mode.BUYER_COLLECTION, q.supplier()),
                q.capacityWindowTicks(), q.deliveryTicks(), q.sourceInputRevision(), q.sourcePlanRevision(), q.assessedAtTick(), q.expiresAtTick()));
    }
    private static SettlementEconomicPlan next(FrontierWorldState state, ConditionalProductionQuote q, long now, long goodsPrice) {
        var lifecycle = state.companies().goodsTrade().participants().economicState(SUPPLIER);
        var old = lifecycle.accepted().orElseThrow();
        var quote = new ConditionalProductionQuote(q.supplier(), q.recipient(), q.itemKind(), q.quantity(), goodsPrice,
                q.resourceFreight(), q.cashFreight(), q.fulfillment(), q.capacityWindowTicks(), q.deliveryTicks(),
                lifecycle.inputRevision(), old.revision() + 1, now, q.expiresAtTick());
        return new SettlementEconomicPlan(old.revision() + 1, lifecycle.inputRevision(), now, old.reviewAtTick(), old.domestic(),
                old.complete(), old.productionCeilings(), old.peerVersions(), old.affectedProducts(), old.cultivation(), old.needs(), List.of(quote));
    }
    private static SettlementEconomicPlan copy(SettlementEconomicPlan p, List<ConditionalProductionQuote> quotes) {
        return new SettlementEconomicPlan(p.revision(), p.inputRevision(), p.atTick(), p.reviewAtTick(), p.domestic(), p.complete(),
                p.productionCeilings(), p.peerVersions(), p.affectedProducts(), p.cultivation(), p.needs(), quotes);
    }
    private static FrontierWorldState review(FrontierWorldState state, SubjectId owner, long tick) {
        var events = GoodsParticipantProcess.plan(state, GoodsParticipantProcess.review(owner, tick), new SimInstant(tick));
        var codecs = io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition.payloadCodecs();
        for (var proposed : events) {
            var payload = codecs.decode(proposed.payload().type(), codecs.encode(proposed.payload()));
            if (payload instanceof io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect) continue;
            var event = new FrontierEvent(
                    FrontierEvent.SCHEMA_VERSION,
                    new EventId("event:conditional-review"), new TransactionId("transaction:conditional-review"),
                    state.bootstrap().worldId(), new Revision(1), new SimInstant(tick), proposed.subject(),
                    CauseChain.root(new CommandId("command:conditional-review")), payload);
            state = io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition.reduce(state, event);
        }
        return state;
    }
    private static Fixture fixture() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:conditional-production"), 126, FrontierRulesets.toolGraybox()));
        for (var id : state.resourceSites().sites().keySet().stream().sorted().toList()) {
            var events = ResourceSiteProcess.planPreparation(state, ResourceSiteProcess.preparation(id, 4000));
            state = ResourceSiteProcess.reducePreparationStarted(state, id, (ResourceSitePreparationStarted) events.getFirst().payload());
            state = ResourceSiteProcess.reducePrepared(state, id, (ResourceSitePrepared) events.get(1).payload());
        }
        // This story negotiates stone inputs, not the choice between stone and iron tools.
        // Make that market condition explicit rather than relying on unrelated bakery labour costs.
        var market = state.companies().goodsTrade();
        for (String kind : List.of("minecraft:raw_iron", "minecraft:iron_ingot"))
            market = market.recordSale(kind,
                    new io.farfrontier.palemirror.frontier.v3.model.market.MarketPriceHistory.Sale(
                            new SubjectId("receipt:conditional-fixture-" + kind.replace(':', '-')),
                            SUPPLIER, BUYER, 4000, 1, FixedScalar.whole(20)));
        state = state.withCompanies(state.companies().withGoodsTrade(market));
        var inventory = state.inventory();
        for (var item : List.copyOf(inventory.items().values())) if (item.economicOwnerId().equals(BUYER) && item.itemKind().equals("minecraft:stone_hoe"))
            inventory = inventory.changeCondition(item, new ItemCondition(item.condition().maximumDamage(), 120));
        state = AcceptedEconomyFixture.publish(state.withInventory(inventory), BUYER, 5000);
        var buyer = state.companies().goodsTrade().participants().participants().get(BUYER);
        var supplier = state.companies().goodsTrade().participants().participants().get(SUPPLIER);
        int quantity = Math.toIntExact(state.companies().goodsTrade().participants().economicState(BUYER).accepted().orElseThrow().domestic().require(ITEM).purchase());
        state = state.withCompanies(state.companies().withGoodsTrade(state.companies().goodsTrade().place(new GoodsTradeOrder(
                new SubjectId("order:conditional-production-input"), buyer.party(), GoodsTradeOrder.Side.BUY,
                FrontierWorldState.depotId(BUYER), ITEM, quantity, 0, new FixedScalar(1_810_000), 10000))));
        var domestic = SettlementEconomicEvaluation.domestic(state, supplier, 5001);
        var prices = GoodsParticipantPricing.review(state, supplier, 5001, domestic);
        state = GoodsParticipantPricing.applyCommitted(state, SUPPLIER, new GoodsParticipantPricesReviewed(supplier.reviewRevision(), 5001, prices), 5001);
        var before = state.inventory(); state = AcceptedEconomyFixture.publish(state, SUPPLIER, 5001);
        assertSame(before, state.inventory());
        var quote = state.companies().goodsTrade().participants().economicState(SUPPLIER).accepted().orElseThrow().conditionalQuotes().stream()
                .filter(q -> q.recipient().equals(buyer.party()) && q.itemKind().equals(ITEM)).findFirst().orElseThrow();
        return new Fixture(state, quote);
    }
}
