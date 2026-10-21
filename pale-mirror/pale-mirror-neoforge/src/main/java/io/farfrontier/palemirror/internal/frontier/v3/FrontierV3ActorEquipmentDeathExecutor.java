package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.ExactItemStack;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.HumanTacticalFunctionProjection;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

import java.util.List;

/**
 * Resolves one exact actor-held stack before Vanilla removes a managed body on death.
 *
 * <p>A matching physical hand becomes one tagged world drop through the common native
 * operation, then its exact custody consequence is confirmed. An absent hand is never
 * reconstructed into the world. This first boundary intentionally supports the single visible
 * hand surface used by the current graybox equipment contract.</p>
 */
final class FrontierV3ActorEquipmentDeathExecutor {
    private FrontierV3ActorEquipmentDeathExecutor() { }
    static boolean settled(FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId id) {
        return state.inventory().actorItems(id.actorId()).isEmpty();
    }

    static boolean resolve(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, Entity entity, SubjectId actor) {
        FrontierWorldState state = runtime.decodedState().orElse(null);
        var declaration = FrontierV3ActorCarrierComposition.declaredBy(entity).orElse(null);
        if (state == null || declaration == null || !declaration.actorId().equals(actor)
                || !state.actorLocations().containsKey(actor) || declaration.kind() != state.actorLocations().get(actor).kind()
                || !io.farfrontier.palemirror.frontier.v3.model.SceneLease.deterministicEntityId(state.bootstrap().worldId(), actor).equals(entity.getUUID()))
            return false;
        List<ExactItemStack> items = state.inventory().actorItems(actor).stream()
                .filter(item -> HumanTacticalFunctionProjection.isGrayboxWeaponKind(item.itemKind())).toList();
        if (items.isEmpty()) return false;
        if (items.size() != 1) {
            throw new IllegalStateException("current graybox actor equipment contract permits one exact held weapon");
        }
        if (!(entity instanceof Mob mob)) throw new IllegalArgumentException("managed equipment requires a Mob hand");
        return FrontierV3ExactHeldItemDeath.resolve(level, runtime, mob, items.getFirst(),
                "actor-equipment:" + items.getFirst().id().value(), "actor_equipment");
    }

    static void recover(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
            Mob body, io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId id, FrontierV3BodyResourceSnapshot snapshot) {
        var state = runtime.decodedState().orElseThrow();
        io.farfrontier.palemirror.frontier.v3.model.ActorBodyAuthority.requireRetiredDeath(state, id);
        // Missing after restart is not the pre-loot loss observation. Pending drop
        // reconciliation owns that outcome; only a still-held exact stack can start a drop.
        var before = snapshot.slots(level).get(new io.farfrontier.palemirror.frontier.v3.model.ActorItemSlot.Hand(
                io.farfrontier.palemirror.frontier.v3.model.ActorContainerItemOrder.Hand.MAIN));
        if (before == null || !net.minecraft.world.item.ItemStack.matches(before, body.getMainHandItem())) return;
        resolve(level, runtime, body, id.actorId());
    }
}
