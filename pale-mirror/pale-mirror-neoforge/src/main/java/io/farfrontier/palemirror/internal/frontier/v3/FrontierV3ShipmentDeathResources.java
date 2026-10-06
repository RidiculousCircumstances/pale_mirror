package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Captures the identified dying hand before Vanilla loot, under the retained shipment fence. */
final class FrontierV3ShipmentDeathResources {
    private static final String RECEIPT = "pmv3_shipment_death_drop_v1";
    private FrontierV3ShipmentDeathResources() { }
    static FrontierV3ActorDeathResourceComposition.AfterFatality prepare(ServerLevel level,
            FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, Mob body, ActorBodyId id) {
        var state = runtime.decodedState().orElseThrow();
        var authority = state.actorExecutions().actors().get(id.actorId());
        if (authority == null || !ActorBodyAuthority.current(state, id.actorId()).equals(id)) return () -> { };
        var execution = authority.current().filter(e -> e.activityKind() == ActorActivityKind.COURIER)
                .or(() -> authority.suspended().filter(e -> e.activityKind() == ActorActivityKind.COURIER));
        if (execution.isEmpty()) return () -> { };
        var shipment = state.shipments().shipments().get(execution.orElseThrow().activityOwnerId());
        if (shipment == null || shipment.status() != Shipment.Status.CARRYING || shipment.pendingPhysicalStep().isPresent()
                || shipment.reception().isPresent()) return () -> { }; // Possibly applied endpoint effects retain their exact owner.
        ItemStack witness = body.getMainHandItem().copy();
        if (!witness.isEmpty() && !matches(witness, shipment)) return () -> { }; // Unclassified substitution is not fictional loss.
        var identity = new ActorActuationId(id, execution.orElseThrow());
        return () -> {
            var current = runtime.decodedState().orElseThrow();
            if (!shipment.equals(current.shipments().shipments().get(shipment.id()))
                    || current.actorLocations().get(id.actorId()).condition().status() != ActorLifeStatus.DEAD
                    || !ItemStack.matches(witness, body.getMainHandItem())) return;
            if (witness.isEmpty()) {
                submit(level, runtime, new ShipmentCargoDispositionObserved(shipment.id(), shipment.revision(), identity,
                        ShipmentCargoDispositionObserved.Outcome.MISSING_BEFORE_LOOT, Optional.empty()));
                return;
            }
            UUID carrier = dropId(current, shipment, id);
            if (level.getEntity(carrier) != null) return; // Never replay insertion from absence/presence guesses.
            var receipt = new ShipmentCargoDispositionObserved(shipment.id(), shipment.revision(), identity,
                    ShipmentCargoDispositionObserved.Outcome.WORLD_DROP, Optional.of(carrier));
            ItemEntity drop = new ItemEntity(level, body.getX(), body.getY() + 0.25D, body.getZ(), witness.copy());
            drop.setUUID(carrier);
            drop.getPersistentData().putByteArray(RECEIPT,
                    io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition.payloadCodecs().encode(receipt));
            if (!level.addFreshEntity(drop)) return;
            body.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            if (level.getEntity(carrier) == drop && !drop.isRemoved() && matches(drop.getItem(), shipment)) submit(level, runtime, receipt);
        };
    }
    static boolean reconcileOneDrop(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state) {
        for (var shipment : state.shipments().shipments().values().stream().filter(s -> !s.terminal()).sorted(Comparator.comparing(Shipment::id)).toList()) {
            if (state.actorLocations().get(shipment.execution().actorId()).condition().status() != ActorLifeStatus.DEAD) continue;
            var retired = state.fencedRecovery().tombstones().get(ActorBodyId.recoveryBindingId(shipment.execution().actorId()));
            if (retired == null) continue;
            var id = new ActorBodyId(shipment.execution().actorId(), retired.retiredEpoch());
            ActorBodyAuthority.requireRetiredDeath(state, id);
            UUID carrier = dropId(state, shipment, id);
            if (!(level.getEntity(carrier) instanceof ItemEntity drop) || drop.isRemoved() || !matches(drop.getItem(), shipment)
                    || !drop.getPersistentData().contains(RECEIPT, net.minecraft.nbt.Tag.TAG_BYTE_ARRAY)) continue;
            ShipmentCargoDispositionObserved receipt;
            try {
                receipt = (ShipmentCargoDispositionObserved) io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition.payloadCodecs()
                        .decode("frontier.shipment_cargo_disposition_observed", drop.getPersistentData().getByteArray(RECEIPT));
            } catch (IllegalArgumentException invalid) { continue; }
            if (!receipt.shipmentId().equals(shipment.id()) || !receipt.identity().body().equals(id)
                    || !receipt.worldCarrier().equals(Optional.of(carrier))) continue;
            if (submit(level, runtime, receipt)) return true;
        }
        return false;
    }
    private static boolean matches(ItemStack stack, Shipment shipment) {
        return !stack.isEmpty() && stack.getCount() == shipment.quantity()
                && BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(shipment.itemKind());
    }
    private static UUID dropId(FrontierWorldState state, Shipment shipment, ActorBodyId body) {
        return UUID.nameUUIDFromBytes((state.bootstrap().worldId().value() + ":shipment-death:" + shipment.id().value()
                + ":" + shipment.revision() + ":" + body.physicalEpoch()).getBytes(StandardCharsets.UTF_8));
    }
    private static boolean submit(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, ShipmentCargoDispositionObserved receipt) {
        var result = FrontierV3CommandSubmission.submit(runtime, "shipment-cargo-disposition", receipt.shipmentId().value(), receipt);
        FrontierV3DiagnosticTrace.record(level.getServer(), "shipment:" + receipt.shipmentId().value(), "shipment-cargo-disposition", receipt.shipmentId(), result);
        return result instanceof CommandResult.Accepted;
    }
}
