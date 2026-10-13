package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.PayloadCodec;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.io.*;
import static io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec.*;

final class ProductionStationRecipeCodec implements PayloadCodec {
    @Override public String type() { return ProductionStationRecipeChanged.TYPE; }
    @Override public byte[] encode(FrontierPayload payload) {
        var event = (ProductionStationRecipeChanged) payload;
        return FrontierWorldPayloadCodecs.encodeProduction(out -> {
            out.writeByte(switch (event.stage()) { case COLD_COMPLETED -> 1; case HOT_PREPARED -> 2; case HOT_COMPLETED -> 3; });
            writeBoundary(out, event.boundary()); PhysicalObservationStackCodec.writeStacks(out, event.observed());
        });
    }
    @Override public FrontierPayload decode(byte[] bytes) {
        return FrontierWorldPayloadCodecs.decodeProduction(bytes, in -> new ProductionStationRecipeChanged(
                switch (in.readUnsignedByte()) { case 1 -> ProductionStationRecipeChanged.Stage.COLD_COMPLETED;
                    case 2 -> ProductionStationRecipeChanged.Stage.HOT_PREPARED; case 3 -> ProductionStationRecipeChanged.Stage.HOT_COMPLETED;
                    default -> throw new IllegalArgumentException("unknown station recipe boundary"); }, readBoundary(in), PhysicalObservationStackCodec.readStacks(in)));
    }
    static void writeBoundary(DataOutputStream out, ProductionStationRecipeBoundary value) throws IOException {
        writeString(out, value.stationId().value()); writeString(out, value.containerId().value()); writeString(out, value.operationId().value().value());
        out.writeLong(value.machineRevision()); out.writeLong(value.authorityEpoch());
        PhysicalObservationStackCodec.writeStacks(out, value.before()); PhysicalObservationStackCodec.writeStacks(out, value.after());
    }
    static ProductionStationRecipeBoundary readBoundary(DataInputStream in) throws IOException {
        return new ProductionStationRecipeBoundary(new SubjectId(readString(in)), new SubjectId(readString(in)),
                new ProductionOperation.Id(new SubjectId(readString(in))), in.readLong(), in.readLong(),
                PhysicalObservationStackCodec.readStacks(in), PhysicalObservationStackCodec.readStacks(in));
    }
}
