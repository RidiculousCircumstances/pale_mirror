package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldPayloadCodecs;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises the active meal owner with a different edible and multi-lot/slot portions. */
class ResidentFoodPortionTest {
    private static final String FOOD = "minecraft:carrot";
    private static final SubjectId LOT_A = new SubjectId("lot:portion-a");
    private static final SubjectId LOT_B = new SubjectId("lot:portion-b");
    private record Fixture(FrontierWorldState state, SubjectId resident, SubjectId depot, SurfaceAnchor service) { }

    private static Fixture fixture(int secondQuantity) {
        var base = FrontierRulesets.production();
        var life = base.residentLife();
        var foods = new FoodCatalog(Map.of(FoodCatalog.BREAD, new FoodCatalog.Food(FoodCatalog.BREAD, 1_000, 64),
                FOOD, new FoodCatalog.Food(FOOD, 100, 64)));
        var rules = new FrontierRuleset("portion-test", base.schemaVersion(), base.cadence(), base.spatial(),
                base.rates(), base.facilityCapacity(), base.combat(), base.hiveCommand(),
                new FrontierRuleset.ResidentLife(life.dayTicks(), life.workTicks(), life.satietyUnitTicks(),
                        life.eatBelowUnits(), life.mealTargetUnits(), foods, life.satietyCapacityUnits(),
                        life.metabolismDefaultPermille(), life.metabolismMinPermille(), life.metabolismMaxPermille(),
                        life.metabolismBaselineSpreadPermille(), life.starvation()), base.resourceHarvestColdTravelTicksPerEdge());
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:food-portion"), 421L, rules));
        var settlement = state.bootstrap().settlements().getFirst();
        var resident = settlement.residents().getFirst().id();
        var depot = FrontierWorldState.depotId(settlement.id());
        var account = ReferenceContainerCustody.scopeId(depot);
        var stock = state.inventory().fungibleResources().transformCold(account,
                Map.of(new SubjectId("lot:bootstrap-1-wheat"), 3), Map.of(),
                new ResourceLot(LOT_A, settlement.id(), FOOD, 3, "portion-test", List.of()))
                .transformCold(account, Map.of(new SubjectId("lot:bootstrap-1-wheat"), secondQuantity), Map.of(),
                        new ResourceLot(LOT_B, settlement.id(), FOOD, secondQuantity, "portion-test", List.of()))
                .destroy(account, Map.of(new SubjectId("lot:bootstrap-1-wheat"), 61 - secondQuantity), Map.of());
        var service = SettlementDepotServicePort.forDepot(settlement.structures().stream()
                .filter(value -> value.kind() == StructureKind.DEPOT).findFirst().orElseThrow()).serviceSurface();
        state = state.withActorBody(resident, service.standingBody()).withInventory(state.inventory().withFungibleResources(stock))
                .withHumanPopulation(state.humanPopulation().accrueHunger(resident, 96_000L, rules.residentLife()));
        return new Fixture(state, resident, depot, service);
    }

    private static <T extends FrontierPayload> T roundtrip(T payload) {
        var codec = FrontierWorldPayloadCodecs.create();
        @SuppressWarnings("unchecked") T decoded = (T) codec.decode(payload.type(), codec.encode(payload));
        assertEquals(payload, decoded);
        return decoded;
    }

    @Test void incompleteStockIsConsumedAsOneAvailablePortionWithoutMealDebt() {
        var fixture = fixture(2);
        var state = fixture.state();
        var resident = fixture.resident();
        var started = roundtrip(ResidentMealProcess.selectSourceAtYield(state, resident, 96_000L).orElseThrow());
        assertEquals(FOOD, started.meal().portion().itemKind());
        assertEquals(Map.of(LOT_A, 3, LOT_B, 2), started.meal().portion().lotQuantities());
        state = ResidentMealProcess.reduceStarted(state, resident, started);
        long tick = 96_001;
        for (int step = 0; state.humanPopulation().meals().containsKey(resident) && step < 16; step++) {
            tick = state.humanPopulation().meals().get(resident).coldTravel()
                    .map(route -> route.arrivalTick() + 1L).orElse(tick + 1L);
            state = ResidentMealProcess.reduceColdStep(state, resident,
                    ResidentMealProcess.planColdStep(state, resident, tick).orElseThrow());
        }
        assertFalse(state.humanPopulation().meals().containsKey(resident));
        assertEquals(500, state.humanPopulation().nutrition(resident).satietyUnits());
        assertEquals(0, state.inventory().fungibleResources().totalQuantity(started.meal().settlementId(), FOOD));
        assertTrue(state.inventory().fungibleResources().claims().isEmpty());
        assertTrue(ResidentMealOpportunity.find(state, resident, 96_004L).isEmpty());
    }

    @Test void hotMultiSlotPortionFencesAndRetiresTheExactAmountOnlyOnce() {
        var fixture = fixture(8);
        var state = fixture.state();
        var resident = fixture.resident();
        var account = ReferenceContainerCustody.scopeId(fixture.depot());
        var slots = List.of(new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ContainerSlot(
                        new InventoryCustody.ContainerSlot(fixture.depot(), 7)), FOOD, 3),
                new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ContainerSlot(
                        new InventoryCustody.ContainerSlot(fixture.depot(), 19)), FOOD, 8));
        var ledger = state.inventory().fungibleResources();
        state = state.withInventory(state.inventory().withFungibleResources(ledger.rebind(account, 1,
                FungiblePhysicalObservation.bind(ledger, account, 1, slots))));
        var replica = PhysicalReplicaRecord.expected(fixture.depot(), ReferenceContainerCustody.semanticKind(state, fixture.depot()),
                7, ReferenceContainerCustody.canonicalFingerprint(state, fixture.depot()), ReferenceContainerCustody.provenance(fixture.depot()));
        state = state.withChanges(FrontierWorldStateUpdate.begin().replicaCustody(PhysicalReplicaCustodyState.empty()
                .declare(replica).observe(fixture.depot(), 7, 1, replica.fingerprint(), replica.provenance(), 7)
                .acquire(new PhysicalCustodyLease(account, fixture.depot(), ReferenceContainerCustody.PROVIDER_ID,
                        1, 7, 2, PhysicalCustodyLeaseStatus.ACQUIRED, null))));
        var ambient = new AmbientActorLease(resident, fixture.service().standingBody(), new SimInstant(96_000), 1,
                AmbientLeaseStatus.PREPARED, AmbientGoalKind.PATROL, fixture.service().standingBody());
        state = AmbientLeaseStateProcess.transition(AmbientLeaseStateProcess.prepare(state, ambient), resident, AmbientLeaseStatus.HOT);
        var started = ResidentMealProcess.selectSourceAtYield(state, resident, 96_000).orElseThrow();
        assertEquals(9, started.meal().portion().quantity());
        assertEquals(Map.of(LOT_A, 3, LOT_B, 6), started.meal().portion().lotQuantities());
        state = ResidentActivityProcess.reduceMealStarted(state, resident, roundtrip(started));
        state = ResidentMealProcess.reduceHotArrived(state, resident, new ResidentMealHotArrived(resident, 1, fixture.service().standingBody(), started.meal().executionId()));
        var prepared = roundtrip(new ResidentMealHotEffectPrepared(resident,
                new ResidentMealPhysicalStep(ResidentMeal.Phase.TAKE, Map.of(7, 3, 19, 8), 0, 1, 1, 1, started.meal().executionId())));
        var before = state;
        assertThrows(IllegalArgumentException.class, () -> ResidentMealProcess.reduceHotPrepared(before, resident,
                new ResidentMealHotEffectPrepared(resident, new ResidentMealPhysicalStep(ResidentMeal.Phase.TAKE, 7, 3, 1, 1, 1, started.meal().executionId()))));
        state = ResidentMealProcess.reduceHotPrepared(state, resident, prepared);
        assertTrue(ContainerPhysicalAuthorityComposition.pending(state, fixture.depot()));
        assertFalse(ContainerPhysicalAuthorityComposition.pending(state, new SubjectId("container:other")));
        var isolatedInventory = state.inventory();
        var isolatedRecovery = state.fencedRecovery();
        var container = isolatedInventory.containers().get(fixture.depot());
        for (var phase : List.of(ContainerSurfaceStatus.PREPARED, ContainerSurfaceStatus.ACTIVE, ContainerSurfaceStatus.CONFLICT)) {
            isolatedInventory = isolatedInventory.withSurfaceStatus(fixture.depot(), phase);
            isolatedRecovery = FencedRecoveryContainerSupport.transition(isolatedRecovery, container, phase);
        }
        var isolated = state.withChanges(FrontierWorldStateUpdate.begin()
                .inventory(isolatedInventory).fencedRecovery(isolatedRecovery));
        var binding = isolatedRecovery.current().get(FencedRecoveryContainerSupport.bindingId(container));
        assertThrows(IllegalArgumentException.class, () -> ReferenceSurfaceRecovery.verify(isolated,
                new ReferenceSurfaceVerified(fixture.depot(), 7, 2, binding.authorityEpoch(),
                        replica.fingerprint(), replica.provenance())));
        var hand = new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ActorPocket(resident,
                SceneLease.deterministicEntityId(state.bootstrap().worldId(), resident), 0), FOOD, 9);
        roundtrip(new ResidentMealHotHandMaterialized(resident, 1, hand, started.meal().executionId()));
        for (var equipmentHand : ActorContainerItemOrder.Hand.values())
            roundtrip(new ResidentMealHotHandReleased(resident, 1, new FungiblePhysicalObservation.Stack(
                    new PhysicalStackAddress.ActorHand(resident,
                            SceneLease.deterministicEntityId(state.bootstrap().worldId(), resident), equipmentHand), FOOD, 9), started.meal().executionId()));
        state = ResidentActivityProcess.reduceMealEffectObserved(state, resident, roundtrip(new ResidentMealHotEffectObserved(resident,
                ResidentMeal.Phase.TAKE, 1, fixture.service().standingBody(),
                List.of(new FungiblePhysicalObservation.Stack(slots.getLast().address(), FOOD, 2)), List.of(hand), started.meal().executionId())), 96_001);
        assertFalse(ContainerPhysicalAuthorityComposition.pending(state, fixture.depot()));
        var eating = state.humanPopulation().meals().get(resident).clearingSurface().standingBody();
        var atService = state;
        assertThrows(IllegalArgumentException.class, () -> ResidentMealProcess.reduceHotPrepared(atService, resident,
                new ResidentMealHotEffectPrepared(resident,
                        new ResidentMealPhysicalStep(ResidentMeal.Phase.CONSUME, -1, 9, 1, 0, 1, started.meal().executionId()))));
        state = ResidentMealProcess.reduceHotAccessCleared(state, resident,
                roundtrip(new ResidentMealHotAccessCleared(resident, 1, eating, started.meal().executionId())));
        state = ResidentMealProcess.reduceHotPrepared(state, resident, roundtrip(new ResidentMealHotEffectPrepared(resident,
                new ResidentMealPhysicalStep(ResidentMeal.Phase.CONSUME, -1, 9, 1, 0, 1, started.meal().executionId()))));
        var consumed = new ResidentMealHotEffectObserved(resident, ResidentMeal.Phase.CONSUME, 1,
                eating, List.of(), List.of(), started.meal().executionId());
        var events = ResidentMealProcess.planHotObserved(state, consumed, 96_002);
        assertTrue(events.stream().anyMatch(event -> event.payload().equals(consumed)));
        for (var event : events) {
            if (event.payload() instanceof ResidentStarvationIntegrated health)
                state = ResidentStarvationProcess.reduce(state, resident, health);
            else if (event.payload() instanceof ResidentMealHotEffectObserved receipt)
                state = ResidentActivityProcess.reduceMealEffectObserved(state, resident, receipt, 96_002);
        }
        assertEquals(900, state.humanPopulation().nutrition(resident).satietyUnits());
        assertEquals(2, state.inventory().fungibleResources().totalQuantity(started.meal().settlementId(), FOOD));
        assertFalse(state.inventory().fungibleResources().accounts().containsKey(started.meal().actorAccountId()));
        var after = state;
        assertThrows(IllegalArgumentException.class, () -> ResidentMealProcess.reduceHotObserved(after, resident, consumed, 96_002));
    }
}
