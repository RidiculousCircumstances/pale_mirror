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
import io.farfrontier.palemirror.frontier.v3.model.ContainerSurface;
import io.farfrontier.palemirror.frontier.v3.model.ContainerSurfaceStatus;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.ExactItemStack;
import io.farfrontier.palemirror.frontier.v3.model.FrontierResourceSitePlan;
import io.farfrontier.palemirror.frontier.v3.model.FrontierSceneBehaviors;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalEffectObservation;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalIntentTransition;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSite;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestJob;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestLineage;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestObservation;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestProgress;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteLifecycle;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/** Turns one loaded mature field into its sole named 64-wheat stack without any implicit yield. */
final class FrontierV3ResourceSiteHarvestExecutor {
    /**
     * A mature canonical site can legitimately be one bounded materializer
     * turn ahead of its loaded crop blocks.  That is not player drift and
     * must not turn a harvest into an irreversible conflict.
     */
    /**
     * Read-only distinction between a field that may admit work and one whose
     * retained worker has already made visible, owned progress.  The latter is
     * neither another admission candidate nor a world conflict.
     */
    enum Precondition { READY, WAITING_FOR_FIELD_PROJECTION, HARVESTING, CONFLICT }

    /** Immutable loaded-world probe for the read-only operator diagnostic boundary. */
    record Readiness(boolean fieldLoaded, boolean depotLoaded, String depotSurface, boolean ownedChestPresent,
                     boolean fieldMatchesMatureStage, boolean outputSlotEmpty, int claimedFieldStage,
                     boolean fieldMatchesClaimedStage, Precondition precondition, boolean queued, boolean executionEligible) { }

    private FrontierV3ResourceSiteHarvestExecutor() { }

    static void tick(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        // COLD may retain a canonical deferred crop prefix, while the registered HOT scene owns
        // visible local work. This receipt owner remains the sole loaded-world final-output
        // boundary, so a naturally loaded field can never manufacture a second depot stack.
        if (!effectExecutionAdmitted()) return;
        FrontierWorldState state = runtime.decodedState().orElse(null); if (state == null) return;
        // Never let an unloaded alphabetically first field starve a later naturally loaded one.
        // The bounded site aggregate is the work set: scanning it is capped by the bootstrap's
        // exact resource sites, unlike scanning the retained global physical-intent ledger.
        firstRunnable(pendingIntents(state), intent -> executionEligible(level, state, intent))
                .ifPresent(intent -> execute(level, runtime, state, intent));
    }

    static List<PhysicalIntent> pendingIntents(FrontierWorldState state) {
        if (!effectExecutionAdmitted()) return List.of();
        return state.resourceSites().sites().values().stream().sorted(java.util.Comparator.comparing(ResourceSiteLifecycle::siteId))
                .flatMap(lifecycle -> java.util.stream.Stream.concat(
                        lifecycle.activeWork().filter(ResourceSiteHarvestJob.class::isInstance).map(ResourceSiteHarvestJob.class::cast)
                                .map(ResourceSiteHarvestJob::intentId).stream(),
                        lifecycle.harvestLineage().filter(ResourceSiteHarvestLineage::receiptPending)
                                .filter(lineage -> !lineage.composedIntoCanonicalSuccessor(state))
                                .map(ResourceSiteHarvestLineage::predecessorIntentId).stream()))
                .map(state.physicalIntents()::get).filter(Objects::nonNull)
                .filter(intent -> intent.kind() == PhysicalIntentKind.RESOURCE_SITE_HARVEST)
                .filter(intent -> intent.status() == PhysicalIntentStatus.PREPARED || intent.status() == PhysicalIntentStatus.RUNNING)
                .toList();
    }

    /** Bounded fair admission: an unloaded field is a deferral, never a global queue head. */
    static Optional<PhysicalIntent> firstRunnable(List<PhysicalIntent> candidates, Predicate<PhysicalIntent> runnable) {
        Objects.requireNonNull(candidates, "harvest candidates"); Objects.requireNonNull(runnable, "harvest runnable probe");
        return candidates.stream().filter(runnable).findFirst();
    }

    static boolean effectExecutionAdmitted() {
        return io.farfrontier.palemirror.frontier.v3.process.ResourceSiteHarvestProcess.irreversibleCropEffectsAdmitted();
    }

    /** Explains a pending harvest without loading chunks or mutating either world. */
    static Optional<Readiness> readiness(ServerLevel level, FrontierWorldState state, PhysicalIntentId intentId) {
        Objects.requireNonNull(level, "level"); Objects.requireNonNull(state, "state"); Objects.requireNonNull(intentId, "intent id");
        Target target = target(state, state.physicalIntents().get(intentId));
        if (target == null) return Optional.empty();
        boolean fieldLoaded = loaded(level, target.site());
        boolean depotLoaded = level.hasChunkAt(target.chestPosition());
        ContainerSurface surface = state.inventory().surfaces().get(outputSlot(target).containerId());
        String surfaceStatus = surface == null ? "MISSING" : surface.status().name();
        ChestBlockEntity chest = depotLoaded ? FrontierV3CargoHandoffExecutor.activeChest(level,
                new FrontierV3CargoHandoffExecutor.StoreTarget(target.chestPosition(), outputSlot(target).containerId())) : null;
        FrontierV3ResourceSiteLedger.Claim claim = fieldLoaded ? FrontierV3ResourceSiteLedger.get(level).claim(target.site().id()) : null;
        boolean mature = fieldLoaded && claim != null && claim.status() == FrontierV3ResourceSiteLedger.Status.ACTIVE
                && claim.stage() == ResourceSiteLifecycle.MATURE_STAGE && FrontierV3ResourceSiteExecutor.matches(level, target.site(), ResourceSiteLifecycle.MATURE_STAGE);
        boolean matchesClaim = fieldLoaded && claim != null && claim.status() == FrontierV3ResourceSiteLedger.Status.ACTIVE
                && FrontierV3ResourceSiteExecutor.matches(level, target.site(), claim.stage());
        boolean outputSlotEmpty = chest != null && target.output().custody() instanceof io.farfrontier.palemirror.frontier.v3.model.InventoryCustody.ContainerSlot slot
                && chest.getItem(slot.slot()).isEmpty();
        Precondition precondition = fieldLoaded && depotLoaded && chest != null
                ? precondition(level, target.site(), claim, chest, target.output()) : Precondition.CONFLICT;
        boolean queued = pendingIntents(state).stream().anyMatch(candidate -> candidate.id().equals(intentId));
        return Optional.of(new Readiness(fieldLoaded, depotLoaded, surfaceStatus, chest != null, mature, outputSlotEmpty,
                claim == null ? -1 : claim.stage(), matchesClaim, precondition, queued, queued && executionEligible(level, state, state.physicalIntents().get(intentId))));
    }

    private static boolean executionEligible(ServerLevel level, FrontierWorldState state, PhysicalIntent intent) {
        Target target = target(state, intent);
        // A retained canonical surface is projected on natural demand.  Until its physical
        // location exists there is no chunk to inspect and no absence to infer. A still-working
        // HOT harvest belongs to its scene/cursor authority, rather than occupying this final
        // receipt executor ahead of a later COLD-completed deferred receipt.
        return target != null && loaded(level, target.site()) && level.hasChunkAt(target.chestPosition())
                && (target.deferredReceipt() || target.activeJob().filter(job -> job.progress().complete()
                && !hasOpenHarvestScene(state, job.id())).isPresent());
    }

    private static void execute(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state, PhysicalIntent intent) {
        Target target = target(state, intent); if (target == null) { unknown(runtime, intent.id(), "missing-canonical-target"); return; }
        // The output is an atomic depot receipt, but it is not eligible until the same retained
        // worker cursor has observed every semantic crop cell.  A HOT scene admits RUNNING
        // immediately before its first physical crop; a complete COLD cursor may also admit
        // the same already-bound deferred receipt.  This executor never creates a worker,
        // route, crop cursor, or output identity merely because a player loaded the field.
        if (!target.deferredReceipt() && !target.activeJob().orElseThrow().progress().complete()) return;
        // The same progress authority closes its HOT worker scene before the field lifecycle
        // consumes the active job into GROWING.  A receipt one server turn earlier makes a
        // still-DRAINING lease point at a vanished job, so defer rather than relying on tick
        // registration order or weakening the scene invariant.
        if (target.activeJob().map(job -> hasOpenHarvestScene(state, job.id())).orElse(false)) return;
        if (!loaded(level, target.site()) || !level.hasChunkAt(target.chestPosition())) return;
        ContainerSurface surface = state.inventory().surfaces().get(outputSlot(target).containerId());
        if (surface == null || surface.status() == ContainerSurfaceStatus.CONFLICT) { unknown(runtime, intent.id(), "depot-conflict"); return; }
        // COLD already owns a terminal output before the container projection exists.  Its
        // later receipt must inspect the loaded physical surface even while that surface is
        // still UNMATERIALIZED: absence is a local output/materialization conflict, not a
        // reason to leave the exact terminal intent RUNNING until another observer arrives.
        if (surface.status() != ContainerSurfaceStatus.ACTIVE && !target.deferredReceipt()) return;
        ChestBlockEntity chest = FrontierV3CargoHandoffExecutor.activeChest(level,
                new FrontierV3CargoHandoffExecutor.StoreTarget(target.chestPosition(), outputSlot(target).containerId()));
        if (chest == null) { unknown(runtime, intent.id(), "missing-depot"); return; }
        FrontierV3ResourceSiteLedger ledger = FrontierV3ResourceSiteLedger.get(level);
        if (intent.status() == PhysicalIntentStatus.RUNNING) {
            if (completeRunning(level, target.site(), ledger, chest, target.output())) { confirm(runtime, intent, target); }
            else { fail(level, runtime, ledger, target, "completion-postcondition-conflict"); }
            return;
        }
        // A completed cursor with a PREPARED intent is not an invitation to reconstruct a
        // physical execution.  It is a stale or foreign tuple and must remain inert until the
        // owning scene/recovery policy classifies it.
    }

    /**
     * The one exact completion boundary for an intent that was already RUNNING while its named
     * farmer advanced the 64-cell HOT cursor.  A complete owned field with an empty exact depot
     * slot is the normal first completion and may perform the one atomic receipt.  Deliberately
     * leave the complete AIR cursor intact: regrowth belongs to the bounded site projector in
     * the succeeding lifecycle, rather than making this terminal receipt recreate 64 crops in
     * one server turn.  The same complete receipt after restart only acknowledges the existing
     * output.  Every other partial or altered world is conflict evidence, never a reason to
     * manufacture wheat.
     */
    static boolean completeRunning(ServerLevel level, ResourceSite site, FrontierV3ResourceSiteLedger ledger,
                                   ChestBlockEntity chest, ExactItemStack output) {
        if (completePostcondition(level, site, ledger, chest, output)) return true;
        if (!fullyHarvested(level, site, ledger) || !(output.custody() instanceof io.farfrontier.palemirror.frontier.v3.model.InventoryCustody.ContainerSlot slot)
                || !chest.getItem(slot.slot()).isEmpty()) return false;
        if (ledger.hasHarvestReceipt(site.id(), output)) return false;
        return apply(level, ledger, site, chest, output);
    }

    private static boolean fullyHarvested(ServerLevel level, ResourceSite site, FrontierV3ResourceSiteLedger ledger) {
        FrontierV3ResourceSiteLedger.Claim claim = ledger.claim(site.id());
        return claim != null && claim.status() == FrontierV3ResourceSiteLedger.Status.ACTIVE
                && claim.stage() == ResourceSiteLifecycle.MATURE_STAGE
                && claim.harvestedCropSlots() == ResourceSiteHarvestProgress.TOTAL_CROP_SLOTS
                && FrontierV3ResourceSiteExecutor.matchesHarvestProgress(level, site, ResourceSiteHarvestProgress.TOTAL_CROP_SLOTS);
    }

    private static boolean hasOpenHarvestScene(FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.api.SubjectId jobId) {
        return state.sceneLeases().values().stream().filter(FrontierSceneBehaviors::isResourceSiteHarvest)
                .filter(lease -> lease.status() != io.farfrontier.palemirror.frontier.v3.model.SceneLeaseStatus.CLOSED)
                .anyMatch(lease -> FrontierSceneBehaviors.resourceSiteHarvest(lease).jobId().equals(jobId));
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
        boolean untouchedMature = claim.stage() == ResourceSiteLifecycle.MATURE_STAGE && claim.harvestedCropSlots() == 0
                && FrontierV3ResourceSiteExecutor.matches(level, site, ResourceSiteLifecycle.MATURE_STAGE);
        boolean fullyWorked = claim.stage() == ResourceSiteLifecycle.MATURE_STAGE
                && claim.harvestedCropSlots() == ResourceSiteHarvestProgress.TOTAL_CROP_SLOTS
                && FrontierV3ResourceSiteExecutor.matchesHarvestProgress(level, site, ResourceSiteHarvestProgress.TOTAL_CROP_SLOTS);
        boolean partialWork = claim.stage() == ResourceSiteLifecycle.MATURE_STAGE
                && claim.harvestedCropSlots() > 0
                && claim.harvestedCropSlots() < ResourceSiteHarvestProgress.TOTAL_CROP_SLOTS
                && FrontierV3ResourceSiteExecutor.matchesHarvestProgress(level, site, claim.harvestedCropSlots());
        if (untouchedMature || fullyWorked) return Precondition.READY;
        return partialWork ? Precondition.HARVESTING : Precondition.CONFLICT;
    }

    private static boolean completePostcondition(ServerLevel level, Target target, FrontierV3ResourceSiteLedger ledger, ChestBlockEntity chest) {
        return completePostcondition(level, target.site(), ledger, chest, target.output());
    }

    static boolean completePostcondition(ServerLevel level, ResourceSite site, FrontierV3ResourceSiteLedger ledger, ChestBlockEntity chest, ExactItemStack expected) {
        if (!(expected.custody() instanceof io.farfrontier.palemirror.frontier.v3.model.InventoryCustody.ContainerSlot slot)) return false;
        ItemStack output = chest.getItem(slot.slot());
        return ledger.hasHarvestReceipt(site.id(), expected) && fullyHarvested(level, site, ledger)
                && FrontierV3CargoHandoffExecutor.exactMatch(output, expected);
    }

    static boolean apply(ServerLevel level, FrontierV3ResourceSiteLedger ledger, ResourceSite site, ChestBlockEntity chest, ExactItemStack output) {
        if (precondition(level, site, ledger, chest, output) != Precondition.READY) return false;
        if (!fullyHarvested(level, site, ledger)) {
            for (int index = 0; index < site.cropSlots().size(); index++) {
                BlockPosition crop = site.cropSlots().get(index);
                level.setBlock(new BlockPos(crop.x(), crop.y(), crop.z()), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
                ledger.harvestOne(site.id(), index + 1);
            }
        }
        if (!ledger.recordHarvestReceipt(site.id(), output)) return false;
        int slot = ((io.farfrontier.palemirror.frontier.v3.model.InventoryCustody.ContainerSlot) output.custody()).slot();
        chest.setItem(slot, FrontierV3CargoHandoffExecutor.materializedStack(output)); chest.setChanged();
        return completePostcondition(level, site, ledger, chest, output);
    }

    private static void confirm(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, PhysicalIntent intent, Target target) {
        ResourceSiteHarvestObservation observation = new ResourceSiteHarvestObservation(new PhysicalObservationId("observation:" + intent.id().value().replace(':', '-')),
                intent.id(), target.site().id(), target.workerId(), target.output(), 64);
        if (!transition(runtime, intent.id(), PhysicalIntentStatus.CONFIRMED, Optional.of(observation), "confirmed")) return;
        // `submit` accepts the canonical receipt before the executor's decoded snapshot advances
        // to GROWING.  Do not read that predecessor snapshot as a physical mismatch here: the
        // next resource-site turn owns the confirmed receipt/growth-epoch transition through
        // its fenced `isExactConfirmedHarvestRegrowth` admission.
    }

    private static Target target(FrontierWorldState state, PhysicalIntent intent) {
        if (intent == null) return null;
        if (intent.kind() != PhysicalIntentKind.RESOURCE_SITE_HARVEST) return null;
        ResourceSiteLifecycle lifecycle = state.resourceSites().sites().get(intent.causeSubjectId()); if (lifecycle == null) return null;
        ResourceSiteHarvestLineage deferred = lifecycle.harvestLineage().filter(ResourceSiteHarvestLineage::receiptPending)
                .filter(lineage -> !lineage.composedIntoCanonicalSuccessor(state))
                .filter(lineage -> lineage.predecessorIntentId().equals(intent.id())).orElse(null);
        if (deferred != null) {
            ResourceSite site = FrontierResourceSitePlan.compile(state.bootstrap()).get(lifecycle.siteId());
            ContainerSurface surface = state.inventory().surfaces().get(deferred.outputSlot().containerId());
            if (site == null || surface == null) return null;
            ExactItemStack output = new ExactItemStack(deferred.outputItemId(), site.settlementId(), "minecraft:wheat", 64, deferred.outputSlot());
            return new Target(intent, site, deferred.workerId(), Optional.empty(), output,
                    new BlockPos(surface.position().x(), surface.position().y(), surface.position().z()), true);
        }
        ResourceSiteHarvestJob job = lifecycle.activeWork().filter(ResourceSiteHarvestJob.class::isInstance).map(ResourceSiteHarvestJob.class::cast)
                .filter(value -> value.intentId().equals(intent.id())).orElse(null);
        if (job == null) return null; ResourceSite site = FrontierResourceSitePlan.compile(state.bootstrap()).get(job.siteId());
        ContainerSurface surface = state.inventory().surfaces().get(job.outputSlot().containerId()); if (site == null || surface == null) return null;
        ExactItemStack output = new ExactItemStack(job.outputItemId(), site.settlementId(), "minecraft:wheat", 64, job.outputSlot());
        return new Target(intent, site, job.workerId(), Optional.of(job), output,
                new BlockPos(surface.position().x(), surface.position().y(), surface.position().z()), false);
    }

    private static boolean loaded(ServerLevel level, ResourceSite site) {
        return java.util.stream.Stream.concat(site.cropSlots().stream(), site.soilSlots().stream())
                .allMatch(slot -> level.hasChunkAt(new BlockPos(slot.x(), slot.y(), slot.z())));
    }
    private static void fail(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierV3ResourceSiteLedger ledger, Target target, String cause) {
        // COLD has already closed this task and retained its exact output.  A later foreign,
        // missing, or mismatched Minecraft surface is therefore evidence about this one
        // materialization owner only.  Escalating it through the renewable field conflict path
        // poisons the completed field and repeats a new site incident every tick, even though
        // no semantic harvest work remains to be repaired.
        if (target.deferredReceipt()) {
            unknown(runtime, target.intent().id(), cause);
            return;
        }
        unknown(runtime, target.intent().id(), cause);
        io.farfrontier.palemirror.frontier.v3.api.FrontierCanonicalState<?> checkpoint = runtime.canonicalState().orElseThrow();
        io.farfrontier.palemirror.frontier.v3.api.CommandId id = new io.farfrontier.palemirror.frontier.v3.api.CommandId(
                "executor:resource-site-conflict-harvest-r" + checkpoint.revision().value());
        FrontierV3ResourceSiteConflictExecutor.recordConflict(level, runtime, ledger, target.site(), target.site().cropSlots().getFirst(),
                io.farfrontier.palemirror.frontier.v3.model.ResourceSiteConflictReason.OBSERVED_MANAGED_CELL_MISMATCH,
                io.farfrontier.palemirror.frontier.v3.model.ResourceSiteConflictSource.ADAPTER_WRITE_FAILURE, id);
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
    private static io.farfrontier.palemirror.frontier.v3.model.InventoryCustody.ContainerSlot outputSlot(Target target) {
        return (io.farfrontier.palemirror.frontier.v3.model.InventoryCustody.ContainerSlot) target.output().custody();
    }
    private record Target(PhysicalIntent intent, ResourceSite site, SubjectId workerId, Optional<ResourceSiteHarvestJob> activeJob,
                          ExactItemStack output, BlockPos chestPosition, boolean deferredReceipt) { }
}
