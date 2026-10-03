package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.PayloadCodec;
import io.farfrontier.palemirror.frontier.v3.model.*;

final class BakerySceneReconciledCodec implements PayloadCodec {
    @Override public String type() { return "frontier.bakery_scene_reconciled"; }
    @Override public byte[] encode(FrontierPayload payload) {
        var value = (BakerySceneReconciled) payload;
        var hand = (PhysicalStackAddress.ActorHand) value.observedHand().address();
        return FrontierWorldPayloadCodecs.encodeProduction(out -> {
            FrontierWorldPayloadCodecs.writeSubject(out, value.jobId());
            FrontierWorldPayloadCodecs.writeString(out, value.leaseId().value());
            out.writeLong(value.leaseRevision()); out.writeLong(value.recoveryEpoch());
            out.writeInt(value.observedBody().x()); out.writeInt(value.observedBody().y()); out.writeInt(value.observedBody().z());
            FrontierWorldPayloadCodecs.writeSubject(out, hand.actorId());
            FrontierWorldPayloadCodecs.writeString(out, hand.entityId().toString());
            FrontierWorldPayloadCodecs.writeString(out, value.observedHand().itemKind()); out.writeInt(value.observedHand().quantity());
        });
    }
    @Override public FrontierPayload decode(byte[] bytes) {
        return FrontierWorldPayloadCodecs.decodeProduction(bytes, in -> new BakerySceneReconciled(
                FrontierWorldPayloadCodecs.readSubject(in).value(), new SceneLeaseId(FrontierWorldPayloadCodecs.readString(in)),
                in.readLong(), in.readLong(), new BodyPosition(in.readInt(), in.readInt(), in.readInt()),
                new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ActorHand(
                        FrontierWorldPayloadCodecs.readSubject(in).value(), java.util.UUID.fromString(FrontierWorldPayloadCodecs.readString(in)),
                        ActorContainerItemOrder.Hand.MAIN), FrontierWorldPayloadCodecs.readString(in), in.readInt())));
    }
}
