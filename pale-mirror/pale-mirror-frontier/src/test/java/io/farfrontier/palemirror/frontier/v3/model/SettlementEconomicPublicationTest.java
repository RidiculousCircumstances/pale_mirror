package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.persistence.*;
import io.farfrontier.palemirror.frontier.v3.process.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SettlementEconomicPublicationTest {
    @Test void marketOnlyEvidenceStartsAtCaptureAndCannotCrossANonMarketBranch() {
        var stock = Map.of(SettlementEconomicPlanningState.Change.STOCK, Set.of("minecraft:bread"));
        var market = Map.of(SettlementEconomicPlanningState.Change.MARKET, Set.of("minecraft:coal"));
        var captured = SettlementEconomicPlanningState.empty().invalidate(stock);
        var changed = captured.invalidate(market).invalidate(market);
        assertTrue(changed.onlyMarketChangesSince(captured), "stock was already accounted at capture; only later causes matter");
        assertFalse(changed.invalidate(stock).onlyMarketChangesSince(captured),
                "the same dirty stock flag cannot hide a second stock change");
        var branch = SettlementEconomicPlanningState.empty().invalidate(stock).invalidate(market).invalidate(market);
        assertEquals(changed, branch, "canonical causes/revisions may coincide on independent branches");
        assertFalse(branch.onlyMarketChangesSince(captured), "equal values are not evidence of the same non-market basis");
        var rebuilt = new SettlementEconomicPlanningState(captured.inputRevision(), captured.accepted(), captured.affected());
        assertEquals(captured, rebuilt);
        assertFalse(rebuilt.invalidate(market).onlyMarketChangesSince(captured));
        assertTrue(rebuilt.invalidate(market).onlyMarketChangesSince(rebuilt));
    }
    @Test void recoveryKeepsCanonicalInputsButStartsAFreshMarketEvidenceBoundary() {
        var state = initial();
        var captured = SettlementEconomicPlanningState.empty().invalidate(
                Map.of(SettlementEconomicPlanningState.Change.STOCK, Set.of("minecraft:bread")));
        state = state.withCompanies(state.companies().withGoodsTrade(state.companies().goodsTrade().withParticipants(
                state.companies().goodsTrade().participants().withEconomicState(HOME, captured))));
        var codec = new FrontierWorldStateCodec(); var restored = codec.decode(codec.encode(state));
        assertEquals(state, restored); assertArrayEquals(codec.encode(state), codec.encode(restored));
        var recovered = restored.companies().goodsTrade().participants().economicState(HOME);
        var changed = recovered.invalidate(Map.of(SettlementEconomicPlanningState.Change.MARKET, Set.of("minecraft:coal")));
        assertTrue(changed.onlyMarketChangesSince(recovered));
        assertFalse(changed.onlyMarketChangesSince(captured), "a pre-restart calculation cannot borrow new-process evidence");
    }
    private static final SubjectId HOME = new SubjectId("settlement:1");
    private static FrontierWorldState initial() {
        return FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:published-economy"),
                126L, FrontierRulesets.dynamicFarmingGraybox()));
    }
    @Test void batchedOwnerChangesPreserveEveryIndependentGenerationAndRejectUnknownOwners() {
        var registry = initial().companies().goodsTrade().participants();
        var peer = new SubjectId("settlement:2");
        var first = registry.economicState(HOME).invalidate(Map.of(SettlementEconomicPlanningState.Change.STOCK, Set.of("minecraft:bread")));
        var second = registry.economicState(peer).invalidate(Map.of(SettlementEconomicPlanningState.Change.CASH, Set.of()));
        var sequential = registry.withEconomicState(HOME, first).withEconomicState(peer, second);
        var batched = registry.withEconomicStates(Map.of(HOME, first, peer, second));
        assertEquals(sequential, batched);
        assertSame(first, batched.economicState(HOME)); assertSame(second, batched.economicState(peer));
        assertTrue(registry.planning().isEmpty(), "publication cannot mutate the original registry");
        assertThrows(IllegalArgumentException.class, () -> registry.withEconomicStates(Map.of(HOME, first,
                new SubjectId("settlement:unknown"), second)));
        assertSame(registry, registry.withEconomicStates(Map.of()));
        var state = initial();
        var advanced = state.withCompanies(state.companies().withGoodsTrade(state.companies().goodsTrade().withParticipants(batched)));
        var rewound = batched.withEconomicState(HOME, registry.economicState(HOME));
        assertThrows(IllegalArgumentException.class, () -> advanced.withCompanies(advanced.companies().withGoodsTrade(
                advanced.companies().goodsTrade().withParticipants(rewound))), "a sparse changed-row check must still reject an older owner generation");
        assertThrows(IllegalArgumentException.class, () -> advanced.withCompanies(advanced.companies().withGoodsTrade(
                advanced.companies().goodsTrade().withParticipants(registry))), "removing planning rows cannot erase their previous input generations");
    }
    @Test void readsAwaitPublicationAndOrdinaryBodyUpdatesKeepTheAcceptedDecision() {
        var state = initial(); var party = state.companies().goodsTrade().participants().participants().get(HOME);
        assertTrue(GoodsParticipantAssessment.read(state, party, 0).view().supplyPlan().isEmpty());
        assertEquals(SettlementEconomicPlanningState.Status.AWAITING_PLAN,
                state.companies().goodsTrade().participants().economicState(HOME).status(0));
        state = AcceptedEconomyFixture.publish(state, HOME, 10);
        var accepted = state.companies().goodsTrade().participants().economicState(HOME).accepted().orElseThrow();
        var actor = state.humanPopulation().residents().values().stream().filter(r -> r.settlementId().equals(HOME)).findFirst().orElseThrow();
        var body = state.actorLocations().get(actor.id()).body();
        var stepped = state.withActorBody(actor.id(), new BodyPosition(body.x() + 1, body.y(), body.z()));
        assertSame(accepted, stepped.companies().goodsTrade().participants().economicState(HOME).accepted().orElseThrow());
        assertSame(accepted.complete(), GoodsParticipantAssessment.view(stepped, party, 11).supplyPlan().orElseThrow());
        assertEquals(10, GoodsParticipantAssessment.read(stepped, party, 11).atTick());
        assertTrue(SettlementEconomicReviewRequests.changed(state, stepped, 11).isEmpty());
        assertTrue(stepped.companies().goodsTrade().participants().economicState(new SubjectId("settlement:2")).accepted().isEmpty(),
                "reading one participant cannot recursively plan its neighbour");
    }
    @Test void aForeignAcceptedPlanCannotDiscardUnchangedLocalObservedStock() {
        var state = AcceptedEconomyFixture.publish(initial(), HOME, 10);
        var party = state.companies().goodsTrade().participants().participants().get(HOME);
        var observed = GoodsParticipantView.observe(state, party);
        var publishedPeer = AcceptedEconomyFixture.publish(state, new SubjectId("settlement:2"), 11);
        assertSame(observed, GoodsParticipantView.observe(publishedPeer, party),
                "foreign market planning is not a new local stock, protection or cash fact");
        assertSame(state.inventory(), publishedPeer.inventory());
        var codec = new FrontierWorldStateCodec(); var recovered = codec.decode(codec.encode(publishedPeer));
        assertEquals(observed, GoodsParticipantView.observe(recovered,
                recovered.companies().goodsTrade().participants().participants().get(HOME)));
    }

    @Test void oneResidentsHungerThresholdInvalidatesOnlyItsOwnPlanAndRecoveryPreservesTheCause() {
        var state = AcceptedEconomyFixture.publish(initial(), HOME, 10);
        var actor = state.humanPopulation().residentsIn(HOME).getFirst();
        var rules = state.bootstrap().ruleset().residentLife();
        long onset = state.humanPopulation().nutrition(actor.id()).nextThresholdTick(rules,
                actor.characteristics().effectiveMetabolismPermille(0));
        var sameStatus = state.withHumanPopulation(state.humanPopulation().accrueHunger(actor.id(), 1, rules));
        assertSame(state.companies().goodsTrade().participants().planning(), sameStatus.companies().goodsTrade().participants().planning(),
                "ordinary satiety integration is not a new economic need threshold");
        var changed = sameStatus.withHumanPopulation(sameStatus.humanPopulation().accrueHunger(actor.id(), onset, rules));
        var reasons = changed.companies().goodsTrade().participants().economicState(HOME).affected();
        assertEquals(Set.of(SettlementFoodPolicy.BREAD), reasons.get(SettlementEconomicPlanningState.Change.NEED));
        assertEquals(state.companies().goodsTrade().participants().economicState(new SubjectId("settlement:2")),
                changed.companies().goodsTrade().participants().economicState(new SubjectId("settlement:2")));
        var codec = new FrontierWorldStateCodec(); var recovered = codec.decode(codec.encode(changed));
        assertEquals(reasons, recovered.companies().goodsTrade().participants().economicState(HOME).affected());
        var restored = recovered.withHumanPopulation(recovered.humanPopulation().consumeResidentBread(actor.id(), onset, rules));
        assertTrue(restored.companies().goodsTrade().participants().economicState(HOME).inputRevision()
                > changed.companies().goodsTrade().participants().economicState(HOME).inputRevision());
    }

    @Test void budgetedSoilPreparationKeepsThePlanButSoilDamageRequiresImmediateReview() {
        var state = CultivationPlanningTest.prepared();
        var site = state.resourceSiteDescriptors().values().stream().filter(f -> f.settlementId().equals(HOME)).findFirst().orElseThrow();
        var id = site.layout().cells().getFirst().id();
        for (var soil : List.of(ResourceFieldCycle.Soil.GRASS, ResourceFieldCycle.Soil.DIRT,
                ResourceFieldCycle.Soil.valueOf("COARSE_DIRT"))) {
            var bare = state.resourceSites().cycle(site.id()).observedInterference(id,
                    new ResourceFieldCycle.CellState(soil, ResourceFieldCycle.Crop.ABSENT, 0, false, false));
            state = state.withResourceSites(state.resourceSites().replace(state.resourceSites().site(site.id()), bare));
            state = AcceptedEconomyFixture.publish(state, HOME, 4000);
            var input = state.companies().goodsTrade().participants().economicState(HOME);
            var tilled = state.withResourceSites(state.resourceSites().replace(state.resourceSites().site(site.id()), bare.tilled(id)));
            assertEquals(input, tilled.companies().goodsTrade().participants().economicState(HOME),
                    "completing already forecast soil work cannot invalidate its own plan: " + soil);
            assertTrue(SettlementEconomicReviewRequests.changed(state, tilled, 4001).isEmpty());
            state = tilled;
        }
        var damaged = state.resourceSites().cycle(site.id()).observedInterference(id,
                new ResourceFieldCycle.CellState(ResourceFieldCycle.Soil.OBSTRUCTED, ResourceFieldCycle.Crop.OBSTRUCTED, 0, false, false));
        var changed = state.withResourceSites(state.resourceSites().replace(state.resourceSites().site(site.id()), damaged));
        assertEquals(List.of(GoodsParticipantProcess.review(HOME, 4002)), SettlementEconomicReviewRequests.changed(state, changed, 4001));
        assertTrue(changed.companies().goodsTrade().participants().economicState(HOME).changes()
                .contains(SettlementEconomicPlanningState.Change.CAPACITY));
    }

    @Test void publicationAndItsReasonsRoundTripWithoutRecalculatingPolicy() {
        var state = AcceptedEconomyFixture.publish(initial(), HOME, 10);
        var plan = state.companies().goodsTrade().participants().economicState(HOME).accepted().orElseThrow();
        var payload = new SettlementEconomicPlanPublished(plan); var codecs = FrontierWorldPayloadCodecs.create();
        assertEquals(payload, codecs.decode(payload.type(), codecs.encode(payload)));
        var snapshot = new FrontierWorldStateCodec();
        assertEquals(state, snapshot.decode(snapshot.encode(state)));
        var before = state;
        assertThrows(IllegalArgumentException.class, () -> SettlementEconomicPublication.apply(before, HOME, plan, 10),
                "the same publication is not a second admission");
    }
    @Test void addressedCashChangeRequestsOnlyItsOwnersAndCannotReuseTheirOldPurchaseBudget() {
        var state = AcceptedEconomyFixture.publish(initial(), HOME, 10);
        var peer = new SubjectId("settlement:2"); var untouched = new SubjectId("settlement:3");
        state = AcceptedEconomyFixture.publish(state, peer, 10);
        var before = state;
        state = state.withInventory(state.inventory().withEconomics(state.inventory().economics().transfer(HOME, peer, FixedScalar.ONE)));
        var requests = SettlementEconomicReviewRequests.changed(before, state, 11);
        assertEquals(Set.of(HOME, peer), requests.stream().map(a -> a.subject()).collect(java.util.stream.Collectors.toSet()));
        assertEquals(before.companies().goodsTrade().participants().economicState(untouched),
                state.companies().goodsTrade().participants().economicState(untouched));
        var participant = state.companies().goodsTrade().participants().participants().get(HOME);
        assertEquals(FixedScalar.ZERO, GoodsParticipantFunding.purchase(GoodsParticipantAssessment.trading(state, participant, 11),
                state.bootstrap().ruleset().goodsTrade(), "minecraft:coal"));
        var again = state.withInventory(state.inventory().withEconomics(state.inventory().economics().transfer(HOME, peer, FixedScalar.ONE)));
        assertEquals(requests.stream().map(a -> a.id()).toList(),
                SettlementEconomicReviewRequests.changed(state, again, 12).stream().map(a -> a.id()).toList(),
                "repeated changes use the same coalesced review identities");
    }
    @Test void aWholeCapacityChangeCannotBeNarrowedByAnUnrelatedStockChange() {
        var state = AcceptedEconomyFixture.publish(initial(), HOME, 10);
        var lifecycle = state.companies().goodsTrade().participants().economicState(HOME);
        var catalog = state.bootstrap().ruleset().goodsTrade().supplyPlanning().commodities().keySet();
        lifecycle = lifecycle.invalidate(Map.of(SettlementEconomicPlanningState.Change.STOCK, Set.of("minecraft:bread")));
        lifecycle = lifecycle.invalidate(Map.of(SettlementEconomicPlanningState.Change.CAPACITY, Set.of()));
        assertEquals(catalog, lifecycle.awaitingResources(11, catalog));
        var registry = state.companies().goodsTrade().participants().withEconomicState(HOME, lifecycle);
        var changed = state.withCompanies(state.companies().withGoodsTrade(state.companies().goodsTrade().withParticipants(registry)));
        var snapshot = new FrontierWorldStateCodec();
        assertEquals(changed, snapshot.decode(snapshot.encode(changed)));
        assertThrows(IllegalArgumentException.class, () -> changed.withCompanies(state.companies()),
                "chaining an old company component must not erase the economic input generation");
    }
    @Test void aLostFacilityInvalidatesItsOwnerWithoutReplanningTheNeighbour() {
        var state = AcceptedEconomyFixture.publish(initial(), HOME, 10);
        var conditions = new HashMap<>(state.structureConditions());
        var facility = FrontierWorldStateSupport.settlement(state.bootstrap(), HOME).structures().stream()
                .filter(value -> value.kind() == StructureKind.WORKSHOP).findFirst().orElseThrow();
        conditions.put(facility.id(), StructureCondition.DESTROYED);
        var changed = state.withChanges(FrontierWorldStateUpdate.begin().structureConditions(conditions));
        assertEquals(Set.of(HOME), SettlementEconomicReviewRequests.changed(state, changed, 11).stream()
                .map(action -> action.subject()).collect(java.util.stream.Collectors.toSet()));
        assertTrue(ProductionStockDemand.read(changed, HOME, "minecraft:bread", 11).missing() == 0);
        assertEquals(state.companies().goodsTrade().participants().economicState(new SubjectId("settlement:2")),
                changed.companies().goodsTrade().participants().economicState(new SubjectId("settlement:2")));
    }
    @Test void aPhysicalStorageConflictReconsidersCapacityWithoutAStockMutation() {
        var state = AcceptedEconomyFixture.publish(initial(), HOME, 10);
        var container = FrontierWorldState.depotId(HOME);
        var replica = new PhysicalReplicaRecord(container, ReferenceContainerCustody.semanticKind(state, container),
                0, 0, 1, "before", ReferenceContainerCustody.provenance(container), PhysicalReplicaState.CONFLICT,
                Optional.of("different"), Optional.of(ReferenceContainerCustody.provenance(container)),
                Optional.of(PhysicalReplicaConflictReason.FINGERPRINT_MISMATCH));
        var replicas = new HashMap<>(state.replicaCustody().replicas()); replicas.put(container, replica);
        var changed = state.withChanges(FrontierWorldStateUpdate.begin().replicaCustody(
                new PhysicalReplicaCustodyState(replicas, state.replicaCustody().custodyByScope())));
        assertSame(state.inventory(), changed.inventory());
        assertTrue(ReferenceContainerCustody.blocksCanonicalUse(changed, container));
        assertEquals(Set.of(HOME), SettlementEconomicReviewRequests.changed(state, changed, 11).stream()
                .map(action -> action.subject()).collect(java.util.stream.Collectors.toSet()));
        assertTrue(changed.companies().goodsTrade().participants().economicState(HOME).changes()
                .contains(SettlementEconomicPlanningState.Change.CAPACITY));
    }
    @Test void engineRetainsCoalescedReviewsAndReplaysItsPublishedDecision() {
        var world = new WorldId("frontier:economic-wal");
        var configuration = io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition.configuration(
                world, 126L, FrontierRulesets.dynamicFarmingGraybox());
        var tail = new ArrayList<io.farfrontier.palemirror.frontier.v3.kernel.TransactionRecord>();
        configuration = configuration.withTransactionCommitter((transaction, durability) -> tail.add(transaction));
        var engine = io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines.createCanonicalStateAccess(configuration);
        var initial = engine.checkpoint();
        for (int turn = 0; engine.canonicalState().state().companies().goodsTrade().participants().economicState(HOME).accepted().isEmpty(); turn++) {
            assertTrue(turn < 64, "initial economic review must not depend on its neighbours publishing first");
            var next = engine.nextExecutionBoundary().orElseThrow();
            var result = engine.advanceTo(next, new io.farfrontier.palemirror.frontier.v3.kernel.WorkBudget(128, 1024));
            assertEquals(EngineStatus.Kind.ACTIVE, result.status().kind(), result.status().failureDetail().orElse("active"));
        }
        assertTrue(tail.stream().flatMap(tx -> tx.events().stream()).anyMatch(event -> event.payload() instanceof SettlementEconomicPlanPublished));
        var reviews = engine.checkpoint().schedules().stream().filter(action -> action.kind().equals(GoodsParticipantProcess.REVIEW)).toList();
        assertEquals(reviews.size(), reviews.stream().map(action -> action.subject()).distinct().count());
        var recovered = io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines.recoverCanonicalStateAccess(configuration,
                new RecoveryImage(world, Optional.of(new SnapshotRecord(initial, 0)), tail));
        assertEquals(engine.canonicalState().state().companies(), recovered.canonicalState().state().companies());
        assertEquals(engine.canonicalState().state().inventory(), recovered.canonicalState().state().inventory());
        assertEquals(engine.checkpoint().schedules(), recovered.checkpoint().schedules());
    }
    @Test void ambiguousStationPortSchemasAreRejectedWithoutGuessingTheirDirection() {
        var codec = new FrontierWorldStateCodec();
        for (int schema : List.of(301, 302)) {
            byte[] header = java.nio.ByteBuffer.allocate(6).putInt(0x4656334D).putShort((short) schema).array();
            assertThrows(IllegalArgumentException.class, () -> codec.decode(header));
        }
        var current = initial();
        assertEquals(current, codec.decode(codec.encode(current)));
    }
    @Test void additionalOutputUsesOneRetainedCoverageBaseline() {
        var state = AcceptedEconomyFixture.publish(initial(), HOME, 10);
        var plan = state.companies().goodsTrade().participants().economicState(HOME).accepted().orElseThrow();
        for (var item : plan.complete().demands().keySet()) {
            var demand = ProductionStockDemand.read(state, HOME, item, 10);
            assertEquals(plan.complete().economy().orElseThrow().additionalProduction().get(item).longValue(), demand.missing());
            assertEquals(plan.productionCeilings().get(item).longValue(), demand.target());
        }
    }
}
