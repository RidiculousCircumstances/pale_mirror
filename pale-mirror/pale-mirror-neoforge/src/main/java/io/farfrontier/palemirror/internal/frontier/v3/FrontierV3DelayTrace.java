package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import jdk.jfr.*;
import java.util.concurrent.atomic.AtomicLong;

/** Optional bounded JFR evidence. No canonical state, retention store, scheduling or repair authority. */
final class FrontierV3DelayTrace {
    private static final java.lang.management.ThreadMXBean CPU = java.lang.management.ManagementFactory.getThreadMXBean();
    private static long cpu() {
        try { return CPU.isCurrentThreadCpuTimeSupported() && CPU.isThreadCpuTimeEnabled() ? CPU.getCurrentThreadCpuTime() : -1; }
        catch (RuntimeException unavailable) { return -1; }
    }
    private static final AtomicLong IDS = new AtomicLong();
    private static final AtomicLong EVENTS = new AtomicLong();
    private static final class Local {
        Context action;
        long cause, turn, advance;
    }
    private static final ThreadLocal<Local> CURRENT = ThreadLocal.withInitial(Local::new);
    private static final EventType POINTS = EventType.getEventType(Point.class);
    private static final EventType TURNS = EventType.getEventType(Turn.class);
    private static final EventType ATTEMPTS = EventType.getEventType(Attempt.class);
    private static final EventType STAGES = EventType.getEventType(Stage.class);
    private FrontierV3DelayTrace() { }

    @Name("pale_mirror.DelayPoint") @Label("Simulation dependency lifecycle") @Category("Pale Mirror") @StackTrace(false)
    static final class Point extends Event {
        long sequence, monotonicNanos, cpuNanos, id, parent, observedBy, causedBy, turn, advance, observedTurn, observedAdvance, revision, tick, value;
        String world, action, kind, subject, type, phase, reference, detail;
    }
    @Name("pale_mirror.ClockTurn") @Label("Simulation owner turn and following gate") @Category("Pale Mirror") @StackTrace(false)
    static final class Turn extends Event {
        long sequence, id, parent, previousTurn, previousAdvance, previousCause, startedNanos, endedNanos, startedCpuNanos, endedCpuNanos, beforeTick, afterTick, beforeDebt, debt, revision, dependency;
        int actionsLeft, weightLeft, classificationsLeft, executionsLeft;
        String world, gate, action, budgetCause;
    }
    @Name("pale_mirror.ClockAttempt") @Label("Requested and achieved simulation advance") @Category("Pale Mirror") @StackTrace(false)
    static final class Attempt extends Event {
        long sequence, id, parent, turn, previousAdvance, previousCause, startedNanos, endedNanos, startedCpuNanos, endedCpuNanos, beforeTick, afterTick, targetTick, debt;
        int actions, weight, classifications, executions, transactions;
        int actionsLimit, weightLimit, classificationsLimit, executionsLimit;
        String world, deferred;
    }
    @Name("pale_mirror.ExecutionStage") @Label("Correlated simulation execution stage") @Category("Pale Mirror") @StackTrace(false)
    static final class Stage extends Event {
        long sequence, startedNanos, endedNanos, startedCpuNanos, endedCpuNanos, attempt, turn, advance, revision, tick;
        String action, world, stage, kind, subject;
    }
    @Name("pale_mirror.SchedulePressure") @Label("Exact schedule pressure") @Category("Pale Mirror") @StackTrace(false)
    static final class Pressure extends Event {
        long monotonicNanos, tick, readyLag, deadlineLag;
        int total, ready, held, future, checking;
        String world;
    }
    private static final EventType PRESSURE = EventType.getEventType(Pressure.class);
    static void pressure(String world, FrontierExecutionMetrics.QueuePressure cut) {
        try {
            if (!PRESSURE.isEnabled()) return;
            var event = new Pressure(); event.monotonicNanos = System.nanoTime(); event.world = world;
            event.tick = cut.observedAtTicks(); event.total = cut.total(); event.ready = cut.ready();
            event.held = cut.held(); event.future = cut.future(); event.checking = cut.checking();
            event.readyLag = cut.oldestReadyLagTicks(); event.deadlineLag = cut.oldestDeadlineLagTicks(); event.commit();
        } catch (RuntimeException ignored) { }
    }
    static void appended(TransactionRecord record, long sequence) {
        try {
            if (!POINTS.isEnabled()) return;
            var event = new Point(); event.sequence = EVENTS.incrementAndGet(); event.monotonicNanos = System.nanoTime();
            event.type = "TRANSACTION"; event.phase = "APPENDED"; event.world = record.worldId().value();
            event.revision = record.revision().value(); event.tick = record.instant().ticks();
            var local = CURRENT.get(); event.parent = local.cause; event.causedBy = local.cause;
            event.turn = local.turn; event.advance = local.advance;
            event.reference = record.id().value(); event.value = sequence; event.commit();
        } catch (RuntimeException ignored) { }
    }
    record Context(long attempt, String world, ScheduledAction action, long revision, long tick) { }

    static FrontierExecutionMetrics.Span action(WorldId world, ScheduledAction action, Revision revision, SimInstant at) {
        if (!POINTS.isEnabled() && !STAGES.isEnabled()) return () -> { };
        var local = CURRENT.get(); var previous = local.action; long previousCause = local.cause;
        var context = new Context(IDS.incrementAndGet(), world.value(), action, revision.value(), at.ticks());
        local.action = context; local.cause = context.attempt();
        point(context, context.attempt(), previousCause, "ACTION", "BEGIN", "", 0);
        return () -> {
            try { point(context, context.attempt(), previousCause, "ACTION", "END", "", 0); }
            finally { local.action = previous; local.cause = previousCause; }
        };
    }

    static boolean stagesEnabled() { return STAGES.isEnabled(); }
    static Stage stage(FrontierExecutionMetrics.Stage stage, String kind, String subject) {
        if (!STAGES.isEnabled()) return null;
        var event = new Stage(); var local = CURRENT.get(); var context = local.action;
        event.turn = local.turn; event.advance = local.advance;
        event.sequence = EVENTS.incrementAndGet(); event.startedNanos = System.nanoTime();
        event.stage = stage.name(); event.kind = kind; event.subject = subject; event.startedCpuNanos = cpu();
        if (context != null) {
            event.attempt = context.attempt(); event.action = context.action().id().value();
            event.revision = context.revision(); event.tick = context.tick(); event.world = context.world();
        }
        event.begin(); return event;
    }
    static void close(Stage event) {
        if (event == null) return;
        try { event.endedNanos = System.nanoTime(); event.endedCpuNanos = cpu(); event.end(); event.commit(); } catch (RuntimeException ignored) { }
    }
    /** Adapter-only worker envelope; no knowledge of navigation or scheduling policy. */
    static java.util.concurrent.Executor executor(java.util.concurrent.Executor delegate, String type, String world) {
        return work -> {
            var trace = task(type, world);
            if (trace.id == 0) { delegate.execute(work); return; }
            trace.mark("QUEUED");
            try {
                delegate.execute(() -> {
                    trace.run(() -> {
                        trace.mark("RUNNING");
                        try { work.run(); trace.mark("COMPUTED"); }
                        catch (RuntimeException | Error failure) { trace.mark("FAILED"); throw failure; }
                    });
                });
            } catch (RuntimeException failure) { trace.mark("QUEUE_BUSY"); throw failure; }
        };
    }
    static Task task(String type, String world) {
        try { if (!POINTS.isEnabled()) return Task.OFF;
            var local = CURRENT.get();
            return new Task(IDS.incrementAndGet(), type, local.action, world, local.cause, local.turn, local.advance); }
        catch (RuntimeException ignored) { return Task.OFF; }
    }
    static final class Task {
        static final Task OFF = new Task(0, "", null, "", 0, 0, 0);
        final long id;
        final String type, world;
        final Context context;
        final long parent, turn, advance;
        Task(long id, String type, Context context, String world, long parent, long turn, long advance) {
            this.id = id; this.type = type; this.context = context; this.world = world;
            this.parent = parent; this.turn = turn; this.advance = advance;
        }
        void run(Runnable action) {
            if (id == 0) { action.run(); return; }
            var local = CURRENT.get(); var oldAction = local.action;
            long oldCause = local.cause, oldTurn = local.turn, oldAdvance = local.advance;
            local.action = context; local.cause = id; local.turn = turn; local.advance = advance;
            try { action.run(); }
            finally { local.action = oldAction; local.cause = oldCause; local.turn = oldTurn; local.advance = oldAdvance; }
        }
        void deliver(Runnable action) {
            if (id == 0) { action.run(); return; }
            var local = CURRENT.get(); var oldAction = local.action;
            long oldCause = local.cause, oldTurn = local.turn, oldAdvance = local.advance;
            local.action = null; local.cause = id; local.turn = 0; local.advance = 0;
            try { action.run(); }
            finally { local.action = oldAction; local.cause = oldCause; local.turn = oldTurn; local.advance = oldAdvance; }
        }
        void notifyReady(Runnable ready) { run(() -> { mark("NOTIFY_BEGIN"); ready.run(); }); }
        void mark(String phase) { mark(phase, "", 0); }
        void mark(String phase, String reference, long value) {
            if (id != 0) point(context, id, parent, type, phase, reference, value, world, turn, advance);
        }
        boolean matches(ScheduledAction action, long revision) {
            return context != null && context.action().equals(action) && context.revision() == revision;
        }
    }
    private static void point(Context context, long id, long parent, String type, String phase, String reference, long value) {
        point(context, id, parent, type, phase, reference, value, "unbound");
    }
    private static void point(Context context, long id, long parent, String type, String phase, String reference, long value, String world) {
        if (!POINTS.isEnabled()) return;
        var local = CURRENT.get();
        point(context, id, parent, type, phase, reference, value, world, local.turn, local.advance);
    }
    private static void point(Context context, long id, long parent, String type, String phase, String reference, long value, String world, long turn, long advance) {
        try {
            if (!POINTS.isEnabled()) return;
            var event = new Point(); event.sequence = EVENTS.incrementAndGet(); event.monotonicNanos = System.nanoTime();
            event.cpuNanos = phase.equals("RUNNING") || phase.equals("COMPUTED") || phase.equals("FAILED")
                    || phase.startsWith("OWNER_COMPLETION_") ? cpu() : -1;
            event.world = world; event.id = id; event.parent = parent; event.type = type; event.phase = phase; event.reference = reference; event.value = value;
            var local = CURRENT.get(); var observer = local.action;
            event.observedBy = observer == null ? 0 : observer.attempt(); event.causedBy = local.cause;
            event.turn = turn; event.advance = advance; event.observedTurn = local.turn; event.observedAdvance = local.advance;
            if (context != null) {
                event.world = context.world(); event.action = context.action().id().value(); event.kind = context.action().kind();
                event.subject = context.action().subject().value(); event.revision = context.revision(); event.tick = context.tick();
            }
            event.commit();
        } catch (RuntimeException ignored) { }
    }
    static void transaction(TransactionRecord record, boolean published) {
        try {
            if (!POINTS.isEnabled()) return;
            var event = new Point(); var local = CURRENT.get(); var context = local.action;
            event.causedBy = local.cause; event.turn = local.turn; event.advance = local.advance;
            event.sequence = EVENTS.incrementAndGet(); event.monotonicNanos = System.nanoTime();
            event.type = "TRANSACTION"; event.phase = published ? "PUBLISHED" : "PREPARED";
            event.world = record.worldId().value(); event.revision = record.revision().value(); event.tick = record.instant().ticks();
            event.reference = record.id().value();
            event.detail = record.events().stream().anyMatch(e -> e.payload().requiresDurableBeforeEffect()) ? "DURABLE_BEFORE_EFFECT" : "BATCHABLE";
            if (context != null) {
                event.parent = context.attempt(); event.action = context.action().id().value();
                event.kind = context.action().kind(); event.subject = context.action().subject().value();
            }
            event.commit();
        } catch (RuntimeException ignored) { }
    }
    @Name("pale_mirror.ClockState") @Label("Observed clock gate change") @Category("Pale Mirror") @StackTrace(false)
    static final class State extends Event {
        long sequence, monotonicNanos, tick, debt, dependency, turn, advance;
        boolean allowance, conditionsKnown, exactPending, commitPending, walPending;
        String world, gate, budgetCause, reason;
    }
    private static final EventType STATES = EventType.getEventType(State.class);
    static boolean statesEnabled() { return STATES.isEnabled(); }
    static void state(String world, long tick, long debt, String gate, long dependency, String budgetCause, boolean allowance, String reason, boolean exact, boolean commit, boolean wal) {
        try {
            if (!STATES.isEnabled()) return;
            var e = new State(); var local = CURRENT.get();
            e.sequence = EVENTS.incrementAndGet(); e.monotonicNanos = System.nanoTime();
            e.world = world; e.tick = tick; e.debt = debt; e.gate = gate; e.dependency = dependency;
            e.turn = local.turn; e.advance = local.advance; e.budgetCause = budgetCause; e.allowance = allowance; e.reason = reason;
            e.conditionsKnown = !reason.equals("TURN_BEGIN"); e.exactPending = exact; e.commitPending = commit; e.walPending = wal; e.commit();
        } catch (RuntimeException ignored) { }
    }
    static void walObserved(long task, String world, long through) {
        point(null, task, 0, "WAL", "RECEIPT_OBSERVED", world, through, world);
    }
    static boolean turnsEnabled() { try { return TURNS.isEnabled(); } catch (RuntimeException ignored) { return false; } }
    static Turn turn(String world, long tick) { return turn(world, tick, -1); }
    static Turn turn(String world, long tick, long debt) {
        try {
            if (!TURNS.isEnabled()) return null;
            var event = new Turn(); var local = CURRENT.get();
            event.id = IDS.incrementAndGet(); event.parent = local.cause;
            event.previousTurn = local.turn; event.previousAdvance = local.advance; event.previousCause = local.cause;
            local.turn = event.id; local.advance = 0; local.cause = event.id;
            event.sequence = EVENTS.incrementAndGet(); event.startedNanos = System.nanoTime();
            event.world = world; event.beforeTick = tick; event.beforeDebt = debt; event.startedCpuNanos = cpu();
            state(world, tick, debt, "OWNER_RUNNING", 0, "", false, "TURN_BEGIN", false, false, false);
            event.begin(); return event;
        } catch (RuntimeException ignored) { return null; }
    }
    static void close(Turn event, long tick, long debt, long revision, String gate, long dependency,
                      ScheduledAction action, WorkBudget budget, String budgetCause) {
        if (event == null) return;
        try {
            event.endedNanos = System.nanoTime(); event.endedCpuNanos = cpu(); event.afterTick = tick; event.debt = debt; event.revision = revision;
            event.gate = gate; event.dependency = dependency; event.action = action == null ? "" : action.id().value();
            event.budgetCause = budgetCause;
            if (budget != null) {
                event.actionsLeft = budget.maxActions(); event.weightLeft = budget.maxWeight();
                event.classificationsLeft = budget.maxClassificationEvaluations(); event.executionsLeft = budget.maxExecutionEvaluations();
            }
            event.end(); event.commit();
        } catch (RuntimeException ignored) { }
        finally { var local = CURRENT.get(); local.turn = event.previousTurn; local.advance = event.previousAdvance; local.cause = event.previousCause; }
    }
    static Attempt attempt(String world, long tick, long target, WorkBudget budget) {
        try {
        if (!ATTEMPTS.isEnabled()) return null;
        var event = new Attempt(); var local = CURRENT.get();
        event.id = IDS.incrementAndGet(); event.parent = local.cause; event.turn = local.turn;
        event.previousAdvance = local.advance; event.previousCause = local.cause;
        local.advance = event.id; local.cause = event.id;
        event.sequence = EVENTS.incrementAndGet(); event.startedNanos = System.nanoTime();
        event.world = world; event.beforeTick = tick; event.targetTick = target; event.startedCpuNanos = cpu();
        event.actionsLimit = budget.maxActions(); event.weightLimit = budget.maxWeight();
        event.classificationsLimit = budget.maxClassificationEvaluations(); event.executionsLimit = budget.maxExecutionEvaluations();
        event.begin(); return event;
        } catch (RuntimeException ignored) { return null; }
    }
    static void close(Attempt event, long tick, long debt, AdvanceResult result) {
        if (event == null) return;
        try {
            event.endedNanos = System.nanoTime(); event.endedCpuNanos = cpu(); event.afterTick = tick; event.debt = debt;
            if (result != null) {
                event.transactions = result.transactions().size();
                var used = result.consumed(); event.actions = used.actions(); event.weight = used.weight();
                event.classifications = used.classifications(); event.executions = used.executions();
                event.deferred = result.deferredAction().map(action -> action.id().value()).orElse("");
            }
            event.end(); event.commit();
        } catch (RuntimeException ignored) { }
        finally { var local = CURRENT.get(); local.advance = event.previousAdvance; local.cause = event.previousCause; }
    }
}
