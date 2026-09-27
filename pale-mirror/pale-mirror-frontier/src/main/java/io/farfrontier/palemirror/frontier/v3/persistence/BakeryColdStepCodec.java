package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.kernel.PayloadCodec;
import io.farfrontier.palemirror.frontier.v3.model.BakeryColdStep;
import io.farfrontier.palemirror.frontier.v3.model.BakeryWorkState;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;

/** Explicit WAL tag for one retained bakery COLD step. */
final class BakeryColdStepCodec implements PayloadCodec {
    @Override public String type() { return "frontier.bakery_cold_step"; }

    @Override public byte[] encode(FrontierPayload payload) {
        BakeryColdStep step = (BakeryColdStep) payload;
        return FrontierWorldPayloadCodecs.encodeProduction(output -> {
            FrontierWorldPayloadCodecs.writeSubject(output, step.jobId());
            output.writeByte(step.expectedPhase().wireTag());
            output.writeByte(step.action().wireTag());
            output.writeInt(step.nextSurface().x());
            output.writeInt(step.nextSurface().y());
            output.writeInt(step.nextSurface().z());
        });
    }

    @Override public FrontierPayload decode(byte[] bytes) {
        return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> new BakeryColdStep(
                FrontierWorldPayloadCodecs.readSubject(input).value(),
                BakeryWorkState.Phase.fromWireTag(input.readUnsignedByte()),
                BakeryColdStep.Action.fromWireTag(input.readUnsignedByte()),
                new SurfaceAnchor(new BlockPosition(input.readInt(), input.readInt(), input.readInt()))));
    }
}
