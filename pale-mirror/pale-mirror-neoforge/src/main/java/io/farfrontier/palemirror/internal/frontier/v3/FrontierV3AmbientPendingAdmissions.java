package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.AmbientLeaseStatus;
import io.farfrontier.palemirror.frontier.v3.model.AmbientLeaseTransition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import net.minecraft.world.entity.Entity;

import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Sole temporary authority for an exact managed entity joining before the UUID index and a
 * compatible projection are both ready. It is noncanonical, bounded, and releases only after
 * strict provider-backed recognition or ordinary entity removal.
 */
final class FrontierV3AmbientPendingAdmissions {
    static final int MAX_ENTRIES = 4_096;
    private static final Map<FrontierV3ServerRuntime<?, ?>, Map<UUID, Entity>> ADMISSIONS = new IdentityHashMap<>();

    private FrontierV3AmbientPendingAdmissions() { }

    static Entity get(FrontierV3ServerRuntime<?, ?> runtime, UUID entityId) {
        Map<UUID, Entity> admissions = ADMISSIONS.get(runtime);
        return admissions == null ? null : admissions.get(entityId);
    }

    static RetainResult retain(FrontierV3ServerRuntime<?, ?> runtime, Entity entity, SubjectId actorId) {
        Map<UUID, Entity> admissions = ADMISSIONS.computeIfAbsent(runtime, ignored -> new LinkedHashMap<>());
        if (admissions.size() >= MAX_ENTRIES && !admissions.containsKey(entity.getUUID())) return RetainResult.LIMIT_REACHED;
        Entity present = admissions.get(entity.getUUID());
        if (present != null && present != entity && !present.isRemoved()) {
            PaleMirrorMod.LOGGER.warn("Frontier v3 rejects a duplicate unindexed managed body uuid={} for actor={}",
                    entity.getUUID(), actorId.value());
            return RetainResult.DUPLICATE_UNINDEXED;
        }
        admissions.put(entity.getUUID(), entity);
        return RetainResult.RETAINED;
    }

    static void clean(FrontierV3ServerRuntime<?, ?> runtime) {
        Map<UUID, Entity> admissions = ADMISSIONS.get(runtime);
        if (admissions == null) return;
        // UUID indexing is not a hand-off: strict projection recognition remains mandatory.
        admissions.entrySet().removeIf(entry -> entry.getValue().isRemoved());
        if (admissions.isEmpty()) ADMISSIONS.remove(runtime);
    }

    /** Completes only strict post-projection restart recovery; stale snapshots remain inert. */
    static void reclaimProjected(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state) {
        Map<UUID, Entity> admissions = ADMISSIONS.get(runtime);
        if (admissions == null) return;
        for (Map.Entry<UUID, Entity> entry : List.copyOf(admissions.entrySet())) {
            Entity entity = entry.getValue();
            if (entity.isRemoved() || !FrontierV3AmbientCarrierRecognition.recognizes(runtime, entity)) continue;
            SubjectId actorId;
            try {
                actorId = new SubjectId(entity.getPersistentData().getString(FrontierV3AmbientActorExecutor.ACTOR_KEY));
            } catch (IllegalArgumentException invalid) {
                continue;
            }
            if (state.ambientLeases().get(actorId) != null
                    && state.ambientLeases().get(actorId).status() == AmbientLeaseStatus.UNKNOWN_AFTER_RESTART) {
                if (!(FrontierV3AmbientActorExecutor.submit(runtime, "ambient-recovered", actorId.value(),
                        new AmbientLeaseTransition(actorId, AmbientLeaseStatus.HOT))
                        instanceof io.farfrontier.palemirror.frontier.v3.api.CommandResult.Accepted)) continue;
                state = runtime.decodedState().orElse(null);
                if (state == null) return;
                if (state.ambientLeases().get(actorId) == null
                        || state.ambientLeases().get(actorId).status() != AmbientLeaseStatus.HOT) continue;
            }
            // Strict recognition plus accepted recovery is the sole hand-off from this bridge.
            admissions.remove(entry.getKey(), entity);
        }
        if (admissions.isEmpty()) ADMISSIONS.remove(runtime);
    }

    static void forget(FrontierV3ServerRuntime<?, ?> runtime) {
        ADMISSIONS.remove(runtime);
    }

    enum RetainResult { RETAINED, DUPLICATE_UNINDEXED, LIMIT_REACHED }
}
