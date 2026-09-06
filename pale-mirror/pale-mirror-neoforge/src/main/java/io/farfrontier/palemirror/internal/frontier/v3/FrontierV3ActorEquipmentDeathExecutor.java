package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.ExactItemDestroyed;
import io.farfrontier.palemirror.frontier.v3.model.ExactItemStack;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.HumanTacticalFunctionProjection;
import io.farfrontier.palemirror.frontier.v3.model.InventoryCustody;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Resolves one exact actor-held stack before Vanilla removes a managed body on death.
 *
 * <p>A matching physical hand becomes one tagged world drop only after the canonical custody
 * transfer is durable. A missing or altered hand is an observed loss instead; it is never
 * reconstructed into the world. This first boundary intentionally supports the single visible
 * hand surface used by the current graybox equipment contract.</p>
 */
final class FrontierV3ActorEquipmentDeathExecutor {
    private FrontierV3ActorEquipmentDeathExecutor() { }

    static boolean resolve(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, Entity entity) {
        FrontierWorldState state = runtime.decodedState().orElse(null);
        Optional<SubjectId> actorId = actorId(state, entity);
        if (state == null || actorId.isEmpty()) return false;
        List<ExactItemStack> items = state.inventory().actorItems(actorId.orElseThrow()).stream()
                .filter(item -> HumanTacticalFunctionProjection.isGrayboxWeaponKind(item.itemKind())).toList();
        if (items.isEmpty()) return false;
        if (items.size() != 1) {
            throw new IllegalStateException("current graybox actor equipment contract permits one exact held weapon");
        }
        ExactItemStack item = items.getFirst(); InventoryCustody.Actor source = new InventoryCustody.Actor(actorId.orElseThrow());
        if (!(entity instanceof Mob mob) || !FrontierV3CargoHandoffExecutor.exactMatch(mob.getItemBySlot(EquipmentSlot.MAINHAND), item)) {
            destroy(level, runtime, item, source, "actor-death-held-item-missing");
            return true;
        }
        ItemStack stack = mob.getItemBySlot(EquipmentSlot.MAINHAND);
        UUID carrierId = UUID.nameUUIDFromBytes((state.bootstrap().worldId().value() + ":" + actorId.orElseThrow().value() + ":" + item.id().value())
                .getBytes(StandardCharsets.UTF_8));
        ItemEntity drop = new ItemEntity(level, entity.getX(), entity.getY() + 0.25D, entity.getZ(), stack.copy());
        drop.setUUID(carrierId); FrontierV3CargoHandoffExecutor.bindWorldCarrier(drop.getItem(), carrierId); drop.setItem(drop.getItem());
        CommandResult result = FrontierV3CommandSubmission.submit(runtime, "actor-equipment-death-drop", item.id().value(),
                new io.farfrontier.palemirror.frontier.v3.model.ExactItemCustodyChanged(item.id(), source, new InventoryCustody.WorldCarrier(carrierId)));
        mob.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        if (!level.addFreshEntity(drop)) {
            destroy(level, runtime, item, new InventoryCustody.WorldCarrier(carrierId), "actor-death-drop-spawn-failed");
            return true;
        }
        FrontierV3DiagnosticTrace.record(level.getServer(), FrontierV3DiagnosticTrace.defenderEquipmentCorrelation(item.id()),
                "defender_equipment_dropped", actorId.orElseThrow(), result);
        return true;
    }

    private static void destroy(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, ExactItemStack item,
                                InventoryCustody source, String cause) {
        CommandResult result = FrontierV3CommandSubmission.submit(runtime, "actor-equipment-death-destroyed", item.id().value(),
                new ExactItemDestroyed(item.id(), source, cause));
        FrontierV3DiagnosticTrace.record(level.getServer(), FrontierV3DiagnosticTrace.defenderEquipmentCorrelation(item.id()),
                "defender_equipment_destroyed", item.economicOwnerId(), result);
    }

    private static Optional<SubjectId> actorId(FrontierWorldState state, Entity entity) {
        if (state == null) return Optional.empty();
        String raw = entity.getPersistentData().getString(FrontierV3AmbientActorExecutor.ACTOR_KEY);
        if (raw.isBlank()) raw = entity.getPersistentData().getString(FrontierV3SceneExecutor.ACTOR_KEY);
        if (raw.isBlank()) return Optional.empty();
        try {
            SubjectId actor = new SubjectId(raw);
            return state.actorLocations().containsKey(actor) && FrontierV3AmbientActorExecutor.entityId(state, actor).equals(entity.getUUID())
                    ? Optional.of(actor) : Optional.empty();
        } catch (IllegalArgumentException invalid) { return Optional.empty(); }
    }
}
