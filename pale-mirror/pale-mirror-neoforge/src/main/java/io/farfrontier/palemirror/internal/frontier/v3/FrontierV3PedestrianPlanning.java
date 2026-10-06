package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.navigation.CooperativePedestrianPlanner;
import io.farfrontier.palemirror.frontier.v3.model.navigation.HierarchicalPedestrianSearch;
import io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRoutePlanning;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.group.UnitGroup;
import io.farfrontier.palemirror.frontier.v3.model.group.UnitGroupNavigationReady;
import io.farfrontier.palemirror.frontier.v3.process.UnitGroupProcess;

/** Owns optional calculation lifetime and server-turn budget, never domain clocks, chunks or entities. */
final class FrontierV3PedestrianPlanning {
    private static FrontierV3ServerRuntime<?, ?> owner;
    private static CooperativePedestrianPlanner planner;
    private static AutoCloseable binding;
    private static long observedProgress = -1;
    private FrontierV3PedestrianPlanning() { }

    static synchronized void tick(FrontierV3ServerRuntime<?, ?> runtime) {
        if (owner == null) {
            var next = new CooperativePedestrianPlanner();
            var nextBinding = PedestrianRoutePlanning.bind(next);
            owner = runtime; planner = next; binding = nextBinding;
        }
        if (owner != runtime) throw new IllegalStateException("pedestrian planning has a foreign runtime owner");
        planner.advance(HierarchicalPedestrianSearch.MIN_SLICE_WORK * 2);
        long progress = planner.progressRevision();
        if (progress != observedProgress) {
            boolean recovering = observedProgress == -1;
            observedProgress = progress;
            // Event-driven reconsideration of retained groups only, not per-tick population polling.
            // Rebind on restart; the volatile calculation queue is deliberately not persisted.
            var state = (FrontierWorldState) runtime.decodedState().orElseThrow();
            var notifications = state.unitGroups().groups().values().stream()
                    .filter(group -> group.phase() != UnitGroup.Phase.CLOSED)
                    .filter(group -> recovering || UnitGroupProcess.navigationReadiness(state, group).reconsiderOnPlanningProgress())
                    .map(group -> new UnitGroupNavigationReady(group.id(), group.revision())).toList();
            for (var notification : notifications) FrontierV3CommandSubmission.submit(runtime, "group-navigation-ready",
                    notification.groupId().value(), notification);
        }
    }
    static synchronized void forget(FrontierV3ServerRuntime<?, ?> runtime) {
        if (owner != runtime) return;
        try { binding.close(); }
        catch (Exception error) { throw new IllegalStateException("cannot release pedestrian planner", error); }
        finally { planner.close(); binding = null; planner = null; owner = null; observedProgress = -1; }
    }
}
