package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.model.*;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/** Explicit family predecessor bytes; decoding never grants body or execution authority. */
final class WorkObservationCodecs {
    private WorkObservationCodecs() { }
    static void writeProduction(DataOutputStream out, ProductionWorkObservation value) throws IOException {
        ActorHotObservationCodec.write(out, value.authority());
        FrontierWorldPayloadCodecs.writeString(out, value.topologyId().value());
        out.writeLong(value.topologyRevision()); out.writeShort(value.cursor());
        ProductionWorkProgressStateCodec.write(out, value.progress()); out.writeLong(value.spatialRevision());
    }
    static ProductionWorkObservation readProduction(DataInputStream in) throws IOException {
        return new ProductionWorkObservation(ActorHotObservationCodec.read(in),
                new TraversalTopologyId(FrontierWorldPayloadCodecs.readString(in)), in.readLong(),
                in.readUnsignedShort(), ProductionWorkProgressStateCodec.read(in), in.readLong());
    }
    static void writeService(DataOutputStream out, SettlementServiceWorkObservation value) throws IOException {
        ActorHotObservationCodec.write(out, value.authority()); out.writeByte(value.phase().wireTag());
        out.writeShort(value.inputCursor()); out.writeShort(value.workCursor()); out.writeByte(value.completedWorkTicks());
    }
    static SettlementServiceWorkObservation readService(DataInputStream in) throws IOException {
        return new SettlementServiceWorkObservation(ActorHotObservationCodec.read(in),
                SettlementServiceWorkPhase.fromWireTag(in.readUnsignedByte()), in.readUnsignedShort(),
                in.readUnsignedShort(), in.readUnsignedByte());
    }
}
