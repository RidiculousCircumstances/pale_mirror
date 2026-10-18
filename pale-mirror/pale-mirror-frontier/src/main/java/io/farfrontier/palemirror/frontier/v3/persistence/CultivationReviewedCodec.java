package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.kernel.PayloadCodec;
import io.farfrontier.palemirror.frontier.v3.model.CultivationReviewed;
import java.nio.ByteBuffer;

final class CultivationReviewedCodec implements PayloadCodec {
    @Override public String type() { return CultivationReviewed.TYPE; }
    @Override public byte[] encode(FrontierPayload payload) {
        var value = (CultivationReviewed) payload;
        return ByteBuffer.allocate(16).putLong(value.previousReviewTick()).putLong(value.atTick()).array();
    }
    @Override public FrontierPayload decode(byte[] bytes) {
        if (bytes.length != 16) throw new IllegalArgumentException("invalid cultivation review encoding");
        var input = ByteBuffer.wrap(bytes); return new CultivationReviewed(input.getLong(), input.getLong());
    }
}
