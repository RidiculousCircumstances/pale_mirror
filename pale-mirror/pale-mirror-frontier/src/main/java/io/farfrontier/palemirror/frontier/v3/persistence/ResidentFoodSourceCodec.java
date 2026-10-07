package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.model.*;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/** Explicit source and presentation tags; hydration never guesses source from an account or ID. */
final class ResidentFoodSourceCodec {
    private ResidentFoodSourceCodec() { }
    static void write(DataOutputStream out, ResidentFoodSource source) throws IOException {
        switch (source) {
            case ResidentFoodSource.Depot depot -> {
                out.writeByte(1); UnitGroupStateCodec.id(out, depot.settlementId());
                UnitGroupStateCodec.id(out, depot.containerId()); UnitGroupStateCodec.id(out, depot.accountId());
                ActorItemSlotCodec.write(out, depot.portionSlot());
            }
            case ResidentFoodSource.Personal personal -> {
                out.writeByte(2); UnitGroupStateCodec.id(out, personal.actorId());
                UnitGroupStateCodec.id(out, personal.accountId()); ActorItemSlotCodec.write(out, personal.slot());
            }
        }
    }
    static ResidentFoodSource read(DataInputStream in) throws IOException {
        return switch (in.readUnsignedByte()) {
            case 1 -> new ResidentFoodSource.Depot(UnitGroupStateCodec.id(in), UnitGroupStateCodec.id(in),
                    UnitGroupStateCodec.id(in), ActorItemSlotCodec.read(in));
            case 2 -> new ResidentFoodSource.Personal(UnitGroupStateCodec.id(in), UnitGroupStateCodec.id(in), ActorItemSlotCodec.read(in));
            default -> throw new IllegalArgumentException("unknown resident food source tag");
        };
    }
}
