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
        var state = runtime.decodedState().orElse(null);
        if (state == null || !(entity.level() instanceof net.minecraft.server.level.ServerLevel level)) return false;
        var carrier = ManagedCarrier.from(entity);
        return recoverableOwnership(state, carrier, FrontierV3AmbientCarrierLedger.get(level, state.bootstrap().worldId()))
                && recognizes(runtime, carrier);
    }

    static boolean recoverableOwnership(FrontierWorldState state, ManagedCarrier carrier,
                                         FrontierV3AmbientCarrierLedger ledger) {
        if (!recognizesOwnership(state, carrier)) return false;
        var actor = actorId(carrier);
        if (ledger.hasCarrier(actor) || ledger.hasDepartureConflict(actor)
                || ledger.departure(actor).isPresent() || ledger.ambientDeparture(actor).isPresent()) return false;
        var handoff = ledger.pendingHandoff(actor);
        if (handoff.isPresent()) return carrier.matches(handoff.orElseThrow().current());
        var first = ledger.firstAdmission(actor).orElse(null);
        if (first != null && (first.phase() == FrontierV3ActorFirstAdmission.Phase.NEVER_CREATED
                || first.phase() == FrontierV3ActorFirstAdmission.Phase.PROVEN_ABSENT
                || first.phase() == FrontierV3ActorFirstAdmission.Phase.PENDING
                    && !carrier.matches(first.attempt().orElseThrow().declaration()))) return false;
        return ledger.pendingAdoption(actor).map(value -> carrier.matches(value.admitted())).orElse(true);
    }

    /** Retiring a stale saved body requires its completed canonical owner AND exact retained fence. */
    static boolean retainedClosedRelease(FrontierWorldState state, ManagedCarrier carrier,
                                         FrontierV3AmbientCarrierLedger ledger) {
        var actor = actorId(carrier);
        if (actor == null || carrier.epoch() < 1L) return false;
        var lease = state.ambientLeases().get(actor);
        var location = state.actorLocations().get(actor);
        if (lease == null || lease.status() != AmbientLeaseStatus.CLOSED || location == null
                || location.condition().status() != ActorLifeStatus.ALIVE
                || ledger.hasDepartureConflict(actor) || ledger.pendingAdoption(actor).isPresent()
                || state.sceneLeases().values().stream().anyMatch(scene -> scene.status()
                    != io.farfrontier.palemirror.frontier.v3.model.SceneLeaseStatus.CLOSED
                    && scene.members().stream().anyMatch(member -> member.actorId().equals(actor)))) return false;
        var expected = FrontierV3AmbientActorExecutor.carrierDeclaration(state, actor,
                FrontierV3ActorCarrierComposition.Owner.AMBIENT_LEASE,
                FrontierV3AmbientActorExecutor.entityId(state, actor),
                FrontierV3ActorCarrierComposition.Representation.LIVE_BODY, lease.revision(), carrier.epoch());
        return carrier.ownedBy(actor, FrontierV3AmbientActorExecutor.bioform(state, actor)) && carrier.matches(expected)
                && ledger.matchesCarrier(expected.inactiveCarrier(), lease.revision(), lease.revision());
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
                && FrontierV3ActorCarrierComposition.Owner.AMBIENT_LEASE.name().equals(carrier.owner())
                && FrontierV3ActorCarrierComposition.Representation.LIVE_BODY.name().equals(carrier.representation())
                && carrier.authorityRevision() == lease.revision() && carrier.epoch() >= 1L
                && carrier.ownedBy(actorId, FrontierV3AmbientActorExecutor.bioform(state, actorId));
    }

    private static SubjectId actorId(ManagedCarrier carrier) {
        try { return new SubjectId(carrier.actorId()); } catch (IllegalArgumentException invalid) { return null; }
    }

    /** Exact read-only facts adapted from a joining Minecraft entity. */
    record ManagedCarrier(UUID entityId, String actorId, boolean removed, boolean bioform, String kind,
                          String owner, String representation, long authorityRevision, long epoch) {
        ManagedCarrier {
            Objects.requireNonNull(entityId, "entity id"); Objects.requireNonNull(actorId, "actor id"); Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(owner, "owner tag"); Objects.requireNonNull(representation, "representation tag");
        }
        static ManagedCarrier from(Entity entity) {
            Objects.requireNonNull(entity, "entity");
            return new ManagedCarrier(entity.getUUID(), entity.getPersistentData().getString(FrontierV3AmbientActorExecutor.ACTOR_KEY), entity.isRemoved(),
                    entity instanceof Zombie, entity.getPersistentData().getString(FrontierV3AmbientActorExecutor.KIND_KEY),
                    entity.getPersistentData().getString(FrontierV3ActorCarrierComposition.OWNER_KEY),
                    entity.getPersistentData().getString(FrontierV3ActorCarrierComposition.REPRESENTATION_KEY),
                    exactLong(entity.getPersistentData(), FrontierV3ActorCarrierComposition.REVISION_KEY),
                    exactLong(entity.getPersistentData(), FrontierV3ActorCarrierComposition.EPOCH_KEY));
        }
        private static long exactLong(net.minecraft.nbt.CompoundTag tag, String key) {
            // Zero is an invalid raw observation, never a synthesized authority.
            return tag.contains(key, net.minecraft.nbt.Tag.TAG_LONG) ? tag.getLong(key) : 0L;
        }
        boolean matches(FrontierV3ActorCarrierComposition.Declaration declaration) {
            return !removed && entityId.equals(declaration.entityId()) && actorId.equals(declaration.actorId().value())
                    && kind.equals(declaration.kind().name()) && owner.equals(declaration.owner().name())
                    && representation.equals(declaration.representation().name())
                    && authorityRevision == declaration.authorityRevision() && epoch == declaration.epoch();
        }
        boolean ownedBy(SubjectId expectedActor, boolean expectedBioform) {
            return !removed && expectedActor.value().equals(actorId) && bioform == expectedBioform
                    && (expectedBioform ? "BIOFORM" : "RESIDENT").equals(kind);
        }
    }
}
