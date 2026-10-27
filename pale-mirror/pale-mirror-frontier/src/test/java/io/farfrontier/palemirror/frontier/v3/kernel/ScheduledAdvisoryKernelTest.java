package io.farfrontier.palemirror.frontier.v3.kernel;

import io.farfrontier.palemirror.frontier.v3.api.*;
import java.util.*;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ScheduledAdvisoryKernelTest {
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
                return Optional.of(() -> ready.get() ? Optional.of(result) : Optional.empty());
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
        assertEquals(1, polled.consumed().actions()); assertEquals(1, polled.consumed().weight(),
                "polling the same retained calculation must not pay its full computation cost again");
        assertTrue(polled.consumed().remaining(budget).isEmpty(), "waiting still respects the original action allowance");
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
        hold.set(true); engine.advanceTo(new SimInstant(3), budget);
        assertEquals(1, cancelled.get(), "a newly held owner releases its disposable calculation slot");
        assertEquals(admitted + 1, begun.get());
        ready.set(true);
        for (int i = 4; i < 12 && engine.scheduledActions().size() > 1; i++) engine.advanceTo(new SimInstant(i), budget);
        assertEquals(new Counter(19), engine.canonicalState().state());
        assertEquals(List.of(actions.getFirst()), engine.scheduledActions());
        assertEquals(20, begun.get(), "every independently admissible owner computes once");
        hold.set(false); engine.advanceTo(new SimInstant(12), budget);
        assertEquals(new Counter(20), engine.canonicalState().state()); assertTrue(engine.scheduledActions().isEmpty());
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
