package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.CauseChain;
import io.farfrontier.palemirror.frontier.v3.api.CheckpointImage;
import io.farfrontier.palemirror.frontier.v3.api.CommandId;
import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.FrontierEvent;
import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.FrontierProjection;
import io.farfrontier.palemirror.frontier.v3.api.ProjectionQuery;
import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.RejectionCode;
import io.farfrontier.palemirror.frontier.v3.api.Revision;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.CommandPlan;
import io.farfrontier.palemirror.frontier.v3.kernel.EngineLimits;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration;
import io.farfrontier.palemirror.frontier.v3.kernel.PayloadCodec;
import io.farfrontier.palemirror.frontier.v3.kernel.PayloadCodecs;
import io.farfrontier.palemirror.frontier.v3.kernel.StateCodec;
import io.farfrontier.palemirror.frontier.v3.kernel.TransactionCommitter;
import io.farfrontier.palemirror.frontier.v3.kernel.WorkBudget;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierStore;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldRuntimeDefinition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalIntentTransition;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrontierV3ServerRuntimeTest {
    private static final WorldId WORLD = new WorldId("frontier:runtime");
    private static final SubjectId SUBJECT = new SubjectId("settlement:runtime");

    @Test
    void freshLifecycleWritesAheadTicksPersistsAndRecoversWithoutWorldTimeInput(@TempDir Path directory) {
        FrontierStore store = new FrontierFileStore(directory, codecs());
        FrontierV3ServerRuntime<Counter, CounterProjection> runtime = FrontierV3ServerRuntime.start(configuration(), store, 2);
        assertEquals(FrontierV3RuntimeStatus.Kind.ACTIVE, runtime.status().kind());

        FrontierCommand command = command("command:increment", Revision.ZERO, SimInstant.ZERO, 5);
        assertInstanceOf(CommandResult.Accepted.class, runtime.submit(command).orElseThrow());
        runtime.tick(new WorkBudget(4, 8));
        runtime.tick(new WorkBudget(4, 8));
        runtime.tick(new WorkBudget(4, 8));
        runtime.shutdown();

        assertEquals(FrontierV3RuntimeStatus.Kind.STOPPED, runtime.status().kind());
        FrontierV3ServerRuntime<Counter, CounterProjection> recovered = FrontierV3ServerRuntime.start(configuration(), store, 2);
        CounterProjection projection = recovered.projection(ProjectionQuery.summary()).orElseThrow();
        assertEquals(5, projection.value());
        assertEquals(new Revision(1L), projection.revision());
        assertEquals(new SimInstant(3L), projection.instant());
        CommandResult duplicate = recovered.submit(command("command:increment", new Revision(1L), new SimInstant(3L), 5)).orElseThrow();
        assertEquals(RejectionCode.DUPLICATE_COMMAND, assertInstanceOf(CommandResult.Rejected.class, duplicate).rejection().code());
    }

    @Test
    void corruptDurableHistoryQuarantinesStartupInsteadOfCreatingAReplacementWorld(@TempDir Path directory) throws Exception {
        FrontierStore store = new FrontierFileStore(directory, codecs());
        FrontierV3ServerRuntime<Counter, CounterProjection> runtime = FrontierV3ServerRuntime.start(configuration(), store, 20);
        assertInstanceOf(CommandResult.Accepted.class,
                runtime.submit(command("command:corrupt", Revision.ZERO, SimInstant.ZERO, 1)).orElseThrow());
        Path wal = directory.resolve("frontier-v3/frontier_runtime/wal-00000000000000000001.bin");
        byte[] bytes = Files.readAllBytes(wal);
        bytes[bytes.length - 1] ^= 1;
        Files.write(wal, bytes);

        FrontierV3ServerRuntime<Counter, CounterProjection> failed = FrontierV3ServerRuntime.start(configuration(), store, 20);

        assertEquals(FrontierV3RuntimeStatus.Kind.QUARANTINED, failed.status().kind());
        assertTrue(failed.projection(ProjectionQuery.summary()).isEmpty());
    }

    @Test
    void restartQuarantinesAnUninspectableRunningPhysicalIntentWithoutBlindReplay(@TempDir Path directory) {
        WorldId world = new WorldId("frontier:restart-safety");
        FrontierStore store = new FrontierFileStore(directory, FrontierWorldRuntimeDefinition.payloadCodecs());
        var configuration = FrontierWorldRuntimeDefinition.configuration(world, 91L);
        FrontierV3ServerRuntime<FrontierWorldState, io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection> runtime =
                FrontierV3ServerRuntime.start(configuration, store, 10_000);
        for (int tick = 0; tick < 900; tick++) runtime.tick(new WorkBudget(8, 64));

        PhysicalIntentId intentId = new PhysicalIntentId("intent:cargo-handoff-supply-1-1");
        CheckpointImage prepared = runtime.checkpointImage().orElseThrow();
        CommandId runningCommand = new CommandId("test:mark-running");
        assertInstanceOf(CommandResult.Accepted.class, runtime.submit(new FrontierCommand(1, runningCommand, world,
                prepared.revision(), prepared.instant(), FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(runningCommand),
                new PhysicalIntentTransition(intentId, PhysicalIntentStatus.RUNNING, java.util.Optional.empty()))).orElseThrow());

        FrontierV3ServerRuntime<FrontierWorldState, io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection> recovered =
                FrontierV3ServerRuntime.start(configuration, store, 10_000);
        assertEquals(1, FrontierV3PhysicalIntentRestartSafety.quarantineUninspectableRunningIntents(recovered));
        FrontierWorldState state = new FrontierWorldStateCodec().decode(recovered.checkpointImage().orElseThrow().canonicalState());
        assertEquals(PhysicalIntentStatus.UNKNOWN_AFTER_RESTART, state.physicalIntents().get(intentId).status());
        assertEquals(0, recovered.projection(ProjectionQuery.summary()).orElseThrow().preparedPhysicalIntentCount());
        assertEquals(1, recovered.projection(ProjectionQuery.summary()).orElseThrow().unknownPhysicalIntentCount());
    }

    private static FrontierEngineConfiguration<Counter, CounterProjection> configuration() {
        StateCodec<Counter> codec = new StateCodec<>() {
            @Override public byte[] encode(Counter state) { return ByteBuffer.allocate(4).putInt(state.value()).array(); }
            @Override public Counter decode(byte[] bytes) {
                if (bytes.length != 4) throw new IllegalArgumentException("counter state is malformed");
                return new Counter(ByteBuffer.wrap(bytes).getInt());
            }
        };
        return new FrontierEngineConfiguration<>(WORLD, new Counter(0), SimInstant.ZERO,
                (state, command) -> new CommandPlan.Accepted(List.of(new ProposedEvent(SUBJECT, command.payload()))),
                (state, action) -> List.of(),
                (state, event) -> new Counter(Math.addExact(state.value(), ((Delta) event.payload()).value())),
                codec, (state, world, revision, instant, query) -> new CounterProjection(world, revision, instant, state.value()),
                new EngineLimits(8, 100L, 8), List.of(), TransactionCommitter.noOp());
    }

    private static FrontierCommand command(String id, Revision revision, SimInstant instant, int value) {
        CommandId commandId = new CommandId(id);
        return new FrontierCommand(1, commandId, WORLD, revision, instant, SUBJECT, CauseChain.root(commandId), new Delta(value));
    }

    private static PayloadCodecs codecs() {
        return new PayloadCodecs(List.of(new PayloadCodec() {
            @Override public String type() { return "test.runtime_delta"; }
            @Override public byte[] encode(FrontierPayload payload) {
                return ByteBuffer.allocate(4).putInt(((Delta) payload).value()).array();
            }
            @Override public FrontierPayload decode(byte[] bytes) {
                if (bytes.length != 4) throw new IllegalArgumentException("delta payload is malformed");
                return new Delta(ByteBuffer.wrap(bytes).getInt());
            }
        }));
    }

    private record Counter(int value) { }
    private record CounterProjection(WorldId worldId, Revision revision, SimInstant instant, int value) implements FrontierProjection { }
    private record Delta(int value) implements FrontierPayload {
        @Override public String type() { return "test.runtime_delta"; }
    }
}
