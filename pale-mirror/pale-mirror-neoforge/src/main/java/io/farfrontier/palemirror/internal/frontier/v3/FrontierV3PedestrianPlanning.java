package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.navigation.CooperativePedestrianPlanner;
import io.farfrontier.palemirror.frontier.v3.model.navigation.HierarchicalPedestrianSearch;
import io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRoutePlanning;

/** Owns optional calculation lifetime and server-turn budget, never domain clocks, chunks or entities. */
final class FrontierV3PedestrianPlanning {
    private static FrontierV3ServerRuntime<?, ?> owner;
    private static CooperativePedestrianPlanner planner;
    private static AutoCloseable binding;
    private FrontierV3PedestrianPlanning() { }

    static synchronized void tick(FrontierV3ServerRuntime<?, ?> runtime) {
        if (owner == null) {
            var next = new CooperativePedestrianPlanner();
            var nextBinding = PedestrianRoutePlanning.bind(next);
            owner = runtime; planner = next; binding = nextBinding;
        }
        if (owner != runtime) throw new IllegalStateException("pedestrian planning has a foreign runtime owner");
        planner.advance(HierarchicalPedestrianSearch.MIN_SLICE_WORK * 2);
    }
    static synchronized void forget(FrontierV3ServerRuntime<?, ?> runtime) {
        if (owner != runtime) return;
        try { binding.close(); }
        catch (Exception error) { throw new IllegalStateException("cannot release pedestrian planner", error); }
        finally { planner.close(); binding = null; planner = null; owner = null; }
    }
}
