package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.List;
import java.util.Objects;

/**
 * Immutable exact work crew retained by one engineering-work owner.
 *
 * <p>This is intentionally embedded in its owning work order: residents remain in the population
 * register, while this value is their exclusive current-work claim. It is not a global roster and
 * it grants neither tools nor a tactical function.</p>
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

    public static EngineeringRecoveryTeam forWorkOrder(SubjectId workOrderId, SubjectId settlementId, List<SubjectId> members) {
        Objects.requireNonNull(workOrderId, "engineering work order");
        if (!workOrderId.value().startsWith("construction:") && !workOrderId.value().startsWith("maintenance:")) {
            throw new IllegalArgumentException("engineering team needs an owned engineering work order");
        }
        List<SubjectId> exactMembers = List.copyOf(Objects.requireNonNull(members, "engineering candidates"));
        if (exactMembers.isEmpty()) throw new IllegalArgumentException("engineering team needs one exact leader");
        return new EngineeringRecoveryTeam(idFor(workOrderId), workOrderId, settlementId, exactMembers.getFirst(), exactMembers);
    }

    public static SubjectId idFor(SubjectId workOrderId) {
        Objects.requireNonNull(workOrderId, "engineering work order");
        String value = workOrderId.value();
        if (value.startsWith("construction:")) return new SubjectId("unit:engineering-" + value.substring("construction:".length()));
        if (value.startsWith("maintenance:")) return new SubjectId("unit:engineering-" + value.substring("maintenance:".length()));
        throw new IllegalArgumentException("engineering team needs an owned engineering work order");
    }
}
