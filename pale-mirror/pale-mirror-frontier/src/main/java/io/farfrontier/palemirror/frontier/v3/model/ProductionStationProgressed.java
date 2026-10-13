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
        return machine.operation().filter(operation -> !operation.transformed()
                && (state.bootstrap().ruleset().production().require(operation.recipeId()).processingMode() == ProductionRecipe.ProcessingMode.AUTOMATIC
                    || operation.initiator() instanceof ProductionOperation.PlayerInitiator))
                .filter(operation -> operation.progress().filter(WorkProgress::complete).isEmpty())
                .filter(operation -> !(operation.initiator() instanceof ProductionOperation.PlayerInitiator)
                        || state.bootstrap().ruleset().production().require(operation.recipeId()).processingMode() == ProductionRecipe.ProcessingMode.AUTOMATIC
                        || operation.progress().filter(WorkProgress::running).isPresent())
                .filter(operation -> operation.progress().filter(progress -> progress.running() && tick < progress.activeUntilTick()).isEmpty())
                .filter(operation -> {
                    var container = state.inventory().containers().values().stream().filter(record ->
                            record.productionStation().filter(spec -> spec.id().equals(stationId)).isPresent()).findFirst().orElseThrow();
                    return !ReferenceContainerCustody.blocksCanonicalUse(state, container.id());
                })
                .map(operation -> new ProductionStationProgressed(stationId, operation.id(), machine.revision(), operation.progress(),
                        ProductionStationClock.next(state, operation, tick, Optional.empty())));
    }
    public static FrontierWorldState apply(FrontierWorldState state, SubjectId subject, ProductionStationProgressed event) {
        if (!event.stationId().equals(subject)
                || !automatic(state, subject, event.next().evaluatedAtTick()).equals(Optional.of(event)))
            throw new IllegalArgumentException("station processing boundary has stale operation, revision or clock");
        var operation = state.productionStations().require(subject).operation().orElseThrow();
        return state.withChanges(FrontierWorldStateUpdate.begin().productionStations(ProductionStationProcessing.progress(state, operation, event.next())));
    }
}
