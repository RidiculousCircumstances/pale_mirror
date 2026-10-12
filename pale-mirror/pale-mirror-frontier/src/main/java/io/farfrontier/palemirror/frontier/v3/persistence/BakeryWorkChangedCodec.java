package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.PayloadCodec;
import io.farfrontier.palemirror.frontier.v3.model.BakeryWorkChanged;

final class BakeryWorkChangedCodec implements PayloadCodec {
    @Override public String type() { return "frontier.bakery_work_changed"; }
    @Override public byte[] encode(FrontierPayload payload) {
        var value = (BakeryWorkChanged) payload;
        return FrontierWorldPayloadCodecs.encodeProduction(out -> {
            FrontierWorldPayloadCodecs.writeSubject(out, value.jobId());
            FrontierWorldStateCodec.writeString(out, value.leaseId().value());
            ActorExecutionStateCodec.writeId(out, value.executionId());
            FrontierWorldPayloadCodecs.writeBody(out, value.observedWorker());
            BakeryLabourChangeCodec.write(out, value.change());
        });
    }
    @Override public FrontierPayload decode(byte[] bytes) {
        return FrontierWorldPayloadCodecs.decodeProduction(bytes, in -> new BakeryWorkChanged(
                FrontierWorldPayloadCodecs.readSubject(in).value(),
                new SceneLeaseId(FrontierWorldStateCodec.readString(in)), ActorExecutionStateCodec.readId(in),
                FrontierWorldPayloadCodecs.readBody(in), BakeryLabourChangeCodec.read(in)));
    }
}
