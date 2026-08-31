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
import io.farfrontier.palemirror.frontier.v3.model.EquipmentIssueObservation;
import io.farfrontier.palemirror.frontier.v3.model.ExactItemStack;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.HumanTacticalFunctionProjection;
import io.farfrontier.palemirror.frontier.v3.model.InventoryCustody;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalEffectObservation;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalIntentTransition;
import io.farfrontier.palemirror.frontier.v3.model.SettlementAssault;
import io.farfrontier.palemirror.frontier.v3.model.SettlementAssaultStatus;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

import java.util.Comparator;
import java.util.Optional;

/**
 * One loaded-chunk, durable-before-effect depot-to-Villager equipment hand-off.
 *
 * <p>The canonical item remains in its source slot until an exact tagged stack is observed in
 * the exact deterministic resident body hand. A RUNNING request may resume only from a wholly
 * unchanged source; every mixed physical result is retained as UNKNOWN rather than repaired.</p>
 */
final class FrontierV3EquipmentIssueExecutor {
    private FrontierV3EquipmentIssueExecutor() { }

    static void tick(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        FrontierWorldState state = runtime.decodedState().orElse(null); if (state == null) return;
        state.physicalIntents().values().stream().sorted(Comparator.comparing(PhysicalIntent::id))
                .filter(intent -> intent.kind() == PhysicalIntentKind.EQUIPMENT_ISSUE)
                .filter(intent -> intent.status() == PhysicalIntentStatus.PREPARED || intent.status() == PhysicalIntentStatus.RUNNING)
                .findFirst().ifPresent(intent -> execute(level, runtime, state, intent));
    }

    private static void execute(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                FrontierWorldState state, PhysicalIntent intent) {
        Target target = target(state, intent);
        if (target == null) { unknown(runtime, intent.id(), "canonical-conflict"); return; }
        if (!level.hasChunkAt(target.chestPosition())) return;
        ChestBlockEntity chest = FrontierV3ContainerSurfaceExecutor.activeChest(level, target.chestPosition(), target.sourceSlot().containerId());
        Villager resident = resident(level, state, target.residentId());
        if (chest == null || resident == null) return;
        if (intent.status() == PhysicalIntentStatus.RUNNING) { inspectRunning(level, runtime, intent, target, chest, resident); return; }
        if (!sourceMatches(chest, target) || !resident.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty()) {
            unknown(runtime, intent.id(), "precondition-conflict"); return;
        }
        if (!transition(runtime, intent.id(), PhysicalIntentStatus.RUNNING, Optional.empty(), "running")) return;
        if (!handOff(chest, resident, target)) { unknown(runtime, intent.id(), "effect-conflict"); return; }
        confirm(level, runtime, intent, target);
    }

    private static Target target(FrontierWorldState state, PhysicalIntent intent) {
        if (intent.subjectIds().size() != 3) return null;
        SubjectId assaultId = intent.subjectIds().getFirst(), residentId = intent.subjectIds().get(1), itemId = intent.subjectIds().get(2);
        SettlementAssault assault = state.strategicPlans().settlementAssaults().get(assaultId);
        ExactItemStack item = state.inventory().items().get(itemId);
        if (assault == null || assault.status() == SettlementAssaultStatus.RESOLVED || !intent.causeSubjectId().equals(assault.settlementId())
                || !assault.defenderIds().contains(residentId) || item == null || !(item.custody() instanceof InventoryCustody.ContainerSlot source)
                || !source.containerId().equals(FrontierWorldState.depotId(assault.settlementId())) || !item.economicOwnerId().equals(assault.settlementId())
                || !HumanTacticalFunctionProjection.isGrayboxWeaponKind(item.itemKind())) return null;
        ContainerSurface surface = state.inventory().surfaces().get(source.containerId());
        if (surface == null || surface.status() != ContainerSurfaceStatus.ACTIVE) return null;
        return new Target(assaultId, residentId, item, source, new BlockPos(surface.position().x(), surface.position().y(), surface.position().z()));
    }

    private static Villager resident(ServerLevel level, FrontierWorldState state, SubjectId residentId) {
        if (state.ambientLeases().get(residentId) == null || state.ambientLeases().get(residentId).status() != AmbientLeaseStatus.HOT) return null;
        var body = level.getEntity(FrontierV3AmbientActorExecutor.entityId(state, residentId));
        return body instanceof Villager villager && FrontierV3AmbientActorExecutor.owned(villager, residentId, false) ? villager : null;
    }

    private static boolean sourceMatches(ChestBlockEntity chest, Target target) {
        return target.sourceSlot().slot() >= 0 && target.sourceSlot().slot() < chest.getContainerSize()
                && FrontierV3CargoHandoffExecutor.exactMatch(chest.getItem(target.sourceSlot().slot()), target.item());
    }

    static boolean handOff(ChestBlockEntity chest, Villager resident, Target target) {
        if (!sourceMatches(chest, target) || !resident.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty()) return false;
        ItemStack stack = chest.getItem(target.sourceSlot().slot()); chest.setItem(target.sourceSlot().slot(), ItemStack.EMPTY); chest.setChanged();
        resident.setItemSlot(EquipmentSlot.MAINHAND, stack);
        return chest.getItem(target.sourceSlot().slot()).isEmpty() && FrontierV3CargoHandoffExecutor.exactMatch(resident.getItemBySlot(EquipmentSlot.MAINHAND), target.item());
    }

    private static void inspectRunning(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, PhysicalIntent intent, Target target,
                                       ChestBlockEntity chest, Villager resident) {
        boolean source = sourceMatches(chest, target), hand = FrontierV3CargoHandoffExecutor.exactMatch(resident.getItemBySlot(EquipmentSlot.MAINHAND), target.item());
        if (!source && hand) { confirm(level, runtime, intent, target); return; }
        if (source && resident.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty() && handOff(chest, resident, target)) { confirm(level, runtime, intent, target); return; }
        unknown(runtime, intent.id(), "restart-postcondition-conflict");
    }

    private static void confirm(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, PhysicalIntent intent, Target target) {
        EquipmentIssueObservation observation = new EquipmentIssueObservation(new PhysicalObservationId("observation:" + intent.id().value().replace(':', '-')),
                intent.id(), target.assaultId(), target.residentId(), target.item().id(), target.sourceSlot());
        CommandResult result = transitionResult(runtime, intent.id(), PhysicalIntentStatus.CONFIRMED, Optional.of(observation), "confirmed");
        if (!(result instanceof CommandResult.Accepted)) {
            throw new IllegalStateException("equipment issue confirmation was rejected");
        }
        FrontierV3DiagnosticTrace.record(level.getServer(), FrontierV3DiagnosticTrace.defenderEquipmentCorrelation(target.item().id()),
                "defender_equipment_issued", target.assaultId(), result);
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
        CommandId command = new CommandId("executor:equipment-issue-" + phase + "-" + id.value().replace(':', '-'));
        return runtime.submit(new FrontierCommand(1, command, checkpoint.worldId(), checkpoint.revision(), checkpoint.instant(),
                FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(command), new PhysicalIntentTransition(id, status, observation)))
                .orElseThrow(() -> new IllegalStateException("v3 runtime is inactive"));
    }

    record Target(SubjectId assaultId, SubjectId residentId, ExactItemStack item, InventoryCustody.ContainerSlot sourceSlot,
                  BlockPos chestPosition) { }
}
