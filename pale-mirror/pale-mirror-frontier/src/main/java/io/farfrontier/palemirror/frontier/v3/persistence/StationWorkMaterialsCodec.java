package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.StationWorkMaterials;
import java.io.*;
import java.util.*;
import static io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec.*;

final class StationWorkMaterialsCodec {
    static void write(DataOutputStream out, StationWorkMaterials materials) throws IOException {
        out.writeLong(materials.tripOrdinal()); out.writeByte(materials.queued().size());
        for (var input : materials.queued()) {
            writeString(out, input.itemKind()); writeString(out, input.sourceContainer().value());
            writeString(out, input.sourceAccount().value()); writeString(out, input.claimId().value());
            portions(out, input.lots()); out.writeBoolean(input.withdrawn());
        }
        out.writeByte(materials.loaded().size());
        for (var input : materials.loaded()) {
            writeString(out, input.itemKind()); writeString(out, input.claimId().value()); portions(out, input.lots());
        }
    }
    static StationWorkMaterials read(DataInputStream in) throws IOException {
        long ordinal = in.readLong(); int queued = count(in);
        var pickups = new ArrayList<StationWorkMaterials.Pickup>();
        for (int i = 0; i < queued; i++) pickups.add(new StationWorkMaterials.Pickup(readString(in),
                new SubjectId(readString(in)), new SubjectId(readString(in)), new SubjectId(readString(in)), portions(in), in.readBoolean()));
        int loaded = count(in); var deposited = new ArrayList<StationWorkMaterials.Loaded>();
        for (int i = 0; i < loaded; i++) deposited.add(new StationWorkMaterials.Loaded(readString(in), new SubjectId(readString(in)), portions(in)));
        return new StationWorkMaterials(pickups, deposited, ordinal);
    }
    private static int count(DataInputStream in) throws IOException {
        int size = in.readUnsignedByte(); if (size > 64) throw new IllegalArgumentException("unbounded material workflow"); return size;
    }
    private static void portions(DataOutputStream out, Map<SubjectId, Integer> lots) throws IOException {
        out.writeByte(lots.size());
        for (var entry : new TreeMap<>(lots).entrySet()) { writeString(out, entry.getKey().value()); out.writeByte(entry.getValue()); }
    }
    private static Map<SubjectId, Integer> portions(DataInputStream in) throws IOException {
        int size = count(in); var lots = new TreeMap<SubjectId, Integer>();
        for (int i = 0; i < size; i++) if (lots.put(new SubjectId(readString(in)), in.readUnsignedByte()) != null)
            throw new IllegalArgumentException("duplicate material lot");
        return Map.copyOf(lots);
    }
    private StationWorkMaterialsCodec() { }
}
