package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.PayloadCodec;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.UUID;
import static io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec.*;

final class ProductionStationOperatorCodec implements PayloadCodec {
    @Override public String type() { return ProductionStationOperatorChanged.TYPE; }
    @Override public byte[] encode(FrontierPayload payload) {
        var event = (ProductionStationOperatorChanged) payload;
        return FrontierWorldPayloadCodecs.encodeProduction(out -> {
            writeString(out, event.stationId().value()); out.writeLong(event.machineRevision()); writeString(out, event.playerId().toString());
            writeString(out, event.recipeId().value()); out.writeByte(switch (event.stage()) { case START -> 1; case ATTEND -> 2; case PAUSE -> 3; case RESUME -> 4; });
            out.writeLong(event.atTick());
        });
    }
    @Override public FrontierPayload decode(byte[] bytes) {
        return FrontierWorldPayloadCodecs.decodeProduction(bytes, in -> new ProductionStationOperatorChanged(
                new SubjectId(readString(in)), in.readLong(), UUID.fromString(readString(in)), new ProductionRecipe.Id(readString(in)),
                switch (in.readUnsignedByte()) { case 1 -> ProductionStationOperatorChanged.Stage.START;
                    case 2 -> ProductionStationOperatorChanged.Stage.ATTEND; case 3 -> ProductionStationOperatorChanged.Stage.PAUSE;
                    case 4 -> ProductionStationOperatorChanged.Stage.RESUME;
                    default -> throw new IllegalArgumentException("unknown station operator boundary"); }, in.readLong()));
    }
}
