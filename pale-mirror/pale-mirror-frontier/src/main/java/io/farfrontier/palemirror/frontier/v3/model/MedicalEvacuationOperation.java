package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/**
 * One exact patient, retained care team and exact treatment supply.
 *
 * <p>The first operation kind is local infirmary treatment.  The type is
 * deliberately named for the wider medical/evacuation owner because a future
 * evacuation keeps the same patient/team/supply ownership rather than adding
 * a parallel roster.</p>
 */
public record MedicalEvacuationOperation(SubjectId id, SubjectId settlementId, SubjectId patientId,
                                         SubjectId infirmaryId, MedicalEvacuationTeam team,
                                         SubjectId supplyItemId, PhysicalIntentId consumptionIntentId,
                                         MedicalEvacuationStatus status, long terminalAtTick) {
    /** A bounded recent receipt tail; live/unknown work is never compacted. */
    public static final int RETAINED_COMPLETED = 64;
    public static final int MAX_RETAINED = HumanPopulation.MAX_PROVISIONS + RETAINED_COMPLETED;

    public MedicalEvacuationOperation {
        Objects.requireNonNull(id, "medical operation id");
        Objects.requireNonNull(settlementId, "medical operation settlement");
        Objects.requireNonNull(patientId, "medical operation patient");
        Objects.requireNonNull(infirmaryId, "medical operation infirmary");
        Objects.requireNonNull(team, "medical operation team");
        Objects.requireNonNull(supplyItemId, "medical operation supply");
        Objects.requireNonNull(consumptionIntentId, "medical operation consumption intent");
        Objects.requireNonNull(status, "medical operation status");
        if (!id.value().startsWith("medical:") || !team.ownerId().equals(id) || !team.settlementId().equals(settlementId)
                || team.memberIds().contains(patientId) || !consumptionIntentId.value().equals("intent:" + id.value().replace(':', '-') + "-consume")) {
            throw new IllegalArgumentException("medical operation must retain distinct patient, team and stable exact consumption intent");
        }
        if (((status == MedicalEvacuationStatus.COMPLETED || status == MedicalEvacuationStatus.BLOCKED) && terminalAtTick < 0L)
                || (status != MedicalEvacuationStatus.COMPLETED && status != MedicalEvacuationStatus.BLOCKED && terminalAtTick != -1L)) {
            throw new IllegalArgumentException("only terminal medical operations retain a terminal tick");
        }
    }

    /** Unknown recovery still retains the exact people until the physical result is reconciled. */
    public boolean active() { return status == MedicalEvacuationStatus.PREPARED || status == MedicalEvacuationStatus.TREATING
            || status == MedicalEvacuationStatus.UNKNOWN_AFTER_RESTART; }

    public boolean requiresSupply() { return status == MedicalEvacuationStatus.PREPARED || status == MedicalEvacuationStatus.TREATING; }

    /** Only terminal treatment receipts are compactable; unknown effects retain their people. */
    public boolean compactable() { return status == MedicalEvacuationStatus.COMPLETED || status == MedicalEvacuationStatus.BLOCKED; }

    public MedicalEvacuationOperation withStatus(MedicalEvacuationStatus next, long atTick) {
        return new MedicalEvacuationOperation(id, settlementId, patientId, infirmaryId, team, supplyItemId, consumptionIntentId, next,
                next == MedicalEvacuationStatus.COMPLETED || next == MedicalEvacuationStatus.BLOCKED ? atTick : -1L);
    }
}
