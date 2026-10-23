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
        tick(runtime, (long) (io.farfrontier.palemirror.internal.world.PaleMirrorServerConfig.RUNTIME_BUDGET_MILLIS.get() * 1_000_000L),
                io.farfrontier.palemirror.internal.world.PaleMirrorServerConfig.NAVIGATION_WORKERS.get(),
                io.farfrontier.palemirror.internal.world.PaleMirrorServerConfig.NAVIGATION_SLICES_PER_TURN.get());
    }
    /** Host budget is operational input, independent of simulation time and test config bootstrapping. */
    static synchronized void tick(FrontierV3ServerRuntime<?, ?> runtime, long allowance) {
        tick(runtime, allowance, io.farfrontier.palemirror.internal.world.PaleMirrorServerConfig.NAVIGATION_WORKERS.getDefault(),
                io.farfrontier.palemirror.internal.world.PaleMirrorServerConfig.NAVIGATION_SLICES_PER_TURN.getDefault());
    }
    private static void tick(FrontierV3ServerRuntime<?, ?> runtime, long allowance, int parallelism, int slices) {
        if (allowance <= 0) throw new IllegalArgumentException("navigation host budget must be positive");
        if (owner == null) {
            // Host-owned bounded pool. A task captures only immutable known
            // geometry; no worker can access ServerLevel, chunks or domain writes.
            var nextWorkers = new java.util.concurrent.ThreadPoolExecutor(parallelism, parallelism, 0L, java.util.concurrent.TimeUnit.MILLISECONDS,
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
        continuations.releaseUnused(planner::discardUnobserved);
        long started = System.nanoTime();
        planner.advance(HierarchicalPedestrianSearch.MIN_SLICE_WORK * slices,
                () -> System.nanoTime() - started < allowance);
        continuations.changed(planner.drainChanges());
        admitReady(runtime, continuations);
        deferredReady = continuations.readyCount();
    }
    static void admitReady(FrontierV3ServerRuntime<?, ?> runtime, PedestrianPlanningWakeIndex index) {
        if (index.waitingCount() == 0 || runtime.awaitingCanonicalPredecessor()) return;
        var ready = index.ready(runtime.canonicalState().orElseThrow().instant(), runtime::retainsScheduledAction);
        if (ready.isEmpty()) return;
        int budget = runtime.commandAdmissionCapacity().orElseThrow().optionalCommands(MAX_WAKE_COMMANDS_PER_TURN);
        int admitted = 0;
        for (var action : ready) {
            if (admitted == budget) break;
            var wake = new PedestrianPlanningReady(action);
            var continuation = wake.continuationAt(runtime.canonicalState().orElseThrow().instant());
            var result = FrontierV3CommandSubmission.submitResultBound(runtime, "pedestrian-planning-ready", action.subject().value(),
                    wake, action);
            if (result instanceof io.farfrontier.palemirror.frontier.v3.api.CommandResult.Rejected rejected) {
                var code = rejected.rejection().code();
                if (code == io.farfrontier.palemirror.frontier.v3.api.RejectionCode.RECEIPT_CAPACITY_EXHAUSTED
                        || code == io.farfrontier.palemirror.frontier.v3.api.RejectionCode.TRANSACTION_CAPACITY_EXHAUSTED) break;
                throw new IllegalStateException("addressed planning wake rejected: " + result);
            }
            if (result instanceof io.farfrontier.palemirror.frontier.v3.api.CommandResult.Pending pending
                    && pending.reason() == io.farfrontier.palemirror.frontier.v3.api.CommandResult.Pending.Reason.PREDECESSOR) break;
            runtime.afterCommand(result, () -> {
                if (!runtime.retainsScheduledAction(continuation))
                    throw new IllegalStateException("accepted planning wake lost its exact runnable generation");
                index.resumed(action, continuation);
            });
            admitted++;
            if (result instanceof io.farfrontier.palemirror.frontier.v3.api.CommandResult.Pending) break;
        }
    }
    static synchronized java.util.Set<io.farfrontier.palemirror.frontier.v3.model.BlockPosition> knowledgeDemands(FrontierV3ServerRuntime<?, ?> runtime) {
        return owner == runtime ? planner.knowledgeDemands() : java.util.Set.of();
    }
    static synchronized void geometryObserved(FrontierV3ServerRuntime<?, ?> runtime,
            java.util.Set<io.farfrontier.palemirror.frontier.v3.model.BlockPosition> positions) {
        if (owner == runtime) planner.geometryObserved(positions);
    }
    static synchronized String diagnostic(FrontierV3ServerRuntime<?, ?> runtime) {
        var capacity = runtime.commandAdmissionCapacity().orElseThrow();
        return "{\"waiting\":" + (owner == runtime ? continuations.waitingCount() : 0)
                + ",\"readyDeferred\":" + (owner == runtime ? deferredReady : 0)
                + ",\"activeSearches\":" + (owner == runtime ? planner.pendingCount() : 0)
                + ",\"inFlightSlices\":" + (owner == runtime ? planner.inFlightCount() : 0)
                + ",\"runningWorkers\":" + (owner == runtime ? workers.getActiveCount() : 0)
                + ",\"workerPoolSize\":" + (owner == runtime ? workers.getPoolSize() : 0)
                + ",\"queuedWorkerSlices\":" + (owner == runtime ? workers.getQueue().size() : 0)
                + ",\"completedWorkerSlices\":" + (owner == runtime ? workers.getCompletedTaskCount() : 0)
                + ",\"deferredSearches\":" + (owner == runtime ? planner.deferredCount() : 0)
                + ",\"suspendedSearches\":" + (owner == runtime ? planner.suspendedCount() : 0)
                + ",\"serviceQueues\":" + serviceDiagnostic(runtime)
                + ",\"oldestWaitPlanningTurns\":" + (owner == runtime ? planner.oldestWaitTurns() : 0)
                + ",\"searchWorkUnits\":" + (owner == runtime ? planner.workUnits() : 0)
                + ",\"completedSearches\":" + (owner == runtime ? planner.completedCount() : 0)
                + ",\"searchProgressRevision\":" + (owner == runtime ? planner.progressRevision() : 0)
                + ",\"oldestRequests\":" + waitingDiagnostic(runtime)
                + ",\"geometryWaits\":" + knowledgeDiagnostic(runtime)
                + ",\"wakeSafetyMaximum\":" + MAX_WAKE_COMMANDS_PER_TURN
                + ",\"availableReceipts\":" + capacity.availableReceipts()
                + ",\"maximumReceipts\":" + capacity.maximumReceipts()
                + ",\"physicalAdmissionHeld\":" + !capacity.hasCausalHeadroom()
                + ",\"availableTransactions\":" + capacity.availableTransactions() + "}";
    }
    private static String serviceDiagnostic(FrontierV3ServerRuntime<?, ?> runtime) {
        if (owner != runtime) return "[]";
        return planner.serviceDiagnostics().stream().map(service -> "{\"service\":\"" + service.service()
                + "\",\"active\":" + service.active() + ",\"waiting\":" + service.waiting()
                + ",\"oldestWaitPlanningTurns\":" + service.oldestWaitTurns() + "}")
                .collect(java.util.stream.Collectors.joining(",", "[", "]"));
    }
    private static String waitingDiagnostic(FrontierV3ServerRuntime<?, ?> runtime) {
        if (owner != runtime) return "[]";
        return planner.waitingDiagnostics(MAX_WAKE_COMMANDS_PER_TURN).stream().map(wait ->
                "{\"from\":[" + wait.start().x() + "," + wait.start().y() + "," + wait.start().z()
                + "],\"to\":[" + wait.target().x() + "," + wait.target().y() + "," + wait.target().z()
                + "],\"phase\":\"" + (wait.deferred() ? "CAPACITY_WAIT" : "SEARCHING")
                + "\",\"detail\":\"" + wait.extent().detail() + "\",\"maximumSurfaces\":" + wait.extent().maximumSurfaces()
                + ",\"waitPlanningTurns\":" + wait.waitTurns() + ",\"searchWorkUnits\":" + wait.searchWorkUnits()
                + ",\"stage\":\"" + wait.stage() + "\",\"restarts\":" + wait.restarts() + "}")
                .collect(java.util.stream.Collectors.joining(",", "[", "]"));
    }
    private static String knowledgeDiagnostic(FrontierV3ServerRuntime<?, ?> runtime) {
        if (owner != runtime) return "[]";
        var state = (FrontierWorldState) runtime.decodedState().orElseThrow();
        return planner.knowledgeDiagnostics(8).stream().map(wait -> {
            var cells = wait.result().missingGeometry().stream().sorted(java.util.Comparator
                    .comparingInt(io.farfrontier.palemirror.frontier.v3.model.BlockPosition::x)
                    .thenComparingInt(io.farfrontier.palemirror.frontier.v3.model.BlockPosition::y)
                    .thenComparingInt(io.farfrontier.palemirror.frontier.v3.model.BlockPosition::z)).limit(8).map(position -> {
                var evidence = state.navigationGeometry().cells().get(position);
                return "{\"position\":[" + position.x() + "," + position.y() + "," + position.z() + "],\"observation\":\""
                        + (evidence == null ? "NOT_OBSERVED" : evidence.kind().name()) + "\"}";
            }).collect(java.util.stream.Collectors.joining(",", "[", "]"));
            return "{\"from\":[" + wait.start().x() + "," + wait.start().y() + "," + wait.start().z()
                    + "],\"to\":[" + wait.target().x() + "," + wait.target().y() + "," + wait.target().z()
                    + "],\"reason\":\"" + wait.result().reason() + "\",\"missingCells\":" + cells + "}";
        }).collect(java.util.stream.Collectors.joining(",", "[", "]"));
    }
    static synchronized void forget(FrontierV3ServerRuntime<?, ?> runtime) {
        if (owner != runtime) return;
        try { continuationBinding.close(); binding.close(); }
        catch (Exception error) { throw new IllegalStateException("cannot release pedestrian planner", error); }
        finally { planner.close(); workers.shutdownNow(); workers = null; binding = null; continuationBinding = null; continuations = null; planner = null; owner = null; deferredReady = 0; }
    }
}
