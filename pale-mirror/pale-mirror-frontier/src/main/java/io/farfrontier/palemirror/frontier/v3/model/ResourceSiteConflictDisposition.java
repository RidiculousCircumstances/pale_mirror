package io.farfrontier.palemirror.frontier.v3.model;

import java.util.Objects;

/** Durable, inspectable owning response for one exact resource-site cell. */
public record ResourceSiteConflictDisposition(BlockPosition position, ResourceSiteConflictReason reason,
                                              ResourceSiteConflictPolicy policy) {
    public ResourceSiteConflictDisposition {
        Objects.requireNonNull(position, "resource-site conflict position");
        Objects.requireNonNull(reason, "resource-site conflict reason");
        Objects.requireNonNull(policy, "resource-site conflict policy");
        if (reason == ResourceSiteConflictReason.RECOVERY_UNRESOLVED
                != (policy == ResourceSiteConflictPolicy.RECOVERY_INSPECTION_REQUIRED)) {
            throw new IllegalArgumentException("resource-site conflict reason and policy disagree");
        }
    }

    public static ResourceSiteConflictDisposition terminal(BlockPosition position, ResourceSiteConflictReason reason) {
        if (reason == ResourceSiteConflictReason.RECOVERY_UNRESOLVED) {
            throw new IllegalArgumentException("recovery ambiguity is not a terminal physical disposition");
        }
        return new ResourceSiteConflictDisposition(position, reason, ResourceSiteConflictPolicy.TERMINAL_REPAIR_REQUIRED);
    }

    static ResourceSiteConflictDisposition recovery(BlockPosition position) {
        return new ResourceSiteConflictDisposition(position, ResourceSiteConflictReason.RECOVERY_UNRESOLVED,
                ResourceSiteConflictPolicy.RECOVERY_INSPECTION_REQUIRED);
    }
}
