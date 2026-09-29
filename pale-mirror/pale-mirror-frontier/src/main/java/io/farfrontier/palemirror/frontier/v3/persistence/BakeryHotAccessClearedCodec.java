package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.kernel.PayloadCodec;
import io.farfrontier.palemirror.frontier.v3.model.BakeryHotAccessCleared;
import io.farfrontier.palemirror.frontier.v3.model.BodyPosition;

final class BakeryHotAccessClearedCodec implements PayloadCodec {
    @Override public String type() { return "frontier.bakery_hot_access_cleared"; }

    @Override public byte[] encode(FrontierPayload payload) {
        BakeryHotAccessCleared cleared = (BakeryHotAccessCleared) payload;
        return FrontierWorldPayloadCodecs.encodeProduction(output -> {
            FrontierWorldPayloadCodecs.writeSubject(output, cleared.jobId());
            FrontierWorldPayloadCodecs.writeString(output, cleared.leaseId().value());
            output.writeInt(cleared.observedWorker().x());
            output.writeInt(cleared.observedWorker().y());
            output.writeInt(cleared.observedWorker().z());
        });
    }

    @Override public FrontierPayload decode(byte[] bytes) {
        return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> new BakeryHotAccessCleared(
                FrontierWorldPayloadCodecs.readSubject(input).value(),
                new SceneLeaseId(FrontierWorldPayloadCodecs.readString(input)),
                new BodyPosition(input.readInt(), input.readInt(), input.readInt())));
    }
}
