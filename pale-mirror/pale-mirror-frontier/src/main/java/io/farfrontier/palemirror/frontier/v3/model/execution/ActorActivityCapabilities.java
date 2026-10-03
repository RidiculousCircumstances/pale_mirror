package io.farfrontier.palemirror.frontier.v3.model.execution;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Closed declared composition. Unknown owners never gain an inferred/default strategy. */
public final class ActorActivityCapabilities {
    private final Map<ActorActivityKind, ActorActivityCapability> capabilities;
    public ActorActivityCapabilities(Set<ActorActivityKind> required, List<? extends ActorActivityCapability> registrations) {
        Objects.requireNonNull(required); Objects.requireNonNull(registrations);
        var values = new EnumMap<ActorActivityKind, ActorActivityCapability>(ActorActivityKind.class);
        for (var capability : registrations) {
            Objects.requireNonNull(capability, "activity capability");
            if (values.putIfAbsent(Objects.requireNonNull(capability.kind()), capability) != null)
                throw new IllegalArgumentException("duplicate actor activity capability");
        }
        if (!values.keySet().equals(required)) throw new IllegalArgumentException("missing or undeclared actor activity capability");
        capabilities = Map.copyOf(values);
    }
    public ActorActivityCapability require(ActorActivityKind kind) {
        var capability = capabilities.get(Objects.requireNonNull(kind));
        if (capability == null) throw new IllegalArgumentException("activity has no adopted capability: " + kind);
        return capability;
    }
    public void validateKinds(ActorExecutionState executions) {
        executions.currentKinds().forEach(this::require);
        for (var continuation : executions.suspended())
            if (!require(continuation.activityKind()).supportsContinuation())
                throw new IllegalArgumentException("activity does not support retained continuation");
    }
}
