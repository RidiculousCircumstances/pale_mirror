package io.farfrontier.palemirror.frontier.reference;

import java.util.List;
import java.util.Objects;

/** Compact terminal record retained after a V2 charter can no longer affect planning. */
public record ReferenceV2CharterReceipt(
        int id,
        int leaderId,
        List<Integer> members,
        String target,
        int openedDay,
        int expiresDay,
        int closedDay,
        String status,
        String reason
) {
    public ReferenceV2CharterReceipt {
        members = List.copyOf(Objects.requireNonNull(members, "members"));
        target = Objects.requireNonNull(target, "target");
        status = Objects.requireNonNull(status, "status");
        reason = Objects.requireNonNull(reason, "reason");
    }
}
