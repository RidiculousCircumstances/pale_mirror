package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionResumed;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorPresenceStarted;
import io.farfrontier.palemirror.frontier.v3.kernel.PayloadCodec;
import io.farfrontier.palemirror.frontier.v3.kernel.PayloadCodecs;
import java.util.List;

final class ActorExecutionPayloadCodecs {
    private ActorExecutionPayloadCodecs() { }
    static PayloadCodecs create() { return new PayloadCodecs(List.of(new PayloadCodec() {
        @Override public String type() { return "frontier.actor_execution_resumed"; }
        @Override public byte[] encode(FrontierPayload payload) {
            var resumed = (ActorExecutionResumed) payload;
            return FrontierWorldPayloadCodecs.encodeProduction(out -> {
                ActorExecutionStateCodec.writeId(out, resumed.suspended());
                ActorExecutionStateCodec.writeId(out, resumed.successor()); out.writeLong(resumed.atTick());
                out.writeBoolean(resumed.releasing().isPresent());
                if (resumed.releasing().isPresent()) ActorExecutionStateCodec.writeId(out, resumed.releasing().orElseThrow());
            });
        }
        @Override public FrontierPayload decode(byte[] bytes) {
            return FrontierWorldPayloadCodecs.decodeProduction(bytes, in -> {
                var suspended = ActorExecutionStateCodec.readId(in);
                var successor = ActorExecutionStateCodec.readId(in);
                long atTick = in.readLong();
                var releasing = in.readBoolean() ? java.util.Optional.of(ActorExecutionStateCodec.readId(in))
                        : java.util.Optional.<io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId>empty();
                return new ActorExecutionResumed(suspended, successor, releasing, atTick);
            });
        }
    }, new PayloadCodec() {
        @Override public String type() { return "frontier.actor_presence_started"; }
        @Override public byte[] encode(FrontierPayload payload) {
            var presence = (ActorPresenceStarted) payload;
            return FrontierWorldPayloadCodecs.encodeProduction(out -> {
                ActorExecutionStateCodec.writeId(out, presence.execution()); out.writeLong(presence.atTick());
            });
        }
        @Override public FrontierPayload decode(byte[] bytes) {
            return FrontierWorldPayloadCodecs.decodeProduction(bytes, in -> new ActorPresenceStarted(
                    ActorExecutionStateCodec.readId(in), in.readLong()));
        }
    })); }
}
