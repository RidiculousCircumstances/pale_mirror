package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWireTags;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind;
import io.farfrontier.palemirror.frontier.v3.model.group.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.TimedKnownRoute;
import java.io.*;
import java.util.*;

/** Explicit stable tags; bounded domain group records survive recovery without physical actors or caches. */
final class UnitGroupStateCodec {
    private UnitGroupStateCodec() { }
    static void write(DataOutputStream out, UnitGroupState state) throws IOException {
        out.writeInt(state.groups().size());
        for (var group : state.groups().values().stream().sorted(Comparator.comparing(UnitGroup::id)).toList()) group(out, group);
    }
    static UnitGroupState read(DataInputStream in) throws IOException {
        int count = count(in, UnitGroupState.MAX_GROUPS); var groups = new LinkedHashMap<SubjectId, UnitGroup>();
        for (int i = 0; i < count; i++) { var group = group(in); if (groups.putIfAbsent(group.id(), group) != null) throw new IllegalArgumentException("duplicate group ID"); }
        return new UnitGroupState(groups);
    }
    static void group(DataOutputStream out, UnitGroup group) throws IOException {
        id(out, group.id()); switch (group.mission().kind()) { case TRANSPORT -> out.writeByte(1); }
        id(out, group.mission().id()); out.writeInt(group.members().size());
        for (var member : group.members()) {
            id(out, member.actorId()); out.writeByte(switch (member.role()) { case CARRIER -> 1; case ESCORT -> 2; case GUIDE -> 3; });
            out.writeByte(FrontierWireTags.tag(member.activityKind())); id(out, member.activityOwnerId());
        }
        switch (group.formation()) { case COLUMN -> out.writeByte(1); }
        out.writeByte(switch (group.phase()) { case READY -> 1; case TRAVELLING -> 2; case AT_GOAL -> 3; case CLOSED -> 4; });
        out.writeLong(group.revision()); out.writeLong(group.goalOrdinal()); out.writeBoolean(group.journey().isPresent());
        if (group.journey().isPresent()) journey(out, group.journey().orElseThrow());
    }
    static UnitGroup group(DataInputStream in) throws IOException {
        var id = id(in); var kind = switch (in.readUnsignedByte()) { case 1 -> UnitGroup.MissionKind.TRANSPORT; default -> throw new IllegalArgumentException("unknown group mission tag"); };
        var mission = new UnitGroup.Mission(kind, id(in)); int count = count(in, UnitGroup.MAX_MEMBERS); var members = new ArrayList<UnitGroup.Member>();
        for (int i = 0; i < count; i++) {
            var actor = id(in); var role = switch (in.readUnsignedByte()) { case 1 -> UnitGroup.Role.CARRIER; case 2 -> UnitGroup.Role.ESCORT; case 3 -> UnitGroup.Role.GUIDE; default -> throw new IllegalArgumentException("unknown group role tag"); };
            members.add(new UnitGroup.Member(actor, role, FrontierWireTags.require(ActorActivityKind.class, in.readUnsignedByte()), id(in)));
        }
        var formation = switch (in.readUnsignedByte()) { case 1 -> UnitGroup.Formation.COLUMN; default -> throw new IllegalArgumentException("unknown formation tag"); };
        var phase = switch (in.readUnsignedByte()) { case 1 -> UnitGroup.Phase.READY; case 2 -> UnitGroup.Phase.TRAVELLING; case 3 -> UnitGroup.Phase.AT_GOAL; case 4 ->
                UnitGroup.Phase.CLOSED; default -> throw new IllegalArgumentException("unknown group phase tag"); };
        long revision = in.readLong(), ordinal = in.readLong(); var journey = in.readBoolean() ? Optional.of(journey(in)) : Optional.<UnitGroup.Journey>empty();
        return new UnitGroup(id, mission, members, formation, phase, revision, ordinal, journey);
    }
    static void journey(DataOutputStream out, UnitGroup.Journey value) throws IOException {
        surface(out, value.destination()); out.writeInt(value.route().size()); for (var cell : value.route()) surface(out, cell);
        out.writeInt(value.cursor()); out.writeInt(value.stations().size());
        for (var entry : value.stations().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) { id(out, entry.getKey()); surface(out, entry.getValue()); }
    }
    static UnitGroup.Journey journey(DataInputStream in) throws IOException {
        var target = surface(in); int n = count(in, TimedKnownRoute.MAX_SURFACES); var route = new ArrayList<SurfaceAnchor>();
        for (int i = 0; i < n; i++) route.add(surface(in)); int cursor = in.readInt(), members = count(in, UnitGroup.MAX_MEMBERS);
        var stations = new LinkedHashMap<SubjectId, SurfaceAnchor>();
        for (int i = 0; i < members; i++) if (stations.putIfAbsent(id(in), surface(in)) != null) throw new IllegalArgumentException("duplicate formation actor");
        return new UnitGroup.Journey(target, route, cursor, stations);
    }
    static void id(DataOutputStream out, SubjectId id) throws IOException { out.writeUTF(id.value()); }
    static SubjectId id(DataInputStream in) throws IOException { return new SubjectId(in.readUTF()); }
    static void surface(DataOutputStream out, SurfaceAnchor surface) throws IOException { FrontierWorldStateCodec.writePosition(out, surface.support()); }
    static SurfaceAnchor surface(DataInputStream in) throws IOException { return new SurfaceAnchor(FrontierWorldStateCodec.readPosition(in)); }
    static int count(DataInputStream in, int max) throws IOException { int n = in.readInt(); if (n < 0 || n > max) throw new IllegalArgumentException("invalid bounded group collection count"); return n; }
}
