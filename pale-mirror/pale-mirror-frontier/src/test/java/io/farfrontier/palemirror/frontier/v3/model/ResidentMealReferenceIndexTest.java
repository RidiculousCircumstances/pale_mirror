package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.process.ResidentMealProcess;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ResidentMealReferenceIndexTest {
    @Test void warmedMealStillRejectsLostAllocationBodyAndEndpointAndRecoversAfterFailure() {
        var state = meal(); var meal = state.humanPopulation().meals().values().iterator().next();
        ResidentMealReferenceClosure.validateTransition(state, state);
        var resources = state.inventory().fungibleResources().releaseClaim(meal.sourceAccountId(), meal.claimId());
        var missingClaim = FrontierWorldState.duringUnpublishedTransition(() -> state.withInventory(state.inventory().withFungibleResources(resources)));
        assertThrows(IllegalArgumentException.class, () -> ResidentMealReferenceClosure.validateTransition(state, missingClaim));
        ResidentMealReferenceClosure.validateTransition(state, state);
        var body = state.actorLocations().get(meal.residentId());
        var dead = FrontierWorldState.duringUnpublishedTransition(() -> state.withChanges(FrontierWorldStateUpdate.begin().actorLocations(
                ImmutableLookupMap.withEntry(state.actorLocations(), meal.residentId(), body.deadAt(body.body())))));
        assertThrows(IllegalArgumentException.class, () -> ResidentMealReferenceClosure.validateTransition(state, dead));
        ResidentMealReferenceClosure.validateTransition(state, state);
        var inventory = state.inventory(); var surface = inventory.surfaces().get(meal.depotId());
        var changedSurfaces = ImmutableLookupMap.withEntry(inventory.surfaces(), surface.containerId(),
                new ContainerSurface(surface.containerId(), surface.position().offset(20, 0, 0), surface.status()));
        var wrongEndpoint = FrontierWorldState.duringUnpublishedTransition(() -> state.withInventory(new ExactInventory(
                inventory.containers(), inventory.items(), inventory.cargo(), inventory.playerItems(), inventory.worldCarrierItems(),
                inventory.conflicts(), changedSurfaces, inventory.economics(), inventory.fungibleResources())));
        assertThrows(IllegalArgumentException.class, () -> ResidentMealReferenceClosure.validateTransition(state, wrongEndpoint));
        ImmutableInvariantCheck.full(() -> { ResidentMealReferenceClosure.validateTransition(state, state); return null; });
        ResidentMealReferenceClosure.validate(state);
    }
    private static FrontierWorldState meal() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:meal-read-index"), 421L));
        var home = state.bootstrap().settlements().getFirst(); var resident = home.residents().getFirst().id();
        var container = new SubjectId("container:meal-read-index"); var account = ReferenceContainerCustody.scopeId(container);
        var station = SettlementServiceAccessPoints.depotPort(state, home.id()).exteriorApproach();
        var endpoint = new ShipmentEndpoint.Storage(home.id(), container, station, Optional.empty(), station);
        var policy = new ResourceStoragePolicy(endpoint, ResourceStorageFilter.all(), 100, ResourceStorageRole.WAREHOUSE);
        state = new ResourceStorageDeclared(new ContainerRecord(container, home.id(), 27, ContainerPurpose.RESOURCE_STORAGE,
                Optional.empty(), Optional.of(policy)), station.support().offset(1, 1, 0)).apply(state, home.id());
        var lot = new ResourceLot(new SubjectId("lot:meal-read-index"), home.id(), "minecraft:bread", 64, "test:meal", List.of());
        var resources = state.inventory().fungibleResources().issue(lot,
                new CustodyAccount(account, new ResourceCustody.Container(container), Map.of(lot.id(), 64), Map.of()));
        state = state.withChanges(FrontierWorldStateUpdate.begin().inventory(state.inventory().withFungibleResources(resources))
                .humanPopulation(state.humanPopulation().accrueHunger(resident, 27_000L)));
        var start = ResidentMealProcess.selectSourceAtYield(state, resident, 27_000L).orElseThrow();
        return ResidentMealProcess.reduceStarted(state, resident, start);
    }
}
