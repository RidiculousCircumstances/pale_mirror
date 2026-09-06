package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.BodyPosition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierSceneAdmission;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import net.minecraft.world.entity.Mob;

import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Bounded, noncanonical caches keyed by a runtime checkpoint. They are only an observation and
 * admission optimization: every value is discarded with its runtime and can never reconstruct
 * an actor, lease or canonical decision after restart.
 */
final class FrontierV3AmbientActorCaches {
    private static final Map<FrontierV3ServerRuntime<?, ?>, Map<SubjectId, Observed>> OBSERVATIONS = new IdentityHashMap<>();
    private static final Map<FrontierV3ServerRuntime<?, ?>, ReservationCache> RESERVATIONS = new IdentityHashMap<>();
    private static final Map<FrontierV3ServerRuntime<?, ?>, GenericAdmissionCache> GENERIC_ADMISSIONS = new IdentityHashMap<>();

    private FrontierV3AmbientActorCaches() { }

    static void rememberObserved(FrontierV3ServerRuntime<?, ?> runtime, SubjectId actorId, Mob body, int capacity) {
        Map<SubjectId, Observed> observations = OBSERVATIONS.computeIfAbsent(runtime, ignored -> new LinkedHashMap<>());
        if (observations.size() < capacity || observations.containsKey(actorId)) {
            observations.put(actorId, new Observed(new BodyPosition(body.getBlockX(), body.getBlockY(), body.getBlockZ()),
                    new FixedScalar(Math.round((double) body.getHealth() * FixedScalar.SCALE))));
        }
    }

    static Observed lastObserved(FrontierV3ServerRuntime<?, ?> runtime, SubjectId actorId) {
        Map<SubjectId, Observed> observations = OBSERVATIONS.get(runtime);
        return observations == null ? null : observations.get(actorId);
    }

    static void forgetObserved(FrontierV3ServerRuntime<?, ?> runtime, SubjectId actorId) {
        Map<SubjectId, Observed> observations = OBSERVATIONS.get(runtime);
        if (observations == null) return;
        observations.remove(actorId);
        if (observations.isEmpty()) OBSERVATIONS.remove(runtime);
    }

    static Set<SubjectId> reservedActors(FrontierV3ServerRuntime<?, ?> runtime, FrontierWorldState state) {
        ReservationCache cached = RESERVATIONS.get(runtime);
        if (cached != null && cached.state() == state) return cached.actors();
        Set<SubjectId> actors = FrontierSceneAdmission.reservedActors(state);
        RESERVATIONS.put(runtime, new ReservationCache(state, actors));
        return actors;
    }

    static FrontierSceneAdmission.GenericAmbientAdmission genericAmbientAdmission(FrontierV3ServerRuntime<?, ?> runtime,
                                                                                   FrontierWorldState state) {
        GenericAdmissionCache cached = GENERIC_ADMISSIONS.get(runtime);
        if (cached != null && cached.state() == state) return cached.admission();
        FrontierSceneAdmission.GenericAmbientAdmission admission = FrontierSceneAdmission.genericAmbientAdmission(state);
        GENERIC_ADMISSIONS.put(runtime, new GenericAdmissionCache(state, admission));
        return admission;
    }

    static void forget(FrontierV3ServerRuntime<?, ?> runtime) {
        OBSERVATIONS.remove(runtime);
        RESERVATIONS.remove(runtime);
        GENERIC_ADMISSIONS.remove(runtime);
    }

    record Observed(BodyPosition body, FixedScalar health) { }
    private record ReservationCache(FrontierWorldState state, Set<SubjectId> actors) { }
    private record GenericAdmissionCache(FrontierWorldState state, FrontierSceneAdmission.GenericAmbientAdmission admission) { }
}
