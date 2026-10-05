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
import io.farfrontier.palemirror.internal.frontier.v3.FrontierV3ResourceSiteLedger.ProjectionMode;
import static io.farfrontier.palemirror.internal.frontier.v3.FrontierV3ResourceSiteProjectionAdmission.*;
final class FrontierV3ResourceSiteExecutor {
    private static final int MAX_SITE_PROJECTION_WRITES_PER_TICK = 8;
    private static final Map<FrontierV3ServerRuntime<?, ?>, Integer> STAGE_CURSORS = new IdentityHashMap<>();
    private static final Map<FrontierV3ServerRuntime<?, ?>, Map<SubjectId, Integer>> CELL_CURSORS = new IdentityHashMap<>();
    private static final Map<FrontierV3ServerRuntime<?, ?>, Map<SubjectId, Integer>> WORLD_OBSERVATION_CURSORS = new IdentityHashMap<>();
    static final Map<FrontierV3ServerRuntime<?, ?>, Set<SubjectId>> RECOVERY_SITES = new IdentityHashMap<>();
    static final Map<FrontierV3ServerRuntime<?, ?>, FrontierV3FairTurn<SubjectId>> RECOVERY_TURNS = new IdentityHashMap<>();
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
        BlockState expected(int index) { return index < completedSlots ? crop(0) : crop(ResourceSiteLifecycle.MATURE_STAGE); }
    }
    record HarvestRestartClassification(HarvestRestartPhysicalState state, ManagedFacilityProgressProjection projection,
                                        BlockPosition witness) { }
    record FieldWrite(BlockPosition position, BlockState state) { }
    private record FieldProjectionWork(int desiredStage, int completedCropSlots, List<FieldWrite> writes,
                                       int nextWrite, ProjectionMode mode) {
        FieldProjectionWork {
            if (desiredStage < 0 || desiredStage > ResourceSiteLifecycle.MATURE_STAGE || completedCropSlots < 0
                    || nextWrite < 0 || nextWrite > writes.size()) throw new IllegalArgumentException("invalid bounded resource projection work");
            writes = List.copyOf(writes);
            java.util.Objects.requireNonNull(mode, "projection mode");
        }
        boolean complete() { return nextWrite == writes.size(); }
        boolean activatesReservedClaim() { return mode == ProjectionMode.INITIAL || mode == ProjectionMode.INITIAL_SOIL; }
        boolean restoresHarvestCursor() { return mode == ProjectionMode.SUCCESSOR_RESTORE; }
        FieldProjectionWork advanced(int count) { return new FieldProjectionWork(desiredStage, completedCropSlots, writes, nextWrite + count,
                mode); }
    }
    private FrontierV3ResourceSiteExecutor() { }
    static int projectionWriteBudget() { return MAX_SITE_PROJECTION_WRITES_PER_TICK; }
    static void forget(FrontierV3ServerRuntime<?, ?> runtime) {
        STAGE_CURSORS.remove(runtime); CELL_CURSORS.remove(runtime); WORLD_OBSERVATION_CURSORS.remove(runtime);
        RECOVERY_SITES.remove(runtime); RECOVERY_TURNS.remove(runtime); PROJECTION_WORK.remove(runtime);
        FrontierV3ResourceFieldWorldChangeExecutor.forget(runtime);
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
        if (FrontierV3ResourceFieldAcceptanceExecutor.reconcileOne(level, runtime)) return;
        if (FrontierV3ResourceFieldPlayerBreakExecutor.reconcileOne(level, runtime)) return;
        if (FrontierV3ResourceFieldWorldChangeExecutor.reconcileOne(level, runtime)) return;
        if (observeOneNaturallyLoadedWorldCell(level, runtime, state)) return;
        FrontierV3ResourceSitePreparationSelection.nextLoaded(state,
                site -> loaded(level, site) && FrontierV3GrayboxExecutor.resourceSiteProjectionDemanded(runtime, level, site)).ifPresent(intent -> execute(level, runtime, state, intent));
        reconcileOneAfterRestart(level, runtime, state);
        projectOneGrowthStage(level, runtime, runtime.decodedState().orElse(state));
    }

    /** Physical damage is an observation, not a projection-demand side effect. */
    private static boolean observeOneNaturallyLoadedWorldCell(ServerLevel level,
                                                               FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                                               FrontierWorldState state) {
        var ledger = FrontierV3ResourceSiteLedger.get(level);
        var cursors = WORLD_OBSERVATION_CURSORS.computeIfAbsent(runtime, ignored -> new HashMap<>());
        for (SubjectId siteId : state.resourceSites().sites().keySet().stream().sorted().toList()) {
            if (state.resourceSites().hasPendingWorldChange(siteId)
                    || ledger.fieldWorldChange(siteId) != null || ledger.fieldForeignChange(siteId) != null
                    || !(ledger.fieldClaim(siteId) instanceof FrontierV3ResourceSiteLedger.FieldOwnership owner)
                    || owner.status() != FrontierV3ResourceSiteLedger.Status.ACTIVE) continue;
            var cycle = state.resourceSites().cycle(siteId);
            if (!owner.witness().matchesCycle(cycle)) continue;
            int index = Math.floorMod(cursors.getOrDefault(siteId, 0), cycle.layout().cells().size());
            cursors.put(siteId, (index + 1) % cycle.layout().cells().size());
            var cell = cycle.layout().cells().get(index);
            if (FrontierV3ResourceFieldWorkAccessExecutor.observeOne(level, runtime, siteId, cell.id())) return true;
            if (FrontierV3ResourceFieldWorldChangeExecutor.observeOne(level, runtime, siteId, cell.id())) return true;
        }
        return false;
    }
    static BlockBreakObservation observePlayerBlockBreak(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                                         ServerLevel level, BlockPos position,
                                                         net.minecraft.server.level.ServerPlayer player) {
        FrontierWorldState state = runtime.decodedState().orElse(null);
        if (state == null) return BlockBreakObservation.REJECTED;
        Target target = target(state, position);
        if (target == null) return BlockBreakObservation.UNMANAGED;
        ResourceSiteLifecycle lifecycle = state.resourceSites().site(target.site().id());
        if (lifecycle.phase() == ResourceSitePhase.DESTROYED || lifecycle.phase() == ResourceSitePhase.CONFLICT)
            return BlockBreakObservation.UNMANAGED;
        if (FrontierV3ResourceSiteLedger.get(level).siteClaim(target.site().id()) instanceof FrontierV3ResourceSiteLedger.CellSiteClaim)
            return FrontierV3ResourceFieldPlayerBreakExecutor.prepare(level, runtime, target.site(), position, player);
        return observeBlockBreak(runtime, level, position, "player:" + player.getUUID());
    }
    static BlockBreakObservation observeBlockBreak(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, ServerLevel level,
                                                   BlockPos position, String cause) {
        FrontierWorldState state = runtime.decodedState().orElse(null);
        if (state == null) return BlockBreakObservation.REJECTED;
        Target target = target(state, position);
        if (target == null) return BlockBreakObservation.UNMANAGED;
        FrontierV3ResourceSiteLedger ledger = FrontierV3ResourceSiteLedger.get(level);
        ResourceSiteLifecycle lifecycle = state.resourceSites().site(target.site().id());
        if (lifecycle.phase() == ResourceSitePhase.DESTROYED || lifecycle.phase() == ResourceSitePhase.CONFLICT) return BlockBreakObservation.UNMANAGED;
        if (ledger.siteClaim(target.site().id()) instanceof FrontierV3ResourceSiteLedger.CellSiteClaim) {
            // Player crop removal has its own prepared/postcondition path. All other
            // generic cell-owned breaks lack that typed permission and fail closed.
            return BlockBreakObservation.REJECTED;
        }
        FrontierV3ResourceSiteLedger.Claim claim = ledger.claim(target.site().id());
        if (claim == null || claim.status() != FrontierV3ResourceSiteLedger.Status.ACTIVE || !matchesClaim(level, target.site(), claim)) {
            BlockPosition observed = claim == null ? canonical(position)
                    : firstMismatchClaim(level, target.site(), claim).orElse(canonical(position));
            return FrontierV3ResourceSiteConflictExecutor.recordPlayerConflict(level, runtime, ledger, target.site(), observed, cause)
                    ? BlockBreakObservation.ACCEPTED : BlockBreakObservation.REJECTED;
        }
        return FrontierV3ResourceSiteConflictExecutor.recordPlayerConflict(level, runtime, ledger, target.site(), canonical(position), cause)
                ? BlockBreakObservation.ACCEPTED : BlockBreakObservation.REJECTED;
    }
    /** Managed field soil is process-owned, like its growth clock; vanilla cannot retire it. */
    static boolean blocksNativeSoilReversion(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                             ServerLevel level, BlockPos position) {
        FrontierWorldState state = runtime.stateForNativeGrowthFence().orElse(null);
        if (state == null || !level.getBlockState(position).is(Blocks.FARMLAND)) return false;
        Target target = target(state, position);
        if (target == null) return false;
        var lifecycle = state.resourceSites().sites().get(target.site().id());
        var siteClaim = FrontierV3ResourceSiteLedger.get(level).siteClaim(target.site().id());
        if (siteClaim instanceof FrontierV3ResourceSiteLedger.CellSiteClaim cell)
            return lifecycle != null && lifecycle.phase() != ResourceSitePhase.DESTROYED
                    && target.site().layout().soilAt(canonical(position)).isPresent()
                    && cell.claim().status() != FrontierV3ResourceSiteLedger.Status.CONFLICT;
        FrontierV3ResourceSiteLedger.Claim legacy = siteClaim instanceof FrontierV3ResourceSiteLedger.LegacySiteClaim value
                ? value.claim() : null;
        return lifecycle != null && lifecycle.phase() != ResourceSitePhase.DESTROYED
                && blocksNativeSoilReversion(legacy,
                        target.site().layout().soilAt(canonical(position)).isPresent());
    }

    static boolean blocksNativeSoilReversion(FrontierV3ResourceSiteLedger.Claim claim, boolean declaredSoilCell) {
        return declaredSoilCell && claim != null && claim.status() == FrontierV3ResourceSiteLedger.Status.ACTIVE;
    }

    static boolean blocksNativeCropGrowth(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, ServerLevel level, BlockPos position) {
        FrontierWorldState state = runtime.stateForNativeGrowthFence().orElse(null); if (state == null) return false;
        Target target = target(state, position); if (target == null || target.site().layout().cropAt(canonical(position)).isEmpty()) return false;
        FrontierV3ResourceSiteLedger ledger = FrontierV3ResourceSiteLedger.get(level);
        var siteClaim = ledger.siteClaim(target.site().id());
        if (siteClaim instanceof FrontierV3ResourceSiteLedger.CellSiteClaim cell)
            return FrontierV3ResourceFieldNativeGrowthFence.block(level, target.site(), cell.claim(),
                    target.site().layout().cropAt(canonical(position)).orElseThrow().id());
        FrontierV3ResourceSiteLedger.Claim claim = siteClaim instanceof FrontierV3ResourceSiteLedger.LegacySiteClaim value
                ? value.claim() : null;
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
        var crop = site.layout().cropAt(canonical(position));
        if (crop.isEmpty()) return false;
        var siteClaim = ledger.siteClaim(site.id());
        if (siteClaim instanceof FrontierV3ResourceSiteLedger.CellSiteClaim cell)
            return FrontierV3ResourceFieldNativeGrowthFence.block(level, site, cell.claim(), crop.orElseThrow().id());
        FrontierV3ResourceSiteLedger.Claim claim = siteClaim instanceof FrontierV3ResourceSiteLedger.LegacySiteClaim value
                ? value.claim() : null;
        return blocksNativeCropGrowth(claim, false,
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
        if (site.layout().cropAt(canonical(position)).isEmpty()) return false;
        var siteClaim = ledger.siteClaim(site.id());
        if (siteClaim instanceof FrontierV3ResourceSiteLedger.CellSiteClaim cell)
            return FrontierV3ResourceFieldNativeGrowthFence.restore(level, site, cell.claim(), position);
        FrontierV3ResourceSiteLedger.Claim claim = siteClaim instanceof FrontierV3ResourceSiteLedger.LegacySiteClaim value
                ? value.claim() : null;
        if (claim == null || claim.status() != FrontierV3ResourceSiteLedger.Status.ACTIVE || claim.projection() != null) return false;
        BlockState observed = level.getBlockState(position);
        if (!observed.is(Blocks.WHEAT)) return false;
        int index = site.cropSlots().indexOf(canonical(position));
        BlockState expected = claim.stage() == ResourceSiteLifecycle.MATURE_STAGE && index < claim.harvestedCropSlots()
                ? crop(0) : crop(claim.stage());
        if (observed.equals(expected)) return false;
        if (!level.setBlock(position, expected, 2)) return false;
        ledger.recordNativeGrowthFence(site.id(), new FrontierV3ResourceSiteLedger.NativeGrowthFence(
                "crop-grow-post", canonical(position), observed.getValue(CropBlock.AGE), claim.stage()));
        return level.getBlockState(position).equals(expected);
    }
    static boolean restoreNativeGrowthPostcondition(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                                     ServerLevel level, BlockPos position) {
        FrontierWorldState state = runtime.stateForNativeGrowthFence().orElse(null);
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
            BlockState expected = index < claim.harvestedCropSlots() ? crop(0) : crop(ResourceSiteLifecycle.MATURE_STAGE);
            if (!level.getBlockState(minecraft(slot)).equals(expected)) return Optional.of(slot);
        }
        return Optional.empty();
    }
    private static void projectOneGrowthStage(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state) {
        Set<SubjectId> pendingRecovery = RECOVERY_SITES.getOrDefault(runtime, Set.of());
        var ledger = FrontierV3ResourceSiteLedger.get(level);
        List<ResourceSiteLifecycle> candidates = projectionCandidates(state.resourceSites().sites().values(), pendingRecovery,
                lifecycle -> {
                    ResourceSite site = state.resourceSite(lifecycle.siteId());
                    return !state.resourceSites().hasPendingWorldChange(site.id())
                            && ledger.fieldWorldChange(site.id()) == null
                            && ledger.fieldForeignChange(site.id()) == null && loaded(level, site)
                            && FrontierV3GrayboxExecutor.resourceSiteProjectionDemanded(runtime, level, site);
                });
        if (candidates.isEmpty()) return;
        int index = Math.floorMod(STAGE_CURSORS.getOrDefault(runtime, 0), candidates.size());
        STAGE_CURSORS.put(runtime, (index + 1) % candidates.size()); ResourceSiteLifecycle lifecycle = candidates.get(index);
        ResourceSite site = state.resourceSite(lifecycle.siteId());
        var siteClaim = ledger.siteClaim(site.id());
        if (siteClaim == null && baseline(level, site)) {
            // A COLD-grown site may have no preparation intent at first player ingress.
            // The ordinary projector, not only the preparation-intent executor, is therefore
            // an initial physical owner. It must reserve the same cell claim before writing.
            FrontierV3ResourceFieldInitialWriter.reserve(level, site, projectionClaim(site));
            siteClaim = ledger.siteClaim(site.id());
        }
        if (siteClaim instanceof FrontierV3ResourceSiteLedger.CellSiteClaim cellClaim) {
            if (cellClaim.claim() instanceof FrontierV3ResourceSiteLedger.FieldInitialization initial) {
                advanceCellInitialization(level, runtime, site, initial);
                return;
            }
            if (!(cellClaim.claim() instanceof FrontierV3ResourceSiteLedger.FieldOwnership owner)
                    || owner.status() != FrontierV3ResourceSiteLedger.Status.ACTIVE) return;
            var cycle = state.resourceSites().cycle(site.id());
            var predecessor = owner.witness();
            if (predecessor.matchesLayout(site.id(), cycle.layout()) && predecessor.epoch() < cycle.epoch()
                    && ledger.fieldWorldChange(site.id()) == null && ledger.fieldForeignChange(site.id()) == null
                    && !state.resourceSites().hasPendingWorldChange(site.id())
                    && cycle.pendingPlayerBreaks().isEmpty()
                    && cycle.layout().cells().stream().noneMatch(cell -> predecessor.cell(cell.id()).pending().isPresent())) {
                ledger.replaceFieldClaim(owner, owner.withWitness(predecessor.rebaseColdEpoch(cycle)));
                ledger.persist(level);
                owner = (FrontierV3ResourceSiteLedger.FieldOwnership) ledger.fieldClaim(site.id());
            }
            if (!owner.witness().matchesCycle(cycle)) return;
            var cursors = CELL_CURSORS.computeIfAbsent(runtime, ignored -> new HashMap<>());
            int cellIndex = Math.floorMod(cursors.getOrDefault(site.id(), 0), cycle.layout().cells().size());
            int count = Math.min(projectionWriteBudget(), cycle.layout().cells().size());
            var cells = new java.util.ArrayList<io.farfrontier.palemirror.frontier.v3.model.ResourceFieldLayout.CellId>(count);
            for (int offset = 0; offset < count; offset++)
                cells.add(cycle.layout().cells().get((cellIndex + offset) % cycle.layout().cells().size()).id());
            cursors.put(site.id(), (cellIndex + count) % cycle.layout().cells().size());
            FrontierV3ResourceFieldGrowthProjector.projectCurrentBatch(level, runtime, site.id(), cells);
            return;
        }
        // Only a per-cell physical owner may project the current field. A stage/prefix
        // claim has no exact cell-generation authority and must never normalize a surface.
        FrontierV3ResourceSiteConflictExecutor.recordLifecycleConflict(level, runtime, ledger, site,
                site.cropSlots().getFirst(),
                io.farfrontier.palemirror.frontier.v3.model.ResourceSiteDiagnosticProducer.ORDINARY_OBSERVATION_MISMATCH,
                LifecycleConflictOrigin.ORDINARY_GROWTH, "RETIRED_STAGE_PREFIX_OWNER");
    }

    private static void advanceCellInitialization(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                                  ResourceSite site,
                                                  FrontierV3ResourceSiteLedger.FieldInitialization initial) {
        if (initial.status() != FrontierV3ResourceSiteLedger.Status.PENDING || !initial.cursor().matches(site)) return;
        if (initial.cursor().complete()) {
            var seeded = io.farfrontier.palemirror.frontier.v3.model.ResourceFieldCycle.seeded(site.id(), site.layout(), 1);
            var witness = FrontierV3ResourceFieldWitness.claimed(site.id(), seeded.epoch(),
                    io.farfrontier.palemirror.frontier.v3.model.ResourceFieldPhysicalSurface.fromCycle(seeded));
            try { FrontierV3ResourceFieldInitialWriter.activate(level, site, seeded, witness); }
            catch (IllegalStateException physicalMismatch) {
                recordCellInitializationConflict(level, runtime, site,
                        firstMismatch(level, site, 0).orElse(site.cropSlots().getFirst()));
            }
            return;
        }
        var result = FrontierV3ResourceFieldInitialWriter.writeOne(level, site);
        if (result == FrontierV3ResourceFieldInitialWriter.Result.FOREIGN
                || result == FrontierV3ResourceFieldInitialWriter.Result.AMBIGUOUS) {
            var step = FrontierV3ResourceFieldInitialPlan.stepAt(site, initial.cursor().nextWrite());
            recordCellInitializationConflict(level, runtime, site, step.position());
        }
    }
    private static void recordCellInitializationConflict(ServerLevel level,
            FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, ResourceSite site, BlockPosition position) {
        var checkpoint = runtime.canonicalState().orElseThrow(() -> new IllegalStateException("v3 runtime is inactive"));
        FrontierV3ResourceSiteConflictExecutor.recordConflict(level, runtime, FrontierV3ResourceSiteLedger.get(level),
                site, position, io.farfrontier.palemirror.frontier.v3.model.ResourceSiteDiagnosticProducer.ORDINARY_OBSERVATION_MISMATCH,
                new CommandId("executor:resource-site-cell-initialization-r" + checkpoint.revision().value()
                        + "-p" + minecraft(position).asLong()));
    }
    /** Partial work retains its own durable cursor, never an exclusive turn over unrelated fields. */
    static List<ResourceSiteLifecycle> projectionCandidates(java.util.Collection<ResourceSiteLifecycle> sites,
                                                            Set<SubjectId> pendingRecovery,
                                                            java.util.function.Predicate<ResourceSiteLifecycle> locallyEligible) {
        return sites.stream().filter(FrontierV3ResourceSiteExecutor::projectsGrowthStage)
                .filter(lifecycle -> !pendingRecovery.contains(lifecycle.siteId()))
                .filter(locallyEligible).sorted(Comparator.comparing(ResourceSiteLifecycle::siteId)).toList();
    }

    private static List<FieldWrite> initialWrites(ResourceSite site, int desiredStage, int completedCropSlots) {
        List<FieldWrite> writes = new java.util.ArrayList<>();
        for (BlockPosition position : initialProjectionSlotOrder(site)) {
            if (site.irrigationSlots().contains(position)) writes.add(new FieldWrite(position, Blocks.WATER.defaultBlockState()));
            else if (site.soilSlots().contains(position)) writes.add(new FieldWrite(position, Blocks.FARMLAND.defaultBlockState()));
            else {
                int index = site.cropSlots().indexOf(position);
                writes.add(new FieldWrite(position, desiredStage == ResourceSiteLifecycle.MATURE_STAGE && index < completedCropSlots
                        ? crop(0) : crop(desiredStage)));
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
    private static FieldProjectionWork restoreProjectionWork(ResourceSite site, FrontierV3ResourceSiteLedger.Claim claim) {
        FrontierV3ResourceSiteLedger.ProjectionTransition projection = claim.projection();
        FrontierV3ResourceSiteLedger.Claim origin = new FrontierV3ResourceSiteLedger.Claim(claim.intentId(), claim.status(),
                projection.fromStage(), projection.fromHarvestedCropSlots());
        List<FieldWrite> writes = switch (projection.mode()) {
            case INITIAL, INITIAL_SOIL -> initialWrites(site, projection.targetStage(), projection.targetHarvestedCropSlots());
            case ADVANCE -> transitionWrites(site, origin, projection.targetStage(), projection.targetHarvestedCropSlots(), false);
            case SUCCESSOR_RESTORE -> transitionWrites(site, origin, projection.targetStage(), projection.targetHarvestedCropSlots(), true);
        };
        if (writes.size() != projection.writeCount()) return null;
        return new FieldProjectionWork(projection.targetStage(), projection.targetHarvestedCropSlots(), writes,
                projection.nextWrite(), projection.mode());
    }
    /** Read-only restart seam for a persisted bounded field cursor. */
    static boolean matchesPersistedProjectionPrefix(ServerLevel level, ResourceSite site, FrontierV3ResourceSiteLedger.Claim claim) {
        if (claim == null || claim.projection() == null) return false;
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
                if (work.activatesReservedClaim()) {
                    boolean baseline = work.mode() == ProjectionMode.INITIAL
                            ? level.getBlockState(minecraft(slot)).isAir()
                            : matchesInitialProjectionBaseline(level, site, slot);
                    if (!baseline) return false;
                    continue;
                }
                else expected = expectedClaimBlock(site, claim.projection(), slot);
            }
            BlockState observed = level.getBlockState(minecraft(slot));
            // Vanilla hydration changes farmland moisture without changing field ownership.
            if (expected.is(Blocks.FARMLAND) ? !observed.is(Blocks.FARMLAND) : !observed.equals(expected)) return false;
        }
        return true;
    }
    private static boolean matchesInitialProjectionBaseline(ServerLevel level, ResourceSite site, BlockPosition slot) {
        BlockPos position = minecraft(slot);
        BlockState observed = level.getBlockState(position);
        if (site.cropSlots().contains(slot)) return observed.isAir();
        return (observed.is(Blocks.GRASS_BLOCK) || observed.is(Blocks.DIRT) || observed.is(Blocks.LIGHT_GRAY_CONCRETE))
                && !level.getBlockState(position.below()).isAir();
    }
    private static BlockState expectedClaimBlock(ResourceSite site, FrontierV3ResourceSiteLedger.ProjectionTransition projection,
                                                 BlockPosition slot) {
        if (site.irrigationSlots().contains(slot)) return Blocks.WATER.defaultBlockState();
        if (site.soilSlots().contains(slot)) return Blocks.FARMLAND.defaultBlockState();
        int index = site.cropSlots().indexOf(slot);
        if (index < 0) throw new IllegalArgumentException("unmanaged projection slot");
        return projection.fromStage() == ResourceSiteLifecycle.MATURE_STAGE && index < projection.fromHarvestedCropSlots()
                ? crop(0) : crop(projection.fromStage());
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
            level.setBlock(minecraft(projection.slotPlan().get(index)), crop(0), 3);
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
    private static void reconcileOneAfterRestart(ServerLevel level,
                                                 FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                                 FrontierWorldState state) {
        FrontierV3ResourceSiteRestartDispatcher.reconcileOneAfterRestart(level, runtime, state);
    }
    static boolean projectionInFlight(FrontierV3ServerRuntime<?, ?> runtime, SubjectId siteId) {
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
        var siteClaim = ledger.siteClaim(target.site().id());
        if (siteClaim == null || siteClaim instanceof FrontierV3ResourceSiteLedger.CellSiteClaim) {
            prepareCellField(level, runtime, target, siteClaim);
            return;
        }
        FrontierV3ResourceSiteLedger.Claim claim = ledger.claim(target.site().id());
        if (intent.status() == PhysicalIntentStatus.RUNNING) { inspectRunning(level, runtime, ledger, target, claim); return; }
        if (claim != null) { unknown(runtime, intent.id(), "reserved-before-running"); return; }
        if (!baseline(level, target.site())) { unknown(runtime, intent.id(), "foreign-baseline"); return; }
        ledger.reserve(target.site().id(), intent.id());
        if (!transition(runtime, intent.id(), PhysicalIntentStatus.RUNNING, Optional.empty(), "running")) return;
        if (!placeWholeField(level, target.site())) { unknown(runtime, intent.id(), "partial-write"); return; }
        ledger.activate(target.site().id()); confirm(runtime, intent, target.site());
    }
    /** Fresh fields have one durable cell owner from the first physical write onward. */
    private static void prepareCellField(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                         Target target, FrontierV3ResourceSiteLedger.SiteClaim siteClaim) {
        PhysicalIntent intent = target.intent();
        ResourceSite site = target.site();
        if (siteClaim == null) {
            if (intent.status() != PhysicalIntentStatus.PREPARED || !baseline(level, site)) {
                unknown(runtime, intent.id(), "cell-field-foreign-baseline"); return;
            }
            FrontierV3ResourceFieldInitialWriter.reserve(level, site, intent.id());
            if (!transition(runtime, intent.id(), PhysicalIntentStatus.RUNNING, Optional.empty(), "running")) return;
            return;
        }
        FrontierV3ResourceSiteLedger.FieldClaim field = ((FrontierV3ResourceSiteLedger.CellSiteClaim) siteClaim).claim();
        if (!field.intentId().equals(intent.id())) {
            unknown(runtime, intent.id(), "cell-field-foreign-intent"); return;
        }
        if (intent.status() == PhysicalIntentStatus.PREPARED) {
            if (field instanceof FrontierV3ResourceSiteLedger.FieldInitialization) {
                transition(runtime, intent.id(), PhysicalIntentStatus.RUNNING, Optional.empty(), "running");
            } else unknown(runtime, intent.id(), "cell-field-active-before-running");
            return;
        }
        if (field instanceof FrontierV3ResourceSiteLedger.FieldOwnership owner) {
            if (owner.status() == FrontierV3ResourceSiteLedger.Status.ACTIVE
                    && owner.witness().matchesLayout(site.id(), site.layout())) confirm(runtime, intent, site);
            else unknown(runtime, intent.id(), "cell-field-activation-conflict");
            return;
        }
        if (!(field instanceof FrontierV3ResourceSiteLedger.FieldInitialization initial)
                || initial.status() != FrontierV3ResourceSiteLedger.Status.PENDING
                || !initial.cursor().matches(site)) {
            unknown(runtime, intent.id(), "cell-field-initialization-conflict"); return;
        }
        if (!initial.cursor().complete()) {
            var result = FrontierV3ResourceFieldInitialWriter.writeOne(level, site);
            if (result == FrontierV3ResourceFieldInitialWriter.Result.FOREIGN
                    || result == FrontierV3ResourceFieldInitialWriter.Result.AMBIGUOUS)
                unknown(runtime, intent.id(), "cell-field-" + result.name().toLowerCase(java.util.Locale.ROOT));
            return;
        }
        var targetCycle = io.farfrontier.palemirror.frontier.v3.model.ResourceFieldCycle.seeded(site.id(), site.layout(), 1);
        var witness = FrontierV3ResourceFieldWitness.claimed(site.id(), targetCycle.epoch(),
                io.farfrontier.palemirror.frontier.v3.model.ResourceFieldPhysicalSurface.fromCycle(targetCycle));
        FrontierV3ResourceFieldInitialWriter.activate(level, site, targetCycle, witness);
        confirm(runtime, intent, site);
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
                new PhysicalObservationId("observation:" + intent.id().value().replace(':', '-')), intent.id(), site.id(),
                site.soilSlots().size(), site.cropSlots().size());
        if (!transition(runtime, intent.id(), PhysicalIntentStatus.CONFIRMED, Optional.of(receipt), "confirmed")) {
            throw new IllegalStateException("resource-site preparation confirmation was rejected");
        }
    }
    private static Target target(FrontierWorldState state, PhysicalIntent intent) {
        if (intent.kind() != PhysicalIntentKind.RESOURCE_SITE_PREPARATION || intent.postcondition() != PhysicalPostcondition.RESOURCE_SITE_PREPARED_OBSERVED) return null;
        ResourceSite site = state.resourceSiteDescriptors().get(intent.causeSubjectId());
        return site == null ? null : new Target(site, intent);
    }
    private static Target target(FrontierWorldState state, BlockPos position) {
        return state.resourceSiteDescriptors().values().stream().filter(site -> contains(site, position)).findFirst()
                .map(site -> new Target(site, null)).orElse(null);
    }
    static boolean contains(ResourceSite site, BlockPos position) {
        return site.contains(canonical(position));
    }
    static boolean loaded(ServerLevel level, ResourceSite site) {
        return site.occupiedChunks().stream().allMatch(chunk -> level.hasChunk(chunk.x(), chunk.z()));
    }
    static boolean baseline(ServerLevel level, ResourceSite site) {
        return site.managedSlots().stream().allMatch(slot -> matchesInitialProjectionBaseline(level, site, slot));
    }
    static boolean matches(ServerLevel level, ResourceSite site, int stage) {
        return matchesInfrastructure(level, site)
                && site.cropSlots().stream().allMatch(crop -> level.getBlockState(minecraft(crop)).equals(crop(stage)));
    }
    static boolean matchesInfrastructure(ServerLevel level, ResourceSite site) {
        return site.soilSlots().stream().allMatch(soil -> level.getBlockState(minecraft(soil)).is(Blocks.FARMLAND))
                && site.irrigationSlots().stream().allMatch(irrigation -> level.getBlockState(minecraft(irrigation)).equals(Blocks.WATER.defaultBlockState()));
    }
    private static Optional<BlockPosition> firstInfrastructureMismatch(ServerLevel level, ResourceSite site) {
        return java.util.stream.Stream.concat(site.soilSlots().stream().filter(soil -> !level.getBlockState(minecraft(soil)).is(Blocks.FARMLAND)),
                site.irrigationSlots().stream().filter(irrigation -> !level.getBlockState(minecraft(irrigation)).equals(Blocks.WATER.defaultBlockState()))).findFirst();
    }
    static Optional<BlockPosition> firstMismatch(ServerLevel level, ResourceSite site, int stage) {
        return java.util.stream.Stream.concat(firstInfrastructureMismatch(level, site).stream(),
                site.cropSlots().stream().filter(crop -> !level.getBlockState(minecraft(crop)).equals(crop(stage)))).findFirst();
    }
    private static boolean projectsGrowthStage(ResourceSiteLifecycle lifecycle) {
        return lifecycle.phase() == ResourceSitePhase.GROWING || lifecycle.phase() == ResourceSitePhase.READY
                || lifecycle.phase() == ResourceSitePhase.HARVESTING;
    }
    static BlockState crop(int stage) { return Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, stage); }
    private static ManagedFacilityProgressProjection harvestProjection(ResourceSite site, int completedCropSlots) {
        return new ManagedFacilityProgressProjection(site.id(), site.cropSlots(), completedCropSlots);
    }
    private static PhysicalIntentId projectionClaim(ResourceSite site) {
        return new PhysicalIntentId("intent:site-projection-" + site.id().value().substring("site:".length()));
    }
    static boolean ownsFacilityClaim(PhysicalIntentId claimIntentId, ResourceSite site, PhysicalIntentId transitionIntentId) {
        return claimIntentId.equals(projectionClaim(site))
                || transitionIntentId != null && claimIntentId.equals(transitionIntentId);
    }
    static boolean ownsRestartClaim(PhysicalIntentId claimIntentId, ResourceSite site, PhysicalIntentId preparationIntentId) {
        return ownsFacilityClaim(claimIntentId, site, preparationIntentId);
    }
    static BlockPos minecraft(BlockPosition position) { return new BlockPos(position.x(), position.y(), position.z()); }
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
