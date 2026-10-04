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
        ExactItemStack item = items.getFirst(); InventoryCustody.Actor source = new InventoryCustody.Actor(actor);
        if (!(entity instanceof Mob mob) || !FrontierV3CargoHandoffExecutor.exactMatch(mob.getItemBySlot(EquipmentSlot.MAINHAND), item)) {
            return destroy(level, runtime, item, source, "actor-death-held-item-missing");
        }
        ItemStack stack = mob.getItemBySlot(EquipmentSlot.MAINHAND);
        UUID carrierId = UUID.nameUUIDFromBytes((state.bootstrap().worldId().value() + ":" + actor.value() + ":" + item.id().value())
                .getBytes(StandardCharsets.UTF_8));
        ItemEntity drop = new ItemEntity(level, entity.getX(), entity.getY() + 0.25D, entity.getZ(), stack.copy());
        drop.setUUID(carrierId); FrontierV3CargoHandoffExecutor.bindWorldCarrier(drop.getItem(), carrierId); drop.setItem(drop.getItem());
        CommandResult result = FrontierV3CommandSubmission.submit(runtime, "actor-equipment-death-drop", item.id().value(),
                new io.farfrontier.palemirror.frontier.v3.model.ExactItemCustodyChanged(item.id(), source, new InventoryCustody.WorldCarrier(carrierId)));
        FrontierV3DiagnosticTrace.record(level.getServer(), FrontierV3DiagnosticTrace.defenderEquipmentCorrelation(item.id()),
                "defender_equipment_drop_receipt", actor, result);
        if (!(result instanceof CommandResult.Accepted)) return false;
        mob.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        if (!level.addFreshEntity(drop)) {
            return destroy(level, runtime, item, new InventoryCustody.WorldCarrier(carrierId), "actor-death-drop-spawn-failed");
        }
        FrontierV3DiagnosticTrace.record(level.getServer(), FrontierV3DiagnosticTrace.defenderEquipmentCorrelation(item.id()),
                "defender_equipment_dropped", actor, result);
        return true;
    }

    private static boolean destroy(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, ExactItemStack item,
                                InventoryCustody source, String cause) {
        CommandResult result = FrontierV3CommandSubmission.submit(runtime, "actor-equipment-death-destroyed", item.id().value(),
                new ExactItemDestroyed(item.id(), source, cause));
        FrontierV3DiagnosticTrace.record(level.getServer(), FrontierV3DiagnosticTrace.defenderEquipmentCorrelation(item.id()),
                "defender_equipment_destroyed", item.economicOwnerId(), result);
        return result instanceof CommandResult.Accepted;
    }
}
