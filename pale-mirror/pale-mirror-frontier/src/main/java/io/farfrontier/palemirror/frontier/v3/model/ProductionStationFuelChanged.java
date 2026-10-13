package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import java.util.*;

/** Durable machine-owned fuel transition, never a resident work/arrival event. */
public record ProductionStationFuelChanged(Stage stage, ProductionStationFuelBurn burn,
        List<FungiblePhysicalObservation.Stack> remaining) implements FrontierPayload {
    public static final String TYPE = "frontier.production_station_fuel_changed";
    public enum Stage { COLD_CONSUMED, HOT_PREPARED, HOT_CONSUMED }
    public ProductionStationFuelChanged {
        Objects.requireNonNull(stage); Objects.requireNonNull(burn); remaining = List.copyOf(remaining);
        if (remaining.size() > 64 || stage != Stage.HOT_CONSUMED && !remaining.isEmpty()
                || (stage == Stage.COLD_CONSUMED) != (burn.authorityEpoch() == 0))
            throw new IllegalArgumentException("station fuel transition has foreign physical evidence");
    }
    @Override public String type() { return TYPE; }
    @Override public boolean requiresDurableBeforeEffect() { return stage != Stage.COLD_CONSUMED; }
}
