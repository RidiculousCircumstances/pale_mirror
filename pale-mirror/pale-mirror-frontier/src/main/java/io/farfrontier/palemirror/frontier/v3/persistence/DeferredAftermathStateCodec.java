package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.OptionalLong;

/** Stable snapshot representation for the single bounded deferred-aftermath owner. */
final class DeferredAftermathStateCodec {
    private DeferredAftermathStateCodec() { }
    static void write(DataOutputStream output, DeferredAftermathState state) throws IOException {
        output.writeShort(state.entries().size());
        for (DeferredAftermath value : state.entries().values().stream().sorted(java.util.Comparator.comparing(DeferredAftermath::id)).toList()) {
            subject(output, value.id()); subject(output, value.ownerId()); subject(output, value.causeId()); output.writeLong(value.eventAt());
            output.writeBoolean(value.observedAt().isPresent()); if (value.observedAt().isPresent()) output.writeLong(value.observedAt().getAsLong());
            FrontierWorldPayloadCodecs.writeString(output, value.provenance()); output.writeByte(FrontierWireTags.tag(value.knowledge()));
            output.writeLong(value.expectedEpoch()); output.writeByte(value.cells().size()); output.writeByte(value.resolutionCursor());
            for (DeferredAftermathCell cell : value.cells()) {
                output.writeInt(cell.position().x()); output.writeInt(cell.position().y()); output.writeInt(cell.position().z());
                PhysicalDeltaPayloadCodecs.writeTarget(output, cell.semanticTarget()); FrontierWorldPayloadCodecs.writeString(output, cell.expectedMaterial().name());
                        output.writeByte(FrontierWireTags.tag(cell.expectedPart())); output.writeLong(cell.authorityRevision());
                output.writeByte(FrontierWireTags.tag(cell.status()));
            }
        }
    }
    static DeferredAftermathState read(DataInputStream input) throws IOException {
        int count = input.readUnsignedShort(); if (count > DeferredAftermathState.MAX_ENTRIES) throw new IllegalArgumentException("deferred aftermath retention exceeded");
        Map<SubjectId, DeferredAftermath> values = new LinkedHashMap<>();
        for (int index = 0; index < count; index++) {
            SubjectId id = subject(input), owner = subject(input), cause = subject(input); long eventAt = input.readLong();
            OptionalLong observedAt = input.readBoolean() ? OptionalLong.of(input.readLong()) : OptionalLong.empty(); String provenance = FrontierWorldPayloadCodecs.readString(input);
            DeferredAftermathKnowledge knowledge = FrontierWireTags.require(DeferredAftermathKnowledge.class, input.readUnsignedByte());
            long epoch = input.readLong(); int cells = input.readUnsignedByte(), cursor = input.readUnsignedByte(); ArrayList<DeferredAftermathCell> footprint = new ArrayList<>();
            for (int cell = 0; cell < cells; cell++) footprint.add(new DeferredAftermathCell(
                    new BlockPosition(input.readInt(), input.readInt(), input.readInt()), PhysicalDeltaPayloadCodecs.readTarget(input),
                    GrayboxMaterial.valueOf(FrontierWorldPayloadCodecs.readString(input)),
                    FrontierWireTags.require(GrayboxSemanticPart.class, input.readUnsignedByte()), input.readLong(),
                    FrontierWireTags.require(DeferredAftermathCellStatus.class, input.readUnsignedByte())));
            DeferredAftermath aftermath = new DeferredAftermath(id, owner, cause, eventAt, observedAt, provenance, knowledge, epoch, footprint, cursor);
            if (values.put(id, aftermath) != null) throw new IllegalArgumentException("duplicate deferred aftermath identity");
        }
        return new DeferredAftermathState(values);
    }
    private static void subject(DataOutputStream output, SubjectId value) throws IOException { FrontierWorldPayloadCodecs.writeSubject(output, value); }
    private static SubjectId subject(DataInputStream input) throws IOException { return FrontierWorldPayloadCodecs.readSubject(input).value(); }
}
