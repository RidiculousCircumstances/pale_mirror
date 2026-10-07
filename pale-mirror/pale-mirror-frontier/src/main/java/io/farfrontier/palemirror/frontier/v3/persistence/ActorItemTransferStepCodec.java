package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.model.*;
import java.io.*;
import java.util.ArrayList;

final class ActorItemTransferStepCodec {
    private ActorItemTransferStepCodec() { }
    static void write(DataOutputStream out, ActorItemTransferStep step) throws IOException {
        ActorHotObservationCodec.write(out, step.observation()); out.writeByte(step.source().size());
        for (var slice : step.source()) {
            FungibleResourceStateCodec.writePhysicalAddress(out, slice.address());
            out.writeByte(slice.before()); out.writeByte(slice.moved()); out.writeLong(slice.epoch());
        }
        out.writeLong(step.destinationEpoch());
        out.writeInt(step.destinationSlot()); out.writeInt(step.destinationBefore());
    }
    static ActorItemTransferStep read(DataInputStream in) throws IOException {
        var observation = ActorHotObservationCodec.read(in); int count = in.readUnsignedByte();
        if (count < 1 || count > 27) throw new IllegalArgumentException("invalid inventory interaction preimage size");
        var slices = new ArrayList<MaterialSourceSelection.Slice>();
        for (int i = 0; i < count; i++) slices.add(new MaterialSourceSelection.Slice(
                FungibleResourceStateCodec.readPhysicalAddress(in), in.readUnsignedByte(), in.readUnsignedByte(), in.readLong()));
        return new ActorItemTransferStep(observation, slices, in.readLong(), in.readInt(), in.readInt());
    }
}
