package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.kernel.PayloadCodec;
import io.farfrontier.palemirror.frontier.v3.model.*;


final class BakeryHotEffectObservedCodec implements PayloadCodec {
    @Override public String type() { return "frontier.bakery_hot_effect_observed"; }
    @Override public byte[] encode(FrontierPayload payload) {
        BakeryHotEffectObserved value = (BakeryHotEffectObserved) payload;
        return FrontierWorldPayloadCodecs.encodeProduction(output -> {
            FrontierWorldPayloadCodecs.writeSubject(output, value.jobId());
            FrontierWorldStateCodec.writeString(output, value.leaseId().value());
            output.writeByte(value.phase().wireTag());
            output.writeInt(value.observedWorker().x()); output.writeInt(value.observedWorker().y()); output.writeInt(value.observedWorker().z());
            output.writeLong(value.sourceEpoch()); output.writeLong(value.destinationEpoch());
            PhysicalObservationStackCodec.writeStacks(output, value.remainingSource()); PhysicalObservationStackCodec.writeStacks(output, value.destination());
        });
    }
    @Override public FrontierPayload decode(byte[] bytes) {
        return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> new BakeryHotEffectObserved(
                FrontierWorldPayloadCodecs.readSubject(input).value(),
                new SceneLeaseId(FrontierWorldStateCodec.readString(input)),
                BakeryWorkState.Phase.fromWireTag(input.readUnsignedByte()),
                new BodyPosition(input.readInt(), input.readInt(), input.readInt()),
                input.readLong(), input.readLong(), PhysicalObservationStackCodec.readStacks(input), PhysicalObservationStackCodec.readStacks(input)));
    }

}
