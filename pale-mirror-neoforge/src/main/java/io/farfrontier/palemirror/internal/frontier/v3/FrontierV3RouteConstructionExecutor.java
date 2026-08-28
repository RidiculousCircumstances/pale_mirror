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
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.ContainerSurface;
import io.farfrontier.palemirror.frontier.v3.model.ExactItemStack;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldRuntimeDefinition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.model.FrontierGrayboxPlan;
import io.farfrontier.palemirror.frontier.v3.model.GrayboxCell;
import io.farfrontier.palemirror.frontier.v3.model.GrayboxMaterial;
import io.farfrontier.palemirror.frontier.v3.model.GrayboxSemanticPart;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalEffectObservation;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalIntentTransition;
import io.farfrontier.palemirror.frontier.v3.model.RouteConstruction;
import io.farfrontier.palemirror.frontier.v3.model.RouteConstructionObservation;
import io.farfrontier.palemirror.frontier.v3.model.RouteConstructionStatus;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Builds one inactive replacement-route cell from one exact maintenance-depot item. */
final class FrontierV3RouteConstructionExecutor {
    private FrontierV3RouteConstructionExecutor() { }

    static void tick(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        FrontierWorldState state = state(runtime); if (state == null) return;
        state.physicalIntents().values().stream().sorted(Comparator.comparing(PhysicalIntent::id))
                .filter(intent -> intent.kind() == PhysicalIntentKind.ROUTE_CONSTRUCTION)
                .filter(intent -> intent.status() == PhysicalIntentStatus.PREPARED || intent.status() == PhysicalIntentStatus.RUNNING)
                .findFirst().ifPresent(intent -> execute(level, runtime, state, intent));
    }

    private static void execute(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state, PhysicalIntent intent) {
        Target target = target(state, intent);
        if (target == null || !level.hasChunkAt(target.position()) || !level.hasChunkAt(target.chestPosition())) return;
        ChestBlockEntity chest = FrontierV3CargoHandoffExecutor.activeChest(level,
                new FrontierV3CargoHandoffExecutor.StoreTarget(target.chestPosition(), target.containerId()));
        if (chest == null) { unknown(runtime, intent.id(), "chest-conflict"); return; }
        if (intent.status() == PhysicalIntentStatus.RUNNING) { inspectRunning(level, runtime, intent, target, chest); return; }
        if (!transition(runtime, intent.id(), PhysicalIntentStatus.RUNNING, Optional.empty(), "running")) return;
        if (!applyOne(level, FrontierV3GrayboxLedger.get(level), target.position(), target.cell(), chest, target.slot(), target.material())) {
            unknown(runtime, intent.id(), "precondition-conflict"); return;
        }
        confirm(level, runtime, intent, target);
    }

    private static void inspectRunning(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, PhysicalIntent intent,
                                       Target target, ChestBlockEntity chest) {
        ItemStack stack = chest.getItem(target.slot()); FrontierV3GrayboxLedger.Claim claim = FrontierV3GrayboxLedger.get(level).claim(target.position());
        boolean consumed = target.material().count() == 1 ? stack.isEmpty() : stack.getCount() == target.material().count() - 1
                && FrontierV3CargoHandoffExecutor.itemId(stack).equals(Optional.of(target.material().id()));
        boolean claimed = claim != null && claim.owner().equals(target.cell().ownerId().value()) && claim.material().equals(target.cell().material().name())
                && claim.semanticPart().equals(target.cell().semanticPart().name()) && !claim.conflicted();
        if (consumed && claimed && level.getBlockState(target.position()).equals(FrontierV3GrayboxExecutor.material(target.cell().material()))) confirm(level, runtime, intent, target);
        else unknown(runtime, intent.id(), "restart-postcondition-conflict");
    }

    private static void confirm(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, PhysicalIntent intent, Target target) {
        if (!level.getBlockState(target.position()).equals(FrontierV3GrayboxExecutor.material(target.cell().material()))) {
            unknown(runtime, intent.id(), "postcondition-conflict"); return;
        }
        RouteConstructionObservation observation = new RouteConstructionObservation(new PhysicalObservationId("observation:" + intent.id().value().replace(':', '-')),
                intent.id(), target.project().id(), target.material().id(), new BlockPosition(target.position().getX(), target.position().getY(), target.position().getZ()));
        if (!transition(runtime, intent.id(), PhysicalIntentStatus.CONFIRMED, Optional.of(observation), "confirmed")) {
            throw new IllegalStateException("route construction confirmation was rejected");
        }
    }

    /** One non-replayable placement: a prior graybox claim or any non-air cell remains a conflict. */
    static boolean applyOne(ServerLevel level, FrontierV3GrayboxLedger ledger, BlockPos position, GrayboxCell cell,
                            ChestBlockEntity chest, int slot, ExactItemStack material) {
        ItemStack stack = chest.getItem(slot);
        if (!FrontierV3CargoHandoffExecutor.exactMatch(stack, material) || !level.getBlockState(position).isAir() || ledger.claim(position) != null) return false;
        ledger.ensureCapacityFor(position);
        if (!level.setBlock(position, FrontierV3GrayboxExecutor.material(cell.material()), 3)) return false;
        stack.shrink(1); chest.setItem(slot, stack); chest.setChanged();
        ledger.applied(position, cell.ownerId().value(), cell.material().name(), cell.semanticPart().name());
        return true;
    }

    private static Target target(FrontierWorldState state, PhysicalIntent intent) {
        BlockPosition raw = wholeBlock(intent); if (raw == null) return null;
        RouteConstruction project = intent.subjectIds().stream().map(state.routeConstructions()::get).filter(java.util.Objects::nonNull).findFirst().orElse(null);
        SubjectId itemId = intent.subjectIds().stream().filter(id -> !id.equals(project == null ? null : project.id()) && !id.value().equals("route:frontier-network"))
                .findFirst().orElse(null);
        ExactItemStack material = itemId == null ? null : state.inventory().items().get(itemId);
        if (project == null || project.status() != RouteConstructionStatus.BUILDING || material == null
                || !(material.custody() instanceof io.farfrontier.palemirror.frontier.v3.model.InventoryCustody.ContainerSlot slot)) return null;
        List<BlockPosition> cells = FrontierGrayboxPlan.routeConstructionCells(state, project);
        if (project.confirmedCells() >= cells.size() || !cells.get(project.confirmedCells()).equals(raw)) return null;
        ContainerSurface surface = state.inventory().surfaces().get(slot.containerId());
        if (surface == null) return null;
        return new Target(new BlockPos(raw.x(), raw.y(), raw.z()), new GrayboxCell(raw, new SubjectId("route:frontier-network"), GrayboxMaterial.ROUTE,
                GrayboxSemanticPart.ROUTE_SURFACE), project, material, slot.containerId(), slot.slot(), new BlockPos(surface.position().x(), surface.position().y(), surface.position().z()));
    }

    private static BlockPosition wholeBlock(PhysicalIntent intent) {
        long scale = io.farfrontier.palemirror.frontier.v3.api.FixedScalar.SCALE;
        if (intent.origin().x().raw() % scale != 0L || intent.origin().y().raw() % scale != 0L || intent.origin().z().raw() % scale != 0L) return null;
        return new BlockPosition(Math.toIntExact(intent.origin().x().raw() / scale), Math.toIntExact(intent.origin().y().raw() / scale), Math.toIntExact(intent.origin().z().raw() / scale));
    }
    private static void unknown(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, PhysicalIntentId id, String phase) {
        transition(runtime, id, PhysicalIntentStatus.UNKNOWN_AFTER_RESTART, Optional.empty(), phase);
    }
    private static boolean transition(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, PhysicalIntentId intentId,
                                      PhysicalIntentStatus status, Optional<PhysicalEffectObservation> observation, String phase) {
        CheckpointImage checkpoint = runtime.checkpointImage().orElseThrow(() -> new IllegalStateException("v3 runtime is inactive"));
        CommandId id = new CommandId("executor:route-build-" + phase + "-" + intentId.value().replace(':', '-'));
        CommandResult result = runtime.submit(new FrontierCommand(1, id, checkpoint.worldId(), checkpoint.revision(), checkpoint.instant(),
                FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(id), new PhysicalIntentTransition(intentId, status, observation)))
                .orElseThrow(() -> new IllegalStateException("v3 runtime is inactive"));
        return result instanceof CommandResult.Accepted;
    }
    private static FrontierWorldState state(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        return runtime.checkpointImage().map(image -> new FrontierWorldStateCodec().decode(image.canonicalState())).orElse(null);
    }
    private record Target(BlockPos position, GrayboxCell cell, RouteConstruction project, ExactItemStack material, SubjectId containerId, int slot,
                          BlockPos chestPosition) { }
}
