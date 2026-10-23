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
        out.writeBoolean(step.individual().isPresent());
        if (step.individual().isPresent()) {
            var witness = step.individual().orElseThrow(); ExactItemStateCodec.write(out, witness.item()); slot(out, witness.endpoint());
            out.writeBoolean(witness.attached().isPresent()); if (witness.attached().isPresent()) slot(out, witness.attached().orElseThrow());
            out.writeLong(witness.sourceEpoch());
        }
    }
    static ShipmentPhysicalStep read(DataInputStream in) throws IOException {
        var status = ShipmentStateCodec.status(in); long revision = in.readLong(); var observation = ActorHotObservationCodec.read(in);
        int n = in.readUnsignedByte(); if (n < 0 || n > 54) throw new IllegalArgumentException("invalid shipment source preimage size");
        var source = new ArrayList<MaterialSourceSelection.Slice>(n);
        for (int i = 0; i < n; i++) source.add(new MaterialSourceSelection.Slice(FungibleResourceStateCodec.readPhysicalAddress(in),
                in.readUnsignedByte(), in.readUnsignedByte(), in.readLong()));
        int slot = in.readInt(); long epoch = in.readLong(); int before = in.readUnsignedByte();
        var lots = ShipmentStateCodec.lots(in);
        var individual = in.readBoolean() ? Optional.of(new IndividualItemTransferWitness(ExactItemStateCodec.read(in), slot(in),
                in.readBoolean() ? Optional.of(slot(in)) : Optional.empty(), in.readLong())) : Optional.<IndividualItemTransferWitness>empty();
        return new ShipmentPhysicalStep(status, revision, observation, source, slot, epoch, lots, before, individual);
    }
    private static void slot(DataOutputStream out, InventoryCustody.ContainerSlot slot) throws IOException {
        out.writeUTF(slot.containerId().value()); out.writeInt(slot.slot());
    }
    private static InventoryCustody.ContainerSlot slot(DataInputStream in) throws IOException {
        return new InventoryCustody.ContainerSlot(new io.farfrontier.palemirror.frontier.v3.api.SubjectId(in.readUTF()), in.readInt());
    }
    private ShipmentPhysicalStepCodec() { }
}
