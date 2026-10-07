package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.model.TransportMission;
import java.io.*;
import java.util.ArrayList;

final class TransportMissionCodec {
    private TransportMissionCodec() { }
    static void write(DataOutputStream out, TransportMission value) throws IOException {
        UnitGroupStateCodec.id(out, value.id()); UnitGroupStateCodec.id(out, value.groupId()); out.writeInt(value.shipmentIds().size());
        for (var id : value.shipmentIds()) UnitGroupStateCodec.id(out, id);
        ShipmentStateCodec.endpoint(out, value.sender()); ShipmentStateCodec.endpoint(out, value.receiver());
        UnitGroupStateCodec.surface(out, value.homeRendezvous()); UnitGroupStateCodec.surface(out, value.destinationRendezvous());
        stage(out, value.stage()); out.writeLong(value.revision());
        out.writeBoolean(value.financialBudgetId().isPresent());
        if (value.financialBudgetId().isPresent()) UnitGroupStateCodec.id(out, value.financialBudgetId().orElseThrow());
        out.writeBoolean(value.supplies().isPresent());
        if (value.supplies().isPresent()) ExpeditionSupplyLoadCodec.write(out, value.supplies().orElseThrow());
    }
    static TransportMission read(DataInputStream in) throws IOException {
        var id = UnitGroupStateCodec.id(in); var group = UnitGroupStateCodec.id(in);
        int n = UnitGroupStateCodec.count(in, 32); var shipments = new ArrayList<io.farfrontier.palemirror.frontier.v3.api.SubjectId>();
        for (int i = 0; i < n; i++) shipments.add(UnitGroupStateCodec.id(in));
        return new TransportMission(id, group, shipments, ShipmentStateCodec.endpoint(in), ShipmentStateCodec.endpoint(in),
                UnitGroupStateCodec.surface(in), UnitGroupStateCodec.surface(in), stage(in), in.readLong(),
                in.readBoolean() ? java.util.Optional.of(UnitGroupStateCodec.id(in)) : java.util.Optional.empty(),
                in.readBoolean() ? java.util.Optional.of(ExpeditionSupplyLoadCodec.read(in)) : java.util.Optional.empty());
    }
    static void stage(DataOutputStream out, TransportMission.Stage stage) throws IOException {
        out.writeByte(switch (stage) { case LOADING -> 1; case OUTBOUND -> 2; case UNLOADING -> 3; case RETURNING -> 4; case COMPLETE -> 5; });
    }
    static TransportMission.Stage stage(DataInputStream in) throws IOException {
        return switch (in.readUnsignedByte()) { case 1 -> TransportMission.Stage.LOADING; case 2 -> TransportMission.Stage.OUTBOUND; case 3 -> TransportMission.Stage.UNLOADING; case
                4 -> TransportMission.Stage.RETURNING; case 5 -> TransportMission.Stage.COMPLETE; default -> throw new IllegalArgumentException("unknown transport stage tag"); };
    }
}
