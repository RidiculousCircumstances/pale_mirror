package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import java.util.*;

/** One machine-owned transformation, independent of the operator's task family. */
public record ProductionStationRecipeChanged(Stage stage, ProductionStationRecipeBoundary boundary,
                                              List<FungiblePhysicalObservation.Stack> observed,
                                              Optional<ExactItemStack> observedExact) implements FrontierPayload {
    public ProductionStationRecipeChanged(Stage stage, ProductionStationRecipeBoundary boundary,
            List<FungiblePhysicalObservation.Stack> observed) {
        this(stage, boundary, observed, Optional.empty());
    }
    public enum Stage { COLD_COMPLETED, HOT_PREPARED, HOT_COMPLETED }
    public static final String TYPE = "frontier.production_station_recipe_changed";
    public ProductionStationRecipeChanged {
        Objects.requireNonNull(stage); Objects.requireNonNull(boundary); observed = List.copyOf(observed);
        Objects.requireNonNull(observedExact);
        if ((stage == Stage.COLD_COMPLETED) != (boundary.authorityEpoch() == 0)
                || stage != Stage.HOT_COMPLETED && (!observed.isEmpty() || observedExact.isPresent()) || observed.size() > 3)
            throw new IllegalArgumentException("recipe boundary has foreign physical evidence");
    }
    @Override public String type() { return TYPE; }
    @Override public boolean requiresDurableBeforeEffect() { return stage != Stage.COLD_COMPLETED; }
}
