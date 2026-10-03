package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.kernel.PayloadCodec;
import io.farfrontier.palemirror.frontier.v3.model.BakeryHotHandMaterialized;
import io.farfrontier.palemirror.frontier.v3.model.FungiblePhysicalObservation;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalStackAddress;

import java.util.UUID;

final class BakeryHotHandMaterializedCodec implements PayloadCodec {
    @Override public String type() { return "frontier.bakery_hot_hand_materialized"; }
    @Override public byte[] encode(FrontierPayload payload) {
        BakeryHotHandMaterialized value = (BakeryHotHandMaterialized) payload;
        PhysicalStackAddress.ActorHand hand = (PhysicalStackAddress.ActorHand) value.observedHand().address();
        return FrontierWorldPayloadCodecs.encodeProduction(output -> {
            FrontierWorldPayloadCodecs.writeSubject(output, value.jobId());
            FrontierWorldStateCodec.writeString(output, value.leaseId().value());
            FrontierWorldPayloadCodecs.writeSubject(output, value.actorAccountId());
            output.writeLong(value.actorEpoch());
            FrontierWorldPayloadCodecs.writeSubject(output, hand.actorId());
            FrontierWorldStateCodec.writeString(output, hand.entityId().toString());
            FrontierWorldStateCodec.writeString(output, value.observedHand().itemKind());
            output.writeByte(value.observedHand().quantity());
        });
    }
    @Override public FrontierPayload decode(byte[] bytes) {
        return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> new BakeryHotHandMaterialized(
                FrontierWorldPayloadCodecs.readSubject(input).value(),
                new SceneLeaseId(FrontierWorldStateCodec.readString(input)),
                FrontierWorldPayloadCodecs.readSubject(input).value(), input.readLong(),
                new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ActorHand(
                        FrontierWorldPayloadCodecs.readSubject(input).value(),
                        UUID.fromString(FrontierWorldStateCodec.readString(input)),
                        io.farfrontier.palemirror.frontier.v3.model.ActorContainerItemOrder.Hand.MAIN),
                        FrontierWorldStateCodec.readString(input), input.readUnsignedByte())));
    }
}
