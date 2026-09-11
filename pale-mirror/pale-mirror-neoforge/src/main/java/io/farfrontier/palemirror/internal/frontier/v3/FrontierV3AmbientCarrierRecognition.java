package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.ActorLifeStatus;
import io.farfrontier.palemirror.frontier.v3.model.AmbientLeaseStatus;
import io.farfrontier.palemirror.frontier.v3.model.FrontierSceneAdmission;
import io.farfrontier.palemirror.frontier.v3.model.FrontierSettlementAssaultBattlefield;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Zombie;

import java.util.Objects;
import java.util.UUID;

/** Strict read-only recognition for an exact ambient carrier at the Entity-join boundary. */
final class FrontierV3AmbientCarrierRecognition {
    private FrontierV3AmbientCarrierRecognition() { }

    static boolean recognizes(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, Entity entity) {
        return recognizes(runtime, ManagedCarrier.from(entity));
    }

    static boolean recognizes(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, ManagedCarrier carrier) {
        return recognizes(runtime, carrier, current -> FrontierV3GrayboxExecutor.admissionProvider(runtime, current));
    }

    static boolean recognizes(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, ManagedCarrier carrier,
                              FrontierSceneAdmission.ProviderSource providerSource) {
        Objects.requireNonNull(runtime, "runtime"); Objects.requireNonNull(carrier, "carrier"); Objects.requireNonNull(providerSource, "provider source");
        FrontierWorldState state = runtime.decodedState().orElse(null);
        return state != null && recognizes(state, carrier, providerSource);
    }

    static boolean recognizes(FrontierWorldState state, ManagedCarrier carrier, FrontierSceneAdmission.ProviderSource providerSource) {
        Objects.requireNonNull(state, "state"); Objects.requireNonNull(carrier, "carrier"); Objects.requireNonNull(providerSource, "provider source");
        SubjectId actorId = actorId(carrier);
        if (actorId == null || !recognizesOwnership(state, carrier, actorId)) return false;
        var provider = providerSource.provider(state);
        if (provider.isEmpty()) return false;
        return !FrontierSceneAdmission.reserved(state, actorId, ignored -> provider);
    }

    /**
     * Exact non-geometric ownership proof used solely to retain a restored Entity until the
     * projection owner has published its first compatible provider.  It grants no ambient
     * execution or scene-admission authority; those remain behind {@link #recognizes}.
     */
    static boolean recognizesOwnership(FrontierWorldState state, ManagedCarrier carrier) {
        Objects.requireNonNull(state, "state"); Objects.requireNonNull(carrier, "carrier");
        SubjectId actorId = actorId(carrier);
        return actorId != null && recognizesOwnership(state, carrier, actorId);
    }

    private static boolean recognizesOwnership(FrontierWorldState state, ManagedCarrier carrier, SubjectId actorId) {
        var location = state.actorLocations().get(actorId); var lease = state.ambientLeases().get(actorId);
        return location != null && location.condition().status() == ActorLifeStatus.ALIVE
                && lease != null && lease.status() != AmbientLeaseStatus.CLOSED
                && FrontierV3AmbientActorExecutor.entityId(state, actorId).equals(carrier.entityId())
                && carrier.ownedBy(actorId, FrontierV3AmbientActorExecutor.bioform(state, actorId));
    }

    private static SubjectId actorId(ManagedCarrier carrier) {
        try { return new SubjectId(carrier.actorId()); } catch (IllegalArgumentException invalid) { return null; }
    }

    /** Exact read-only facts adapted from a joining Minecraft entity. */
    record ManagedCarrier(UUID entityId, String actorId, boolean removed, boolean bioform, String kind) {
        ManagedCarrier {
            Objects.requireNonNull(entityId, "entity id"); Objects.requireNonNull(actorId, "actor id"); Objects.requireNonNull(kind, "kind");
        }
        static ManagedCarrier from(Entity entity) {
            Objects.requireNonNull(entity, "entity");
            return new ManagedCarrier(entity.getUUID(), entity.getPersistentData().getString(FrontierV3AmbientActorExecutor.ACTOR_KEY), entity.isRemoved(),
                    entity instanceof Zombie, entity.getPersistentData().getString(FrontierV3AmbientActorExecutor.KIND_KEY));
        }
        boolean ownedBy(SubjectId expectedActor, boolean expectedBioform) {
            return !removed && expectedActor.value().equals(actorId) && bioform == expectedBioform
                    && (expectedBioform ? "BIOFORM" : "RESIDENT").equals(kind);
        }
    }
}
