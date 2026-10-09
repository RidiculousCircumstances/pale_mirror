package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import net.minecraft.nbt.*;
import java.io.*;
import java.util.*;

/** Carrier-owned row codec. The generic journal neither interprets nor dispatches these facets. */
final class FrontierV3CarrierJournalImage {
    private static final List<String> FACETS = List.of("bodyResidences", "bodyDepartures", "bodyDepartureConflicts",
            "savedBodyDepartures", "carriers", "pendingAdoptions", "firstAdmissions", "departures", "savedDepartures",
            "readFencedDepartures", "returnReads", "departureConflicts", "ambientDepartures", "savedAmbientDepartures",
            "ambientDepartureConflicts");
    private FrontierV3CarrierJournalImage() { }
    static CompoundTag marker(SubjectId actor) { var tag = new CompoundTag(); tag.putString("actor", actor.value()); return tag; }
    static CompoundTag marker(SubjectId actor, String field, long value) { var tag = marker(actor); tag.putLong(field, value); return tag; }
    static void list(CompoundTag row, String facet, CompoundTag value) {
        var list = new ListTag(); if (value != null) list.add(value); row.put(facet, list);
    }
    static boolean hasRows(CompoundTag row) { return FACETS.stream().anyMatch(key -> !row.getList(key, Tag.TAG_COMPOUND).isEmpty()); }
    static byte[] encode(CompoundTag row) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var output = new DataOutputStream(bytes)) { NbtIo.write(row, output); }
        return bytes.toByteArray();
    }
    static CompoundTag merge(Map<String, byte[]> actors) throws IOException {
        var image = new CompoundTag(); image.putInt("format", 11);
        FACETS.forEach(key -> image.put(key, new ListTag()));
        for (var entry : new TreeMap<>(actors).entrySet()) {
            new SubjectId(entry.getKey());
            try (var input = new DataInputStream(new ByteArrayInputStream(entry.getValue()))) {
                var row = NbtIo.read(input, NbtAccounter.create(16L * 1024 * 1024));
                if (row.getInt("format") != 11 || !row.getString("actor").equals(entry.getKey())
                        || row.size() != FACETS.size() + 2 || input.read() != -1)
                    throw new IOException("incompatible carrier journal row");
                for (String facet : FACETS) {
                    if (!(row.get(facet) instanceof ListTag values) || values.size() > 1
                            || !values.isEmpty() && values.getElementType() != Tag.TAG_COMPOUND)
                        throw new IOException("invalid carrier journal facet");
                    for (Tag value : values) {
                        var fact = (CompoundTag) value;
                        String actor = switch (facet) {
                            case "bodyDepartures", "bodyDepartureConflicts" -> fact.getCompound("identity").getString("actor");
                            case "pendingAdoptions" -> fact.getCompound("predecessor").getString("actor");
                            case "departures", "departureConflicts", "ambientDepartures", "ambientDepartureConflicts" ->
                                    fact.getCompound("carrier").getString("actor");
                            default -> fact.getString("actor");
                        };
                        if (!actor.equals(entry.getKey())) throw new IOException("carrier journal facet crosses actor row");
                        image.getList(facet, Tag.TAG_COMPOUND).add(value);
                    }
                }
            }
        }
        return image;
    }
}
