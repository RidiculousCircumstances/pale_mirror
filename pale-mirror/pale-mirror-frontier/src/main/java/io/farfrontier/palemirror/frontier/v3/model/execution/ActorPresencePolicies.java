package io.farfrontier.palemirror.frontier.v3.model.execution;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.ActorKind;
import io.farfrontier.palemirror.frontier.v3.model.ActorLifeStatus;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Closed owner-supplied eligibility; actor type is declared, never discovered from a roster. */
public final class ActorPresencePolicies {
    @FunctionalInterface public interface Eligibility {
        boolean permits(FrontierWorldState state, SubjectId actorId);
    }
    public record Registration(ActorKind kind, Eligibility eligibility) {
        public Registration { Objects.requireNonNull(kind); Objects.requireNonNull(eligibility); }
    }
    private final Map<ActorKind, Eligibility> policies;
    public ActorPresencePolicies(Set<ActorKind> required, List<Registration> registrations) {
        Objects.requireNonNull(required); Objects.requireNonNull(registrations);
        var values = new EnumMap<ActorKind, Eligibility>(ActorKind.class);
        for (var registration : registrations) {
            Objects.requireNonNull(registration);
            if (values.putIfAbsent(registration.kind(), registration.eligibility()) != null)
                throw new IllegalArgumentException("duplicate actor presence policy");
        }
        if (!values.keySet().equals(required))
            throw new IllegalArgumentException("missing or undeclared actor presence policy");
        policies = Map.copyOf(values);
    }
    public void requireEligible(FrontierWorldState state, SubjectId actorId) {
        Objects.requireNonNull(state); Objects.requireNonNull(actorId);
        var actor = state.actorLocations().get(actorId);
        if (actor == null || actor.condition().status() != ActorLifeStatus.ALIVE)
            throw new IllegalArgumentException("presence requires its exact living declared actor");
        var policy = policies.get(actor.kind());
        if (policy == null || !policy.permits(state, actorId))
            throw new IllegalArgumentException("actor owner does not permit passive presence");
    }
}
