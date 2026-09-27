package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.kernel.PayloadCodec;
import io.farfrontier.palemirror.frontier.v3.model.*;

import java.util.List;
import java.util.UUID;

final class BakeryHotHandReleaseCodec implements PayloadCodec {
    @Override public String type() { return "frontier.bakery_hot_hand_release"; }
    @Override public byte[] encode(FrontierPayload payload) {
        BakeryHotHandRelease value = (BakeryHotHandRelease) payload;
        PhysicalStackAddress.ActorHand address = (PhysicalStackAddress.ActorHand) value.observedHand().address();
        return FrontierWorldPayloadCodecs.encodeProduction(output -> {
            FrontierWorldPayloadCodecs.writeSubject(output, value.jobId());
            FrontierWorldPayloadCodecs.writeSubject(output, value.actorAccountId());
            output.writeLong(value.actorEpoch());
            FrontierWorldPayloadCodecs.writeSubject(output, address.actorId());
            FrontierWorldStateCodec.writeString(output, address.entityId().toString());
            FrontierWorldStateCodec.writeString(output, value.observedHand().itemKind());
            output.writeByte(value.observedHand().quantity());
            FrontierWorldStateCodec.writeString(output, value.sceneRelease().leaseId().value());
            if (value.sceneRelease().members().size() != 1) throw new IllegalArgumentException("bakery release needs one observed baker");
            SceneMemberPosition member = value.sceneRelease().members().getFirst();
            FrontierWorldPayloadCodecs.writeSubject(output, member.actorId());
            output.writeInt(member.body().x()); output.writeInt(member.body().y()); output.writeInt(member.body().z());
            output.writeLong(member.health().raw());
        });
    }
    @Override public FrontierPayload decode(byte[] bytes) {
        return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> {
            var job = FrontierWorldPayloadCodecs.readSubject(input).value();
            var account = FrontierWorldPayloadCodecs.readSubject(input).value();
            long epoch = input.readLong();
            var hand = new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ActorHand(
                    FrontierWorldPayloadCodecs.readSubject(input).value(),
                    UUID.fromString(FrontierWorldStateCodec.readString(input))),
                    FrontierWorldStateCodec.readString(input), input.readUnsignedByte());
            var scene = new SceneLeaseId(FrontierWorldStateCodec.readString(input));
            var actor = FrontierWorldPayloadCodecs.readSubject(input).value();
            var body = new BodyPosition(input.readInt(), input.readInt(), input.readInt());
            var member = new SceneMemberPosition(actor, body, new FixedScalar(input.readLong()));
            return new BakeryHotHandRelease(job, account, epoch, hand, new SceneLeaseReleased(scene, List.of(member)));
        });
    }
}
