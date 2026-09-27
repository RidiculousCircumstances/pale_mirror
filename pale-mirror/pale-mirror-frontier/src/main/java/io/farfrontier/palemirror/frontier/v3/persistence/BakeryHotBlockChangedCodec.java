package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.kernel.PayloadCodec;
import io.farfrontier.palemirror.frontier.v3.model.BakeryHotBlockChanged;
import io.farfrontier.palemirror.frontier.v3.model.BakeryWorkState;
import java.util.Optional;

final class BakeryHotBlockChangedCodec implements PayloadCodec {
    @Override public String type() { return "frontier.bakery_hot_block_changed"; }
    @Override public byte[] encode(FrontierPayload payload) {
        BakeryHotBlockChanged value = (BakeryHotBlockChanged) payload;
        return FrontierWorldPayloadCodecs.encodeProduction(output -> {
            FrontierWorldPayloadCodecs.writeSubject(output, value.jobId());
            FrontierWorldStateCodec.writeString(output, value.leaseId().value());
            output.writeByte(value.phase().wireTag());
            output.writeBoolean(value.block().isPresent());
            if (value.block().isPresent()) BakeryWorkStateCodec.writeBlock(output, value.block().orElseThrow());
        });
    }
    @Override public FrontierPayload decode(byte[] bytes) {
        return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> new BakeryHotBlockChanged(
                FrontierWorldPayloadCodecs.readSubject(input).value(),
                new SceneLeaseId(FrontierWorldStateCodec.readString(input)),
                BakeryWorkState.Phase.fromWireTag(input.readUnsignedByte()),
                input.readBoolean() ? Optional.of(BakeryWorkStateCodec.readBlock(input)) : Optional.empty()));
    }
}
