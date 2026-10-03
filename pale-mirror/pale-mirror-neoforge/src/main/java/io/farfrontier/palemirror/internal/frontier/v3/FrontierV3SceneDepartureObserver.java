package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

/** Exact chunk departure evidence only; no canonical mutation or authority transfer in callbacks. */
final class FrontierV3SceneDepartureObserver {
    private FrontierV3SceneDepartureObserver() { }

    static boolean observeLeave(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, Entity entity) {
        if (!(entity instanceof Mob body) || entity.getRemovalReason() != Entity.RemovalReason.UNLOADED_TO_CHUNK
                || body.getHealth() <= 0.0F) return false;
        var state = runtime.decodedState().orElse(null);
        if (state == null) return false;
        var binding = binding(state, entity);
        if (binding == null || !FrontierV3ActorCarrierComposition.ownsUnloading(entity, binding.live())) return false;
        var actor = state.actorLocations().get(binding.member().actorId());
        if (actor == null || actor.condition().status() != ActorLifeStatus.ALIVE) return false;
        var ambient = state.ambientLeases().get(binding.member().actorId());
        if (ambient != null && ambient.status() != AmbientLeaseStatus.CLOSED) return false;
        BodyPosition observedBody;
        if (FrontierSceneBehaviors.isResourceSiteHarvest(binding.lease())) {
            var supported = FrontierV3SupportedBodyCapture.observeDeparting(level, body);
            if (supported.isEmpty()) return false;
            observedBody = supported.orElseThrow();
        } else {
            observedBody = FrontierV3BodyObservation.position(body);
        }
        long revision = Math.max(1L, binding.lease().revision());
        var inactive = new FrontierV3ActorCarrierComposition.Declaration(binding.live().actorId(), binding.live().kind(),
                binding.live().owner(), binding.live().entityId(), FrontierV3ActorCarrierComposition.Representation.INACTIVE_CARRIER,
                revision, binding.live().epoch());
        java.util.Optional<FrontierV3SceneDeparture.HandStack> offhand = java.util.Optional.empty();
        if (FrontierSceneBehaviors.isResourceSiteHarvest(binding.lease())
                && FrontierSceneLeaseStateSupport.hasBoundActorHand(state, binding.lease())) {
            var held = body.getOffhandItem();
            if (!held.isEmpty() && net.minecraft.world.item.ItemStack.isSameItemSameComponents(held,
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.WHEAT, held.getCount()))) {
                offhand = java.util.Optional.of(new FrontierV3SceneDeparture.HandStack("minecraft:wheat", held.getCount()));
            }
        }
        java.util.Optional<FrontierV3SceneDeparture.HandStack> mainhand = java.util.Optional.empty();
        if (FrontierSceneBehaviors.isProductionWork(binding.lease())
                && FrontierSceneLeaseStateSupport.hasBoundActorHand(state, binding.lease())
                && FrontierV3BakeryHandProjection.matchesCurrent(state, binding.lease(), binding.member(), body)) {
            var held = body.getMainHandItem();
            if (!held.isEmpty()) mainhand = java.util.Optional.of(new FrontierV3SceneDeparture.HandStack(
                    net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(held.getItem()).toString(), held.getCount()));
        }
        var receipt = new FrontierV3SceneDeparture(new FrontierV3AmbientCarrierLedger.Carrier(inactive, revision,
                ambient == null ? 0L : ambient.revision()), binding.lease().id(), binding.lease().revision(),
                new SceneMemberPosition(binding.member().actorId(), observedBody,
                        new FixedScalar(Math.round((double) body.getHealth() * FixedScalar.SCALE))), actor.condition().health(), offhand, mainhand);
        return FrontierV3AmbientCarrierLedger.get(level, state.bootstrap().worldId()).recordDeparture(receipt);
    }

    static void observeJoin(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, Entity entity) {
        // A dead returned body belongs to the shared death observer, not survivor
        // resumption. Keep the departure witness until canonical death is accepted.
        if (!(entity instanceof Mob body) || body.getHealth() <= 0.0F) return;
        var state = runtime.decodedState().orElse(null);
        if (state == null) return;
        var binding = binding(state, entity);
        if (binding != null && FrontierV3ActorCarrierComposition.owns(entity, binding.live())) {
            var ledger = FrontierV3AmbientCarrierLedger.get(level, state.bootstrap().worldId());
            var departure = ledger.departure(binding.member().actorId()).orElse(null);
            if (departure != null && departure.mainhand().isPresent()) {
                var held = body.getMainHandItem();
                var expected = departure.mainhand().orElseThrow();
                if (held.isEmpty() || !expected.itemKind().equals(
                        net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(held.getItem()).toString())
                        || expected.quantity() != held.getCount()
                        || !net.minecraft.world.item.ItemStack.isSameItemSameComponents(held,
                            new net.minecraft.world.item.ItemStack(held.getItem(), held.getCount()))) return;
            }
            if (departure != null && departure.offhand().isPresent()) {
                var held = body.getOffhandItem();
                var expected = departure.offhand().orElseThrow();
                if (held.isEmpty() || !net.minecraft.world.item.ItemStack.isSameItemSameComponents(held,
                        new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.WHEAT, held.getCount()))
                        || !expected.itemKind().equals("minecraft:wheat") || expected.quantity() != held.getCount()) return;
            }
            BodyPosition observedBody;
            if (FrontierSceneBehaviors.isResourceSiteHarvest(binding.lease())) {
                var supported = FrontierV3SupportedBodyCapture.observe(level, body);
                if (supported.isEmpty()) return;
                observedBody = supported.orElseThrow();
            } else {
                observedBody = FrontierV3BodyObservation.position(body);
            }
            resumeReturned(state, binding.lease(), binding.member(), binding.live(),
                    new SceneMemberPosition(binding.member().actorId(), observedBody,
                            new FixedScalar(Math.round((double) body.getHealth() * FixedScalar.SCALE))), ledger);
            // A different returned snapshot remains conflicting evidence. Never erase it
            // merely because a body with the same UUID became visible again.
        }
    }

    /** The returned snapshot cannot erase evidence against a newer canonical baseline. */
    static boolean resumeReturned(FrontierWorldState state, SceneLease lease, SceneMember member,
                                   FrontierV3ActorCarrierComposition.Declaration live, SceneMemberPosition observed,
                                   FrontierV3AmbientCarrierLedger ledger) {
        return observedDeparture(state, lease, member, ledger)
                .filter(receipt -> live.representation() == FrontierV3ActorCarrierComposition.Representation.LIVE_BODY
                        && live.owner() == receipt.carrier().identity().owner()
                        && live.kind() == receipt.carrier().identity().kind()
                        && live.actorId().equals(receipt.carrier().identity().actorId())
                        && live.authorityRevision() == lease.revision()
                        && live.epoch() == receipt.carrier().identity().epoch()
                        && live.entityId().equals(receipt.carrier().identity().entityId())
                        && receipt.observed().equals(observed))
                .map(ledger::resumeDeparture).orElse(false);
    }

    /** Unresolved final-departure evidence prevents another physical writer, even while HOT. */
    static boolean permitsLiveWork(FrontierV3AmbientCarrierLedger ledger, SceneMember member) {
        return ledger.departure(member.actorId()).isEmpty();
    }

    static java.util.Optional<FrontierV3SceneDeparture> validDeparture(FrontierWorldState state, SceneLease lease,
                                                                      SceneMember member, FrontierV3AmbientCarrierLedger ledger) {
        return observedDeparture(state, lease, member, ledger).filter(ledger::savedDeparture);
    }

    /** Unload observation is not yet a release witness until entity storage confirms it. */
    static java.util.Optional<FrontierV3SceneDeparture> observedDeparture(FrontierWorldState state, SceneLease lease,
                                                                      SceneMember member, FrontierV3AmbientCarrierLedger ledger) {
        var actor = state.actorLocations().get(member.actorId());
        var ambient = state.ambientLeases().get(member.actorId());
        if (ledger.hasDepartureConflict(member.actorId()) || actor == null || actor.condition().status() != ActorLifeStatus.ALIVE
                || (ambient != null && ambient.status() != AmbientLeaseStatus.CLOSED)) return java.util.Optional.empty();
        return ledger.departure(member.actorId()).filter(receipt ->
                receipt.leaseId().equals(lease.id()) && receipt.sceneRevision() == lease.revision()
                && receipt.carrier().identity().entityId().equals(member.entityId())
                && receipt.canonicalHealthAtCapture().equals(actor.condition().health())
                && receipt.carrier().ambientRevision() == (ambient == null ? 0L : ambient.revision()))
                .filter(receipt -> declaredActorCurrent(state, receipt));
    }

    private static boolean declaredActorCurrent(FrontierWorldState state, FrontierV3SceneDeparture receipt) {
        var declaration = receipt.carrier().identity();
        try {
            return declaration.equals(FrontierV3ActorCarrierComposition.fromCanonical(state, declaration.actorId(),
                    declaration.kind(), declaration.owner(), declaration.entityId(), declaration.representation(),
                    declaration.authorityRevision(), declaration.epoch()));
        } catch (IllegalArgumentException foreignDeclaration) { return false; }
    }

    static boolean fenceDeparture(net.minecraft.server.level.ServerLevel level, FrontierWorldState state,
                                  SceneLease lease, SceneMember member, FrontierV3AmbientCarrierLedger ledger) {
        if (!fenceDeparture(state, lease, member, ledger)) return false;
        ledger.persist(level, state.bootstrap().worldId());
        return true;
    }

    static boolean fenceDeparture(FrontierWorldState state, SceneLease lease, SceneMember member,
                                  FrontierV3AmbientCarrierLedger ledger) {
        return validDeparture(state, lease, member, ledger).map(receipt -> {
            var carrier = receipt.carrier();
            return ledger.fence(carrier.identity(), carrier.physicalRevision(), carrier.ambientRevision());
        }).orElse(false);
    }

    private static Binding binding(FrontierWorldState state, Entity entity) {
        var tag = entity.getPersistentData();
        try {
            var leaseId = new SceneLeaseId(tag.getString(FrontierV3SceneExecutor.LEASE_KEY));
            var actorId = new SubjectId(tag.getString(FrontierV3SceneExecutor.ACTOR_KEY));
            SceneLease lease = state.sceneLeases().get(leaseId);
            if (lease == null || lease.status() == SceneLeaseStatus.CLOSED
                    || !tag.contains(FrontierV3SceneExecutor.REVISION_KEY)
                    || tag.getLong(FrontierV3SceneExecutor.REVISION_KEY) != lease.revision()) return null;
            SceneMember member = lease.members().stream().filter(value -> value.actorId().equals(actorId)).findFirst().orElse(null);
            if (member == null || !member.entityId().equals(entity.getUUID())) return null;
            var kind = FrontierV3ActorCarrierComposition.ActorKind.valueOf(tag.getString(FrontierV3ActorCarrierComposition.KIND_KEY));
            var live = FrontierV3ActorCarrierComposition.fromCanonical(state, actorId, kind,
                    FrontierV3ActorCarrierComposition.Owner.SCENE_LEASE, member.entityId(),
                    FrontierV3ActorCarrierComposition.Representation.LIVE_BODY, lease.revision(),
                    tag.getLong(FrontierV3ActorCarrierComposition.EPOCH_KEY));
            return new Binding(lease, member, live);
        } catch (IllegalArgumentException invalidPhysicalDeclaration) {
            return null;
        }
    }

    private record Binding(SceneLease lease, SceneMember member, FrontierV3ActorCarrierComposition.Declaration live) { }
}
