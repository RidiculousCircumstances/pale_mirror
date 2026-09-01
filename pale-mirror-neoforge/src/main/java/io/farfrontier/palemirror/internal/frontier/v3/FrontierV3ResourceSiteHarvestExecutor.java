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
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
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
import java.util.Objects;
import java.util.Optional;

/** Turns one loaded mature field into its sole named 64-wheat stack without any implicit yield. */
final class FrontierV3ResourceSiteHarvestExecutor {
    /**
     * A mature canonical site can legitimately be one bounded materializer
     * turn ahead of its loaded crop blocks.  That is not player drift and
     * must not turn a harvest into an irreversible conflict.
     */
    enum Precondition { READY, WAITING_FOR_FIELD_PROJECTION, CONFLICT }

    /** Immutable loaded-world probe for the read-only operator diagnostic boundary. */
    record Readiness(boolean fieldLoaded, boolean depotLoaded, String depotSurface, boolean ownedChestPresent,
                     boolean fieldMatchesMatureStage, boolean outputSlotEmpty, int claimedFieldStage,
                     boolean fieldMatchesClaimedStage, Precondition precondition) { }

    private FrontierV3ResourceSiteHarvestExecutor() { }

    static void tick(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        FrontierWorldState state = runtime.decodedState().orElse(null); if (state == null) return;
        state.physicalIntents().values().stream().sorted(Comparator.comparing(PhysicalIntent::id))
                .filter(intent -> intent.kind() == PhysicalIntentKind.RESOURCE_SITE_HARVEST)
                .filter(intent -> intent.status() == PhysicalIntentStatus.PREPARED || intent.status() == PhysicalIntentStatus.RUNNING)
                .findFirst().ifPresent(intent -> execute(level, runtime, state, intent));
    }

    /** Explains a pending harvest without loading chunks or mutating either world. */
    static Optional<Readiness> readiness(ServerLevel level, FrontierWorldState state, PhysicalIntentId intentId) {
        Objects.requireNonNull(level, "level"); Objects.requireNonNull(state, "state"); Objects.requireNonNull(intentId, "intent id");
        Target target = target(state, state.physicalIntents().get(intentId));
        if (target == null) return Optional.empty();
        boolean fieldLoaded = loaded(level, target.site());
        boolean depotLoaded = level.hasChunkAt(target.chestPosition());
        ContainerSurface surface = state.inventory().surfaces().get(target.job().outputSlot().containerId());
        String surfaceStatus = surface == null ? "MISSING" : surface.status().name();
        ChestBlockEntity chest = depotLoaded ? FrontierV3CargoHandoffExecutor.activeChest(level,
                new FrontierV3CargoHandoffExecutor.StoreTarget(target.chestPosition(), target.job().outputSlot().containerId())) : null;
        FrontierV3ResourceSiteLedger.Claim claim = fieldLoaded ? FrontierV3ResourceSiteLedger.get(level).claim(target.site().id()) : null;
        boolean mature = fieldLoaded && claim != null && claim.status() == FrontierV3ResourceSiteLedger.Status.ACTIVE
                && claim.stage() == ResourceSiteLifecycle.MATURE_STAGE && FrontierV3ResourceSiteExecutor.matches(level, target.site(), ResourceSiteLifecycle.MATURE_STAGE);
        boolean matchesClaim = fieldLoaded && claim != null && claim.status() == FrontierV3ResourceSiteLedger.Status.ACTIVE
                && FrontierV3ResourceSiteExecutor.matches(level, target.site(), claim.stage());
        boolean outputSlotEmpty = chest != null && target.output().custody() instanceof io.farfrontier.palemirror.frontier.v3.model.InventoryCustody.ContainerSlot slot
                && chest.getItem(slot.slot()).isEmpty();
        Precondition precondition = fieldLoaded && depotLoaded && chest != null
                ? precondition(level, target.site(), claim, chest, target.output()) : Precondition.CONFLICT;
        return Optional.of(new Readiness(fieldLoaded, depotLoaded, surfaceStatus, chest != null, mature, outputSlotEmpty,
                claim == null ? -1 : claim.stage(), matchesClaim, precondition));
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
        Precondition precondition = precondition(level, target.site(), ledger.claim(target.site().id()), chest, target.output());
        if (precondition == Precondition.WAITING_FOR_FIELD_PROJECTION) return;
        if (precondition != Precondition.READY) { fail(runtime, ledger, target, "field-or-depot-precondition"); return; }
        if (!transition(runtime, intent.id(), PhysicalIntentStatus.RUNNING, Optional.empty(), "running")) return;
        if (!apply(level, ledger, target.site(), chest, target.output())) { fail(runtime, ledger, target, "partial-harvest"); return; }
        confirm(runtime, intent, target);
    }

    private static void inspectRunning(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, Target target,
                                       FrontierV3ResourceSiteLedger ledger, ChestBlockEntity chest) {
        if (completePostcondition(level, target, ledger, chest)) { confirm(runtime, target.intent(), target); return; }
        fail(runtime, ledger, target, "restart-postcondition-conflict");
    }

    static Precondition precondition(ServerLevel level, ResourceSite site, FrontierV3ResourceSiteLedger ledger, ChestBlockEntity chest, ExactItemStack output) {
        return precondition(level, site, ledger.claim(site.id()), chest, output);
    }

    private static Precondition precondition(ServerLevel level, ResourceSite site, FrontierV3ResourceSiteLedger.Claim claim,
                                             ChestBlockEntity chest, ExactItemStack output) {
        if (!(output.custody() instanceof io.farfrontier.palemirror.frontier.v3.model.InventoryCustody.ContainerSlot slot)
                || claim == null || claim.status() != FrontierV3ResourceSiteLedger.Status.ACTIVE || !chest.getItem(slot.slot()).isEmpty()) {
            return Precondition.CONFLICT;
        }
        // The field ledger is the durable proof of PM ownership.  If its
        // complete footprint still exactly matches that known stage, the
        // regular bounded stage projector simply has not reached this site
        // after the canonical maturity event yet.  Never overwrite or
        // "repair" it here; wait for that owner to project the next stage.
        if (claim.stage() < ResourceSiteLifecycle.MATURE_STAGE
                && FrontierV3ResourceSiteExecutor.matches(level, site, claim.stage())) {
            return Precondition.WAITING_FOR_FIELD_PROJECTION;
        }
        return claim.stage() == ResourceSiteLifecycle.MATURE_STAGE
                && FrontierV3ResourceSiteExecutor.matches(level, site, ResourceSiteLifecycle.MATURE_STAGE)
                ? Precondition.READY : Precondition.CONFLICT;
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
        if (precondition(level, site, ledger, chest, output) != Precondition.READY) return false;
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
        if (intent == null) return null;
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
        io.farfrontier.palemirror.frontier.v3.api.FrontierCanonicalState<?> checkpoint = runtime.canonicalState().orElseThrow(() -> new IllegalStateException("v3 runtime is inactive"));
        // A physical action may survive a crash between its Minecraft postcondition and canonical
        // receipt. Bind each admission attempt to its precise canonical revision, as the other
        // physical bridges do: a retained rejection or pre-crash command receipt cannot turn a
        // later loaded-world inspection into an indistinguishable duplicate command.
        CommandId id = commandId(phase, intentId, checkpoint.revision().value());
        CommandResult result = runtime.submit(new FrontierCommand(1, id, checkpoint.worldId(), checkpoint.revision(), checkpoint.instant(),
                FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(id), new PhysicalIntentTransition(intentId, status, observation)))
                .orElseThrow(() -> new IllegalStateException("v3 runtime is inactive"));
        if (result instanceof CommandResult.Rejected rejected && status == PhysicalIntentStatus.CONFIRMED) {
            throw new IllegalStateException("resource-site harvest confirmation was rejected [" + rejected.rejection().code()
                    + "]: " + rejected.rejection().detail());
        }
        return result instanceof CommandResult.Accepted;
    }
    static CommandId commandId(String phase, PhysicalIntentId intentId, long revision) {
        return new CommandId("executor:resource-site-harvest-" + phase + "-" + intentId.value().replace(':', '-') + "-r" + revision);
    }
    private record Target(PhysicalIntent intent, ResourceSite site, ResourceSiteHarvestJob job, ExactItemStack output, BlockPos chestPosition) { }
}
