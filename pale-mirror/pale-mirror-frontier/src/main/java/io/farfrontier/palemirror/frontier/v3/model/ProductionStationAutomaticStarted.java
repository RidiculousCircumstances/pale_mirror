package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import java.util.Objects;

/** Exact station revision and policy recipe at institution admission; replay selects the same retained inventory. */
public record ProductionStationAutomaticStarted(SubjectId stationId, long machineRevision,
        ProductionRecipe.Id recipeId, long atTick) implements FrontierPayload {
    public static final String TYPE = "frontier.production_station_automatic_started";
    public ProductionStationAutomaticStarted {
        Objects.requireNonNull(stationId); Objects.requireNonNull(recipeId);
        if (machineRevision < 1 || atTick < 0) throw new IllegalArgumentException("invalid automatic station admission");
    }
    @Override public String type() { return TYPE; }
}
