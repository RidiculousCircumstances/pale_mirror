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
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.ContainerSurface;
import io.farfrontier.palemirror.frontier.v3.model.ContainerSurfaceStatus;
import io.farfrontier.palemirror.frontier.v3.model.ExactItemStack;
import io.farfrontier.palemirror.frontier.v3.model.FrontierGrayboxPlan;
import io.farfrontier.palemirror.frontier.v3.model.FrontierEngineeringWorkSceneSupport;
import io.farfrontier.palemirror.frontier.v3.model.FrontierRouteNetwork;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.GrayboxCell;
import io.farfrontier.palemirror.frontier.v3.model.InventoryCustody;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalEffectObservation;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalIntentTransition;
import io.farfrontier.palemirror.frontier.v3.model.RouteMaintenance;
import io.farfrontier.palemirror.frontier.v3.model.RouteMaintenanceMaterialLoadObservation;
import io.farfrontier.palemirror.frontier.v3.model.RouteMaintenanceObservation;
import io.farfrontier.palemirror.frontier.v3.model.RouteMaintenanceStateSupport;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

import java.util.List;
import java.util.Optional;

/** Repairs one retained route-loss cell from one separately observed maintenance-depot unit. */
final class FrontierV3RouteMaintenanceExecutor {
    private FrontierV3RouteMaintenanceExecutor() { }

    static void tick(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        FrontierWorldState state = runtime.decodedState().orElse(null); if (state == null) return;
        // A remote maintenance depot is allowed to be COLD.  Its naturally unloaded pickup
        // is a deferral, never a family-wide lock that prevents a different loaded repair from
        // consuming its own exact source/target pair.  Invalid entries still remain selected so
        // the owner records visible UNKNOWN/conflict evidence rather than hiding corruption.
        Optional<PhysicalIntent> material = FrontierV3PhysicalIntentScheduling.firstActionable(materialIntents(state),
                intent -> materialReadiness(level, state, intent));
        if (material.isPresent()) { loadMaterial(level, runtime, state, material.orElseThrow()); return; }
        FrontierV3PhysicalIntentScheduling.firstActionable(workIntents(state), intent -> workReadiness(level, state, intent))
                .ifPresent(intent -> execute(level, runtime, state, intent));
    }

    private static boolean pending(PhysicalIntent intent) {
        return intent.status() == PhysicalIntentStatus.PREPARED || intent.status() == PhysicalIntentStatus.RUNNING;
    }

    static List<PhysicalIntent> materialIntents(FrontierWorldState state) {
        return state.physicalIntents().values().stream()
                .filter(intent -> intent.kind() == PhysicalIntentKind.ROUTE_MAINTENANCE_MATERIAL_LOADING)
                .filter(FrontierV3RouteMaintenanceExecutor::pending).toList();
    }

    static List<PhysicalIntent> workIntents(FrontierWorldState state) {
        return state.physicalIntents().values().stream().filter(intent -> intent.kind() == PhysicalIntentKind.ROUTE_MAINTENANCE)
                .filter(FrontierV3RouteMaintenanceExecutor::pending).toList();
    }

    private static FrontierV3PhysicalIntentScheduling.Readiness materialReadiness(ServerLevel level, FrontierWorldState state, PhysicalIntent intent) {
        BlockPosition origin = wholeBlock(intent);
        if (origin == null || materialTarget(state, intent) == null) return FrontierV3PhysicalIntentScheduling.Readiness.INVALID;
        BlockPos position = new BlockPos(origin.x(), origin.y(), origin.z());
        if (!level.hasChunkAt(position)) return FrontierV3PhysicalIntentScheduling.Readiness.DEFERRED;
        return FrontierV3PhysicalIntentScheduling.Readiness.RUNNABLE;
    }

    private static FrontierV3PhysicalIntentScheduling.Readiness workReadiness(ServerLevel level, FrontierWorldState state, PhysicalIntent intent) {
        BlockPosition origin = wholeBlock(intent);
        if (origin == null || target(state, intent) == null) return FrontierV3PhysicalIntentScheduling.Readiness.INVALID;
        BlockPos position = new BlockPos(origin.x(), origin.y(), origin.z());
        if (!FrontierV3PhysicalDemand.exists(level, position)) {
            return FrontierV3PhysicalIntentScheduling.Readiness.DEFERRED;
        }
        if (!FrontierEngineeringWorkSceneSupport.permitsCurrentWorkIntent(state, intent)) {
            return FrontierV3PhysicalIntentScheduling.Readiness.DEFERRED;
        }
        FrontierV3GrayboxLedger.Claim claim = FrontierV3GrayboxLedger.get(level).claim(position);
        // A COLD loss can predate the first ordinary visit. Its provenance tombstone is written
        // by the independent projector on that visit; empty air before that write is a bounded
        // projection delay, not a failed repair.
        if (claim == null && level.getBlockState(position).isAir()) return FrontierV3PhysicalIntentScheduling.Readiness.DEFERRED;
        return FrontierV3PhysicalIntentScheduling.Readiness.RUNNABLE;
    }

    private static void execute(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state, PhysicalIntent intent) {
        // Admission only proves that this work intent was prepared while the exact crew owned a
        // HOT worksite.  A graceful restart can retain that unstarted intent while the scene is
        // UNKNOWN_AFTER_RESTART.  Do not let a newly loaded target spend the cargo before the
        // same lease/body set has reclaimed HOT authority.
        if (!FrontierEngineeringWorkSceneSupport.permitsCurrentWorkIntent(state, intent)) return;
        BlockPosition raw = wholeBlock(intent);
        if (raw == null) { unknown(runtime, intent.id(), "canonical-origin-conflict"); return; }
        if (!FrontierV3PhysicalDemand.exists(level, new BlockPos(raw.x(), raw.y(), raw.z()))) return;
        Target target = target(state, intent);
        if (target == null) { unknown(runtime, intent.id(), "canonical-target-conflict"); return; }
        if (intent.status() == PhysicalIntentStatus.RUNNING) { inspectRunning(level, runtime, intent, target); return; }
        if (!transition(runtime, intent.id(), PhysicalIntentStatus.RUNNING, Optional.empty(), "running")) return;
        if (!repairOne(level, FrontierV3GrayboxLedger.get(level), target.position(), target.cell())) {
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
        RouteMaintenanceObservation observation = new RouteMaintenanceObservation(observationId(intent), intent.id(), target.maintenance().id(),
                target.material().id(), new BlockPosition(target.position().getX(), target.position().getY(), target.position().getZ()));
        CommandResult result = transitionResult(runtime, intent.id(), PhysicalIntentStatus.CONFIRMED, Optional.of(observation), "confirmed");
        if (!(result instanceof CommandResult.Accepted)) throw new IllegalStateException("route maintenance confirmation was rejected");
        FrontierV3DiagnosticTrace.record(level.getServer(), correlation(target.maintenance()), "route_maintenance_cell_repaired", target.maintenance().id(), result);
    }

    private static Target target(FrontierWorldState state, PhysicalIntent intent) {
        BlockPosition raw = wholeBlock(intent); if (raw == null) return null;
        RouteMaintenance maintenance = RouteMaintenanceStateSupport.workOperation(state, intent);
        if (maintenance == null || !maintenance.building() || !maintenance.repairCell().equals(raw) || maintenance.cargoId().isEmpty()) return null;
        SubjectId cargoId = maintenance.cargoId().orElseThrow();
        SubjectId itemId = intent.subjectIds().stream().filter(id -> !id.equals(FrontierRouteNetwork.OWNER) && !id.equals(maintenance.id()) && !id.equals(cargoId))
                .findFirst().orElse(null);
        ExactItemStack material = itemId == null ? null : state.inventory().items().get(itemId);
        if (material == null || !material.custody().equals(new InventoryCustody.Cargo(cargoId))
                || FrontierGrayboxPlan.intactSemanticCell(state.bootstrap(), state.hiveColony(), state.routeTopology(), state.routeConstructions(),
                FrontierRouteNetwork.OWNER, raw) == null) return null;
        return new Target(new BlockPos(raw.x(), raw.y(), raw.z()), new GrayboxCell(raw, FrontierRouteNetwork.OWNER, maintenance.expectedMaterial(),
                maintenance.semanticPart()), maintenance, material);
    }

    /**
     * Restores only a known PM-owned loss.  Unlike route construction, maintenance must retain
     * the original provenance claim while the cell is damaged; accepting a fresh/unclaimed cell
     * here would turn a repair receipt into permission to adopt arbitrary world geometry.
     */
    static boolean repairOne(ServerLevel level, FrontierV3GrayboxLedger ledger, BlockPos position, GrayboxCell cell) {
        FrontierV3GrayboxLedger.Claim claim = ledger.claim(position);
        if (claim == null || !claim.conflicted() || !claim.owner().equals(cell.ownerId().value())
                || !claim.material().equals(cell.material().name()) || !claim.semanticPart().equals(cell.semanticPart().name())
                || !level.getBlockState(position).isAir()) return false;
        if (!level.setBlock(position, FrontierV3GrayboxExecutor.material(cell.material()), 3)) return false;
        ledger.repaired(position, cell.ownerId().value(), cell.material().name(), cell.semanticPart().name());
        return true;
    }

    private static void loadMaterial(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state, PhysicalIntent intent) {
        BlockPosition origin = wholeBlock(intent);
        if (origin == null) { unknown(runtime, intent.id(), "material-canonical-origin-conflict"); return; }
        if (!FrontierV3PhysicalDemand.exists(level, new BlockPos(origin.x(), origin.y(), origin.z()))) return;
        MaterialTarget target = materialTarget(state, intent);
        if (target == null) { unknown(runtime, intent.id(), "material-canonical-target-conflict"); return; }
        ChestBlockEntity chest = FrontierV3CargoHandoffExecutor.activeChest(level,
                new FrontierV3CargoHandoffExecutor.StoreTarget(target.chestPosition(), target.containerId()));
        if (chest == null) { unknown(runtime, intent.id(), "material-chest-conflict"); return; }
        if (intent.status() == PhysicalIntentStatus.RUNNING) { inspectMaterialLoading(level, runtime, intent, target, chest); return; }
        if (!FrontierV3CargoHandoffExecutor.exactMatch(chest.getItem(target.slot()), target.sourceMaterial())) {
            unknown(runtime, intent.id(), "material-stack-conflict"); return;
        }
        if (!transition(runtime, intent.id(), PhysicalIntentStatus.RUNNING, Optional.empty(), "material-running")) return;
        if (!FrontierV3RouteConstructionExecutor.extractOne(chest, target.slot(), target.sourceMaterial())) {
            unknown(runtime, intent.id(), "material-removal-conflict"); return;
        }
        confirmMaterialLoading(level, runtime, intent, target);
    }

    private static void inspectMaterialLoading(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, PhysicalIntent intent,
                                               MaterialTarget target, ChestBlockEntity chest) {
        ItemStack stack = chest.getItem(target.slot());
        if (stack.getCount() == target.sourceRemainingCount() && (stack.isEmpty()
                || FrontierV3CargoHandoffExecutor.itemId(stack).equals(Optional.of(target.sourceMaterial().id())))) {
            confirmMaterialLoading(level, runtime, intent, target);
        } else unknown(runtime, intent.id(), "material-restart-postcondition-conflict");
    }

    private static void confirmMaterialLoading(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                               PhysicalIntent intent, MaterialTarget target) {
        RouteMaintenanceMaterialLoadObservation observation = new RouteMaintenanceMaterialLoadObservation(observationId(intent), intent.id(),
                target.maintenance().id(), target.cargoId(), target.sourceMaterial().id(), target.cargoMaterialId(), target.sourceRemainingCount());
        CommandResult result = transitionResult(runtime, intent.id(), PhysicalIntentStatus.CONFIRMED, Optional.of(observation), "material-confirmed");
        if (!(result instanceof CommandResult.Accepted)) throw new IllegalStateException("route maintenance material confirmation was rejected: "
                + ((CommandResult.Rejected) result).rejection().code() + " " + ((CommandResult.Rejected) result).rejection().detail());
        FrontierV3DiagnosticTrace.record(level.getServer(), correlation(target.maintenance()), "route_maintenance_material_loaded", target.maintenance().id(), result);
    }

    private static MaterialTarget materialTarget(FrontierWorldState state, PhysicalIntent intent) {
        BlockPosition origin = wholeBlock(intent); if (origin == null) return null;
        RouteMaintenance maintenance = state.routeMaintenances().get(intent.causeSubjectId());
        if (maintenance == null || !maintenance.building() || maintenance.cargoId().isPresent() || intent.subjectIds().size() != 5) return null;
        SubjectId cargoId = intent.subjectIds().get(2), cargoMaterialId = intent.subjectIds().get(3), sourceId = intent.subjectIds().get(4);
        ExactItemStack material = state.inventory().items().get(sourceId);
        if (material == null || !intent.subjectIds().getFirst().equals(FrontierRouteNetwork.OWNER) || !intent.subjectIds().get(1).equals(maintenance.id())
                || !(material.custody() instanceof InventoryCustody.ContainerSlot slot) || !slot.containerId().equals(FrontierRouteNetwork.MAINTENANCE_CONTAINER)) return null;
        ContainerSurface surface = state.inventory().surfaces().get(slot.containerId());
        if (surface == null || surface.status() != ContainerSurfaceStatus.ACTIVE || !surface.position().equals(origin)) return null;
        return new MaterialTarget(maintenance, cargoId, cargoMaterialId, material, slot.containerId(), slot.slot(), material.count() - 1,
                new BlockPos(surface.position().x(), surface.position().y(), surface.position().z()));
    }

    private static BlockPosition wholeBlock(PhysicalIntent intent) {
        long scale = io.farfrontier.palemirror.frontier.v3.api.FixedScalar.SCALE;
        if (intent.origin().x().raw() % scale != 0L || intent.origin().y().raw() % scale != 0L || intent.origin().z().raw() % scale != 0L) return null;
        return new BlockPosition(Math.toIntExact(intent.origin().x().raw() / scale), Math.toIntExact(intent.origin().y().raw() / scale),
                Math.toIntExact(intent.origin().z().raw() / scale));
    }

    private static PhysicalObservationId observationId(PhysicalIntent intent) {
        return new PhysicalObservationId("observation:" + intent.id().value().replace(':', '-'));
    }
    private static String correlation(RouteMaintenance maintenance) { return FrontierV3DiagnosticTrace.routeMaintenanceCorrelation(maintenance.id()); }
    private static void unknown(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, PhysicalIntentId id, String phase) {
        transition(runtime, id, PhysicalIntentStatus.UNKNOWN_AFTER_RESTART, Optional.empty(), phase);
    }
    private static boolean transition(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, PhysicalIntentId id, PhysicalIntentStatus status,
                                      Optional<PhysicalEffectObservation> observation, String phase) {
        return transitionResult(runtime, id, status, observation, phase) instanceof CommandResult.Accepted;
    }
    private static CommandResult transitionResult(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, PhysicalIntentId intentId,
                                                   PhysicalIntentStatus status, Optional<PhysicalEffectObservation> observation, String phase) {
        var checkpoint = runtime.canonicalState().orElseThrow(() -> new IllegalStateException("v3 runtime is inactive"));
        CommandId id = new CommandId("executor:route-maintenance-" + phase + "-" + intentId.value().replace(':', '-'));
        return runtime.submit(new FrontierCommand(1, id, checkpoint.worldId(), checkpoint.revision(), checkpoint.instant(),
                FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(id), new PhysicalIntentTransition(intentId, status, observation)))
                .orElseThrow(() -> new IllegalStateException("v3 runtime is inactive"));
    }

    private record Target(BlockPos position, GrayboxCell cell, RouteMaintenance maintenance, ExactItemStack material) { }
    private record MaterialTarget(RouteMaintenance maintenance, SubjectId cargoId, SubjectId cargoMaterialId, ExactItemStack sourceMaterial,
                                  SubjectId containerId, int slot, int sourceRemainingCount, BlockPos chestPosition) { }
}
