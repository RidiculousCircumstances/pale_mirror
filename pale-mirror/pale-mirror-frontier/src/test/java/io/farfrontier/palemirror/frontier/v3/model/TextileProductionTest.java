package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TextileProductionTest {
    private static final FrontierRuleset RULES = FrontierRulesets.installed("frontier-v3-textiles-terrain-r32");
    private static final class World {
        static final FrontierWorldState STATE = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:textile-crop-mounts"), 139, RULES));
    }
    @Test void distinctCropsAndIndependentMachineMountsSurviveRecovery() {
        var state = World.STATE; var home = state.bootstrap().settlements().getFirst();
        var crops = state.resourceSiteDescriptors().values().stream().filter(site -> site.settlementId().equals(home.id())).toList();
        assertEquals(Set.of(ResourceSiteKind.WHEAT_FIELD, ResourceSiteKind.COTTON_FIELD), crops.stream().map(ResourceSite::kind).collect(java.util.stream.Collectors.toSet()));
        var cotton = crops.stream().filter(site -> site.kind() == ResourceSiteKind.COTTON_FIELD).findFirst().orElseThrow();
        var wheat = crops.stream().filter(site -> site.kind() == ResourceSiteKind.WHEAT_FIELD).findFirst().orElseThrow();
        assertTrue(Collections.disjoint(cotton.managedSlots(), wheat.managedSlots()));
        var extraction = FieldHarvestExtraction.definition(state.bootstrap(), cotton, cotton.layout().cells().getFirst().crop());
        assertEquals("pale_mirror:cotton", extraction.coldOutput().getFirst().itemKind());
        assertEquals("pale_mirror:cotton_crop", extraction.before().kind());
        var factory = home.structures().stream().filter(b -> b.kind() == StructureKind.TEXTILE_WORKSHOP).findFirst().orElseThrow();
        var stations = ProductionFacilityLayout.stations(factory);
        assertEquals(2, stations.size());
        assertEquals(Set.of(ProductionStationSpec.Capability.SPINNING, ProductionStationSpec.Capability.WEAVING), stations.stream().map(ProductionStationSpec::capability).collect(java.util.stream.Collectors.toSet()));
        assertEquals(2, stations.stream().map(ProductionStationSpec::workplaceId).distinct().count());
        assertEquals(2, stations.stream().map(ProductionStationSpec::containerId).distinct().count());
        assertThrows(IllegalArgumentException.class, () -> ProductionFacilityLayout.station(factory));
        assertThrows(IllegalArgumentException.class, () -> ProductionFacilityLayout.station(factory, new ProductionStationSpec.WorkplaceId("missing")));
        var configuration = io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition.configuration(state.bootstrap());
        for (var station : stations) assertTrue(configuration.initialSchedules().stream().anyMatch(action -> action.subject().equals(station.id())),
                "every declared machine must enter the ordinary station scheduler");
        var recovered = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state));
        assertEquals(cotton, recovered.resourceSite(cotton.id()));
        for (var station : stations) {
            assertEquals(station, ProductionStationDirectory.require(recovered.inventory(), station.id()).productionStation().orElseThrow());
            assertTrue(station.fuelSlot().isEmpty());
            StationWorkAuthorization.require(ResidentWorkKind.PRODUCTION, station.capability());
        }
    }
    @Test void materialBackedStaffingDoesNotDependOnThePlanItHelpsRebuildOrGrantWork() {
        var state = World.STATE; var home = state.bootstrap().settlements().getFirst().id();
        var container = FrontierWorldState.depotId(home); var account = ReferenceContainerCustody.scopeId(container);
        var lot = new SubjectId("lot:textile-staffing-input");
        var resources = state.inventory().fungibleResources().issue(new ResourceLot(lot, home, "pale_mirror:cotton", 4,
                "fixture:textile-staffing", List.of()), new CustodyAccount(new SubjectId("custody:textile-staffing-input"), new ResourceCustody.Container(container), Map.of(lot, 4), Map.of()))
                .transfer(new SubjectId("custody:textile-staffing-input"), account, Map.of(lot, 4), Map.of());
        var supplied = state.withInventory(state.inventory().withFungibleResources(resources));
        assertTrue(supplied.companies().goodsTrade().participants().economicState(home).accepted().isEmpty());
        var observed = io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRoutePlanning.observe(
                () -> AttendedStockProduction.materialBackedDemand(supplied, home, 0));
        assertTrue(observed.result(), "real stock and a declared reserve retain staffing potential during plan review");
        assertTrue(observed.requests().isEmpty(), "staffing must not launch navigation");
        assertEquals(0, ProductionStockDemand.read(supplied, home, "minecraft:string", 0).missing());
        assertTrue(AttendedStockProduction.ready(supplied, home, 0).isEmpty(), "potential is not a production grant");
    }
    @Test void tacticalRosterCannotInvalidateItsOwnUnifiedProductionPlanButAChangedScheduleCan() {
        var state = World.STATE; var home = state.bootstrap().settlements().getFirst().id();
        var permissions = SettlementWorkPolicy.permissions(state, home);
        var member = permissions.workers(ResidentWorkKind.PRODUCTION).iterator().next();
        var roster = state.withStrategicPlans(state.strategicPlans().withWorkPermissions(home, permissions.withoutResident(member)));
        var changes = new ArrayList<SettlementEconomicPlanningState.Change>();
        new PopulationEconomicInputChanges().collect(state, roster, (owner, reason, resources) -> changes.add(reason));
        assertTrue(changes.isEmpty(), "admission permissions are not a change to long-run productive capacity");
        var schedule = new SettlementDailySchedule(24_000, List.of(
                new SettlementDailySchedule.Segment(0, 23_999, SettlementDailySchedule.Window.WORK),
                new SettlementDailySchedule.Segment(23_999, 24_000, SettlementDailySchedule.Window.FREE)));
        var changed = roster.withHumanPopulation(roster.humanPopulation().withSchedule(home, schedule));
        new PopulationEconomicInputChanges().collect(roster, changed, (owner, reason, resources) -> changes.add(reason));
        assertTrue(changes.contains(SettlementEconomicPlanningState.Change.CAPACITY), "a real capacity input must still invalidate");
    }
    @Test void reserveIsFiniteAndTextileRecipesDoNotCreateFutureConsumption() {
        var state = World.STATE; var home = state.bootstrap().settlements().getFirst().id();
        assertEquals(64, new FieldReserveSupply().read(state, home).needs().get("pale_mirror:cotton").immediate());
        var reserve = new AttendedStockReserveSupply().read(state, home);
        assertEquals(64, reserve.needs().get("minecraft:string").immediate());
        assertEquals(64, reserve.needs().get("pale_mirror:cloth").immediate());
        assertEquals(io.farfrontier.palemirror.frontier.v3.model.supply.SupplyReservePlanner.Rate.ZERO, reserve.needs().get("pale_mirror:cloth").consumption());
        var spin = RULES.production().require(new ProductionRecipe.Id("pale_mirror:cotton_thread"));
        var weave = RULES.production().require(new ProductionRecipe.Id("pale_mirror:woven_cloth"));
        assertFalse(spin.requiresCombustion()); assertFalse(weave.requiresCombustion());
        assertEquals(2, spin.product().quantity()); assertEquals(4, weave.inputUnitsPerBatch());
        assertEquals(ResourceSiteKind.WHEAT_FIELD, RULES.fields().cultivationCrop(), "market wheat area decisions cannot replace the cotton plot");
    }
    @Test void replacementIsScopedToItsDeclaredWorkplaceAndRejectsForgedMounts() {
        var state = World.STATE; var home = state.bootstrap().settlements().getFirst();
        var factory = home.structures().stream().filter(b -> b.kind() == StructureKind.TEXTILE_WORKSHOP).findFirst().orElseThrow();
        var stations = ProductionFacilityLayout.stations(factory); var spin = stations.getFirst(); var sibling = stations.getLast();
        var replacement = remount(spin, spin.capability(), spin.workplaceId());
        var unknown = remount(spin, spin.capability(), new ProductionStationSpec.WorkplaceId("missing"));
        assertThrows(IllegalArgumentException.class, () -> install(state, unknown));
        var wrongCapability = remount(spin, sibling.capability(), spin.workplaceId());
        assertThrows(IllegalArgumentException.class, () -> install(state, wrongCapability));
        assertThrows(NullPointerException.class, () -> remount(spin, spin.capability(), null));
        var retired = state.withInventory(state.inventory().withSurfaceStatus(spin.containerId(), ContainerSurfaceStatus.PREPARED)
                .withSurfaceStatus(spin.containerId(), ContainerSurfaceStatus.ACTIVE).withSurfaceStatus(spin.containerId(), ContainerSurfaceStatus.RETIRED));
        retired = new ProductionStationRetired(spin.id(), state.productionStations().require(spin.id()).revision()).apply(retired, spin.id());
        var renewed = install(retired, replacement);
        assertEquals(sibling, ProductionStationDirectory.require(renewed.inventory(), sibling.id()).productionStation().orElseThrow());
        assertEquals(replacement, ProductionStationDirectory.require(renewed.inventory(), replacement.id()).productionStation().orElseThrow());
        assertEquals(state.productionStations().require(sibling.id()), renewed.productionStations().require(sibling.id()));
    }
    private static ProductionStationSpec remount(ProductionStationSpec original, ProductionStationSpec.Capability capability,
            ProductionStationSpec.WorkplaceId workplace) {
        return new ProductionStationSpec(new SubjectId("station:textile-replacement"), original.facilityId(), new SubjectId("container:textile-replacement"),
                original.equipmentType(), capability, workplace, original.workerStation(), original.socketSurface(), original.facing(),
                original.inputSlots(), original.outputSlot(), original.fuelSlot(), original.maintenanceSlot());
    }
    private static FrontierWorldState install(FrontierWorldState state, ProductionStationSpec spec) {
        var record = new ContainerRecord(spec.containerId(), state.bootstrap().settlements().getFirst().id(), spec.inventorySize(),
                ContainerPurpose.PRODUCTION_STATION, Optional.of(spec));
        return new ProductionStationInstalled(record, spec.socketSurface().support().offset(0, 1, 0), List.of())
                .apply(state, spec.id(), ProductionInstallationComposition::installed);
    }

}
