package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.FrontierSceneAdmission;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;

import java.util.Collection;
import java.util.Comparator;
import java.util.Objects;
import java.util.function.Supplier;

/** One bounded, disposable admission pass for the ambient-to-scene hand-off. */
final class FrontierV3AmbientAdmissionPolicy {
    static final int MAX_ACTORS_PER_TICK = 16;

    private FrontierV3AmbientAdmissionPolicy() { }

    static Session begin(FrontierWorldState initialState) {
        return begin(initialState, FrontierSceneAdmission::reservationAdmission);
    }

    static Session begin(FrontierWorldState initialState, AdmissionCompiler compiler) {
        return new Session(initialState, compiler);
    }

    @FunctionalInterface
    interface AdmissionCompiler {
        FrontierSceneAdmission.ReservationAdmission compile(FrontierWorldState state);
    }

    @FunctionalInterface
    interface ActorDecision {
        boolean decide(SubjectId actorId, FrontierWorldState state, FrontierSceneAdmission.ReservationAdmission admission);
    }

    static final class Session {
        private final AdmissionCompiler compiler;
        private FrontierWorldState source;
        private FrontierSceneAdmission.ReservationAdmission admission;

        private Session(FrontierWorldState initialState, AdmissionCompiler compiler) {
            this.compiler = Objects.requireNonNull(compiler, "admission compiler");
            refresh(Objects.requireNonNull(initialState, "initial state"));
        }

        FrontierSceneAdmission.ReservationAdmission admissionFor(FrontierWorldState state) {
            if (source != Objects.requireNonNull(state, "state")) refresh(state);
            return admission;
        }

        boolean reserves(FrontierWorldState state, SubjectId actorId) {
            return admissionFor(state).reserves(actorId);
        }

        int scan(Collection<SubjectId> actorIds, Supplier<FrontierWorldState> currentState, ActorDecision decision) {
            Objects.requireNonNull(actorIds, "actor ids");
            Objects.requireNonNull(currentState, "current state");
            Objects.requireNonNull(decision, "actor decision");
            int applied = 0;
            for (SubjectId actorId : actorIds.stream().sorted(Comparator.naturalOrder()).toList()) {
                if (applied >= MAX_ACTORS_PER_TICK) return applied;
                FrontierWorldState state = currentState.get();
                if (state == null) return applied;
                if (decision.decide(actorId, state, admissionFor(state))) applied++;
            }
            return applied;
        }

        private void refresh(FrontierWorldState state) {
            source = state;
            admission = Objects.requireNonNull(compiler.compile(state), "reservation admission");
        }
    }
}
