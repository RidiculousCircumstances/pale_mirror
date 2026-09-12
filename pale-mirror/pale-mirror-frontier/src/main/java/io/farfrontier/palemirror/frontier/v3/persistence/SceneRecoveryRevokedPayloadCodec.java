package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.kernel.PayloadCodec;
import io.farfrontier.palemirror.frontier.v3.model.SceneLeaseRecoveryRevoked;

/** Exact durable no-visit recovery decision; no physical observation is encoded here. */
final class SceneRecoveryRevokedPayloadCodec implements PayloadCodec {
    @Override public String type() { return "frontier.scene_lease_recovery_revoked"; }
    @Override public byte[] encode(FrontierPayload payload) {
        return FrontierWorldPayloadCodecs.encodeProduction(output ->
                FrontierWorldPayloadCodecs.writeString(output, ((SceneLeaseRecoveryRevoked) payload).leaseId().value()));
    }
    @Override public FrontierPayload decode(byte[] bytes) {
        return FrontierWorldPayloadCodecs.decodeProduction(bytes,
                input -> new SceneLeaseRecoveryRevoked(new SceneLeaseId(FrontierWorldPayloadCodecs.readString(input))));
    }
}
