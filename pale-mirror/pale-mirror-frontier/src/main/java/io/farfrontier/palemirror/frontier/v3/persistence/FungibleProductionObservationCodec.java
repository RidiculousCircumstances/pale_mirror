package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/** Bounded shared WAL/snapshot receipt grammar: tag 23 singleton, tag 24 multi-lot. */
final class FungibleProductionObservationCodec {
    private FungibleProductionObservationCodec() { }

    static void write(DataOutputStream output, FungibleProductionObservation receipt) throws IOException {
        FrontierWorldPayloadCodecs.writeSubject(output, receipt.accountId());
        FrontierWorldPayloadCodecs.writeSubject(output, receipt.containerId());
        FrontierWorldPayloadCodecs.writeSubject(output, receipt.inputLotId());
        FrontierWorldPayloadCodecs.writeSubject(output, receipt.claimId());
        FrontierWorldPayloadCodecs.writeSubject(output, receipt.outputLotId());
        output.writeByte(receipt.quantity()); output.writeLong(receipt.authorityEpoch());
        if (receipt.inputLots().size() > 1) {
            output.writeByte(receipt.inputLots().size());
            for (var entry : receipt.inputLots().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
                FrontierWorldPayloadCodecs.writeSubject(output, entry.getKey()); output.writeByte(entry.getValue());
            }
        }
        output.writeByte(receipt.observedStacks().size());
        for (var stack : receipt.observedStacks()) {
            var slot = (PhysicalStackAddress.ContainerSlot) stack.address();
            output.writeByte(slot.slot().slot());
            FrontierWorldPayloadCodecs.writeString(output, stack.itemKind()); output.writeByte(stack.quantity());
        }
    }

    static FungibleProductionObservation read(DataInputStream input, PhysicalObservationId id, PhysicalIntentId intent) throws IOException {
        return read(input, id, intent, false);
    }

    static FungibleProductionObservation read(DataInputStream input, PhysicalObservationId id, PhysicalIntentId intent,
                                              boolean multipleInputs) throws IOException {
        SubjectId account = subject(input), container = subject(input), lot = subject(input), claim = subject(input), result = subject(input);
        int quantity = input.readUnsignedByte(); long epoch = input.readLong();
        Map<SubjectId, Integer> lots = new LinkedHashMap<>();
        if (multipleInputs) {
            int count = input.readUnsignedByte();
            if (count < 2 || count > 64) throw new IllegalArgumentException("production receipt has invalid input lot count");
            for (int index = 0; index < count; index++) {
                if (lots.put(subject(input), input.readUnsignedByte()) != null) throw new IllegalArgumentException("duplicate production receipt input lot");
            }
        } else lots.put(lot, quantity);
        int size = input.readUnsignedByte();
        if (size < 1 || size > 54) throw new IllegalArgumentException("production observed layout exceeds its bound");
        var stacks = new ArrayList<FungiblePhysicalObservation.Stack>(size);
        for (int index = 0; index < size; index++) {
            int slot = input.readUnsignedByte();
            stacks.add(new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ContainerSlot(
                    new InventoryCustody.ContainerSlot(container, slot)), FrontierWorldPayloadCodecs.readString(input), input.readUnsignedByte()));
        }
        return new FungibleProductionObservation(id, intent, account, container, lot, lots, claim, result, quantity, epoch, stacks);
    }

    private static SubjectId subject(DataInputStream input) throws IOException { return FrontierWorldPayloadCodecs.readSubject(input).value(); }
}
