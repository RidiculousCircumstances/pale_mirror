package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FrontierWorldStateUpdateTest {
    @Test void unchangedStorageProofDoesNotAcceptAChangedOutOfBoundsSurface() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:storage-bounds"), 91L));
        var inventory = state.inventory();
        var original = inventory.surfaces().values().stream().filter(ContainerSurface::fixed).findFirst().orElseThrow();
        var id = original.containerId();
        var outside = new ContainerSurface(id, new BlockPosition(state.bootstrap().bounds().maxXExclusive(),
                original.position().y(), original.position().z()), original.status());
        var changed = new ExactInventory(inventory.containers(), inventory.items(), inventory.cargo(), inventory.playerItems(),
                inventory.worldCarrierItems(), inventory.conflicts(), ImmutableLookupMap.withEntry(inventory.surfaces(), id, outside),
                inventory.economics(), inventory.fungibleResources(), inventory.occupiedSlots());
        assertThrows(IllegalArgumentException.class, () -> state.withInventory(changed));
        assertThrows(IllegalArgumentException.class, () -> ImmutableInvariantCheck.full(() -> state.withInventory(changed)));
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> state.withInventory(inventory),
                "a rejected fork cannot poison the valid predecessor");
        var codec = new io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec();
        org.junit.jupiter.api.Assertions.assertEquals(state, codec.decode(codec.encode(state)));
    }
    @Test void ownedContributionsComposeAtomicallyAndRejectCompetingWritersBeforeAddingAnything() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:update-composition"), 91L));
        var first = FrontierWorldStateUpdate.begin().inventory(state.inventory());
        var second = FrontierWorldStateUpdate.begin().humanPopulation(state.humanPopulation());
        first.merge(second);
        org.junit.jupiter.api.Assertions.assertEquals(java.util.Set.of(FrontierWorldStateUpdate.Component.INVENTORY,
                FrontierWorldStateUpdate.Component.HUMAN_POPULATION), first.changedComponents());
        org.junit.jupiter.api.Assertions.assertEquals(java.util.Set.of(FrontierWorldStateUpdate.Component.HUMAN_POPULATION),
                second.changedComponents(), "composition does not consume or mutate the contributing patch");
        var changed = state.withChanges(first);
        assertSame(state.inventory(), changed.inventory());
        assertSame(state.humanPopulation(), changed.humanPopulation());
        var before = first.changedComponents();
        assertThrows(IllegalArgumentException.class, () -> first.merge(FrontierWorldStateUpdate.begin()
                .actorLocations(state.actorLocations()).inventory(state.inventory())));
        org.junit.jupiter.api.Assertions.assertEquals(before, first.changedComponents(),
                "a duplicate writer must fail before an unrelated component is partially copied");
    }
    @Test
    void namedChangeReplacesOnlyItsDeclaredComponent() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:update"), 91L));
        FrontierWorldState changed = state.withChanges(FrontierWorldStateUpdate.begin()
                .actorLocations(new LinkedHashMap<>(state.actorLocations())));

        assertNotSame(state.actorLocations(), changed.actorLocations());
        assertSame(state.bootstrap(), changed.bootstrap());
        assertSame(state.structureConditions(), changed.structureConditions());
        assertSame(state.infection(), changed.infection());
        assertSame(state.inventory(), changed.inventory());
        assertSame(state.productionJobs(), changed.productionJobs());
        assertSame(state.physicalIntents(), changed.physicalIntents());
        assertSame(state.physicalObservations(), changed.physicalObservations());
        assertSame(state.sceneLeases(), changed.sceneLeases());
        assertSame(state.hiveColony(), changed.hiveColony());
        assertSame(state.structureDamage(), changed.structureDamage());
        assertSame(state.physicalDeltas(), changed.physicalDeltas());
        assertSame(state.ambientLeases(), changed.ambientLeases());
        assertSame(state.routeConstructions(), changed.routeConstructions());
        assertSame(state.routeMaintenances(), changed.routeMaintenances());
        assertSame(state.routeTopology(), changed.routeTopology());
        assertSame(state.strategicPlans(), changed.strategicPlans());
        assertSame(state.humanPopulation(), changed.humanPopulation());
        assertSame(state.companies(), changed.companies());
        assertSame(state.resourceSites(), changed.resourceSites());
    }

    @Test
    void namedChangeRejectsDuplicateDeclarationsAndRetainsDeclaredNoOpIdentity() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:update-negative"), 91L));
        FrontierWorldStateUpdate duplicate = FrontierWorldStateUpdate.begin().inventory(state.inventory());
        assertThrows(IllegalStateException.class, () -> duplicate.inventory(state.inventory()));
        assertSame(state, state.withChanges(FrontierWorldStateUpdate.begin().inventory(state.inventory())));
        assertSame(state, state.withStrategicPlans(state.strategicPlans()));
        assertSame(state, state.withChanges(FrontierWorldStateUpdate.begin()));
        assertNotSame(state, ImmutableInvariantCheck.full(() -> state.withChanges(FrontierWorldStateUpdate.begin())),
                "full ingress must still validate even an unchanged aggregate");
    }
}
