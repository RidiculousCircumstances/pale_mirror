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
    private static java.util.concurrent.ThreadPoolExecutor workers;
    private static AutoCloseable binding;
    private static AutoCloseable continuationBinding;
    private static PedestrianPlanningWakeIndex continuations;
    private static int deferredReady;
    private FrontierV3PedestrianPlanning() { }

    static synchronized void tick(FrontierV3ServerRuntime<?, ?> runtime) {
        tick(runtime, (long) (io.farfrontier.palemirror.internal.world.PaleMirrorServerConfig.RUNTIME_BUDGET_MILLIS.get() * 1_000_000L));
    }
    /** Host budget is operational input, independent of simulation time and test config bootstrapping. */
    static synchronized void tick(FrontierV3ServerRuntime<?, ?> runtime, long allowance) {
        if (allowance <= 0) throw new IllegalArgumentException("navigation host budget must be positive");
        if (owner == null) {
            // Host-owned bounded pool. A task captures only immutable known
            // geometry; no worker can access ServerLevel, chunks or domain writes.
            var nextWorkers = new java.util.concurrent.ThreadPoolExecutor(2, 2, 0L, java.util.concurrent.TimeUnit.MILLISECONDS,
                    new java.util.concurrent.ArrayBlockingQueue<>(8),
                    Thread.ofPlatform().daemon(true).name("pm-v3-navigation-", 0).factory(),
                    new java.util.concurrent.ThreadPoolExecutor.AbortPolicy());
            var next = new CooperativePedestrianPlanner(nextWorkers);
            var nextBinding = PedestrianRoutePlanning.bind(next);
            owner = runtime; planner = next; workers = nextWorkers; binding = nextBinding;
            continuations = new PedestrianPlanningWakeIndex();
            continuationBinding = PedestrianPlanningContinuations.bind(continuations);
            FrontierWorldProcessCatalog.restorePlanningWaits(FrontierWorldRuntimeDefinition.processRegistry(),
                    (FrontierWorldState) runtime.decodedState().orElseThrow(), runtime.executionView().orElseThrow());
        }
        if (owner != runtime) throw new IllegalStateException("pedestrian planning has a foreign runtime owner");
        long started = System.nanoTime();
        planner.advance(HierarchicalPedestrianSearch.MIN_SLICE_WORK * 2,
                () -> System.nanoTime() - started < allowance);
        continuations.changed(planner.drainChanges());
        admitReady(runtime, continuations);
        deferredReady = continuations.readyCount();
    }
    static void admitReady(FrontierV3ServerRuntime<?, ?> runtime, PedestrianPlanningWakeIndex index) {
        if (index.waitingCount() == 0) return;
        var ready = index.ready(runtime.canonicalState().orElseThrow().instant(), runtime::retainsScheduledAction);
        if (ready.isEmpty()) return;
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
                + ",\"activeSearches\":" + (owner == runtime ? planner.pendingCount() : 0)
                + ",\"inFlightSlices\":" + (owner == runtime ? planner.inFlightCount() : 0)
                + ",\"runningWorkers\":" + (owner == runtime ? workers.getActiveCount() : 0)
                + ",\"deferredSearches\":" + (owner == runtime ? planner.deferredCount() : 0)
                + ",\"oldestWaitPlanningTurns\":" + (owner == runtime ? planner.oldestWaitTurns() : 0)
                + ",\"searchWorkUnits\":" + (owner == runtime ? planner.workUnits() : 0)
                + ",\"completedSearches\":" + (owner == runtime ? planner.completedCount() : 0)
                + ",\"searchProgressRevision\":" + (owner == runtime ? planner.progressRevision() : 0)
                + ",\"oldestRequests\":" + waitingDiagnostic(runtime)
                + ",\"wakeSafetyMaximum\":" + MAX_WAKE_COMMANDS_PER_TURN
                + ",\"availableReceipts\":" + capacity.availableReceipts()
                + ",\"maximumReceipts\":" + capacity.maximumReceipts()
                + ",\"physicalAdmissionHeld\":" + !capacity.hasCausalHeadroom()
                + ",\"availableTransactions\":" + capacity.availableTransactions() + "}";
    }
    private static String waitingDiagnostic(FrontierV3ServerRuntime<?, ?> runtime) {
        if (owner != runtime) return "[]";
        return planner.waitingDiagnostics(MAX_WAKE_COMMANDS_PER_TURN).stream().map(wait ->
                "{\"from\":[" + wait.start().x() + "," + wait.start().y() + "," + wait.start().z()
                + "],\"to\":[" + wait.target().x() + "," + wait.target().y() + "," + wait.target().z()
                + "],\"phase\":\"" + (wait.deferred() ? "CAPACITY_WAIT" : "SEARCHING")
                + "\",\"waitPlanningTurns\":" + wait.waitTurns() + ",\"searchWorkUnits\":" + wait.searchWorkUnits() + "}")
                .collect(java.util.stream.Collectors.joining(",", "[", "]"));
    }
    static synchronized void forget(FrontierV3ServerRuntime<?, ?> runtime) {
        if (owner != runtime) return;
        try { continuationBinding.close(); binding.close(); }
        catch (Exception error) { throw new IllegalStateException("cannot release pedestrian planner", error); }
        finally { planner.close(); workers.shutdownNow(); workers = null; binding = null; continuationBinding = null; continuations = null; planner = null; owner = null; deferredReady = 0; }
    }
}
