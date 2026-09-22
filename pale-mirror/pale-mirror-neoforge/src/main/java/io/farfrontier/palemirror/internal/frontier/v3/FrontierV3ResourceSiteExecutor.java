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
import io.farfrontier.palemirror.frontier.v3.api.PhysicalPostcondition;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierResourceSitePlan;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalIntentTransition;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSite;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteConflictReason;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestJob;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestLineage;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestProgress;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteLifecycle;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSitePhase;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSitePreparationObservation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
final class FrontierV3ResourceSiteExecutor {
    private static final int MAX_SITE_PROJECTION_WRITES_PER_TICK = 8;
    private static final Map<FrontierV3ServerRuntime<?, ?>, Integer> STAGE_CURSORS = new IdentityHashMap<>();
    private static final Map<FrontierV3ServerRuntime<?, ?>, Set<SubjectId>> RECOVERY_SITES = new IdentityHashMap<>();
    private static final Map<FrontierV3ServerRuntime<?, ?>, Map<SubjectId, FieldProjectionWork>> PROJECTION_WORK = new IdentityHashMap<>();
    enum BlockBreakObservation { UNMANAGED, ACCEPTED, REJECTED }
    enum StageProjectionResult { CURRENT, UPDATED, CONFLICT, DEFERRED }
    enum RestartReconciliation { CURRENT, RECREATED, CONFLICT, DEFERRED }
    enum ConfirmedHarvestRegrowthAdmission {
        ADMITTED,
        MISSING_ACTIVE_TERMINAL_CLAIM,
        LIFECYCLE_NOT_INITIAL_GROWTH,
        PHYSICAL_RECEIPT_MISMATCH,
        MISSING_CONFIRMED_RECEIPT
    }
    enum LifecycleConflictOrigin { ORDINARY_GROWTH, RESTART_RECONCILIATION }
    enum HarvestRestartPhysicalState {
        UNOBSERVED,
        NEUTRAL_UNCLAIMED,
        OWNED_EXACT,
        OWNED_BEHIND,
        FOREIGN_OR_DAMAGED,
        UNKNOWN
    }
    record ManagedFacilityProgressProjection(SubjectId facilityId, List<BlockPosition> slotPlan, int completedSlots) {
        ManagedFacilityProgressProjection {
            facilityId = java.util.Objects.requireNonNull(facilityId, "facility id");
            slotPlan = List.copyOf(java.util.Objects.requireNonNull(slotPlan, "slot plan"));
            if (slotPlan.isEmpty() || new java.util.LinkedHashSet<>(slotPlan).size() != slotPlan.size()
                    || completedSlots < 0 || completedSlots > slotPlan.size()) {
                throw new IllegalArgumentException("managed facility progress does not match its immutable slot plan");
            }
        }
        BlockState expected(int index) { return index < completedSlots ? Blocks.AIR.defaultBlockState() : crop(ResourceSiteLifecycle.MATURE_STAGE); }
    }
    record HarvestRestartClassification(HarvestRestartPhysicalState state, ManagedFacilityProgressProjection projection,
                                        BlockPosition witness) { }
    private record FieldWrite(BlockPosition position, BlockState state) { }
    private record FieldProjectionWork(int desiredStage, int completedCropSlots, List<FieldWrite> writes,
                                       int nextWrite, boolean activatesReservedClaim, boolean restoresHarvestCursor) {
        FieldProjectionWork {
            if (desiredStage < 0 || desiredStage > ResourceSiteLifecycle.MATURE_STAGE || completedCropSlots < 0
                    || nextWrite < 0 || nextWrite > writes.size()) throw new IllegalArgumentException("invalid bounded resource projection work");
            writes = List.copyOf(writes);
        }
        boolean complete() { return nextWrite == writes.size(); }
        FieldProjectionWork advanced(int count) { return new FieldProjectionWork(desiredStage, completedCropSlots, writes, nextWrite + count,
                activatesReservedClaim, restoresHarvestCursor); }
    }
    private FrontierV3ResourceSiteExecutor() { }
    static int projectionWriteBudget() { return MAX_SITE_PROJECTION_WRITES_PER_TICK; }
    static void forget(FrontierV3ServerRuntime<?, ?> runtime) {
        STAGE_CURSORS.remove(runtime); RECOVERY_SITES.remove(runtime); PROJECTION_WORK.remove(runtime);
    }
    static void beginRecovery(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        FrontierWorldState state = runtime.decodedState().orElse(null); if (state == null) return;
        Set<SubjectId> pending = state.resourceSites().sites().values().stream()
                .filter(FrontierV3ResourceSiteExecutor::projectsGrowthStage).map(ResourceSiteLifecycle::siteId)
                .collect(java.util.stream.Collectors.toCollection(HashSet::new));
        if (!pending.isEmpty()) RECOVERY_SITES.put(runtime, pending);
    }
    static void tick(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        FrontierWorldState state = runtime.decodedState().orElse(null);
        if (state == null) return;
        FrontierV3ResourceSitePreparationSelection.nextLoaded(state,
                site -> loaded(level, site) && FrontierV3GrayboxExecutor.resourceSiteProjectionDemanded(runtime, level, site)).ifPresent(intent -> execute(level, runtime, state, intent));
        reconcileOneAfterRestart(level, runtime, state);
        projectOneGrowthStage(level, runtime, runtime.decodedState().orElse(state));
    }
    static BlockBreakObservation observeBlockBreak(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, ServerLevel level,
                                                   BlockPos position, String cause) {
        FrontierWorldState state = runtime.decodedState().orElse(null);
        if (state == null) return BlockBreakObservation.REJECTED;
        Target target = target(state, position);
        if (target == null) return BlockBreakObservation.UNMANAGED;
        FrontierV3ResourceSiteLedger ledger = FrontierV3ResourceSiteLedger.get(level);
        FrontierV3ResourceSiteLedger.Claim claim = ledger.claim(target.site().id());
        ResourceSiteLifecycle lifecycle = state.resourceSites().site(target.site().id());
        if (lifecycle.phase() == ResourceSitePhase.DESTROYED || lifecycle.phase() == ResourceSitePhase.CONFLICT) return BlockBreakObservation.UNMANAGED;
        if (claim == null || claim.status() != FrontierV3ResourceSiteLedger.Status.ACTIVE || !matchesClaim(level, target.site(), claim)) {
            BlockPosition observed = claim == null ? canonical(position)
                    : firstMismatchClaim(level, target.site(), claim).orElse(canonical(position));
            return FrontierV3ResourceSiteConflictExecutor.recordPlayerConflict(level, runtime, ledger, target.site(), observed, cause)
                    ? BlockBreakObservation.ACCEPTED : BlockBreakObservation.REJECTED;
        }
        return FrontierV3ResourceSiteConflictExecutor.recordPlayerConflict(level, runtime, ledger, target.site(), canonical(position), cause)
                ? BlockBreakObservation.ACCEPTED : BlockBreakObservation.REJECTED;
    }
    static boolean blocksNativeCropGrowth(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, ServerLevel level, BlockPos position) {
        FrontierWorldState state = runtime.decodedState().orElse(null); if (state == null) return false;
        Target target = target(state, position); if (target == null || !target.site().cropSlots().contains(canonical(position))) return false;
        FrontierV3ResourceSiteLedger ledger = FrontierV3ResourceSiteLedger.get(level);
        FrontierV3ResourceSiteLedger.Claim claim = ledger.claim(target.site().id());
        boolean blocked = blocksNativeCropGrowth(claim, projectionInFlight(runtime, target.site().id()),
                level.getBlockState(position).equals(crop(claim == null ? 0 : claim.stage())));
        if (blocked && claim != null && claim.status() == FrontierV3ResourceSiteLedger.Status.ACTIVE
                && level.getBlockState(position).is(Blocks.WHEAT)) {
            ledger.recordNativeGrowthFence(target.site().id(), new FrontierV3ResourceSiteLedger.NativeGrowthFence(
                    "crop-grow-pre", canonical(position), level.getBlockState(position).getValue(CropBlock.AGE), claim.stage()));
        }
        return blocked;
    }
    static boolean blocksNativeCropGrowth(ServerLevel level, FrontierV3ResourceSiteLedger ledger, ResourceSite site, BlockPos position) {
        FrontierV3ResourceSiteLedger.Claim claim = ledger.claim(site.id());
        return site.cropSlots().contains(canonical(position)) && blocksNativeCropGrowth(claim, false,
                level.getBlockState(position).equals(crop(claim == null ? 0 : claim.stage())));
    }
    /**
     * A foreign listener can still force a CropGrowEvent after our pre-event fence has returned
     * {@code DO_NOT_GROW}.  The post-event boundary therefore restores only the exact owned
     * crop state that existed before that native event.  This is deliberately not general
     * reconciliation: an AIR/non-wheat/foreign block remains a truthful local conflict.
     */
    static boolean restoreNativeGrowthPostcondition(ServerLevel level, FrontierV3ResourceSiteLedger ledger,
                                                     ResourceSite site, BlockPos position) {
        if (!site.cropSlots().contains(canonical(position))) return false;
        FrontierV3ResourceSiteLedger.Claim claim = ledger.claim(site.id());
        if (claim == null || claim.status() != FrontierV3ResourceSiteLedger.Status.ACTIVE || claim.projection() != null) return false;
        BlockState observed = level.getBlockState(position);
        if (!observed.is(Blocks.WHEAT)) return false;
        int index = site.cropSlots().indexOf(canonical(position));
        BlockState expected = claim.stage() == ResourceSiteLifecycle.MATURE_STAGE && index < claim.harvestedCropSlots()
                ? Blocks.AIR.defaultBlockState() : crop(claim.stage());
        if (observed.equals(expected)) return false;
        if (!level.setBlock(position, expected, 2)) return false;
        ledger.recordNativeGrowthFence(site.id(), new FrontierV3ResourceSiteLedger.NativeGrowthFence(
                "crop-grow-post", canonical(position), observed.getValue(CropBlock.AGE), claim.stage()));
        return level.getBlockState(position).equals(expected);
    }
    static boolean restoreNativeGrowthPostcondition(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                                     ServerLevel level, BlockPos position) {
        FrontierWorldState state = runtime.decodedState().orElse(null);
        if (state == null) return false;
        Target target = target(state, position);
        return target != null && restoreNativeGrowthPostcondition(level, FrontierV3ResourceSiteLedger.get(level), target.site(), position);
    }
    static boolean blocksNativeCropGrowth(FrontierV3ResourceSiteLedger.Claim claim, boolean projectionInFlight,
                                          boolean matchesRecordedClaimStage) {
        // PENDING has already reserved the exact managed cells while the bounded physical
        // writer transitions its durable claim.  Treating only ACTIVE as owned leaves the
        // complete initial field exposed to vanilla random ticks during that hand-off.
        return projectionInFlight || claim != null;
    }
    static boolean matchesHarvestProgress(ServerLevel level, ResourceSite site, int completedCropSlots) {
        ManagedFacilityProgressProjection projection;
        try { projection = harvestProjection(site, completedCropSlots); }
        catch (IllegalArgumentException invalid) { return false; }
        if (!loaded(level, site) || !matchesInfrastructure(level, site)) return false;
        for (int index = 0; index < projection.slotPlan().size(); index++) {
            if (!level.getBlockState(minecraft(projection.slotPlan().get(index))).equals(projection.expected(index))) return false;
        }
        return true;
    }
    static boolean matchesClaim(ServerLevel level, ResourceSite site, FrontierV3ResourceSiteLedger.Claim claim) {
        return claim.stage() == ResourceSiteLifecycle.MATURE_STAGE && claim.harvestedCropSlots() > 0
                ? matchesHarvestProgress(level, site, claim.harvestedCropSlots()) : matches(level, site, claim.stage());
    }
    static Optional<BlockPosition> firstMismatchClaim(ServerLevel level, ResourceSite site, FrontierV3ResourceSiteLedger.Claim claim) {
        if (claim.stage() != ResourceSiteLifecycle.MATURE_STAGE || claim.harvestedCropSlots() == 0) return firstMismatch(level, site, claim.stage());
        Optional<BlockPosition> infrastructure = firstInfrastructureMismatch(level, site);
        if (infrastructure.isPresent()) return infrastructure;
        for (int index = 0; index < site.cropSlots().size(); index++) {
            BlockPosition slot = site.cropSlots().get(index);
            BlockState expected = index < claim.harvestedCropSlots() ? Blocks.AIR.defaultBlockState() : crop(ResourceSiteLifecycle.MATURE_STAGE);
            if (!level.getBlockState(minecraft(slot)).equals(expected)) return Optional.of(slot);
        }
        return Optional.empty();
    }
    private static void projectOneGrowthStage(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state) {
        Set<SubjectId> pendingRecovery = RECOVERY_SITES.getOrDefault(runtime, Set.of());
        Set<SubjectId> inFlight = PROJECTION_WORK.getOrDefault(runtime, Map.of()).keySet();
        List<ResourceSiteLifecycle> allCandidates = state.resourceSites().sites().values().stream().filter(FrontierV3ResourceSiteExecutor::projectsGrowthStage)
                .filter(lifecycle -> !pendingRecovery.contains(lifecycle.siteId()))
                .filter(lifecycle -> FrontierV3GrayboxExecutor.resourceSiteProjectionDemanded(runtime, level,
                        FrontierResourceSitePlan.compile(state.bootstrap()).get(lifecycle.siteId())))
                .sorted(Comparator.comparing(ResourceSiteLifecycle::siteId)).toList();
        List<ResourceSiteLifecycle> candidates = inFlight.isEmpty() ? allCandidates
                : allCandidates.stream().filter(lifecycle -> inFlight.contains(lifecycle.siteId())).toList();
        if (candidates.isEmpty()) return;
        int index = Math.floorMod(STAGE_CURSORS.getOrDefault(runtime, 0), candidates.size());
        STAGE_CURSORS.put(runtime, (index + 1) % candidates.size()); ResourceSiteLifecycle lifecycle = candidates.get(index);
        ResourceSite site = FrontierResourceSitePlan.compile(state.bootstrap()).get(lifecycle.siteId());
        ResourceSiteHarvestJob harvest = lifecycle.activeWork().filter(ResourceSiteHarvestJob.class::isInstance)
                .map(ResourceSiteHarvestJob.class::cast).orElse(null);
        int completed = harvest == null ? 0 : harvest.progress().completedCropSlots();
        int desiredStage = completed > 0 ? ResourceSiteLifecycle.MATURE_STAGE : lifecycle.growthStage();
        StageProjectionResult result = projectLifecycleBounded(level, runtime, state, site, desiredStage, completed);
        if (result == StageProjectionResult.CONFLICT) {
            FrontierV3ResourceSiteLedger ledger = FrontierV3ResourceSiteLedger.get(level);
            FrontierV3ResourceSiteLedger.Claim observedClaim = ledger.claim(site.id()); int observedStage = observedClaim == null ? lifecycle.growthStage() : observedClaim.stage();
            FrontierV3ResourceSiteConflictExecutor.recordLifecycleConflict(level, runtime, ledger, site,
                    firstMismatch(level, site, observedStage).orElse(site.cropSlots().getFirst()),
                    io.farfrontier.palemirror.frontier.v3.model.ResourceSiteDiagnosticProducer.ORDINARY_OBSERVATION_MISMATCH, LifecycleConflictOrigin.ORDINARY_GROWTH,
                    confirmedHarvestRegrowthAdmission(level, state, site, desiredStage, completed, observedClaim).name());
        }
    }
    private static StageProjectionResult projectLifecycleBounded(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                                                  FrontierWorldState state, ResourceSite site, int desiredStage, int completedCropSlots) {
        if (!loaded(level, site)) return StageProjectionResult.DEFERRED;
        FrontierV3ResourceSiteLedger ledger = FrontierV3ResourceSiteLedger.get(level);
        Map<SubjectId, FieldProjectionWork> workBySite = PROJECTION_WORK.computeIfAbsent(runtime, ignored -> new HashMap<>());
        FieldProjectionWork work = workBySite.get(site.id());
        FrontierV3ResourceSiteLedger.Claim claim = ledger.claim(site.id());
        if (work == null) {
            if (claim != null && claim.projection() != null) {
                work = restoreProjectionWork(site, claim);
                if (work == null || !matchesProjectionPrefix(level, site, claim, work)) return StageProjectionResult.CONFLICT;
            } else if (claim == null) {
                Optional<ResourceSiteHarvestJob> unmaterialized = exactUnmaterializedColdHarvest(level, state, site,
                        desiredStage, completedCropSlots);
                // The field ledger is a physical ownership witness, not the owner of a
                // completed canonical successor.  If its SavedData entry is absent after
                // restart, re-establish it only when the entire currently loaded surface
                // is either the exact current successor or its exact terminal predecessor,
                // and the terminal lineage names the exact composed consumer.  A missing
                // or changed cell remains a local conflict; this branch never repairs a
                // look-alike field.
                boolean exactCurrentSurface = matches(level, site, desiredStage);
                // A completed COLD harvest deliberately leaves the owned irrigation and
                // farmland in place while its complete crop cursor is AIR.  That is not
                // the neutral, unprepared baseline.  When the retained lineage proves
                // that this exact terminal receipt has already composed into canonical
                // custody, it is the predecessor of the current successor epoch and
                // must be projected through the same bounded writer on re-entry.
                boolean exactTerminalPredecessorSurface = desiredStage < ResourceSiteLifecycle.MATURE_STAGE
                        && matchesHarvestProgress(level, site, ResourceSiteHarvestProgress.TOTAL_CROP_SLOTS);
                if (allowsComposedTerminalLedgerRehydration(state.resourceSites().site(site.id()), desiredStage,
                        completedCropSlots, exactCurrentSurface || exactTerminalPredecessorSurface,
                        state.resourceSites().site(site.id()).harvestLineage()
                                .map(lineage -> lineage.composedIntoCanonicalSuccessor(state)).orElse(false))) {
                    if (exactTerminalPredecessorSurface) {
                        // Do not convert an exactly identified predecessor receipt with
                        // an unbounded write.  Retain its terminal cursor first, then
                        // rebuild its current successor through the ordinary bounded
                        // writer so a restart sees the terminal receipt at every
                        // uncommitted physical boundary.
                        ledger.reserveComposedTerminalSuccessor(site.id(), projectionClaim(site));
                        ledger.activate(site.id());
                        FrontierV3ResourceSiteLedger.Claim terminal = ledger.claim(site.id());
                        work = new FieldProjectionWork(desiredStage, completedCropSlots,
                                transitionWrites(site, terminal, desiredStage, completedCropSlots, false), 0, false, false);
                    } else {
                        ledger.reserve(site.id(), projectionClaim(site));
                        ledger.activate(site.id());
                        ledger.updateStage(site.id(), desiredStage);
                        return StageProjectionResult.CURRENT;
                    }
                }
                if (work == null) {
                    if (unmaterialized.isEmpty() && !baseline(level, site)) return StageProjectionResult.CONFLICT;
                    ledger.reserve(site.id(), unmaterialized.map(ResourceSiteHarvestJob::intentId).orElseGet(() -> projectionClaim(site)));
                    work = new FieldProjectionWork(desiredStage, completedCropSlots, initialWrites(site, desiredStage, completedCropSlots), 0, true, false);
                }
            } else {
                boolean successorRegrowth = isExactSuccessorRegrowth(state, site, desiredStage, completedCropSlots, claim);
                boolean terminalPredecessor = isExactTerminalPredecessor(level, state, site, desiredStage, completedCropSlots, claim);
                boolean deferredTerminalReceipt = isExactDeferredHarvestReceipt(level, state, site, desiredStage, completedCropSlots, claim);
                boolean confirmedHarvestRegrowth = isExactConfirmedHarvestRegrowth(level, state, site,
                        desiredStage, completedCropSlots, claim);
                boolean composedTerminalRegrowth = isExactComposedTerminalRegrowth(state.resourceSites().site(site.id()),
                        desiredStage, completedCropSlots, claim);
                boolean interruptedStageZero = isExactInterruptedStageZeroProjection(level, state, site, desiredStage, completedCropSlots, claim);
                boolean uncommittedCurrentHarvest = isExactUncommittedCurrentHarvest(level, state, site, desiredStage, completedCropSlots, claim);
                if (claim.status() != FrontierV3ResourceSiteLedger.Status.ACTIVE
                        || (!successorRegrowth && !terminalPredecessor && !deferredTerminalReceipt && !confirmedHarvestRegrowth
                        && !composedTerminalRegrowth && !interruptedStageZero && !uncommittedCurrentHarvest
                        && !matchesClaim(level, site, claim))) return StageProjectionResult.CONFLICT;
                if (claim.stage() == desiredStage && claim.harvestedCropSlots() == completedCropSlots) return StageProjectionResult.CURRENT;
                // COLD has completed the semantic predecessor but a naturally loaded owner
                // has not yet materialized its one exact output.  Keep the complete prior
                // field as the physical receipt witness; do not let projection, player
                // demand, or a restart turn turn that bounded lag into drift or regrowth.
                if (deferredTerminalReceipt) return StageProjectionResult.DEFERRED;
                boolean lawfulRegrowthReset = resetsTerminalHarvestClaim(claim, completedCropSlots,
                        successorRegrowth, confirmedHarvestRegrowth) || composedTerminalRegrowth;
                if (claim.stage() == ResourceSiteLifecycle.MATURE_STAGE && claim.harvestedCropSlots() > completedCropSlots && !lawfulRegrowthReset) {
                    if (!successorRegrowth) return StageProjectionResult.CONFLICT;
                }
                work = new FieldProjectionWork(desiredStage, completedCropSlots,
                        transitionWrites(site, claim, desiredStage, completedCropSlots, successorRegrowth), 0, false, successorRegrowth);
            }
            if (claim == null || claim.projection() == null) beginProjection(ledger, state, site, work);
            workBySite.put(site.id(), work);
        } else if (work.desiredStage() != desiredStage || work.completedCropSlots() != completedCropSlots) {
        }
        int written = 0;
        while (written < MAX_SITE_PROJECTION_WRITES_PER_TICK && !work.complete()) {
            FieldWrite write = work.writes().get(work.nextWrite());
            BlockPos position = minecraft(write.position());
            if (!level.getBlockState(position).equals(write.state()) && !level.setBlock(position, write.state(), 2)) {
                workBySite.remove(site.id()); return StageProjectionResult.CONFLICT;
            }
            if (work.restoresHarvestCursor()) {
                ledger.restoreOne(site.id(), ledger.claim(site.id()).harvestedCropSlots() - 1);
            }
            work = work.advanced(1); ledger.advanceProjection(site.id(), work.nextWrite()); written++;
        }
        if (!work.complete()) { workBySite.put(site.id(), work); return StageProjectionResult.DEFERRED; }
        if (work.activatesReservedClaim()) ledger.activate(site.id());
        claim = ledger.claim(site.id());
        if (claim == null || claim.status() != FrontierV3ResourceSiteLedger.Status.ACTIVE) { workBySite.remove(site.id()); return StageProjectionResult.CONFLICT; }
        ledger.updateStage(site.id(), work.desiredStage());
        for (int cursor = ledger.claim(site.id()).harvestedCropSlots(); cursor < work.completedCropSlots(); cursor++) ledger.harvestOne(site.id(), cursor + 1);
        boolean exact = work.desiredStage() == ResourceSiteLifecycle.MATURE_STAGE
                ? matchesHarvestProgress(level, site, work.completedCropSlots()) : matches(level, site, work.desiredStage());
        if (exact) ledger.completeProjection(site.id());
        workBySite.remove(site.id());
        return exact ? StageProjectionResult.UPDATED : StageProjectionResult.CONFLICT;
    }
    /**
     * An initial field is a live vanilla surface while its bounded cursor is advancing.  Do not
     * leave a whole row of newly tilled soil bare until a later turn: vanilla may turn that
     * soil back into dirt before the durable writer reaches the corresponding crop.  Each
     * soil/crop pair therefore crosses the physical boundary together, after the four fixed
     * irrigation cells.  The fixed even write budget preserves that pairing across turns.
     */
    private static List<FieldWrite> initialWrites(ResourceSite site, int desiredStage, int completedCropSlots) {
        List<FieldWrite> writes = new java.util.ArrayList<>();
        for (BlockPosition position : initialProjectionSlotOrder(site)) {
            if (site.irrigationSlots().contains(position)) writes.add(new FieldWrite(position, Blocks.WATER.defaultBlockState()));
            else if (site.soilSlots().contains(position)) writes.add(new FieldWrite(position, Blocks.FARMLAND.defaultBlockState()));
            else {
                int index = site.cropSlots().indexOf(position);
                writes.add(new FieldWrite(position, desiredStage == ResourceSiteLifecycle.MATURE_STAGE && index < completedCropSlots
                        ? Blocks.AIR.defaultBlockState() : crop(desiredStage)));
            }
        }
        return List.copyOf(writes);
    }

    /** Pure ordered physical ownership boundary for an INITIAL field cursor. */
    static List<BlockPosition> initialProjectionSlotOrder(ResourceSite site) {
        List<BlockPosition> order = new java.util.ArrayList<>();
        order.addAll(site.irrigationSlots());
        for (BlockPosition cropSlot : site.cropSlots()) {
            order.add(cropSlot.offset(0, -1, 0));
            order.add(cropSlot);
        }
        return List.copyOf(order);
    }
    private static void beginProjection(FrontierV3ResourceSiteLedger ledger, FrontierWorldState state, ResourceSite site,
                                        FieldProjectionWork work) {
        FrontierV3ResourceSiteLedger.Claim claim = ledger.claim(site.id());
        if (claim == null) throw new IllegalStateException("v3 resource site projection has no reservation");
        FrontierV3ResourceSiteLedger.ProjectionMode mode = work.activatesReservedClaim()
                ? FrontierV3ResourceSiteLedger.ProjectionMode.INITIAL
                : work.restoresHarvestCursor() ? FrontierV3ResourceSiteLedger.ProjectionMode.SUCCESSOR_RESTORE
                : FrontierV3ResourceSiteLedger.ProjectionMode.ADVANCE;
        ledger.beginProjection(site.id(), new FrontierV3ResourceSiteLedger.ProjectionTransition(
                projectionSource(state, site), claim.stage(), claim.harvestedCropSlots(), work.desiredStage(),
                work.completedCropSlots(), work.nextWrite(), work.writes().size(), mode));
    }
    private static FieldProjectionWork restoreProjectionWork(ResourceSite site, FrontierV3ResourceSiteLedger.Claim claim) {
        FrontierV3ResourceSiteLedger.ProjectionTransition projection = claim.projection();
        FrontierV3ResourceSiteLedger.Claim origin = new FrontierV3ResourceSiteLedger.Claim(claim.intentId(), claim.status(),
                projection.fromStage(), projection.fromHarvestedCropSlots());
        List<FieldWrite> writes = switch (projection.mode()) {
            case INITIAL -> initialWrites(site, projection.targetStage(), projection.targetHarvestedCropSlots());
            case ADVANCE -> transitionWrites(site, origin, projection.targetStage(), projection.targetHarvestedCropSlots(), false);
            case SUCCESSOR_RESTORE -> transitionWrites(site, origin, projection.targetStage(), projection.targetHarvestedCropSlots(), true);
        };
        if (writes.size() != projection.writeCount()) return null;
        return new FieldProjectionWork(projection.targetStage(), projection.targetHarvestedCropSlots(), writes,
                projection.nextWrite(), projection.mode() == FrontierV3ResourceSiteLedger.ProjectionMode.INITIAL,
                projection.mode() == FrontierV3ResourceSiteLedger.ProjectionMode.SUCCESSOR_RESTORE);
    }
    /** Read-only restart seam for a persisted bounded field cursor. */
    static boolean matchesPersistedProjectionPrefix(ServerLevel level, ResourceSite site, FrontierV3ResourceSiteLedger.Claim claim) {
        FieldProjectionWork work = restoreProjectionWork(site, claim);
        return work != null && matchesProjectionPrefix(level, site, claim, work);
    }
    private static boolean matchesProjectionPrefix(ServerLevel level, ResourceSite site, FrontierV3ResourceSiteLedger.Claim claim,
                                                   FieldProjectionWork work) {
        Map<BlockPosition, BlockState> committed = new HashMap<>();
        for (int index = 0; index < work.nextWrite(); index++) {
            FieldWrite write = work.writes().get(index); committed.put(write.position(), write.state());
        }
        for (BlockPosition slot : site.managedSlots()) {
            BlockState expected = committed.get(slot);
            if (expected == null) {
                if (work.activatesReservedClaim()) expected = Blocks.AIR.defaultBlockState();
                else expected = expectedClaimBlock(site, claim.projection(), slot);
            }
            if (!level.getBlockState(minecraft(slot)).equals(expected)) return false;
        }
        return true;
    }
    private static BlockState expectedClaimBlock(ResourceSite site, FrontierV3ResourceSiteLedger.ProjectionTransition projection,
                                                 BlockPosition slot) {
        if (site.irrigationSlots().contains(slot)) return Blocks.WATER.defaultBlockState();
        if (site.soilSlots().contains(slot)) return Blocks.FARMLAND.defaultBlockState();
        int index = site.cropSlots().indexOf(slot);
        if (index < 0) throw new IllegalArgumentException("unmanaged projection slot");
        return projection.fromStage() == ResourceSiteLifecycle.MATURE_STAGE && index < projection.fromHarvestedCropSlots()
                ? Blocks.AIR.defaultBlockState() : crop(projection.fromStage());
    }
    private static String projectionSource(FrontierWorldState state, ResourceSite site) {
        ResourceSiteLifecycle lifecycle = state.resourceSites().site(site.id());
        return lifecycle.activeWork().filter(ResourceSiteHarvestJob.class::isInstance).map(ResourceSiteHarvestJob.class::cast)
                .map(job -> job.id().value()).orElse("growth:" + site.id().value() + ":e" + lifecycle.growthEpoch());
    }
    private static List<FieldWrite> transitionWrites(ResourceSite site, FrontierV3ResourceSiteLedger.Claim claim,
                                                     int desiredStage, int completedCropSlots, boolean successorRegrowth) {
        List<FieldWrite> writes = new java.util.ArrayList<>();
        if (claim.stage() != desiredStage) {
            for (int index = 0; index < site.cropSlots().size(); index++) writes.add(new FieldWrite(site.cropSlots().get(index),
                    desiredStage == ResourceSiteLifecycle.MATURE_STAGE && index < completedCropSlots ? Blocks.AIR.defaultBlockState() : crop(desiredStage)));
        } else if (desiredStage == ResourceSiteLifecycle.MATURE_STAGE) {
            if (successorRegrowth) {
                int restore = successorRegrowthRestoreSlots(claim.harvestedCropSlots(), completedCropSlots);
                for (int index = claim.harvestedCropSlots() - 1; index >= claim.harvestedCropSlots() - restore; index--) {
                    writes.add(new FieldWrite(site.cropSlots().get(index), crop(ResourceSiteLifecycle.MATURE_STAGE)));
                }
            } else for (int index = claim.harvestedCropSlots(); index < completedCropSlots; index++)
                writes.add(new FieldWrite(site.cropSlots().get(index), Blocks.AIR.defaultBlockState()));
        }
        return List.copyOf(writes);
    }
    static int successorRegrowthRestoreSlots(int predecessorHarvestedCropSlots, int successorCompletedCropSlots) {
        if (predecessorHarvestedCropSlots < 0 || predecessorHarvestedCropSlots > ResourceSiteHarvestProgress.TOTAL_CROP_SLOTS
                || successorCompletedCropSlots < 0 || successorCompletedCropSlots > predecessorHarvestedCropSlots) {
            throw new IllegalArgumentException("successor regrowth cursor is invalid");
        }
        return predecessorHarvestedCropSlots - successorCompletedCropSlots;
    }
    static boolean resetsTerminalHarvestClaim(FrontierV3ResourceSiteLedger.Claim claim, int completedCropSlots,
                                              boolean successorRegrowth, boolean confirmedHarvestRegrowth) {
        return claim.stage() == ResourceSiteLifecycle.MATURE_STAGE
                && claim.harvestedCropSlots() == ResourceSiteHarvestProgress.TOTAL_CROP_SLOTS
                && claim.harvestedCropSlots() > completedCropSlots
                && (successorRegrowth || confirmedHarvestRegrowth);
    }
    static boolean allowsOwnedStageCatchUp(FrontierV3ResourceSiteLedger.Claim claim, int desiredStage,
                                           int completedCropSlots) {
        return claim != null && claim.status() == FrontierV3ResourceSiteLedger.Status.ACTIVE
                && claim.harvestedCropSlots() == 0 && completedCropSlots == 0
                && claim.stage() >= 0 && claim.stage() < desiredStage
                && desiredStage <= ResourceSiteLifecycle.MATURE_STAGE;
    }

    /**
     * Re-entry may rebuild a missing physical ownership witness only for a complete current
     * surface whose exact terminal lineage still names its canonical successor.  This is not
     * a permissive recovery of a damaged field: callers must provide an exact whole-surface
     * observation and a composed successor relation.
     */
    static boolean allowsComposedTerminalLedgerRehydration(ResourceSiteLifecycle lifecycle, int desiredStage,
                                                            int completedCropSlots, boolean exactCurrentSurface,
                                                            boolean composedCanonicalSuccessor) {
        if (lifecycle == null || completedCropSlots != 0 || desiredStage != lifecycle.growthStage()
                || !exactCurrentSurface || !composedCanonicalSuccessor) return false;
        if (lifecycle.phase() != ResourceSitePhase.GROWING && lifecycle.phase() != ResourceSitePhase.READY) return false;
        return lifecycle.harvestLineage().map(lineage -> lineage.outputReceiptResolved()
                && lineage.completedGrowthEpoch() + 1L == lifecycle.growthEpoch()).orElse(false);
    }
    private static String claimPhysicalState(ServerLevel level, FrontierV3ResourceSiteLedger ledger, ResourceSite site, FrontierV3ResourceSiteLedger.Claim claim) {
        String physical;
        if (claim == null) physical = "MISSING_CLAIM";
        else if (matchesClaim(level, site, claim)) physical = "EXACT_CLAIM";
        else if (baseline(level, site)) physical = "NEUTRAL_BASELINE";
        else if (matchesHarvestProgress(level, site, ResourceSiteHarvestProgress.TOTAL_CROP_SLOTS)) physical = "TERMINAL_HARVEST_RECEIPT";
        else {
            physical = null;
            for (int stage = 0; stage <= ResourceSiteLifecycle.MATURE_STAGE; stage++) {
                if (matches(level, site, stage)) { physical = "COMPLETE_STAGE_" + stage; break; }
            }
            if (physical == null) physical = "FOREIGN_OR_DAMAGED_" + cropSurface(level, site);
        }
        FrontierV3ResourceSiteLedger.NativeGrowthFence fence = ledger.nativeGrowthFence(site.id());
        return physical + "_NATIVE_FENCE_" + (fence == null ? "NOT_OBSERVED" : fence.source() + "_A" + fence.observedAge()
                + "_S" + fence.claimStage() + "_P" + fence.position().x() + "_" + fence.position().y() + "_" + fence.position().z());
    }
    private static String cropSurface(ServerLevel level, ResourceSite site) {
        if (!matchesInfrastructure(level, site)) return "INFRASTRUCTURE_MISMATCH";
        int[] ages = new int[ResourceSiteLifecycle.MATURE_STAGE + 1]; int air = 0; int other = 0;
        for (BlockPosition slot : site.cropSlots()) {
            BlockState observed = level.getBlockState(minecraft(slot));
            if (observed.isAir()) air++;
            else if (observed.is(Blocks.WHEAT)) ages[observed.getValue(CropBlock.AGE)]++;
            else other++;
        }
        StringBuilder surface = new StringBuilder("CROPS");
        for (int age = 0; age < ages.length; age++) surface.append("_A").append(age).append('_').append(ages[age]);
        return surface.append("_AIR_").append(air).append("_OTHER_").append(other).toString();
    }
    private static boolean isExactSuccessorRegrowth(FrontierWorldState state, ResourceSite site, int desiredStage,
                                                    int completedCropSlots, FrontierV3ResourceSiteLedger.Claim claim) {
        if (claim == null || desiredStage != ResourceSiteLifecycle.MATURE_STAGE
                || claim.stage() != ResourceSiteLifecycle.MATURE_STAGE
                || claim.harvestedCropSlots() <= completedCropSlots) return false;
        ResourceSiteLifecycle lifecycle = state.resourceSites().site(site.id());
        return lifecycle.activeWork().filter(ResourceSiteHarvestJob.class::isInstance)
                .map(ResourceSiteHarvestJob.class::cast)
                .map(job -> job.progress().completedCropSlots() == completedCropSlots).orElse(false);
    }
    private static boolean isExactTerminalPredecessor(ServerLevel level, FrontierWorldState state, ResourceSite site,
                                                      int desiredStage, int completedCropSlots, FrontierV3ResourceSiteLedger.Claim claim) {
        if (claim == null || claim.status() != FrontierV3ResourceSiteLedger.Status.ACTIVE
                || desiredStage != ResourceSiteLifecycle.MATURE_STAGE || completedCropSlots <= 0
                || !matchesHarvestProgress(level, site, ResourceSiteHarvestProgress.TOTAL_CROP_SLOTS)) return false;
        return state.resourceSites().site(site.id()).activeWork().filter(ResourceSiteHarvestJob.class::isInstance)
                .map(ResourceSiteHarvestJob.class::cast)
                .map(job -> job.progress().completedCropSlots() == completedCropSlots).orElse(false);
    }
    private static boolean isExactConfirmedHarvestRegrowth(ServerLevel level, FrontierWorldState state, ResourceSite site,
                                                           int desiredStage, int completedCropSlots,
                                                           FrontierV3ResourceSiteLedger.Claim claim) {
        return confirmedHarvestRegrowthAdmission(level, state, site, desiredStage, completedCropSlots, claim)
                == ConfirmedHarvestRegrowthAdmission.ADMITTED;
    }
    /**
     * A COLD terminal has already atomically composed its exact output and retired its
     * PREPARED intent.  The durable owned field can still be the older HOT prefix: after a
     * restart it is lawful to replace that exact prefix with the current growth stage, but only
     * when the immediately preceding lineage proves that canonical terminal hand-off.
     */
    static boolean isExactComposedTerminalRegrowth(ResourceSiteLifecycle lifecycle, int desiredStage,
                                                    int completedCropSlots, FrontierV3ResourceSiteLedger.Claim claim) {
        if (claim == null || claim.status() != FrontierV3ResourceSiteLedger.Status.ACTIVE
                || claim.stage() != ResourceSiteLifecycle.MATURE_STAGE || claim.harvestedCropSlots() <= 0
                || completedCropSlots != 0) return false;
        return lifecycle.phase() == ResourceSitePhase.GROWING && desiredStage == lifecycle.growthStage()
                && lifecycle.harvestLineage().map(ResourceSiteHarvestLineage::outputReceiptResolved).orElse(false);
    }
    private static boolean isExactDeferredHarvestReceipt(ServerLevel level, FrontierWorldState state, ResourceSite site,
                                                          int desiredStage, int completedCropSlots,
                                                          FrontierV3ResourceSiteLedger.Claim claim) {
        if (claim == null || claim.status() != FrontierV3ResourceSiteLedger.Status.ACTIVE
                || claim.stage() != ResourceSiteLifecycle.MATURE_STAGE
                || claim.harvestedCropSlots() != ResourceSiteHarvestProgress.TOTAL_CROP_SLOTS
                || !matchesHarvestProgress(level, site, ResourceSiteHarvestProgress.TOTAL_CROP_SLOTS)) return false;
        ResourceSiteLifecycle lifecycle = state.resourceSites().site(site.id());
        return lifecycle.phase() == ResourceSitePhase.GROWING && desiredStage == lifecycle.growthStage() && completedCropSlots == 0
                && lifecycle.harvestLineage().filter(ResourceSiteHarvestLineage::receiptPending)
                .filter(lineage -> !lineage.composedIntoCanonicalSuccessor(state)).isPresent();
    }
    private static ConfirmedHarvestRegrowthAdmission confirmedHarvestRegrowthAdmission(ServerLevel level, FrontierWorldState state,
                                                                                         ResourceSite site, int desiredStage,
                                                                                         int completedCropSlots,
                                                                                         FrontierV3ResourceSiteLedger.Claim claim) {
        ResourceSiteLifecycle lifecycle = state.resourceSites().site(site.id());
        boolean confirmedReceipt = hasConfirmedHarvestReceipt(state, site.id());
        return classifyConfirmedHarvestRegrowth(claim, lifecycle, desiredStage, completedCropSlots,
                matchesHarvestProgress(level, site, ResourceSiteHarvestProgress.TOTAL_CROP_SLOTS), confirmedReceipt);
    }
    private static boolean hasConfirmedHarvestReceipt(FrontierWorldState state, SubjectId siteId) {
        return state.physicalIntents().values().stream().anyMatch(intent -> intent.kind() == PhysicalIntentKind.RESOURCE_SITE_HARVEST
                && intent.status() == PhysicalIntentStatus.CONFIRMED && intent.causeSubjectId().equals(siteId));
    }
    static ConfirmedHarvestRegrowthAdmission classifyConfirmedHarvestRegrowth(FrontierV3ResourceSiteLedger.Claim claim,
                                                                                ResourceSiteLifecycle lifecycle,
                                                                                int desiredStage, int completedCropSlots,
                                                                                boolean exactTerminalPhysicalReceipt,
                                                                                boolean confirmedHarvestReceipt) {
        if (claim == null || claim.status() != FrontierV3ResourceSiteLedger.Status.ACTIVE
                || claim.stage() != ResourceSiteLifecycle.MATURE_STAGE
                || claim.harvestedCropSlots() != ResourceSiteHarvestProgress.TOTAL_CROP_SLOTS) {
            return ConfirmedHarvestRegrowthAdmission.MISSING_ACTIVE_TERMINAL_CLAIM;
        }
        if (lifecycle.phase() != ResourceSitePhase.GROWING || desiredStage != lifecycle.growthStage() || completedCropSlots != 0) {
            return ConfirmedHarvestRegrowthAdmission.LIFECYCLE_NOT_INITIAL_GROWTH;
        }
        if (!exactTerminalPhysicalReceipt) return ConfirmedHarvestRegrowthAdmission.PHYSICAL_RECEIPT_MISMATCH;
        return confirmedHarvestReceipt ? ConfirmedHarvestRegrowthAdmission.ADMITTED
                : ConfirmedHarvestRegrowthAdmission.MISSING_CONFIRMED_RECEIPT;
    }
    private static boolean isExactInterruptedStageZeroProjection(ServerLevel level, FrontierWorldState state, ResourceSite site,
                                                                 int desiredStage, int completedCropSlots,
                                                                 FrontierV3ResourceSiteLedger.Claim claim) {
        if (claim == null || claim.status() != FrontierV3ResourceSiteLedger.Status.ACTIVE || claim.stage() != 0
                || desiredStage != ResourceSiteLifecycle.MATURE_STAGE || completedCropSlots <= 0
                || !matchesInfrastructure(level, site)) return false;
        boolean reachedAirSuffix = false;
        for (BlockPosition crop : site.cropSlots()) {
            BlockState observed = level.getBlockState(minecraft(crop));
            if (observed.equals(crop(0))) {
                if (reachedAirSuffix) return false;
            } else if (observed.isAir()) reachedAirSuffix = true;
            else return false;
        }
        return state.resourceSites().site(site.id()).activeWork().filter(ResourceSiteHarvestJob.class::isInstance)
                .map(ResourceSiteHarvestJob.class::cast)
                .map(job -> job.progress().completedCropSlots() == completedCropSlots).orElse(false);
    }
    private static boolean isExactUncommittedCurrentHarvest(ServerLevel level, FrontierWorldState state, ResourceSite site,
                                                            int desiredStage, int completedCropSlots,
                                                            FrontierV3ResourceSiteLedger.Claim claim) {
        if (claim == null || claim.status() != FrontierV3ResourceSiteLedger.Status.ACTIVE
                ) return false;
        return exactCurrentHarvestJob(level, state, site, desiredStage, completedCropSlots).isPresent();
    }
    private static Optional<ResourceSiteHarvestJob> exactCurrentHarvestJob(ServerLevel level, FrontierWorldState state, ResourceSite site,
                                                                             int desiredStage, int completedCropSlots) {
        if (desiredStage != ResourceSiteLifecycle.MATURE_STAGE || completedCropSlots <= 0
                || !matchesHarvestProgress(level, site, completedCropSlots)) return Optional.empty();
        return namedCurrentHarvestJob(state, site, desiredStage, completedCropSlots);
    }
    private static Optional<ResourceSiteHarvestJob> namedCurrentHarvestJob(FrontierWorldState state, ResourceSite site,
                                                                            int desiredStage, int completedCropSlots) {
        if (desiredStage != ResourceSiteLifecycle.MATURE_STAGE || completedCropSlots <= 0) return Optional.empty();
        return state.resourceSites().site(site.id()).activeWork().filter(ResourceSiteHarvestJob.class::isInstance)
                .map(ResourceSiteHarvestJob.class::cast)
                .filter(job -> job.progress().completedCropSlots() == completedCropSlots);
    }
    private static Optional<ResourceSiteHarvestJob> exactUnmaterializedColdHarvest(ServerLevel level, FrontierWorldState state,
                                                                                     ResourceSite site, int desiredStage,
                                                                                     int completedCropSlots) {
        if (!blankManagedSurface(level, site)) return Optional.empty();
        return namedCurrentHarvestJob(state, site, desiredStage, completedCropSlots).filter(job -> {
            PhysicalIntent intent = state.physicalIntents().get(job.intentId());
            return intent != null && intent.kind() == PhysicalIntentKind.RESOURCE_SITE_HARVEST
                    && intent.status() == PhysicalIntentStatus.PREPARED && intent.causeSubjectId().equals(site.id())
                    && intent.roles().equals(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentRoleBinding.siteHarvest(site.id(), job.id(), job.workerId(), job.outputItemId()));
        });
    }
    private static boolean blankManagedSurface(ServerLevel level, ResourceSite site) {
        return loaded(level, site) && site.managedSlots().stream().allMatch(slot -> level.getBlockState(minecraft(slot)).isAir());
    }
    static StageProjectionResult projectStage(ServerLevel level, FrontierV3ResourceSiteLedger ledger, ResourceSite site, int desiredStage) {
        if (desiredStage < 0 || desiredStage > 7) throw new IllegalArgumentException("resource-site crop stage is invalid");
        if (!loaded(level, site)) return StageProjectionResult.DEFERRED;
        FrontierV3ResourceSiteLedger.Claim claim = ledger.claim(site.id());
        boolean wroteNeutralBaseline = false;
        if (claim == null) {
            if (!baseline(level, site)) return StageProjectionResult.CONFLICT;
            ledger.reserve(site.id(), projectionClaim(site));
            if (!placeWholeField(level, site)) return StageProjectionResult.CONFLICT;
            ledger.activate(site.id()); claim = ledger.claim(site.id()); wroteNeutralBaseline = true;
        }
        if (claim == null || claim.status() != FrontierV3ResourceSiteLedger.Status.ACTIVE) return StageProjectionResult.CONFLICT;
        if (!wroteNeutralBaseline && !matchesClaim(level, site, claim)) return StageProjectionResult.CONFLICT;
        if (claim.stage() == desiredStage) return StageProjectionResult.CURRENT;
        for (BlockPosition crop : site.cropSlots()) level.setBlock(minecraft(crop), crop(desiredStage), 3);
        if (!matches(level, site, desiredStage)) return StageProjectionResult.CONFLICT;
        ledger.updateStage(site.id(), desiredStage); return StageProjectionResult.UPDATED;
    }
    static StageProjectionResult projectHarvestProgress(ServerLevel level, FrontierV3ResourceSiteLedger ledger, ResourceSite site, int completedCropSlots) {
        ManagedFacilityProgressProjection projection = harvestProjection(site, completedCropSlots);
        if (!loaded(level, site)) return StageProjectionResult.DEFERRED;
        StageProjectionResult mature = projectStage(level, ledger, site, ResourceSiteLifecycle.MATURE_STAGE);
        if (mature == StageProjectionResult.CONFLICT || mature == StageProjectionResult.DEFERRED) return mature;
        FrontierV3ResourceSiteLedger.Claim claim = ledger.claim(site.id());
        if (claim == null || claim.status() != FrontierV3ResourceSiteLedger.Status.ACTIVE || claim.stage() != ResourceSiteLifecycle.MATURE_STAGE
                || !matchesHarvestProgress(level, site, claim.harvestedCropSlots()) || claim.harvestedCropSlots() > completedCropSlots) {
            return StageProjectionResult.CONFLICT;
        }
        if (claim.harvestedCropSlots() == completedCropSlots) return StageProjectionResult.CURRENT;
        for (int index = claim.harvestedCropSlots(); index < projection.completedSlots(); index++) {
            level.setBlock(minecraft(projection.slotPlan().get(index)), Blocks.AIR.defaultBlockState(), 3);
            if (!matchesHarvestProgress(level, site, index + 1)) return StageProjectionResult.CONFLICT;
            ledger.harvestOne(site.id(), index + 1);
        }
        return StageProjectionResult.UPDATED;
    }
    static RestartReconciliation reconcileAfterRestart(ServerLevel level, FrontierV3ResourceSiteLedger ledger, ResourceSite site,
                                                        PhysicalIntentId intentId, int desiredStage) {
        if (!loaded(level, site)) return RestartReconciliation.DEFERRED;
        FrontierV3ResourceSiteLedger.Claim claim = ledger.claim(site.id());
        if (claim == null || !ownsRestartClaim(claim.intentId(), site, intentId) || claim.status() != FrontierV3ResourceSiteLedger.Status.ACTIVE) {
            return RestartReconciliation.CONFLICT;
        }
        if (matches(level, site, desiredStage)) {
            if (claim.stage() != desiredStage) ledger.updateStage(site.id(), desiredStage);
            return RestartReconciliation.CURRENT;
        }
        if (!baseline(level, site)) return RestartReconciliation.CONFLICT;
        if (claim.stage() != 0) ledger.updateStage(site.id(), 0);
        if (!placeWholeField(level, site)) return RestartReconciliation.CONFLICT;
        StageProjectionResult projected = projectStage(level, ledger, site, desiredStage);
        return projected == StageProjectionResult.CURRENT || projected == StageProjectionResult.UPDATED
                ? RestartReconciliation.RECREATED : RestartReconciliation.CONFLICT;
    }
    private static void reconcileOneAfterRestart(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state) {
        Set<SubjectId> pending = RECOVERY_SITES.get(runtime); if (pending == null || pending.isEmpty()) return;
        for (SubjectId siteId : pending.stream().sorted().toList()) {
            ResourceSite site = FrontierResourceSitePlan.compile(state.bootstrap()).get(siteId);
            ResourceSiteLifecycle lifecycle = state.resourceSites().site(siteId);
            if (site == null || !loaded(level, site) || !FrontierV3GrayboxExecutor.resourceSiteProjectionDemanded(runtime, level, site)) continue;
            PhysicalIntent intent = state.physicalIntents().values().stream().filter(candidate -> candidate.kind() == PhysicalIntentKind.RESOURCE_SITE_PREPARATION
                    && candidate.status() == PhysicalIntentStatus.CONFIRMED && candidate.causeSubjectId().equals(siteId)).findFirst().orElse(null);
            RestartReconciliation result;
            ResourceSiteHarvestJob harvest = lifecycle.activeWork().filter(ResourceSiteHarvestJob.class::isInstance)
                    .map(ResourceSiteHarvestJob.class::cast).orElse(null);
            if (harvest != null && harvest.progress().completedCropSlots() > 0) {
                FrontierV3ResourceSiteLedger.Claim claim = FrontierV3ResourceSiteLedger.get(level).claim(site.id());
                Optional<ResourceSiteHarvestJob> unmaterialized = exactUnmaterializedColdHarvest(level, state, site,
                        ResourceSiteLifecycle.MATURE_STAGE, harvest.progress().completedCropSlots());
                if (claim != null && claim.projection() != null) {
                    result = boundedRestartProjection(level, runtime, state, site,
                            ResourceSiteLifecycle.MATURE_STAGE, harvest.progress().completedCropSlots());
                } else if (isExactSuccessorRegrowth(state, site, ResourceSiteLifecycle.MATURE_STAGE,
                        harvest.progress().completedCropSlots(), claim)
                        || isExactTerminalPredecessor(level, state, site, ResourceSiteLifecycle.MATURE_STAGE,
                                harvest.progress().completedCropSlots(), claim)
                        || isExactInterruptedStageZeroProjection(level, state, site, ResourceSiteLifecycle.MATURE_STAGE,
                                harvest.progress().completedCropSlots(), claim)
                        || isExactUncommittedCurrentHarvest(level, state, site, ResourceSiteLifecycle.MATURE_STAGE,
                                harvest.progress().completedCropSlots(), claim)
                        || unmaterialized.isPresent()) {
                    result = boundedRestartProjection(level, runtime, state, site,
                            ResourceSiteLifecycle.MATURE_STAGE, harvest.progress().completedCropSlots());
                } else if (projectionInFlight(runtime, site.id())) {
                    result = boundedRestartProjection(level, runtime, state, site,
                            ResourceSiteLifecycle.MATURE_STAGE, harvest.progress().completedCropSlots());
                } else {
                    HarvestRestartClassification classification = classifyHarvestRestart(level, FrontierV3ResourceSiteLedger.get(level), site, intent,
                            harvest.progress().completedCropSlots());
                    result = switch (classification.state()) {
                        case UNOBSERVED -> RestartReconciliation.DEFERRED;
                        case OWNED_EXACT -> RestartReconciliation.CURRENT;
                        case OWNED_BEHIND -> boundedRestartProjection(level, runtime, state, site,
                                ResourceSiteLifecycle.MATURE_STAGE, harvest.progress().completedCropSlots());
                        case NEUTRAL_UNCLAIMED -> intent == null ? RestartReconciliation.CONFLICT
                                : boundedRestartProjection(level, runtime, state, site,
                                        ResourceSiteLifecycle.MATURE_STAGE, harvest.progress().completedCropSlots());
                        case FOREIGN_OR_DAMAGED, UNKNOWN -> RestartReconciliation.CONFLICT;
                    };
                }
            } else if (harvest != null && isExactSuccessorRegrowth(state, site, lifecycle.growthStage(), 0,
                    FrontierV3ResourceSiteLedger.get(level).claim(site.id()))) {
                result = boundedRestartProjection(level, runtime, state, site, lifecycle.growthStage(), 0);
            } else if (isExactDeferredHarvestReceipt(level, state, site, lifecycle.growthStage(), 0,
                    FrontierV3ResourceSiteLedger.get(level).claim(site.id()))) {
                result = RestartReconciliation.DEFERRED;
            } else if (isExactConfirmedHarvestRegrowth(level, state, site, lifecycle.growthStage(), 0,
                    FrontierV3ResourceSiteLedger.get(level).claim(site.id()))) {
                result = boundedRestartProjection(level, runtime, state, site, lifecycle.growthStage(), 0);
            } else if (isExactComposedTerminalRegrowth(lifecycle, lifecycle.growthStage(), 0,
                    FrontierV3ResourceSiteLedger.get(level).claim(site.id())
                    ) && matchesClaim(level, site, FrontierV3ResourceSiteLedger.get(level).claim(site.id()))) {
                result = boundedRestartProjection(level, runtime, state, site, lifecycle.growthStage(), 0);
            } else if (allowsOwnedStageCatchUp(FrontierV3ResourceSiteLedger.get(level).claim(site.id()),
                    lifecycle.growthStage(), 0)
                    && matchesClaim(level, site, FrontierV3ResourceSiteLedger.get(level).claim(site.id()))) {
                result = boundedRestartProjection(level, runtime, state, site, lifecycle.growthStage(), 0);
            } else if (intent == null) {
                result = boundedRestartProjection(level, runtime, state, site, lifecycle.growthStage(), 0);
            } else result = reconcileAfterRestart(level, FrontierV3ResourceSiteLedger.get(level), site, intent.id(), lifecycle.growthStage());
            if (result != RestartReconciliation.DEFERRED) pending.remove(siteId);
            if (result == RestartReconciliation.CONFLICT) {
                FrontierV3ResourceSiteLedger ledger = FrontierV3ResourceSiteLedger.get(level);
                FrontierV3ResourceSiteLedger.Claim claim = ledger.claim(site.id());
                FrontierV3ResourceSiteConflictExecutor.recordLifecycleConflict(level, runtime, ledger, site,
                        firstMismatch(level, site, lifecycle.growthStage()).orElse(site.cropSlots().getFirst()),
                        io.farfrontier.palemirror.frontier.v3.model.ResourceSiteDiagnosticProducer.RESTART_OBSERVATION_MISMATCH, LifecycleConflictOrigin.RESTART_RECONCILIATION,
                        "NOT_EVALUATED", claimPhysicalState(level, ledger, site, claim));
            }
            if (pending.isEmpty()) RECOVERY_SITES.remove(runtime);
            return;
        }
    }
    private static RestartReconciliation boundedRestartProjection(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                                                   FrontierWorldState state, ResourceSite site, int desiredStage, int completedCropSlots) {
        StageProjectionResult projection = projectLifecycleBounded(level, runtime, state, site, desiredStage, completedCropSlots);
        return projection == StageProjectionResult.CONFLICT ? RestartReconciliation.CONFLICT
                : projection == StageProjectionResult.DEFERRED ? RestartReconciliation.DEFERRED : RestartReconciliation.CURRENT;
    }
    private static boolean projectionInFlight(FrontierV3ServerRuntime<?, ?> runtime, SubjectId siteId) {
        return PROJECTION_WORK.getOrDefault(runtime, Map.of()).containsKey(siteId);
    }
    static boolean hasProjectionInFlight(FrontierV3ServerRuntime<?, ?> runtime) {
        return !PROJECTION_WORK.getOrDefault(runtime, Map.of()).isEmpty();
    }
    static String projectionBlockingDescription(FrontierV3ServerRuntime<?, ?> runtime) {
        return PROJECTION_WORK.getOrDefault(runtime, Map.of()).keySet().stream().sorted()
                .map(SubjectId::value).collect(java.util.stream.Collectors.joining(",", "resource-site-projection:", ""));
    }
    static RestartReconciliation reconcileHarvestAfterRestart(ServerLevel level, FrontierV3ResourceSiteLedger ledger, ResourceSite site,
                                                              PhysicalIntent preparationIntent, int completedCropSlots) {
        HarvestRestartClassification classification = classifyHarvestRestart(level, ledger, site, preparationIntent, completedCropSlots);
        return switch (classification.state()) {
            case UNOBSERVED -> RestartReconciliation.DEFERRED;
            case OWNED_EXACT -> RestartReconciliation.CURRENT;
            case OWNED_BEHIND -> projectHarvestReconciliation(level, ledger, site, completedCropSlots);
            case NEUTRAL_UNCLAIMED -> preparationIntent == null ? RestartReconciliation.CONFLICT
                    : recreateNeutralHarvestField(level, ledger, site, completedCropSlots);
            case FOREIGN_OR_DAMAGED, UNKNOWN -> RestartReconciliation.CONFLICT;
        };
    }
    static HarvestRestartClassification classifyHarvestRestart(ServerLevel level, FrontierV3ResourceSiteLedger ledger, ResourceSite site,
                                                                PhysicalIntent preparationIntent, int completedCropSlots) {
        ManagedFacilityProgressProjection projection = harvestProjection(site, completedCropSlots);
        BlockPosition fallback = projection.slotPlan().getFirst();
        if (!loaded(level, site)) return new HarvestRestartClassification(HarvestRestartPhysicalState.UNOBSERVED, projection, fallback);
        FrontierV3ResourceSiteLedger.Claim claim = ledger.claim(site.id());
        if (claim == null) return new HarvestRestartClassification(baseline(level, site)
                ? HarvestRestartPhysicalState.NEUTRAL_UNCLAIMED : HarvestRestartPhysicalState.FOREIGN_OR_DAMAGED, projection, fallback);
        if (claim.status() == FrontierV3ResourceSiteLedger.Status.PENDING) {
            return new HarvestRestartClassification(HarvestRestartPhysicalState.UNKNOWN, projection, fallback);
        }
        if (!ownsRestartClaim(claim.intentId(), site, preparationIntent == null ? null : preparationIntent.id())) {
            return new HarvestRestartClassification(HarvestRestartPhysicalState.FOREIGN_OR_DAMAGED, projection, fallback);
        }
        boolean exactPhysical = claim.stage() == ResourceSiteLifecycle.MATURE_STAGE
                ? matchesHarvestProgress(level, site, claim.harvestedCropSlots()) : matches(level, site, claim.stage());
        HarvestRestartPhysicalState classification = classifyOwnedHarvestClaim(claim, completedCropSlots, exactPhysical);
        if (classification == HarvestRestartPhysicalState.FOREIGN_OR_DAMAGED) {
            return new HarvestRestartClassification(classification, projection, firstMismatchClaim(level, site, claim).orElse(fallback));
        }
        return new HarvestRestartClassification(classification, projection, fallback);
    }
    static HarvestRestartPhysicalState classifyOwnedHarvestClaim(FrontierV3ResourceSiteLedger.Claim claim,
                                                                   int completedCropSlots, boolean exactPhysical) {
        if (claim.status() != FrontierV3ResourceSiteLedger.Status.ACTIVE || !exactPhysical) {
            return exactPhysical ? HarvestRestartPhysicalState.UNKNOWN : HarvestRestartPhysicalState.FOREIGN_OR_DAMAGED;
        }
        if (claim.stage() != ResourceSiteLifecycle.MATURE_STAGE) return HarvestRestartPhysicalState.OWNED_BEHIND;
        if (claim.harvestedCropSlots() > completedCropSlots) return HarvestRestartPhysicalState.UNKNOWN;
        return claim.harvestedCropSlots() == completedCropSlots
                ? HarvestRestartPhysicalState.OWNED_EXACT : HarvestRestartPhysicalState.OWNED_BEHIND;
    }
    private static RestartReconciliation projectHarvestReconciliation(ServerLevel level, FrontierV3ResourceSiteLedger ledger, ResourceSite site,
                                                                        int completedCropSlots) {
        StageProjectionResult projected = projectHarvestProgress(level, ledger, site, completedCropSlots);
        return projected == StageProjectionResult.CURRENT || projected == StageProjectionResult.UPDATED
                ? RestartReconciliation.RECREATED : RestartReconciliation.CONFLICT;
    }
    private static RestartReconciliation recreateNeutralHarvestField(ServerLevel level, FrontierV3ResourceSiteLedger ledger, ResourceSite site,
                                                                       int completedCropSlots) {
        ledger.reserve(site.id(), projectionClaim(site));
        if (!placeWholeField(level, site)) return RestartReconciliation.CONFLICT;
        ledger.activate(site.id());
        return projectHarvestReconciliation(level, ledger, site, completedCropSlots);
    }
    private static void execute(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state, PhysicalIntent intent) {
        Target target = target(state, intent);
        if (target == null) { if (intent.status() == PhysicalIntentStatus.RUNNING) unknown(runtime, intent.id(), "target-conflict"); return; }
        if (!loaded(level, target.site())) return;
        FrontierV3ResourceSiteLedger ledger = FrontierV3ResourceSiteLedger.get(level);
        FrontierV3ResourceSiteLedger.Claim claim = ledger.claim(target.site().id());
        if (intent.status() == PhysicalIntentStatus.RUNNING) { inspectRunning(level, runtime, ledger, target, claim); return; }
        if (claim != null) { unknown(runtime, intent.id(), "reserved-before-running"); return; }
        if (!baseline(level, target.site())) { unknown(runtime, intent.id(), "foreign-baseline"); return; }
        ledger.reserve(target.site().id(), intent.id());
        if (!transition(runtime, intent.id(), PhysicalIntentStatus.RUNNING, Optional.empty(), "running")) return;
        if (!placeWholeField(level, target.site())) { unknown(runtime, intent.id(), "partial-write"); return; }
        ledger.activate(target.site().id()); confirm(runtime, intent, target.site());
    }
    private static void inspectRunning(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                       FrontierV3ResourceSiteLedger ledger, Target target, FrontierV3ResourceSiteLedger.Claim claim) {
        if (claim != null && claim.intentId().equals(target.intent().id()) && claim.status() == FrontierV3ResourceSiteLedger.Status.ACTIVE
                && matches(level, target.site(), 0)) {
            confirm(runtime, target.intent(), target.site()); return;
        }
        unknown(runtime, target.intent().id(), "restart-postcondition-conflict");
    }
    static boolean placeWholeField(ServerLevel level, ResourceSite site) {
        if (!baseline(level, site)) return false;
        for (BlockPosition irrigation : site.irrigationSlots()) level.setBlock(minecraft(irrigation), Blocks.WATER.defaultBlockState(), 3);
        for (BlockPosition crop : site.cropSlots()) {
            level.setBlock(minecraft(crop.offset(0, -1, 0)), Blocks.FARMLAND.defaultBlockState(), 3);
            level.setBlock(minecraft(crop), crop(0), 3);
        }
        return matches(level, site, 0);
    }
    private static void confirm(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, PhysicalIntent intent, ResourceSite site) {
        ResourceSitePreparationObservation receipt = new ResourceSitePreparationObservation(
                new PhysicalObservationId("observation:" + intent.id().value().replace(':', '-')), intent.id(), site.id(), 64, 64);
        if (!transition(runtime, intent.id(), PhysicalIntentStatus.CONFIRMED, Optional.of(receipt), "confirmed")) {
            throw new IllegalStateException("resource-site preparation confirmation was rejected");
        }
    }
    private static Target target(FrontierWorldState state, PhysicalIntent intent) {
        if (intent.kind() != PhysicalIntentKind.RESOURCE_SITE_PREPARATION || intent.postcondition() != PhysicalPostcondition.RESOURCE_SITE_PREPARED_OBSERVED) return null;
        ResourceSite site = FrontierResourceSitePlan.compile(state.bootstrap()).get(intent.causeSubjectId());
        return site == null ? null : new Target(site, intent);
    }
    private static Target target(FrontierWorldState state, BlockPos position) {
        return FrontierResourceSitePlan.compile(state.bootstrap()).values().stream().filter(site -> contains(site, position)).findFirst()
                .map(site -> new Target(site, null)).orElse(null);
    }
    static boolean contains(ResourceSite site, BlockPos position) {
        return site.managedSlots().stream().anyMatch(slot -> minecraft(slot).equals(position));
    }
    static boolean loaded(ServerLevel level, ResourceSite site) {
        return site.managedSlots().stream().map(FrontierV3ResourceSiteExecutor::minecraft).allMatch(level::hasChunkAt);
    }
    static boolean baseline(ServerLevel level, ResourceSite site) {
        return site.cropSlots().stream().allMatch(crop -> level.getBlockState(minecraft(crop)).isAir())
                && site.soilSlots().stream().allMatch(soil -> {
                    BlockState state = level.getBlockState(minecraft(soil));
                    return (state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT) || state.is(Blocks.LIGHT_GRAY_CONCRETE))
                            && !level.getBlockState(minecraft(soil).below()).isAir();
                }) && site.irrigationSlots().stream().allMatch(irrigation -> {
                    BlockState state = level.getBlockState(minecraft(irrigation));
                    return (state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT) || state.is(Blocks.LIGHT_GRAY_CONCRETE))
                            && !level.getBlockState(minecraft(irrigation).below()).isAir();
                });
    }
    static boolean matches(ServerLevel level, ResourceSite site, int stage) {
        return matchesInfrastructure(level, site)
                && site.cropSlots().stream().allMatch(crop -> level.getBlockState(minecraft(crop)).equals(crop(stage)));
    }
    private static boolean matchesInfrastructure(ServerLevel level, ResourceSite site) {
        return site.soilSlots().stream().allMatch(soil -> level.getBlockState(minecraft(soil)).is(Blocks.FARMLAND))
                && site.irrigationSlots().stream().allMatch(irrigation -> level.getBlockState(minecraft(irrigation)).equals(Blocks.WATER.defaultBlockState()));
    }
    private static Optional<BlockPosition> firstInfrastructureMismatch(ServerLevel level, ResourceSite site) {
        return java.util.stream.Stream.concat(site.soilSlots().stream().filter(soil -> !level.getBlockState(minecraft(soil)).is(Blocks.FARMLAND)),
                site.irrigationSlots().stream().filter(irrigation -> !level.getBlockState(minecraft(irrigation)).equals(Blocks.WATER.defaultBlockState()))).findFirst();
    }
    private static Optional<BlockPosition> firstMismatch(ServerLevel level, ResourceSite site, int stage) {
        return java.util.stream.Stream.concat(firstInfrastructureMismatch(level, site).stream(),
                site.cropSlots().stream().filter(crop -> !level.getBlockState(minecraft(crop)).equals(crop(stage)))).findFirst();
    }
    private static boolean projectsGrowthStage(ResourceSiteLifecycle lifecycle) {
        return lifecycle.phase() == ResourceSitePhase.GROWING || lifecycle.phase() == ResourceSitePhase.READY
                || lifecycle.phase() == ResourceSitePhase.HARVESTING;
    }
    private static BlockState crop(int stage) { return Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, stage); }
    private static ManagedFacilityProgressProjection harvestProjection(ResourceSite site, int completedCropSlots) {
        return new ManagedFacilityProgressProjection(site.id(), site.cropSlots(), completedCropSlots);
    }
    private static PhysicalIntentId projectionClaim(ResourceSite site) {
        return new PhysicalIntentId("intent:site-projection-" + site.id().value().substring("site:".length()));
    }
    static boolean ownsRestartClaim(PhysicalIntentId claimIntentId, ResourceSite site, PhysicalIntentId preparationIntentId) {
        return claimIntentId.equals(projectionClaim(site))
                || preparationIntentId != null && claimIntentId.equals(preparationIntentId);
    }
    private static BlockPos minecraft(BlockPosition position) { return new BlockPos(position.x(), position.y(), position.z()); }
    private static BlockPosition canonical(BlockPos position) { return new BlockPosition(position.getX(), position.getY(), position.getZ()); }
    private static void unknown(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, PhysicalIntentId id, String phase) {
        transition(runtime, id, PhysicalIntentStatus.UNKNOWN_AFTER_RESTART, Optional.empty(), phase);
    }
    private static boolean transition(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, PhysicalIntentId id, PhysicalIntentStatus status,
                                      Optional<io.farfrontier.palemirror.frontier.v3.model.PhysicalEffectObservation> observation, String phase) {
        io.farfrontier.palemirror.frontier.v3.api.FrontierCanonicalState<?> checkpoint = runtime.canonicalState().orElseThrow(() -> new IllegalStateException("v3 runtime is inactive"));
        CommandId command = new CommandId("executor:resource-site-" + phase + "-" + id.value().replace(':', '-'));
        CommandResult result = runtime.submit(new FrontierCommand(1, command, checkpoint.worldId(), checkpoint.revision(), checkpoint.instant(),
                FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(command), new PhysicalIntentTransition(id, status, observation)))
                .orElseThrow(() -> new IllegalStateException("v3 runtime is inactive"));
        return result instanceof CommandResult.Accepted;
    }
    record Target(ResourceSite site, PhysicalIntent intent) { }
}
