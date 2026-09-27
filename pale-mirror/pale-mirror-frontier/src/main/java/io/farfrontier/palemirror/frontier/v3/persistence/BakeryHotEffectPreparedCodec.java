package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.kernel.PayloadCodec;
import io.farfrontier.palemirror.frontier.v3.model.BakeryHotEffectPrepared;
import io.farfrontier.palemirror.frontier.v3.model.BakeryWorkState;

final class BakeryHotEffectPreparedCodec implements PayloadCodec {
    @Override public String type() { return "frontier.bakery_hot_effect_prepared"; }
    @Override public byte[] encode(FrontierPayload payload) {
        BakeryHotEffectPrepared value = (BakeryHotEffectPrepared) payload;
        return FrontierWorldPayloadCodecs.encodeProduction(output -> {
            FrontierWorldPayloadCodecs.writeSubject(output, value.jobId());
            FrontierWorldStateCodec.writeString(output, value.leaseId().value());
            output.writeByte(value.phase().wireTag());
            output.writeByte(value.destinationSlot());
        });
    }
    @Override public FrontierPayload decode(byte[] bytes) {
        return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> new BakeryHotEffectPrepared(
                FrontierWorldPayloadCodecs.readSubject(input).value(),
                new SceneLeaseId(FrontierWorldStateCodec.readString(input)),
                BakeryWorkState.Phase.fromWireTag(input.readUnsignedByte()), input.readByte()));
    }
}
