package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** Admission discovery uses declared facility/capability relations; continuation retains the selected exact instance. */
public final class ProductionStationSelection {
    public static Optional<ProductionStationSpec> usable(FrontierWorldState state, SubjectId facility, ProductionRecipe.Id recipeId) {
        var recipe = state.bootstrap().ruleset().production().require(recipeId);
        return state.inventory().containers().values().stream().flatMap(record -> record.productionStation().stream())
                .filter(station -> station.facilityId().equals(facility) && station.capability() == recipe.capability())
                .filter(station -> !ReferenceContainerCustody.blocksCanonicalUse(state, station.containerId()))
                .filter(station -> state.productionStations().machines().containsKey(station.id()))
                .sorted(Comparator.comparing(ProductionStationSpec::id)).reduce((left, right) -> {
                    throw new IllegalArgumentException("facility has competing usable machine instances");
                });
    }
    private ProductionStationSelection() { }
}
