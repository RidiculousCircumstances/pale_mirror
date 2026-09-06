package io.farfrontier.palemirror.frontier.v3.kernel;

import io.farfrontier.palemirror.frontier.v3.api.CauseChain;
import io.farfrontier.palemirror.frontier.v3.api.CommandId;
import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.FrontierEvent;
import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.FrontierProjection;
import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.Revision;
import io.farfrontier.palemirror.frontier.v3.api.ScheduleId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;

import java.nio.ByteBuffer;
import java.util.List;

/** Standalone fresh-JVM deterministic transcript fixture for the Wave 1 exit gate. */
public final class DeterminismFixture {
    private static final WorldId WORLD = new WorldId("frontier:determinism");
    private static final SubjectId SUBJECT = new SubjectId("settlement:determinism");

    private DeterminismFixture() {}

    public static void main(String[] ignored) {
        InMemoryFrontierEngine<State, View> engine = new InMemoryFrontierEngine<>(WORLD, new State(0), SimInstant.ZERO,
                (state, command) -> new CommandPlan.Accepted(List.of(new ProposedEvent(SUBJECT, command.payload()))),
                (state, action) -> List.of(new ProposedEvent(action.subject(), new Delta(action.weight()))),
                (state, event) -> new State(Math.addExact(state.value(), ((Delta) event.payload()).value())),
                state -> ByteBuffer.allocate(4).putInt(state.value()).array(),
                (state, world, revision, instant, query) -> new View(world, revision, instant, state.value()),
                new EngineLimits(16, 20L, 16), List.of(new ScheduledAction(new ScheduleId("schedule:determinism"),
                        new SimInstant(3L), 0, SUBJECT, "process.fixture", 2)));
        CommandId id = new CommandId("command:determinism");
        engine.submit(new FrontierCommand(1, id, WORLD, Revision.ZERO, SimInstant.ZERO, SUBJECT,
                CauseChain.root(id), new Delta(3)));
        engine.advanceTo(new SimInstant(3L), new WorkBudget(2, 2));
        PayloadCodecs codecs = PayloadCodecs.merge(
                new PayloadCodecs(List.of(new DeltaCodec())), KernelPayloadCodecs.scheduleEffects());
        StringBuilder transcript = new StringBuilder();
        for (TransactionRecord transaction : engine.transactions()) {
            transcript.append(java.util.HexFormat.of().formatHex(KernelCodec.encodeTransaction(transaction, codecs))).append('\n');
        }
        transcript.append(java.util.HexFormat.of().formatHex(engine.checkpoint().canonicalState()));
        System.out.print(transcript);
    }

    private record State(int value) {}
    private record View(WorldId worldId, Revision revision, SimInstant instant, int value) implements FrontierProjection {}
    private record Delta(int value) implements FrontierPayload { @Override public String type() { return "fixture.delta"; } }
    private static final class DeltaCodec implements PayloadCodec {
        @Override public String type() { return "fixture.delta"; }
        @Override public byte[] encode(FrontierPayload payload) { return ByteBuffer.allocate(4).putInt(((Delta) payload).value()).array(); }
        @Override public FrontierPayload decode(byte[] bytes) { return new Delta(ByteBuffer.wrap(bytes).getInt()); }
    }
}
