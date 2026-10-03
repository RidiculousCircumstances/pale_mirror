package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionResumed;
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
            });
        }
        @Override public FrontierPayload decode(byte[] bytes) {
            return FrontierWorldPayloadCodecs.decodeProduction(bytes, in -> new ActorExecutionResumed(
                    ActorExecutionStateCodec.readId(in), ActorExecutionStateCodec.readId(in), in.readLong()));
        }
    })); }
}
