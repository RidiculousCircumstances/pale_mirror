package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.persistence.*;
import io.farfrontier.palemirror.frontier.v3.process.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ProductionStationEnergyTest {
    @Test void stationGeometryReusesOnlyUnchangedTerrainEndpointAndPopulation() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:station-geometry-reuse"), 41,
                FrontierRulesets.fuelledQuarryGraybox()));
        var station = state.inventory().containers().values().stream().flatMap(record -> record.productionStation().stream()).findFirst().orElseThrow();
        var provider = new ProductionServiceBoundaries();
        var declaration = ServiceBoundaryComposition.declaration(state, station.containerId());
        var original = provider.geometry(state, declaration);
        assertSame(original, provider.geometry(state, declaration));
        var stocked = fuel(state, station);
        assertSame(original, provider.geometry(stocked, declaration), "stock does not rebuild immutable access terrain");
        var changed = state.recordPhysicalDelta(new PhysicalDelta(station.workerStation().support().offset(0, 1, 0),
                PhysicalDeltaKind.UNKNOWN_SCAR, Optional.empty(), Optional.empty(), "test:station-geometry-edit"));
        assertNotSame(original, provider.geometry(changed, declaration), "physical edits invalidate access geometry");
    }
    private static final SubjectId FUEL = new SubjectId("lot:station-test-fuel");
    private static FrontierWorldState fuel(FrontierWorldState state, ProductionStationSpec station) {
        var owner = state.inventory().containers().get(station.containerId()).ownerId();
        var holder = station.custody(ActorContainerItemOrder.StationPort.FUEL);
        var account = ContainerResourceAccounts.id(holder);
        var ledger = state.inventory().fungibleResources().issue(new ResourceLot(FUEL, owner, "minecraft:coal", 4,
                "fixture:finite-fuel", List.of()), new CustodyAccount(account, holder, Map.of(FUEL, 4), Map.of()));
        return state.withInventory(state.inventory().withFungibleResources(ledger));
    }
}
