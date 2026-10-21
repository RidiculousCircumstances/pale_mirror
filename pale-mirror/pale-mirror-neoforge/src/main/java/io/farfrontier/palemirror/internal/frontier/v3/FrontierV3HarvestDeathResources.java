package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.*;
import java.nio.charset.StandardCharsets;

/** Field-owned aftermath; the common body protocol cannot retire an unresolved job. */
final class FrontierV3HarvestDeathResources {
    private FrontierV3HarvestDeathResources() { }
    static FrontierV3ActorDeathResourceComposition.AfterFatality prepare(ServerLevel level,
            FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, Mob body, ActorBodyId id) {
        var state = runtime.decodedState().orElseThrow(); var job = HarvestDeathResources.work(state, id.actorId()).orElse(null);
        if (job == null) return () -> { };
        var identity = new ActorActuationId(id, HarvestDeathResources.execution(state, id.actorId()).orElseThrow());
        var lease = state.sceneLeases().values().stream().filter(FrontierSceneBehaviors::isResourceSiteHarvest)
                .filter(value -> value.status() != SceneLeaseStatus.CLOSED
                        && FrontierSceneBehaviors.resourceSiteHarvest(value).siteId().equals(job.siteId())
                        && FrontierSceneBehaviors.resourceSiteHarvest(value).jobId().equals(job.id()))
                .reduce((left, right) -> { throw new IllegalArgumentException("dead field worker has competing scenes"); }).orElse(null);
        var effect = lease == null ? java.util.Optional.<FrontierV3OperationAftermath>empty()
                : FrontierV3FieldWorkOperation.deathEffect(level, runtime, state, lease, job, body, identity);
        var hand = body.getOffhandItem().copy();
        return FrontierV3ActorDeathResourceComposition.retained(effect.stream().toList(),
                () -> settle(level, runtime, body, id, hand, effect.stream().toList()));
    }
    static void recover(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
            Mob body, ActorBodyId id, FrontierV3BodyResourceSnapshot snapshot) {
        var hand = snapshot.slots(level).get(new ActorItemSlot.Hand(ActorContainerItemOrder.Hand.OFF));
        if (hand == null) throw new IllegalStateException("field death lost its pre-loot offhand");
        settle(level, runtime, body, id, hand, snapshot.effects());
    }
    private static void settle(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
            Mob body, ActorBodyId id, ItemStack before, List<FrontierV3OperationAftermath> effects) {
        var state = runtime.decodedState().orElseThrow(); ActorBodyAuthority.requireRetiredDeath(state, id);
        var job = HarvestDeathResources.work(state, id.actorId()).orElse(null);
        if (job == null || !HarvestDeathResources.ready(state, job)) return;
        var ledger = FrontierV3ResourceSiteLedger.get(level);
        for (var effect : effects) {
            if (!(effect.consequence() instanceof HarvestRetiredEffectObserved retired)
                    || !retired.identity().body().equals(id) || !retired.effect().jobId().equals(job.id()) || retired.applied()) continue;
            var operation = FrontierV3OperationLedger.get(level).find(effect.child().ticket()).orElse(null);
            if (operation != null && (!operation.cancelled() || !operation.ownerClosed())) return;
            if (operation == null && !FrontierV3OperationLedger.get(level).prunable(
                    effect.child().nativeReceipt(effect.child().declaration().participants().iterator().next()))) return;
            var owner = (FrontierV3ResourceSiteLedger.FieldOwnership) ledger.fieldClaim(job.siteId());
            var witness = owner.witness().cancelUnusedWork(retired);
            if (witness != owner.witness()) { ledger.replaceFieldClaim(owner, owner.withWitness(witness)); ledger.persist(level); }
        }
        // Pending delivery/projection are different native operations. Their unresolved
        // outcome must not turn into a second drop or disappear with this body.
        var delivery = ledger.fieldDelivery(job.siteId()); var projection = ledger.fieldHandProjection(job.siteId());
        if (delivery != null && delivery.jobId().equals(job.id()) || projection != null && projection.jobId().equals(job.id())) return;
        if (ledger.fieldClaim(job.siteId()) instanceof FrontierV3ResourceSiteLedger.FieldOwnership owner
                && owner.witness().cell(job.target().cellId()).pending().isPresent()) return;
        var identity = new ActorActuationId(id, HarvestDeathResources.execution(state, id.actorId()).orElseThrow());
        int quantity = ResourceSiteHarvestCargo.quantity(state, job);
        if (quantity == 0) {
            FrontierV3CommandSubmission.submit(runtime, "field-death-settled", job.id().value(),
                    receipt(job, identity, 0, Optional.empty()));
            return;
        }
        if (!ItemStack.matches(before, body.getOffhandItem()) || !ItemStack.matches(before, new ItemStack(Items.WHEAT, quantity))) return;
        var bindings = state.inventory().fungibleResources().bindings().values().stream()
                .filter(value -> value.accountId().equals(job.actorAccountId())).toList();
        if (bindings.size() != 1 || !bindings.getFirst().address().equals(new PhysicalStackAddress.ActorHand(id.actorId(), body.getUUID()))) return;
        var carrier = UUID.nameUUIDFromBytes((state.bootstrap().worldId().value() + ":field-death:" + job.id().value()
                + ":" + id.physicalEpoch()).getBytes(StandardCharsets.UTF_8));
        if (level.getEntity(carrier) != null || FrontierV3ResourceHandoffLedger.get(level).blocks(carrier)) return;
        var drop = new ItemEntity(level, body.getX(), body.getY() + 0.25D, body.getZ(), before.copy()); drop.setUUID(carrier);
        FrontierV3EntityResourceDropOperation.perform(level, runtime, body, new ActorItemSlot.Hand(ActorContainerItemOrder.Hand.OFF),
                before, drop, receipt(job, identity, bindings.getFirst().authorityEpoch(), Optional.of(FrontierV3EntityResourceDropOperation.ground(drop))));
    }
    private static HarvestDeathSettled receipt(ResourceSiteHarvestJob job, ActorActuationId identity,
            long epoch, Optional<ResourceCustody.Ground> ground) {
        return new HarvestDeathSettled(job.siteId(), identity, job.progress().completedCropSlots(),
                job.harvestedYieldQuantity(), job.deliveredYieldQuantity(), epoch, ground);
    }
    static boolean settled(FrontierWorldState state, ActorBodyId id) {
        return HarvestDeathResources.work(state, id.actorId()).isEmpty();
    }
}
