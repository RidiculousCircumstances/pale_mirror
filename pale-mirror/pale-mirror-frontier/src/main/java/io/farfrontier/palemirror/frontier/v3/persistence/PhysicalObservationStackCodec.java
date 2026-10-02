package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.model.*;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Common bounded container/actor witness grammar; operation owners validate allowed custody. */
final class PhysicalObservationStackCodec {
    private PhysicalObservationStackCodec() { }
    static void writeStacks(DataOutputStream output, List<FungiblePhysicalObservation.Stack> stacks) throws IOException {
        if (stacks.size() > 27) throw new IllegalArgumentException("physical witness exceeds one bounded chest");
        output.writeByte(stacks.size());
        for (var stack : stacks) {
            switch (stack.address()) {
                case PhysicalStackAddress.ContainerSlot slot -> {
                    output.writeByte(0); FrontierWorldPayloadCodecs.writeSubject(output, slot.slot().containerId());
                    output.writeByte(slot.slot().slot());
                }
                case PhysicalStackAddress.ActorHand hand -> {
                    // Tags 0/1 retain their existing container/OFF-hand meaning.
                    output.writeByte(hand.hand() == ActorContainerItemOrder.Hand.OFF ? 1 : 2);
                    FrontierWorldPayloadCodecs.writeSubject(output, hand.actorId());
                    FrontierWorldStateCodec.writeString(output, hand.entityId().toString());
                }
                case PhysicalStackAddress.ActorPocket pocket -> {
                    output.writeByte(3); FrontierWorldPayloadCodecs.writeSubject(output, pocket.actorId());
                    FrontierWorldStateCodec.writeString(output, pocket.entityId().toString()); output.writeByte(pocket.slot());
                }
                default -> throw new IllegalArgumentException("operation witness requires a container or declared actor slot");
            }
            FrontierWorldStateCodec.writeString(output, stack.itemKind()); output.writeByte(stack.quantity());
        }
    }
    static List<FungiblePhysicalObservation.Stack> readStacks(DataInputStream input) throws IOException {
        int count = input.readUnsignedByte();
        if (count > 27) throw new IllegalArgumentException("physical witness exceeds one bounded chest");
        List<FungiblePhysicalObservation.Stack> result = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            PhysicalStackAddress address = switch (input.readUnsignedByte()) {
                case 0 -> new PhysicalStackAddress.ContainerSlot(new InventoryCustody.ContainerSlot(
                        FrontierWorldPayloadCodecs.readSubject(input).value(), input.readUnsignedByte()));
                case 1 -> new PhysicalStackAddress.ActorHand(FrontierWorldPayloadCodecs.readSubject(input).value(),
                        UUID.fromString(FrontierWorldStateCodec.readString(input)), ActorContainerItemOrder.Hand.OFF);
                case 2 -> new PhysicalStackAddress.ActorHand(FrontierWorldPayloadCodecs.readSubject(input).value(),
                        UUID.fromString(FrontierWorldStateCodec.readString(input)), ActorContainerItemOrder.Hand.MAIN);
                case 3 -> new PhysicalStackAddress.ActorPocket(FrontierWorldPayloadCodecs.readSubject(input).value(),
                        UUID.fromString(FrontierWorldStateCodec.readString(input)), input.readUnsignedByte());
                default -> throw new IllegalArgumentException("unknown operation witness address");
            };
            result.add(new FungiblePhysicalObservation.Stack(address, FrontierWorldStateCodec.readString(input), input.readUnsignedByte()));
        }
        return List.copyOf(result);
    }
}
