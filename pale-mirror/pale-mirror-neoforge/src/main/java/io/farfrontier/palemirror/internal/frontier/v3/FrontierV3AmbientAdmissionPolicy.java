package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.ActorLifeStatus;
import io.farfrontier.palemirror.frontier.v3.model.AmbientActorLease;
import io.farfrontier.palemirror.frontier.v3.model.AmbientLeaseStatus;
import io.farfrontier.palemirror.frontier.v3.model.FrontierSceneAdmission;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.HivePhysiologySupport;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/** One bounded, disposable admission pass for the ambient-to-scene hand-off. */
final class FrontierV3AmbientAdmissionPolicy {
    static final int MAX_ACTORS_PER_TICK = 16;

    private FrontierV3AmbientAdmissionPolicy() { }

    static Session begin(FrontierWorldState initialState) {
        return begin(initialState, FrontierSceneAdmission::reservationAdmission);
    }

    static Session begin(FrontierWorldState initialState, AdmissionDeriver deriver) {
        return new Session(initialState, deriver);
    }

    @FunctionalInterface
    interface AdmissionDeriver {
        FrontierSceneAdmission.ReservationAdmission derive(FrontierWorldState state);
    }

    /** The only substituted boundary in the ordinary-JVM policy test. */
    @FunctionalInterface
    interface EffectPort {
        Optional<EffectResult> execute(Selection selection);
    }

    enum Effect { DRAIN_HOT, ABANDON_PREPARED }

    record Selection(SubjectId actorId, FrontierWorldState state, Effect effect) {
        Selection {
            Objects.requireNonNull(actorId, "actor id");
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(effect, "effect");
        }
    }

    /**
     * The canonical transition checkpoint and released result returned by the physical port.
     * A port is not allowed to turn an apparent body-side success into admission authority by
     * returning an arbitrary replacement snapshot.
     */
    record EffectResult(FrontierWorldState drainingState, FrontierWorldState resultingState) {
        EffectResult {
            Objects.requireNonNull(drainingState, "draining state");
            Objects.requireNonNull(resultingState, "resulting state");
        }
    }

    record Decision(SubjectId actorId, FrontierWorldState state, FrontierSceneAdmission.ReservationAdmission admission,
                    boolean reserved, Optional<Effect> selectedEffect, Optional<FrontierWorldState> resultingState) {
        Decision {
            Objects.requireNonNull(actorId, "actor id");
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(admission, "admission");
            selectedEffect = Objects.requireNonNull(selectedEffect, "selected effect");
            resultingState = Objects.requireNonNull(resultingState, "resulting state");
        }

        boolean applied() { return resultingState.isPresent(); }
    }

    static final class Session {
        private final AdmissionDeriver deriver;
        private FrontierWorldState source;
        private FrontierSceneAdmission.ReservationAdmission admission;

        private Session(FrontierWorldState initialState, AdmissionDeriver deriver) {
            this.deriver = Objects.requireNonNull(deriver, "admission deriver");
            refresh(Objects.requireNonNull(initialState, "initial state"));
        }

        FrontierSceneAdmission.ReservationAdmission admissionFor(FrontierWorldState state) {
            if (source != Objects.requireNonNull(state, "state")) refresh(state);
            return admission;
        }

        boolean reserves(FrontierWorldState state, SubjectId actorId) {
            return admissionFor(state).reserves(actorId);
        }

        List<Decision> scan(Collection<SubjectId> actorIds, Supplier<FrontierWorldState> currentState, EffectPort effects) {
            Objects.requireNonNull(actorIds, "actor ids");
            Objects.requireNonNull(currentState, "current state");
            Objects.requireNonNull(effects, "effects");
            List<Decision> decisions = new ArrayList<>();
            int applied = 0;
            FrontierWorldState carriedState = null;
            for (SubjectId actorId : actorIds.stream().sorted(Comparator.naturalOrder()).toList()) {
                if (applied >= MAX_ACTORS_PER_TICK) return List.copyOf(decisions);
                FrontierWorldState state = carriedState != null ? carriedState : currentState.get();
                if (state == null) return List.copyOf(decisions);
                Decision decision = decide(actorId, state, effects);
                decisions.add(decision);
                if (decision.applied()) applied++;
                carriedState = decision.resultingState().orElse(null);
            }
            return List.copyOf(decisions);
        }

        Decision decide(SubjectId actorId, FrontierWorldState state, EffectPort effects) {
            Objects.requireNonNull(actorId, "actor id");
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(effects, "effects");
            FrontierSceneAdmission.ReservationAdmission currentAdmission = admissionFor(state);
            boolean reserved = currentAdmission.reserves(actorId);
            Optional<Effect> effect = selectedEffect(actorId, state, reserved);
            Optional<FrontierWorldState> resultingState = effect.flatMap(value -> effects.execute(new Selection(actorId, state, value)))
                    .filter(result -> hasCanonicalPostcondition(new Selection(actorId, state, effect.orElseThrow()), result))
                    .map(EffectResult::resultingState);
            return new Decision(actorId, state, currentAdmission, reserved, effect, resultingState);
        }

        private static boolean hasCanonicalPostcondition(Selection selection, EffectResult result) {
            AmbientActorLease initialLease = selection.state().ambientLeases().get(selection.actorId());
            var initialActor = selection.state().actorLocations().get(selection.actorId());
            AmbientActorLease drainingLease = result.drainingState().ambientLeases().get(selection.actorId());
            var drainingActor = result.drainingState().actorLocations().get(selection.actorId());
            AmbientActorLease releasedLease = result.resultingState().ambientLeases().get(selection.actorId());
            var releasedActor = result.resultingState().actorLocations().get(selection.actorId());
            if (initialLease == null || initialActor == null || drainingLease == null || drainingActor == null
                    || releasedLease == null || releasedActor == null
                    || drainingLease.status() != AmbientLeaseStatus.DRAINING || releasedLease.status() != AmbientLeaseStatus.CLOSED
                    || !initialLease.actorId().equals(selection.actorId()) || !drainingLease.actorId().equals(selection.actorId())
                    || !releasedLease.actorId().equals(selection.actorId())) return false;
            return switch (selection.effect()) {
                case ABANDON_PREPARED -> initialLease.status() == AmbientLeaseStatus.PREPARED
                        && drainingActor.equals(initialActor)
                        && releasedActor.body().equals(initialLease.handoffBody())
                        && releasedActor.condition().equals(initialActor.condition());
                case DRAIN_HOT -> initialLease.status() == AmbientLeaseStatus.HOT
                        && releasedActor.condition().status() == ActorLifeStatus.ALIVE;
            };
        }

        private static Optional<Effect> selectedEffect(SubjectId actorId, FrontierWorldState state, boolean reserved) {
            if (!reserved) return Optional.empty();
            var location = state.actorLocations().get(actorId);
            if (location == null || location.condition().status() != ActorLifeStatus.ALIVE || !HivePhysiologySupport.permitsAmbientLease(state, actorId)) {
                return Optional.empty();
            }
            AmbientActorLease lease = state.ambientLeases().get(actorId);
            if (lease == null || lease.status() == AmbientLeaseStatus.CLOSED) return Optional.empty();
            return switch (lease.status()) {
                case HOT -> Optional.of(Effect.DRAIN_HOT);
                case PREPARED -> Optional.of(Effect.ABANDON_PREPARED);
                default -> Optional.empty();
            };
        }

        private void refresh(FrontierWorldState state) {
            source = state;
            admission = Objects.requireNonNull(deriver.derive(state), "reservation admission");
        }
    }
}
