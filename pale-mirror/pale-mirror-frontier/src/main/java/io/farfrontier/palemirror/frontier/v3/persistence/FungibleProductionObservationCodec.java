package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;

/** One bounded body grammar shared by WAL and snapshot receipt variants (stable tag 23). */
final class FungibleProductionObservationCodec {
    private FungibleProductionObservationCodec() { }

    static void write(DataOutputStream output, FungibleProductionObservation receipt) throws IOException {
        FrontierWorldPayloadCodecs.writeSubject(output, receipt.accountId());
        FrontierWorldPayloadCodecs.writeSubject(output, receipt.containerId());
        FrontierWorldPayloadCodecs.writeSubject(output, receipt.inputLotId());
        FrontierWorldPayloadCodecs.writeSubject(output, receipt.claimId());
        FrontierWorldPayloadCodecs.writeSubject(output, receipt.outputLotId());
        output.writeByte(receipt.quantity()); output.writeLong(receipt.authorityEpoch());
        output.writeByte(receipt.observedStacks().size());
        for (var stack : receipt.observedStacks()) {
            var slot = (PhysicalStackAddress.ContainerSlot) stack.address();
            output.writeByte(slot.slot().slot());
            FrontierWorldPayloadCodecs.writeString(output, stack.itemKind()); output.writeByte(stack.quantity());
        }
    }

    static FungibleProductionObservation read(DataInputStream input, PhysicalObservationId id, PhysicalIntentId intent) throws IOException {
        SubjectId account = subject(input), container = subject(input), lot = subject(input), claim = subject(input), result = subject(input);
        int quantity = input.readUnsignedByte(); long epoch = input.readLong(); int size = input.readUnsignedByte();
        if (size < 1 || size > 54) throw new IllegalArgumentException("production observed layout exceeds its bound");
        var stacks = new ArrayList<FungiblePhysicalObservation.Stack>(size);
        for (int index = 0; index < size; index++) {
            int slot = input.readUnsignedByte();
            stacks.add(new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ContainerSlot(
                    new InventoryCustody.ContainerSlot(container, slot)), FrontierWorldPayloadCodecs.readString(input), input.readUnsignedByte()));
        }
        return new FungibleProductionObservation(id, intent, account, container, lot, claim, result, quantity, epoch, stacks);
    }

    private static SubjectId subject(DataInputStream input) throws IOException { return FrontierWorldPayloadCodecs.readSubject(input).value(); }
}
