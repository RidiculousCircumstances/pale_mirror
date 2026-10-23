package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import java.util.Optional;
import java.util.UUID;
import java.nio.charset.StandardCharsets;

/** Bakery retains physical outcomes and batch ownership across the common fatality boundary. */
final class FrontierV3StationWorkDeathResources {
    private FrontierV3StationWorkDeathResources() { }
    static FrontierV3ActorDeathResourceComposition.AfterFatality prepare(ServerLevel level,
            FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, Mob body, ActorBodyId id) {
        var state = runtime.decodedState().orElseThrow(); var job = StationWorkDeathResources.work(state, id.actorId()).orElse(null);
        if (job == null) return () -> { };
        if (!(body instanceof Villager worker)) throw new IllegalArgumentException("declared baker has no supported native resident body");
        var identity = new ActorActuationId(id, StationWorkDeathResources.execution(state, id.actorId()).orElseThrow());
        var effect = FrontierV3StationWorkPhysicalEffect.deathEffect(level, state, job, worker, identity);
        var hand = worker.getMainHandItem().copy();
        return FrontierV3ActorDeathResourceComposition.retained(effect.stream().toList(), () -> settle(level, runtime, body, id, hand));
    }
    static void recover(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
            Mob body, ActorBodyId id, FrontierV3BodyResourceSnapshot snapshot) {
        var hand = snapshot.slots(level).get(new ActorItemSlot.Hand(ActorContainerItemOrder.Hand.MAIN));
        if (hand == null) throw new IllegalStateException("bakery death lost its pre-loot hand capability");
        settle(level, runtime, body, id, hand);
    }
    private static void settle(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
            Mob body, ActorBodyId id, ItemStack before) {
        var state = runtime.decodedState().orElseThrow(); ActorBodyAuthority.requireRetiredDeath(state, id);
        var job = StationWorkDeathResources.work(state, id.actorId()).orElse(null);
        if (job == null || !StationWorkDeathResources.ready(state, job)) return;
        var work = job.stationWork().orElseThrow(); var identity = new ActorActuationId(id,
                StationWorkDeathResources.execution(state, id.actorId()).orElseThrow());
        if (!work.phase().carriesCargo()) {
            FrontierV3CommandSubmission.submit(runtime, "bakery-death-settled", job.id().value(),
                    new BakeryDeathSettled(identity, work.phase(), 0, Optional.empty()));
            return;
        }
        var hand = body.getMainHandItem();
        if (!ItemStack.matches(before, hand)) return;
        long epoch = 0;
        boolean exact = StationWorkCargo.exact(state, job);
        if (exact) {
            var item = state.inventory().items().get(StationWorkCargo.exactId(job));
            if (item == null || !FrontierV3ExactItemPresentation.exactMatch(hand, item)) return;
        } else {
            var resources = state.inventory().fungibleResources();
            var bindings = resources.bindings().values().stream().filter(binding -> binding.accountId().equals(work.actorAccountId())).toList();
            if (bindings.size() != 1 || !bindings.getFirst().address().equals(new PhysicalStackAddress.ActorHand(id.actorId(), body.getUUID(), ActorContainerItemOrder.Hand.MAIN))) return;
            var binding = bindings.getFirst(); epoch = binding.authorityEpoch();
            if (!ItemStack.matches(hand, new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(binding.itemKind())), binding.quantity()))) return;
        }
        var carrier = UUID.nameUUIDFromBytes((state.bootstrap().worldId().value() + ":bakery-death:" + job.id().value()
                + ":" + id.physicalEpoch()).getBytes(StandardCharsets.UTF_8));
        if (level.getEntity(carrier) != null || FrontierV3ResourceHandoffLedger.get(level).blocks(carrier)) return;
        var drop = new ItemEntity(level, body.getX(), body.getY() + 0.25D, body.getZ(), hand.copy()); drop.setUUID(carrier);
        if (exact) {
            FrontierV3ExactItemPresentation.bindWorldCarrier(drop.getItem(), carrier);
            drop.setItem(drop.getItem());
        }
        var receipt = new BakeryDeathSettled(identity, work.phase(), epoch, Optional.of(FrontierV3EntityResourceDropOperation.ground(drop)));
        FrontierV3EntityResourceDropOperation.perform(level, runtime, body, new ActorItemSlot.Hand(ActorContainerItemOrder.Hand.MAIN), hand.copy(), drop, receipt);
    }
    static boolean settled(FrontierWorldState state, ActorBodyId id) {
        return StationWorkDeathResources.work(state, id.actorId()).isEmpty();
    }
}
