package io.farfrontier.palemirror.frontier.v3.kernel;

import io.farfrontier.palemirror.frontier.v3.api.AdvanceResult;
import io.farfrontier.palemirror.frontier.v3.api.CauseChain;
import io.farfrontier.palemirror.frontier.v3.api.CommandId;
import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
import io.farfrontier.palemirror.frontier.v3.api.EngineScheduleBinding;
import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.FrontierEvent;
import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.FrontierProjection;
import io.farfrontier.palemirror.frontier.v3.api.ProjectionQuery;
import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.RejectionCode;
import io.farfrontier.palemirror.frontier.v3.api.Revision;
import io.farfrontier.palemirror.frontier.v3.api.ScheduleId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.persistence.RecoveryImage;
import io.farfrontier.palemirror.frontier.v3.persistence.SnapshotRecord;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScheduledCalculationTest {
    private static final WorldId WORLD = new WorldId("world:prepared-test");
    private static final SubjectId SUBJECT = new SubjectId("subject:prepared-test");
    @Test void unfinishedCalculationPublishesNothingAndRecoveryRetainsTheExactAction() {
        var ready = new java.util.concurrent.atomic.AtomicBoolean();
        var starts = new AtomicInteger(); var cancelled = new AtomicInteger();
        var action = scheduled("schedule:prepared", SUBJECT.value(), 1, 1);
        var engine = preparingEngine(action, ready, starts, cancelled);
        var waiting = engine.advanceTo(new SimInstant(10), new WorkBudget(2, 10));
        assertEquals(SimInstant.ZERO, waiting.instant());
        assertEquals(Revision.ZERO, waiting.revision());
        assertTrue(waiting.transactions().isEmpty());
        assertTrue(engine.hasPendingCalculation());
        assertEquals(List.of(action), engine.checkpoint().schedules());
        var checkpoint = engine.checkpoint();
        var recovered = FrontierEngines.recover(configuration(), new RecoveryImage(WORLD,
                Optional.of(new SnapshotRecord(checkpoint, 1)), List.of()));
        assertTrue(!recovered.hasPendingCalculation());
        ready.set(true);
        engine.advanceTo(new SimInstant(10), new WorkBudget(2, 10));
        recovered.advanceTo(new SimInstant(10), new WorkBudget(2, 10));
        assertEquals(recovered.checkpoint(), engine.checkpoint());
        assertEquals(1, starts.get());
        assertTrue(!engine.hasPendingCalculation());
    }

    @Test void aSmallerResumeBudgetCannotMoveTimePastThePreparedTransaction() {
        var ready = new java.util.concurrent.atomic.AtomicBoolean();
        var action = scheduled("schedule:prepared-budget", SUBJECT.value(), 1, 6);
        var engine = preparingEngine(action, ready, new AtomicInteger(), new AtomicInteger());
        engine.advanceTo(new SimInstant(5), new WorkBudget(2, 10));
        ready.set(true);
        var held = engine.advanceTo(new SimInstant(8), new WorkBudget(1, 2, 2, 1, 10));
        assertEquals(SimInstant.ZERO, held.instant()); assertTrue(engine.hasPendingCalculation());
        var completed = engine.advanceTo(new SimInstant(8), new WorkBudget(2, 10));
        assertEquals("ACTIVE", completed.status().kind().name());
        assertEquals(1, completed.transactions().size());
        assertEquals(new SimInstant(8), completed.instant());
    }

    @Test void externalCommandDiscardsUnpublishedCalculationAndRecomputesFromNewFacts() {
        var ready = new java.util.concurrent.atomic.AtomicBoolean();
        var starts = new AtomicInteger(); var cancelled = new AtomicInteger();
        var engine = preparingEngine(scheduled("schedule:prepared", SUBJECT.value(), 1, 1), ready, starts, cancelled);
        engine.advanceTo(new SimInstant(1), new WorkBudget(2, 10));
        assertInstanceOf(CommandResult.Accepted.class, engine.submit(command("command:player-edit", Revision.ZERO, 10)));
        assertEquals(1, cancelled.get());
        ready.set(true);
        engine.advanceTo(new SimInstant(1), new WorkBudget(2, 10));
        assertEquals(21, engine.canonicalState().state().value(), "the new plan uses 10+1, never the stale zero+1");
        assertEquals(2, starts.get());
    }

    @Test void policyRejectionRetainsUnchangedPreparedWork() {
        var ready = new java.util.concurrent.atomic.AtomicBoolean();
        var starts = new AtomicInteger(); var cancelled = new AtomicInteger();
        var engine = preparingEngine(scheduled("schedule:retained", SUBJECT.value(), 1, 1), ready, starts, cancelled);
        engine.advanceTo(new SimInstant(1), new WorkBudget(2, 10));
        assertRejected(engine.submit(command("command:rejected", Revision.ZERO, -1)), RejectionCode.REJECTED_BY_POLICY);
        assertEquals(0, cancelled.get());
        assertTrue(engine.hasPendingCalculation());
        ready.set(true);
        engine.advanceTo(new SimInstant(1), new WorkBudget(2, 10));
        assertEquals(1, starts.get());
        assertEquals(1, engine.canonicalState().state().value());
    }

    private static InMemoryFrontierEngine<Counter, CounterProjection> preparingEngine(ScheduledAction action,
            java.util.concurrent.atomic.AtomicBoolean ready, AtomicInteger starts, AtomicInteger cancelled) {
        ScheduledActionPlanner<Counter> planner = new ScheduledActionPlanner<>() {
            public List<ProposedEvent> plan(Counter state, ScheduledAction ignored) {
                return List.of(new ProposedEvent(SUBJECT, new Delta(state.value() + 1)));
            }
            public ScheduledCalculation begin(Counter state, ScheduledAction pending, SimInstant instant) {
                starts.incrementAndGet();
                return new ScheduledCalculation() {
                    public Optional<List<ProposedEvent>> poll() { return ready.get() ? Optional.of(plan(state, pending)) : Optional.empty(); }
                    public void cancel() { cancelled.incrementAndGet(); }
                };
            }
        };
        return new InMemoryFrontierEngine<>(WORLD, new Counter(0), SimInstant.ZERO,
                (state, command) -> ((Delta) command.payload()).value() < 0
                        ? new CommandPlan.Rejected(new io.farfrontier.palemirror.frontier.v3.api.CommandRejection(
                            RejectionCode.REJECTED_BY_POLICY, "negative counter input"))
                        : new CommandPlan.Accepted(List.of(new ProposedEvent(SUBJECT, command.payload()))),
                planner, (state, event) -> reduce(state, event, false),
                state -> ByteBuffer.allocate(4).putInt(state.value()).array(),
                (state, world, revision, instant, query) -> new CounterProjection(world, revision, instant, state.value()),
                new EngineLimits(8, 100, 8), List.of(action), TransactionCommitter.noOp());
    }

    private static FrontierEngineConfiguration<Counter, CounterProjection> configuration() {
        StateCodec<Counter> codec = new StateCodec<>() {
            @Override public byte[] encode(Counter state) { return ByteBuffer.allocate(4).putInt(state.value()).array(); }
            @Override public Counter decode(byte[] bytes) {
                if (bytes.length != 4) throw new IllegalArgumentException("counter checkpoint is malformed");
                return new Counter(ByteBuffer.wrap(bytes).getInt());
            }
        };
        return new FrontierEngineConfiguration<>(WORLD, new Counter(0), SimInstant.ZERO,
                (state, command) -> new CommandPlan.Accepted(List.of(new ProposedEvent(SUBJECT, command.payload()))),
                (state, action) -> List.of(new ProposedEvent(action.subject(), new Delta(action.weight()))),
                (state, event) -> reduce(state, event, false), codec,
                (state, world, revision, instant, query) -> new CounterProjection(world, revision, instant, state.value()),
                new EngineLimits(8, 100L, 8), List.of(), TransactionCommitter.noOp());
    }

    private static Counter reduce(Counter state, FrontierEvent event, boolean failOnNine) {
        Delta delta = (Delta) event.payload();
        if (failOnNine && delta.value() == 9) {
            throw new IllegalStateException("synthetic reducer failure");
        }
        return new Counter(Math.addExact(state.value(), delta.value()));
    }

    private static FrontierCommand command(String id, Revision revision, int delta) {
        return command(id, revision, new Delta(delta));
    }

    private static FrontierCommand command(String id, Revision revision, FrontierPayload payload) {
        CommandId commandId = new CommandId(id);
        return new FrontierCommand(1, commandId, WORLD, revision, SimInstant.ZERO, SUBJECT,
                CauseChain.root(commandId), payload);
    }

    private static ScheduledAction scheduled(String id, String subject, long dueAt, int weight) {
        return new ScheduledAction(new ScheduleId(id), new SimInstant(dueAt), 0,
                new SubjectId(subject), "process.test", weight);
    }

    private static void assertRejected(CommandResult result, RejectionCode code) {
        CommandResult.Rejected rejected = assertInstanceOf(CommandResult.Rejected.class, result);
        assertEquals(code, rejected.rejection().code());
    }

    private record Counter(int value) {
    }

    private record Delta(int value) implements FrontierPayload {
        @Override
        public String type() {
            return "test.delta";
        }
    }

    private record CounterProjection(WorldId worldId, Revision revision, SimInstant instant, int value)
            implements FrontierProjection {
    }
}
