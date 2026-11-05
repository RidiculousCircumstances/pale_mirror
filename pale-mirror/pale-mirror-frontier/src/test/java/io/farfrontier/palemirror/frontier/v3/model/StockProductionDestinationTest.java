package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.*;
import io.farfrontier.palemirror.frontier.v3.model.geometry.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class StockProductionDestinationTest {
    private static final class World {
        static final FrontierWorldState STATE = FrontierWorldState.initial(FrontierBootstrapper.create(
                new WorldId("frontier:stock-output-access"), 139, FrontierRulesets.installed("frontier-v3-textiles-terrain-r32")));
    }
    @Test void inaccessiblePreferredStorageDoesNotTrapTheNextProductionOrder() {
        var state = World.STATE; var home = state.bootstrap().settlements().getFirst();
        var factory = home.structures().stream().filter(f -> f.kind() == StructureKind.TEXTILE_WORKSHOP).findFirst().orElseThrow();
        var station = ProductionFacilityLayout.stations(factory).getFirst();
        var origin = SettlementWorkshopServicePort.forWorkshop(factory).exteriorApproach();
        var blocked = station.socketSurface(); var id = new SubjectId("container:blocked-output");
        var endpoint = new ShipmentEndpoint.Storage(home.id(), id, blocked, Optional.empty(), blocked);
        var policy = new ResourceStoragePolicy(endpoint, ResourceStorageFilter.all(), 100, ResourceStorageRole.WAREHOUSE);
        var declared = new ResourceStorageDeclared(ContainerRecord.storage(id, home.id(), 27, StorageBlockKind.CHEST, policy),
                blocked.support().offset(1, 1, 0)).apply(state, home.id());
        declared = new NavigationGeometryObserved(declared.navigationGeometry().revision(),
                Map.of(blocked.support().offset(0, 1, 0), NavigationObservation.known(NavigationCell.SOLID)))
                .apply(declared, NavigationGeometryObserved.OWNER);
        var recipe = declared.bootstrap().ruleset().production().require(new ProductionRecipe.Id("pale_mirror:woven_cloth"));
        var rights = ProductionRights.publicService(home.id()); var source = FrontierWorldState.depotId(home.id());
        assertEquals(id, ProductionOutputCapacity.selectRecipeDestination(declared, rights, recipe, 4, source, origin, Optional.empty())
                .orElseThrow().destinationContainerId(), "capacity assessment does not perform route discovery");
        var selected = ProductionOutputCapacity.selectAccessibleRecipeDestination(declared, rights, recipe, 4, source,
                origin, Optional.empty(), UnitNavigationProfile.VILLAGER).orElseThrow();
        assertNotEquals(id, selected.destinationContainerId(), "proven impassable first choice must yield to another eligible store");
        assertEquals(state.actorExecutions(), declared.actorExecutions());
        assertEquals(state.inventory().fungibleResources(), declared.inventory().fungibleResources());
        var unknown = declared.recordPhysicalDelta(new PhysicalDelta(blocked.support().offset(0, 1, 0),
                PhysicalDeltaKind.UNKNOWN_SCAR, Optional.empty(), Optional.empty(), "test:output-access-unknown"));
        var refusal = assertThrows(KnownPedestrianNavigation.RouteUnavailable.class, () ->
                ProductionOutputCapacity.selectAccessibleRecipeDestination(unknown, rights, recipe, 4, source,
                        origin, Optional.empty(), UnitNavigationProfile.VILLAGER));
        assertEquals(PedestrianRouteResult.Status.UNKNOWN_GEOMETRY, refusal.status(), "unknown access must remain an observation wait");
    }
}
