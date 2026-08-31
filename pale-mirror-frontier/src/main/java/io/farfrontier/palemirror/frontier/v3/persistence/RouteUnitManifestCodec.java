package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWireTags;
import io.farfrontier.palemirror.frontier.v3.model.RouteUnitDuty;
import io.farfrontier.palemirror.frontier.v3.model.RouteUnitKind;
import io.farfrontier.palemirror.frontier.v3.model.RouteUnitManifest;
import io.farfrontier.palemirror.frontier.v3.model.RouteUnitMember;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Stable binary representation of the exact unit embedded in a route owner. */
final class RouteUnitManifestCodec {
    private RouteUnitManifestCodec() { }

    static void write(DataOutputStream output, RouteUnitManifest unit) throws IOException {
        subject(output, unit.id()); subject(output, unit.ownerId()); output.writeByte(unit.kind().wireTag());
        output.writeByte(unit.members().size());
        for (RouteUnitMember member : unit.members()) { subject(output, member.residentId()); output.writeByte(member.duty().wireTag()); }
        subject(output, unit.leaderId()); output.writeBoolean(unit.legacyUnderstrength());
    }

    static RouteUnitManifest read(DataInputStream input) throws IOException {
        SubjectId id = subject(input), owner = subject(input); int kind = input.readUnsignedByte();
        List<RouteUnitMember> members = new ArrayList<>();
        for (int index = 0, count = input.readUnsignedByte(); index < count; index++) {
            int duty;
            SubjectId resident = subject(input); duty = input.readUnsignedByte();
            members.add(new RouteUnitMember(resident, FrontierWireTags.require(RouteUnitDuty.class, duty)));
        }
        return new RouteUnitManifest(id, owner, FrontierWireTags.require(RouteUnitKind.class, kind), members, subject(input), input.readBoolean());
    }

    private static void subject(DataOutputStream output, SubjectId id) throws IOException { FrontierWorldPayloadCodecs.writeSubject(output, id); }
    private static SubjectId subject(DataInputStream input) throws IOException { return FrontierWorldPayloadCodecs.readSubject(input).value(); }
}
