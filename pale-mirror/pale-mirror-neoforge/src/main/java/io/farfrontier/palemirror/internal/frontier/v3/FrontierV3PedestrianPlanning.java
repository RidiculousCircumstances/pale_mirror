package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.navigation.CooperativePedestrianPlanner;
import io.farfrontier.palemirror.frontier.v3.model.navigation.HierarchicalPedestrianSearch;
import io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRoutePlanning;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianPlanningReady;
import io.farfrontier.palemirror.frontier.v3.process.PedestrianPlanningContinuations;
import io.farfrontier.palemirror.frontier.v3.process.PedestrianPlanningWakeIndex;
import io.farfrontier.palemirror.frontier.v3.process.FrontierWorldProcessCatalog;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;

/** Owns optional calculation lifetime and server-turn budget, never domain clocks, chunks or entities. */
final class FrontierV3PedestrianPlanning {
    /** Safety ceiling on durable optional notifications in one server invocation. */
    private static final int MAX_WAKE_COMMANDS_PER_TURN = 8;
    private static FrontierV3ServerRuntime<?, ?> owner;
    private static CooperativePedestrianPlanner planner;
    private static AutoCloseable binding;
    private static AutoCloseable continuationBinding;
    private static PedestrianPlanningWakeIndex continuations;
    private static int deferredReady;
    private FrontierV3PedestrianPlanning() { }

    static synchronized void tick(FrontierV3ServerRuntime<?, ?> runtime) {
        if (owner == null) {
            var next = new CooperativePedestrianPlanner();
            var nextBinding = PedestrianRoutePlanning.bind(next);
            owner = runtime; planner = next; binding = nextBinding;
            continuations = new PedestrianPlanningWakeIndex();
            continuationBinding = PedestrianPlanningContinuations.bind(continuations);
            FrontierWorldProcessCatalog.restorePlanningWaits(FrontierWorldRuntimeDefinition.processRegistry(),
                    (FrontierWorldState) runtime.decodedState().orElseThrow(), runtime.executionView().orElseThrow());
        }
        if (owner != runtime) throw new IllegalStateException("pedestrian planning has a foreign runtime owner");
        planner.advance(HierarchicalPedestrianSearch.MIN_SLICE_WORK * 2);
        continuations.changed(planner.drainChanges());
        admitReady(runtime, continuations);
        deferredReady = continuations.readyCount();
    }
    static void admitReady(FrontierV3ServerRuntime<?, ?> runtime, PedestrianPlanningWakeIndex index) {
        var ready = index.ready(runtime.executionView().orElseThrow());
        int budget = runtime.commandAdmissionCapacity().orElseThrow().optionalCommands(MAX_WAKE_COMMANDS_PER_TURN);
        int admitted = 0;
        for (var action : ready) {
            if (admitted == budget) break;
            var result = FrontierV3CommandSubmission.submitResultBound(runtime, "pedestrian-planning-ready", action.subject().value(),
                    new PedestrianPlanningReady(action), action);
            if (result instanceof io.farfrontier.palemirror.frontier.v3.api.CommandResult.Rejected rejected) {
                var code = rejected.rejection().code();
                if (code == io.farfrontier.palemirror.frontier.v3.api.RejectionCode.RECEIPT_CAPACITY_EXHAUSTED
                        || code == io.farfrontier.palemirror.frontier.v3.api.RejectionCode.TRANSACTION_CAPACITY_EXHAUSTED) break;
                throw new IllegalStateException("addressed planning wake rejected: " + result);
            }
            index.remove(action.id());
            admitted++;
        }
    }
    static synchronized String diagnostic(FrontierV3ServerRuntime<?, ?> runtime) {
        var capacity = runtime.commandAdmissionCapacity().orElseThrow();
        return "{\"waiting\":" + (owner == runtime ? continuations.waitingCount() : 0)
                + ",\"readyDeferred\":" + (owner == runtime ? deferredReady : 0)
                + ",\"wakeSafetyMaximum\":" + MAX_WAKE_COMMANDS_PER_TURN
                + ",\"availableReceipts\":" + capacity.availableReceipts()
                + ",\"maximumReceipts\":" + capacity.maximumReceipts()
                + ",\"physicalAdmissionHeld\":" + !capacity.hasCausalHeadroom()
                + ",\"availableTransactions\":" + capacity.availableTransactions() + "}";
    }
    static synchronized void forget(FrontierV3ServerRuntime<?, ?> runtime) {
        if (owner != runtime) return;
        try { continuationBinding.close(); binding.close(); }
        catch (Exception error) { throw new IllegalStateException("cannot release pedestrian planner", error); }
        finally { planner.close(); binding = null; continuationBinding = null; continuations = null; planner = null; owner = null; deferredReady = 0; }
    }
}
