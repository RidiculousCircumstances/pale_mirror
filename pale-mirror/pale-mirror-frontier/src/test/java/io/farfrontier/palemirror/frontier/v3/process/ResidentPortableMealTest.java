package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class ResidentPortableMealTest {
    private record Fixture(FrontierWorldState state, SubjectId actor, SubjectId account, SubjectId lot) { }

    private Fixture carriedBread() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:portable-food"), 421L));
        var settlement = state.bootstrap().settlements().getFirst();
        var actor = settlement.residents().getFirst().id();
        var depot = FrontierWorldState.depotId(settlement.id());
        var account = new SubjectId("custody:portable-food");
        var lot = new SubjectId("lot:portable-food");
        var source = ReferenceContainerCustody.scopeId(depot);
        var ledger = state.inventory().fungibleResources().transformCold(source,
                Map.of(new SubjectId("lot:bootstrap-1-wheat"), 8), Map.of(),
                new ResourceLot(lot, settlement.id(), FoodCatalog.BREAD, 8, "test", List.of()));
        var order = new ActorContainerItemOrder(new SubjectId("action:test-provisioning"), actor,
                ActorContainerItemOrder.Direction.TAKE, new ActorContainerItemOrder.Portion.Fungible(source,
                new ResourceCustody.Container(depot), account, new ResourceCustody.Actor(actor), Optional.empty(),
                FoodCatalog.BREAD, Map.of(lot, 8)), new ActorContainerItemOrder.ContainerEndpoint.FungibleContainer(depot),
                state.actorLocations().get(actor).supportingSurface(), new ActorItemSlot.Pocket(3), 0, 1);
        ledger = ledger.transferActorOrderCold(order);
        state = state.withChanges(FrontierWorldStateUpdate.begin().inventory(state.inventory().withFungibleResources(ledger))
                .humanPopulation(state.humanPopulation().accrueHunger(actor, 27_000)));
        return new Fixture(state, actor, account, lot);
    }

    @Test void coldResidentEatsOnlyAPortionOfOwnSuppliesWithoutDepotRouteOrBodyChange() {
        var fixture = carriedBread();
        var state = fixture.state();
        var position = state.actorLocations().get(fixture.actor()).body();
        var started = ResidentMealProcess.selectSourceAtYield(state, fixture.actor(), 27_000).orElseThrow();
        var meal = started.meal();
        assertInstanceOf(ResidentFoodSource.Personal.class, meal.source());
        assertEquals(new ActorItemSlot.Pocket(3), meal.inventorySlot());
        assertEquals(ResidentMeal.Phase.CONSUME, meal.phase());
        assertTrue(meal.portion().quantity() < 8);
        state = ResidentMealProcess.reduceStarted(state, fixture.actor(), started);
        state = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state));
        var step = ResidentMealProcess.planColdStep(state, fixture.actor(), 27_001).orElseThrow();
        assertTrue(step.plannedRoute().isEmpty());
        state = ResidentMealProcess.reduceColdStep(state, fixture.actor(), step);
        assertEquals(position, state.actorLocations().get(fixture.actor()).body());
        assertEquals(8 - meal.portion().quantity(), state.inventory().fungibleResources().accounts()
                .get(fixture.account()).lotQuantities().get(fixture.lot()));
        assertFalse(state.humanPopulation().meals().containsKey(fixture.actor()));
        assertTrue(state.inventory().fungibleResources().claims().isEmpty());
        assertEquals(Optional.of(new ActorItemSlot.Pocket(3)), state.inventory().fungibleResources().accounts()
                .get(fixture.account()).actorPresentation());
        assertFalse(state.humanPopulation().nutrition(fixture.actor()).wantsFood(state.bootstrap().ruleset().residentLife()));
        assertEquals(state, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state)));
    }

    @Test void reservedGoodsInPersonalCustodyAreNotAnEdibleTravelReserve() {
        var fixture = carriedBread();
        var state = fixture.state();
        var resident = state.humanPopulation().resident(fixture.actor());
        var claim = new ClaimAllocation(new SubjectId("claim:contract-food"), new SubjectId("contract:food"),
                resident.settlementId(), FoodCatalog.BREAD, 8, Map.of(fixture.lot(), 8), ClaimPurpose.GOODS_TRADE);
        var ledger = state.inventory().fungibleResources().reserve(claim, fixture.account());
        state = state.withInventory(state.inventory().withFungibleResources(ledger));
        assertTrue(UnitInventory.select(ledger, fixture.actor(), resident.settlementId(), FoodCatalog.BREAD, 1).isEmpty());
        assertTrue(ResidentMealOpportunity.candidate(state, fixture.actor(), 27_000).isEmpty());
    }
}
