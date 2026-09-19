package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentLifecycleOwner;

import java.util.Set;

/** Explicit family-declared policy for an owner that intentionally admits no physical intent. */
final class NoPhysicalIntentLifecyclePolicy extends AbstractPhysicalIntentLifecycleCapability {
    NoPhysicalIntentLifecyclePolicy(PhysicalIntentLifecycleOwner owner) {
        super(owner, Set.of(), PhysicalIntentLifecycleRetirementPolicy.noPhysical(owner),
                PhysicalIntentRetirementAccount.noPhysical(owner));
    }
}
