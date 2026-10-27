package io.farfrontier.palemirror.frontier.v3.kernel;

import io.farfrontier.palemirror.frontier.v3.api.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InMemoryFrontierReadinessTest {
    private static final WorldId WORLD = new WorldId("frontier:readiness-test");
    private static final SubjectId SUBJECT = new SubjectId("resident:readiness-test");
    @Test void exactSelfReplacementParksWithoutPublishingAndIndependentDueWorkStillRuns() {
        var waiting = scheduled("schedule:calculation", "resident:1-waiting", 5, 1);
        var other = scheduled("schedule:other", "resident:other", 5, 2);
        var preparing = new java.util.concurrent.atomic.AtomicBoolean();
        var ready = new java.util.concurrent.atomic.AtomicBoolean();
        ScheduledActionPlanner<Counter> planner = new ScheduledActionPlanner<>() {
            public boolean held(Counter state, ScheduledAction action) {
                return action.equals(waiting) && preparing.get() && !ready.get();
            }
            public Set<SubjectId> holdWakeKeys(Counter state, ScheduledAction action) { return Set.of(action.subject()); }
            public List<ProposedEvent> plan(Counter state, ScheduledAction action) {
                if (action.equals(waiting) && !ready.get()) {
                    preparing.set(true);
                    return List.of(new ProposedEvent(action.subject(), new ScheduleEffect.Rescheduled(action.id(), action)));
                }
                return List.of(new ProposedEvent(action.subject(), new Delta(1)));
            }
        };
        var engine = new InMemoryFrontierEngine<>(WORLD, new Counter(0), SimInstant.ZERO,
                (Counter state, FrontierCommand command) -> new CommandPlan.Accepted(List.of(new ProposedEvent(SUBJECT, command.payload()))),
                planner, (state, event) -> new Counter(state.value() + ((Delta) event.payload()).value()),
                state -> ByteBuffer.allocate(4).putInt(state.value()).array(),
                (state, world, revision, instant, query) -> new CounterProjection(world, revision, instant, state.value()),
                new EngineLimits(8, 100L, 8), List.of(waiting, other));
        var narrow = engine.advanceTo(new SimInstant(10), new WorkBudget(4, 4, 1, 4, 4));
        assertEquals(1, narrow.consumed().classifications(), "post-plan refusal cannot exceed its classification allowance");
        assertEquals(Optional.of(waiting), narrow.deferredAction());
        assertTrue(engine.transactions().isEmpty());
        engine.advanceTo(new SimInstant(10), new WorkBudget(4, 4));
        assertEquals(1, engine.transactions().size(), "only independent business work is a transaction");
        assertEquals(List.of(waiting), engine.scheduledActions());
        assertEquals(1, engine.canonicalState().state().value());
        engine.advanceTo(new SimInstant(20), new WorkBudget(4, 4));
        assertEquals(1, engine.transactions().size());
        ready.set(true); assertTrue(engine.signalScheduledReadiness(waiting));
        engine.advanceTo(new SimInstant(20), new WorkBudget(4, 4));
        assertEquals(2, engine.canonicalState().state().value()); assertTrue(engine.scheduledActions().isEmpty());
        assertEquals(2, engine.transactions().size());
    }
    @Test
    void addressedReadinessRechecksOnlyAnExactDueGenerationWithoutCanonicalMutation() throws Exception {
        var held = scheduled("schedule:external-ready", "resident:waiting", 5L, 1);
        var future = scheduled("schedule:future-ready", "resident:future", 50L, 1);
        var available = new java.util.concurrent.atomic.AtomicBoolean();
        var checks = new AtomicInteger();
        ScheduledActionPlanner<Counter> planner = new ScheduledActionPlanner<>() {
            public boolean held(Counter state, ScheduledAction action) { checks.incrementAndGet(); return !available.get(); }
            public Set<SubjectId> holdWakeKeys(Counter state, ScheduledAction action) { return Set.of(action.subject()); }
            public Optional<ScheduledHold<Counter>> holdProof(Counter state, ScheduledAction action) {
                return Optional.of(new ScheduledHold<>(action, Set.of(action.subject()), ignored -> !available.get()));
            }
            public List<ProposedEvent> plan(Counter state, ScheduledAction action) {
                return List.of(new ProposedEvent(action.subject(), new Delta(1)));
            }
        };
        var engine = new InMemoryFrontierEngine<>(WORLD, new Counter(0), SimInstant.ZERO,
                (Counter state, FrontierCommand command) -> new CommandPlan.Accepted(List.of(new ProposedEvent(SUBJECT, command.payload()))),
                planner, (state, event) -> new Counter(state.value() + ((Delta) event.payload()).value()),
                state -> ByteBuffer.allocate(4).putInt(state.value()).array(),
                (state, world, revision, instant, query) -> new CounterProjection(world, revision, instant, state.value()),
                new EngineLimits(8, 100L, 8), List.of(held, future));
        engine.advanceTo(new SimInstant(10), new WorkBudget(4, 4));
        var before = engine.checkpoint();
        org.junit.jupiter.api.Assertions.assertFalse(engine.signalScheduledReadiness(future));
        org.junit.jupiter.api.Assertions.assertFalse(engine.signalScheduledReadiness(
                scheduled(held.id().value(), held.subject().value(), 6, 1)));
        assertTrue(engine.signalScheduledReadiness(held));
        assertTrue(engine.signalScheduledReadiness(held));
        assertEquals(before, engine.checkpoint());
        assertTrue(engine.transactions().isEmpty());
        int previousChecks = checks.get();
        engine.advanceTo(new SimInstant(10), new WorkBudget(4, 4));
        assertTrue(checks.get() > previousChecks, "readiness must recheck the owner rather than granting execution");
        assertEquals(before, engine.checkpoint(), "a still-held owner cannot mutate merely because it was signalled");
        available.set(true);
        engine.advanceTo(new SimInstant(10), new WorkBudget(4, 4));
        assertEquals(before, engine.checkpoint(), "an external change alone does not poll a parked action");
        try (var worker = java.util.concurrent.Executors.newSingleThreadExecutor()) {
            assertInstanceOf(IllegalStateException.class, assertThrows(java.util.concurrent.ExecutionException.class,
                    () -> worker.submit(() -> engine.signalScheduledReadiness(held)).get()).getCause());
        }
        assertTrue(engine.signalScheduledReadiness(held));
        engine.advanceTo(new SimInstant(10), new WorkBudget(4, 4));
        assertEquals(1, engine.canonicalState().state().value());
        assertEquals(List.of(future), engine.scheduledActions());
        org.junit.jupiter.api.Assertions.assertFalse(engine.signalScheduledReadiness(held));
        assertEquals(1, engine.transactions().size());
    }

    @Test void queueOnlyCommitsDoNotRestartUnaddressedPollingButChangedOwnerInputsDo() {
        var actions = new ArrayList<ScheduledAction>();
        for (int i = 0; i < 13; i++) actions.add(scheduled("schedule:held-" + i, "resident:held-" + i, 1, 1));
        for (int i = 0; i < 10; i++) actions.add(scheduled("schedule:ready-" + i, "resident:ready-" + i, 2, 1));
        var heldChecks = new AtomicInteger();
        ScheduledActionPlanner<Counter> planner = new ScheduledActionPlanner<>() {
            public boolean held(Counter state, ScheduledAction action) {
                if (action.dueAt().ticks() != 1) return false;
                heldChecks.incrementAndGet(); return state.value() == 0;
            }
            public List<ProposedEvent> plan(Counter state, ScheduledAction action) {
                return List.of(new ProposedEvent(action.subject(), new ScheduleEffect.Consumed(action.id())));
            }
        };
        var engine = new InMemoryFrontierEngine<>(WORLD, new Counter(0), SimInstant.ZERO,
                (Counter state, FrontierCommand command) -> new CommandPlan.Accepted(List.of(new ProposedEvent(SUBJECT, command.payload()))),
                planner, (state, event) -> new Counter(state.value() + ((Delta) event.payload()).value()),
                state -> ByteBuffer.allocate(4).putInt(state.value()).array(),
                (state, world, revision, instant, query) -> new CounterProjection(world, revision, instant, state.value()),
                new EngineLimits(64, 100L, 64), actions);
        var budget = new WorkBudget(1, 4, 8, 1, 4);
        for (int turn = 0; turn < 32; turn++) engine.advanceTo(new SimInstant(2), budget);
        assertEquals(10, engine.transactions().size());
        assertEquals(13, heldChecks.get(), "unchanged owner inputs must be polled once across queue-only revisions");
        var id = new CommandId("test:changed-readiness-inputs");
        assertInstanceOf(CommandResult.Accepted.class, engine.submit(new FrontierCommand(FrontierCommand.SCHEMA_VERSION,
                id, WORLD, engine.checkpoint().revision(), new SimInstant(2), SUBJECT, CauseChain.root(id), new Delta(1))));
        for (int turn = 0; turn < 32 && !engine.scheduledActions().isEmpty(); turn++) engine.advanceTo(new SimInstant(2), budget);
        assertTrue(engine.scheduledActions().isEmpty(), "a changed business input must still recheck and release the waiters");
        assertEquals(1, engine.canonicalState().state().value());
    }
    private static ScheduledAction scheduled(String id, String subject, long due, int weight) {
        return new ScheduledAction(new ScheduleId(id), new SimInstant(due), 0, new SubjectId(subject), "process.test", weight);
    }
    private record Counter(int value) { }
    private record Delta(int value) implements FrontierPayload {
        public String type() { return "test.delta"; }
    }
    private record CounterProjection(WorldId worldId, Revision revision, SimInstant instant, int value) implements FrontierProjection { }
}
