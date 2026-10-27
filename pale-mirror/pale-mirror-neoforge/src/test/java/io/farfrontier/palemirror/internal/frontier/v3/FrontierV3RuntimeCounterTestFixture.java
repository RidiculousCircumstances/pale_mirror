package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import java.nio.ByteBuffer;
import java.util.List;

/** Pure stateful kernel fixture for persistence, budgets and clocks. */
final class FrontierV3RuntimeCounterTestFixture {
    private FrontierV3RuntimeCounterTestFixture() { }
    static final WorldId WORLD = new WorldId("frontier:runtime");
    static final SubjectId SUBJECT = new SubjectId("settlement:runtime");
    static FrontierEngineConfiguration<Counter, CounterProjection> configuration() {
        return configuration(new StateCodec<>() {
            @Override public byte[] encode(Counter state) { return ByteBuffer.allocate(4).putInt(state.value()).array(); }
            @Override public Counter decode(byte[] bytes) {
                if (bytes.length != 4) throw new IllegalArgumentException("counter state is malformed");
                return new Counter(ByteBuffer.wrap(bytes).getInt());
            }
        });
    }

    static FrontierEngineConfiguration<Counter, CounterProjection> configuration(StateCodec<Counter> codec) {
        return new FrontierEngineConfiguration<>(WORLD, new Counter(0), SimInstant.ZERO,
                (state, command) -> new CommandPlan.Accepted(List.of(new ProposedEvent(SUBJECT, command.payload()))),
                (state, action) -> List.of(),
                (state, event) -> new Counter(Math.addExact(state.value(), ((Delta) event.payload()).value())),
                codec, (state, world, revision, instant, query) -> new CounterProjection(world, revision, instant, state.value()),
                new EngineLimits(8, 100L, 8), List.of(), TransactionCommitter.noOp());
    }

    static FrontierCommand command(String id, Revision revision, SimInstant instant, int value) {
        CommandId commandId = new CommandId(id);
        return new FrontierCommand(1, commandId, WORLD, revision, instant, SUBJECT, CauseChain.root(commandId), new Delta(value));
    }

    static PayloadCodecs codecs() {
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

    record Counter(int value) { }
    record CounterProjection(WorldId worldId, Revision revision, SimInstant instant, int value) implements FrontierProjection { }
    record Delta(int value) implements FrontierPayload {
        @Override public String type() { return "test.runtime_delta"; }
    }

    static final class CountingCounterCodec implements StateCodec<Counter> {
        int encodeCalls;
        int decodeCalls;

        @Override public byte[] encode(Counter state) {
            encodeCalls++;
            return ByteBuffer.allocate(4).putInt(state.value()).array();
        }

        @Override public Counter decode(byte[] bytes) {
            decodeCalls++;
            if (bytes.length != 4) throw new IllegalArgumentException("counter state is malformed");
            return new Counter(ByteBuffer.wrap(bytes).getInt());
        }

        void reset() {
            encodeCalls = 0;
            decodeCalls = 0;
        }
    }
}
