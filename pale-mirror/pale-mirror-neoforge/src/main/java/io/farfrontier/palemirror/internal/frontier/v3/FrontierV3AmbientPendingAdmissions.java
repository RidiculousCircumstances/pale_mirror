package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import net.minecraft.world.entity.Entity;

import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Sole temporary authority for an exact managed entity joining before the UUID index and a
 * durable common body confirmation are both ready. It is noncanonical and bounded;
 * it neither restores a presentation scope nor grants activity execution.
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
        // Indexing alone is not confirmation of this physical incarnation.
        admissions.entrySet().removeIf(entry -> entry.getValue().isRemoved());
        if (admissions.isEmpty()) ADMISSIONS.remove(runtime);
    }

    /** A canceled join inserted nothing; withdraw only this exact temporary object. */
    static boolean rejectJoin(FrontierV3ServerRuntime<?, ?> runtime, Entity entity) {
        Map<UUID, Entity> admissions = ADMISSIONS.get(runtime);
        if (admissions == null || !admissions.remove(entity.getUUID(), entity)) return false;
        if (admissions.isEmpty()) ADMISSIONS.remove(runtime);
        return true;
    }

    /** Completes only common indexed body admission; activity recovery belongs to its owner. */
    static void reclaimProjected(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state) {
        Map<UUID, Entity> admissions = ADMISSIONS.get(runtime);
        if (admissions == null) return;
        for (Map.Entry<UUID, Entity> entry : List.copyOf(admissions.entrySet())) {
            Entity entity = entry.getValue();
            // The indexed body has one physical binding irrespective of its next
            // activity. Releasing this join bridge grants no execution authority.
            if (!entity.isRemoved() && entity.level() instanceof net.minecraft.server.level.ServerLevel level
                    && level.getEntity(entry.getKey()) == entity) {
                FrontierV3ActorBodyController.observeJoin(level, runtime, entity);
                FrontierV3ActorBodyController.confirmPresent(level, runtime, entity);
                state = runtime.decodedState().orElse(null);
                if (state == null || runtime.status().kind() != FrontierV3RuntimeStatus.Kind.ACTIVE) return;
                var binding = FrontierV3ActorOwnerBinding.from(entity);
                var ledger = FrontierV3AmbientCarrierLedger.get(level, state.bootstrap().worldId());
                if (binding.isPresent() && recordedBodyOwns(state, binding.orElseThrow(), ledger)
                        && FrontierV3ActorBodyController.readyForExecution(level, state, List.of(
                            new io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId(
                                binding.orElseThrow().declaration().actorId(), binding.orElseThrow().declaration().epoch())))) {
                    admissions.remove(entry.getKey(), entity);
                }
            }
        }
        if (admissions.isEmpty()) ADMISSIONS.remove(runtime);
    }

    /** Only current physical identity and retained insertion history end the join bridge. */
    static boolean recordedBodyOwns(FrontierWorldState state, FrontierV3ActorOwnerBinding binding,
                                     FrontierV3AmbientCarrierLedger ledger) {
        var declaration = binding.declaration();
        return FrontierV3ActorBodyController.recognizesDeclaration(state, declaration)
                && io.farfrontier.palemirror.frontier.v3.model.ActorBodyAuthority.require(state,
                    io.farfrontier.palemirror.frontier.v3.model.ActorBodyAuthority.current(state, declaration.actorId())).phase()
                    == io.farfrontier.palemirror.frontier.v3.model.FencedRecoveryPhase.RUNNING
                && !ledger.hasCarrier(declaration.actorId()) && !ledger.hasDepartureConflict(declaration.actorId())
                && !ledger.hasBodyDeparture(declaration.actorId())
                && ledger.permitsRecordedOwner(binding);
    }

    static void forget(FrontierV3ServerRuntime<?, ?> runtime) {
        ADMISSIONS.remove(runtime);
    }

    enum RetainResult { RETAINED, DUPLICATE_UNINDEXED, LIMIT_REACHED }
}
