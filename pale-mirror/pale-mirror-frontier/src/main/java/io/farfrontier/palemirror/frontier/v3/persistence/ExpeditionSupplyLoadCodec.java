package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.expedition.ExpeditionSupplyLoad;
import java.io.*;
import java.util.*;

final class ExpeditionSupplyLoadCodec {
    private ExpeditionSupplyLoadCodec() { }
    static void write(DataOutputStream out, ExpeditionSupplyLoad load) throws IOException {
        out.writeUTF(load.foodKind()); out.writeLong(load.forecastDurationTicks()); out.writeLong(load.calculatedAtTick()); out.writeLong(load.revision());
        out.writeByte(load.foodTargets().size());
        for (var entry : new TreeMap<>(load.foodTargets()).entrySet()) {
            UnitGroupStateCodec.id(out, entry.getKey()); out.writeInt(entry.getValue());
            UnitGroupStateCodec.surface(out, load.assemblyStations().get(entry.getKey()));
        }
        out.writeInt(load.allocations().size());
        for (var a : load.allocations()) {
            UnitGroupStateCodec.id(out, a.claimId()); UnitGroupStateCodec.id(out, a.actorId());
            UnitGroupStateCodec.id(out, a.sourceAccountId()); UnitGroupStateCodec.id(out, a.destinationAccountId());
            ActorItemSlotCodec.write(out, a.slot()); ShipmentStateCodec.lots(out, a.lots());
            out.writeByte(a.outcome().wireTag()); out.writeBoolean(a.pending().isPresent());
            if (a.pending().isPresent()) ActorItemTransferStepCodec.write(out, a.pending().orElseThrow());
        }
    }
    static ExpeditionSupplyLoad read(DataInputStream in) throws IOException {
        String kind = in.readUTF(); long duration = in.readLong(), tick = in.readLong(), revision = in.readLong();
        int count = in.readUnsignedByte(); if (count < 1 || count > 32) throw new IllegalArgumentException("invalid supply roster size");
        var targets = new LinkedHashMap<SubjectId, Integer>();
        var assembly = new LinkedHashMap<SubjectId, io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor>();
        for (int i = 0; i < count; i++) {
            var actor = UnitGroupStateCodec.id(in);
            if (targets.put(actor, in.readInt()) != null) throw new IllegalArgumentException("duplicate supply roster member");
            assembly.put(actor, UnitGroupStateCodec.surface(in));
        }
        int size = UnitGroupStateCodec.count(in, 320); var allocations = new ArrayList<ExpeditionSupplyLoad.Allocation>();
        for (int i = 0; i < size; i++) allocations.add(new ExpeditionSupplyLoad.Allocation(
                UnitGroupStateCodec.id(in), UnitGroupStateCodec.id(in), UnitGroupStateCodec.id(in), UnitGroupStateCodec.id(in),
                ActorItemSlotCodec.read(in), ShipmentStateCodec.lots(in), ExpeditionSupplyLoad.Outcome.fromWireTag(in.readUnsignedByte()),
                in.readBoolean() ? Optional.of(ActorItemTransferStepCodec.read(in)) : Optional.empty()));
        return new ExpeditionSupplyLoad(kind, duration, tick, targets, assembly, allocations, revision);
    }
}
