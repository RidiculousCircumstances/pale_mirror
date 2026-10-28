package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import jdk.jfr.*;
import java.util.concurrent.atomic.AtomicLong;

/** Optional bounded JFR evidence. No canonical state, retention store, scheduling or repair authority. */
final class FrontierV3DelayTrace {
    private static final AtomicLong IDS = new AtomicLong();
    private static final AtomicLong EVENTS = new AtomicLong();
    private static final ThreadLocal<Context> CURRENT = new ThreadLocal<>();
    private static final EventType POINTS = EventType.getEventType(Point.class);
    private static final EventType TURNS = EventType.getEventType(Turn.class);
    private static final EventType ATTEMPTS = EventType.getEventType(Attempt.class);
    private static final EventType STAGES = EventType.getEventType(Stage.class);
    private FrontierV3DelayTrace() { }

    @Name("pale_mirror.DelayPoint") @Label("Simulation dependency lifecycle") @Category("Pale Mirror") @StackTrace(false)
    static final class Point extends Event {
        long sequence, monotonicNanos, id, parent, observedBy, revision, tick, value;
        String world, action, kind, subject, type, phase, reference, detail;
    }
    @Name("pale_mirror.ClockTurn") @Label("Simulation owner turn and following gate") @Category("Pale Mirror") @StackTrace(false)
    static final class Turn extends Event {
        long sequence, startedNanos, endedNanos, beforeTick, afterTick, debt, revision, dependency;
        int actionsLeft, weightLeft, classificationsLeft, executionsLeft;
        String world, gate, action, budgetCause;
    }
    @Name("pale_mirror.ClockAttempt") @Label("Requested and achieved simulation advance") @Category("Pale Mirror") @StackTrace(false)
    static final class Attempt extends Event {
        long sequence, startedNanos, endedNanos, beforeTick, afterTick, targetTick, debt;
        int actions, weight, classifications, executions, transactions;
        int actionsLimit, weightLimit, classificationsLimit, executionsLimit;
        String world, deferred;
    }
    @Name("pale_mirror.ExecutionStage") @Label("Correlated simulation execution stage") @Category("Pale Mirror") @StackTrace(false)
    static final class Stage extends Event {
        long sequence, startedNanos, endedNanos, attempt, revision, tick;
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
            event.reference = record.id().value(); event.value = sequence; event.commit();
        } catch (RuntimeException ignored) { }
    }
    record Context(long attempt, String world, ScheduledAction action, long revision, long tick) { }

    static FrontierExecutionMetrics.Span action(WorldId world, ScheduledAction action, Revision revision, SimInstant at) {
        if (!POINTS.isEnabled() && !STAGES.isEnabled()) return () -> { };
        var previous = CURRENT.get();
        // Caller supplies the nominal scheduled action. Nothing is decoded from its ID.
        var context = new Context(IDS.incrementAndGet(), world.value(), action, revision.value(), at.ticks());
        CURRENT.set(context);
        point(context, context.attempt(), 0, "ACTION", "BEGIN", "", 0);
        return () -> {
            try { point(context, context.attempt(), 0, "ACTION", "END", "", 0); }
            finally { if (previous == null) CURRENT.remove(); else CURRENT.set(previous); }
        };
    }
    static Stage stage(FrontierExecutionMetrics.Stage stage, String kind, String subject) {
        if (!STAGES.isEnabled()) return null;
        var event = new Stage(); var context = CURRENT.get();
        event.sequence = EVENTS.incrementAndGet(); event.startedNanos = System.nanoTime();
        event.stage = stage.name(); event.kind = kind; event.subject = subject;
        if (context != null) {
            event.attempt = context.attempt(); event.action = context.action().id().value();
            event.revision = context.revision(); event.tick = context.tick(); event.world = context.world();
        }
        event.begin(); return event;
    }
    static void close(Stage event) {
        if (event == null) return;
        try { event.endedNanos = System.nanoTime(); event.end(); event.commit(); } catch (RuntimeException ignored) { }
    }
    /** Adapter-only worker envelope; no knowledge of navigation or scheduling policy. */
    static java.util.concurrent.Executor executor(java.util.concurrent.Executor delegate, String type, String world) {
        return work -> {
            var trace = task(type, world);
            if (trace.id == 0) { delegate.execute(work); return; }
            trace.mark("QUEUED");
            try {
                delegate.execute(() -> {
                    trace.mark("RUNNING");
                    try { work.run(); } finally { trace.mark("COMPUTED"); }
                });
            } catch (RuntimeException failure) { trace.mark("QUEUE_BUSY"); throw failure; }
        };
    }
    static Task task(String type, String world) {
        try { return POINTS.isEnabled() ? new Task(IDS.incrementAndGet(), type, CURRENT.get(), world) : Task.OFF; }
        catch (RuntimeException ignored) { return Task.OFF; }
    }
    static final class Task {
        static final Task OFF = new Task(0, "", null, "");
        final long id;
        final String type, world;
        final Context context;
        Task(long id, String type, Context context, String world) { this.id = id; this.type = type; this.context = context; this.world = world; }
        void mark(String phase) { mark(phase, "", 0); }
        void mark(String phase, String reference, long value) {
            if (id != 0) point(context, id, context == null ? 0 : context.attempt(), type, phase, reference, value, world);
        }
        boolean matches(ScheduledAction action, long revision) {
            return context != null && context.action().equals(action) && context.revision() == revision;
        }
    }
    private static void point(Context context, long id, long parent, String type, String phase, String reference, long value) {
        point(context, id, parent, type, phase, reference, value, "unbound");
    }
    private static void point(Context context, long id, long parent, String type, String phase, String reference, long value, String world) {
        try {
            if (!POINTS.isEnabled()) return;
            var event = new Point(); event.sequence = EVENTS.incrementAndGet(); event.monotonicNanos = System.nanoTime();
            event.world = world; event.id = id; event.parent = parent; event.type = type; event.phase = phase; event.reference = reference; event.value = value;
            var observer = CURRENT.get(); event.observedBy = observer == null ? 0 : observer.attempt();
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
            var event = new Point(); var context = CURRENT.get();
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
    static boolean turnsEnabled() { try { return TURNS.isEnabled(); } catch (RuntimeException ignored) { return false; } }
    static Turn turn(String world, long tick) {
        try {
            if (!TURNS.isEnabled()) return null;
            var event = new Turn(); event.sequence = EVENTS.incrementAndGet(); event.startedNanos = System.nanoTime();
            event.world = world; event.beforeTick = tick; event.begin(); return event;
        } catch (RuntimeException ignored) { return null; }
    }
    static void close(Turn event, long tick, long debt, long revision, String gate, long dependency,
                      ScheduledAction action, WorkBudget budget, String budgetCause) {
        if (event == null) return;
        try {
            event.endedNanos = System.nanoTime(); event.afterTick = tick; event.debt = debt; event.revision = revision;
            event.gate = gate; event.dependency = dependency; event.action = action == null ? "" : action.id().value();
            event.budgetCause = budgetCause;
            if (budget != null) {
                event.actionsLeft = budget.maxActions(); event.weightLeft = budget.maxWeight();
                event.classificationsLeft = budget.maxClassificationEvaluations(); event.executionsLeft = budget.maxExecutionEvaluations();
            }
            event.end(); event.commit();
        } catch (RuntimeException ignored) { }
    }
    static Attempt attempt(String world, long tick, long target, WorkBudget budget) {
        try {
        if (!ATTEMPTS.isEnabled()) return null;
        var event = new Attempt(); event.sequence = EVENTS.incrementAndGet(); event.startedNanos = System.nanoTime();
        event.world = world; event.beforeTick = tick; event.targetTick = target;
        event.actionsLimit = budget.maxActions(); event.weightLimit = budget.maxWeight();
        event.classificationsLimit = budget.maxClassificationEvaluations(); event.executionsLimit = budget.maxExecutionEvaluations();
        event.begin(); return event;
        } catch (RuntimeException ignored) { return null; }
    }
    static void close(Attempt event, long tick, long debt, AdvanceResult result) {
        if (event == null) return;
        try {
            event.endedNanos = System.nanoTime(); event.afterTick = tick; event.debt = debt;
            if (result != null) {
                event.transactions = result.transactions().size();
                var used = result.consumed(); event.actions = used.actions(); event.weight = used.weight();
                event.classifications = used.classifications(); event.executions = used.executions();
                event.deferred = result.deferredAction().map(action -> action.id().value()).orElse("");
            }
            event.end(); event.commit();
        } catch (RuntimeException ignored) { }
    }
}
