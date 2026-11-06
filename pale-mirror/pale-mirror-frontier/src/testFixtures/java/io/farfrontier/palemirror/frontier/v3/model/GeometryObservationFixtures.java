package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.geometry.*;
import java.util.*;

/** Explicit initial/history observations for pure tests; never native outcome evidence. */
public final class GeometryObservationFixtures {
    private GeometryObservationFixtures() { }
    public static NavigationObservation air() {
        return NavigationObservation.known(new NavigationCell(NavigationShape.EMPTY, true,
                Optional.of(new NavigationBlockState("minecraft:air", Map.of()))));
    }
    public static FrontierWorldState observedAir(FrontierWorldState state, BlockPosition position) {
        return state.withChanges(FrontierWorldStateUpdate.begin().navigationGeometry(state.navigationGeometry()
                .observe(state.navigationGeometry().revision(), Map.of(position, air()))));
    }
}
