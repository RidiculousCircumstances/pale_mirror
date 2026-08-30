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
import io.farfrontier.palemirror.frontier.v3.model.RouteConstructionMaterialLoadObservation;
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
                .filter(intent -> intent.kind() == PhysicalIntentKind.ROUTE_CONSTRUCTION_MATERIAL_LOADING)
                .filter(intent -> intent.status() == PhysicalIntentStatus.PREPARED || intent.status() == PhysicalIntentStatus.RUNNING)
                .findFirst().ifPresentOrElse(intent -> loadMaterial(level, runtime, state, intent), () -> state.physicalIntents().values().stream().sorted(Comparator.comparing(PhysicalIntent::id))
                .filter(intent -> intent.kind() == PhysicalIntentKind.ROUTE_CONSTRUCTION)
                .filter(intent -> intent.status() == PhysicalIntentStatus.PREPARED || intent.status() == PhysicalIntentStatus.RUNNING)
                .findFirst().ifPresent(intent -> execute(level, runtime, state, intent)));
    }

    private static void execute(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state, PhysicalIntent intent) {
        Target target = target(state, intent);
        if (target == null || !level.hasChunkAt(target.position())) return;
        if (intent.status() == PhysicalIntentStatus.RUNNING) { inspectRunning(level, runtime, intent, target); return; }
        if (!transition(runtime, intent.id(), PhysicalIntentStatus.RUNNING, Optional.empty(), "running")) return;
        if (!applyOne(level, FrontierV3GrayboxLedger.get(level), target.position(), target.cell())) {
            unknown(runtime, intent.id(), "precondition-conflict"); return;
        }
        confirm(level, runtime, intent, target);
    }

    private static void inspectRunning(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, PhysicalIntent intent, Target target) {
        FrontierV3GrayboxLedger.Claim claim = FrontierV3GrayboxLedger.get(level).claim(target.position());
        boolean claimed = claim != null && claim.owner().equals(target.cell().ownerId().value()) && claim.material().equals(target.cell().material().name())
                && claim.semanticPart().equals(target.cell().semanticPart().name()) && !claim.conflicted();
        if (claimed && level.getBlockState(target.position()).equals(FrontierV3GrayboxExecutor.material(target.cell().material()))) confirm(level, runtime, intent, target);
        else unknown(runtime, intent.id(), "restart-postcondition-conflict");
    }

    private static void confirm(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, PhysicalIntent intent, Target target) {
        if (!level.getBlockState(target.position()).equals(FrontierV3GrayboxExecutor.material(target.cell().material()))) {
            unknown(runtime, intent.id(), "postcondition-conflict"); return;
        }
        RouteConstructionObservation observation = new RouteConstructionObservation(new PhysicalObservationId("observation:" + intent.id().value().replace(':', '-')),
                intent.id(), target.project().id(), target.material().id(), new BlockPosition(target.position().getX(), target.position().getY(), target.position().getZ()));
        CommandResult result = transitionResult(runtime, intent.id(), PhysicalIntentStatus.CONFIRMED, Optional.of(observation), "confirmed");
        if (!(result instanceof CommandResult.Accepted)) {
            throw new IllegalStateException("route construction confirmation was rejected");
        }
        FrontierV3DiagnosticTrace.record(level.getServer(), FrontierV3DiagnosticTrace.routeConstructionCorrelation(target.project().id()),
                "route_construction_cell_built", target.project().id(), result);
    }

    /** One non-replayable placement: a prior graybox claim or any non-air cell remains a conflict. */
    static boolean applyOne(ServerLevel level, FrontierV3GrayboxLedger ledger, BlockPos position, GrayboxCell cell) {
        if (!level.getBlockState(position).isAir() || ledger.claim(position) != null) return false;
        ledger.ensureCapacityFor(position);
        if (!level.setBlock(position, FrontierV3GrayboxExecutor.material(cell.material()), 3)) return false;
        ledger.applied(position, cell.ownerId().value(), cell.material().name(), cell.semanticPart().name());
        return true;
    }

    /** One physical source-stack unit leaves the loaded maintenance chest; its ID remains on the real remainder. */
    static boolean extractOne(ChestBlockEntity chest, int slot, ExactItemStack source) {
        if (chest == null || slot < 0 || slot >= chest.getContainerSize() || !FrontierV3CargoHandoffExecutor.exactMatch(chest.getItem(slot), source)) return false;
        ItemStack remainder = chest.getItem(slot).copy(); remainder.shrink(1); chest.setItem(slot, remainder); chest.setChanged();
        return chest.getItem(slot).getCount() == source.count() - 1
                && (chest.getItem(slot).isEmpty() || FrontierV3CargoHandoffExecutor.itemId(chest.getItem(slot)).equals(Optional.of(source.id())));
    }

    private static Target target(FrontierWorldState state, PhysicalIntent intent) {
        BlockPosition raw = wholeBlock(intent); if (raw == null) return null;
        RouteConstruction project = intent.subjectIds().stream().map(state.routeConstructions()::get).filter(java.util.Objects::nonNull).findFirst().orElse(null);
        SubjectId cargoId = project == null ? null : project.cargoId().orElse(null);
        SubjectId itemId = intent.subjectIds().stream().filter(id -> !id.equals(project == null ? null : project.id()) && !id.equals(cargoId) && !id.value().equals("route:frontier-network"))
                .findFirst().orElse(null);
        ExactItemStack material = itemId == null ? null : state.inventory().items().get(itemId);
        if (project == null || project.status() != RouteConstructionStatus.BUILDING || material == null
                || cargoId == null || !material.custody().equals(new io.farfrontier.palemirror.frontier.v3.model.InventoryCustody.Cargo(cargoId))) return null;
        List<BlockPosition> cells = FrontierGrayboxPlan.routeConstructionCells(state, project);
        if (project.confirmedCells() >= cells.size() || !cells.get(project.confirmedCells()).equals(raw)) return null;
        return new Target(new BlockPos(raw.x(), raw.y(), raw.z()), new GrayboxCell(raw, new SubjectId("route:frontier-network"), GrayboxMaterial.ROUTE,
                GrayboxSemanticPart.ROUTE_SURFACE), project, material);
    }

    /**
     * Removes one exact source-stack unit while its chest is naturally loaded;
     * later work uses only the separately persisted COLD cargo.
     */
    private static void loadMaterial(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                     FrontierWorldState state, PhysicalIntent intent) {
        MaterialTarget target = materialTarget(state, intent); if (target == null || !level.hasChunkAt(target.chestPosition())) return;
        ChestBlockEntity chest = FrontierV3CargoHandoffExecutor.activeChest(level,
                new FrontierV3CargoHandoffExecutor.StoreTarget(target.chestPosition(), target.containerId()));
        if (chest == null) { unknown(runtime, intent.id(), "material-chest-conflict"); return; }
        if (intent.status() == PhysicalIntentStatus.RUNNING) { inspectMaterialLoading(level, runtime, intent, target, chest); return; }
        if (!FrontierV3CargoHandoffExecutor.exactMatch(chest.getItem(target.slot()), target.sourceMaterial())) {
            unknown(runtime, intent.id(), "material-stack-conflict"); return;
        }
        if (!transition(runtime, intent.id(), PhysicalIntentStatus.RUNNING, Optional.empty(), "material-running")) return;
        if (!extractOne(chest, target.slot(), target.sourceMaterial())) { unknown(runtime, intent.id(), "material-removal-conflict"); return; }
        confirmMaterialLoading(level, runtime, intent, target);
    }

    private static void inspectMaterialLoading(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, PhysicalIntent intent,
                                               MaterialTarget target, ChestBlockEntity chest) {
        ItemStack stack = chest.getItem(target.slot());
        if (stack.getCount() == target.sourceRemainingCount() && (stack.isEmpty() || FrontierV3CargoHandoffExecutor.itemId(stack).equals(Optional.of(target.sourceMaterial().id())))) {
            confirmMaterialLoading(level, runtime, intent, target);
        }
        else unknown(runtime, intent.id(), "material-restart-postcondition-conflict");
    }

    private static void confirmMaterialLoading(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, PhysicalIntent intent, MaterialTarget target) {
        RouteConstructionMaterialLoadObservation observation = new RouteConstructionMaterialLoadObservation(
                new PhysicalObservationId("observation:" + intent.id().value().replace(':', '-')), intent.id(), target.project().id(),
                target.cargoId(), target.sourceMaterial().id(), target.cargoMaterialId(), target.sourceRemainingCount());
        CommandResult result = transitionResult(runtime, intent.id(), PhysicalIntentStatus.CONFIRMED, Optional.of(observation), "material-confirmed");
        if (!(result instanceof CommandResult.Accepted)) {
            CommandResult.Rejected rejected = (CommandResult.Rejected) result;
            throw new IllegalStateException("route construction material loading confirmation was rejected: " + rejected.rejection().detail());
        }
        FrontierV3DiagnosticTrace.record(level.getServer(), FrontierV3DiagnosticTrace.routeConstructionCorrelation(target.project().id()),
                "route_construction_material_loaded", target.project().id(), result);
    }

    private static MaterialTarget materialTarget(FrontierWorldState state, PhysicalIntent intent) {
        BlockPosition origin = wholeBlock(intent); if (origin == null) return null;
        RouteConstruction project = state.routeConstructions().get(intent.causeSubjectId());
        if (project == null || project.status() != RouteConstructionStatus.BUILDING || project.cargoId().isPresent() || intent.subjectIds().size() != 5) return null;
        SubjectId cargoId = intent.subjectIds().get(2), cargoMaterialId = intent.subjectIds().get(3), itemId = intent.subjectIds().get(4); ExactItemStack material = state.inventory().items().get(itemId);
        if (material == null || !intent.subjectIds().getFirst().equals(material.economicOwnerId()) || !intent.subjectIds().get(1).equals(project.id())
                || !(material.custody() instanceof io.farfrontier.palemirror.frontier.v3.model.InventoryCustody.ContainerSlot slot)
                || !slot.containerId().equals(new SubjectId("container:frontier-route-maintenance"))) return null;
        ContainerSurface surface = state.inventory().surfaces().get(slot.containerId());
        if (surface == null || surface.status() != io.farfrontier.palemirror.frontier.v3.model.ContainerSurfaceStatus.ACTIVE
                || !surface.position().equals(origin)) return null;
        return new MaterialTarget(project, cargoId, cargoMaterialId, material, slot.containerId(), slot.slot(), material.count() - 1,
                new BlockPos(surface.position().x(), surface.position().y(), surface.position().z()));
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
        return transitionResult(runtime, intentId, status, observation, phase) instanceof CommandResult.Accepted;
    }
    private static CommandResult transitionResult(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, PhysicalIntentId intentId,
                                                   PhysicalIntentStatus status, Optional<PhysicalEffectObservation> observation, String phase) {
        CheckpointImage checkpoint = runtime.checkpointImage().orElseThrow(() -> new IllegalStateException("v3 runtime is inactive"));
        CommandId id = new CommandId("executor:route-build-" + phase + "-" + intentId.value().replace(':', '-'));
        return runtime.submit(new FrontierCommand(1, id, checkpoint.worldId(), checkpoint.revision(), checkpoint.instant(),
                FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(id), new PhysicalIntentTransition(intentId, status, observation)))
                .orElseThrow(() -> new IllegalStateException("v3 runtime is inactive"));
    }
    private static FrontierWorldState state(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        return runtime.decodedState().orElse(null);
    }
    private record Target(BlockPos position, GrayboxCell cell, RouteConstruction project, ExactItemStack material) { }
    private record MaterialTarget(RouteConstruction project, SubjectId cargoId, SubjectId cargoMaterialId, ExactItemStack sourceMaterial,
                                  SubjectId containerId, int slot, int sourceRemainingCount, BlockPos chestPosition) { }
}
