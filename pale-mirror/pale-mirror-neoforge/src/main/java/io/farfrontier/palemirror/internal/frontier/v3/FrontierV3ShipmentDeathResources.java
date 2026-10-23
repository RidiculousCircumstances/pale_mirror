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
    private FrontierV3ShipmentDeathResources() { }
    static boolean settled(FrontierWorldState state, ActorBodyId id) {
        return state.shipments().shipments().values().stream().filter(shipment -> shipment.execution().actorId().equals(id.actorId()))
                .noneMatch(shipment -> shipment.pendingPhysicalStep().isPresent()
                        || shipment.status() == Shipment.Status.AWAITING_LOAD
                        || shipment.carrying() && shipment.quantity() > 0 && shipment.mobileContainerId().isEmpty());
    }
    static FrontierV3ActorDeathResourceComposition.AfterFatality prepare(ServerLevel level,
            FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, Mob body, ActorBodyId id) {
        var state = runtime.decodedState().orElseThrow();
        if (!ActorBodyAuthority.current(state, id.actorId()).equals(id)) return () -> { };
        var witness = body.getMainHandItem().copy();
        var effects = FrontierV3ShipmentDeathEffects.capture(level, state, body, id);
        return FrontierV3ActorDeathResourceComposition.retained(effects,
                () -> settle(level, runtime, body, id, witness));
    }
    static void recover(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
            Mob body, ActorBodyId id, FrontierV3BodyResourceSnapshot snapshot) {
        ActorBodyAuthority.requireRetiredDeath(runtime.decodedState().orElseThrow(), id);
        var witness = snapshot.slots(level).get(new ActorItemSlot.Hand(ActorContainerItemOrder.Hand.MAIN));
        if (witness == null) throw new IllegalStateException("shipment recovery lacks a pre-loot native hand");
        settle(level, runtime, body, id, witness);
    }
    private static void settle(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
            Mob body, ActorBodyId id, ItemStack witness) {
        var state = runtime.decodedState().orElseThrow(); ActorBodyAuthority.requireRetiredDeath(state, id);
        var uncollected = state.shipments().shipments().values().stream()
                .filter(s -> s.execution().actorId().equals(id.actorId()) && s.status() == Shipment.Status.AWAITING_LOAD
                        && s.pendingPhysicalStep().isEmpty() && s.reception().isEmpty()).toList();
        for (var shipment : uncollected)
            FrontierV3CommandSubmission.submit(runtime, "shipment-death-settled", shipment.id().value(),
                    new ShipmentDeathSettled(shipment.id(), shipment.revision(), new ActorActuationId(id, shipment.execution())));
        captured(level, runtime, body, id, witness).settle();
    }
    private static FrontierV3ActorDeathResourceComposition.AfterFatality captured(ServerLevel level,
            FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, Mob body, ActorBodyId id, ItemStack witness) {
        var state = runtime.decodedState().orElseThrow();
        var authority = state.actorExecutions().actors().get(id.actorId());
        if (authority == null) return () -> { };
        var execution = authority.current().filter(e -> e.activityKind() == ActorActivityKind.COURIER || e.activityKind() == ActorActivityKind.GROUP_MEMBER)
                .or(() -> authority.suspended().filter(e -> e.activityKind() == ActorActivityKind.COURIER || e.activityKind() == ActorActivityKind.GROUP_MEMBER));
        if (execution.isEmpty()) return () -> { };
        var retainedExecution = execution.orElseThrow();
        var shipment = retainedExecution.activityKind() == ActorActivityKind.COURIER
                ? state.shipments().shipments().get(retainedExecution.activityOwnerId())
                : TransportCargoExecution.parcels(state, state.unitGroups().groups().get(retainedExecution.activityOwnerId()), retainedExecution)
                    .stream().filter(s -> !s.terminal() && s.carrying()).findFirst().orElse(null);
        if (shipment == null || shipment.mobileContainerId().isPresent() || !shipment.carrying() || shipment.pendingPhysicalStep().isPresent()
                || shipment.reception().isPresent()) return () -> { }; // Possibly applied endpoint effects retain their exact owner.
        if (shipment.cargo() instanceof ShipmentCargo.Individual cargo) {
            var item = state.inventory().items().get(cargo.itemId());
            if (!cargo.matches(item)) throw new IllegalArgumentException("dead courier lost its individual preimage");
            return () -> {
                var current = runtime.decodedState().orElseThrow();
                if (shipment.equals(current.shipments().shipments().get(shipment.id())) && ItemStack.matches(witness, body.getMainHandItem()))
                    FrontierV3ExactHeldItemDeath.resolve(level, runtime, body, item, "shipment:" + shipment.id().value(), "shipment_item");
            };
        }
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
            if (level.getEntity(carrier) != null || FrontierV3ResourceHandoffLedger.get(level).blocks(carrier)) return;
            ItemEntity drop = new ItemEntity(level, body.getX(), body.getY() + 0.25D, body.getZ(), witness.copy());
            drop.setUUID(carrier);
            var receipt = new ShipmentCargoDispositionObserved(shipment.id(), shipment.revision(), identity,
                    ShipmentCargoDispositionObserved.Outcome.WORLD_DROP, Optional.of(FrontierV3EntityResourceDropOperation.ground(drop)));
            FrontierV3EntityResourceDropOperation.perform(level, runtime, body,
                    new ActorItemSlot.Hand(ActorContainerItemOrder.Hand.MAIN), drop, receipt);
        };
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
