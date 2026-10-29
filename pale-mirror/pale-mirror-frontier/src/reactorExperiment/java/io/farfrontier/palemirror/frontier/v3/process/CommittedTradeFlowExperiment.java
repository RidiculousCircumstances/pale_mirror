package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.*;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/** Library-selection diagnostic, not a production scheduler or dependency graph.
 * Conservative invalidation on each accepted transaction is deliberate: both
 * adapters receive identical complete inputs. No canonical event is coalesced.
 * Deterministic canonical-tick dispatch barriers make work budgets comparable. */
public final class CommittedTradeFlowExperiment {
    private static final com.sun.management.ThreadMXBean CPU =
            (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
    private static final PedestrianRoutePlanner PENDING = (geometry, start, target) ->
            new PedestrianRouteResult(PedestrianRouteResult.Status.PLANNING, List.of(), 0L, 0,
                    "EXPERIMENT_NO_COMPLETED_ROUTE");
    // Geometry wrappers are rebuilt per preparation. Compare the FULL declared
    // terrain/profile version, scope/bounds and endpoints, not wrapper identity.
    // This is a diagnostic answer, never a route cache key or movement authority.
    record Demand(Object version, WorldBounds bounds, SurfaceAnchor start, SurfaceAnchor target,
                  PedestrianRouteExtent extent, PedestrianPlanningService service) { }
    record Answer(GoodsTripPlanning.Choice choice, String decision, boolean awaiting, int events, Set<Demand> demands) { }
    static Answer calculate(CommittedTradeCorpus.Frame frame, SubjectId key) {
        var state = frame.state(); var participant = state.companies().goodsTrade().participants().participants().get(key);
        if (participant == null) throw new IllegalStateException("observed participant removed");
        var preparation = FrontierWorldState.duringUnpublishedTransition(() ->
                PedestrianRoutePlanning.prepare(PENDING, () -> GoodsTripPlanning.plan(state, participant, frame.tick())));
        var result = preparation.observation().result();
        var demands = new HashSet<Demand>();
        preparation.demands().forEach((request, service) -> demands.add(new Demand(request.version(), request.geometry().bounds(),
                request.start(), request.target(), request.extent(), service)));
        return new Answer(result.choice(), result.decision(), result.awaitingNavigation(), result.events().size(), Set.copyOf(demands));
    }
    /** Host-owned bounded dispatch: notices arrive in accepted order, workers run
     * at canonical tick barriers. No sleeps, timer/debounce or unbounded pool. */
    static final class Dispatch implements Executor, AutoCloseable {
        final ArrayDeque<Runnable> pending = new ArrayDeque<>();
        final ExecutorService workers = Executors.newFixedThreadPool(2);
        final AtomicLong workerCpu = new AtomicLong(), workerBytes = new AtomicLong();
        int peak;
        public synchronized void execute(Runnable work) {
            if (pending.size() == 8) throw new RejectedExecutionException("experiment dispatch bound");
            pending.add(work); peak = Math.max(peak, pending.size());
        }
        void flush() throws Exception {
            for (;;) {
                var batch = new ArrayList<Runnable>();
                synchronized (this) { for (int i = 0; i < 2 && !pending.isEmpty(); i++) batch.add(pending.remove()); }
                if (batch.isEmpty()) return;
                var futures = batch.stream().map(work -> workers.submit(() -> {
                    long cpu = CPU.getCurrentThreadCpuTime();
                    long bytes = CPU.getThreadAllocatedBytes(Thread.currentThread().threadId());
                    try { work.run(); }
                    finally {
                        workerCpu.addAndGet(CPU.getCurrentThreadCpuTime() - cpu);
                        workerBytes.addAndGet(CPU.getThreadAllocatedBytes(Thread.currentThread().threadId()) - bytes);
                    }
                })).toList();
                for (var future : futures) future.get(60, TimeUnit.SECONDS);
            }
        }
        public void close() throws Exception {
            workers.shutdown();
            if (!workers.awaitTermination(60, TimeUnit.SECONDS)) {
                workers.shutdownNow(); throw new IllegalStateException("experiment worker did not stop");
            }
        }
    }
    interface Flow extends AutoCloseable {
        void notice(SubjectId key);
        void close();
    }
    static final class Run implements AutoCloseable {
        final List<SubjectId> keys;
        final Dispatch dispatch = new Dispatch();
        final Map<SubjectId, CommittedTradeCorpus.Frame> publishedAt = new HashMap<>();
        final Map<SubjectId, Answer> answers = new HashMap<>();
        final AtomicLong calculationCpu = new AtomicLong(), calculationBytes = new AtomicLong(), calculationWall = new AtomicLong();
        volatile CommittedTradeCorpus.Frame latest;
        volatile boolean closed;
        Consumer<SubjectId> beforeCalculation = key -> { };
        long notices, calls, skipped, stale, failures, publications, dispatches;
        final Flow flow;
        Run(String mode, List<SubjectId> keys) {
            this.keys = keys;
            flow = mode.equals("JAVA") ? javaFlow() : reactorFlow();
        }
        void accept(CommittedTradeCorpus.Frame frame) {
            if (closed) throw new IllegalStateException("closed experiment scope");
            latest = frame;
            for (var key : keys) { notices++; flow.notice(key); }
        }
        void execute(SubjectId key) {
            CommittedTradeCorpus.Frame input;
            synchronized (this) {
                dispatches++;
                input = latest; if (closed) return;
                if (publishedAt.get(key) == input) { skipped++; return; }
                calls++;
            }
            long cpu = CPU.getCurrentThreadCpuTime(), bytes = CPU.getThreadAllocatedBytes(Thread.currentThread().threadId());
            long start = System.nanoTime();
            try {
                beforeCalculation.accept(key);
                Answer answer = calculate(input, key);
                synchronized (this) {
                    if (closed || latest != input) { stale++; return; }
                    answers.put(key, answer); publishedAt.put(key, input); publications++;
                }
            } catch (RuntimeException failure) {
                synchronized (this) { failures++; }
                throw failure;
            } finally {
                calculationCpu.addAndGet(CPU.getCurrentThreadCpuTime() - cpu);
                calculationBytes.addAndGet(CPU.getThreadAllocatedBytes(Thread.currentThread().threadId()) - bytes);
                calculationWall.addAndGet(System.nanoTime() - start);
            }
        }
        Flow javaFlow() {
            return new Flow() {
                final Set<SubjectId> active = new HashSet<>();
                public synchronized void notice(SubjectId key) {
                    if (!active.add(key)) return;
                    dispatch.execute(() -> {
                        boolean completed = false;
                        try { execute(key); completed = true; }
                        finally {
                            synchronized (this) {
                                active.remove(key);
                                // A stale completion leaves latest dirty. Errors
                                // require an explicit retry notice, never a spin.
                                boolean dirty;
                                synchronized (Run.this) { dirty = publishedAt.get(key) != latest; }
                                if (completed && !closed && dirty) notice(key);
                            }
                        }
                    });
                }
                public synchronized void close() { active.clear(); }
            };
        }
        Flow reactorFlow() {
            var scheduler = Schedulers.fromExecutor(dispatch::execute);
            var sinks = new HashMap<SubjectId, Sinks.Many<SubjectId>>();
            var subscriptions = new ArrayList<Disposable>();
            for (var key : keys) {
                var sink = Sinks.many().unicast().<SubjectId>onBackpressureBuffer(new ArrayBlockingQueue<>(1));
                sinks.put(key, sink);
                subscriptions.add(sink.asFlux().onBackpressureLatest()
                        .concatMap(ignored -> Mono.<Void>fromRunnable(() -> execute(key)).subscribeOn(scheduler)
                                .onErrorResume(error -> Mono.empty()), 0).subscribe());
            }
            return new Flow() {
                public void notice(SubjectId key) {
                    var status = sinks.get(key).tryEmitNext(key);
                    if (status != Sinks.EmitResult.OK) throw new IllegalStateException("notification emission: " + status);
                }
                public void close() {
                    subscriptions.forEach(Disposable::dispose); scheduler.dispose();
                    // Scheduler wraps execute only: the experiment owns the host.
                    if (dispatch.workers.isShutdown()) throw new AssertionError("Reactor shut down host executor");
                }
            };
        }
        public void close() throws Exception { closed = true; flow.close(); dispatch.close(); }
    }
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("usage: <captured corpus directory> <output directory>");
        var output = Path.of(args[1]); Files.createDirectories(output);
        var corpus = CommittedTradeCorpus.read(Path.of(args[0]));
        var frames = corpus.frames();
        var people = frames.getFirst().state().companies().goodsTrade().participants().participants().values().stream()
                .filter(p -> p.policy() == GoodsPolicyKind.PUBLIC_SETTLEMENT).sorted(Comparator.comparing(p -> p.party().id())).toList();
        check(people.size() == 100, "requires real 100-town corpus");
        // Diagnostic sampling only, not domain dispatch by spelling/namespace.
        var keys = List.of(people.get(9).party().id(), people.get(20).party().id(), people.get(81).party().id(), people.get(8).party().id());
        var ticks = new ArrayList<List<CommittedTradeCorpus.Frame>>();
        for (var frame : frames) {
            if (ticks.isEmpty() || ticks.getLast().getLast().tick() != frame.tick()) ticks.add(new ArrayList<>());
            ticks.getLast().add(frame);
        }
        String identity = "snapshot=" + corpus.snapshot() + "\ncovered=" + corpus.covered() + "\ntransactions=" + frames.size()
                + "\nevents=" + corpus.transactions().stream().mapToInt(t -> t.events().size()).sum()
                + "\nfirstTick=" + frames.getFirst().tick() + "\nlastTick=" + frames.getLast().tick() + "\nbarriers=" + ticks.size()
                + "\nparticipants=" + keys + "\nreplay_ns=" + corpus.replayNanos() + "\njava=" + System.getProperty("java.runtime.version")
                + "\nreactor=3.8.8\nscope=offline canonical-tick dispatch, conservative accepted-transaction invalidation; no native scheduling/TPS claim\n";
        Files.writeString(output.resolve("identity.txt"), identity); System.out.println(identity);
        // Uncached direct oracle at EVERY tick boundary. Includes full choice,
        // decision, draft-event count and actual navigation demands, not a flag.
        var oracle = new ArrayList<Map<SubjectId, Answer>>();
        var answerRows = new ArrayList<String>();
        for (var tick : ticks) {
            var answers = new HashMap<SubjectId, Answer>();
            for (var key : keys) {
                var answer = calculate(tick.getLast(), key); answers.put(key, answer);
                answerRows.add(tick.getLast().tick() + "\t" + key.value() + "\t" + answer);
            }
            oracle.add(Map.copyOf(answers));
        }
        Files.write(output.resolve("answers.tsv"), answerRows);
        var rows = new ArrayList<String>();
        rows.add("mode,round,barriers,notices,calls,publications,skipped,stale,failures,dispatches,peak_queue,wall_ns,caller_cpu_ns,caller_bytes,worker_cpu_ns,worker_bytes,calculation_cpu_ns,calculation_bytes,calculation_wall_ns");
        for (int round = -1; round < 3; round++) {
            for (String mode : round % 2 == 0 ? List.of("JAVA", "REACTOR") : List.of("REACTOR", "JAVA")) {
                try (var run = new Run(mode, keys)) {
                    var observed = new ArrayList<Map<SubjectId, Answer>>();
                    long cpu = CPU.getCurrentThreadCpuTime(), bytes = CPU.getThreadAllocatedBytes(Thread.currentThread().threadId());
                    long start = System.nanoTime();
                    for (var tick : ticks) {
                        for (var frame : tick) run.accept(frame);
                        run.dispatch.flush();
                        observed.add(Map.copyOf(run.answers));
                        check(run.failures == 0 && run.stale == 0, "unexpected calculation failure/stale at gated barrier");
                    }
                    String row = mode + "," + round + "," + ticks.size() + "," + run.notices + "," + run.calls + "," + run.publications
                            + "," + run.skipped + "," + run.stale + "," + run.failures + "," + run.dispatches + "," + run.dispatch.peak
                            + "," + (System.nanoTime() - start) + "," + (CPU.getCurrentThreadCpuTime() - cpu)
                            + "," + (CPU.getThreadAllocatedBytes(Thread.currentThread().threadId()) - bytes)
                            + "," + run.dispatch.workerCpu + "," + run.dispatch.workerBytes
                            + "," + run.calculationCpu + "," + run.calculationBytes + "," + run.calculationWall;
                    // Oracle verification is experiment-only work, not runtime
                    // publication/admission. Keep its potentially deep geometry
                    // comparison out of BOTH measured processing paths.
                    check(observed.equals(oracle), mode + " differs from direct oracle at accepted tick");
                    check(run.publications == (long) keys.size() * ticks.size(), "equal-valued generations lost fresh evidence");
                    System.out.println(row);
                    if (round >= 0) { rows.add(row); Files.write(output.resolve("measurements.csv"), rows); }
                }
            }
        }
        var checks = new ArrayList<String>();
        for (String mode : List.of("JAVA", "REACTOR")) semantics(mode, keys.getFirst(), frames.getFirst(), frames.getLast(), checks);
        Files.write(output.resolve("semantic-checks.txt"), checks); checks.forEach(System.out::println);
    }
    static void semantics(String mode, SubjectId key, CommittedTradeCorpus.Frame first,
                          CommittedTradeCorpus.Frame last, List<String> checks) throws Exception {
        try (var run = new Run(mode, List.of(key))) {
            run.accept(first);
            // Deterministic delayed real calculation: a newer ACCEPTED image
            // arrives after input capture. Neither adapter may publish it late.
            run.beforeCalculation = ignored -> { run.beforeCalculation = k -> { }; run.accept(last); };
            run.dispatch.flush();
            check(run.stale == 1 && run.publications == 1 && run.calls == 2, mode + " lost stale/latest handling");
            check(run.answers.get(key).equals(calculate(last, key)), mode + " latest result mismatch");
            checks.add(mode + ": delayed captured result rejected; latest accepted image recalculated; exact answer matched");
        }
        try (var run = new Run(mode, List.of(key))) {
            run.beforeCalculation = ignored -> { throw new IllegalStateException("injected calculation failure"); };
            run.accept(first);
            try { run.dispatch.flush(); } catch (ExecutionException expected) {
                check(mode.equals("JAVA") && expected.getCause() instanceof IllegalStateException, "unexpected failure");
            }
            check(run.failures == 1 && run.publications == 0, "error became a domain answer");
            run.beforeCalculation = ignored -> { }; run.accept(last); run.dispatch.flush();
            check(run.publications == 1 && run.answers.get(key).equals(calculate(last, key)), "explicit retry failed");
            checks.add(mode + ": calculation error published nothing; explicit retry succeeded");
        }
        try (var run = new Run(mode, List.of(key))) {
            run.accept(first); run.beforeCalculation = ignored -> { run.closed = true; run.flow.close(); };
            run.dispatch.flush(); check(run.publications == 0 && run.stale == 1, "closed scope published late answer");
            try { run.accept(last); throw new AssertionError("closed scope accepted input"); }
            catch (IllegalStateException expected) { }
            check(!run.dispatch.workers.isShutdown(), "adapter owns host executor");
            checks.add(mode + ": close during real calculation rejected late answer; host executor retained");
        }
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
