package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import java.util.Objects;
import java.util.Optional;

/** Sparse automatic-machine clock boundary, not an inventory effect receipt. */
public record ProductionStationProgressed(SubjectId stationId, ProductionOperation.Id operationId,
                                         long machineRevision, Optional<WorkProgress> previous,
                                         WorkProgress next) implements FrontierPayload {
    public static final String TYPE = "frontier.production_station_progressed";
    public ProductionStationProgressed {
        Objects.requireNonNull(stationId); Objects.requireNonNull(operationId);
        previous = Objects.requireNonNull(previous); Objects.requireNonNull(next);
        if (machineRevision < 1 || previous.equals(Optional.of(next)))
            throw new IllegalArgumentException("invalid station processing boundary");
    }
    @Override public String type() { return TYPE; }
    public static Optional<ProductionStationProgressed> automatic(FrontierWorldState state, SubjectId stationId, long tick) {
        var machine = state.productionStations().require(stationId);
        if (machine.pendingFuel().isPresent() || machine.pendingRecipe().isPresent()) return Optional.empty();
        var operation = machine.operation().orElse(null);
        if (operation == null || operation.transformed() || operation.complete()) return Optional.empty();
        var record = ProductionStationEnergy.record(state, stationId);
        if (ReferenceContainerCustody.blocksCanonicalUse(state, record.id())) return Optional.empty();
        var attendance = operation.attendance().filter(value -> value.permittedUntilTick() > tick);
        if (!ProductionStationClock.updateNeeded(state, operation, tick, attendance)) return Optional.empty();
        var next = ProductionStationClock.next(state, operation, tick, attendance);
        if (operation.progress().equals(Optional.of(next))) return Optional.empty();
        return Optional.of(new ProductionStationProgressed(stationId, operation.id(), machine.revision(), operation.progress(), next));
    }
    public static FrontierWorldState apply(FrontierWorldState state, SubjectId subject, ProductionStationProgressed event) {
        if (!event.stationId().equals(subject)
                || !automatic(state, subject, event.next().evaluatedAtTick()).equals(Optional.of(event)))
            throw new IllegalArgumentException("station processing boundary has stale operation, revision or clock");
        var operation = state.productionStations().require(subject).operation().orElseThrow();
        return state.withChanges(FrontierWorldStateUpdate.begin().productionStations(ProductionStationProcessing.progress(
                state, operation, event.next(), operation.attendance().filter(value -> value.permittedUntilTick() > event.next().evaluatedAtTick()))));
    }
}
