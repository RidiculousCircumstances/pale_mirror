package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.EngineeringWorkSceneCause;
import io.farfrontier.palemirror.frontier.v3.model.LogisticsSceneCause;
import io.farfrontier.palemirror.frontier.v3.model.MedicalTreatmentSceneCause;
import io.farfrontier.palemirror.frontier.v3.model.ProductionWorkSceneCause;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestSceneCause;
import io.farfrontier.palemirror.frontier.v3.model.SceneCause;
import io.farfrontier.palemirror.frontier.v3.model.SettlementAssaultSceneCause;
import io.farfrontier.palemirror.frontier.v3.model.SettlementServiceWorkSceneCause;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Optional;

/** Stable sealed-codec boundary for the typed cause which owns one durable scene lease. */
final class SceneCauseStateCodec {
    private SceneCauseStateCodec() { }

    static void write(DataOutputStream output, SceneCause cause) throws IOException {
        if (cause instanceof LogisticsSceneCause logistics) {
            output.writeByte(0); FrontierWorldStateCodec.writeString(output, logistics.operationId().value());
            FrontierWorldStateCodec.writeString(output, logistics.cargoId().value()); output.writeBoolean(logistics.engagementId().isPresent());
            if (logistics.engagementId().isPresent()) FrontierWorldStateCodec.writeString(output, logistics.engagementId().orElseThrow().value());
            FrontierWorldStateCodec.writePosition(output, logistics.cargoPosition()); return;
        }
        if (cause instanceof SettlementAssaultSceneCause assault) {
            output.writeByte(1); FrontierWorldStateCodec.writeString(output, assault.assaultId().value());
            FrontierWorldStateCodec.writeString(output, assault.settlementId().value()); return;
        }
        if (cause instanceof EngineeringWorkSceneCause engineering) {
            output.writeByte(2); FrontierWorldStateCodec.writeString(output, engineering.projectId().value()); output.writeInt(engineering.workCellIndex()); return;
        }
        if (cause instanceof MedicalTreatmentSceneCause medical) { output.writeByte(3); FrontierWorldStateCodec.writeString(output, medical.operationId().value()); return; }
        if (cause instanceof ResourceSiteHarvestSceneCause harvest) { output.writeByte(4); FrontierWorldStateCodec.writeString(output, harvest.jobId().value()); return; }
        if (cause instanceof ProductionWorkSceneCause production) { output.writeByte(5); FrontierWorldStateCodec.writeString(output, production.jobId().value()); return; }
        if (cause instanceof SettlementServiceWorkSceneCause service) { output.writeByte(6); FrontierWorldStateCodec.writeString(output, service.workId().value()); return; }
        throw new IllegalArgumentException("unknown scene cause: " + cause.getClass().getName());
    }

    static SceneCause read(DataInputStream input) throws IOException {
        return switch (input.readUnsignedByte()) {
            case 0 -> new LogisticsSceneCause(new SubjectId(FrontierWorldStateCodec.readString(input)),
                    new SubjectId(FrontierWorldStateCodec.readString(input)), readOptionalSubject(input), FrontierWorldStateCodec.readPosition(input));
            case 1 -> new SettlementAssaultSceneCause(new SubjectId(FrontierWorldStateCodec.readString(input)), new SubjectId(FrontierWorldStateCodec.readString(input)));
            case 2 -> new EngineeringWorkSceneCause(new SubjectId(FrontierWorldStateCodec.readString(input)), input.readInt());
            case 3 -> new MedicalTreatmentSceneCause(new SubjectId(FrontierWorldStateCodec.readString(input)));
            case 4 -> new ResourceSiteHarvestSceneCause(new SubjectId(FrontierWorldStateCodec.readString(input)));
            case 5 -> new ProductionWorkSceneCause(new SubjectId(FrontierWorldStateCodec.readString(input)));
            case 6 -> new SettlementServiceWorkSceneCause(new SubjectId(FrontierWorldStateCodec.readString(input)));
            default -> throw new IllegalArgumentException("unknown scene cause kind");
        };
    }

    private static Optional<SubjectId> readOptionalSubject(DataInputStream input) throws IOException {
        return input.readBoolean() ? Optional.of(new SubjectId(FrontierWorldStateCodec.readString(input))) : Optional.empty();
    }
}
