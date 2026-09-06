package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.List;
import java.util.Objects;

/**
 * Immutable exact care team retained by one medical or evacuation operation.
 *
 * <p>The population register continues to own residents.  This small value is
 * only the operation's exclusive current-work claim; it grants neither a
 * profession nor supplies nor a combat function.</p>
 */
public record MedicalEvacuationTeam(SubjectId id, SubjectId ownerId, SubjectId settlementId,
                                   SubjectId leaderId, List<SubjectId> memberIds) {
    public static final int MIN_MEMBERS = 1;
    public static final int MAX_MEMBERS = 3;

    public MedicalEvacuationTeam {
        Objects.requireNonNull(id, "medical team id");
        Objects.requireNonNull(ownerId, "medical team owner");
        Objects.requireNonNull(settlementId, "medical team settlement");
        Objects.requireNonNull(leaderId, "medical team leader");
        memberIds = List.copyOf(Objects.requireNonNull(memberIds, "medical team members"));
        if (!id.equals(idFor(ownerId)) || memberIds.size() < MIN_MEMBERS || memberIds.size() > MAX_MEMBERS
                || memberIds.stream().distinct().count() != memberIds.size() || !memberIds.contains(leaderId)) {
            throw new IllegalArgumentException("medical team must retain one to three distinct members and its exact leader");
        }
    }

    public static MedicalEvacuationTeam forOperation(SubjectId operationId, SubjectId settlementId, List<SubjectId> members) {
        Objects.requireNonNull(operationId, "medical operation");
        if (!operationId.value().startsWith("medical:")) throw new IllegalArgumentException("medical team needs a medical operation owner");
        List<SubjectId> exactMembers = List.copyOf(Objects.requireNonNull(members, "medical candidates"));
        if (exactMembers.isEmpty()) throw new IllegalArgumentException("medical team needs one exact leader");
        return new MedicalEvacuationTeam(idFor(operationId), operationId, settlementId, exactMembers.getFirst(), exactMembers);
    }

    public static SubjectId idFor(SubjectId operationId) {
        return new SubjectId("unit:medical-" + operationId.value().substring("medical:".length()));
    }
}
