package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.kernel.PayloadCodec;
import io.farfrontier.palemirror.frontier.v3.model.BakeryHotWorkTick;
import io.farfrontier.palemirror.frontier.v3.model.BodyPosition;

final class BakeryHotWorkTickCodec implements PayloadCodec {
    @Override public String type() { return "frontier.bakery_hot_work_tick"; }
    @Override public byte[] encode(FrontierPayload payload) {
        BakeryHotWorkTick value = (BakeryHotWorkTick) payload;
        return FrontierWorldPayloadCodecs.encodeProduction(output -> {
            FrontierWorldPayloadCodecs.writeSubject(output, value.jobId());
            FrontierWorldStateCodec.writeString(output, value.leaseId().value());
            output.writeInt(value.observedWorker().x()); output.writeInt(value.observedWorker().y()); output.writeInt(value.observedWorker().z());
            output.writeByte(value.nextCompletedTicks());
        });
    }
    @Override public FrontierPayload decode(byte[] bytes) {
        return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> new BakeryHotWorkTick(
                FrontierWorldPayloadCodecs.readSubject(input).value(), new SceneLeaseId(FrontierWorldStateCodec.readString(input)),
                new BodyPosition(input.readInt(), input.readInt(), input.readInt()), input.readUnsignedByte()));
    }
}
