package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.kernel.PayloadCodec;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;

final class BakeryRetiredEffectObservedCodec implements PayloadCodec {
    @Override public String type() { return "frontier.bakery_retired_effect_observed"; }
    @Override public byte[] encode(FrontierPayload payload) {
        var value = (BakeryRetiredEffectObserved) payload;
        return FrontierWorldPayloadCodecs.encodeProduction(out -> {
            out.writeUTF(value.identity().body().actorId().value()); out.writeLong(value.identity().body().physicalEpoch());
            ActorExecutionStateCodec.writeId(out, value.identity().execution()); out.writeBoolean(value.applied());
            BakeryHotEffectObservedCodec.write(out, value.effect());
        });
    }
    @Override public FrontierPayload decode(byte[] bytes) {
        return FrontierWorldPayloadCodecs.decodeProduction(bytes, in -> {
            var body = new ActorBodyId(new SubjectId(in.readUTF()), in.readLong());
            var identity = new ActorActuationId(body, ActorExecutionStateCodec.readId(in));
            boolean applied = in.readBoolean();
            return new BakeryRetiredEffectObserved(identity, BakeryHotEffectObservedCodec.read(in), applied);
        });
    }
}
