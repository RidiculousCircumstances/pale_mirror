package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.model.CultivationReview;
import io.farfrontier.palemirror.frontier.v3.model.agriculture.CultivationAreaPlanner.Reason;
import java.io.*;

final class CultivationReviewStateCodec {
    static void write(DataOutputStream output, CultivationReview value) throws IOException {
        output.writeLong(value.atTick()); output.writeLong(value.targetOutput()); output.writeLong(value.expectedOutput());
        output.writeLong(value.firstHarvestAfterTicks()); output.writeLong(value.horizonTicks());
        output.writeInt(value.selectedCells()); output.writeInt(value.plantedCells()); output.writeInt(value.reserveCells());
        output.writeByte(switch (value.reason()) {
            case STABLE -> 1; case DOMESTIC_RISK -> 2; case OUTPUT_TARGET -> 3;
            case DEVELOPMENT_LIMIT -> 4; case SUITABLE_LAND_LIMIT -> 5; case SAFE_FALLOW -> 6;
        });
    }
    static CultivationReview read(DataInputStream input) throws IOException {
        long at = input.readLong(), target = input.readLong(), expected = input.readLong(), first = input.readLong(), horizon = input.readLong();
        int selected = input.readInt(), planted = input.readInt(), reserve = input.readInt();
        var reason = switch (input.readUnsignedByte()) {
            case 1 -> Reason.STABLE; case 2 -> Reason.DOMESTIC_RISK; case 3 -> Reason.OUTPUT_TARGET;
            case 4 -> Reason.DEVELOPMENT_LIMIT; case 5 -> Reason.SUITABLE_LAND_LIMIT; case 6 -> Reason.SAFE_FALLOW;
            default -> throw new IllegalArgumentException("unknown cultivation reason");
        };
        return new CultivationReview(at, target, expected, first, horizon, selected, planted, reserve, reason);
    }
    private CultivationReviewStateCodec() { }
}
