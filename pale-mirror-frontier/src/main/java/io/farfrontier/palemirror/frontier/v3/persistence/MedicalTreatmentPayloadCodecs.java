package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.kernel.PayloadCodec;
import io.farfrontier.palemirror.frontier.v3.model.*;

import java.util.ArrayList;
import java.util.List;

/** WAL codecs owned by the exact medical-operation boundary. */
final class MedicalTreatmentPayloadCodecs {
    private MedicalTreatmentPayloadCodecs() { }

    static PayloadCodec started() {
        return new PayloadCodec() {
            @Override public String type() { return "frontier.medical_treatment_started"; }
            @Override public byte[] encode(FrontierPayload payload) { return FrontierWorldPayloadCodecs.encodeProduction(output -> {
                MedicalEvacuationOperation operation = ((MedicalTreatmentStarted) payload).operation();
                FrontierWorldPayloadCodecs.writeSubject(output, operation.id()); FrontierWorldPayloadCodecs.writeSubject(output, operation.settlementId());
                FrontierWorldPayloadCodecs.writeSubject(output, operation.patientId()); FrontierWorldPayloadCodecs.writeSubject(output, operation.infirmaryId());
                FrontierWorldPayloadCodecs.writeSubject(output, operation.team().id()); FrontierWorldPayloadCodecs.writeSubject(output, operation.team().leaderId());
                FrontierWorldStateCodec.writeCount(output, operation.team().memberIds().size());
                for (SubjectId member : operation.team().memberIds()) FrontierWorldPayloadCodecs.writeSubject(output, member);
                FrontierWorldPayloadCodecs.writeSubject(output, operation.supplyItemId()); FrontierWorldPayloadCodecs.writeString(output, operation.consumptionIntentId().value());
                output.writeByte(operation.status().wireTag());
            }); }
            @Override public FrontierPayload decode(byte[] bytes) { return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> {
                SubjectId id = FrontierWorldPayloadCodecs.readSubject(input).value(); SubjectId settlement = FrontierWorldPayloadCodecs.readSubject(input).value();
                SubjectId patient = FrontierWorldPayloadCodecs.readSubject(input).value(); SubjectId infirmary = FrontierWorldPayloadCodecs.readSubject(input).value();
                SubjectId teamId = FrontierWorldPayloadCodecs.readSubject(input).value(); SubjectId leader = FrontierWorldPayloadCodecs.readSubject(input).value();
                List<SubjectId> members = new ArrayList<>();
                for (int index = 0, count = FrontierWorldStateCodec.readCount(input); index < count; index++) members.add(FrontierWorldPayloadCodecs.readSubject(input).value());
                SubjectId supply = FrontierWorldPayloadCodecs.readSubject(input).value(); PhysicalIntentId intent = new PhysicalIntentId(FrontierWorldPayloadCodecs.readString(input));
                int status = input.readUnsignedByte();
                if (status >= MedicalEvacuationStatus.values().length) throw new IllegalArgumentException("unknown medical treatment status");
                MedicalEvacuationTeam team = new MedicalEvacuationTeam(teamId, id, settlement, leader, members);
                return new MedicalTreatmentStarted(new MedicalEvacuationOperation(id, settlement, patient, infirmary, team, supply, intent,
                        FrontierWireTags.require(MedicalEvacuationStatus.class, status), -1L));
            }); }
        };
    }

    static PayloadCodec transition() {
        return new PayloadCodec() {
            @Override public String type() { return "frontier.medical_treatment_transition"; }
            @Override public byte[] encode(FrontierPayload payload) { return FrontierWorldPayloadCodecs.encodeProduction(output -> {
                MedicalTreatmentTransition transition = (MedicalTreatmentTransition) payload;
                FrontierWorldPayloadCodecs.writeSubject(output, transition.operationId()); output.writeByte(transition.status().wireTag());
            }); }
            @Override public FrontierPayload decode(byte[] bytes) { return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> {
                SubjectId operation = FrontierWorldPayloadCodecs.readSubject(input).value(); int status = input.readUnsignedByte();
                if (status >= MedicalEvacuationStatus.values().length) throw new IllegalArgumentException("unknown medical treatment status");
                return new MedicalTreatmentTransition(operation, FrontierWireTags.require(MedicalEvacuationStatus.class, status));
            }); }
        };
    }
}
