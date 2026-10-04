package io.farfrontier.palemirror.frontier.v3.model.navigation;

import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

/** Composition of geometry-only legs, not an itinerary with required intermediate actions. */
public final class PedestrianPathComposition {
    private PedestrianPathComposition() { }

    /**
     * An outdoor leg can re-enter an already traversed entrance apron. Remove that
     * geometric cycle, preserving both endpoints and every remaining witnessed edge.
     * Replanning the next step must not repeatedly execute an unnecessary exit detour.
     */
    public static List<SurfaceAnchor> withoutLoops(List<SurfaceAnchor> path) {
        Objects.requireNonNull(path, "pedestrian composed path");
        if (path.isEmpty()) throw new IllegalArgumentException("pedestrian composed path is empty");
        var result = new ArrayList<SurfaceAnchor>();
        var indices = new HashMap<SurfaceAnchor, Integer>();
        for (SurfaceAnchor surface : path) {
            Objects.requireNonNull(surface, "pedestrian composed surface");
            Integer prior = indices.get(surface);
            if (prior != null) {
                while (result.size() > prior + 1) indices.remove(result.removeLast());
            } else {
                indices.put(surface, result.size());
                result.add(surface);
            }
        }
        return List.copyOf(result);
    }
}
