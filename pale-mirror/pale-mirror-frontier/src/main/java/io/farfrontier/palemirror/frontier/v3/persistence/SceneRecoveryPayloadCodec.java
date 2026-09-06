package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.model.*;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.kernel.PayloadCodec;

import java.util.LinkedHashSet;
import java.util.Set;

/** Stable binary codec for loaded-world proof that exact restart-scene recovery is impossible. */
final class SceneRecoveryPayloadCodec implements PayloadCodec {
    @Override public String type() { return "frontier.scene_lease_recovery_unresolved"; }
    @Override public byte[] encode(FrontierPayload payload) {
        return FrontierWorldPayloadCodecs.encodeProduction(output -> {
            SceneLeaseRecoveryUnresolved unresolved = (SceneLeaseRecoveryUnresolved) payload;
            FrontierWorldPayloadCodecs.writeString(output, unresolved.leaseId().value()); output.writeByte(unresolved.missingActorIds().size());
            for (SubjectId actor : unresolved.missingActorIds().stream().sorted().toList()) FrontierWorldPayloadCodecs.writeSubject(output, actor);
            output.writeBoolean(unresolved.missingCargoCarrier());
        });
    }
    @Override public FrontierPayload decode(byte[] bytes) {
        return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> {
            SceneLeaseId lease = new SceneLeaseId(FrontierWorldPayloadCodecs.readString(input)); Set<SubjectId> missing = new LinkedHashSet<>();
            for (int index = 0, count = input.readUnsignedByte(); index < count; index++) missing.add(FrontierWorldPayloadCodecs.readSubject(input).value());
            return new SceneLeaseRecoveryUnresolved(lease, missing, input.readBoolean());
        });
    }
}
