package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
import io.farfrontier.palemirror.frontier.v3.model.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Shared exact-hand disposition. The resource owner, not this adapter, decides when loss is known. */
final class FrontierV3ExactHeldItemDeath {
    private FrontierV3ExactHeldItemDeath() { }

    static boolean resolve(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                           Mob body, ExactItemStack item, String correlation, String tracePrefix) {
        var state = runtime.decodedState().orElseThrow();
        var declaration = FrontierV3ActorCarrierComposition.declaredBy(body).orElse(null);
        if (!(item.custody() instanceof InventoryCustody.Actor source) || declaration == null
                || !declaration.actorId().equals(source.actorId()) || body.level() != level
                || !item.equals(state.inventory().items().get(item.id()))
                || !SceneLease.deterministicEntityId(state.bootstrap().worldId(), source.actorId()).equals(body.getUUID()))
            throw new IllegalArgumentException("exact death disposition lacks its declared actor/hand predecessor");
        var hand = body.getItemBySlot(EquipmentSlot.MAINHAND);
        if (!FrontierV3CargoHandoffExecutor.exactMatch(hand, item))
            return destroy(level, runtime, item, source, correlation, tracePrefix, "actor-death-held-item-missing");
        var carrier = UUID.nameUUIDFromBytes((state.bootstrap().worldId().value() + ":"
                + source.actorId().value() + ":" + item.id().value()).getBytes(StandardCharsets.UTF_8));
        var drop = new ItemEntity(level, body.getX(), body.getY() + 0.25D, body.getZ(), hand.copy());
        drop.setUUID(carrier);
        FrontierV3CargoHandoffExecutor.bindWorldCarrier(drop.getItem(), carrier);
        drop.setItem(drop.getItem());
        var result = FrontierV3CommandSubmission.submit(runtime, "actor-held-death-drop", item.id().value(),
                new ExactItemCustodyChanged(item.id(), source, new InventoryCustody.WorldCarrier(carrier)));
        FrontierV3DiagnosticTrace.record(level.getServer(), correlation, tracePrefix + "_drop_receipt", source.actorId(), result);
        if (!(result instanceof CommandResult.Accepted)) return false;
        body.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        if (!level.addFreshEntity(drop))
            return destroy(level, runtime, item, new InventoryCustody.WorldCarrier(carrier), correlation, tracePrefix,
                    "actor-death-drop-spawn-failed");
        FrontierV3DiagnosticTrace.record(level.getServer(), correlation, tracePrefix + "_dropped", source.actorId(), result);
        return true;
    }

    private static boolean destroy(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                   ExactItemStack item, InventoryCustody source, String correlation, String tracePrefix, String cause) {
        var result = FrontierV3CommandSubmission.submit(runtime, "actor-held-death-destroyed", item.id().value(),
                new ExactItemDestroyed(item.id(), source, cause));
        FrontierV3DiagnosticTrace.record(level.getServer(), correlation, tracePrefix + "_destroyed", item.economicOwnerId(), result);
        return result instanceof CommandResult.Accepted;
    }
}
