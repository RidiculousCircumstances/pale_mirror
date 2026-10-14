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
        var result = new LoopErasure();
        for (SurfaceAnchor surface : path) {
            while (!result.appendStep(surface)) { /* One bounded removal per step. */ }
        }
        return result.result();
    }

    /** Incremental counterpart for planners: a long loop cannot consume an uncharged slice. */
    static final class LoopErasure {
        private final ArrayList<SurfaceAnchor> path = new ArrayList<>();
        private final HashMap<SurfaceAnchor, Integer> indices = new HashMap<>();

        boolean appendStep(SurfaceAnchor surface) {
            Objects.requireNonNull(surface, "pedestrian composed surface");
            Integer prior = indices.get(surface);
            if (prior == null) {
                indices.put(surface, path.size()); path.add(surface); return true;
            }
            if (path.size() > prior + 1) { indices.remove(path.removeLast()); return false; }
            return true;
        }
        int size() { return path.size(); }
        List<SurfaceAnchor> result() { return List.copyOf(path); }
    }
}
