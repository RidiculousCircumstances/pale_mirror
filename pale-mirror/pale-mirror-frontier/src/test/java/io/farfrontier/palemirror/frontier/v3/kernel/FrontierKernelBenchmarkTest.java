package io.farfrontier.palemirror.frontier.v3.kernel;

import com.sun.management.ThreadMXBean;
import io.farfrontier.palemirror.frontier.v3.api.CauseChain;
import io.farfrontier.palemirror.frontier.v3.api.CommandId;
import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.FrontierProjection;
import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.Revision;
import io.farfrontier.palemirror.frontier.v3.api.ScheduleId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Reproducible workload fixture, not a live-server performance claim. It measures the pure
 * command/event/reducer and due-action paths separately and validates their final hash inputs.
 */
@Tag("benchmark")
class FrontierKernelBenchmarkTest {
    private static final int OPERATIONS = 10_000;
    private static final WorldId WORLD = new WorldId("frontier:benchmark");
    private static final SubjectId SUBJECT = new SubjectId("settlement:benchmark");

    @Test
    void reportsWaveOneKernelBaseline() {
        runCommandWorkload(1_000); // JIT warm-up is deliberately outside reported metrics.
        BenchmarkResult commands = runCommandWorkload(OPERATIONS);
        BenchmarkResult schedules = runScheduledWorkload(OPERATIONS);

        System.out.printf(Locale.ROOT,
                "FRONTIER_V3_WAVE1 commands=%d ns=%d opsPerSecond=%.1f allocatedBytes=%d hash=%s%n",
                OPERATIONS, commands.elapsedNanos(), commands.opsPerSecond(), commands.allocatedBytes(), commands.hash());
        System.out.printf(Locale.ROOT,
                "FRONTIER_V3_WAVE1 schedules=%d ns=%d opsPerSecond=%.1f allocatedBytes=%d hash=%s%n",
                OPERATIONS, schedules.elapsedNanos(), schedules.opsPerSecond(), schedules.allocatedBytes(), schedules.hash());
    }

    private static BenchmarkResult runCommandWorkload(int operations) {
        InMemoryFrontierEngine<Counter, CounterProjection> engine = engine(List.of(), operations + 1);
        AllocationMeter meter = AllocationMeter.currentThread();
        long started = System.nanoTime();
        for (int index = 0; index < operations; index++) {
            CommandId id = new CommandId("command:benchmark-" + index);
            FrontierCommand command = new FrontierCommand(1, id, WORLD, new Revision(index), SimInstant.ZERO,
                    SUBJECT, CauseChain.root(id), new Delta(1));
            assertInstanceOf(CommandResult.Accepted.class, engine.submit(command));
        }
        long elapsed = System.nanoTime() - started;
        assertEquals(operations, engine.projection(io.farfrontier.palemirror.frontier.v3.api.ProjectionQuery.summary()).value());
        return new BenchmarkResult(elapsed, meter.allocatedSinceStart(), hex(engine.checkpoint().canonicalState()));
    }

    private static BenchmarkResult runScheduledWorkload(int operations) {
        List<ScheduledAction> schedules = new ArrayList<>(operations);
        for (int index = 0; index < operations; index++) {
            schedules.add(new ScheduledAction(new ScheduleId("schedule:benchmark-" + index), new SimInstant(1L), 0,
                    SUBJECT, "process.benchmark", 1));
        }
        InMemoryFrontierEngine<Counter, CounterProjection> engine = engine(schedules, operations + 1);
        AllocationMeter meter = AllocationMeter.currentThread();
        long started = System.nanoTime();
        engine.advanceTo(new SimInstant(1L), new WorkBudget(operations, operations));
        long elapsed = System.nanoTime() - started;
        assertEquals(operations, engine.projection(io.farfrontier.palemirror.frontier.v3.api.ProjectionQuery.summary()).value());
        return new BenchmarkResult(elapsed, meter.allocatedSinceStart(), hex(engine.checkpoint().canonicalState()));
    }

    private static InMemoryFrontierEngine<Counter, CounterProjection> engine(List<ScheduledAction> schedules, int capacity) {
        return new InMemoryFrontierEngine<>(
                WORLD, new Counter(0), SimInstant.ZERO,
                (state, command) -> new CommandPlan.Accepted(List.of(new ProposedEvent(SUBJECT, command.payload()))),
                (state, action) -> List.of(new ProposedEvent(SUBJECT, new Delta(1))),
                (state, event) -> new Counter(Math.addExact(state.value(), ((Delta) event.payload()).value())),
                state -> ByteBuffer.allocate(4).putInt(state.value()).array(),
                (state, world, revision, instant, query) -> new CounterProjection(world, revision, instant, state.value()),
                new EngineLimits(capacity, 1_000L, capacity), schedules);
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append(String.format(Locale.ROOT, "%02x", value));
        }
        return result.toString();
    }

    private record BenchmarkResult(long elapsedNanos, long allocatedBytes, String hash) {
        private double opsPerSecond() {
            return OPERATIONS * 1_000_000_000.0 / elapsedNanos;
        }
    }

    private record Counter(int value) {
    }

    private record Delta(int value) implements FrontierPayload {
        @Override
        public String type() {
            return "benchmark.delta";
        }
    }

    private record CounterProjection(WorldId worldId, Revision revision, SimInstant instant, int value)
            implements FrontierProjection {
    }

    private record AllocationMeter(ThreadMXBean bean, long threadId, long start) {
        private static AllocationMeter currentThread() {
            java.lang.management.ThreadMXBean raw = ManagementFactory.getThreadMXBean();
            if (raw instanceof ThreadMXBean bean && bean.isThreadAllocatedMemorySupported()) {
                if (!bean.isThreadAllocatedMemoryEnabled()) {
                    bean.setThreadAllocatedMemoryEnabled(true);
                }
                return new AllocationMeter(bean, Thread.currentThread().threadId(), bean.getThreadAllocatedBytes(Thread.currentThread().threadId()));
            }
            return new AllocationMeter(null, -1L, -1L);
        }

        private long allocatedSinceStart() {
            return bean == null ? -1L : bean.getThreadAllocatedBytes(threadId) - start;
        }
    }
}
