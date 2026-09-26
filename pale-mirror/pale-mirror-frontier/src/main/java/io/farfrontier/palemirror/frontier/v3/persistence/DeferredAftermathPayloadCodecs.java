package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.kernel.PayloadCodec;
import io.farfrontier.palemirror.frontier.v3.kernel.PayloadCodecs;
import io.farfrontier.palemirror.frontier.v3.model.*;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;

/** WAL payload codecs for the deferred canonical consequence boundary. */
final class DeferredAftermathPayloadCodecs {
    private DeferredAftermathPayloadCodecs() { }
    static PayloadCodecs codecs() { return new PayloadCodecs(List.of(prepared(), resolved())); }
    private static PayloadCodec prepared() { return new PayloadCodec() {
        @Override public String type() { return "frontier.deferred_aftermath_prepared"; }
        @Override public byte[] encode(FrontierPayload payload) { return FrontierWorldPayloadCodecs.encodeProduction(output -> write(output, ((DeferredAftermathPrepared) payload).aftermath())); }
        @Override public FrontierPayload decode(byte[] bytes) { return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> new DeferredAftermathPrepared(read(input))); }
    }; }
    private static PayloadCodec resolved() { return new PayloadCodec() {
        @Override public String type() { return "frontier.deferred_aftermath_resolved"; }
        @Override public byte[] encode(FrontierPayload payload) { return FrontierWorldPayloadCodecs.encodeProduction(output -> {
            DeferredAftermathResolved value = (DeferredAftermathResolved) payload; subject(output, value.aftermathId()); output.writeLong(value.expectedEpoch()); output.writeLong(value.observationAt());
            output.writeByte(value.expectedCursor()); output.writeLong(value.authorityRevision()); output.writeByte(FrontierWireTags.tag(value.result()));
        }); }
        @Override public FrontierPayload decode(byte[] bytes) { return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> new DeferredAftermathResolved(
                subject(input), input.readLong(), input.readLong(), input.readUnsignedByte(), input.readLong(), FrontierWireTags.require(DeferredAftermathCellStatus.class, input.readUnsignedByte()))); }
    }; }
    private static void write(DataOutputStream output, DeferredAftermath value) throws IOException {
        subject(output, value.id()); subject(output, value.ownerId()); subject(output, value.causeId()); output.writeLong(value.eventAt());
        output.writeBoolean(value.observedAt().isPresent()); if (value.observedAt().isPresent()) output.writeLong(value.observedAt().getAsLong());
        FrontierWorldPayloadCodecs.writeString(output, value.provenance()); output.writeByte(FrontierWireTags.tag(value.knowledge())); output.writeLong(value.expectedEpoch());
        output.writeByte(value.cells().size()); output.writeByte(value.resolutionCursor());
        for (DeferredAftermathCell cell : value.cells()) {
            output.writeInt(cell.position().x()); output.writeInt(cell.position().y()); output.writeInt(cell.position().z());
            PhysicalDeltaPayloadCodecs.writeTarget(output, cell.semanticTarget()); FrontierWorldPayloadCodecs.writeString(output, cell.expectedMaterial().name());
                    output.writeByte(FrontierWireTags.tag(cell.expectedPart())); output.writeLong(cell.authorityRevision());
            output.writeByte(FrontierWireTags.tag(cell.status()));
        }
    }
    private static DeferredAftermath read(DataInputStream input) throws IOException {
        SubjectId id = subject(input), owner = subject(input), cause = subject(input); long eventAt = input.readLong();
        OptionalLong observed = input.readBoolean() ? OptionalLong.of(input.readLong()) : OptionalLong.empty(); String provenance = FrontierWorldPayloadCodecs.readString(input);
        DeferredAftermathKnowledge knowledge = FrontierWireTags.require(DeferredAftermathKnowledge.class, input.readUnsignedByte()); long epoch = input.readLong();
        int count = input.readUnsignedByte(), cursor = input.readUnsignedByte(); ArrayList<DeferredAftermathCell> cells = new ArrayList<>();
        for (int index = 0; index < count; index++) cells.add(new DeferredAftermathCell(
                new BlockPosition(input.readInt(), input.readInt(), input.readInt()), PhysicalDeltaPayloadCodecs.readTarget(input),
                GrayboxMaterial.valueOf(FrontierWorldPayloadCodecs.readString(input)),
                FrontierWireTags.require(GrayboxSemanticPart.class, input.readUnsignedByte()), input.readLong(),
                FrontierWireTags.require(DeferredAftermathCellStatus.class, input.readUnsignedByte())));
        return new DeferredAftermath(id, owner, cause, eventAt, observed, provenance, knowledge, epoch, cells, cursor);
    }
    private static void subject(DataOutputStream output, SubjectId value) throws IOException { FrontierWorldPayloadCodecs.writeSubject(output, value); }
    private static SubjectId subject(DataInputStream input) throws IOException { return FrontierWorldPayloadCodecs.readSubject(input).value(); }
}
