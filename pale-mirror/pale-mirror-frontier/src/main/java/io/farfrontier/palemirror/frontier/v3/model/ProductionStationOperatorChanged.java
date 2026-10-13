package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import java.util.Objects;
import java.util.UUID;

/** Physical adapter's current menu/operator observation; never an invented resident activity. */
public record ProductionStationOperatorChanged(SubjectId stationId, long machineRevision, UUID playerId,
                                                ProductionRecipe.Id recipeId, Stage stage, long atTick) implements FrontierPayload {
    public enum Stage { START, ATTEND, PAUSE, RESUME }
    public static final String TYPE = "frontier.production_station_operator_changed";
    public ProductionStationOperatorChanged {
        Objects.requireNonNull(stationId); Objects.requireNonNull(playerId); Objects.requireNonNull(recipeId); Objects.requireNonNull(stage);
        if (machineRevision < 1 || atTick < 0) throw new IllegalArgumentException("invalid current station operator observation");
    }
    @Override public String type() { return TYPE; }
}
