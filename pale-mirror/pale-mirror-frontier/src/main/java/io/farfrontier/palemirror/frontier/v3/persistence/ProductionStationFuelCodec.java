package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.PayloadCodec;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.io.*;
import java.util.*;
import static io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec.*;

final class ProductionStationFuelCodec implements PayloadCodec {
    @Override public String type() { return ProductionStationFuelChanged.TYPE; }
    @Override public byte[] encode(FrontierPayload payload) {
        var event = (ProductionStationFuelChanged) payload;
        return FrontierWorldPayloadCodecs.encodeProduction(out -> {
            out.writeByte(switch (event.stage()) { case COLD_CONSUMED -> 1; case HOT_PREPARED -> 2; case HOT_CONSUMED -> 3; });
            writeBurn(out, event.burn()); PhysicalObservationStackCodec.writeStacks(out, event.remaining());
        });
    }
    @Override public FrontierPayload decode(byte[] bytes) {
        return FrontierWorldPayloadCodecs.decodeProduction(bytes, in -> new ProductionStationFuelChanged(switch (in.readUnsignedByte()) {
            case 1 -> ProductionStationFuelChanged.Stage.COLD_CONSUMED; case 2 -> ProductionStationFuelChanged.Stage.HOT_PREPARED;
            case 3 -> ProductionStationFuelChanged.Stage.HOT_CONSUMED; default -> throw new IllegalArgumentException("unknown station fuel event stage");
        }, readBurn(in), PhysicalObservationStackCodec.readStacks(in)));
    }
    static void writeBurn(DataOutputStream out, ProductionStationFuelBurn burn) throws IOException {
        writeString(out, burn.stationId().value()); writeString(out, burn.containerId().value()); writeString(out, burn.accountId().value());
        out.writeLong(burn.machineRevision()); out.writeLong(burn.authorityEpoch()); writeString(out, burn.itemKind());
        ShipmentStateCodec.lots(out, burn.lots()); out.writeLong(burn.heat()); out.writeByte(burn.source().size());
        for (var source : burn.source()) {
            FungibleResourceStateCodec.writePhysicalAddress(out, source.address());
            out.writeByte(source.before()); out.writeByte(source.moved()); out.writeLong(source.epoch());
        }
    }
    static ProductionStationFuelBurn readBurn(DataInputStream in) throws IOException {
        var station = new SubjectId(readString(in)); var container = new SubjectId(readString(in)); var account = new SubjectId(readString(in));
        long revision = in.readLong(), epoch = in.readLong(); var kind = readString(in); var lots = ShipmentStateCodec.lots(in);
        long heat = in.readLong(); int count = in.readUnsignedByte();
        if (count > 1) throw new IllegalArgumentException("station fuel source exceeds declared port bound");
        var source = new ArrayList<MaterialSourceSelection.Slice>();
        for (int index = 0; index < count; index++) source.add(new MaterialSourceSelection.Slice(
                FungibleResourceStateCodec.readPhysicalAddress(in), in.readUnsignedByte(), in.readUnsignedByte(), in.readLong()));
        return new ProductionStationFuelBurn(station, container, account, revision, epoch, kind, lots, heat, source);
    }
}
