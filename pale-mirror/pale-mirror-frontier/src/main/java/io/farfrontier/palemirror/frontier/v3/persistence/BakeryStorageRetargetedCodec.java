package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.kernel.PayloadCodec;
import io.farfrontier.palemirror.frontier.v3.model.*;

final class BakeryStorageRetargetedCodec implements PayloadCodec {
    @Override public String type() { return "frontier.bakery_storage_retargeted"; }
    @Override public byte[] encode(FrontierPayload payload) {
        var value = (BakeryStorageRetargeted) payload;
        return FrontierWorldPayloadCodecs.encodeProduction(out -> {
            FrontierWorldPayloadCodecs.writeSubject(out, value.jobId()); out.writeInt(value.phase().wireTag());
            FrontierWorldPayloadCodecs.writeSubject(out, value.previousContainer());
            FrontierWorldPayloadCodecs.writeSubject(out, value.destinationContainer());
            ActorExecutionStateCodec.writeId(out, value.executionId());
            PedestrianRouteReceiptCodec.write(out, value.plannedRoute());
        });
    }
    @Override public FrontierPayload decode(byte[] bytes) {
        return FrontierWorldPayloadCodecs.decodeProduction(bytes, in -> new BakeryStorageRetargeted(
                FrontierWorldPayloadCodecs.readSubject(in).value(), BakeryWorkState.Phase.fromWireTag(in.readInt()),
                FrontierWorldPayloadCodecs.readSubject(in).value(), FrontierWorldPayloadCodecs.readSubject(in).value(),
                ActorExecutionStateCodec.readId(in), PedestrianRouteReceiptCodec.read(in)));
    }
}
