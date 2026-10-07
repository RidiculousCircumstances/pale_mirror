package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.model.ActorItemSlot;
import io.farfrontier.palemirror.frontier.v3.model.ActorContainerItemOrder;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/** Common inventory placement tags, independent of meals or a particular work family. */
final class ActorItemSlotCodec {
    private ActorItemSlotCodec() { }
    static void write(DataOutputStream out, ActorItemSlot slot) throws IOException {
        switch (slot) {
            case ActorItemSlot.Pocket pocket -> { out.writeByte(1); out.writeByte(pocket.index()); }
            case ActorItemSlot.Hand hand -> { out.writeByte(2);
                out.writeByte(switch (hand.hand()) { case MAIN -> 1; case OFF -> 2; }); }
        }
    }
    static ActorItemSlot read(DataInputStream in) throws IOException {
        return switch (in.readUnsignedByte()) {
            case 1 -> new ActorItemSlot.Pocket(in.readUnsignedByte());
            case 2 -> new ActorItemSlot.Hand(switch (in.readUnsignedByte()) {
                case 1 -> ActorContainerItemOrder.Hand.MAIN; case 2 -> ActorContainerItemOrder.Hand.OFF;
                default -> throw new IllegalArgumentException("unknown actor inventory hand tag"); });
            default -> throw new IllegalArgumentException("unknown actor inventory slot tag");
        };
    }
}
