package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.kernel.PayloadCodec;
import io.farfrontier.palemirror.frontier.v3.model.*;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class BakeryHotEffectObservedCodec implements PayloadCodec {
    @Override public String type() { return "frontier.bakery_hot_effect_observed"; }
    @Override public byte[] encode(FrontierPayload payload) {
        BakeryHotEffectObserved value = (BakeryHotEffectObserved) payload;
        return FrontierWorldPayloadCodecs.encodeProduction(output -> {
            FrontierWorldPayloadCodecs.writeSubject(output, value.jobId());
            FrontierWorldStateCodec.writeString(output, value.leaseId().value());
            output.writeByte(value.phase().wireTag());
            output.writeInt(value.observedWorker().x()); output.writeInt(value.observedWorker().y()); output.writeInt(value.observedWorker().z());
            output.writeLong(value.sourceEpoch()); output.writeLong(value.destinationEpoch());
            writeStacks(output, value.remainingSource()); writeStacks(output, value.destination());
        });
    }
    @Override public FrontierPayload decode(byte[] bytes) {
        return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> new BakeryHotEffectObserved(
                FrontierWorldPayloadCodecs.readSubject(input).value(),
                new SceneLeaseId(FrontierWorldStateCodec.readString(input)),
                BakeryWorkState.Phase.fromWireTag(input.readUnsignedByte()),
                new BodyPosition(input.readInt(), input.readInt(), input.readInt()),
                input.readLong(), input.readLong(), readStacks(input), readStacks(input)));
    }

    private static void writeStacks(DataOutputStream output, List<FungiblePhysicalObservation.Stack> stacks) throws IOException {
        output.writeByte(stacks.size());
        for (FungiblePhysicalObservation.Stack stack : stacks) {
            if (stack.address() instanceof PhysicalStackAddress.ContainerSlot slot) {
                output.writeByte(0); FrontierWorldPayloadCodecs.writeSubject(output, slot.slot().containerId()); output.writeByte(slot.slot().slot());
            } else if (stack.address() instanceof PhysicalStackAddress.ActorHand hand) {
                output.writeByte(1); FrontierWorldPayloadCodecs.writeSubject(output, hand.actorId());
                FrontierWorldStateCodec.writeString(output, hand.entityId().toString());
            } else throw new IllegalArgumentException("bakery observes only its chest or exact actor hand");
            FrontierWorldStateCodec.writeString(output, stack.itemKind()); output.writeByte(stack.quantity());
        }
    }

    private static List<FungiblePhysicalObservation.Stack> readStacks(DataInputStream input) throws IOException {
        int count = input.readUnsignedByte();
        if (count > 27) throw new IllegalArgumentException("bakery physical witness exceeds one bounded chest");
        List<FungiblePhysicalObservation.Stack> result = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            PhysicalStackAddress address = switch (input.readUnsignedByte()) {
                case 0 -> new PhysicalStackAddress.ContainerSlot(new InventoryCustody.ContainerSlot(
                        FrontierWorldPayloadCodecs.readSubject(input).value(), input.readUnsignedByte()));
                case 1 -> new PhysicalStackAddress.ActorHand(FrontierWorldPayloadCodecs.readSubject(input).value(),
                        UUID.fromString(FrontierWorldStateCodec.readString(input)));
                default -> throw new IllegalArgumentException("unknown bakery witness address");
            };
            result.add(new FungiblePhysicalObservation.Stack(address, FrontierWorldStateCodec.readString(input), input.readUnsignedByte()));
        }
        return List.copyOf(result);
    }
}
