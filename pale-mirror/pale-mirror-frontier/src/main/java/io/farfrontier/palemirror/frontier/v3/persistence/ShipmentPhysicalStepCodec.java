package io.farfrontier.palemirror.frontier.v3.persistence;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.io.*;
import java.util.*;

/** Prepared physical preimage uses shared address and exact body/execution grammars. */
final class ShipmentPhysicalStepCodec {
    static void write(DataOutputStream out, ShipmentPhysicalStep step) throws IOException {
        ShipmentStateCodec.status(out, step.status()); out.writeLong(step.shipmentRevision());
        ActorHotObservationCodec.write(out, step.observation()); out.writeByte(step.source().size());
        for (var slice : step.source()) {
            FungibleResourceStateCodec.writePhysicalAddress(out, slice.address());
            out.writeByte(slice.before()); out.writeByte(slice.moved()); out.writeLong(slice.epoch());
        }
        out.writeInt(step.destinationSlot()); out.writeLong(step.destinationEpoch());
        out.writeByte(step.destinationBefore()); ShipmentStateCodec.lots(out, step.lotQuantities());
    }
    static ShipmentPhysicalStep read(DataInputStream in) throws IOException {
        var status = ShipmentStateCodec.status(in); long revision = in.readLong(); var observation = ActorHotObservationCodec.read(in);
        int n = in.readUnsignedByte(); if (n < 1 || n > 27) throw new IllegalArgumentException("invalid shipment source preimage size");
        var source = new ArrayList<MaterialSourceSelection.Slice>(n);
        for (int i = 0; i < n; i++) source.add(new MaterialSourceSelection.Slice(FungibleResourceStateCodec.readPhysicalAddress(in),
                in.readUnsignedByte(), in.readUnsignedByte(), in.readLong()));
        int slot = in.readInt(); long epoch = in.readLong(); int before = in.readUnsignedByte();
        return new ShipmentPhysicalStep(status, revision, observation, source, slot, epoch, ShipmentStateCodec.lots(in), before);
    }
    private ShipmentPhysicalStepCodec() { }
}
