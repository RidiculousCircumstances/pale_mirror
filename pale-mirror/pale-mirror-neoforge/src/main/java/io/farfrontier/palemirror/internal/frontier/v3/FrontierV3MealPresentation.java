package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActuationId;
import io.farfrontier.palemirror.internal.network.ResidentMealPresentationPayload;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import java.util.*;

/** Cosmetic pulses from an already-authorized seated eater and its actual food pocket. */
final class FrontierV3MealPresentation {
    private record Stamp(long epoch, long sequence, long sentAt, boolean eating) { }
    private static final Map<Mob, Stamp> STAMPS = new WeakHashMap<>();
    static void eating(Mob actor, ActorActuationId authority, ItemStack actualFood, int phase) {
        if (actualFood.isEmpty()) { clear(actor); return; }
        var declaration = FrontierV3ActorCarrierComposition.declaredBy(actor).orElseThrow();
        if (!declaration.actorId().equals(authority.body().actorId()) || declaration.epoch() != authority.body().physicalEpoch())
            throw new IllegalArgumentException("meal presentation addresses a foreign body");
        var level = (ServerLevel) actor.level(); long now = level.getGameTime(); var prior = STAMPS.get(actor);
        if (prior != null && prior.epoch() == declaration.epoch() && prior.eating() && now - prior.sentAt() < 20) return;
        long sequence = Math.max(now + 1, prior == null ? 1 : Math.addExact(prior.sequence(), 1));
        STAMPS.put(actor, new Stamp(declaration.epoch(), sequence, now, true));
        send(level, actor, new ResidentMealPresentationPayload(actor.getId(), actor.getUUID(), declaration.epoch(), sequence,
                Optional.of(BuiltInRegistries.ITEM.getKey(actualFood.getItem())), 40, phase));
    }
    static void clear(Mob actor) {
        var prior = STAMPS.get(actor); if (prior == null || !prior.eating()) return;
        var declaration = FrontierV3ActorCarrierComposition.declaredBy(actor);
        if (declaration.isEmpty() || declaration.orElseThrow().epoch() != prior.epoch()) { STAMPS.remove(actor); return; }
        var level = (ServerLevel) actor.level(); long sequence = Math.max(level.getGameTime() + 1, Math.addExact(prior.sequence(), 1));
        STAMPS.put(actor, new Stamp(prior.epoch(), sequence, level.getGameTime(), false));
        send(level, actor, new ResidentMealPresentationPayload(actor.getId(), actor.getUUID(), prior.epoch(), sequence, Optional.empty(), 1, 0));
    }
    private static void send(ServerLevel level, Mob actor, ResidentMealPresentationPayload payload) {
        for (var player : level.players()) if (player.distanceToSqr(actor) <= 128 * 128 && NetworkRegistry.hasChannel(player.connection, payload.type().id()))
            PacketDistributor.sendToPlayer(player, payload);
    }
    private FrontierV3MealPresentation() { }
}
