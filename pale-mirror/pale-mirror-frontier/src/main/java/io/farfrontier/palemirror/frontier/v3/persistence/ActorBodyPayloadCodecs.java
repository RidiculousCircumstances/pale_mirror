package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.kernel.PayloadCodec;
import io.farfrontier.palemirror.frontier.v3.kernel.PayloadCodecs;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyReleased;
import java.util.List;

final class ActorBodyPayloadCodecs {
    private ActorBodyPayloadCodecs() { }
    static PayloadCodecs create() { return new PayloadCodecs(List.of(new PayloadCodec() {
        @Override public String type() { return "frontier.actor_body_released"; }
        @Override public byte[] encode(FrontierPayload payload) {
            var released = (ActorBodyReleased) payload;
            return FrontierWorldPayloadCodecs.encodeProduction(out -> {
                FrontierWorldPayloadCodecs.writeSubject(out, released.body().actorId());
                out.writeLong(released.body().physicalEpoch());
            });
        }
        @Override public FrontierPayload decode(byte[] bytes) {
            return FrontierWorldPayloadCodecs.decodeProduction(bytes, in -> new ActorBodyReleased(
                    new ActorBodyId(FrontierWorldPayloadCodecs.readSubject(in).value(), in.readLong())));
        }
    })); }
}
