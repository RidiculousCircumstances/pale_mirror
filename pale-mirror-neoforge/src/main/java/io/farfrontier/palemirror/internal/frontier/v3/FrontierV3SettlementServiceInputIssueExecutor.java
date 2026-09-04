package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.CauseChain;
import io.farfrontier.palemirror.frontier.v3.api.CommandId;
import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.ContainerSurface;
import io.farfrontier.palemirror.frontier.v3.model.ContainerSurfaceStatus;
import io.farfrontier.palemirror.frontier.v3.model.ExactItemStack;
import io.farfrontier.palemirror.frontier.v3.model.FrontierSceneBehaviors;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.InventoryCustody;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalEffectObservation;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalIntentTransition;
import io.farfrontier.palemirror.frontier.v3.model.SceneLease;
import io.farfrontier.palemirror.frontier.v3.model.SceneLeaseStatus;
import io.farfrontier.palemirror.frontier.v3.model.SettlementServiceInputIssueObservation;
import io.farfrontier.palemirror.frontier.v3.model.SettlementServiceInputIssueStateSupport;
import io.farfrontier.palemirror.frontier.v3.model.SettlementServiceWork;
import io.farfrontier.palemirror.frontier.v3.model.SettlementServiceWorkSceneCause;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Durable-before-effect hand-off from one declared depot service slot to the exact service
 * worker's visible main hand.  This is intentionally independent of defender equipment: the
 * service-work aggregate owns the station, route and purpose of this custody boundary.
 */
final class FrontierV3SettlementServiceInputIssueExecutor {
    private FrontierV3SettlementServiceInputIssueExecutor() { }

    static void tick(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        FrontierWorldState state = runtime.decodedState().orElse(null);
        if (state == null) return;
        FrontierV3PhysicalIntentScheduling.firstActionable(pendingIntents(state), intent -> readiness(level, state, intent))
                .ifPresent(intent -> execute(level, runtime, state, intent));
    }

    static List<PhysicalIntent> pendingIntents(FrontierWorldState state) {
        return state.physicalIntents().values().stream().filter(intent -> intent.kind() == PhysicalIntentKind.SETTLEMENT_SERVICE_INPUT_ISSUE)
                .filter(intent -> intent.status() == PhysicalIntentStatus.PREPARED || intent.status() == PhysicalIntentStatus.RUNNING
                        || intent.status() == PhysicalIntentStatus.UNKNOWN_AFTER_RESTART).toList();
    }

    static FrontierV3PhysicalIntentScheduling.Readiness readiness(ServerLevel level, FrontierWorldState state, PhysicalIntent intent) {
        Target target = target(state, intent);
        if (target == null) return FrontierV3PhysicalIntentScheduling.Readiness.INVALID;
        if (!level.hasChunkAt(target.chestPosition())) return FrontierV3PhysicalIntentScheduling.Readiness.DEFERRED;
        ChestBlockEntity chest = FrontierV3ContainerSurfaceExecutor.activeChest(level, target.chestPosition(), target.sourceSlot().containerId());
        if (chest == null) return FrontierV3PhysicalIntentScheduling.Readiness.DEFERRED;
        return resident(level, state, target) == null ? FrontierV3PhysicalIntentScheduling.Readiness.DEFERRED
                : FrontierV3PhysicalIntentScheduling.Readiness.RUNNABLE;
    }

    private static void execute(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                FrontierWorldState state, PhysicalIntent intent) {
        Target target = target(state, intent);
        if (target == null) { unknown(runtime, intent.id(), "canonical-conflict"); return; }
        if (!level.hasChunkAt(target.chestPosition())) return;
        ChestBlockEntity chest = FrontierV3ContainerSurfaceExecutor.activeChest(level, target.chestPosition(), target.sourceSlot().containerId());
        Villager worker = resident(level, state, target);
        if (chest == null || worker == null) return;
        if (intent.status() == PhysicalIntentStatus.UNKNOWN_AFTER_RESTART) { inspectRecovered(runtime, intent, target, chest, worker); return; }
        if (intent.status() == PhysicalIntentStatus.RUNNING) { inspectRunning(runtime, intent, target, chest, worker); return; }
        if (!sourceMatches(chest, target) || !worker.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty()) {
            unknown(runtime, intent.id(), "precondition-conflict"); return;
        }
        if (!transition(runtime, intent.id(), PhysicalIntentStatus.RUNNING, Optional.empty(), "running")) return;
        if (!handOff(chest, worker, target)) { unknown(runtime, intent.id(), "effect-conflict"); return; }
        confirm(runtime, intent, target);
    }

    private static Target target(FrontierWorldState state, PhysicalIntent intent) {
        try { SettlementServiceInputIssueStateSupport.validateIntent(state, intent); }
        catch (IllegalArgumentException invalid) { return null; }
        SubjectId workId = intent.subjectIds().getFirst();
        SettlementServiceWork work = state.serviceWorks().get(workId);
        ExactItemStack item = state.inventory().items().get(work.inputItemId());
        ContainerSurface surface = state.inventory().surfaces().get(work.inputSource().containerId());
        SceneLease lease = state.sceneLeases().values().stream().filter(value -> value.status() == SceneLeaseStatus.HOT)
                .filter(FrontierSceneBehaviors::isServiceWork)
                .filter(value -> FrontierSceneBehaviors.serviceWork(value).equals(new SettlementServiceWorkSceneCause(work.id())))
                .filter(value -> value.members().stream().anyMatch(member -> member.actorId().equals(work.workerId())))
                .min(Comparator.comparing(SceneLease::id)).orElse(null);
        if (item == null || surface == null || surface.status() != ContainerSurfaceStatus.ACTIVE || lease == null) return null;
        return new Target(work, item, work.inputSource(), new BlockPos(surface.position().x(), surface.position().y(), surface.position().z()), lease);
    }

    private static Villager resident(ServerLevel level, FrontierWorldState state, Target target) {
        java.util.UUID entityId = target.lease().members().stream().filter(member -> member.actorId().equals(target.work().workerId()))
                .findFirst().orElseThrow().entityId();
        var entity = level.getEntity(entityId);
        var member = target.lease().members().stream().filter(value -> value.actorId().equals(target.work().workerId())).findFirst().orElseThrow();
        if (!(entity instanceof Villager villager) || !villager.isAlive()
                || !FrontierV3SceneExecutor.owned(villager, state, target.lease(), member)) return null;
        var body = target.work().inputStation().standingBody();
        BlockPos station = new BlockPos(body.x(), body.y(), body.z());
        return villager.blockPosition().equals(station) ? villager : null;
    }

    private static boolean sourceMatches(ChestBlockEntity chest, Target target) {
        return target.sourceSlot().slot() < chest.getContainerSize()
                && FrontierV3CargoHandoffExecutor.exactMatch(chest.getItem(target.sourceSlot().slot()), target.item());
    }

    private static boolean handOff(ChestBlockEntity chest, Villager worker, Target target) {
        return handOff(chest, worker, target.item(), target.sourceSlot());
    }

    static boolean handOff(ChestBlockEntity chest, Villager worker, ExactItemStack item, InventoryCustody.ContainerSlot sourceSlot) {
        if (sourceSlot.slot() >= chest.getContainerSize() || !FrontierV3CargoHandoffExecutor.exactMatch(chest.getItem(sourceSlot.slot()), item)
                || !worker.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty()) return false;
        ItemStack stack = chest.getItem(sourceSlot.slot());
        chest.setItem(sourceSlot.slot(), ItemStack.EMPTY); chest.setChanged(); worker.setItemSlot(EquipmentSlot.MAINHAND, stack);
        return chest.getItem(sourceSlot.slot()).isEmpty()
                && FrontierV3CargoHandoffExecutor.exactMatch(worker.getItemBySlot(EquipmentSlot.MAINHAND), item);
    }

    private static void inspectRunning(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, PhysicalIntent intent, Target target,
                                       ChestBlockEntity chest, Villager worker) {
        boolean source = sourceMatches(chest, target), hand = FrontierV3CargoHandoffExecutor.exactMatch(worker.getItemBySlot(EquipmentSlot.MAINHAND), target.item());
        if (!source && hand) { confirm(runtime, intent, target); return; }
        if (source && worker.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty() && handOff(chest, worker, target)) { confirm(runtime, intent, target); return; }
        unknown(runtime, intent.id(), "restart-postcondition-conflict");
    }

    private static void inspectRecovered(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, PhysicalIntent intent, Target target,
                                         ChestBlockEntity chest, Villager worker) {
        boolean source = sourceMatches(chest, target), hand = FrontierV3CargoHandoffExecutor.exactMatch(worker.getItemBySlot(EquipmentSlot.MAINHAND), target.item());
        if (!source && hand) confirm(runtime, intent, target);
        else if (source && worker.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty() && handOff(chest, worker, target)) confirm(runtime, intent, target);
    }

    private static void confirm(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, PhysicalIntent intent, Target target) {
        SettlementServiceInputIssueObservation receipt = new SettlementServiceInputIssueObservation(
                new PhysicalObservationId("observation:" + intent.id().value().replace(':', '-')), intent.id(), target.work().id(),
                target.work().workerId(), target.item().id(), target.sourceSlot());
        if (!(transitionResult(runtime, intent.id(), PhysicalIntentStatus.CONFIRMED, Optional.of(receipt), "confirmed") instanceof CommandResult.Accepted)) {
            throw new IllegalStateException("service input issue confirmation was rejected");
        }
    }

    private static void unknown(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, PhysicalIntentId id, String phase) {
        transition(runtime, id, PhysicalIntentStatus.UNKNOWN_AFTER_RESTART, Optional.empty(), phase);
    }

    private static boolean transition(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, PhysicalIntentId id, PhysicalIntentStatus status,
                                      Optional<PhysicalEffectObservation> observation, String phase) {
        return transitionResult(runtime, id, status, observation, phase) instanceof CommandResult.Accepted;
    }

    private static CommandResult transitionResult(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, PhysicalIntentId id,
                                                   PhysicalIntentStatus status, Optional<PhysicalEffectObservation> observation, String phase) {
        var checkpoint = runtime.canonicalState().orElseThrow(() -> new IllegalStateException("v3 runtime is inactive"));
        CommandId command = new CommandId("executor:service-input-issue-" + phase + "-r" + checkpoint.revision().value());
        return runtime.submit(new FrontierCommand(1, command, checkpoint.worldId(), checkpoint.revision(), checkpoint.instant(),
                FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(command), new PhysicalIntentTransition(id, status, observation)))
                .orElseThrow(() -> new IllegalStateException("v3 runtime is inactive"));
    }

    record Target(SettlementServiceWork work, ExactItemStack item, InventoryCustody.ContainerSlot sourceSlot, BlockPos chestPosition,
                  SceneLease lease) { }
}
