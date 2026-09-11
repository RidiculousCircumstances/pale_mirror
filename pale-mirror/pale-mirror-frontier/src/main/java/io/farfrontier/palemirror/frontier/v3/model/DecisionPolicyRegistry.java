package io.farfrontier.palemirror.frontier.v3.model;

import java.util.Map;
import java.util.Objects;

/** Closed world-bound registry; planners may not substitute an owner policy at runtime. */
public final class DecisionPolicyRegistry {
    private static final Map<DecisionAuthorityKind, DecisionPolicyDescriptor> REGISTERED = Map.of(
            DecisionAuthorityKind.SETTLEMENT, new DecisionPolicyDescriptor("frontier:settlement", 1),
            DecisionAuthorityKind.HIVEMIND, new DecisionPolicyDescriptor("frontier:hivemind", 1));

    private DecisionPolicyRegistry() { }

    public static DecisionPolicyDescriptor require(DecisionAuthority authority) {
        Objects.requireNonNull(authority, "decision authority");
        DecisionPolicyDescriptor registered = REGISTERED.get(authority.kind());
        if (!authority.policy().equals(registered)) {
            throw new IllegalArgumentException("decision authority does not name a registered policy");
        }
        return registered;
    }
}
