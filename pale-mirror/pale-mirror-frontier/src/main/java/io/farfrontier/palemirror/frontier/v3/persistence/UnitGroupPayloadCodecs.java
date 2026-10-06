package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.group.*;
import java.io.*;
import java.util.*;

final class UnitGroupPayloadCodecs {
    private UnitGroupPayloadCodecs() { }
    static PayloadCodecs groups() {
        return new PayloadCodecs(List.of(codec("frontier.unit_group_advanced", (out, payload) -> {
            var value = (UnitGroupAdvanced) payload; UnitGroupStateCodec.id(out, value.groupId()); out.writeLong(value.expectedRevision());
            out.writeByte(switch (value.change()) { case START -> 1; case FRAME -> 2; case ARRIVE -> 3; case CLOSE -> 4; });
            out.writeLong(value.goalOrdinal()); out.writeBoolean(value.journey().isPresent());
            if (value.journey().isPresent()) UnitGroupStateCodec.journey(out, value.journey().orElseThrow());
            out.writeBoolean(value.departureActor().isPresent()); if (value.departureActor().isPresent()) UnitGroupStateCodec.id(out, value.departureActor().orElseThrow());
        }, in -> {
            var id = UnitGroupStateCodec.id(in); long revision = in.readLong();
            var change = switch (in.readUnsignedByte()) { case 1 -> UnitGroupAdvanced.Change.START; case 2 -> UnitGroupAdvanced.Change.FRAME; case 3 ->
                    UnitGroupAdvanced.Change.ARRIVE; case 4 -> UnitGroupAdvanced.Change.CLOSE; default -> throw new IllegalArgumentException("unknown group transition tag"); };
            long ordinal = in.readLong(); var journey = in.readBoolean() ? Optional.of(UnitGroupStateCodec.journey(in)) : Optional.<UnitGroup.Journey>empty();
            var departure = in.readBoolean() ? Optional.of(UnitGroupStateCodec.id(in)) : Optional.<SubjectId>empty();
            return new UnitGroupAdvanced(id, revision, change, ordinal, journey, departure);
        }), codec("frontier.unit_group_navigation_ready", (out, payload) -> {
            var value = (UnitGroupNavigationReady) payload;
            UnitGroupStateCodec.id(out, value.groupId()); out.writeLong(value.expectedRevision());
        }, in -> new UnitGroupNavigationReady(UnitGroupStateCodec.id(in), in.readLong()))));
    }
    static PayloadCodecs transport() {
        return new PayloadCodecs(List.of(codec("frontier.transport_mission_started", (out, payload) -> {
            var value = (TransportMissionStarted) payload; UnitGroupStateCodec.id(out, value.senderId()); out.writeInt(value.senderKind().wireTag());
            TransportMissionCodec.write(out, value.mission()); UnitGroupStateCodec.group(out, value.group()); out.writeInt(value.shipments().size());
            for (var shipment : value.shipments()) ShipmentStateCodec.writeShipment(out, shipment);
        }, in -> {
            var sender = UnitGroupStateCodec.id(in); var kind = FrontierWireTags.require(EconomicOwnerKind.class, in.readInt());
            var mission = TransportMissionCodec.read(in); var group = UnitGroupStateCodec.group(in); int n = UnitGroupStateCodec.count(in, UnitGroup.MAX_MEMBERS);
            var shipments = new ArrayList<Shipment>(); for (int i = 0; i < n; i++) shipments.add(ShipmentStateCodec.readShipment(in));
            return new TransportMissionStarted(sender, kind, mission, group, shipments);
        }), codec("frontier.transport_mission_advanced", (out, payload) -> {
            var value = (TransportMissionAdvanced) payload; UnitGroupStateCodec.id(out, value.missionId()); out.writeLong(value.expectedRevision()); TransportMissionCodec.stage(out, value.next());
        }, in -> new TransportMissionAdvanced(UnitGroupStateCodec.id(in), in.readLong(), TransportMissionCodec.stage(in))),
                codec("frontier.transport_mission_retired", (out, payload) -> {
                    var value = (TransportMissionRetired) payload; UnitGroupStateCodec.id(out, value.missionId()); out.writeLong(value.expectedRevision());
                }, in -> new TransportMissionRetired(UnitGroupStateCodec.id(in), in.readLong()))));
    }
    private interface Writer { void write(DataOutputStream out, FrontierPayload payload) throws IOException; }
    private interface Reader { FrontierPayload read(DataInputStream in) throws IOException; }
    private static PayloadCodec codec(String type, Writer writer, Reader reader) {
        return new PayloadCodec() {
            @Override public String type() { return type; }
            @Override public byte[] encode(FrontierPayload payload) {
                try { var bytes = new ByteArrayOutputStream(); try (var out = new DataOutputStream(bytes)) { writer.write(out, payload); } return bytes.toByteArray(); }
                catch (IOException failure) { throw new IllegalStateException("in-memory group encode failed", failure); }
            }
            @Override public FrontierPayload decode(byte[] bytes) {
                try (var in = new DataInputStream(new ByteArrayInputStream(bytes))) {
                    var payload = reader.read(in); if (in.available() != 0) throw new IllegalArgumentException("trailing group payload bytes"); return payload;
                } catch (IOException failure) { throw new IllegalArgumentException("truncated group payload", failure); }
            }
        };
    }
}
