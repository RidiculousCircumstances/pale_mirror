package io.farfrontier.palemirror.frontier.v3.model;

import java.util.Objects;

/** Durable, inspectable owning response for one exact resource-site cell. */
public record ResourceSiteConflictDisposition(BlockPosition position, ResourceSiteConflictReason reason,
                                              ResourceSiteConflictPolicy policy, ConflictIncident incident) {
    public ResourceSiteConflictDisposition {
        Objects.requireNonNull(position, "resource-site conflict position");
        Objects.requireNonNull(reason, "resource-site conflict reason");
        Objects.requireNonNull(policy, "resource-site conflict policy");
        incident = Objects.requireNonNull(incident, "resource-site conflict incident");
        if (requiresRecoveryInspection(reason)
                != (policy == ResourceSiteConflictPolicy.RECOVERY_INSPECTION_REQUIRED)) {
            throw new IllegalArgumentException("resource-site conflict reason and policy disagree");
        }
    }

    public static ResourceSiteConflictDisposition terminal(BlockPosition position, ResourceSiteConflictReason reason, ConflictIncident incident) {
        if (requiresRecoveryInspection(reason)) {
            throw new IllegalArgumentException("recovery ambiguity is not a terminal physical disposition");
        }
        return new ResourceSiteConflictDisposition(position, reason, ResourceSiteConflictPolicy.TERMINAL_REPAIR_REQUIRED, incident);
    }

    public static ResourceSiteConflictDisposition recovery(BlockPosition position, ResourceSiteConflictReason reason, ConflictIncident incident) {
        if (!requiresRecoveryInspection(reason)) throw new IllegalArgumentException("terminal conflict reason cannot become recovery inspection");
        return new ResourceSiteConflictDisposition(position, reason,
                ResourceSiteConflictPolicy.RECOVERY_INSPECTION_REQUIRED, incident);
    }

    public static ResourceSiteConflictDisposition recovery(BlockPosition position, ConflictIncident incident) {
        return recovery(position, ResourceSiteConflictReason.RECOVERY_UNRESOLVED, incident);
    }

    public static boolean requiresRecoveryInspection(ResourceSiteConflictReason reason) {
        return reason == ResourceSiteConflictReason.RECOVERY_UNRESOLVED
                || reason == ResourceSiteConflictReason.CARRIER_FENCE_UNRESOLVED;
    }
}
