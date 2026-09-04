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
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Exact medic-held decontamination effect, deliberately separate from the legacy depot endpoint. */
final class FrontierV3SettlementServiceDecontaminationExecutor {
    private FrontierV3SettlementServiceDecontaminationExecutor() { }

    static void tick(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        FrontierWorldState state = runtime.decodedState().orElse(null); if (state == null) return;
        state.physicalIntents().values().stream().sorted(Comparator.comparing(PhysicalIntent::id))
                .filter(intent -> SettlementServiceDecontaminationStateSupport.owns(state, intent))
                .filter(intent -> intent.status() == PhysicalIntentStatus.PREPARED || intent.status() == PhysicalIntentStatus.RUNNING
                        || intent.status() == PhysicalIntentStatus.UNKNOWN_AFTER_RESTART).findFirst()
                .ifPresent(intent -> execute(level, runtime, state, intent));
    }

    private static void execute(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                FrontierWorldState state, PhysicalIntent intent) {
        Target target = target(level, state, intent);
        if (target == null) { unknown(runtime, intent.id(), "canonical-conflict"); return; }
        if (target.markers().stream().anyMatch(position -> !level.hasChunkAt(position))) return;
        if (intent.status() == PhysicalIntentStatus.UNKNOWN_AFTER_RESTART) { inspectRecovered(level, runtime, intent, target); return; }
        if (intent.status() == PhysicalIntentStatus.RUNNING) { inspectRunning(level, runtime, intent, target); return; }
        if (!beforeMatches(level, target) || !FrontierV3CargoHandoffExecutor.exactMatch(target.worker().getItemBySlot(EquipmentSlot.MAINHAND), target.material())) {
            unknown(runtime, intent.id(), "precondition-conflict"); return;
        }
        if (!transition(runtime, intent.id(), PhysicalIntentStatus.RUNNING, Optional.empty(), "running")) return;
        if (!applyOne(level, target)) { unknown(runtime, intent.id(), "effect-conflict"); return; }
        confirm(runtime, intent, target);
    }

    private static Target target(ServerLevel level, FrontierWorldState state, PhysicalIntent intent) {
        try { SettlementServiceDecontaminationStateSupport.validateIntent(state, intent); }
        catch (IllegalArgumentException invalid) { return null; }
        SettlementServiceWork work = state.serviceWorks().get(intent.causeSubjectId());
        ExactItemStack material = state.inventory().items().get(work.inputItemId());
        InfectionCell cell = ((SettlementServiceTarget.Infection) work.target()).cell();
        FrontierV3InfectionOverlayLedger.Claim claim = FrontierV3InfectionOverlayLedger.get(level).claim(cell);
        if (material == null || claim == null || !claim.active()) return null;
        SceneLease lease = state.sceneLeases().values().stream().filter(value -> value.status() == SceneLeaseStatus.HOT)
                .filter(FrontierSceneBehaviors::isServiceWork).filter(value -> FrontierSceneBehaviors.serviceWork(value).workId().equals(work.id()))
                .findFirst().orElse(null);
        if (lease == null) return null;
        SceneMember member = lease.members().stream().filter(value -> value.actorId().equals(work.workerId())).findFirst().orElse(null);
        if (member == null || !(level.getEntity(member.entityId()) instanceof Villager worker) || !worker.isAlive()
                || !FrontierV3SceneExecutor.owned(worker, state, lease, member)
                || !worker.blockPosition().equals(new BlockPos(work.workStation().x(), work.workStation().y() + 1, work.workStation().z()))) return null;
        long prior = state.infection().get(cell).value().raw(); long remaining = Math.max(0L, prior - state.bootstrap().ruleset().rates().decontaminationReduction().raw());
        return new Target(work, cell, claim.blockPositions(), material, worker, prior, remaining, InfectionOverlayStage.fromRaw(prior),
                remaining == 0L ? Optional.empty() : Optional.of(InfectionOverlayStage.fromRaw(remaining)));
    }

    private static boolean beforeMatches(ServerLevel level, Target target) {
        return target.markers().stream().allMatch(position -> level.getBlockState(position).equals(FrontierV3InfectionOverlayExecutor.material(target.priorStage())));
    }

    private static boolean applyOne(ServerLevel level, Target target) {
        if (!beforeMatches(level, target) || !FrontierV3CargoHandoffExecutor.exactMatch(target.worker().getItemBySlot(EquipmentSlot.MAINHAND), target.material())) return false;
        if (!replace(level, target.markers(), target.remainingRaw() == 0L ? Blocks.AIR.defaultBlockState()
                : FrontierV3InfectionOverlayExecutor.material(target.resultStage().orElseThrow()))) return false;
        FrontierV3InfectionOverlayLedger ledger = FrontierV3InfectionOverlayLedger.get(level);
        if (target.remainingRaw() == 0L) ledger.clearedByEffect(target.cell()); else ledger.updateStage(target.cell(), target.resultStage().orElseThrow());
        ItemStack held = target.worker().getItemBySlot(EquipmentSlot.MAINHAND); held.shrink(1); target.worker().setItemSlot(EquipmentSlot.MAINHAND, held);
        return consumed(target.worker().getItemBySlot(EquipmentSlot.MAINHAND), target.material());
    }

    private static void inspectRunning(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, PhysicalIntent intent, Target target) {
        if (consumed(target.worker().getItemBySlot(EquipmentSlot.MAINHAND), target.material()) && afterMatches(level, target)) confirm(runtime, intent, target);
        else unknown(runtime, intent.id(), "restart-postcondition-conflict");
    }

    private static void inspectRecovered(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, PhysicalIntent intent, Target target) {
        if (consumed(target.worker().getItemBySlot(EquipmentSlot.MAINHAND), target.material()) && afterMatches(level, target)) confirm(runtime, intent, target);
    }

    private static boolean afterMatches(ServerLevel level, Target target) {
        if (target.remainingRaw() == 0L) return target.markers().stream().allMatch(position -> level.getBlockState(position).isAir());
        return target.markers().stream().allMatch(position -> level.getBlockState(position).equals(FrontierV3InfectionOverlayExecutor.material(target.resultStage().orElseThrow())));
    }
    private static boolean consumed(ItemStack held, ExactItemStack material) {
        return material.count() == 1 ? held.isEmpty() : held.getCount() == material.count() - 1 && FrontierV3CargoHandoffExecutor.itemId(held).equals(Optional.of(material.id()));
    }
    private static boolean replace(ServerLevel level, List<BlockPos> positions, net.minecraft.world.level.block.state.BlockState next) {
        for (BlockPos position : positions) if (!level.setBlock(position, next, 3)) return false;
        return true;
    }
    private static void confirm(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, PhysicalIntent intent, Target target) {
        DecontaminationObservation observation = new DecontaminationObservation(new PhysicalObservationId("observation:" + intent.id().value().replace(':', '-')),
                intent.id(), target.material().id(), target.cell(), target.priorRaw(), target.remainingRaw());
        if (!transition(runtime, intent.id(), PhysicalIntentStatus.CONFIRMED, Optional.of(observation), "confirmed")) {
            throw new IllegalStateException("service decontamination confirmation was rejected");
        }
    }
    private static void unknown(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, PhysicalIntentId id, String phase) {
        transition(runtime, id, PhysicalIntentStatus.UNKNOWN_AFTER_RESTART, Optional.empty(), phase);
    }
    private static boolean transition(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, PhysicalIntentId id, PhysicalIntentStatus status,
                                      Optional<PhysicalEffectObservation> observation, String phase) {
        var checkpoint = runtime.canonicalState().orElseThrow(() -> new IllegalStateException("v3 runtime is inactive"));
        CommandId command = new CommandId("executor:service-decontamination-" + phase + "-r" + checkpoint.revision().value());
        CommandResult result = runtime.submit(new FrontierCommand(1, command, checkpoint.worldId(), checkpoint.revision(), checkpoint.instant(),
                FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(command), new PhysicalIntentTransition(id, status, observation)))
                .orElseThrow(() -> new IllegalStateException("v3 runtime is inactive"));
        return result instanceof CommandResult.Accepted;
    }

    private record Target(SettlementServiceWork work, InfectionCell cell, List<BlockPos> markers, ExactItemStack material, Villager worker,
                          long priorRaw, long remainingRaw, InfectionOverlayStage priorStage, Optional<InfectionOverlayStage> resultStage) { }
}
