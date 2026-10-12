package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentLifecycleOwner;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentRoleBinding;
import java.util.Objects;

/** Retained admission identity/instant for one atomic transfer, not a journey or another job owner. */
public record EquipmentServiceRequest(ServiceAccessDemand.Identity identity, long requestedAtTick) {
    public EquipmentServiceRequest {
        Objects.requireNonNull(identity, "declared equipment service identity");
        if (requestedAtTick < 0) throw new IllegalArgumentException("negative equipment service request instant");
    }
    /** Validate the complete producer declaration before an intent/event/recovered candidate can escape. */
    public void validateFor(PhysicalIntentLifecycleOwner owner, PhysicalIntentKind kind, PhysicalIntentRoleBinding roles) {
        EquipmentServiceComposition.CAPABILITIES.require(owner, kind, roles, this);
    }
}
