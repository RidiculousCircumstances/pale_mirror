package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ResourceStorageTest {
    private FrontierWorldState initial() {
        return FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:resource-storage"),
                20260918065L, FrontierRulesets.installed("frontier-v3-quarry-graybox-r6")));
    }
    @Test void filtersUseExplicitCategoryMembershipRatherThanPrefixesAndCanForbidEverything() {
        var filter = new ResourceStorageFilter(false, Set.of("minecraft:bread"),
                Map.of("pale_mirror:stone", Set.of("minecraft:cobblestone", "minecraft:andesite")));
        assertTrue(filter.accepts("minecraft:bread")); assertTrue(filter.accepts("minecraft:andesite"));
        assertFalse(filter.accepts("minecraft:stone")); assertFalse(filter.accepts("minecraft:wheat"));
        assertFalse(new ResourceStorageFilter(false, Set.of(), Map.of()).accepts("minecraft:bread"));
        assertThrows(IllegalArgumentException.class, () -> new ResourceStorageFilter(true, Set.of("minecraft:bread"), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new ResourceStorageFilter(false, Set.of(), Map.of("stone", Set.of("minecraft:stone"))));
    }
    @Test void declaredStorageAndFiltersRoundTripWithoutAddingStockOrInferringFacilities() {
        var state = initial(); var codec = new FrontierWorldStateCodec();
        var restored = codec.decode(codec.encode(state)); assertEquals(state, restored);
        for (var container : restored.inventory().containers().values()) {
            if (container.storage().isEmpty()) continue;
            var policy = container.storage().orElseThrow(); ShipmentEndpointComposition.validate(restored, policy.endpoint());
            assertEquals(container.id(), policy.endpoint().containerId()); assertEquals(container.ownerId(), policy.endpoint().settlementId());
        }
        assertTrue(restored.inventory().containers().values().stream().filter(c -> c.productionStation().isPresent()).allMatch(c -> c.storage().isEmpty()));
        var site = restored.extractionSites().deposits().values().iterator().next().site();
        var storage = restored.inventory().containers().get(QuarryStorageFixture.container(state, site)).storage().orElseThrow();
        assertFalse(storage.filter().accepts("minecraft:bread"));
        assertFalse(ContainerStorageAdmission.receive(restored, QuarryStorageFixture.container(state, site), "minecraft:bread", 1, Optional.empty()));
    }
    @Test void discoveryRespectsOwnershipTierFilterAndAccessibilityRatherThanTheSitesOriginalChest() {
        var state = initial(); var site = state.extractionSites().deposits().values().iterator().next().site();
        var destination = ResourceStorageSelection.destination(state, site.settlementId(), "minecraft:cobblestone", 1,
                site.layout().storagePort(), Set.of(ResourceStorageRole.WAREHOUSE), Optional.empty(), endpoint -> true).orElseThrow();
        assertNotEquals(QuarryStorageFixture.container(state, site), destination.containerId());
        assertEquals(site.settlementId(), destination.settlementId());
        assertTrue(ResourceStorageSelection.destination(state, site.settlementId(), "minecraft:cobblestone", 1,
                site.layout().storagePort(), Set.of(ResourceStorageRole.WAREHOUSE), Optional.empty(), endpoint -> false).isEmpty());
        assertTrue(ResourceStorageSelection.destination(state, new SubjectId("settlement:unowned"), "minecraft:cobblestone", 1,
                site.layout().storagePort(), Set.of(ResourceStorageRole.WAREHOUSE), Optional.empty(), endpoint -> true).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> ResourceStorageSelection.destination(state, site.settlementId(), "minecraft:cobblestone", 0,
                site.layout().storagePort(), Set.of(ResourceStorageRole.WAREHOUSE), Optional.empty(), endpoint -> true));
    }
    @Test void ordinaryStorageRequiresCompleteMatchingIdentityAndAccessPolicy() {
        var id = new SubjectId("container:extra"); var home = new SubjectId("settlement:1");
        var endpoint = new ShipmentEndpoint.Storage(home, id, SurfaceAnchor.at(1, 64, 1), Optional.empty(), SurfaceAnchor.at(1, 64, 1));
        var policy = new ResourceStoragePolicy(endpoint, ResourceStorageFilter.all(), 5, ResourceStorageRole.WAREHOUSE);
        assertDoesNotThrow(() -> new ContainerRecord(id, home, 27, ContainerPurpose.RESOURCE_STORAGE, Optional.empty(), Optional.of(policy)));
        assertThrows(IllegalArgumentException.class, () -> new ContainerRecord(id, home, 27, ContainerPurpose.RESOURCE_STORAGE));
        assertThrows(IllegalArgumentException.class, () -> new ContainerRecord(new SubjectId("container:other"), home, 27,
                ContainerPurpose.RESOURCE_STORAGE, Optional.empty(), Optional.of(policy)));
        assertThrows(IllegalArgumentException.class, () -> ResourceStorageRole.decode(99));
        assertThrows(IllegalArgumentException.class, () -> ServicePointId.Kind.fromWireTag(99));
    }
    @Test void physicallyConflictedStorageCannotSupplyOrReceiveStockWithoutAReplicaConflict() {
        var state = initial(); var site = state.extractionSites().deposits().values().iterator().next().site();
        var destination = ResourceStorageSelection.destination(state, site.settlementId(), "minecraft:cobblestone", 1,
                site.layout().storagePort(), Set.of(ResourceStorageRole.WAREHOUSE), Optional.empty(), endpoint -> true).orElseThrow();
        var conflicted = state.withInventory(state.inventory().withSurfaceStatus(destination.containerId(), ContainerSurfaceStatus.CONFLICT));
        assertTrue(ReferenceContainerCustody.blocksCanonicalUse(conflicted, destination.containerId()));
        assertFalse(ContainerStorageAdmission.receive(conflicted, destination.containerId(), "minecraft:cobblestone", 1, Optional.empty()));
        assertTrue(ResourceStorageSelection.destination(conflicted, site.settlementId(), "minecraft:cobblestone", 1,
                site.layout().storagePort(), Set.of(ResourceStorageRole.WAREHOUSE), Optional.empty(), endpoint -> true).isEmpty());
    }
    @Test void storageRouteAdmissionUsesKnownPhysicalObstaclesWithoutAcquiringAnExecution() {
        var state = initial(); var site = state.extractionSites().deposits().values().iterator().next().site();
        var target = ResourceStorageSelection.candidates(state, site.settlementId(), "minecraft:cobblestone",
                site.layout().storagePort(), Set.of(ResourceStorageRole.WAREHOUSE)).getFirst();
        assertEquals(List.of(target.station()), ResourceStorageNavigation.plannedPath(state, target.station(), target));
        var blocked = state.recordPhysicalDelta(new PhysicalDelta(target.station().support().offset(0, 1, 0),
                PhysicalDeltaKind.UNKNOWN_SCAR, Optional.empty(), Optional.empty(), "test:blocked-storage-access"));
        var refusal = assertThrows(io.farfrontier.palemirror.frontier.v3.model.navigation.KnownPedestrianNavigation.RouteUnavailable.class,
                () -> ResourceStorageNavigation.plannedPath(blocked, target.station(), target));
        assertEquals(io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteResult.Status.UNKNOWN_GEOMETRY, refusal.status(),
                "an unclassified physical change requires observation; it is not a proved impassable block");
        assertEquals(state.actorExecutions(), blocked.actorExecutions());
        assertEquals(state.inventory().fungibleResources(), blocked.inventory().fungibleResources());
    }
    @Test void publicPickupGeometryRetainsBuildingExitWithoutGrantingPrivateStations() {
        var state = initial(); var home = state.bootstrap().settlements().getFirst();
        var depot = home.structures().stream().filter(f -> f.kind() == StructureKind.DEPOT).findFirst().orElseThrow();
        var access = SettlementDepotServicePort.forDepot(depot);
        var geometry = KnownPedestrianRouteKnowledge.forPublicJourney(state, home.id(), List.of());
        assertTrue(geometry.reachable(access.serviceSurface(), access.exteriorApproach()));
        var blocked = state.recordPhysicalDelta(new PhysicalDelta(access.exteriorApproach().support().offset(0, 1, 0),
                PhysicalDeltaKind.UNKNOWN_SCAR, Optional.empty(), Optional.empty(), "test:blocked-public-exit"));
        assertFalse(KnownPedestrianRouteKnowledge.forPublicJourney(blocked, home.id(), List.of())
                .reachable(access.serviceSurface(), access.exteriorApproach()));
    }
    @Test void declarationAndPolicyChangesPreserveStockRejectStaleOwnershipAndRoundTripEvents() {
        var state = initial(); var site = state.extractionSites().deposits().values().iterator().next().site();
        var station = site.layout().storagePort(); var id = new SubjectId("container:storage-test");
        var endpoint = new ShipmentEndpoint.Storage(site.settlementId(), id, station, Optional.empty(), station);
        var policy = new ResourceStoragePolicy(endpoint, ResourceStorageFilter.all(), 5, ResourceStorageRole.WAREHOUSE);
        var declaration = new ResourceStorageDeclared(new ContainerRecord(id, site.settlementId(), 27, ContainerPurpose.RESOURCE_STORAGE,
                Optional.empty(), Optional.of(policy)), station.support().offset(1, 1, 0));
        var codecs = io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldPayloadCodecs.create();
        assertEquals(declaration, codecs.decode(declaration.type(), codecs.encode(declaration)));
        var declared = declaration.apply(state, site.settlementId());
        assertEquals(state.inventory().fungibleResources(), declared.inventory().fungibleResources());
        assertTrue(FrontierContainerSocketPlan.declaredSupport(declared, declared.inventory().surfaces().get(id)).orElseThrow()
                instanceof ContainerSocketSupport.Terrain);
        assertThrows(IllegalArgumentException.class, () -> declaration.apply(declared, site.settlementId()));
        assertThrows(IllegalArgumentException.class, () -> declaration.apply(state, new SubjectId("settlement:foreign")));
        var replacement = new ResourceStoragePolicy(endpoint, new ResourceStorageFilter(false, Set.of("minecraft:cobblestone"), Map.of()),
                10, ResourceStorageRole.WAREHOUSE);
        var change = new ResourceStoragePolicyChanged(id, policy, replacement);
        assertEquals(change, codecs.decode(change.type(), codecs.encode(change)));
        var changed = change.apply(declared, site.settlementId());
        var accepted = new FrontierEvent(1, new EventId("event:storage-policy"), new TransactionId("transaction:storage-policy"),
                changed.bootstrap().worldId(), new Revision(1), new SimInstant(0), site.settlementId(),
                CauseChain.root(new CommandId("command:storage-policy")), change);
        var wake = io.farfrontier.palemirror.frontier.v3.process.FrontierWorldProcessCatalog.wakeKeys(declared, changed, accepted);
        assertTrue(wake.contains(id), "changed eligibility must wake requests waiting on this storage");
        assertTrue(wake.contains(site.settlementId()), "new storage policy must wake its settlement's work selection");
        assertEquals(state.inventory().fungibleResources(), changed.inventory().fungibleResources());
        assertEquals(changed, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(changed)));
        assertThrows(IllegalArgumentException.class, () -> change.apply(changed, site.settlementId()));
    }
}
