package io.farfrontier.palemirror.frontier.v3.model.geometry;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.*;

/** One bounded atomic replacement of observed navigation facts, including explicit invalidations. */
public record NavigationGeometryObserved(long expectedRevision,
        Map<BlockPosition, NavigationObservation> cells,
        Map<NavigationBlockState, NavigationObservation> definitions) implements FrontierPayload {
    public static final SubjectId OWNER = new SubjectId("geometry:navigation");
    public static final String TYPE = "frontier.navigation_geometry_observed";
    public NavigationGeometryObserved(long expectedRevision, Map<BlockPosition, NavigationObservation> cells) {
        this(expectedRevision, cells, Map.of());
    }
    public NavigationGeometryObserved {
        cells = Map.copyOf(cells);
        definitions = Map.copyOf(definitions);
        if (expectedRevision < 0 || cells.isEmpty() && definitions.isEmpty() || cells.size() + definitions.size() > NavigationGeometryState.MAX_CHANGE)
            throw new IllegalArgumentException("invalid geometry observation batch");
    }
    public FrontierWorldState apply(FrontierWorldState state, SubjectId subject) {
        return state.withChanges(FrontierWorldStateUpdate.begin().navigationGeometry(validated(state, subject)));
    }
    public NavigationGeometryState validated(FrontierWorldState state, SubjectId subject) {
        if (!OWNER.equals(subject) || cells.keySet().stream().anyMatch(position -> !state.bootstrap().bounds().contains(position)))
            throw new IllegalArgumentException("foreign navigation observation scope");
        return state.navigationGeometry().observe(expectedRevision, cells, definitions);
    }
    @Override public String type() { return TYPE; }
    @Override public boolean requiresDurableBeforeEffect() { return true; }
}
