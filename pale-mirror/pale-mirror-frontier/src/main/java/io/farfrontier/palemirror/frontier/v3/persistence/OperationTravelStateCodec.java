package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/** One current-format travel representation for snapshot, WAL and captured predecessors. */
final class OperationTravelStateCodec {
    private OperationTravelStateCodec() { }
    static void write(DataOutputStream out, OperationTravel travel) throws IOException {
        FrontierWorldPayloadCodecs.writeSubject(out, travel.frontId());
        TraversalTopologyStateCodec.write(out, travel.topology());
        out.writeShort(travel.cursor()); out.writeByte(travel.formation().size());
        for (var entry : travel.formation().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            FrontierWorldPayloadCodecs.writeSubject(out, entry.getKey());
            var body = entry.getValue(); out.writeInt(body.x()); out.writeInt(body.y()); out.writeInt(body.z());
        }
        FrontierWorldStateCodec.writePosition(out, travel.cargoAnchor().surface().support());
        out.writeByte(travel.approaches().size());
        for (var entry : travel.approaches().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            FrontierWorldPayloadCodecs.writeSubject(out, entry.getKey()); StationApproachStateCodec.write(out, entry.getValue());
        }
    }
    static OperationTravel read(DataInputStream in) throws IOException {
        var front = FrontierWorldPayloadCodecs.readSubject(in).value(); var topology = TraversalTopologyStateCodec.read(in);
        int cursor = in.readUnsignedShort(); int count = in.readUnsignedByte();
        if (count < 1 || count > 8) throw new IllegalArgumentException("invalid travel cohort size");
        Map<SubjectId, BodyPosition> formation = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            var actor = FrontierWorldPayloadCodecs.readSubject(in).value();
            var body = new BodyPosition(in.readInt(), in.readInt(), in.readInt());
            if (formation.put(actor, body) != null) throw new IllegalArgumentException("duplicate travel member");
        }
        var cargo = TransportAnchor.atSupportCell(FrontierWorldStateCodec.readPosition(in));
        int approachesCount = in.readUnsignedByte();
        if (approachesCount > count) throw new IllegalArgumentException("invalid travel approach count");
        Map<SubjectId, StationApproachState> approaches = new LinkedHashMap<>();
        for (int i = 0; i < approachesCount; i++) {
            var actor = FrontierWorldPayloadCodecs.readSubject(in).value();
            if (approaches.put(actor, StationApproachStateCodec.read(in)) != null)
                throw new IllegalArgumentException("duplicate travel approach");
        }
        return new OperationTravel(front, topology, cursor, formation, cargo, approaches);
    }
}
