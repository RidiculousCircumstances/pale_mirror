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
    private static java.util.function.Supplier<PedestrianPlanningContinuations.Recovery> recoveryPlan;
    private static java.util.concurrent.Future<PedestrianPlanningContinuations.Recovery> recovery;
    private static int deferredReady;
    private static io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianPlanningAllowance workTurn;
    private static long remainingHostNanos;
    private static final java.util.concurrent.atomic.AtomicBoolean calculationReady = new java.util.concurrent.atomic.AtomicBoolean();
    private FrontierV3PedestrianPlanning() { }

    /** Renewed exactly once by the observed host tick, even while persistence yields. */
    static synchronized void beginTurn(FrontierV3ServerRuntime<?, ?> runtime) {
        openTurn(runtime, (long) (io.farfrontier.palemirror.internal.world.PaleMirrorServerConfig.RUNTIME_BUDGET_MILLIS.get() * 1_000_000L),
                io.farfrontier.palemirror.internal.world.PaleMirrorServerConfig.NAVIGATION_WORKERS.get(),
                io.farfrontier.palemirror.internal.world.PaleMirrorServerConfig.NAVIGATION_SLICES_PER_TURN.get());
    }
    /** Explicit host-turn budget, independent of simulation time/config bootstrapping. */
    static synchronized void tick(FrontierV3ServerRuntime<?, ?> runtime, long allowance) {
        openTurn(runtime, allowance, io.farfrontier.palemirror.internal.world.PaleMirrorServerConfig.NAVIGATION_WORKERS.getDefault(),
                io.farfrontier.palemirror.internal.world.PaleMirrorServerConfig.NAVIGATION_SLICES_PER_TURN.getDefault());
        resume(runtime);
    }
    private static void openTurn(FrontierV3ServerRuntime<?, ?> runtime, long allowance, int parallelism, int slices) {
        if (allowance <= 0) throw new IllegalArgumentException("navigation host budget must be positive");
        initialize(runtime, parallelism);
        if (workTurn != null) workTurn.close();
        workTurn = new io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianPlanningAllowance(
                HierarchicalPedestrianSearch.MIN_SLICE_WORK * slices);
        remainingHostNanos = allowance; calculationReady.set(true);
    }
    /** Worker readiness reuses the original deterministic allowance and remaining owner
     * CPU budget. It neither advances time nor renews physical/canonical admission. */
    static synchronized void resume(FrontierV3ServerRuntime<?, ?> runtime) {
        if (owner != runtime || workTurn == null || remainingHostNanos <= 0 || !calculationReady.compareAndSet(true, false)) return;
        long started = System.nanoTime();
        try {
            recoverWaits(runtime);
            continuations.releaseUnused(planner::discardUnobserved);
            planner.retainDemandKeys(continuations.retainedDemandKeys(runtime.canonicalState().orElseThrow().instant()));
            planner.advance(workTurn, () -> System.nanoTime() - started < remainingHostNanos);
            continuations.changed(planner.drainChanges());
            admitReady(runtime, continuations);
            deferredReady = continuations.readyCount();
        } finally { remainingHostNanos = Math.max(0L, remainingHostNanos - (System.nanoTime() - started)); }
    }
    /** Bind the service during startup, not by class-loading its implementation
     * in the first ordinary world tick. Detached recovery still waits asynchronously.
     */
    static synchronized void start(FrontierV3ServerRuntime<?, ?> runtime) {
        initialize(runtime, io.farfrontier.palemirror.internal.world.PaleMirrorServerConfig.NAVIGATION_WORKERS.get());
        recoverWaits(runtime);
    }
    private static void initialize(FrontierV3ServerRuntime<?, ?> runtime, int parallelism) {
        if (owner == null) {
            // Host-owned bounded pool. A task captures only immutable known
            // geometry; no worker can access ServerLevel, chunks or domain writes.
            var nextWorkers = new java.util.concurrent.ThreadPoolExecutor(parallelism, parallelism, 0L, java.util.concurrent.TimeUnit.MILLISECONDS,
                    new java.util.concurrent.ArrayBlockingQueue<>(8),
                    Thread.ofPlatform().daemon(true).name("pm-v3-navigation-", 0).factory(),
                    new java.util.concurrent.ThreadPoolExecutor.AbortPolicy());
            var next = new CooperativePedestrianPlanner(nextWorkers);
            var nextBinding = PedestrianRoutePlanning.bind(next);
            next.onReady(() -> { calculationReady.set(true); runtime.signalCalculationReady(); });
            owner = runtime; planner = next; workers = nextWorkers; binding = nextBinding;
            continuations = new PedestrianPlanningWakeIndex();
            continuationBinding = PedestrianPlanningContinuations.bind(continuations);
            recoveryPlan = FrontierWorldProcessCatalog.preparePlanningWaits(FrontierWorldRuntimeDefinition.processRegistry(),
                    (FrontierWorldState) runtime.decodedState().orElseThrow(), runtime.executionView().orElseThrow());
        }
        if (owner != runtime) throw new IllegalStateException("pedestrian planning has a foreign runtime owner");
    }
    private static void recoverWaits(FrontierV3ServerRuntime<?, ?> runtime) {
        if (recoveryPlan != null) {
            var captured = recoveryPlan;
            var submitted = runtime.prepareReadOnly(() -> FrontierWorldState.duringUnpublishedTransition(() ->
                    io.farfrontier.palemirror.frontier.v3.model.ImmutablePlanningScope.within(captured)));
            if (submitted.isPresent()) { recovery = submitted.orElseThrow(); recoveryPlan = null; }
        }
        if (recovery == null || !recovery.isDone()) return;
        try { recovery.get().publish(runtime::retainsScheduledAction); recovery = null; }
        catch (InterruptedException error) {
            Thread.currentThread().interrupt(); throw new IllegalStateException("planning recovery interrupted", error);
        } catch (java.util.concurrent.ExecutionException error) {
            throw new IllegalStateException("planning recovery failed", error.getCause());
        }
    }
    /** Startup hints used to be rebuilt synchronously before the first execution.
     * Detached reconstruction retains that ordering without parking the server thread. */
    static boolean recovered(FrontierV3ServerRuntime<?, ?> runtime) {
        return owner == runtime && recoveryPlan == null && recovery == null;
    }
    static void admitReady(FrontierV3ServerRuntime<?, ?> runtime, PedestrianPlanningWakeIndex index) {
        if (index.waitingCount() == 0) return;
        var ready = index.ready(runtime.canonicalState().orElseThrow().instant(), runtime::retainsScheduledAction);
        if (ready.isEmpty()) return;
        int budget = runtime.commandAdmissionCapacity().orElseThrow().optionalCommands(MAX_WAKE_COMMANDS_PER_TURN);
        int admitted = 0;
        int commands = 0;
        for (var action : ready) {
            if (admitted == MAX_WAKE_COMMANDS_PER_TURN) break;
            if (runtime.signalScheduledReadiness(action)) {
                index.resumed(action, action);
                admitted++;
                continue;
            }
            // Only advancing a future canonical deadline requires a durable command.
            // Capacity/predecessor pressure here cannot block another already-due waiter.
            if (commands == budget || runtime.awaitingCanonicalPredecessor()) continue;
            var wake = new PedestrianPlanningReady(action);
            var continuation = wake.continuationAt(runtime.canonicalState().orElseThrow().instant());
            var result = FrontierV3CommandSubmission.submitResultBound(runtime, "pedestrian-planning-ready", action.subject().value(),
                    wake, action);
            if (result instanceof io.farfrontier.palemirror.frontier.v3.api.CommandResult.Rejected rejected) {
                var code = rejected.rejection().code();
                if (code == io.farfrontier.palemirror.frontier.v3.api.RejectionCode.RECEIPT_CAPACITY_EXHAUSTED
                        || code == io.farfrontier.palemirror.frontier.v3.api.RejectionCode.TRANSACTION_CAPACITY_EXHAUSTED) { commands = budget; continue; }
                throw new IllegalStateException("addressed planning wake rejected: " + result);
            }
            if (result instanceof io.farfrontier.palemirror.frontier.v3.api.CommandResult.Pending pending
                    && pending.reason() == io.farfrontier.palemirror.frontier.v3.api.CommandResult.Pending.Reason.PREDECESSOR) continue;
            runtime.afterCommand(result, () -> {
                if (!runtime.retainsScheduledAction(continuation))
                    throw new IllegalStateException("accepted planning wake lost its exact runnable generation");
                index.resumed(action, continuation);
            });
            admitted++; commands++;
        }
    }
    private static String continuationSamples() {
        var samples = new java.util.ArrayList<java.util.Map<String, Object>>();
        for (var wait : continuations.diagnostics(2)) {
            var requests = new java.util.ArrayList<java.util.Map<String, Object>>();
            for (var request : wait.requests().stream().limit(1).toList()) {
                var result = planner.peek(request.geometry(), request.start(), request.target(), request.extent());
                requests.add(java.util.Map.of("from", java.util.List.of(request.start().x(), request.start().y(), request.start().z()),
                        "to", java.util.List.of(request.target().x(), request.target().y(), request.target().z()),
                        "retained", result.map(value -> value.status().name() + ":" + value.reason().substring(0, Math.min(128, value.reason().length()))).orElse("NONE")));
            }
            samples.add(java.util.Map.of("action", wait.action().id().value(), "due", wait.action().dueAt().ticks(),
                    "parked", wait.parked(), "runnable", wait.runnable(), "ownerHeld", wait.ownerHeld(),
                    "origin", wait.origin(), "requests", requests));
        }
        return new com.google.gson.Gson().toJson(samples);
    }
    static synchronized java.util.Set<io.farfrontier.palemirror.frontier.v3.model.BlockPosition> knowledgeDemands(FrontierV3ServerRuntime<?, ?> runtime) {
        return owner == runtime ? planner.knowledgeDemands() : java.util.Set.of();
    }
    static synchronized void geometryObserved(FrontierV3ServerRuntime<?, ?> runtime,
            java.util.Set<io.farfrontier.palemirror.frontier.v3.model.BlockPosition> positions) {
        if (owner == runtime) planner.geometryObserved(positions);
    }
    static synchronized String diagnostic(FrontierV3ServerRuntime<?, ?> runtime) {
        return diagnostic(runtime, false);
    }
    static synchronized String diagnostic(FrontierV3ServerRuntime<?, ?> runtime, boolean details) {
        var capacity = runtime.commandAdmissionCapacity().orElseThrow();
        return "{\"waiting\":" + (owner == runtime ? continuations.waitingCount() : 0)
                + ",\"awaitingResultPublication\":" + (owner == runtime ? planner.awaitingPublicationCount() : 0)
                + ",\"retainedResults\":" + (owner == runtime ? planner.retainedResultCount() : 0)
                + ",\"protectedResults\":" + (owner == runtime ? planner.protectedResultCount() : 0)
                + ",\"readyDeferred\":" + (owner == runtime ? deferredReady : 0)
                + (details ? ",\"continuationSamples\":" + (owner == runtime ? continuationSamples() : "[]") : "")
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
                + ",\"recovering\":" + !recovered(runtime)
                + ",\"searchWorkUnits\":" + (owner == runtime ? planner.workUnits() : 0)
                + ",\"completedSearches\":" + (owner == runtime ? planner.completedCount() : 0)
                + ",\"searchProgressRevision\":" + (owner == runtime ? planner.progressRevision() : 0)
                + ",\"regionCache\":" + (owner == runtime ? new com.google.gson.Gson().toJson(planner.regionCacheDiagnostic()) : "null")
                + (details ? ",\"oldestRequests\":" + waitingDiagnostic(runtime)
                    + ",\"activeRequests\":" + (owner == runtime ? requestDiagnostic(planner.activeDiagnostics()) : "[]")
                    + ",\"geometryWaits\":" + knowledgeDiagnostic(runtime) : "")
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
        return requestDiagnostic(planner.waitingDiagnostics(MAX_WAKE_COMMANDS_PER_TURN / 2));
    }
    private static String requestDiagnostic(java.util.List<io.farfrontier.palemirror.frontier.v3.model.navigation.CooperativePedestrianPlanner.WaitingDiagnostic> requests) {
        return requests.stream().map(wait ->
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
        if (recovery != null) recovery.cancel(true);
        recovery = null; recoveryPlan = null;
        if (workTurn != null) workTurn.close(); workTurn = null; remainingHostNanos = 0; calculationReady.set(false);
        try { continuationBinding.close(); binding.close(); }
        catch (Exception error) { throw new IllegalStateException("cannot release pedestrian planner", error); }
        finally { planner.close(); workers.shutdownNow(); workers = null; binding = null; continuationBinding = null; continuations = null; planner = null; owner = null; deferredReady = 0; }
    }
}
