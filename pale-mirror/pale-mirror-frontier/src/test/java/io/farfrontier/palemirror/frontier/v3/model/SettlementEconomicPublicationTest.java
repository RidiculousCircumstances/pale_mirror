package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.persistence.*;
import io.farfrontier.palemirror.frontier.v3.process.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SettlementEconomicPublicationTest {
    private static final SubjectId HOME = new SubjectId("settlement:1");
    private static FrontierWorldState initial() {
        return FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:published-economy"),
                126L, FrontierRulesets.dynamicFarmingGraybox()));
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
