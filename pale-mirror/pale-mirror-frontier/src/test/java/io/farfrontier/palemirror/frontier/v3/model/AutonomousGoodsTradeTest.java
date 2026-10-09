package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.process.*;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Connected policy/ledger/station checks. Native motion and engine restart are separate acceptance levels. */
class AutonomousGoodsTradeTest {
    private static final SubjectId HOME = new SubjectId("settlement:1");
    private static final SubjectId COMPANY = CompanyFoundationProcess.companyId(HOME);
    private static final SubjectId DEPOT = FrontierWorldState.depotId(HOME);

    @Test void quarryCatalogOffersItsRealDepotSurplusToANonProducerThroughOrdinaryPublicTrading() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:stone-trade-policy"),
                20260918065L, FrontierRulesets.installed("frontier-v3-quarry-graybox-r1")));
        var seller = state.companies().goodsTrade().participants().participants().get(HOME);
        var producers = GrayboxQuarryPlan.producerSettlements(state.bootstrap()).stream().map(Settlement::id).toList();
        var buyer = seller.known().stream().filter(peer -> !producers.contains(peer.party().id()))
                .findFirst().orElseThrow().party().id();
        // Isolated policy input, not native mining evidence: the separate kernel/native
        // mining-haul checks establish how the same endpoint acquires this stock.
        state = issue(state, HOME, "minecraft:cobblestone", 128, "stone-policy-fixture");
        state = issue(state, HOME, "minecraft:bread", 384, "dispatch-provisions-fixture");
        var view = GoodsParticipantView.read(state, state.companies().goodsTrade().participants().participants().get(HOME));
        var stoneOffer = GoodsParticipantPolicies.require(view.participant()).decide(view, state.bootstrap().ruleset().goodsTrade()).stream()
                .filter(intent -> intent.itemKind().equals("minecraft:cobblestone")).findFirst().orElseThrow();
        assertEquals(GoodsTradeOrder.Side.SELL, stoneOffer.side()); assertEquals(64, stoneOffer.quantity());
        var treasury = state.inventory().economics().require(buyer).balance();
        state = review(state, HOME, 400); state = review(state, buyer, 400);
        var contract = state.companies().goodsTrade().contracts().values().stream().filter(value ->
                value.itemKind().equals("minecraft:cobblestone") && value.buyer().id().equals(buyer)).findFirst().orElseThrow();
        assertTrue(contract.quantity() > 0 && contract.quantity() <= 64, "normal funds/cargo limits may split the reserve purchase");
        assertEquals(HOME, contract.seller().id());
        assertEquals(treasury, state.inventory().economics().require(buyer).balance(), "dispatch is not acceptance/payment");
        assertEquals(contract.quantity(), GoodsParticipantView.read(state, state.companies().goodsTrade().participants().participants().get(buyer))
                .stocks().get("minecraft:cobblestone").expectedIncoming());
        assertEquals(state, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state)));
    }

    @Test void ordinarySettlementBootstrapExecutesItsTradeReviewWithoutACompany() {
        var configuration = FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:goods-company-foundation"), 47L);
        var engine = io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines.createCanonicalStateAccess(configuration);
        for (int boundary = 0; boundary < 2000; boundary++) {
            var participant = engine.canonicalState().state().companies().goodsTrade().participants().participants().get(HOME);
            if (participant != null && participant.reviewRevision() > 0) break;
            var next = engine.checkpoint().schedules().stream().sorted().findFirst().orElseThrow();
            var result = engine.advanceTo(next.dueAt(), new io.farfrontier.palemirror.frontier.v3.kernel.WorkBudget(128, 1024));
            assertEquals(EngineStatus.Kind.ACTIVE, result.status().kind(), result.status().failureDetail().orElse("active"));
        }
        assertTrue(engine.canonicalState().state().companies().companies().isEmpty());
        assertTrue(engine.canonicalState().state().companies().goodsTrade().participants().participants().get(HOME).reviewRevision() > 0);
        assertTrue(engine.checkpoint().schedules().stream().anyMatch(action -> action.subject().equals(HOME)
                && action.kind().equals(GoodsParticipantProcess.REVIEW)));
    }

    @Test void autonomousQuotesDispatchAndCompleteThroughTheActualKernelQueue() {
        var configuration = FrontierV3FixtureCatalog.autonomousGoodsConfiguration(new WorldId("frontier:autonomous-goods-kernel"), 41);
        var engine = io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines.createCanonicalStateAccess(configuration);
        var before = engine.canonicalState().state();
        assertTrue(before.companies().goodsTrade().orders().isEmpty()); assertTrue(before.shipments().shipments().isEmpty());
        // Coordinated frame arrivals are additional real queue boundaries, not a single courier leg.
        for (int boundary = 0; boundary < 2000; boundary++) {
            var current = engine.canonicalState().state();
            if (current.companies().goodsTrade().contracts().values().stream().anyMatch(GoodsTradeContract::fulfilled)) break;
            var next = engine.checkpoint().schedules().stream()
                    .filter(action -> !FrontierWorldRuntimeDefinition.scheduledHeld(current, action)).sorted().findFirst().orElseThrow();
            var advance = engine.advanceTo(new SimInstant(Math.max(engine.checkpoint().instant().ticks(), next.dueAt().ticks())),
                    new io.farfrontier.palemirror.frontier.v3.kernel.WorkBudget(128, 1024));
            assertEquals(EngineStatus.Kind.ACTIVE, advance.status().kind(), advance.status().failureDetail().orElse("active"));
        }
        var state = engine.canonicalState().state();
        var contract = state.companies().goodsTrade().contracts().values().stream().filter(GoodsTradeContract::fulfilled).findFirst()
                .orElseThrow(() -> new AssertionError("autonomous flow stalled: " + state.shipments()
                        + " decisions=" + state.companies().goodsTrade().participants().participants().values().stream()
                            .filter(value -> value.reviewRevision() > 0).map(value -> value.party().id().value() + "=" + value.decision()).toList()));
        assertEquals(16, contract.acceptedQuantity());
        var shipment = state.shipments().shipments().values().stream().filter(value -> value.authorization().claimantId().equals(contract.id())).findFirst().orElseThrow();
        assertEquals(Shipment.Status.DELIVERED, shipment.status()); assertTrue(shipment.reception().isEmpty());
        assertEquals(SettlementFoodPolicy.breadStock(before, contract.buyer().id()) + 16,
                SettlementFoodPolicy.breadStock(state, contract.buyer().id()));
        assertEquals(before.inventory().economics().require(contract.buyer().id()).balance().minus(FixedScalar.whole(32)),
                state.inventory().economics().require(contract.buyer().id()).balance());
        assertTrue(state.inventory().economics().reservations().isEmpty());
    }

    @Test void companyBuysOnlyExportablePublicGrainAndBuyerAcceptancePaysForActualTitle() {
        var state = companyFixture();
        state = issue(state, HOME, "minecraft:wheat", 64, "extra-public-grain");
        var publicMoney = state.inventory().economics().require(HOME).balance();
        var companyMoney = state.inventory().economics().require(COMPANY).balance();
        state = review(state, HOME, 400);
        state = review(state, COMPANY, 400);
        assertEquals(1, state.companies().goodsTrade().contracts().size());
        var contract = state.companies().goodsTrade().contracts().values().iterator().next();
        assertEquals(HOME, contract.seller().id()); assertEquals(COMPANY, contract.buyer().id());
        assertEquals(64, contract.quantity()); assertEquals(0, contract.acceptedQuantity());
        assertEquals(companyMoney, state.inventory().economics().require(COMPANY).balance());
        assertEquals(0, state.inventory().fungibleResources().totalQuantity(COMPANY, "minecraft:wheat"));
        assertTrue(state.shipments().shipments().isEmpty(), "same-container title transfer must not invent a cargo trip");
        state = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state));
        state = review(state, COMPANY, state.companies().goodsTrade().orders().get(contract.sellOrderId()).expiresAtTick() + 1);
        assertTrue(state.companies().goodsTrade().contracts().get(contract.id()).fulfilled());
        assertEquals(64, state.inventory().fungibleResources().totalQuantity(HOME, "minecraft:wheat"));
        assertEquals(64, state.inventory().fungibleResources().totalQuantity(COMPANY, "minecraft:wheat"));
        assertEquals(publicMoney.plus(FixedScalar.whole(64)), state.inventory().economics().require(HOME).balance());
        assertEquals(companyMoney.minus(FixedScalar.whole(64)), state.inventory().economics().require(COMPANY).balance());
    }

    @Test void mutuallyQuotedGoodsDoNotFreezeStockOrBuyerMoneyWithoutSourceDispatchBudget() {
        var configuration = FrontierV3FixtureCatalog.autonomousGoodsConfiguration(new WorldId("frontier:unfunded-dispatch"), 41);
        var state = io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines.createCanonicalStateAccess(configuration).canonicalState().state();
        var seller = state.companies().goodsTrade().participants().participants().get(HOME);
        var buyer = seller.known().getFirst().party().id();
        state = state.withInventory(state.inventory().withEconomics(state.inventory().economics().transfer(HOME, buyer,
                state.inventory().economics().availableToReserve(HOME))));
        var before = state;
        state = review(state, HOME, 400); state = review(state, buyer, 400);
        assertFalse(state.companies().goodsTrade().orders().isEmpty(), "consent may exist without a committed shipment");
        assertTrue(state.companies().goodsTrade().contracts().isEmpty());
        assertTrue(state.inventory().fungibleResources().claims().isEmpty());
        assertTrue(state.inventory().economics().reservations().isEmpty());
        assertEquals(before.inventory().economics(), state.inventory().economics());
        assertTrue(state.companies().goodsTrade().participants().participants().get(buyer).decision().contains("DISPATCH_UNFUNDED"));
    }

    @Test void delayedPeriodicAndOpportunityReviewsUseExecutionTimeAtTheOrderExpiryBoundary() {
        var quoted = review(issue(companyFixture(), HOME, "minecraft:wheat", 64, "delayed-public-grain"), HOME, 400);
        var sell = quoted.companies().goodsTrade().orders().values().stream()
                .filter(order -> order.party().id().equals(HOME) && order.counterparty().id().equals(COMPANY)
                        && order.side() == GoodsTradeOrder.Side.SELL && order.itemKind().equals("minecraft:wheat"))
                .findFirst().orElseThrow();
        var recovered = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(quoted));
        var rules = recovered.bootstrap().ruleset().goodsTrade();
        for (boolean periodic : List.of(true, false)) {
            var action = periodic ? GoodsParticipantProcess.review(COMPANY, 800)
                    : ((ScheduleEffect.Created) GoodsParticipantWakeup.party(recovered, COMPANY,
                            "delayed-review", 799).getFirst().payload()).action();
            for (long executedAt : new long[]{sell.expiresAtTick(), sell.expiresAtTick() + 1}) {
                // Exercise the registered dispatcher, not a helper that might bypass the lost clock.
                var events = FrontierWorldRuntimeDefinition.planScheduled(recovered, action, new SimInstant(executedAt));
                var next = apply(recovered, events, executedAt, "goods-trade");
                assertEquals(executedAt, next.companies().goodsTrade().participants().participants().get(COMPANY).reviewedAtTick());
                if (executedAt == sell.expiresAtTick()) {
                    var contract = next.companies().goodsTrade().contracts().values().stream().findFirst().orElseThrow();
                    assertEquals(sell.id(), contract.sellOrderId());
                    assertEquals(64, contract.quantity(), "the declared inclusive expiry boundary remains valid");
                } else {
                    assertTrue(next.companies().goodsTrade().contracts().isEmpty(), "expired consent cannot authorize new business");
                    assertTrue(next.inventory().economics().reservations().isEmpty());
                    assertEquals(recovered.inventory().economics(), next.inventory().economics());
                }
                var placedOrders = events.stream().map(ProposedEvent::payload).filter(GoodsTradeOrderPlaced.class::isInstance)
                        .map(GoodsTradeOrderPlaced.class::cast).toList();
                assertFalse(placedOrders.isEmpty());
                assertTrue(placedOrders.stream().allMatch(placed -> placed.order().expiresAtTick() == executedAt + rules.orderLifetime()));
                var nextReviews = events.stream().map(ProposedEvent::payload).filter(ScheduleEffect.Created.class::isInstance)
                        .map(ScheduleEffect.Created.class::cast).map(ScheduleEffect.Created::action)
                        .filter(scheduled -> scheduled.kind().equals(GoodsParticipantProcess.REVIEW)).toList();
                assertEquals(periodic ? 1 : 0, nextReviews.size());
                if (periodic) assertEquals(executedAt + rules.reviewInterval(), nextReviews.getFirst().dueAt().ticks());
            }
        }
    }

    @Test void registeredGoodsReviewRejectsAnExecutionInstantBeforeItsDeadline() {
        var state = companyFixture();
        var action = GoodsParticipantProcess.review(COMPANY, 800);
        assertThrows(IllegalArgumentException.class, () -> FrontierWorldRuntimeDefinition.planScheduled(state, action, new SimInstant(799)));
    }

    @Test void unfundedOrUnknownDemandIsExplainedWithoutInventingAnOrderOrPayment() {
        var state = companyFixture();
        var funds = state.inventory().economics().availableToReserve(HOME);
        state = state.withInventory(state.inventory().withEconomics(state.inventory().economics().transfer(HOME, new SubjectId("settlement:2"), funds)));
        state = review(state, HOME, 400);
        assertTrue(state.companies().goodsTrade().orders().isEmpty());
        assertTrue(state.companies().goodsTrade().participants().participants().get(HOME).decision().contains("NO_FUNDS"));
        assertTrue(state.inventory().economics().reservations().isEmpty());
        var participant = state.companies().goodsTrade().participants().participants().get(HOME);
        var map = new HashMap<>(state.companies().goodsTrade().participants().participants());
        map.put(HOME, new GoodsParticipant(participant.party(), participant.policy(), participant.endpoint(), List.of(),
                participant.reviewRevision(), participant.decision(), participant.reviewedAtTick()));
        state = state.withCompanies(state.companies().withGoodsTrade(state.companies().goodsTrade().withParticipants(new GoodsParticipantState(map))));
        state = review(state, HOME, 800);
        assertTrue(state.companies().goodsTrade().participants().participants().get(HOME).decision().startsWith("NO_KNOWN_COUNTERPARTY"));
        assertTrue(state.companies().goodsTrade().contracts().isEmpty());
    }

    @Test void ownAccountCompanyUsesTheSameBakerStationWithoutResidentWagesOrPublicInvoice() {
        var state = companyFixture();
        // Finite owned fixture stock isolates the production integration; procurement has its own connected check above.
        var publicWheat = new SubjectId("lot:bootstrap-1-wheat");
        var account = ReferenceContainerCustody.scopeId(DEPOT);
        state = state.withInventory(state.inventory().withFungibleResources(state.inventory().fungibleResources()
                .destroy(account, Map.of(publicWheat, 64), Map.of())));
        state = issue(state, COMPANY, "minecraft:wheat", 64, "company-grain");
        assertEquals(StrategicObjectiveKind.SETTLEMENT_COMPANY_PRODUCTION,
                SettlementManagementComposition.MANAGEMENT.decide(state,
                        FrontierWorldStateSupport.settlement(state.bootstrap(), HOME)).selected().orElseThrow().kind());
        var planned = StrategicObjectiveProcess.plan(state, StrategicObjectiveProcess.review(HOME, 1, 200));
        var task = planned.stream().map(ProposedEvent::payload).filter(StrategicTaskPlanned.class::isInstance)
                .map(StrategicTaskPlanned.class::cast).findFirst().orElseThrow().task();
        state = apply(state, planned, 200, "strategy");
        var startedEvents = ProductionProcess.planStart(state, ProductionProcess.start(task, 201));
        var started = startedEvents.stream().map(ProposedEvent::payload).filter(ProductionStarted.class::isInstance)
                .map(ProductionStarted.class::cast).findFirst().orElseThrow();
        assertEquals(ProductionRights.Mode.COMPANY_OWN_ACCOUNT, started.job().rights().mode());
        assertEquals(COMPANY, started.job().rights().resourceOwner().id());
        state = apply(state, startedEvents, 201, "economy");
        var job = started.job();
        assertTrue(state.inventory().economics().reservations().isEmpty());
        var companyMoney = state.inventory().economics().require(COMPANY).balance();
        var publicMoney = state.inventory().economics().require(HOME).balance();
        assertFalse(state.inventory().economics().accounts().containsKey(job.workerId()));
        boolean recoveredAtStation = false;
        for (long tick = 300, count = 0; state.productionJobs().containsKey(job.id()) && count < 700; tick += 20, count++) {
            var events = ProductionProcess.planCompletion(state, ProductionProcess.complete(job, tick));
            assertTrue(events.stream().anyMatch(event -> event.payload() instanceof BakeryColdStep), "bakery stalled: " + events);
            state = apply(state, events, tick, "economy");
            if (!recoveredAtStation && state.productionJobs().containsKey(job.id())
                    && state.productionJobs().get(job.id()).bakeryWork().orElseThrow().phase() == BakeryWorkState.Phase.PROCESSING) {
                state = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state));
                recoveredAtStation = true;
            }
        }
        assertTrue(recoveredAtStation); assertFalse(state.productionJobs().containsKey(job.id()));
        assertEquals(64, state.inventory().fungibleResources().totalQuantity(COMPANY, "minecraft:bread"));
        assertEquals(0, SettlementFoodPolicy.breadStock(state, HOME));
        assertEquals(publicMoney, state.inventory().economics().require(HOME).balance());
        assertEquals(companyMoney, state.inventory().economics().require(COMPANY).balance());
        assertFalse(state.inventory().economics().accounts().containsKey(job.workerId()));
        state = review(state, COMPANY, 20_000);
        state = review(state, HOME, 20_000);
        state = review(state, HOME, 20_400);
        assertEquals(64, SettlementFoodPolicy.breadStock(state, HOME));
        assertEquals(0, state.inventory().fungibleResources().totalQuantity(COMPANY, "minecraft:bread"));
        assertEquals(companyMoney.plus(FixedScalar.whole(128)), state.inventory().economics().require(COMPANY).balance());
    }

    @Test void publicPolicyDoesNotExportPromisedFoodTwiceOrOverrideTheNeedsReserve() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:policy-food-reserve"), 41));
        state = issue(state, HOME, "minecraft:bread", 200, "public-food");
        var participant = state.companies().goodsTrade().participants().participants().get(HOME);
        var view = GoodsParticipantView.read(state, participant);
        var stock = view.stocks().get("minecraft:bread");
        int protectedQuantity = Math.max(stock.protectedMinimum(), state.bootstrap().ruleset().goodsTrade().policies()
                .get(GoodsPolicyKind.PUBLIC_SETTLEMENT).stream().filter(value -> value.itemKind().equals("minecraft:bread"))
                .findFirst().orElseThrow().target(view.residents()));
        var afterPromise = new GoodsParticipantView(participant, view.residents(), view.availableMoney(),
                Map.of("minecraft:bread", new GoodsParticipantView.Stock(200, protectedQuantity, 0, protectedQuantity),
                        "minecraft:wheat", view.stocks().get("minecraft:wheat")));
        assertTrue(GoodsParticipantPolicies.require(participant).decide(afterPromise, state.bootstrap().ruleset().goodsTrade()).stream()
                .noneMatch(intent -> intent.itemKind().equals("minecraft:bread") && intent.side() == GoodsTradeOrder.Side.SELL));
        assertThrows(IllegalArgumentException.class, () -> GoodsParticipantPolicies.register(List.of()));
        assertThrows(IllegalArgumentException.class, () -> new GoodsParticipant(new GoodsTradeParty(HOME, EconomicOwnerKind.COMPANY),
                GoodsPolicyKind.PUBLIC_SETTLEMENT, participant.endpoint(), participant.known(), 0, "forged", 0));
    }

    private static FrontierWorldState companyFixture() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:autonomous-goods"), 41));
        state = apply(state, OptionalCompanyEmploymentFixture.foundation(state, HOME, 0), 0, "economy");
        // Finite capital transferred from existing treasury, never periodically minted by a market rule.
        var economics = state.inventory().economics().transfer(HOME, COMPANY, FixedScalar.whole(80))
                .transfer(new SubjectId("settlement:2"), HOME, FixedScalar.whole(100))
                .transfer(new SubjectId("settlement:3"), HOME, FixedScalar.whole(100));
        return state.withInventory(state.inventory().withEconomics(economics));
    }

    private static FrontierWorldState issue(FrontierWorldState state, SubjectId owner, String kind, int quantity, String suffix) {
        var lot = new SubjectId("lot:" + suffix); var staging = new SubjectId("custody:fixture-" + suffix);
        var destination = FungibleResourceCustodySupport.accountAtContainer(state, DEPOT);
        if (destination.isEmpty()) {
            var resources = state.inventory().fungibleResources().issue(new ResourceLot(lot, owner, kind, quantity, "fixture:" + suffix, List.of()),
                    new CustodyAccount(ReferenceContainerCustody.scopeId(DEPOT), new ResourceCustody.Container(DEPOT), Map.of(lot, quantity), Map.of()));
            return state.withInventory(state.inventory().withFungibleResources(resources));
        }
        var resources = state.inventory().fungibleResources().issue(new ResourceLot(lot, owner, kind, quantity, "fixture:" + suffix, List.of()),
                new CustodyAccount(staging, new ResourceCustody.Container(DEPOT), Map.of(lot, quantity), Map.of()));
        resources = resources.transfer(staging, destination.orElseThrow().id(), Map.of(lot, quantity), Map.of());
        return state.withInventory(state.inventory().withFungibleResources(resources));
    }

    private static FrontierWorldState review(FrontierWorldState state, SubjectId participant, long tick) {
        return apply(state, GoodsParticipantProcess.plan(state, GoodsParticipantProcess.review(participant, tick),
                new SimInstant(tick)), tick, "goods-trade");
    }

    private static FrontierWorldState apply(FrontierWorldState state, List<ProposedEvent> events, long tick, String family) {
        FrontierWorldRuntimeDefinition.processRegistry().validateEmissions(family, events);
        for (var proposed : events) {
            var codecs = FrontierWorldRuntimeDefinition.payloadCodecs();
            var payload = codecs.decode(proposed.payload().type(), codecs.encode(proposed.payload()));
            assertEquals(proposed.payload(), payload, "WAL must preserve declared ownership");
            if (payload instanceof ScheduleEffect) continue;
            var event = new FrontierEvent(FrontierEvent.SCHEMA_VERSION, new EventId("event:autonomous-goods"),
                    new TransactionId("transaction:autonomous-goods"), state.bootstrap().worldId(), new Revision(1), new SimInstant(tick),
                    proposed.subject(), CauseChain.root(new CommandId("command:autonomous-goods")), payload);
            var next = FrontierWorldRuntimeDefinition.reduce(state, event);
            FrontierReferenceClosure.validateTransition(state, next, List.of()); state = next;
        }
        return state;
    }
}
