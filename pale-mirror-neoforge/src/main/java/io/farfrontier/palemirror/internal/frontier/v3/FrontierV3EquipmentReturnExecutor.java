package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.CauseChain;
import io.farfrontier.palemirror.frontier.v3.api.CheckpointImage;
import io.farfrontier.palemirror.frontier.v3.api.CommandId;
import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.AmbientLeaseStatus;
import io.farfrontier.palemirror.frontier.v3.model.ContainerSurface;
import io.farfrontier.palemirror.frontier.v3.model.ContainerSurfaceStatus;
import io.farfrontier.palemirror.frontier.v3.model.EquipmentReturnObservation;
import io.farfrontier.palemirror.frontier.v3.model.EquipmentReturnStateSupport;
import io.farfrontier.palemirror.frontier.v3.model.ExactItemStack;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.InventoryCustody;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalEffectObservation;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalIntentTransition;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

import java.util.Comparator;
import java.util.Optional;

/** One loaded-chunk, durable-before-effect former-defender-hand to depot-slot equipment return. */
final class FrontierV3EquipmentReturnExecutor {
    private FrontierV3EquipmentReturnExecutor() { }

    static void tick(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        FrontierWorldState state = runtime.decodedState().orElse(null); if (state == null) return;
        state.physicalIntents().values().stream().sorted(Comparator.comparing(PhysicalIntent::id))
                .filter(intent -> intent.kind() == PhysicalIntentKind.EQUIPMENT_RETURN)
                .filter(intent -> intent.status() == PhysicalIntentStatus.PREPARED || intent.status() == PhysicalIntentStatus.RUNNING)
                .findFirst().ifPresent(intent -> execute(level, runtime, state, intent));
    }

    private static void execute(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                FrontierWorldState state, PhysicalIntent intent) {
        Target target = target(state, intent);
        if (target == null) { unknown(runtime, intent.id(), "canonical-conflict"); return; }
        if (!level.hasChunkAt(target.chestPosition())) return;
        ChestBlockEntity chest = FrontierV3ContainerSurfaceExecutor.activeChest(level, target.chestPosition(), target.targetSlot().containerId());
        Villager resident = resident(level, state, target.residentId());
        if (chest == null || resident == null) return;
        if (intent.status() == PhysicalIntentStatus.RUNNING) { inspectRunning(level, runtime, intent, target, chest, resident); return; }
        if (!handMatches(resident, target) || !chest.getItem(target.targetSlot().slot()).isEmpty()) {
            unknown(runtime, intent.id(), "precondition-conflict"); return;
        }
        if (!transition(runtime, intent.id(), PhysicalIntentStatus.RUNNING, Optional.empty(), "running")) return;
        if (!handOff(chest, resident, target)) { unknown(runtime, intent.id(), "effect-conflict"); return; }
        confirm(level, runtime, intent, target);
    }

    private static Target target(FrontierWorldState state, PhysicalIntent intent) {
        if (intent.subjectIds().size() != 3) return null;
        SubjectId ownerId = intent.subjectIds().getFirst(), residentId = intent.subjectIds().get(1), itemId = intent.subjectIds().get(2);
        ExactItemStack item = state.inventory().items().get(itemId);
        final InventoryCustody.ContainerSlot target;
        try { target = EquipmentReturnStateSupport.targetSlot(state, intent); }
        catch (IllegalArgumentException invalid) { return null; }
        try { EquipmentReturnStateSupport.validateIntent(state, intent); }
        catch (IllegalArgumentException invalid) { return null; }
        if (item == null || !(item.custody() instanceof InventoryCustody.Actor actor)
                || !actor.actorId().equals(residentId) || state.inventory().itemAt(target.containerId(), target.slot()).isPresent()) return null;
        ContainerSurface surface = state.inventory().surfaces().get(target.containerId());
        if (surface == null || surface.status() != ContainerSurfaceStatus.ACTIVE) return null;
        return new Target(ownerId, residentId, item, target, new BlockPos(surface.position().x(), surface.position().y(), surface.position().z()));
    }

    private static Villager resident(ServerLevel level, FrontierWorldState state, SubjectId residentId) {
        if (state.ambientLeases().get(residentId) == null || state.ambientLeases().get(residentId).status() != AmbientLeaseStatus.HOT) return null;
        var body = level.getEntity(FrontierV3AmbientActorExecutor.entityId(state, residentId));
        return body instanceof Villager villager && FrontierV3AmbientActorExecutor.owned(villager, residentId, false) ? villager : null;
    }

    private static boolean handMatches(Villager resident, Target target) {
        return FrontierV3CargoHandoffExecutor.exactMatch(resident.getItemBySlot(EquipmentSlot.MAINHAND), target.item());
    }

    static boolean handOff(ChestBlockEntity chest, Villager resident, Target target) {
        if (!handMatches(resident, target) || !chest.getItem(target.targetSlot().slot()).isEmpty()) return false;
        ItemStack stack = resident.getItemBySlot(EquipmentSlot.MAINHAND); resident.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        chest.setItem(target.targetSlot().slot(), stack); chest.setChanged();
        return resident.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty()
                && FrontierV3CargoHandoffExecutor.exactMatch(chest.getItem(target.targetSlot().slot()), target.item());
    }

    private static void inspectRunning(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, PhysicalIntent intent, Target target,
                                       ChestBlockEntity chest, Villager resident) {
        boolean hand = handMatches(resident, target), stored = FrontierV3CargoHandoffExecutor.exactMatch(chest.getItem(target.targetSlot().slot()), target.item());
        if (!hand && stored) { confirm(level, runtime, intent, target); return; }
        if (hand && chest.getItem(target.targetSlot().slot()).isEmpty() && handOff(chest, resident, target)) { confirm(level, runtime, intent, target); return; }
        unknown(runtime, intent.id(), "restart-postcondition-conflict");
    }

    private static void confirm(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, PhysicalIntent intent, Target target) {
        EquipmentReturnObservation observation = new EquipmentReturnObservation(new PhysicalObservationId("observation:" + intent.id().value().replace(':', '-')),
                intent.id(), target.ownerId(), target.residentId(), target.item().id(), target.targetSlot());
        CommandResult result = transitionResult(runtime, intent.id(), PhysicalIntentStatus.CONFIRMED, Optional.of(observation), "confirmed");
        if (!(result instanceof CommandResult.Accepted)) throw new IllegalStateException("equipment return confirmation was rejected");
        FrontierV3DiagnosticTrace.record(level.getServer(), FrontierV3DiagnosticTrace.humanEquipmentCorrelation(target.ownerId(), target.item().id()),
                "human_equipment_returned", target.ownerId(), result);
    }

    private static void unknown(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, PhysicalIntentId id, String phase) {
        transition(runtime, id, PhysicalIntentStatus.UNKNOWN_AFTER_RESTART, Optional.empty(), phase);
    }

    private static boolean transition(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, PhysicalIntentId id, PhysicalIntentStatus status,
                                      Optional<PhysicalEffectObservation> observation, String phase) {
        return transitionResult(runtime, id, status, observation, phase) instanceof CommandResult.Accepted;
    }

    private static CommandResult transitionResult(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, PhysicalIntentId id, PhysicalIntentStatus status,
                                                   Optional<PhysicalEffectObservation> observation, String phase) {
        CheckpointImage checkpoint = runtime.checkpointImage().orElseThrow(() -> new IllegalStateException("v3 runtime is inactive"));
        CommandId command = commandId(phase, checkpoint.revision());
        return runtime.submit(new FrontierCommand(1, command, checkpoint.worldId(), checkpoint.revision(), checkpoint.instant(),
                FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(command), new PhysicalIntentTransition(id, status, observation)))
                .orElseThrow(() -> new IllegalStateException("v3 runtime is inactive"));
    }

    /** Revision makes each accepted executor command unique without embedding an unbounded semantic ID. */
    static CommandId commandId(String phase, io.farfrontier.palemirror.frontier.v3.api.Revision revision) {
        return new CommandId("executor:equipment-return-" + phase + "-r" + revision.value());
    }

    record Target(SubjectId ownerId, SubjectId residentId, ExactItemStack item, InventoryCustody.ContainerSlot targetSlot,
                  BlockPos chestPosition) { }
}
