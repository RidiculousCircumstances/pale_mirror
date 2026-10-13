package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.PayloadCodec;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.*;
import static io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec.*;

final class ProductionStationLifecycleCodecs {
    static List<PayloadCodec> codecs() { return List.of(new Installed(), new Retired()); }
    private record Installed() implements PayloadCodec {
        @Override public String type() { return ProductionStationInstalled.TYPE; }
        @Override public byte[] encode(FrontierPayload payload) {
            var value = (ProductionStationInstalled) payload;
            return FrontierWorldPayloadCodecs.encodeProduction(out -> {
                ContainerRecordStateCodec.write(out, Map.of(value.container().id(), value.container())); writePosition(out, value.position());
                out.writeByte(value.contents().size());
                for (var lot : value.contents()) {
                    writeString(out, lot.id().value()); writeString(out, lot.economicOwnerId().value()); writeString(out, lot.itemKind());
                    out.writeByte(lot.quantity()); writeString(out, lot.provenance());
                }
            });
        }
        @Override public FrontierPayload decode(byte[] bytes) {
            return FrontierWorldPayloadCodecs.decodeProduction(bytes, in -> {
                var records = ContainerRecordStateCodec.read(in);
                if (records.size() != 1) throw new IllegalArgumentException("installation must declare exactly one installed container");
                var position = readPosition(in); int count = in.readUnsignedByte();
                if (count > 3) throw new IllegalArgumentException("installed contents exceed machine ports");
                var lots = new ArrayList<ResourceLot>();
                for (int index = 0; index < count; index++) lots.add(new ResourceLot(new SubjectId(readString(in)), new SubjectId(readString(in)),
                        readString(in), in.readUnsignedByte(), readString(in), List.of()));
                return new ProductionStationInstalled(records.values().iterator().next(), position, lots);
            });
        }
    }
    private record Retired() implements PayloadCodec {
        @Override public String type() { return ProductionStationRetired.TYPE; }
        @Override public byte[] encode(FrontierPayload payload) {
            var value = (ProductionStationRetired) payload;
            return FrontierWorldPayloadCodecs.encodeProduction(out -> { writeString(out, value.stationId().value()); out.writeLong(value.machineRevision()); });
        }
        @Override public FrontierPayload decode(byte[] bytes) {
            return FrontierWorldPayloadCodecs.decodeProduction(bytes, in -> new ProductionStationRetired(new SubjectId(readString(in)), in.readLong()));
        }
    }
    private ProductionStationLifecycleCodecs() { }
}
