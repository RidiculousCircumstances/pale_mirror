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
    private static final Map<FrontierV3ServerRuntime<?, ?>, Map<SubjectId, Integer>> CELL_CURSORS = new IdentityHashMap<>();
    private static final Map<FrontierV3ServerRuntime<?, ?>, Map<SubjectId, Integer>> WORLD_OBSERVATION_CURSORS = new IdentityHashMap<>();
    static final Map<FrontierV3ServerRuntime<?, ?>, Set<SubjectId>> RECOVERY_SITES = new IdentityHashMap<>();
    static final Map<FrontierV3ServerRuntime<?, ?>, FrontierV3FairTurn<SubjectId>> RECOVERY_TURNS = new IdentityHashMap<>();
    enum BlockBreakObservation { UNMANAGED, ACCEPTED, REJECTED }
    enum LifecycleConflictOrigin { ORDINARY_GROWTH, RESTART_RECONCILIATION }
    private FrontierV3ResourceSiteExecutor() { }
    static int projectionWriteBudget() { return MAX_SITE_PROJECTION_WRITES_PER_TICK; }
    static void forget(FrontierV3ServerRuntime<?, ?> runtime) {
        STAGE_CURSORS.remove(runtime); CELL_CURSORS.remove(runtime); WORLD_OBSERVATION_CURSORS.remove(runtime);
        RECOVERY_SITES.remove(runtime); RECOVERY_TURNS.remove(runtime);
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
            if (!(ledger.fieldClaim(siteId) instanceof FrontierV3ResourceSiteLedger.FieldOwnership owner)
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
        return BlockBreakObservation.REJECTED;
    }
    /** Managed field soil is process-owned, like its growth clock; vanilla cannot retire it. */
    static boolean blocksNativeSoilReversion(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                             ServerLevel level, BlockPos position) {
        FrontierWorldState state = runtime.passiveOwnershipState().orElse(null);
        if (state == null || !level.getBlockState(position).is(Blocks.FARMLAND)) return false;
        Target target = target(state, position);
        if (target == null) return false;
        var lifecycle = state.resourceSites().sites().get(target.site().id());
        var siteClaim = FrontierV3ResourceSiteLedger.get(level).siteClaim(target.site().id());
        if (siteClaim instanceof FrontierV3ResourceSiteLedger.CellSiteClaim cell)
            return lifecycle != null && lifecycle.phase() != ResourceSitePhase.DESTROYED
                    && target.site().layout().soilAt(canonical(position)).isPresent()
                    && cell.claim().status() != FrontierV3ResourceSiteLedger.Status.CONFLICT;
        return false;
    }


    /** Passive protection of retained ownership after its execution runtime has been released. */
    static boolean blocksNativeSoilReversion(ServerLevel level, FrontierV3ResourceSiteLedger ledger,
                                              ResourceSite site, BlockPos position) {
        if (!level.getBlockState(position).is(Blocks.FARMLAND)
                || site.layout().soilAt(canonical(position)).isEmpty()) return false;
        var claim = ledger.siteClaim(site.id());
        if (claim instanceof FrontierV3ResourceSiteLedger.CellSiteClaim cell)
            return cell.claim().status() != FrontierV3ResourceSiteLedger.Status.CONFLICT;
        return false;
    }

    static boolean blocksNativeCropGrowth(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, ServerLevel level, BlockPos position) {
        FrontierWorldState state = runtime.passiveOwnershipState().orElse(null); if (state == null) return false;
        Target target = target(state, position); if (target == null || target.site().layout().cropAt(canonical(position)).isEmpty()) return false;
        FrontierV3ResourceSiteLedger ledger = FrontierV3ResourceSiteLedger.get(level);
        var siteClaim = ledger.siteClaim(target.site().id());
        if (siteClaim instanceof FrontierV3ResourceSiteLedger.CellSiteClaim cell)
            return FrontierV3ResourceFieldNativeGrowthFence.block(level, target.site(), cell.claim(),
                    target.site().layout().cropAt(canonical(position)).orElseThrow().id());
        return false;
    }
    static boolean blocksNativeCropGrowth(ServerLevel level, FrontierV3ResourceSiteLedger ledger, ResourceSite site, BlockPos position) {
        var crop = site.layout().cropAt(canonical(position));
        if (crop.isEmpty()) return false;
        var siteClaim = ledger.siteClaim(site.id());
        if (siteClaim instanceof FrontierV3ResourceSiteLedger.CellSiteClaim cell)
            return FrontierV3ResourceFieldNativeGrowthFence.block(level, site, cell.claim(), crop.orElseThrow().id());
        return false;
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
        return false;
    }
    static boolean restoreNativeGrowthPostcondition(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                                     ServerLevel level, BlockPos position) {
        FrontierWorldState state = runtime.passiveOwnershipState().orElse(null);
        if (state == null) return false;
        Target target = target(state, position);
        return target != null && restoreNativeGrowthPostcondition(level, FrontierV3ResourceSiteLedger.get(level), target.site(), position);
    }
    private static void projectOneGrowthStage(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state) {
        Set<SubjectId> pendingRecovery = RECOVERY_SITES.getOrDefault(runtime, Set.of());
        var ledger = FrontierV3ResourceSiteLedger.get(level);
        List<ResourceSiteLifecycle> candidates = projectionCandidates(state.resourceSites().sites().values(), pendingRecovery,
                lifecycle -> {
                    ResourceSite site = state.resourceSite(lifecycle.siteId());
                    return loaded(level, site)
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
            FrontierV3ResourceFieldInitialWriter.reserve(level, site, projectionClaim(site), state.resourceSites().cycle(site.id()));
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
                    && !ledger.hasPendingFieldMutation(site.id())
                    && !state.resourceSites().hasPendingWorldChange(site.id())
                    && cycle.pendingPlayerBreaks().isEmpty()
                    && cycle.layout().cells().stream().noneMatch(cell -> predecessor.cell(cell.id()).pending().isPresent())) {
                ledger.replaceFieldClaim(owner, owner.withWitness(predecessor.rebaseColdEpoch(cycle)));
                ledger.persist(level);
                owner = (FrontierV3ResourceSiteLedger.FieldOwnership) ledger.fieldClaim(site.id());
            }
            if (!owner.witness().matchesCycle(cycle)) return;
            var cursors = CELL_CURSORS.computeIfAbsent(runtime, ignored -> new HashMap<>());
            var selection = FrontierV3ResourceFieldProjectionSelection.select(cycle, owner.witness(),
                    cursors.getOrDefault(site.id(), 0));
            cursors.put(site.id(), selection.nextIndex());
            if (!selection.cells().isEmpty())
                FrontierV3ResourceFieldGrowthProjector.projectCurrentBatch(level, runtime, site.id(), selection.cells());
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
            // Validate the retained prepared image, not a newly constructed age-zero field.
            var witness = initial.cursor().target();
            var conditions = new java.util.LinkedHashMap<io.farfrontier.palemirror.frontier.v3.model.ResourceFieldLayout.CellId,
                    io.farfrontier.palemirror.frontier.v3.model.ResourceFieldCycle.CellState>();
            for (var cell : site.layout().cells()) {
                var condition = witness.cell(cell.id()).committed();
                conditions.put(cell.id(), new io.farfrontier.palemirror.frontier.v3.model.ResourceFieldCycle.CellState(
                        condition.soil(), condition.crop(), condition.growthStage(), false, false));
            }
            var target = io.farfrontier.palemirror.frontier.v3.model.ResourceFieldCycle.restore(
                    site.id(), site.layout(), witness.epoch(), conditions);
            try { FrontierV3ResourceFieldInitialWriter.activate(level, site, target, witness); }
            catch (IllegalStateException physicalMismatch) {
                recordCellInitializationConflict(level, runtime, site,
                        firstMismatch(level, site, 0).orElse(site.cropSlots().getFirst()));
            }
            return;
        }
        var result = FrontierV3ResourceFieldInitialWriter.writeBatch(level, site);
        if (result == FrontierV3ResourceFieldInitialWriter.Result.FOREIGN
                || result == FrontierV3ResourceFieldInitialWriter.Result.AMBIGUOUS) {
            var step = FrontierV3ResourceFieldInitialPlan.stepAt(site, initial.cursor().nextWrite(), initial.cursor().target());
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
    /** Read-only restart seam for a persisted bounded field cursor. */
    private static void reconcileOneAfterRestart(ServerLevel level,
                                                 FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                                 FrontierWorldState state) {
        FrontierV3ResourceSiteRestartDispatcher.reconcileOneAfterRestart(level, runtime, state);
    }
    private static void execute(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state, PhysicalIntent intent) {
        Target target = target(state, intent);
        if (target == null) { if (intent.status() == PhysicalIntentStatus.RUNNING) unknown(runtime, intent.id(), "target-conflict"); return; }
        if (!loaded(level, target.site())) return;
        FrontierV3ResourceSiteLedger ledger = FrontierV3ResourceSiteLedger.get(level);
        var siteClaim = ledger.siteClaim(target.site().id());
        prepareCellField(level, runtime, target, siteClaim);
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
            var result = FrontierV3ResourceFieldInitialWriter.writeBatch(level, site);
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
    private static boolean matchesInitialProjectionBaseline(ServerLevel level, ResourceSite site, BlockPosition slot) {
        BlockPos position = minecraft(slot);
        BlockState observed = level.getBlockState(position);
        if (site.cropSlots().contains(slot)) return observed.isAir();
        return (observed.is(Blocks.GRASS_BLOCK) || observed.is(Blocks.DIRT) || observed.is(Blocks.LIGHT_GRAY_CONCRETE))
                && !level.getBlockState(position.below()).isAir();
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
