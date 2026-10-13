package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import java.util.*;

/** Resource/custody disposition precedes removing the old machine runtime, never the reverse. */
public record ProductionStationRetired(SubjectId stationId, long machineRevision) implements FrontierPayload {
    public static final String TYPE = "frontier.production_station_retired";
    public ProductionStationRetired { Objects.requireNonNull(stationId); if (machineRevision < 1) throw new IllegalArgumentException("invalid retirement revision"); }
    @Override public String type() { return TYPE; }
    public FrontierWorldState apply(FrontierWorldState state, SubjectId subject) {
        var machine = state.productionStations().require(stationId);
        var record = ProductionStationEnergy.record(state, stationId);
        if (!subject.equals(stationId) || machine.revision() != machineRevision || machine.operation().isPresent()
                || machine.pendingFuel().isPresent() || machine.pendingRecipe().isPresent()
                || state.inventory().surfaces().get(record.id()).status() != ContainerSurfaceStatus.RETIRED)
            throw new IllegalArgumentException("machine retirement lacks disposed contents, operation and physical instance");
        var machines = new LinkedHashMap<>(state.productionStations().machines()); machines.remove(stationId);
        return state.withChanges(FrontierWorldStateUpdate.begin().productionStations(new ProductionStations(machines)));
    }
}
