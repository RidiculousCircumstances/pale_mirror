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
import io.farfrontier.palemirror.frontier.v3.model.ContainerSurface;
import io.farfrontier.palemirror.frontier.v3.model.ContainerSurfaceStatus;
import io.farfrontier.palemirror.frontier.v3.model.ExactItemStack;
import io.farfrontier.palemirror.frontier.v3.model.FrontierResourceSitePlan;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldRuntimeDefinition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalEffectObservation;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalIntentTransition;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSite;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestJob;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestObservation;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteLifecycle;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

import java.util.Comparator;
import java.util.Optional;

/** Turns one loaded mature field into its sole named 64-wheat stack without any implicit yield. */
final class FrontierV3ResourceSiteHarvestExecutor {
    private FrontierV3ResourceSiteHarvestExecutor() { }

    static void tick(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        FrontierWorldState state = runtime.decodedState().orElse(null); if (state == null) return;
        state.physicalIntents().values().stream().sorted(Comparator.comparing(PhysicalIntent::id))
                .filter(intent -> intent.kind() == PhysicalIntentKind.RESOURCE_SITE_HARVEST)
                .filter(intent -> intent.status() == PhysicalIntentStatus.PREPARED || intent.status() == PhysicalIntentStatus.RUNNING)
                .findFirst().ifPresent(intent -> execute(level, runtime, state, intent));
    }

    private static void execute(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state, PhysicalIntent intent) {
        Target target = target(state, intent); if (target == null) { unknown(runtime, intent.id(), "missing-canonical-target"); return; }
        if (!loaded(level, target.site()) || !level.hasChunkAt(target.chestPosition())) return;
        ContainerSurface surface = state.inventory().surfaces().get(target.job().outputSlot().containerId());
        if (surface == null || surface.status() == ContainerSurfaceStatus.CONFLICT) { unknown(runtime, intent.id(), "depot-conflict"); return; }
        if (surface.status() != ContainerSurfaceStatus.ACTIVE) return;
        ChestBlockEntity chest = FrontierV3CargoHandoffExecutor.activeChest(level,
                new FrontierV3CargoHandoffExecutor.StoreTarget(target.chestPosition(), target.job().outputSlot().containerId()));
        if (chest == null) { unknown(runtime, intent.id(), "missing-depot"); return; }
        FrontierV3ResourceSiteLedger ledger = FrontierV3ResourceSiteLedger.get(level);
        if (intent.status() == PhysicalIntentStatus.RUNNING) { inspectRunning(level, runtime, target, ledger, chest); return; }
        if (!precondition(level, target, ledger, chest)) { fail(runtime, ledger, target, "field-or-depot-precondition"); return; }
        if (!transition(runtime, intent.id(), PhysicalIntentStatus.RUNNING, Optional.empty(), "running")) return;
        if (!apply(level, ledger, target.site(), chest, target.output())) { fail(runtime, ledger, target, "partial-harvest"); return; }
        confirm(runtime, intent, target);
    }

    private static void inspectRunning(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, Target target,
                                       FrontierV3ResourceSiteLedger ledger, ChestBlockEntity chest) {
        if (completePostcondition(level, target, ledger, chest)) { confirm(runtime, target.intent(), target); return; }
        fail(runtime, ledger, target, "restart-postcondition-conflict");
    }

    private static boolean precondition(ServerLevel level, Target target, FrontierV3ResourceSiteLedger ledger, ChestBlockEntity chest) {
        return precondition(level, target.site(), ledger, chest, target.output());
    }

    static boolean precondition(ServerLevel level, ResourceSite site, FrontierV3ResourceSiteLedger ledger, ChestBlockEntity chest, ExactItemStack output) {
        if (!(output.custody() instanceof io.farfrontier.palemirror.frontier.v3.model.InventoryCustody.ContainerSlot slot)) return false;
        FrontierV3ResourceSiteLedger.Claim claim = ledger.claim(site.id());
        return claim != null && claim.status() == FrontierV3ResourceSiteLedger.Status.ACTIVE && claim.stage() == ResourceSiteLifecycle.MATURE_STAGE
                && FrontierV3ResourceSiteExecutor.matches(level, site, ResourceSiteLifecycle.MATURE_STAGE) && chest.getItem(slot.slot()).isEmpty();
    }

    private static boolean completePostcondition(ServerLevel level, Target target, FrontierV3ResourceSiteLedger ledger, ChestBlockEntity chest) {
        return completePostcondition(level, target.site(), ledger, chest, target.output());
    }

    static boolean completePostcondition(ServerLevel level, ResourceSite site, FrontierV3ResourceSiteLedger ledger, ChestBlockEntity chest, ExactItemStack expected) {
        if (!(expected.custody() instanceof io.farfrontier.palemirror.frontier.v3.model.InventoryCustody.ContainerSlot slot)) return false;
        FrontierV3ResourceSiteLedger.Claim claim = ledger.claim(site.id()); ItemStack output = chest.getItem(slot.slot());
        return claim != null && claim.status() == FrontierV3ResourceSiteLedger.Status.ACTIVE && claim.stage() == 0
                && FrontierV3ResourceSiteExecutor.matches(level, site, 0) && FrontierV3CargoHandoffExecutor.exactMatch(output, expected);
    }

    static boolean apply(ServerLevel level, FrontierV3ResourceSiteLedger ledger, ResourceSite site, ChestBlockEntity chest, ExactItemStack output) {
        if (!precondition(level, site, ledger, chest, output)) return false;
        if (FrontierV3ResourceSiteExecutor.projectStage(level, ledger, site, 0) == FrontierV3ResourceSiteExecutor.StageProjectionResult.CONFLICT) return false;
        int slot = ((io.farfrontier.palemirror.frontier.v3.model.InventoryCustody.ContainerSlot) output.custody()).slot();
        chest.setItem(slot, FrontierV3CargoHandoffExecutor.materializedStack(output)); chest.setChanged();
        return completePostcondition(level, site, ledger, chest, output);
    }

    private static void confirm(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, PhysicalIntent intent, Target target) {
        ResourceSiteHarvestObservation observation = new ResourceSiteHarvestObservation(new PhysicalObservationId("observation:" + intent.id().value().replace(':', '-')),
                intent.id(), target.site().id(), target.job().workerId(), target.output(), 64);
        transition(runtime, intent.id(), PhysicalIntentStatus.CONFIRMED, Optional.of(observation), "confirmed");
    }

    private static Target target(FrontierWorldState state, PhysicalIntent intent) {
        if (intent.kind() != PhysicalIntentKind.RESOURCE_SITE_HARVEST) return null;
        ResourceSiteLifecycle lifecycle = state.resourceSites().sites().get(intent.causeSubjectId()); if (lifecycle == null) return null;
        ResourceSiteHarvestJob job = lifecycle.activeWork().filter(ResourceSiteHarvestJob.class::isInstance).map(ResourceSiteHarvestJob.class::cast)
                .filter(value -> value.intentId().equals(intent.id())).orElse(null);
        if (job == null) return null; ResourceSite site = FrontierResourceSitePlan.compile(state.bootstrap()).get(job.siteId());
        ContainerSurface surface = state.inventory().surfaces().get(job.outputSlot().containerId()); if (site == null || surface == null) return null;
        ExactItemStack output = new ExactItemStack(job.outputItemId(), site.settlementId(), "minecraft:wheat", 64, job.outputSlot());
        return new Target(intent, site, job, output, new BlockPos(surface.position().x(), surface.position().y(), surface.position().z()));
    }

    private static boolean loaded(ServerLevel level, ResourceSite site) {
        return java.util.stream.Stream.concat(site.cropSlots().stream(), site.soilSlots().stream())
                .allMatch(slot -> level.hasChunkAt(new BlockPos(slot.x(), slot.y(), slot.z())));
    }
    private static void fail(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierV3ResourceSiteLedger ledger, Target target, String cause) {
        unknown(runtime, target.intent().id(), cause); FrontierV3ResourceSiteExecutor.recordConflict(runtime, ledger, target.site(), target.site().cropSlots().getFirst(), cause);
    }
    private static void unknown(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, PhysicalIntentId id, String phase) {
        transition(runtime, id, PhysicalIntentStatus.UNKNOWN_AFTER_RESTART, Optional.empty(), phase);
    }
    private static boolean transition(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, PhysicalIntentId intentId, PhysicalIntentStatus status,
                                      Optional<PhysicalEffectObservation> observation, String phase) {
        CheckpointImage checkpoint = runtime.checkpointImage().orElseThrow(() -> new IllegalStateException("v3 runtime is inactive"));
        // A physical action may survive a crash between its Minecraft postcondition and canonical
        // receipt. Bind each admission attempt to its precise canonical revision, as the other
        // physical bridges do: a retained rejection or pre-crash command receipt cannot turn a
        // later loaded-world inspection into an indistinguishable duplicate command.
        CommandId id = commandId(phase, intentId, checkpoint.revision().value());
        CommandResult result = runtime.submit(new FrontierCommand(1, id, checkpoint.worldId(), checkpoint.revision(), checkpoint.instant(),
                FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(id), new PhysicalIntentTransition(intentId, status, observation)))
                .orElseThrow(() -> new IllegalStateException("v3 runtime is inactive"));
        if (!(result instanceof CommandResult.Accepted) && status == PhysicalIntentStatus.CONFIRMED) {
            throw new IllegalStateException("resource-site harvest confirmation was rejected: " + result);
        }
        return result instanceof CommandResult.Accepted;
    }
    static CommandId commandId(String phase, PhysicalIntentId intentId, long revision) {
        return new CommandId("executor:resource-site-harvest-" + phase + "-" + intentId.value().replace(':', '-') + "-r" + revision);
    }
    private record Target(PhysicalIntent intent, ResourceSite site, ResourceSiteHarvestJob job, ExactItemStack output, BlockPos chestPosition) { }
}
