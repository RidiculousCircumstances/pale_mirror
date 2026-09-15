package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.CheckpointImage;
import io.farfrontier.palemirror.frontier.v3.api.CauseChain;
import io.farfrontier.palemirror.frontier.v3.api.CommandId;
import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
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
import io.farfrontier.palemirror.frontier.v3.model.InfectionCell;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalDelta;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalDeltaKind;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSite;
import io.farfrontier.palemirror.frontier.v3.model.FrontierResourceSitePlan;
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
    /** A fixed local probe budget keeps an ordinary turn below a world-wide resident scan. */
    private static final int MAX_NATURAL_CHUNK_PROBES_PER_CELL = 64;
    /** First-visibility work is an ingress queue, never a scan of every resident chunk. */
    // An ingress may expose a small declared facility across several already-natural chunks.
    // Complete at most one bounded nest/facility envelope (sixteen chunks), never a scan of
    // resident world geometry, so boards cannot advertise a partly-current sibling organ.
    private static final int MAX_FIRST_VISIBILITY_CHUNKS_PER_TICK = 16;
    /** A chunk is not a cost unit: one organ column can contain thousands of declared cells. */
    // Package discovery is priority-indexed; keep the shared structural worker's existing
    // 64-cell fairness bound so one settlement ingress cannot delay a hive overlay boundary.
    private static final int MAX_FIRST_VISIBILITY_CELLS_PER_TICK = 64;
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
    /**
     * A first-visible chunk remains pending until every one of its immutable cells has been
     * reconciled.  This continuation is deliberately volatile: it is only a registered physical
     * projection cursor, while the ledger and canonical state remain the durable authorities.
     */
    private static final Map<FrontierV3ServerRuntime<?, ?>, Map<ChunkPos, FirstVisibilityWork>> FIRST_VISIBILITY_WORK = new IdentityHashMap<>();
    /**
     * Static completion is already emitted one chunk at a time by the projection queue.  Keep
     * that exact delta for the EFFECT-stage handoff instead of rewalking every exposed record on
     * every server tick merely to find a STATIC_CURRENT entry.
     */
    private static final Map<FrontierV3ServerRuntime<?, ?>, java.util.LinkedHashSet<ChunkPos>> DYNAMIC_CATCH_UP = new IdentityHashMap<>();
    /** Ordinary exposure is fair; player ingress and its declared nest fence take precedence. */
    private static final Map<FrontierV3ServerRuntime<?, ?>, java.util.LinkedHashSet<ChunkPos>> PENDING_FIRST_VISIBILITY = new IdentityHashMap<>();
    private static final Map<FrontierV3ServerRuntime<?, ?>, java.util.LinkedHashSet<ChunkPos>> PRIORITY_FIRST_VISIBILITY = new IdentityHashMap<>();
    /** A load callback can precede cursor publication.  Defer only that exact fence, bounded. */
    private static final Map<FrontierV3ServerRuntime<?, ?>, java.util.LinkedHashSet<ChunkPos>> PENDING_HIVE_FENCES = new IdentityHashMap<>();
    private static final Map<FrontierV3ServerRuntime<?, ?>, java.util.LinkedHashSet<ChunkPos>> PRIORITY_HIVE_FENCES = new IdentityHashMap<>();
    /** A player transfer can precede cursor publication; retain only its exact settlement fence. */
    private static final Map<FrontierV3ServerRuntime<?, ?>, java.util.LinkedHashSet<ChunkPos>> PENDING_SETTLEMENT_FENCES = new IdentityHashMap<>();
    private static final Map<FrontierV3ServerRuntime<?, ?>, java.util.LinkedHashSet<ChunkPos>> PRIORITY_SETTLEMENT_FENCES = new IdentityHashMap<>();
    /**
     * Chunk load is not player ingress.  The graybox dimension can keep generated chunks
     * resident for bootstrap/static ownership work, so a resource facility may only consume
     * COLD state after vanilla has placed an ordinary player at its local boundary.
     */
    private static final Map<FrontierV3ServerRuntime<?, ?>, java.util.Set<ChunkPos>> PLAYER_INGRESS = new IdentityHashMap<>();

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
                cursor = Cursor.from(input, plan, cursor, state);
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

    /**
     * Returns the immutable structural snapshot already compiled by the projection owner for
     * this runtime turn.  Consumers at later physical stages may inspect it, but must never
     * compile a second world-wide baseline on the server tick just to answer a local readiness
     * question.  An unavailable snapshot is intentionally a closed/pending presentation gate.
     */
    static Optional<FrontierGrayboxPlan> publishedStructuralBaseline(FrontierV3ServerRuntime<?, ?> runtime) {
        Cursor cursor = CURSORS.get(runtime);
        return cursor == null || cursor.structuralBaseline == null
                ? Optional.empty() : Optional.of(cursor.structuralBaseline);
    }

    /**
     * One immutable column index derived when the structural cursor compiles its retained plan.
     * Dynamic surface owners use it to remain above a whole declared organ column even before
     * every one of those cells has received its naturally-loaded materialization turn.
     */
    static Optional<Map<Long, Integer>> publishedStructuralCeilings(FrontierV3ServerRuntime<?, ?> runtime) {
        Cursor cursor = CURSORS.get(runtime);
        return cursor == null || cursor.structuralBaseline == null
                ? Optional.empty() : Optional.of(cursor.structuralCeilings);
    }

    static Map<Long, Integer> structuralCeilings(FrontierGrayboxPlan plan) {
        Map<Long, Integer> ceilings = new java.util.HashMap<>();
        plan.cells().keySet().forEach(position -> ceilings.merge(columnKey(position.x(), position.z()), position.y(), Math::max));
        return Map.copyOf(ceilings);
    }

    static long columnKey(int x, int z) { return ((long) x << 32) ^ (z & 0xffffffffL); }

    static Optional<FrontierV3HiveFoundryAudit.HiveExpectations> publishedHiveExpectations(FrontierV3ServerRuntime<?, ?> runtime) {
        Cursor cursor = CURSORS.get(runtime);
        return cursor == null ? Optional.empty() : Optional.ofNullable(cursor.hiveExpectations);
    }

    /** Releases only volatile exposure bookkeeping with the normal runtime cleanup. */
    static void forgetFirstVisibility(FrontierV3ServerRuntime<?, ?> runtime) {
        FIRST_VISIBILITY.remove(runtime); PLAYER_INGRESS.remove(runtime);
        FIRST_VISIBILITY_WORK.remove(runtime);
        DYNAMIC_CATCH_UP.remove(runtime);
        PENDING_FIRST_VISIBILITY.remove(runtime); PRIORITY_FIRST_VISIBILITY.remove(runtime);
        PENDING_HIVE_FENCES.remove(runtime); PRIORITY_HIVE_FENCES.remove(runtime);
        PENDING_SETTLEMENT_FENCES.remove(runtime); PRIORITY_SETTLEMENT_FENCES.remove(runtime);
    }

    /**
     * Actual ChunkEvent.Load completion for an already player-fenced boundary.  A raw server
     * chunk load is not player ingress: recording every bootstrap/generation callback creates a
     * world-sized projection queue which keeps chunks resident and can stall the no-player COLD
     * handoff.  Player ingress records the exact boundary first; this callback only requeues it
     * once vanilla has made that already-declared chunk natural.
     */
    static void observeNaturalChunkLoad(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, ChunkPos chunk) {
        Objects.requireNonNull(level, "first visibility level"); Objects.requireNonNull(runtime, "first visibility runtime");
        Objects.requireNonNull(chunk, "first visibility chunk");
        if (runtime.decodedState().isEmpty() || !level.hasChunk(chunk.x, chunk.z)) return;
        FirstVisibilityRecord record = FIRST_VISIBILITY.getOrDefault(runtime, Map.of()).get(chunk);
        if (record != null && record.status() == FirstVisibility.PENDING) firstVisibilityQueue(runtime, true).add(chunk);
    }

    /**
     * Records a player's exact destination before vanilla has necessarily installed its chunk
     * holder.  This is only an exposure fence: the registered turn still waits for natural
     * availability before inspecting or changing a block, and it never creates a ticket.
     */
    static void observePlayerIngress(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, ChunkPos chunk) {
        Objects.requireNonNull(runtime, "first visibility runtime"); Objects.requireNonNull(chunk, "first visibility chunk");
        FrontierWorldState state = runtime.decodedState().orElse(null);
        if (state == null) return;
        boolean newIngress = PLAYER_INGRESS.computeIfAbsent(runtime, ignored -> new java.util.LinkedHashSet<>()).add(chunk);
        // `runObservedPhysicalTurn` observes the same vanilla-owned player every normal server
        // tick.  An ingress is a one-time admission boundary, not a per-tick invitation to
        // enumerate the complete declared sibling hive fence again.  Replaying that immutable
        // fence for every resident player tick was the production watchdog/TPS path: it made a
        // quiet player's ordinary presence proportional to the full visible nest even after
        // its pending cells were already queued.  The first record stays pending/requeues in
        // the bounded projector below, and a later ordinary chunk load has its own callback,
        // so deduplicating this observation neither drops a facility nor creates a ticket.
        if (!newIngress) return;
        // The exact COLD field is an independent facility owner. Queue it before the larger
        // settlement shell so an already-current harvest cursor cannot remain invisible while
        // decorative/static package chunks consume the bounded structural turn.
        retainResourceSiteVisibility(runtime, state, chunk);
        retainFirstVisibility(runtime, chunk, true);
        retainSettlementVisibility(runtime, chunk);
        retainSiblingHiveVisibility(runtime, chunk, true);
    }

    /**
     * A field is one current facility, not one convenient crop cell.  The first ordinary ingress
     * in its bounded neighbourhood therefore fences every naturally-loaded chunk containing its
     * soil capital, every crop slot, and both irrigation pairs.  This is declaration-indexed
     * facility ownership (twelve sites), never an arbitrary world/chunk search or ticket.
     */
    private static void retainResourceSiteVisibility(FrontierV3ServerRuntime<?, ?> runtime, FrontierWorldState state, ChunkPos ingress) {
        FrontierResourceSitePlan.compile(state.bootstrap()).values().stream()
                .filter(site -> resourceSiteIngressMatches(ingress, site))
                .flatMap(site -> site.managedSlots().stream())
                .map(position -> new ChunkPos(position.x() >> 4, position.z() >> 4))
                .forEach(chunk -> retainFirstVisibility(runtime, chunk, true));
    }

    /**
     * A settlement arrival is a package boundary, not a collection of unrelated structure
     * chunks.  The immutable cursor has already associated its facilities and the locally
     * reachable route cells with each possible ingress chunk.  Retaining that finite package
     * here neither scans the world nor asks vanilla to load a neighbour; unavailable chunks
     * remain pending until ordinary streaming makes them natural.
     */
    private static void retainSettlementVisibility(FrontierV3ServerRuntime<?, ?> runtime, ChunkPos ingress) {
        Cursor cursor = CURSORS.get(runtime);
        if (cursor == null) {
            settlementFenceQueue(runtime, true).add(ingress);
            return;
        }
        cursor.settlementVisibilityChunks(ingress).forEach(chunk -> retainFirstVisibility(runtime, chunk, true));
    }

    /**
     * A player at a farm shell can be one chunk outside its field edge.  This is the bounded
     * ordinary-visibility neighbourhood, not a ticket or a search: the field still has to be
     * naturally resident before the resource owner reads or writes one of its cells.
     */
    static boolean resourceSitePlayerIngressed(FrontierV3ServerRuntime<?, ?> runtime, ResourceSite site) {
        return PLAYER_INGRESS.getOrDefault(runtime, java.util.Set.of()).stream()
                .anyMatch(ingress -> resourceSiteIngressMatches(ingress, site));
    }

    static boolean resourceSiteIngressMatches(ChunkPos ingress, ResourceSite site) {
        return site.managedSlots().stream().map(position -> new ChunkPos(position.x() >> 4, position.z() >> 4))
                .anyMatch(field -> Math.abs(field.x - ingress.x) <= 1 && Math.abs(field.z - ingress.z) <= 1);
    }

    /**
     * A seed nest is one player-facing object even where its named organs straddle adjacent
     * chunks.  Ingress into one naturally loaded organ chunk therefore fences the other
     * already-natural sibling chunks before any label/cocoon can advertise the nest.  The
     * infection owner has a separate, already-published patch snapshot; retain the small set of
     * corresponding surface chunks too, so the ordinary overlay pass can complete the same
     * declared nest before its presentation becomes eligible.  This is bookkeeping only: it
     * neither asks the chunk manager for a sibling nor compiles either plan.
     */
    private static void retainSiblingHiveVisibility(FrontierV3ServerRuntime<?, ?> runtime, ChunkPos ingress, boolean priority) {
        workFor(runtime).siblingHiveFenceRetentions++;
        Cursor cursor = CURSORS.get(runtime);
        if (cursor == null || cursor.hiveExpectations == null) {
            hiveFenceQueue(runtime, priority).add(ingress);
            return;
        }
        // The visible infection footprint is a property of the immutable nest grammar, not a
        // player-time query over the mutable overlay snapshot.  Deriving every 4x4 surface
        // column here made a normal player ingress proportional to the whole visible nest and
        // is the exact live watchdog path.  Cursor construction has already reduced the
        // declared structural and possible overlay footprint to unique chunks; the normal
        // bounded projector still decides which naturally-resident cells are current.
        cursor.hiveVisibilityChunks(ingress).forEach(chunk -> retainFirstVisibility(runtime, chunk, priority));
    }

    private static void retainFirstVisibility(FrontierV3ServerRuntime<?, ?> runtime, ChunkPos chunk, boolean priority) {
        Map<ChunkPos, FirstVisibilityRecord> records = FIRST_VISIBILITY.computeIfAbsent(runtime, ignored -> new LinkedHashMap<>());
        FirstVisibilityRecord record = records.putIfAbsent(chunk, new FirstVisibilityRecord(FirstVisibility.PENDING, -1L, 0));
        if (record == null || record.status() == FirstVisibility.PENDING) firstVisibilityQueue(runtime, priority).add(chunk);
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
        // The event callback intentionally never compiles a plan.  Complete only a bounded
        // queue of the exact callbacks that preceded cursor publication.  Walking every
        // resident record here used to make a one-player ingress proportional to all loaded
        // chunks and was the watchdog path observed in production.
        drainHiveFences(runtime, cursor);
        drainSettlementFences(runtime, cursor);
        int remainingCells = MAX_FIRST_VISIBILITY_CELLS_PER_TICK;
        for (int processed = 0; processed < MAX_FIRST_VISIBILITY_CHUNKS_PER_TICK && remainingCells > 0; processed++) {
            ChunkPos chunk = pollFirstVisibility(runtime);
            if (chunk == null) return;
            FirstVisibilityRecord record = records.get(chunk);
            if (record == null || record.status() != FirstVisibility.PENDING) continue;
            if (!level.hasChunk(chunk.x, chunk.z)) continue;
            FirstVisibilityWork work = firstVisibilityWork(runtime, chunk, cursor.cellsIn(chunk));
            FirstVisibility result = FirstVisibility.PENDING;
            // A queued chunk used to be treated as one bounded unit, but a single hive chunk
            // can contain the entire tall organ stack.  Consume physical cells, not chunks;
            // requeue the exact incomplete boundary so a player ingress cannot monopolise a
            // server tick or starve the ordinary COLD/release lane.
            while (remainingCells > 0 && !work.complete()) {
                ProjectionResult projection = projectFirstVisible(FrontierV3AftermathPhysicalWorld.firstVisibility(level), ledger, state, work.next());
                remainingCells--;
                if (projection == ProjectionResult.CONFLICT) { result = FirstVisibility.BLOCKED; break; }
                work.observe(projection);
                if (work.blocked()) { result = FirstVisibility.BLOCKED; break; }
            }
            if (result == FirstVisibility.PENDING && work.complete()) result = FirstVisibility.STATIC_CURRENT;
            if (result == FirstVisibility.PENDING) {
                firstVisibilityQueue(runtime, true).add(chunk);
                continue;
            }
            firstVisibilityWork(runtime).remove(chunk);
            long revision = runtime.checkpointImage().orElseThrow().revision().value();
            if (records.replace(chunk, record, new FirstVisibilityRecord(result, revision, work.cells()))
                    && result == FirstVisibility.STATIC_CURRENT) DYNAMIC_CATCH_UP.computeIfAbsent(runtime, ignored -> new java.util.LinkedHashSet<>()).add(chunk);
        }
    }

    private static Map<ChunkPos, FirstVisibilityWork> firstVisibilityWork(FrontierV3ServerRuntime<?, ?> runtime) {
        return FIRST_VISIBILITY_WORK.computeIfAbsent(runtime, ignored -> new LinkedHashMap<>());
    }

    private static FirstVisibilityWork firstVisibilityWork(FrontierV3ServerRuntime<?, ?> runtime, ChunkPos chunk, List<GrayboxCell> cells) {
        return firstVisibilityWork(runtime).computeIfAbsent(chunk, ignored -> new FirstVisibilityWork(cells));
    }

    private static java.util.LinkedHashSet<ChunkPos> firstVisibilityQueue(FrontierV3ServerRuntime<?, ?> runtime, boolean priority) {
        return (priority ? PRIORITY_FIRST_VISIBILITY : PENDING_FIRST_VISIBILITY)
                .computeIfAbsent(runtime, ignored -> new java.util.LinkedHashSet<>());
    }

    private static java.util.LinkedHashSet<ChunkPos> hiveFenceQueue(FrontierV3ServerRuntime<?, ?> runtime, boolean priority) {
        return (priority ? PRIORITY_HIVE_FENCES : PENDING_HIVE_FENCES)
                .computeIfAbsent(runtime, ignored -> new java.util.LinkedHashSet<>());
    }

    private static java.util.LinkedHashSet<ChunkPos> settlementFenceQueue(FrontierV3ServerRuntime<?, ?> runtime, boolean priority) {
        return (priority ? PRIORITY_SETTLEMENT_FENCES : PENDING_SETTLEMENT_FENCES)
                .computeIfAbsent(runtime, ignored -> new java.util.LinkedHashSet<>());
    }

    private static ChunkPos pollFirstVisibility(FrontierV3ServerRuntime<?, ?> runtime) {
        ChunkPos priority = poll(PRIORITY_FIRST_VISIBILITY.get(runtime));
        return priority != null ? priority : poll(PENDING_FIRST_VISIBILITY.get(runtime));
    }

    private static void drainHiveFences(FrontierV3ServerRuntime<?, ?> runtime, Cursor cursor) {
        for (int drained = 0; drained < MAX_FIRST_VISIBILITY_CHUNKS_PER_TICK; drained++) {
            ChunkPos ingress = poll(PRIORITY_HIVE_FENCES.get(runtime));
            boolean priority = ingress != null;
            if (ingress == null) ingress = poll(PENDING_HIVE_FENCES.get(runtime));
            if (ingress == null) return;
            // The cursor is now present.  This does bounded declared-neighbour bookkeeping;
            // it still never requests a chunk from vanilla.
            retainSiblingHiveVisibility(runtime, ingress, priority);
        }
    }

    private static void drainSettlementFences(FrontierV3ServerRuntime<?, ?> runtime, Cursor cursor) {
        for (int drained = 0; drained < MAX_FIRST_VISIBILITY_CHUNKS_PER_TICK; drained++) {
            ChunkPos ingress = poll(PRIORITY_SETTLEMENT_FENCES.get(runtime));
            if (ingress == null) ingress = poll(PENDING_SETTLEMENT_FENCES.get(runtime));
            if (ingress == null) return;
            cursor.settlementVisibilityChunks(ingress).forEach(chunk -> retainFirstVisibility(runtime, chunk, true));
        }
    }

    private static ChunkPos poll(java.util.LinkedHashSet<ChunkPos> queue) {
        if (queue == null || queue.isEmpty()) return null;
        java.util.Iterator<ChunkPos> iterator = queue.iterator();
        ChunkPos next = iterator.next(); iterator.remove();
        return next;
    }

    /**
     * One exact chunk-local dependency pass.  A deferred cell is retained for a later pass only
     * after another cell progressed; a wholly unresolved pass is the same fail-closed physical
     * conflict as the former synchronous loop, merely spread across bounded server turns.
     */
    private static final class FirstVisibilityWork {
        private final java.util.ArrayDeque<GrayboxCell> pending;
        private final int cells;
        private int remainingInPass;
        private boolean progressedInPass;
        private boolean blocked;

        FirstVisibilityWork(List<GrayboxCell> cells) {
            this.pending = new java.util.ArrayDeque<>(cells);
            this.cells = cells.size();
            this.remainingInPass = cells.size();
        }
        GrayboxCell next() { return current = pending.removeFirst(); }
        void observe(ProjectionResult result) {
            if (result == ProjectionResult.DEFERRED) pending.addLast(current); else progressedInPass = true;
            remainingInPass--;
            if (remainingInPass == 0 && !pending.isEmpty()) {
                if (!progressedInPass) blocked = true;
                else { remainingInPass = pending.size(); progressedInPass = false; }
            }
            current = null;
        }
        // The current cell is retained only for a deferred result; keeping it here avoids
        // allocating a list for every bounded projection turn.
        private GrayboxCell current;
        boolean complete() { return pending.isEmpty(); }
        boolean blocked() { return blocked; }
        int cells() { return cells; }
    }

    /**
     * Promotes static first visibility only after the ordinary physical turn has completed its
     * dynamic observation for that checkpoint, immediately before scene consumption.
     */
    static void completeDynamicCatchUp(FrontierV3ServerRuntime<?, ?> runtime) {
        long revision = runtime.checkpointImage().orElseThrow().revision().value();
        Map<ChunkPos, FirstVisibilityRecord> records = FIRST_VISIBILITY.get(runtime);
        if (records == null) return;
        // EFFECT owners can append an unrelated canonical receipt between the PROJECTION stage
        // and this final observation boundary.  The fence is one completed registered physical
        // turn, not accidental equality with the projection-stage checkpoint revision.
        ChunkPos chunk;
        while ((chunk = poll(DYNAMIC_CATCH_UP.get(runtime))) != null) {
            FirstVisibilityRecord record = records.get(chunk);
            if (record != null && record.status() == FirstVisibility.STATIC_CURRENT) {
                records.replace(chunk, record, new FirstVisibilityRecord(FirstVisibility.READY, revision, record.cells()));
            }
        }
    }

    /** A scene can use an exposed chunk only after the static and dynamic visibility boundaries completed. */
    static boolean sceneEligible(FrontierV3ServerRuntime<?, ?> runtime, BlockPosition position) {
        FirstVisibilityRecord visibility = FIRST_VISIBILITY.getOrDefault(runtime, Map.of()).get(new ChunkPos(position.x() >> 4, position.z() >> 4));
        return visibility == null || visibility.status() == FirstVisibility.READY;
    }

    /**
     * A dynamic surface may use an ordinary chunk only after this owner's retained static
     * first-visibility work for that same chunk is complete.  Unknown chunks remain eligible:
     * this is an ingress-race fence, never a global loaded-chunk policy or a ticket request.
     */
    static boolean staticVisibilityComplete(FrontierV3ServerRuntime<?, ?> runtime, ChunkPos chunk) {
        FirstVisibilityRecord visibility = FIRST_VISIBILITY.getOrDefault(runtime, Map.of()).get(chunk);
        return visibility == null || visibility.status() == FirstVisibility.STATIC_CURRENT || visibility.status() == FirstVisibility.READY;
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
        private final Map<Long, Integer> structuralCeilings;
        private final FrontierV3HiveFoundryAudit.HiveExpectations hiveExpectations;
        private final Map<ChunkPos, List<GrayboxCell>> hiveVisibilityCells;
        private final Map<ChunkPos, List<ChunkPos>> hiveVisibilityChunks;
        private final Map<ChunkPos, List<ChunkPos>> settlementVisibilityChunks;
        private final List<ChunkCells> chunks;
        private int nextChunkIndex;

        private Cursor(FrontierGrayboxPlan.StructuralInput input, FrontierGrayboxPlan structuralBaseline, Map<Long, Integer> structuralCeilings,
                       FrontierV3HiveFoundryAudit.HiveExpectations hiveExpectations, FrontierWorldState state, List<ChunkCells> chunks, int nextChunkIndex,
                       java.util.Set<BlockPos> activeWorksiteStaging) {
            this.input = input;
            this.structuralBaseline = structuralBaseline;
            this.structuralCeilings = structuralCeilings;
            this.hiveExpectations = hiveExpectations;
            this.hiveVisibilityCells = hiveVisibilityFence(hiveExpectations);
            this.hiveVisibilityChunks = hiveVisibilityChunks(hiveVisibilityCells);
            this.settlementVisibilityChunks = FrontierV3SettlementVisibilityIndex.compile(state, structuralBaseline);
            this.chunks = chunks;
            this.nextChunkIndex = nextChunkIndex;
            this.activeWorksiteStaging = activeWorksiteStaging;
        }
        private final java.util.Set<BlockPos> activeWorksiteStaging;
        static Cursor from(FrontierGrayboxPlan.StructuralInput input, FrontierGrayboxPlan plan, Cursor prior, FrontierWorldState state) {
            List<GrayboxCell> cells = plan.cells().values().stream().sorted(Comparator
                    .comparingInt((GrayboxCell cell) -> cell.position().y())
                    .thenComparingInt(cell -> cell.position().x()).thenComparingInt(cell -> cell.position().z())).toList();
            return fromCells(input, plan, structuralCeilings(plan), FrontierV3HiveFoundryAudit.expectations(state, plan), state, cells, prior, plan.cells().values().stream()
                    .filter(cell -> cell.semanticPart() == GrayboxSemanticPart.WORKSITE_STAGING)
                    .map(FrontierV3GrayboxExecutor::toMinecraft).collect(java.util.stream.Collectors.toUnmodifiableSet()));
        }
        static Cursor fromCells(FrontierGrayboxPlan.StructuralInput input, List<GrayboxCell> cells, Cursor prior) {
            return fromCells(input, null, Map.of(), null, null, cells, prior, java.util.Set.of());
        }
        private static Cursor fromCells(FrontierGrayboxPlan.StructuralInput input, FrontierGrayboxPlan structuralBaseline, Map<Long, Integer> structuralCeilings,
                                        FrontierV3HiveFoundryAudit.HiveExpectations hiveExpectations, FrontierWorldState state,
                                        List<GrayboxCell> cells, Cursor prior,
                                        java.util.Set<BlockPos> activeWorksiteStaging) {
            Map<ChunkKey, List<GrayboxCell>> grouped = new LinkedHashMap<>();
            cells.forEach(cell -> grouped.computeIfAbsent(ChunkKey.of(cell), ignored -> new ArrayList<>()).add(cell));
            List<ChunkCells> chunks = grouped.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(entry -> {
                ChunkCells before = prior == null ? null : prior.chunk(entry.getKey());
                return new ChunkCells(entry.getKey(), List.copyOf(entry.getValue()), before);
            }).toList();
            int next = prior == null || chunks.isEmpty() ? 0 : indexOf(chunks, prior.nextChunkKey());
            return new Cursor(input, structuralBaseline, structuralCeilings, hiveExpectations, state, chunks, next, activeWorksiteStaging);
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
        /** Immutable ingress lookup; never scan all hive cells from a server tick. */
        List<GrayboxCell> hiveVisibilityCells(ChunkPos ingress) { return hiveVisibilityCells.getOrDefault(ingress, List.of()); }
        /** Complete structural plus possible infection-surface chunk fence for one declared nest ingress. */
        List<ChunkPos> hiveVisibilityChunks(ChunkPos ingress) { return hiveVisibilityChunks.getOrDefault(ingress, List.of()); }
        /** Complete local authored package for one already-known ordinary settlement ingress. */
        List<ChunkPos> settlementVisibilityChunks(ChunkPos ingress) { return settlementVisibilityChunks.getOrDefault(ingress, List.of()); }

        Optional<GrayboxCell> nextNaturallyLoaded(Predicate<GrayboxCell> loaded) {
            if (chunks.isEmpty()) return Optional.empty();
            // An ordinary post-ingress turn used to walk every declared plan chunk until it
            // found one resident. At 2,160 loaded/exposed chunks that made an otherwise idle
            // no-player tick proportional to the whole world and starved the server before the
            // COLD release could run. The local probe remains fixed: first visibility owns
            // prompt whole-facility projection; this fallback only provides bounded discovery.
            for (int attempts = 0; attempts < Math.min(chunks.size(), MAX_NATURAL_CHUNK_PROBES_PER_CELL); attempts++) {
                ChunkCells chunk = chunks.get(nextChunkIndex);
                nextChunkIndex = (nextChunkIndex + 1) % chunks.size();
                if (loaded.test(chunk.sample())) return Optional.of(chunk.next());
            }
            return Optional.empty();
        }
        private ChunkCells chunk(ChunkKey key) {
            return chunks.stream().filter(chunk -> chunk.key().equals(key)).findFirst().orElse(null);
        }
        static Map<ChunkPos, List<GrayboxCell>> hiveVisibilityFence(FrontierV3HiveFoundryAudit.HiveExpectations expectations) {
            if (expectations == null) return Map.of();
            // Several organs in one coherent nest can share an ingress chunk.  The old
            // put() silently retained whichever organ happened to be visited last, so an
            // ordinary player entering that shared chunk only fenced a fragment of the nest.
            // Merge the declared sibling envelopes: the index remains immutable and bounded,
            // but a shared boundary now owns the complete current nest it advertises.
            Map<ChunkPos, java.util.LinkedHashSet<GrayboxCell>> byIngress = new java.util.HashMap<>();
            expectations.cells().forEach((organ, cells) -> {
                List<GrayboxCell> nestCells = expectations.nestMembers().getOrDefault(organ, List.of()).stream()
                        .flatMap(member -> expectations.cells().getOrDefault(member, List.of()).stream()).toList();
                cells.stream().map(cell -> new ChunkPos(cell.position().x() >> 4, cell.position().z() >> 4)).distinct()
                        .forEach(chunk -> byIngress.computeIfAbsent(chunk, ignored -> new java.util.LinkedHashSet<>()).addAll(nestCells));
            });
            return byIngress.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                    Map.Entry::getKey, entry -> List.copyOf(entry.getValue())));
        }
        /**
         * Compile the entire declared physical fence once.  An {@link InfectionCell} is a 4x4
         * X/Z square, so translating it to chunk coverage is constant-bounded and does not
         * discover terrain, inspect a chunk, or read the dynamic overlay.  Including a possible
         * (currently inactive) patch chunk is safe: first visibility only queues natural chunks
         * and the overlay owner independently decides whether it has a current desired cell.
         */
        static Map<ChunkPos, List<ChunkPos>> hiveVisibilityChunks(Map<ChunkPos, List<GrayboxCell>> cellsByIngress) {
            Map<ChunkPos, List<ChunkPos>> result = new java.util.HashMap<>();
            cellsByIngress.forEach((ingress, cells) -> {
                java.util.LinkedHashSet<ChunkPos> chunks = new java.util.LinkedHashSet<>();
                for (GrayboxCell cell : cells) {
                    chunks.add(new ChunkPos(cell.position().x() >> 4, cell.position().z() >> 4));
                    InfectionCell infection = InfectionCell.at(cell.position());
                    int originX = Math.multiplyExact(infection.x(), InfectionCell.BLOCKS);
                    int originZ = Math.multiplyExact(infection.z(), InfectionCell.BLOCKS);
                    int lastX = Math.addExact(originX, InfectionCell.BLOCKS - 1);
                    int lastZ = Math.addExact(originZ, InfectionCell.BLOCKS - 1);
                    for (int chunkX = Math.floorDiv(originX, 16); chunkX <= Math.floorDiv(lastX, 16); chunkX++) {
                        for (int chunkZ = Math.floorDiv(originZ, 16); chunkZ <= Math.floorDiv(lastZ, 16); chunkZ++) {
                            chunks.add(new ChunkPos(chunkX, chunkZ));
                        }
                    }
                }
                result.put(ingress, List.copyOf(chunks));
            });
            return Map.copyOf(result);
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
        private int compatibilityChecks, freshnessConstructions, planCompilations, providerAcquisitions, pointQueries, siblingHiveFenceRetentions;
        ProjectionWorkSnapshot snapshot() { return new ProjectionWorkSnapshot(compatibilityChecks, freshnessConstructions, planCompilations, providerAcquisitions, pointQueries, siblingHiveFenceRetentions); }
    }
    record ProjectionWorkSnapshot(int compatibilityChecks, int freshnessConstructions, int planCompilations, int providerAcquisitions,
                                  int pointQueries, int siblingHiveFenceRetentions) { }
}
