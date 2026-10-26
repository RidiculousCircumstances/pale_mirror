package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import java.util.*;

/** An ordinary placed machine is a new instance with an explicitly supplied facility/capability relation. */
public record ProductionStationInstalled(ContainerRecord container, BlockPosition position, List<FixedContainerAdoption.PlacedResource> contents) implements FrontierPayload {
    public static final String TYPE = "frontier.production_station_installed";
    public ProductionStationInstalled {
        Objects.requireNonNull(container); Objects.requireNonNull(position); contents = List.copyOf(contents);
        if (container.productionStation().isEmpty() || container.purpose() != ContainerPurpose.PRODUCTION_STATION
                || container.slotCount() != container.productionStation().orElseThrow().inventorySize())
            throw new IllegalArgumentException("machine installation lacks its complete instance declaration");
    }
    @Override public String type() { return TYPE; }
    @Override public boolean requiresDurableBeforeEffect() { return true; }
    public FrontierWorldState apply(FrontierWorldState state, SubjectId subject,
            java.util.function.BiFunction<FrontierWorldState, ProductionStationSpec, FrontierWorldStateUpdate> installationPort) {
        var spec = container.productionStation().orElseThrow();
        var home = FrontierWorldStateSupport.settlement(state.bootstrap(), container.ownerId());
        var facility = home.structures().stream().filter(value -> value.id().equals(spec.facilityId())).findFirst().orElseThrow(
                () -> new IllegalArgumentException("installed machine has no declared owning facility"));
        var supported = SettlementStructureFootprint.supportSurfaces(facility);
        ProductionPlaces.require(spec.capability(), facility);
        if (!subject.equals(spec.id()) || state.productionStations().machines().containsKey(subject)
                || !state.bootstrap().bounds().contains(position) || !position.equals(spec.socketSurface().support().offset(0, 1, 0))
                || state.structureConditions().get(facility.id()) != StructureCondition.INTACT
                || !supported.contains(spec.socketSurface()) || !supported.contains(spec.workerStation())
                || !state.bootstrap().ruleset().production().requireEquipment(spec.equipmentType()).capabilities().contains(spec.capability())
                || state.inventory().containers().values().stream().flatMap(record -> record.productionStation().stream())
                    .anyMatch(prior -> prior.id().equals(spec.id()) || prior.facilityId().equals(spec.facilityId())
                        && state.inventory().surfaces().get(prior.containerId()).status() != ContainerSurfaceStatus.RETIRED))
            throw new IllegalArgumentException("machine installation has foreign instance/facility/socket or competing equipment");
        for (var value : contents) {
            var section = container.compartmentLayout().sectionAt(value.slot());
            if (!ProductionStationRecipe.accepts(state.bootstrap().ruleset().production(), spec, section, value.lot().itemKind()))
                throw new IllegalArgumentException("installed material is not admitted by its declared compartment");
        }
        return state.withChanges(FrontierWorldStateUpdate.begin()
                .inventory(FixedContainerAdoption.adoptPlaced(state.inventory(), container, position, contents))
                .fencedRecovery(FixedContainerAdoption.prepare(state.fencedRecovery(), container))
                .productionStations(state.productionStations().with(ProductionStations.Machine.installed(subject)))
                .merge(installationPort.apply(state, spec)));
    }
}
