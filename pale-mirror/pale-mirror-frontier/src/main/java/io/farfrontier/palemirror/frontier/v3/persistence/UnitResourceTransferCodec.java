package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.model.*;
import java.io.*;
import java.util.*;

final class UnitResourceTransferCodec {
    private UnitResourceTransferCodec() { }
    static void write(DataOutputStream out, UnitResourceTransfer value) throws IOException {
        UnitGroupStateCodec.id(out, value.claimId()); UnitGroupStateCodec.id(out, value.actorId()); UnitGroupStateCodec.id(out, value.containerId());
        UnitGroupStateCodec.id(out, value.sourceAccountId()); UnitGroupStateCodec.id(out, value.destinationAccountId()); UnitGroupStateCodec.id(out, value.sourceEconomicOwnerId());
        out.writeUTF(value.itemKind()); out.writeInt(value.lots().size());
        for (var entry : new TreeMap<>(value.lots()).entrySet()) { UnitGroupStateCodec.id(out, entry.getKey()); out.writeInt(entry.getValue()); }
        ActorItemSlotCodec.write(out, value.slot()); UnitGroupStateCodec.surface(out, value.station()); ActorExecutionStateCodec.writeId(out, value.execution());
        out.writeBoolean(value.pending().isPresent()); if (value.pending().isPresent()) ActorItemTransferStepCodec.write(out, value.pending().orElseThrow());
    }
    static UnitResourceTransfer read(DataInputStream in) throws IOException {
        var claim = UnitGroupStateCodec.id(in); var actor = UnitGroupStateCodec.id(in); var container = UnitGroupStateCodec.id(in);
        var source = UnitGroupStateCodec.id(in); var destination = UnitGroupStateCodec.id(in); var owner = UnitGroupStateCodec.id(in); var kind = in.readUTF();
        int count = UnitGroupStateCodec.count(in, 64); var lots = new LinkedHashMap<io.farfrontier.palemirror.frontier.v3.api.SubjectId, Integer>();
        for (int i = 0; i < count; i++) if (lots.putIfAbsent(UnitGroupStateCodec.id(in), in.readInt()) != null) throw new IllegalArgumentException("duplicate replenishment lot");
        return new UnitResourceTransfer(claim, actor, container, source, destination, owner, kind, lots, ActorItemSlotCodec.read(in),
                UnitGroupStateCodec.surface(in), ActorExecutionStateCodec.readId(in), in.readBoolean() ? Optional.of(ActorItemTransferStepCodec.read(in)) : Optional.empty());
    }
}
