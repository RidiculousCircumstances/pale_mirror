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

    @Test void autonomousQuotesDispatchAndCompleteThroughTheActualKernelQueue() {
        var configuration = FrontierV3FixtureCatalog.autonomousGoodsConfiguration(new WorldId("frontier:autonomous-goods-kernel"), 41);
        var engine = io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines.createCanonicalStateAccess(configuration);
        var before = engine.canonicalState().state();
        assertTrue(before.companies().goodsTrade().orders().isEmpty()); assertTrue(before.shipments().shipments().isEmpty());
        for (int boundary = 0; boundary < 120; boundary++) {
            var current = engine.canonicalState().state();
            if (current.companies().goodsTrade().contracts().values().stream().anyMatch(GoodsTradeContract::fulfilled)) break;
            var next = engine.checkpoint().schedules().stream()
                    .filter(action -> !FrontierWorldRuntimeDefinition.scheduledHeld(current, action)).sorted().findFirst().orElseThrow();
            var advance = engine.advanceTo(next.dueAt(), new io.farfrontier.palemirror.frontier.v3.kernel.WorkBudget(128, 1024));
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
        state = review(state, COMPANY, 800);
        assertTrue(state.companies().goodsTrade().contracts().get(contract.id()).fulfilled());
        assertEquals(64, state.inventory().fungibleResources().totalQuantity(HOME, "minecraft:wheat"));
        assertEquals(64, state.inventory().fungibleResources().totalQuantity(COMPANY, "minecraft:wheat"));
        assertEquals(publicMoney.plus(FixedScalar.whole(64)), state.inventory().economics().require(HOME).balance());
        assertEquals(companyMoney.minus(FixedScalar.whole(64)), state.inventory().economics().require(COMPANY).balance());
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

    @Test void ownAccountCompanyUsesTheSameBakerStationAndPaysAWageWithoutPublicInvoice() {
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
        var planned = StrategicObjectiveProcess.plan(state, StrategicObjectiveProcess.review(HOME, 1, 200), false);
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
        var reservation = state.inventory().economics().reservations().values().stream()
                .filter(value -> value.reasonId().equals(job.id())).findFirst().orElseThrow();
        assertEquals(COMPANY, reservation.payerId()); assertEquals(job.workerId(), reservation.payeeId());
        assertEquals(FixedScalar.ONE, reservation.amount());
        var companyMoney = state.inventory().economics().require(COMPANY).balance();
        var publicMoney = state.inventory().economics().require(HOME).balance();
        var workerMoney = state.inventory().economics().require(job.workerId()).balance();
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
        assertEquals(companyMoney.minus(FixedScalar.ONE), state.inventory().economics().require(COMPANY).balance());
        assertEquals(workerMoney.plus(FixedScalar.ONE), state.inventory().economics().require(job.workerId()).balance());
        state = review(state, COMPANY, 20_000);
        state = review(state, HOME, 20_000);
        state = review(state, HOME, 20_400);
        assertEquals(64, SettlementFoodPolicy.breadStock(state, HOME));
        assertEquals(0, state.inventory().fungibleResources().totalQuantity(COMPANY, "minecraft:bread"));
        assertEquals(companyMoney.minus(FixedScalar.ONE).plus(FixedScalar.whole(128)), state.inventory().economics().require(COMPANY).balance());
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
        state = apply(state, CompanyFoundationProcess.plan(state, CompanyFoundationProcess.review(HOME, 1, 0)), 0, "economy");
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
        return apply(state, GoodsParticipantProcess.plan(state, GoodsParticipantProcess.review(participant, tick)), tick, "goods-trade");
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
