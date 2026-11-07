package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.geometry.BlockInteractionReach;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.process.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** The actual Northwatch stacked pantry: stock admission succeeds but its old preferred contact is a barrel top. */
class UpperStorageEquipmentTest {
    private static FrontierWorldState donated() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:upper-pantry"), 139,
                FrontierRulesets.installed("frontier-v3-clinical-care-r35")));
        var home = state.bootstrap().settlements().getFirst();
        var change = SettlementStaffingComposition.change(state, home.id(), 1);
        if (change.isPresent()) state = SettlementStaffingComposition.reduce(state, home.id(), change.orElseThrow());
        var container = upper(state); var inventory = state.inventory(); var items = new HashMap<>(inventory.items());
        for (int slot = 0; slot < 2; slot++) {
            var item = new ExactItemStack(new SubjectId("item:upper-donation-" + slot), home.id(), "minecraft:iron_sword", 1,
                    new InventoryCustody.ContainerSlot(container, slot), new ItemCondition(250, 0));
            items.put(item.id(), item);
        }
        // Recover the exact historical endpoint stored by the affected live world.
        // Fresh authored declarations now exclude container tops as preferred contacts.
        var kitchen = home.structures().stream().filter(building -> building.kind() == StructureKind.KITCHEN).findFirst().orElseThrow();
        var historical = new SurfaceAnchor(kitchen.placement().world(new BlockPosition(14, 2, 18)));
        var record = inventory.containers().get(container); var policy = record.storage().orElseThrow();
        var records = ImmutableLookupMap.withEntry(inventory.containers(), container, record.withStorage(new ResourceStoragePolicy(
                policy.endpoint().withStation(historical), policy.filter(), policy.priority(), policy.role())));
        return state.withInventory(new ExactInventory(records, items, inventory.cargo(), inventory.playerItems(),
                inventory.worldCarrierItems(), inventory.conflicts(), inventory.surfaces(), inventory.economics(), inventory.fungibleResources()));
    }
    private static SubjectId upper(FrontierWorldState state) {
        var kitchen = state.bootstrap().settlements().getFirst().structures().stream()
                .filter(building -> building.kind() == StructureKind.KITCHEN).findFirst().orElseThrow();
        var position = kitchen.placement().world(new BlockPosition(15, 3, 18));
        return state.inventory().surfaces().values().stream().filter(surface -> surface.fixed() && surface.position().equals(position))
                .map(ContainerSurface::containerId).findFirst().orElseThrow();
    }
    @Test void upperPantryWeaponsAppointGuardsUsingReachableFloorContactsAndKeepThoseContactsAcrossRecovery() {
        var state = donated(); var home = state.bootstrap().settlements().getFirst().id(); var container = upper(state);
        var declared = state.inventory().containers().get(container).storage().orElseThrow().endpoint();
        var profile = state.actorLocations().get(new SubjectId("resident:1-16")).navigationProfile();
        assertTrue(ResourceStorageNavigation.forEndpoint(state, declared).forProfile(profile).geometry().supportExcluded(declared.station()),
                "the production fixture must retain the original container-top contact");
        var before = state.inventory();
        for (var event : GuardWorkProcess.review(state, GuardContinuation.review(home, 2000), 2000))
            if (event.payload() instanceof GuardDutyEvent appointed) state = GuardWorkProcess.reduce(state, event.subject(), appointed, 2000);
        var duties = GuardEquipment.in(state, home);
        assertEquals(2, duties.size());
        assertSame(before, state.inventory(), "appointment selects stock; it cannot issue or manufacture it");
        for (var duty : duties) {
            assertEquals(container, duty.source().containerId());
            assertNotEquals(declared.station(), duty.source().station());
            assertTrue(BlockInteractionReach.VANILLA_ADULT_PEDESTRIAN.contains(duty.source().station(), state.inventory().surfaces().get(container).position()));
            assertFalse(state.inventory().surfaces().values().stream().anyMatch(surface -> surface.fixed() && surface.position().equals(duty.source().station().support())));
            assertTrue(GuardAuthority.sourceAvailable(state, duty));
            var actor = state.actorLocations().get(duty.execution().actorId());
            assertEquals(duty.source().station(), ResourceStorageNavigation.plannedPath(state, actor.supportingSurface(), actor.navigationProfile(), duty.source()).getLast());
            assertEquals(duty.source().station(), GuardAuthority.order(state, duty).station());
        }
        var codec = new FrontierWorldStateCodec(); var recovered = codec.decode(codec.encode(state));
        assertEquals(state, recovered);
        for (var duty : duties) assertTrue(GuardAuthority.sourceAvailable(recovered, duty));
        var exact = recovered; var duty = duties.getFirst();
        assertThrows(IllegalArgumentException.class, () -> ResourceStorageAccess.at(exact,
                duty.source().withStation(SurfaceAnchor.at(duty.source().station().x() + 20, duty.source().station().y(), duty.source().station().z()))));
        var blocked = state.withInventory(state.inventory().withSurfaceStatus(container, ContainerSurfaceStatus.CONFLICT));
        assertFalse(WorkEquipmentSelection.hasAvailable(blocked, home, GuardEquipment.REQUIREMENTS, Set.of()));
    }
    @Test void foodInTheRetainedUpperBarrelReachesNutritionWithoutMovingTheContainerOrItsContentsToAnotherStore() {
        var state=donated();var home=state.bootstrap().settlements().getFirst();var target=upper(state);
        var original=CommunalDiningPlan.initialFoodStorage(state.bootstrap(),home.id());
        var source=FungibleResourceCustodySupport.accountAtContainer(state,original).orElseThrow();
        var ledger=state.inventory().fungibleResources();var destination=ReferenceContainerCustody.scopeId(target);
        var moved=ledger.transferToNewAccount(source.id(),new CustodyAccount(destination,new ResourceCustody.Container(target),source.lotQuantities(),Map.of()));
        state=state.withInventory(state.inventory().withFungibleResources(moved));
        var access=ResourceStorageAccess.at(state,target);var actor=new SubjectId("resident:1-14");
        assertTrue(ResourceStorageNavigation.forEndpoint(state,access.endpoint()).forActor(state,actor).clearStation(access.interactionStation(state)));
        var point=ServiceBoundaryComposition.geometry(state,ServiceBoundaryComposition.declaration(state,target).identity());
        assertEquals(access.interactionStation(state),point.station());
        state=state.withActorBody(actor,point.waitingSurfaces().getFirst().standingBody());
        state=state.withHumanPopulation(state.humanPopulation().accrueHunger(actor,27000,state.bootstrap().ruleset().residentLife()));
        var start=ResidentMealProcess.selectSourceAtYield(state,actor,27000).orElseThrow();
        assertEquals(target,start.meal().depotId());
        state=ResidentMealProcess.reduceStarted(state,actor,start);
        var completed=ConcurrentMealAccessTest.finishMeals(state,List.of(actor));
        assertFalse(completed.humanPopulation().meals().containsKey(actor));
        assertEquals(ResidentNutritionStatus.NOURISHED,completed.humanPopulation().nutrition(actor).status());
        assertEquals(state.inventory().surfaces().get(target),completed.inventory().surfaces().get(target));
        assertEquals(2,completed.inventory().items().values().stream().filter(item->item.custody() instanceof InventoryCustody.ContainerSlot slot&&slot.containerId().equals(target)).count());
    }

}
