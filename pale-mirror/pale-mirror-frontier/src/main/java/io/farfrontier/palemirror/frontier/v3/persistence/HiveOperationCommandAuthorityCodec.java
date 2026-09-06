package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** One current-schema binary representation shared by snapshot and WAL authority facts. */
final class HiveOperationCommandAuthorityCodec {
    private HiveOperationCommandAuthorityCodec() { }
    static void write(DataOutputStream output, HiveOperationCommandAuthority value) throws IOException {
        output.writeByte(value.kind().wireTag()); subject(output, value.originalAuthorityId()); subject(output, value.currentAuthorityId());
        output.writeByte(value.rosterIds().size()); for (SubjectId member : value.rosterIds()) subject(output, member);
        output.writeByte(value.subordinateWeight()); output.writeBoolean(value.relayCoverage().isPresent());
        if (value.relayCoverage().isPresent()) { HiveRelayCoverageProof proof = value.relayCoverage().orElseThrow(); subject(output, proof.ganglionId()); position(output, proof.centre()); output.writeShort(proof.radius()); }
        output.writeByte(value.signalPhase().wireTag()); output.writeLong(value.signalSince());
    }
    static HiveOperationCommandAuthority read(DataInputStream input) throws IOException {
        HiveCommandAuthorityKind kind = FrontierWireTags.require(HiveCommandAuthorityKind.class, input.readUnsignedByte()); SubjectId original = subject(input), current = subject(input);
        List<SubjectId> roster = new ArrayList<>(); for (int index = 0, count = input.readUnsignedByte(); index < count; index++) roster.add(subject(input));
        int weight = input.readUnsignedByte(); Optional<HiveRelayCoverageProof> relay = input.readBoolean()
                ? Optional.of(new HiveRelayCoverageProof(subject(input), position(input), input.readUnsignedShort())) : Optional.empty();
        HiveCommandSignalPhase phase = FrontierWireTags.require(HiveCommandSignalPhase.class, input.readUnsignedByte());
        return new HiveOperationCommandAuthority(kind, original, current, roster, weight, relay, phase, input.readLong());
    }
    private static void subject(DataOutputStream output, SubjectId value) throws IOException { FrontierWorldPayloadCodecs.writeSubject(output, value); }
    private static SubjectId subject(DataInputStream input) throws IOException { return FrontierWorldPayloadCodecs.readSubject(input).value(); }
    private static void position(DataOutputStream output, BlockPosition value) throws IOException { output.writeInt(value.x()); output.writeInt(value.y()); output.writeInt(value.z()); }
    private static BlockPosition position(DataInputStream input) throws IOException { return new BlockPosition(input.readInt(), input.readInt(), input.readInt()); }
}
