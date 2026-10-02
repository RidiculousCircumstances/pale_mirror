package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.kernel.PayloadCodec;
import io.farfrontier.palemirror.frontier.v3.model.ReferenceSurfaceVerified;

final class ReferenceSurfaceVerifiedCodec implements PayloadCodec {
    @Override public String type() { return "frontier.reference_surface_verified"; }
    @Override public byte[] encode(FrontierPayload payload) {
        var value = (ReferenceSurfaceVerified) payload;
        return FrontierWorldPayloadCodecs.encodeProduction(output -> {
            FrontierWorldPayloadCodecs.writeSubject(output, value.containerId());
            output.writeLong(value.expectedCanonicalRevision()); output.writeLong(value.expectedReplicaRevision());
            output.writeLong(value.expectedSurfaceEpoch());
            FrontierWorldStateCodec.writeString(output, value.fingerprint());
            FrontierWorldStateCodec.writeString(output, value.provenance());
        });
    }
    @Override public FrontierPayload decode(byte[] bytes) {
        return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> new ReferenceSurfaceVerified(
                FrontierWorldPayloadCodecs.readSubject(input).value(), input.readLong(), input.readLong(), input.readLong(),
                FrontierWorldStateCodec.readString(input), FrontierWorldStateCodec.readString(input)));
    }
}
