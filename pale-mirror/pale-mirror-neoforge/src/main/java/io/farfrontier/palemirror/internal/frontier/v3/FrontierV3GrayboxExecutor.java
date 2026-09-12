package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.CheckpointImage;
import io.farfrontier.palemirror.frontier.v3.api.CauseChain;
import io.farfrontier.palemirror.frontier.v3.api.CommandId;
import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.model.FrontierGrayboxPlan;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierSettlementAssaultBattlefield;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.model.GrayboxCell;
import io.farfrontier.palemirror.frontier.v3.model.GrayboxMaterial;
import io.farfrontier.palemirror.frontier.v3.model.GrayboxSemanticPart;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalDelta;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalDeltaKind;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalDeltaObserved;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalDeltasObserved;
import io.farfrontier.palemirror.frontier.v3.model.StructureDamaged;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Comparator;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Bounded loaded-chunk executor for the immutable v3 structural graybox plan.
 *
 * <p>The ledger is provenance, not a template-repair permission. An unclaimed non-air block is
 * durably recorded as a foreign obstruction; a changed owned block becomes a terminal conflict.
 * Neither branch changes the observed world. This executor deliberately handles only structural
 * cells: infection is a separate dynamic-overlay boundary with its own physical-delta policy.</p>
 */
final class FrontierV3GrayboxExecutor {
    private static final int MAX_CELLS_PER_TICK = 64;
    private static final Map<FrontierV3ServerRuntime<?, ?>, Cursor> CURSORS = new IdentityHashMap<>();
    private static final Map<FrontierV3ServerRuntime<?, ?>, ProjectionWork> WORK = new IdentityHashMap<>();
    /**
     * A natural chunk load is an exposure boundary, not another best-effort projector turn.
     * The event only records that boundary: vanilla is still completing its own chunk-load call
     * stack there, so material writes must wait for the registered projection stage.  Incomplete
     * or foreign static geometry remains visibly unavailable rather than being filled by a later
     * 64-cell batch after a player has already seen the chunk.
     */
    private static final Map<FrontierV3ServerRuntime<?, ?>, Map<ChunkPos, FirstVisibilityRecord>> FIRST_VISIBILITY = new IdentityHashMap<>();

    enum ProjectionResult { APPLIED, CURRENT, CONFLICT, DEFERRED }
    enum BlockBreakObservation { UNMANAGED, ACCEPTED, REJECTED }
    /**
     * STATIC_CURRENT means the chunk's immutable cells were installed at the natural-load
     * boundary.  READY is deliberately delayed until the ordinary physical turn has also
     * observed the dynamic owners for the same checkpoint; a scene must not become eligible in
     * the small gap between those two responsibilities.
     */
    private enum FirstVisibility { PENDING, STATIC_CURRENT, READY, BLOCKED }
    private record FirstVisibilityRecord(FirstVisibility status, long revision, int cells) { }
    record FirstVisibilitySnapshot(ChunkPos chunk, String status, long revision, int cells) {
        static FirstVisibilitySnapshot unavailable() { return new FirstVisibilitySnapshot(null, "INVALID", -1L, 0); }
        static FirstVisibilitySnapshot unobserved(ChunkPos chunk) { return new FirstVisibilitySnapshot(chunk, "UNOBSERVED", -1L, 0); }
    }

    private FrontierV3GrayboxExecutor() { }

    static void tick(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        FrontierWorldState state = runtime.decodedState().orElse(null);
        if (state == null) return;
        FrontierV3GrayboxLedger ledger = FrontierV3GrayboxLedger.get(level);
        Cursor cursor = cursor(runtime, state);
        projectPendingFirstVisibility(level, ledger, runtime, state, cursor);
        retireStaleWorksiteStaging(level, ledger, cursor);
        tick(FrontierV3AftermathPhysicalWorld.minecraft(level), runtime, state, cursor);
    }

    /** Same production cursor and ownership rule, with a narrow physical adapter for seam tests. */
    static void tick(FrontierV3AftermathPhysicalWorld world, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        if (runtime.canonicalState().isEmpty()) return;
        FrontierWorldState state = runtime.decodedState().orElse(null);
        if (state == null) return;
        tick(world, runtime, state, cursor(runtime, state));
    }

    /** Same projection-owner refresh used by the state-replacement seam test. */
    static void refresh(FrontierV3AftermathPhysicalWorld world, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                        FrontierWorldState state) {
        tick(world, runtime, state, cursor(runtime, Objects.requireNonNull(state, "state")));
    }

    private static Cursor cursor(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state) {
        Cursor cursor = CURSORS.get(runtime);
        ProjectionWork work = workFor(runtime);
        if (cursor != null) work.compatibilityChecks++;
        if (cursor == null || !cursor.input().matches(state)) {
            work.freshnessConstructions++;
            FrontierGrayboxPlan.StructuralInput input = FrontierGrayboxPlan.structuralInput(state);
            if (cursor == null || !input.equals(cursor.input())) {
                work.planCompilations++;
                FrontierGrayboxPlan plan = FrontierGrayboxPlan.compileStructuralBaseline(state);
                cursor = Cursor.from(input, plan, cursor);
                CURSORS.put(runtime, cursor);
            } else {
                cursor.replaceInput(input);
            }
        }
        return cursor;
    }

    private static void tick(FrontierV3AftermathPhysicalWorld world, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                             FrontierWorldState state, Cursor cursor) {
        FrontierV3GrayboxLedger ledger = world.ledger();
        for (int count = 0; count < MAX_CELLS_PER_TICK; count++) {
            GrayboxCell cell = cursor.nextNaturallyLoaded(candidate -> world.naturallyLoaded(candidate.position())
                    && allowsDeferredProjection(runtime, candidate)).orElse(null);
            if (cell == null) return;
            // A loss is an exact canonical mask over the retained immutable baseline.  Keeping
            // it out of StructuralInput prevents one player break (or one explosion cell) from
            // rebuilding and re-sorting the complete 1024×1024-world plan.  A later confirmed
            // repair simply removes this mask; the repair executor owns its physical write and
            // provenance transition, while this ordinary projector may subsequently see it as
            // CURRENT.  We never use the cached baseline to recreate a lost cell.
            PhysicalDelta delta = state.physicalDeltas().get(cell.position());
            if (delta != null) {
                if (matchesKnownLoss(delta, cell)) retainKnownLoss(world, cell);
                continue;
            }
            project(world, cell);
        }
    }

    static void forget(FrontierV3ServerRuntime<?, ?> runtime) { CURSORS.remove(runtime); WORK.remove(runtime); }

    /** Releases only volatile exposure bookkeeping with the normal runtime cleanup. */
    static void forgetFirstVisibility(FrontierV3ServerRuntime<?, ?> runtime) { FIRST_VISIBILITY.remove(runtime); }

    /**
     * Actual ChunkEvent.Load composition: fence the newly natural chunk before it can be used by
     * a scene.  The registered projection stage installs its current static cells on the next
     * ordinary server turn.  This event never asks ChunkMap for a chunk, creates a ticket, or
     * re-enters vanilla's chunk-load stack with a block mutation.
     */
    static void observeNaturalChunkLoad(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, ChunkPos chunk) {
        Objects.requireNonNull(level, "first visibility level"); Objects.requireNonNull(runtime, "first visibility runtime");
        Objects.requireNonNull(chunk, "first visibility chunk");
        if (runtime.decodedState().isEmpty() || !level.hasChunk(chunk.x, chunk.z)) return;
        retainFirstVisibility(runtime, chunk);
    }

    /**
     * Records a player's exact destination before vanilla has necessarily installed its chunk
     * holder.  This is only an exposure fence: the registered turn still waits for natural
     * availability before inspecting or changing a block, and it never creates a ticket.
     */
    static void observePlayerIngress(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, ChunkPos chunk) {
        Objects.requireNonNull(runtime, "first visibility runtime"); Objects.requireNonNull(chunk, "first visibility chunk");
        if (runtime.decodedState().isEmpty()) return;
        retainFirstVisibility(runtime, chunk);
    }

    private static void retainFirstVisibility(FrontierV3ServerRuntime<?, ?> runtime, ChunkPos chunk) {
        FIRST_VISIBILITY.computeIfAbsent(runtime, ignored -> new LinkedHashMap<>())
                .putIfAbsent(chunk, new FirstVisibilityRecord(FirstVisibility.PENDING, -1L, 0));
    }

    /**
     * Completes one already-observed first-visibility boundary from the normal projection
     * composition.  The special physical adapter sends client state without neighbour updates:
     * immutable graybox cells have no redstone/physics dependency, and neighbour notification
     * from a ChunkEvent.Load callback can synchronously request a second chunk.
     */
    private static void projectPendingFirstVisibility(ServerLevel level, FrontierV3GrayboxLedger ledger,
                                                       FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                                       FrontierWorldState state, Cursor cursor) {
        Map<ChunkPos, FirstVisibilityRecord> records = FIRST_VISIBILITY.get(runtime);
        if (records == null) return;
        // A client state packet may make an adjacent vanilla chunk naturally loaded while this
        // registered turn is projecting the current one.  Snapshot only the already-pending
        // keys: that new exposure is a distinct boundary for the next ordinary turn, never a
        // concurrent modification or an unbounded same-turn cascade.
        List<ChunkPos> pendingChunks = records.entrySet().stream()
                .filter(entry -> entry.getValue().status() == FirstVisibility.PENDING)
                .map(Map.Entry::getKey).toList();
        for (ChunkPos chunk : pendingChunks) {
            FirstVisibilityRecord record = records.get(chunk);
            if (record == null || record.status() != FirstVisibility.PENDING) continue;
            if (!level.hasChunk(chunk.x, chunk.z)) continue;
            FirstVisibility result = FirstVisibility.STATIC_CURRENT;
            List<GrayboxCell> cells = cursor.cellsIn(chunk);
            List<GrayboxCell> pending = cells;
            // Dependencies are confined to one already-loaded chunk and sorted by height.
            // Revisit only while an earlier support made a later cell eligible; no retry/polling
            // is permitted outside this single registered projection turn.
            while (!pending.isEmpty()) {
                int progressed = 0;
                List<GrayboxCell> deferred = new ArrayList<>();
                for (GrayboxCell cell : pending) {
                    ProjectionResult projection = projectFirstVisible(FrontierV3AftermathPhysicalWorld.firstVisibility(level), ledger, state, cell);
                    if (projection == ProjectionResult.CONFLICT) { result = FirstVisibility.BLOCKED; break; }
                    if (projection == ProjectionResult.DEFERRED) deferred.add(cell);
                    else progressed++;
                }
                if (result == FirstVisibility.BLOCKED) break;
                if (deferred.isEmpty()) break;
                if (progressed == 0) { result = FirstVisibility.BLOCKED; break; }
                pending = List.copyOf(deferred);
            }
            long revision = runtime.checkpointImage().orElseThrow().revision().value();
            records.replace(chunk, record, new FirstVisibilityRecord(result, revision, cells.size()));
        }
    }

    /**
     * Promotes static first visibility only after the ordinary physical turn has completed its
     * dynamic observation for that checkpoint, immediately before scene consumption.
     */
    static void completeDynamicCatchUp(FrontierV3ServerRuntime<?, ?> runtime) {
        long revision = runtime.checkpointImage().orElseThrow().revision().value();
        Map<ChunkPos, FirstVisibilityRecord> records = FIRST_VISIBILITY.get(runtime);
        if (records == null) return;
        records.replaceAll((chunk, record) -> record.status() == FirstVisibility.STATIC_CURRENT && record.revision() == revision
                ? new FirstVisibilityRecord(FirstVisibility.READY, revision, record.cells()) : record);
    }

    /** A scene can use an exposed chunk only after the static and dynamic visibility boundaries completed. */
    static boolean sceneEligible(FrontierV3ServerRuntime<?, ?> runtime, BlockPosition position) {
        FirstVisibilityRecord visibility = FIRST_VISIBILITY.getOrDefault(runtime, Map.of()).get(new ChunkPos(position.x() >> 4, position.z() >> 4));
        return visibility == null || visibility.status() == FirstVisibility.READY;
    }

    /** Read-only record of one natural exposure; unknown chunks have not yet been exposed. */
    static FirstVisibilitySnapshot firstVisibility(FrontierV3ServerRuntime<?, ?> runtime, String id) {
        String[] parts = Objects.requireNonNull(id, "first visibility id").split(",", -1);
        if (parts.length != 2) return FirstVisibilitySnapshot.unavailable();
        try {
            ChunkPos chunk = new ChunkPos(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
            FirstVisibilityRecord record = FIRST_VISIBILITY.getOrDefault(runtime, Map.of()).get(chunk);
            return record == null ? FirstVisibilitySnapshot.unobserved(chunk)
                    : new FirstVisibilitySnapshot(chunk, record.status().name(), record.revision(), record.cells());
        } catch (NumberFormatException invalid) { return FirstVisibilitySnapshot.unavailable(); }
    }

    private static boolean allowsDeferredProjection(FrontierV3ServerRuntime<?, ?> runtime, GrayboxCell cell) {
        FirstVisibilityRecord visibility = FIRST_VISIBILITY.getOrDefault(runtime, Map.of()).get(new ChunkPos(cell.position().x() >> 4, cell.position().z() >> 4));
        return visibility == null || visibility.status() != FirstVisibility.BLOCKED;
    }

    private static ProjectionResult projectFirstVisible(FrontierV3AftermathPhysicalWorld world, FrontierV3GrayboxLedger ledger,
                                                        FrontierWorldState state, GrayboxCell cell) {
        PhysicalDelta delta = state.physicalDeltas().get(cell.position());
        if (delta != null) {
            if (matchesKnownLoss(delta, cell)) { retainKnownLoss(world, cell); return ProjectionResult.CURRENT; }
            return ProjectionResult.CONFLICT;
        }
        return project(world, cell);
    }

    /**
     * Returns the exact immutable baseline retained by an earlier projection turn as a bounded
     * point-query provider for ambient admission.  There is intentionally no synchronous
     * compiler fallback: an absent or structurally stale cursor leaves assault admission empty
     * until the registered projection stage refreshes it.
     */
    static Optional<FrontierSettlementAssaultBattlefield.Provider> admissionProvider(
            FrontierV3ServerRuntime<?, ?> runtime, FrontierWorldState state) {
        return FrontierGrayboxPlan.withoutStructuralDerivation(() -> {
            Cursor cursor = CURSORS.get(runtime);
            ProjectionWork work = workFor(runtime); work.compatibilityChecks++;
            if (cursor == null || !cursor.input().matches(state)) return Optional.empty();
            work.providerAcquisitions++;
            return Optional.of(cursor.providerSnapshot(state, work));
        });
    }

    /**
     * Observes a real player break of a still-owned semantic cell before Minecraft removes it.
     * The command is durable first. Only after acceptance does the claim become a conflict, just
     * before Minecraft mutates the block; a rejected observation is reported to the caller so it
     * can cancel the break rather than creating unaccounted physical reality.
     */
    static BlockBreakObservation observeBlockBreak(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, ServerLevel level,
                                                   BlockPos position, String cause) {
        FrontierV3GrayboxLedger ledger = FrontierV3GrayboxLedger.get(level);
        Optional<List<PhysicalDelta>> observed = preparePhysicalDeltas(level, ledger, position, cause);
        if (observed.isEmpty()) return BlockBreakObservation.UNMANAGED;
        try {
            io.farfrontier.palemirror.frontier.v3.api.FrontierCanonicalState<?> checkpoint = runtime.canonicalState().orElseThrow(() -> new IllegalStateException("v3 runtime is inactive"));
            List<PhysicalDelta> deltas = observed.orElseThrow();
            CommandId id = new CommandId("executor:physical-delta-r" + checkpoint.revision().value() + "-p" + position.asLong() + "-n" + deltas.size());
            FrontierPayload payload = deltas.size() == 1 ? new PhysicalDeltaObserved(deltas.getFirst()) : new PhysicalDeltasObserved(deltas);
            CommandResult result = runtime.submit(new FrontierCommand(1, id, checkpoint.worldId(), checkpoint.revision(), checkpoint.instant(),
                    FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(id), payload))
                    .orElseThrow(() -> new IllegalStateException("v3 runtime is inactive"));
            if (!(result instanceof CommandResult.Accepted)) return BlockBreakObservation.REJECTED;
            for (PhysicalDelta delta : deltas) {
                FrontierV3DiagnosticTrace.record(level.getServer(), FrontierV3DiagnosticTrace.physicalDeltaCorrelation(delta.position()),
                        deltas.size() == 1 ? "physical_delta_observed" : "physical_deltas_observed", delta.ownerId().orElseThrow(), result);
                ledger.conflict(toMinecraft(delta.position())); // revoke every affected desired-state claim before Minecraft mutates the support
            }
            return BlockBreakObservation.ACCEPTED;
        } catch (RuntimeException failed) {
            return BlockBreakObservation.REJECTED;
        }
    }

    /** Converts every still-owned graybox part (settlement, hive or route) into typed loss evidence. */
    static Optional<PhysicalDeltaObserved> preparePhysicalDelta(ServerLevel level, FrontierV3GrayboxLedger ledger,
                                                                 BlockPos position, String cause) {
        FrontierV3GrayboxLedger.Claim claim = ledger.claim(position);
        if (claim == null || claim.conflicted()) return Optional.empty();
        GrayboxMaterial material;
        GrayboxSemanticPart part;
        try {
            material = GrayboxMaterial.valueOf(claim.material());
            part = GrayboxSemanticPart.valueOf(claim.semanticPart());
        } catch (IllegalArgumentException malformed) {
            ledger.conflict(position);
            return Optional.empty();
        }
        if (!level.getBlockState(position).equals(material(material))) {
            ledger.conflict(position);
            return Optional.empty();
        }
        return Optional.of(new PhysicalDeltaObserved(new PhysicalDelta(
                new io.farfrontier.palemirror.frontier.v3.model.BlockPosition(position.getX(), position.getY(), position.getZ()),
                PhysicalDeltaKind.KNOWN_SEMANTIC_LOSS, Optional.of(new io.farfrontier.palemirror.frontier.v3.api.SubjectId(claim.owner())),
                Optional.of(part), cause)));
    }

    /**
     * Resolves the bounded immediate Minecraft-survival consequences of one owned break before
     * the initiating block mutates.  This is a provider rule, not a route-coordinate exception:
     * it maps immutable semantic claims to the current NeoForge palette's real support rule.
     */
    static Optional<List<PhysicalDelta>> preparePhysicalDeltas(ServerLevel level, FrontierV3GrayboxLedger ledger,
                                                                 BlockPos position, String cause) {
        Optional<PhysicalDeltaObserved> direct = preparePhysicalDelta(level, ledger, position, cause);
        if (direct.isEmpty()) return Optional.empty();
        List<PhysicalDelta> deltas = new ArrayList<>();
        deltas.add(direct.orElseThrow().delta());
        BlockPos above = position.above();
        FrontierV3GrayboxLedger.Claim dependent = ledger.claim(above);
        if (dependent != null && !dependent.conflicted() && losesMinecraftSurvivalWithBelowRemoved(dependent, above, position)) {
            preparePhysicalDelta(level, ledger, above, cause + ":survival-after:" + position.asLong())
                    .map(PhysicalDeltaObserved::delta).ifPresent(deltas::add);
        }
        return Optional.of(List.copyOf(deltas));
    }

    /**
     * Current graybox's route deck is a carpet on an owned raised-route footing.  Keeping this
     * palette rule at the physical-provider boundary makes all real vanilla survival cascades
     * explicit while the pure topology continues to own only semantic support/deck cells.
     */
    private static boolean losesMinecraftSurvivalWithBelowRemoved(FrontierV3GrayboxLedger.Claim claim, BlockPos dependent,
                                                                   BlockPos removedSupport) {
        return dependent.below().equals(removedSupport)
                && claim.material().equals(GrayboxMaterial.ROUTE.name())
                && claim.semanticPart().equals(GrayboxSemanticPart.ROUTE_SURFACE.name());
    }

    /** Converts only a still-owned exact cell into canonical evidence and terminally retires its claim. */
    static Optional<StructureDamaged> prepareStructureDamage(ServerLevel level, FrontierV3GrayboxLedger ledger,
                                                              BlockPos position, String cause) {
        Optional<PhysicalDeltaObserved> observed = preparePhysicalDelta(level, ledger, position, cause);
        if (observed.isEmpty() || !observed.orElseThrow().delta().ownerId().orElseThrow().value().startsWith("structure:")) return Optional.empty();
        PhysicalDelta delta = observed.orElseThrow().delta();
        return Optional.of(new StructureDamaged(
                delta.ownerId().orElseThrow(), delta.position(), delta.semanticPart().orElseThrow(), cause));
    }

    /** Applies exactly one loaded desired cell; exposed package-private for negative GameTests. */
    static ProjectionResult project(ServerLevel level, FrontierV3GrayboxLedger ledger, GrayboxCell cell) {
        return project(FrontierV3AftermathPhysicalWorld.minecraft(level), cell);
    }

    static ProjectionResult project(FrontierV3AftermathPhysicalWorld world, GrayboxCell cell) {
        io.farfrontier.palemirror.frontier.v3.model.BlockPosition position = cell.position();
        if (!world.naturallyLoaded(position)) return ProjectionResult.DEFERRED;
        FrontierV3GrayboxLedger ledger = world.ledger();
        FrontierV3GrayboxLedger.Claim prior = ledger.claim(toMinecraft(cell));
        if (prior != null) {
            if (prior.conflicted() || !matches(prior, cell)) return ProjectionResult.CONFLICT;
            if (world.hasMaterial(position, cell.material())) return ProjectionResult.CURRENT;
            ledger.conflict(toMinecraft(cell));
            return ProjectionResult.CONFLICT;
        }
        if (!world.isAir(position)) {
            ledger.obstructed(toMinecraft(cell), cell.ownerId().value(), cell.material().name(), cell.semanticPart().name());
            return ProjectionResult.CONFLICT;
        }
        if (requiresSupport(cell.semanticPart()) && world.isAir(cell.position().offset(0, -1, 0))) return ProjectionResult.DEFERRED;
        // Reserve capacity before mutating the world: an exhausted provenance ledger is fail-closed.
        ledger.ensureCapacityFor(toMinecraft(cell));
        if (!world.placeMaterial(position, cell.material())) return ProjectionResult.DEFERRED;
        ledger.applied(toMinecraft(cell), cell.ownerId().value(), cell.material().name(), cell.semanticPart().name());
        return ProjectionResult.APPLIED;
    }

    /**
     * A COLD canonical consequence can predate the first natural chunk visit.  The baseline is
     * masked by the exact delta, but the physical ledger still needs a durable tombstone so a
     * later owned repair can distinguish that scar from fresh air.  This method never mutates
     * the block: foreign/non-air geometry remains untouched and will make repair visibly fail.
     */
    static void retainKnownLoss(ServerLevel level, FrontierV3GrayboxLedger ledger, GrayboxCell cell) {
        retainKnownLoss(FrontierV3AftermathPhysicalWorld.minecraft(level), cell);
    }

    static void retainKnownLoss(FrontierV3AftermathPhysicalWorld world, GrayboxCell cell) {
        if (!world.naturallyLoaded(cell.position()) || !world.isAir(cell.position())) return;
        world.ledger().damaged(toMinecraft(cell), cell.ownerId().value(), cell.material().name(), cell.semanticPart().name());
    }

    /** A masked cell receives a tombstone only for its own exact canonical semantic loss. */
    static boolean matchesKnownLoss(PhysicalDelta delta, GrayboxCell cell) {
        return delta.kind() == PhysicalDeltaKind.KNOWN_SEMANTIC_LOSS
                && delta.ownerId().equals(Optional.of(cell.ownerId()))
                && delta.semanticPart().equals(Optional.of(cell.semanticPart()));
    }

    static BlockState material(GrayboxMaterial material) {
        return switch (material) {
            case HALL -> Blocks.WHITE_CONCRETE.defaultBlockState();
            case HOUSING -> Blocks.ORANGE_CONCRETE.defaultBlockState();
            case FARM -> Blocks.LIME_CONCRETE.defaultBlockState();
            case WORKSHOP -> Blocks.BLUE_CONCRETE.defaultBlockState();
            case WORKSHOP_INPUT -> Blocks.CYAN_CONCRETE.defaultBlockState();
            case WORKSHOP_PROCESS -> Blocks.MAGENTA_CONCRETE.defaultBlockState();
            case DEPOT -> Blocks.YELLOW_CONCRETE.defaultBlockState();
            case INFIRMARY -> Blocks.PINK_CONCRETE.defaultBlockState();
            case HIVE_GANGLION -> Blocks.RED_CONCRETE.defaultBlockState();
            case HIVE_RELAY -> Blocks.PINK_CONCRETE.defaultBlockState();
            case HIVE_BROOD -> Blocks.PURPLE_CONCRETE.defaultBlockState();
            case HIVE_STORE -> Blocks.MAGENTA_CONCRETE.defaultBlockState();
            case HIVE_DIGESTER -> Blocks.BROWN_CONCRETE.defaultBlockState();
            case HIVE_HIBERNACULUM -> Blocks.CYAN_CONCRETE.defaultBlockState();
            // Contrast with the cyan tray makes a dormant bioform immediately legible at range.
            case HIVE_COCOON -> Blocks.WHITE_CONCRETE.defaultBlockState();
            case HIVE_MORPHER -> Blocks.LIME_CONCRETE.defaultBlockState();
            case HIVE_SPORULATOR -> Blocks.ORANGE_CONCRETE.defaultBlockState();
            case HIVE_SENSOR -> Blocks.LIGHT_BLUE_CONCRETE.defaultBlockState();
            // Routes are semantic paved corridors, not waist-high walls.  A thin gray surface
            // stays plainly visible in graybox; exact Transit bodies use the compiler's adjacent
            // clear lane, so forced HOT motion never treats the route block as pass-through air.
            case ROUTE -> Blocks.GRAY_CARPET.defaultBlockState();
            case ROUTE_FOUNDATION -> Blocks.GRAY_CONCRETE.defaultBlockState();
            // A full visible pad is intentional: it gives the exact crew a real floor before
            // any route cell exists, and its distinct cyan makes temporary work easy to read.
            case WORKSITE -> Blocks.LIGHT_BLUE_CONCRETE.defaultBlockState();
            case INFECTION -> throw new IllegalArgumentException("infection requires the dynamic overlay executor");
        };
    }

    /**
     * A stage is an explicit, project-owned temporary floor. Once its canonical work front has
     * moved on, delete only the exact untouched block we wrote; a modified block stays a visible
     * conflict and is never overwritten or silently adopted. The bounded ledger owns at most
     * 48 such claims (12 projects × 4 crew slots), so this scan remains cheaper than one
     * projection chunk turn and is safe across unload/restart.
     */
    private static void retireStaleWorksiteStaging(ServerLevel level, FrontierV3GrayboxLedger ledger, Cursor cursor) {
        for (FrontierV3GrayboxLedger.ClaimAt staged : ledger.claimsWithSemanticPart(GrayboxSemanticPart.WORKSITE_STAGING.name())) {
            BlockPos position = staged.position();
            if (cursor.retainsWorksiteStaging(position) || !level.hasChunkAt(position)) continue;
            FrontierV3GrayboxLedger.Claim claim = staged.claim();
            GrayboxMaterial stagedMaterial;
            try {
                stagedMaterial = GrayboxMaterial.valueOf(claim.material());
            } catch (IllegalArgumentException malformed) {
                ledger.conflict(position); continue;
            }
            if (claim.conflicted() || stagedMaterial != GrayboxMaterial.WORKSITE
                    || !level.getBlockState(position).equals(material(stagedMaterial))) {
                ledger.conflict(position); continue;
            }
            if (!level.setBlock(position, Blocks.AIR.defaultBlockState(), 3) || !level.getBlockState(position).isAir()) continue;
            ledger.retire(position, claim.owner(), claim.material(), claim.semanticPart());
        }
    }

    private static boolean requiresSupport(GrayboxSemanticPart part) {
        return part == GrayboxSemanticPart.FOUNDATION || part == GrayboxSemanticPart.ROUTE_FOUNDATION || part == GrayboxSemanticPart.ROUTE_SURFACE
                || part == GrayboxSemanticPart.PUBLIC_ACCESS_SURFACE || part == GrayboxSemanticPart.WORKSITE_STAGING;
    }
    private static boolean matches(FrontierV3GrayboxLedger.Claim claim, GrayboxCell cell) {
        return claim.owner().equals(cell.ownerId().value()) && claim.material().equals(cell.material().name())
                && claim.semanticPart().equals(cell.semanticPart().name());
    }
    private static BlockPos toMinecraft(GrayboxCell cell) {
        return new BlockPos(cell.position().x(), cell.position().y(), cell.position().z());
    }
    private static BlockPos toMinecraft(io.farfrontier.palemirror.frontier.v3.model.BlockPosition position) {
        return new BlockPos(position.x(), position.y(), position.z());
    }

    /**
     * Round-robins cells inside naturally loaded plan chunks rather than walking a world-wide
     * list.  A first player visit must therefore make local supports and exact containers
     * available promptly even if lexicographically earlier settlements remain unloaded.
     */
    static final class Cursor {
        private FrontierGrayboxPlan.StructuralInput input;
        private final FrontierGrayboxPlan structuralBaseline;
        private final List<ChunkCells> chunks;
        private int nextChunkIndex;

        private Cursor(FrontierGrayboxPlan.StructuralInput input, FrontierGrayboxPlan structuralBaseline, List<ChunkCells> chunks, int nextChunkIndex,
                       java.util.Set<BlockPos> activeWorksiteStaging) {
            this.input = input;
            this.structuralBaseline = structuralBaseline;
            this.chunks = chunks;
            this.nextChunkIndex = nextChunkIndex;
            this.activeWorksiteStaging = activeWorksiteStaging;
        }
        private final java.util.Set<BlockPos> activeWorksiteStaging;
        static Cursor from(FrontierGrayboxPlan.StructuralInput input, FrontierGrayboxPlan plan, Cursor prior) {
            List<GrayboxCell> cells = plan.cells().values().stream().sorted(Comparator
                    .comparingInt((GrayboxCell cell) -> cell.position().y())
                    .thenComparingInt(cell -> cell.position().x()).thenComparingInt(cell -> cell.position().z())).toList();
            return fromCells(input, plan, cells, prior, plan.cells().values().stream()
                    .filter(cell -> cell.semanticPart() == GrayboxSemanticPart.WORKSITE_STAGING)
                    .map(FrontierV3GrayboxExecutor::toMinecraft).collect(java.util.stream.Collectors.toUnmodifiableSet()));
        }
        static Cursor fromCells(FrontierGrayboxPlan.StructuralInput input, List<GrayboxCell> cells, Cursor prior) {
            return fromCells(input, null, cells, prior, java.util.Set.of());
        }
        private static Cursor fromCells(FrontierGrayboxPlan.StructuralInput input, FrontierGrayboxPlan structuralBaseline,
                                        List<GrayboxCell> cells, Cursor prior,
                                        java.util.Set<BlockPos> activeWorksiteStaging) {
            Map<ChunkKey, List<GrayboxCell>> grouped = new LinkedHashMap<>();
            cells.forEach(cell -> grouped.computeIfAbsent(ChunkKey.of(cell), ignored -> new ArrayList<>()).add(cell));
            List<ChunkCells> chunks = grouped.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(entry -> {
                ChunkCells before = prior == null ? null : prior.chunk(entry.getKey());
                return new ChunkCells(entry.getKey(), List.copyOf(entry.getValue()), before);
            }).toList();
            int next = prior == null || chunks.isEmpty() ? 0 : indexOf(chunks, prior.nextChunkKey());
            return new Cursor(input, structuralBaseline, chunks, next, activeWorksiteStaging);
        }
        /** Test-only cell ordering probe; production cursors always retain an exact structural input. */
        static Cursor fromCells(List<GrayboxCell> cells, Cursor prior) {
            return fromCells(null, cells, prior);
        }
        FrontierGrayboxPlan.StructuralInput input() { return input; }
        void replaceInput(FrontierGrayboxPlan.StructuralInput replacement) { input = Objects.requireNonNull(replacement, "structural input"); }
        FrontierSettlementAssaultBattlefield.Provider providerSnapshot(FrontierWorldState state, ProjectionWork work) {
            if (structuralBaseline == null) throw new IllegalStateException("test-only cursor has no structural provider baseline");
            return new ProjectionProviderSnapshot(structuralBaseline, state.physicalDeltas(), work);
        }
        boolean retainsWorksiteStaging(BlockPos position) { return activeWorksiteStaging.contains(position); }
        List<GrayboxCell> cellsIn(ChunkPos chunk) {
            ChunkCells selected = chunk(new ChunkKey(chunk.x, chunk.z));
            return selected == null ? List.of() : selected.cells();
        }
        Optional<GrayboxCell> nextNaturallyLoaded(Predicate<GrayboxCell> loaded) {
            if (chunks.isEmpty()) return Optional.empty();
            for (int attempts = 0; attempts < chunks.size(); attempts++) {
                ChunkCells chunk = chunks.get(nextChunkIndex);
                nextChunkIndex = (nextChunkIndex + 1) % chunks.size();
                if (loaded.test(chunk.sample())) return Optional.of(chunk.next());
            }
            return Optional.empty();
        }
        private ChunkCells chunk(ChunkKey key) {
            return chunks.stream().filter(chunk -> chunk.key().equals(key)).findFirst().orElse(null);
        }
        private ChunkKey nextChunkKey() { return chunks.isEmpty() ? null : chunks.get(nextChunkIndex).key(); }
        private static int indexOf(List<ChunkCells> chunks, ChunkKey key) {
            if (key == null) return 0;
            for (int index = 0; index < chunks.size(); index++) if (chunks.get(index).key().equals(key)) return index;
            return 0;
        }
        private static final class ChunkCells {
            private final ChunkKey key;
            private final List<GrayboxCell> cells;
            private int nextIndex;
            ChunkCells(ChunkKey key, List<GrayboxCell> cells, ChunkCells prior) {
                this.key = key;
                this.cells = cells;
                this.nextIndex = prior != null && prior.cells.equals(cells) ? prior.nextIndex % Math.max(1, cells.size()) : 0;
            }
            ChunkKey key() { return key; }
            GrayboxCell sample() { return cells.getFirst(); }
            GrayboxCell next() {
                if (cells.isEmpty()) throw new IllegalStateException("empty graybox chunk has no next cell");
                GrayboxCell cell = cells.get(nextIndex);
                nextIndex = (nextIndex + 1) % cells.size();
                return cell;
            }
            List<GrayboxCell> cells() { return cells; }
        }
        private record ChunkKey(int x, int z) implements Comparable<ChunkKey> {
            static ChunkKey of(GrayboxCell cell) {
                return new ChunkKey(cell.position().x() >> 4, cell.position().z() >> 4);
            }
            @Override public int compareTo(ChunkKey other) {
                int xComparison = Integer.compare(x, other.x);
                return xComparison != 0 ? xComparison : Integer.compare(z, other.z);
            }
        }
    }

    /** One immutable, non-enumerable admission view over the projection cursor's retained plan. */
    private record ProjectionProviderSnapshot(FrontierGrayboxPlan structuralBaseline,
                                              Map<io.farfrontier.palemirror.frontier.v3.model.BlockPosition, PhysicalDelta> physicalDeltas,
                                              ProjectionWork work)
            implements FrontierSettlementAssaultBattlefield.Provider {
        @Override public Optional<GrayboxCell> cellAt(io.farfrontier.palemirror.frontier.v3.model.BlockPosition position) {
            work.pointQueries++;
            if (physicalDeltas.containsKey(position)) return Optional.empty();
            return Optional.ofNullable(structuralBaseline.cells().get(position));
        }
    }

    static ProjectionWorkSnapshot projectionWork(FrontierV3ServerRuntime<?, ?> runtime) { return workFor(runtime).snapshot(); }
    static void resetProjectionWork(FrontierV3ServerRuntime<?, ?> runtime) { WORK.put(runtime, new ProjectionWork()); }
    private static ProjectionWork workFor(FrontierV3ServerRuntime<?, ?> runtime) { return WORK.computeIfAbsent(runtime, ignored -> new ProjectionWork()); }
    static final class ProjectionWork {
        private int compatibilityChecks, freshnessConstructions, planCompilations, providerAcquisitions, pointQueries;
        ProjectionWorkSnapshot snapshot() { return new ProjectionWorkSnapshot(compatibilityChecks, freshnessConstructions, planCompilations, providerAcquisitions, pointQueries); }
    }
    record ProjectionWorkSnapshot(int compatibilityChecks, int freshnessConstructions, int planCompilations, int providerAcquisitions, int pointQueries) { }
}
