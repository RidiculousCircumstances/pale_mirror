package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Family-neutral physical-intent storage transition.
 *
 * <p>This class deliberately knows no intent kind, observation subtype, owner,
 * subject convention, or state collection.  A composed lifecycle capability
 * supplies the owner-local confirmation and recovery-unknown reductions.  It
 * only validates the durable record's status edge and stages typed intent and
 * observation storage for that reduction.</p>
 */
public final class PhysicalIntentTransitionStorage {
    private PhysicalIntentTransitionStorage() { }

    public static FrontierWorldState reduce(FrontierWorldState state, PhysicalIntent intent,
                                            PhysicalIntentTransition transition,
                                            Confirmed confirmed, RecoveryUnknown recoveryUnknown) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(transition, "physical transition");
        Objects.requireNonNull(confirmed, "owner confirmed reduction");
        Objects.requireNonNull(recoveryUnknown, "owner recovery-unknown reduction");
        PhysicalIntent current = state.physicalIntents().get(transition.intentId());
        if (current == null || !current.equals(intent)) {
            throw new IllegalArgumentException("physical transition has no exact prepared intent");
        }
        PhysicalIntentStatus status = transition.status();
        if (!allowed(current.status(), status)) {
            throw new IllegalArgumentException("physical intent transition is not allowed: " + current.id().value()
                    + " " + current.status() + "->" + status);
        }
        if (status != PhysicalIntentStatus.CONFIRMED) {
            if (transition.observation().isPresent()) {
                throw new IllegalArgumentException("non-confirmed physical intent transition cannot carry observation evidence");
            }
            Map<PhysicalIntentId, PhysicalIntent> next = new LinkedHashMap<>(state.physicalIntents());
            if (status == PhysicalIntentStatus.UNKNOWN_AFTER_RESTART) {
                next.put(current.id(), current.withRecoveryUnknown(transition.diagnostic().orElseThrow(() ->
                        new IllegalArgumentException("recovery-unknown transition requires the owner-supplied diagnostic tuple"))));
                return recoveryUnknown.reduce(state, current, Map.copyOf(next));
            }
            next.put(current.id(), current.withStatus(status, Optional.empty()));
            return record(state, next, state.physicalObservations());
        }
        PhysicalEffectObservation evidence = transition.observation().orElseThrow(
                () -> new IllegalArgumentException("confirmed physical intent requires observation evidence"));
        if (!current.id().equals(evidence.intentId()) || state.physicalObservations().containsKey(evidence.id())) {
            throw new IllegalArgumentException("physical observation does not match a unique confirmed intent");
        }
        Map<PhysicalIntentId, PhysicalIntent> next = new LinkedHashMap<>(state.physicalIntents());
        next.put(current.id(), current.withStatus(status, Optional.of(evidence.id())));
        return confirmed.reduce(state, current, evidence, Map.copyOf(next));
    }

    /** Owner-local reductions use this when confirmation or unknown has no additional domain effect. */
    public static FrontierWorldState record(FrontierWorldState state, Map<PhysicalIntentId, PhysicalIntent> intents,
                                            Map<PhysicalObservationId, PhysicalEffectObservation> observations) {
        return state.withChanges(FrontierWorldStateUpdate.begin().physicalIntents(intents).physicalObservations(observations));
    }

    public static FrontierWorldState recordConfirmed(FrontierWorldState state, PhysicalIntent intent,
                                                      PhysicalEffectObservation evidence,
                                                      Map<PhysicalIntentId, PhysicalIntent> intents) {
        Map<PhysicalObservationId, PhysicalEffectObservation> observations = new LinkedHashMap<>(state.physicalObservations());
        observations.put(evidence.id(), evidence);
        return record(state, intents, observations);
    }

    public static FrontierWorldState recordUnknown(FrontierWorldState state, PhysicalIntent intent,
                                                    Map<PhysicalIntentId, PhysicalIntent> intents) {
        return record(state, intents, state.physicalObservations());
    }

    private static boolean allowed(PhysicalIntentStatus current, PhysicalIntentStatus next) {
        return current == PhysicalIntentStatus.PREPARED && (next == PhysicalIntentStatus.RUNNING
                || next == PhysicalIntentStatus.UNKNOWN_AFTER_RESTART || next == PhysicalIntentStatus.CONFLICTED)
                || current == PhysicalIntentStatus.RUNNING && (next == PhysicalIntentStatus.CONFIRMED
                || next == PhysicalIntentStatus.UNKNOWN_AFTER_RESTART || next == PhysicalIntentStatus.CONFLICTED)
                || current == PhysicalIntentStatus.UNKNOWN_AFTER_RESTART && next == PhysicalIntentStatus.CONFIRMED;
    }

    @FunctionalInterface public interface Confirmed {
        FrontierWorldState reduce(FrontierWorldState state, PhysicalIntent intent, PhysicalEffectObservation evidence,
                                  Map<PhysicalIntentId, PhysicalIntent> intents);
    }

    @FunctionalInterface public interface RecoveryUnknown {
        FrontierWorldState reduce(FrontierWorldState state, PhysicalIntent intent, Map<PhysicalIntentId, PhysicalIntent> intents);
    }
}
