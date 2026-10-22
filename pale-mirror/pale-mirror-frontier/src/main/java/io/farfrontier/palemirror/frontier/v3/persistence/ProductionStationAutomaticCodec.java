package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.PayloadCodec;
import io.farfrontier.palemirror.frontier.v3.model.*;
import static io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec.*;

final class ProductionStationAutomaticCodec implements PayloadCodec {
    @Override public String type() { return ProductionStationAutomaticStarted.TYPE; }
    @Override public byte[] encode(FrontierPayload payload) {
        var event = (ProductionStationAutomaticStarted) payload;
        return FrontierWorldPayloadCodecs.encodeProduction(out -> {
            writeString(out, event.stationId().value()); out.writeLong(event.machineRevision());
            writeString(out, event.recipeId().value()); out.writeLong(event.atTick());
        });
    }
    @Override public FrontierPayload decode(byte[] bytes) {
        return FrontierWorldPayloadCodecs.decodeProduction(bytes, in -> new ProductionStationAutomaticStarted(
                new SubjectId(readString(in)), in.readLong(), new ProductionRecipe.Id(readString(in)), in.readLong()));
    }
}
