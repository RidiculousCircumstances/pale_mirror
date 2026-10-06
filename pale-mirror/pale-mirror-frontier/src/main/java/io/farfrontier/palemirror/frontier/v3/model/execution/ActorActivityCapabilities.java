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
            Objects.requireNonNull(capability.bodyCheckpoint(), "declared activity body checkpoint port");
            var death = Objects.requireNonNull(capability.deathAcknowledgement(), "declared activity death port");
            if (capability.supportsContinuation() && death.isEmpty())
                throw new IllegalArgumentException("retained work requires its declared death acknowledgement port");
            if (capability.supportsContinuation()) Objects.requireNonNull(capability.resumptionReference(),
                    "retained work requires its declared resumption-reference policy");
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
    /** All retained owners acknowledge projection closure independently of interruption. */
    public void validateAmbientRelease(io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState state,
                                       io.farfrontier.palemirror.frontier.v3.api.SubjectId actor) {
        var retained = state.actorExecutions().actors().get(Objects.requireNonNull(actor));
        if (retained == null) return;
        var claims = new java.util.ArrayList<ActorExecutionId>(2);
        retained.current().ifPresent(claims::add);
        retained.suspended().ifPresent(claims::add);
        for (var id : claims) {
            var capability = require(id.activityKind());
            capability.validateReference(state, id);
            capability.validateAmbientRelease(state, id);
        }
    }
    public void requireAmbientMotion(io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState state,
            ActorExecutionId execution, io.farfrontier.palemirror.frontier.v3.model.AmbientActorLease lease) {
        Objects.requireNonNull(state); Objects.requireNonNull(execution); Objects.requireNonNull(lease);
        state.actorExecutions().requireCurrent(execution);
        if (!execution.actorId().equals(lease.actorId())
                || lease.status() != io.farfrontier.palemirror.frontier.v3.model.AmbientLeaseStatus.HOT
                || !lease.equals(state.ambientLeases().get(execution.actorId())))
            throw new IllegalArgumentException("ambient motion requires its exact current presentation record");
        var capability = require(execution.activityKind());
        capability.validateReference(state, execution);
        if (!capability.permitsAmbientMotion(state, execution, lease))
            throw new IllegalArgumentException("activity owner does not authorize this ambient goal");
    }
}
