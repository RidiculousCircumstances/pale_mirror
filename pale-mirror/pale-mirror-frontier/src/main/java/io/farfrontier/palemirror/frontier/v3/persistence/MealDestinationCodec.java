package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.io.*;

final class MealDestinationCodec {
    static void write(DataOutputStream out, MealDestination place) throws IOException {
        switch (place) {
            case MealDestination.Here here -> {
                out.writeByte(1); FrontierWorldStateCodec.writePosition(out, here.station().support());
            }
            case MealDestination.DiningArea area -> {
                out.writeByte(2); FrontierWorldStateCodec.writeString(out, area.facilityId().value());
                FrontierWorldStateCodec.writeString(out, area.zoneId());
            }
        }
    }
    static MealDestination read(DataInputStream in) throws IOException {
        return switch (in.readUnsignedByte()) {
            case 1 -> new MealDestination.Here(new SurfaceAnchor(FrontierWorldStateCodec.readPosition(in)));
            case 2 -> new MealDestination.DiningArea(new SubjectId(FrontierWorldStateCodec.readString(in)), FrontierWorldStateCodec.readString(in));
            default -> throw new IllegalArgumentException("unknown meal destination schema");
        };
    }
    private MealDestinationCodec() { }
}
