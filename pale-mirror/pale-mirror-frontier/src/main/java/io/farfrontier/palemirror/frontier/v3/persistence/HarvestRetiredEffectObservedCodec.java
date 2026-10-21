package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.PayloadCodec;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;

final class HarvestRetiredEffectObservedCodec implements PayloadCodec {
    @Override public String type() { return "frontier.harvest_retired_effect_observed"; }
    @Override public byte[] encode(FrontierPayload payload) {
        var value = (HarvestRetiredEffectObserved) payload;
        return FrontierWorldPayloadCodecs.encodeProduction(out -> {
            out.writeUTF(value.identity().body().actorId().value()); out.writeLong(value.identity().body().physicalEpoch());
            ActorExecutionStateCodec.writeId(out, value.identity().execution()); out.writeBoolean(value.applied());
            byte[] effect = ResourceSitePayloadCodecs.harvestProgressed().encode(value.effect());
            out.writeInt(effect.length); out.write(effect);
        });
    }
    @Override public FrontierPayload decode(byte[] bytes) {
        return FrontierWorldPayloadCodecs.decodeProduction(bytes, in -> {
            var body = new ActorBodyId(new SubjectId(in.readUTF()), in.readLong());
            var identity = new ActorActuationId(body, ActorExecutionStateCodec.readId(in));
            boolean applied = in.readBoolean(); int length = in.readInt();
            if (length < 1 || length > 2048) throw new IllegalArgumentException("retired field receipt exceeds its bound");
            byte[] effect = in.readNBytes(length);
            if (effect.length != length) throw new IllegalArgumentException("truncated retired field receipt");
            return new HarvestRetiredEffectObserved(identity,
                    (ResourceSiteHarvestProgressed) ResourceSitePayloadCodecs.harvestProgressed().decode(effect), applied);
        });
    }
}
