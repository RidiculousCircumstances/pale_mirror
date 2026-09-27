package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.kernel.PayloadCodec;
import io.farfrontier.palemirror.frontier.v3.model.BakeryInputReallocated;

final class BakeryInputReallocatedCodec implements PayloadCodec {
    @Override public String type() { return "frontier.bakery_input_reallocated"; }
    @Override public byte[] encode(FrontierPayload payload) {
        BakeryInputReallocated value = (BakeryInputReallocated) payload;
        return FrontierWorldPayloadCodecs.encodeProduction(output -> {
            FrontierWorldPayloadCodecs.writeSubject(output, value.jobId());
            FrontierWorldPayloadCodecs.writeSubject(output, value.replacement().itemId());
            ProductionJobStateCodec.writeHold(output, value.replacement());
        });
    }
    @Override public FrontierPayload decode(byte[] bytes) {
        return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> new BakeryInputReallocated(
                FrontierWorldPayloadCodecs.readSubject(input).value(),
                ProductionJobStateCodec.readHold(input, FrontierWorldPayloadCodecs.readSubject(input).value())));
    }
}
