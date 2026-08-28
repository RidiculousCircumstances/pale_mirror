package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.CauseChain;
import io.farfrontier.palemirror.frontier.v3.api.CheckpointImage;
import io.farfrontier.palemirror.frontier.v3.api.CommandId;
import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalPostcondition;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.ContainerSurface;
import io.farfrontier.palemirror.frontier.v3.model.DecontaminationObservation;
import io.farfrontier.palemirror.frontier.v3.model.DecontaminationPolicy;
import io.farfrontier.palemirror.frontier.v3.model.ExactItemStack;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldRuntimeDefinition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.model.InfectionCell;
import io.farfrontier.palemirror.frontier.v3.model.InfectionOverlayStage;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalEffectObservation;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalIntentTransition;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

import java.util.Comparator;
import java.util.Optional;

/** Executes one exact reagent effect on an already-owned loaded infection overlay marker. */
final class FrontierV3DecontaminationExecutor {
    private FrontierV3DecontaminationExecutor() { }

    static void tick(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        FrontierWorldState state = state(runtime); if (state == null) return;
        state.physicalIntents().values().stream().sorted(Comparator.comparing(PhysicalIntent::id)).filter(intent -> intent.kind() == PhysicalIntentKind.DECONTAMINATION)
                .filter(intent -> intent.status() == PhysicalIntentStatus.PREPARED || intent.status() == PhysicalIntentStatus.RUNNING).findFirst()
                .ifPresent(intent -> execute(level, runtime, state, intent));
    }

    private static void execute(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state, PhysicalIntent intent) {
        Target target = target(state, intent, FrontierV3InfectionOverlayLedger.get(level));
        if (target == null) {
            if (intent.status() == PhysicalIntentStatus.RUNNING) unknown(runtime, intent.id(), "restart-target-conflict");
            return;
        }
        if (!level.hasChunkAt(target.marker()) || !level.hasChunkAt(target.chestPosition())) return;
        ChestBlockEntity chest = FrontierV3CargoHandoffExecutor.activeChest(level, new FrontierV3CargoHandoffExecutor.StoreTarget(target.chestPosition(), target.containerId()));
        if (chest == null) { unknown(runtime, intent.id(), "chest-conflict"); return; }
        if (intent.status() == PhysicalIntentStatus.RUNNING) { inspectRunning(level, runtime, intent, target, chest); return; }
        if (!transition(runtime, intent.id(), PhysicalIntentStatus.RUNNING, Optional.empty(), "running")) return;
        if (!applyOne(level, FrontierV3InfectionOverlayLedger.get(level), target, chest)) { unknown(runtime, intent.id(), "precondition-conflict"); return; }
        confirm(level, runtime, intent, target);
    }

    private static void inspectRunning(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, PhysicalIntent intent, Target target, ChestBlockEntity chest) {
        ItemStack stack = chest.getItem(target.slot()); FrontierV3InfectionOverlayLedger ledger = FrontierV3InfectionOverlayLedger.get(level);
        if (consumed(stack, target) && recoverLedgerPostcondition(level, ledger, target)) confirm(level, runtime, intent, target);
        else unknown(runtime, intent.id(), "restart-postcondition-conflict");
    }

    private static boolean consumed(ItemStack stack, Target target) {
        return target.material().count() == 1 ? stack.isEmpty() : stack.getCount() == target.material().count() - 1
                && FrontierV3CargoHandoffExecutor.itemId(stack).equals(Optional.of(target.material().id()));
    }

    /** Rebuilds only our own SavedData claim from an already-measured completed effect after a crash. */
    static boolean recoverLedgerPostcondition(ServerLevel level, FrontierV3InfectionOverlayLedger ledger, Target target) {
        FrontierV3InfectionOverlayLedger.Claim claim = ledger.claim(target.cell());
        if (claim == null || claim.conflicted()) return false;
        if (target.remainingRaw() == 0L) {
            if (!level.getBlockState(target.marker()).isAir()) return false;
            if (!claim.cleared()) ledger.clearedByEffect(target.cell());
            return true;
        }
        InfectionOverlayStage result = target.resultStage().orElseThrow();
        if (!level.getBlockState(target.marker()).equals(FrontierV3InfectionOverlayExecutor.material(result))) return false;
        ledger.updateStage(target.cell(), result); return true;
    }

    private static void confirm(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, PhysicalIntent intent, Target target) {
        DecontaminationObservation observation = new DecontaminationObservation(new PhysicalObservationId("observation:" + intent.id().value().replace(':', '-')),
                intent.id(), target.material().id(), target.cell(), target.priorRaw(), target.remainingRaw());
        if (!transition(runtime, intent.id(), PhysicalIntentStatus.CONFIRMED, Optional.of(observation), "confirmed")) {
            throw new IllegalStateException("decontamination confirmation was rejected");
        }
    }

    static boolean applyOne(ServerLevel level, FrontierV3InfectionOverlayLedger ledger, Target target, ChestBlockEntity chest) {
        FrontierV3InfectionOverlayLedger.Claim claim = ledger.claim(target.cell()); ItemStack stack = chest.getItem(target.slot());
        if (claim == null || claim.conflicted() || claim.cleared() || claim.stage() != target.priorStage()
                || !level.getBlockState(target.marker()).equals(FrontierV3InfectionOverlayExecutor.material(target.priorStage()))
                || !FrontierV3CargoHandoffExecutor.exactMatch(stack, target.material())) return false;
        if (target.remainingRaw() == 0L) {
            if (!level.setBlock(target.marker(), Blocks.AIR.defaultBlockState(), 3) || !level.getBlockState(target.marker()).isAir()) return false;
            ledger.clearedByEffect(target.cell());
        } else {
            InfectionOverlayStage result = target.resultStage().orElseThrow();
            if (!level.setBlock(target.marker(), FrontierV3InfectionOverlayExecutor.material(result), 3)
                    || !level.getBlockState(target.marker()).equals(FrontierV3InfectionOverlayExecutor.material(result))) return false;
            ledger.updateStage(target.cell(), result);
        }
        stack.shrink(1); chest.setItem(target.slot(), stack); chest.setChanged(); return true;
    }

    private static Target target(FrontierWorldState state, PhysicalIntent intent, FrontierV3InfectionOverlayLedger ledger) {
        InfectionCell cell = cell(intent); if (intent.postcondition() != PhysicalPostcondition.DECONTAMINATION_OBSERVED || cell == null
                || intent.subjectIds().size() != 2 || !intent.subjectIds().contains(intent.causeSubjectId())) return null;
        SubjectId itemId = intent.subjectIds().stream().filter(id -> !id.equals(intent.causeSubjectId())).findFirst().orElse(null);
        ExactItemStack material = itemId == null ? null : state.inventory().items().get(itemId); Long prior = state.infection().get(cell) == null ? null : state.infection().get(cell).value().raw();
        if (material == null || !material.itemKind().equals(DecontaminationPolicy.REAGENT) || prior == null || !(material.custody() instanceof io.farfrontier.palemirror.frontier.v3.model.InventoryCustody.ContainerSlot slot)) return null;
        ContainerSurface surface = state.inventory().surfaces().get(slot.containerId()); FrontierV3InfectionOverlayLedger.Claim claim = ledger.claim(cell);
        if (surface == null || surface.status() != io.farfrontier.palemirror.frontier.v3.model.ContainerSurfaceStatus.ACTIVE
                || claim == null || claim.conflicted()) return null;
        long remaining = Math.max(0L, prior - DecontaminationPolicy.REDUCTION_RAW); InfectionOverlayStage before = InfectionOverlayStage.fromRaw(prior);
        return new Target(cell, BlockPos.of(claim.position()), material, slot.containerId(), slot.slot(), new BlockPos(surface.position().x(), surface.position().y(), surface.position().z()),
                prior, remaining, before, remaining == 0L ? Optional.empty() : Optional.of(InfectionOverlayStage.fromRaw(remaining)));
    }

    private static InfectionCell cell(PhysicalIntent intent) {
        long scale = FixedScalar.SCALE;
        if (intent.origin().x().raw() % scale != 0L || intent.origin().y().raw() != 0L || intent.origin().z().raw() % scale != 0L) return null;
        BlockPosition position = new BlockPosition(Math.toIntExact(intent.origin().x().raw() / scale), 0, Math.toIntExact(intent.origin().z().raw() / scale));
        InfectionCell cell = InfectionCell.at(position); return cell.originAtY(0).equals(position) ? cell : null;
    }

    private static void unknown(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, PhysicalIntentId id, String phase) {
        transition(runtime, id, PhysicalIntentStatus.UNKNOWN_AFTER_RESTART, Optional.empty(), phase);
    }

    private static boolean transition(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, PhysicalIntentId id, PhysicalIntentStatus status,
                                      Optional<PhysicalEffectObservation> observation, String phase) {
        CheckpointImage checkpoint = runtime.checkpointImage().orElseThrow(() -> new IllegalStateException("v3 runtime is inactive"));
        CommandId command = new CommandId("executor:decontamination-" + phase + "-" + id.value().replace(':', '-'));
        CommandResult result = runtime.submit(new FrontierCommand(1, command, checkpoint.worldId(), checkpoint.revision(), checkpoint.instant(),
                FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(command), new PhysicalIntentTransition(id, status, observation)))
                .orElseThrow(() -> new IllegalStateException("v3 runtime is inactive"));
        return result instanceof CommandResult.Accepted;
    }

    private static FrontierWorldState state(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        return runtime.decodedState().orElse(null);
    }

    record Target(InfectionCell cell, BlockPos marker, ExactItemStack material, SubjectId containerId, int slot, BlockPos chestPosition,
                  long priorRaw, long remainingRaw, InfectionOverlayStage priorStage, Optional<InfectionOverlayStage> resultStage) { }
}
