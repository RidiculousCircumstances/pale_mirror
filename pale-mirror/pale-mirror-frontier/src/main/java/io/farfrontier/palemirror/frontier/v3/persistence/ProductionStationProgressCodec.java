package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.PayloadCodec;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.Optional;
import static io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec.*;

final class ProductionStationProgressCodec implements PayloadCodec {
    @Override public String type() { return ProductionStationProgressed.TYPE; }
    @Override public byte[] encode(FrontierPayload payload) {
        var event = (ProductionStationProgressed) payload;
        return FrontierWorldPayloadCodecs.encodeProduction(out -> {
            writeString(out, event.stationId().value()); writeString(out, event.operationId().value().value());
            out.writeLong(event.machineRevision()); WorkStateCodec.writeProgress(out, event.previous());
            WorkStateCodec.writeProgress(out, Optional.of(event.next()));
        });
    }
    @Override public FrontierPayload decode(byte[] bytes) {
        return FrontierWorldPayloadCodecs.decodeProduction(bytes, in -> new ProductionStationProgressed(
                new SubjectId(readString(in)), new ProductionOperation.Id(new SubjectId(readString(in))), in.readLong(),
                WorkStateCodec.readProgress(in), WorkStateCodec.readProgress(in).orElseThrow()));
    }
}
