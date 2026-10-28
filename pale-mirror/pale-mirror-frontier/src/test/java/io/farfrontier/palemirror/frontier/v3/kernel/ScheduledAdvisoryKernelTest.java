package io.farfrontier.palemirror.frontier.v3.kernel;

import io.farfrontier.palemirror.frontier.v3.api.*;
import java.util.*;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ScheduledAdvisoryKernelTest {
    @Test void freeAdviceLaneIsReservedDuringAnExactPredecessorWithoutExecutingBeforeIt() {
        var advice = scheduled("schedule:starved-advice", "settlement:advice", 1, 1);
        var first = scheduled("schedule:exact-first", "settlement:first", 2, 1);
        var next = scheduled("schedule:exact-next", "settlement:next", 3, 1);
        var available = new AtomicBoolean(); var exactReady = new AtomicBoolean();
        var begun = new AtomicInteger(); var exactInputs = new ArrayList<Counter>();
        var planner = new ScheduledActionPlanner<Counter>() {
            public List<ProposedEvent> plan(Counter state, ScheduledAction action) { throw new AssertionError("exact preparation bypass"); }
            public boolean adviceAvailable() { return available.get(); }
            public Optional<ScheduledAdvisory<Counter>> advice(Counter state, ScheduledAction action, SimInstant at) {
                if (!action.equals(advice)) return Optional.empty();
                return Optional.of(() -> {
                    assertEquals(new Counter(1), state, "admitted advice uses its committed exact predecessor");
                    return (current, now) -> Optional.of(() -> () -> List.of(new ProposedEvent(action.subject(), new Delta(10))));
                });
            }
            public Optional<ScheduledAdviceCalculation<Counter>> beginAdvice(ScheduledAdvisory<Counter> work) {
                begun.incrementAndGet(); return ScheduledActionPlanner.super.beginAdvice(work);
            }
            public ScheduledCalculation begin(Counter state, ScheduledAction action, SimInstant at) {
                exactInputs.add(state);
                return () -> exactReady.getAndSet(false)
                        ? Optional.of(List.of(new ProposedEvent(action.subject(), new Delta(1)))) : Optional.empty();
            }
        };
        var engine = schedulingEngine(planner, List.of(advice, first, next)); var at = new SimInstant(5);
        var budget = new WorkBudget(16, 16);
        // Busy advice admission must not start its calculation yet.
        // The exact predecessor remains independently runnable.
        engine.advanceTo(at, budget); assertTrue(engine.hasPendingCalculation());
        assertEquals(1, engine.advisoryStatus().capacityWaiters());
        assertTrue(engine.advisoryStatus().reservation().isEmpty());
        available.set(true); engine.advanceTo(at, budget);
        assertEquals(0, begun.get()); assertEquals(new Counter(0), engine.canonicalState().state());
        assertEquals(Optional.of(advice), engine.advisoryStatus().reservation());
        assertEquals(FrontierAdvisoryStatus.Readiness.CHECKING, engine.advisoryStatus().reservationReadiness());
        assertEquals(0, engine.advisoryStatus().inFlight());
        exactReady.set(true); engine.advanceTo(at, budget);
        assertEquals(1, begun.get(), "continuous asynchronous exact work must not starve an idle advice lane");
        assertEquals(new Counter(11), engine.canonicalState().state());
        assertEquals(List.of(new Counter(0), new Counter(11)), exactInputs);
        assertTrue(engine.hasPendingCalculation(), "the next exact operation keeps its exclusive predecessor");
        assertEquals(List.of(next), engine.checkpoint().schedules());
        assertTrue(engine.advisoryStatus().reservation().isEmpty());
    }
    @Test void slowAdviceDoesNotBlockExactWorkAndObsoleteResultsCannotPublish() {
        var advisory = scheduled("schedule:advice", "settlement:advice", 1, 1);
        var exact = scheduled("schedule:exact", "settlement:exact", 2, 1);
        var ready = new java.util.concurrent.atomic.AtomicBoolean(); var begun = new AtomicInteger();
        var completedAt = new AtomicReference<SimInstant>();
        var planner = new ScheduledActionPlanner<Counter>() {
            public List<ProposedEvent> plan(Counter state, ScheduledAction action) {
                assertEquals(exact, action); return List.of(new ProposedEvent(action.subject(), new Delta(1)));
            }
            public Optional<ScheduledAdvisory<Counter>> advice(Counter state, ScheduledAction action, SimInstant at) {
                if (!action.equals(advisory)) return Optional.empty();
                return Optional.of(() -> (current, now) -> {
                    if (!current.equals(state)) return Optional.empty();
                    completedAt.set(now);
                    return Optional.of(() -> () -> List.of(new ProposedEvent(action.subject(), new Delta(10))));
                });
            }
            public Optional<ScheduledAdviceCalculation<Counter>> beginAdvice(ScheduledAdvisory<Counter> advice) {
                begun.incrementAndGet(); var result = advice.prepare();
                return Optional.of(new ScheduledAdviceCalculation<>() {
                    public boolean ready() { return ready.get(); }
                    public Optional<ScheduledAdvisory.Completion<Counter>> poll() { return ready.get() ? Optional.of(result) : Optional.empty(); }
                });
            }
        };
        var engine = schedulingEngine(planner, List.of(advisory, exact));
        var first = engine.advanceTo(new SimInstant(2), new WorkBudget(8, 8));
        assertEquals(new Counter(1), engine.canonicalState().state());
        assertEquals(List.of(advisory), engine.checkpoint().schedules(), "recovery must retain the original unconsumed advice action");
        assertEquals(1, first.transactions().size()); assertTrue(!engine.hasPendingCalculation());
        ready.set(true);
        var obsolete = engine.advanceTo(new SimInstant(3), new WorkBudget(8, 8));
        assertEquals(0, obsolete.transactions().size(), "obsolete forecast has no publication or retry WAL");
        assertEquals(new Counter(1), engine.canonicalState().state());
        engine.advanceTo(new SimInstant(4), new WorkBudget(8, 8));
        assertEquals(new Counter(11), engine.canonicalState().state());
        assertEquals(2, begun.get()); assertEquals(new SimInstant(4), completedAt.get());
        assertTrue(engine.scheduledActions().isEmpty());
    }

    @Test void pendingAdviceChargesTheOriginalAllowanceAndCanBeCancelledWithoutLosingItsAction() {
        var action = scheduled("schedule:bounded-advice", SUBJECT.value(), 1, 7);
        var cancelled = new AtomicInteger();
        var planner = new ScheduledActionPlanner<Counter>() {
            public List<ProposedEvent> plan(Counter state, ScheduledAction ignored) { throw new AssertionError("advice bypass"); }
            public Optional<ScheduledAdvisory<Counter>> advice(Counter state, ScheduledAction ignored, SimInstant at) {
                return Optional.of(() -> (current, now) -> Optional.empty());
            }
            public Optional<ScheduledAdviceCalculation<Counter>> beginAdvice(ScheduledAdvisory<Counter> advice) {
                return Optional.of(new ScheduledAdviceCalculation<>() {
                    public boolean ready() { return false; }
                    public Optional<ScheduledAdvisory.Completion<Counter>> poll() { return Optional.empty(); }
                    public void cancel() { cancelled.incrementAndGet(); }
                });
            }
        };
        var engine = schedulingEngine(planner, List.of(action)); var budget = new WorkBudget(1, 7);
        var waiting = engine.advanceTo(new SimInstant(1), budget);
        assertEquals(1, waiting.consumed().actions()); assertEquals(7, waiting.consumed().weight());
        assertTrue(waiting.consumed().remaining(budget).isEmpty());
        var polled = engine.advanceTo(new SimInstant(2), budget);
        assertEquals(WorkConsumption.NONE, polled.consumed(),
                "a pending computation is not runnable work and cannot repeatedly consume the allowance");
        engine.cancelPendingCalculation();
        assertEquals(1, cancelled.get()); assertEquals(List.of(action), engine.checkpoint().schedules());
        assertEquals(new Counter(0), engine.canonicalState().state()); assertTrue(engine.transactions().isEmpty());
    }

    @Test void boundedAdviceRetainsCompletedWorkAndReleasesAnOwnerWhichBecomesHeld() {
        var actions = java.util.stream.IntStream.range(0, 20).mapToObj(i ->
                scheduled("schedule:advice-" + i, "subject:advice-" + i, 1, 1)).toList();
        var ready = new AtomicBoolean(); var hold = new AtomicBoolean(); var begun = new AtomicInteger(); var cancelled = new AtomicInteger();
        var planner = new ScheduledActionPlanner<Counter>() {
            public List<ProposedEvent> plan(Counter state, ScheduledAction action) { throw new AssertionError("advice bypass"); }
            public boolean held(Counter state, ScheduledAction action) { return hold.get() && action.equals(actions.getFirst()); }
            public Optional<ScheduledAdvisory<Counter>> advice(Counter state, ScheduledAction action, SimInstant at) {
                return Optional.of(() -> (current, now) -> Optional.of(() -> () -> List.of(new ProposedEvent(action.subject(), new Delta(1)))));
            }
            public Optional<ScheduledAdviceCalculation<Counter>> beginAdvice(ScheduledAdvisory<Counter> advice) {
                begun.incrementAndGet(); var result = advice.prepare();
                return Optional.of(new ScheduledAdviceCalculation<>() {
                    public boolean ready() { return ready.get(); }
                    public Optional<ScheduledAdvisory.Completion<Counter>> poll() { return ready.get() ? Optional.of(result) : Optional.empty(); }
                    public void cancel() { cancelled.incrementAndGet(); }
                });
            }
        };
        var engine = schedulingEngine(planner, actions); var budget = new WorkBudget(64, 64);
        engine.advanceTo(new SimInstant(1), budget); int admitted = begun.get();
        assertTrue(admitted > 0 && admitted < actions.size());
        engine.advanceTo(new SimInstant(2), budget);
        assertEquals(admitted, begun.get(), "capacity pressure must not discard existing work and recompute it");
        assertEquals(0, cancelled.get()); assertTrue(engine.transactions().isEmpty());
        hold.set(true); ready.set(true); engine.advanceTo(new SimInstant(3), budget);
        assertEquals(1, cancelled.get(), "a completed calculation must recheck its actual owner and cannot grant a held owner execution");
        for (int i = 4; i < 32 && engine.scheduledActions().size() > 1; i++) engine.advanceTo(new SimInstant(i), budget);
        assertEquals(new Counter(19), engine.canonicalState().state());
        assertEquals(List.of(actions.getFirst()), engine.scheduledActions());
        assertEquals(20, begun.get(), "every independently admissible owner computes once");
        hold.set(false); engine.advanceTo(new SimInstant(32), budget);
        assertEquals(new Counter(20), engine.canonicalState().state()); assertTrue(engine.scheduledActions().isEmpty());
    }


    @Test void busyHostCapacityIsNotRepolledByUnrelatedWorldMutationsAndWakeRemainsFair() {
        var actions = List.of(scheduled("schedule:capacity-a", SUBJECT.value(), 1, 1),
                scheduled("schedule:capacity-b", "subject:second", 1, 1));
        var available = new AtomicBoolean(); var begun = new AtomicInteger();
        var planner = new ScheduledActionPlanner<Counter>() {
            public boolean adviceAvailable() { return available.get(); }
            public List<ProposedEvent> plan(Counter state, ScheduledAction action) { throw new AssertionError("advice bypass"); }
            public Optional<ScheduledAdvisory<Counter>> advice(Counter state, ScheduledAction action, SimInstant at) {
                return Optional.of(() -> (current, now) -> Optional.of(() -> () -> List.of(new ProposedEvent(action.subject(), new Delta(1)))));
            }
            public Optional<ScheduledAdviceCalculation<Counter>> beginAdvice(ScheduledAdvisory<Counter> advice) {
                assertTrue(available.get()); begun.incrementAndGet(); return ScheduledActionPlanner.super.beginAdvice(advice);
            }
        };
        var engine = schedulingEngine(planner, actions); var budget = new WorkBudget(64, 64);
        engine.advanceTo(new SimInstant(1), budget);
        for (int i = 1; i <= 20; i++) {
            var id = new CommandId("test:unrelated-" + i);
            assertInstanceOf(CommandResult.Accepted.class, engine.submit(new FrontierCommand(FrontierCommand.SCHEMA_VERSION,
                    id, WORLD, engine.checkpoint().revision(), engine.checkpoint().instant(), SUBJECT, CauseChain.root(id), new Delta(1))));
            assertEquals(WorkConsumption.NONE, engine.advanceTo(new SimInstant(i + 1), budget).consumed());
        }
        assertEquals(0, begun.get()); assertEquals(20, engine.canonicalState().state().value());
        assertEquals(actions, engine.checkpoint().schedules());
        // Recovery retains canonical actions, not technical wait tickets or worker results.
        var recovered = FrontierEngines.recoverCanonicalStateAccess(new FrontierEngineConfiguration<>(WORLD, new Counter(0), SimInstant.ZERO,
                (Counter state, FrontierCommand command) -> new CommandPlan.Accepted(List.of(new ProposedEvent(SUBJECT, command.payload()))),
                planner, (state, event) -> new Counter(state.value() + ((Delta)event.payload()).value()),
                new StateCodec<Counter>() {
                    public byte[] encode(Counter state) { return ByteBuffer.allocate(4).putInt(state.value()).array(); }
                    public Counter decode(byte[] bytes) { return new Counter(ByteBuffer.wrap(bytes).getInt()); }
                }, (state, world, revision, instant, query) -> new CounterProjection(world, revision, instant, state.value()),
                new EngineLimits(64,100,64,64), actions, TransactionCommitter.noOp()),
                new io.farfrontier.palemirror.frontier.v3.persistence.RecoveryImage(WORLD,
                    Optional.of(new io.farfrontier.palemirror.frontier.v3.persistence.SnapshotRecord(engine.checkpoint(),20)), List.of()));
        available.set(true);
        engine.advanceTo(new SimInstant(22), budget); engine.advanceTo(new SimInstant(23), budget);
        recovered.advanceTo(new SimInstant(23), budget);
        assertEquals(new Counter(22), engine.canonicalState().state());
        assertEquals(engine.canonicalState().state(), recovered.canonicalState().state());
        assertTrue(engine.scheduledActions().isEmpty());
        assertEquals(4, begun.get(), "both original and recovered actions complete once; no hidden retry execution");
    }

    private static final WorldId WORLD = new WorldId("frontier:advice-kernel");
    private static final SubjectId SUBJECT = new SubjectId("subject:advice");
    private record Counter(int value) { }
    private record Delta(int value) implements FrontierPayload { public String type() { return "test.delta"; } }
    private record CounterProjection(WorldId worldId, Revision revision, SimInstant instant, int value) implements FrontierProjection { }
    private static ScheduledAction scheduled(String id, String subject, long at, int weight) {
        return new ScheduledAction(new ScheduleId(id), new SimInstant(at), 0, new SubjectId(subject), "test.advice", weight);
    }
    private static InMemoryFrontierEngine<Counter, CounterProjection> schedulingEngine(ScheduledActionPlanner<Counter> planner, List<ScheduledAction> actions) {
        return new InMemoryFrontierEngine<>(WORLD, new Counter(0), SimInstant.ZERO,
                (state, command) -> new CommandPlan.Accepted(List.of(new ProposedEvent(SUBJECT, command.payload()))),
                planner, (state, event) -> new Counter(state.value() + ((Delta)event.payload()).value()),
                state -> ByteBuffer.allocate(4).putInt(state.value()).array(),
                (state, world, revision, instant, query) -> new CounterProjection(world, revision, instant, state.value()),
                new EngineLimits(64, 100, 64, 64), actions);
    }
}
