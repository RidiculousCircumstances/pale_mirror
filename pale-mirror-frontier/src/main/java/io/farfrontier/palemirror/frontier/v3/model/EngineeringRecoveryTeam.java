package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.List;
import java.util.Objects;

/**
 * Immutable exact work crew retained by one engineering/recovery owner.
 *
 * <p>This is intentionally embedded in {@link RouteConstruction}: residents remain in the
 * population register, while this value is their exclusive current-work claim.  It is not a
 * global roster and it grants neither tools nor a tactical function.</p>
 */
public record EngineeringRecoveryTeam(SubjectId id, SubjectId ownerId, SubjectId settlementId,
                                      SubjectId leaderId, List<SubjectId> memberIds) {
    public static final int MIN_MEMBERS = 1;
    public static final int MAX_MEMBERS = 4;

    public EngineeringRecoveryTeam {
        Objects.requireNonNull(id, "engineering team id");
        Objects.requireNonNull(ownerId, "engineering team owner");
        Objects.requireNonNull(settlementId, "engineering team settlement");
        Objects.requireNonNull(leaderId, "engineering team leader");
        memberIds = List.copyOf(Objects.requireNonNull(memberIds, "engineering team members"));
        if (!id.equals(idFor(ownerId)) || memberIds.size() < MIN_MEMBERS || memberIds.size() > MAX_MEMBERS
                || memberIds.stream().distinct().count() != memberIds.size() || !memberIds.contains(leaderId)) {
            throw new IllegalArgumentException("engineering team must retain one to four distinct members and its exact leader");
        }
    }

    public static EngineeringRecoveryTeam forProject(SubjectId projectId, SubjectId settlementId, List<SubjectId> members) {
        Objects.requireNonNull(projectId, "engineering project");
        if (!projectId.value().startsWith("construction:")) throw new IllegalArgumentException("engineering team needs a construction owner");
        List<SubjectId> exactMembers = List.copyOf(Objects.requireNonNull(members, "engineering candidates"));
        if (exactMembers.isEmpty()) throw new IllegalArgumentException("engineering team needs one exact leader");
        return new EngineeringRecoveryTeam(idFor(projectId), projectId, settlementId, exactMembers.getFirst(), exactMembers);
    }

    public static SubjectId idFor(SubjectId projectId) {
        return new SubjectId("unit:engineering-" + projectId.value().substring("construction:".length()));
    }
}
