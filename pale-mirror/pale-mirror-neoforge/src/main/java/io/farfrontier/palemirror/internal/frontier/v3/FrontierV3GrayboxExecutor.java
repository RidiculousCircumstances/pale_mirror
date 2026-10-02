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
import io.farfrontier.palemirror.frontier.v3.model.DeferredAftermath;
import io.farfrontier.palemirror.frontier.v3.model.DeferredAftermathCell;
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
final class FrontierV3GrayboxExecutor {
    private static final int MAX_CELLS_PER_TICK = 8;
    private static final int MAX_NATURAL_CHUNK_PROBES_PER_CELL = 64;
    private static final int MAX_WORKSITE_STAGING_PROBES_PER_CELL = 8;
    private static final int MAX_FIRST_VISIBILITY_CHUNKS_PER_TICK = 16;
    private static final int MAX_FIRST_VISIBILITY_CELLS_PER_TICK = 8;
    private static final Map<FrontierV3ServerRuntime<?, ?>, Cursor> CURSORS = new IdentityHashMap<>();
    private static final Map<FrontierV3ServerRuntime<?, ?>, ProjectionWork> WORK = new IdentityHashMap<>();
    private static final Map<FrontierV3ServerRuntime<?, ?>, Map<ChunkPos, FirstVisibilityRecord>> FIRST_VISIBILITY = new IdentityHashMap<>();
    private static final Map<FrontierV3ServerRuntime<?, ?>, Map<ChunkPos, FirstVisibilityWork>> FIRST_VISIBILITY_WORK = new IdentityHashMap<>();
    private static final Map<FrontierV3ServerRuntime<?, ?>, java.util.LinkedHashSet<ChunkPos>> DYNAMIC_CATCH_UP = new IdentityHashMap<>();
    private static final Map<FrontierV3ServerRuntime<?, ?>, java.util.LinkedHashSet<ChunkPos>> PENDING_FIRST_VISIBILITY = new IdentityHashMap<>();
    private static final Map<FrontierV3ServerRuntime<?, ?>, java.util.LinkedHashSet<ChunkPos>> PRIORITY_FIRST_VISIBILITY = new IdentityHashMap<>();
    private static final Map<FrontierV3ServerRuntime<?, ?>, java.util.LinkedHashSet<ChunkPos>> PENDING_HIVE_FENCES = new IdentityHashMap<>();
    private static final Map<FrontierV3ServerRuntime<?, ?>, java.util.LinkedHashSet<ChunkPos>> PRIORITY_HIVE_FENCES = new IdentityHashMap<>();
    private static final Map<FrontierV3ServerRuntime<?, ?>, java.util.LinkedHashSet<ChunkPos>> PENDING_SETTLEMENT_FENCES = new IdentityHashMap<>();
    private static final Map<FrontierV3ServerRuntime<?, ?>, java.util.LinkedHashSet<ChunkPos>> PRIORITY_SETTLEMENT_FENCES = new IdentityHashMap<>();
    private static final Map<FrontierV3ServerRuntime<?, ?>, java.util.Set<ChunkPos>> PLAYER_INGRESS = new IdentityHashMap<>();
    enum ProjectionResult { APPLIED, CURRENT, CONFLICT, DEFERRED }
    enum BlockBreakObservation { UNMANAGED, ACCEPTED, REJECTED }
    private enum FirstVisibility { PENDING, STATIC_CURRENT, READY, BLOCKED }
    private record FirstVisibilityRecord(FirstVisibility status, long revision, int cells) { }
    record FirstVisibilitySnapshot(ChunkPos chunk, String status, long revision, int cells) {
        static FirstVisibilitySnapshot unavailable() { return new FirstVisibilitySnapshot(null, "INVALID", -1L, 0); }
        static FirstVisibilitySnapshot unobserved(ChunkPos chunk) { return new FirstVisibilitySnapshot(chunk, "UNOBSERVED", -1L, 0); }
    }
    private FrontierV3GrayboxExecutor() { }
    static int firstVisibilityCellBudget() { return MAX_FIRST_VISIBILITY_CELLS_PER_TICK; }
    static int structuralCellBudget() { return MAX_CELLS_PER_TICK; }
    static void primeStructuralBaseline(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        Objects.requireNonNull(runtime, "runtime");
        FrontierWorldState state = runtime.decodedState().orElse(null);
        if (state != null) cursor(runtime, state);
    }
    static void tick(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        FrontierWorldState state = runtime.decodedState().orElse(null);
        if (state == null) return;
        FrontierV3GrayboxLedger ledger = FrontierV3GrayboxLedger.get(level);
        ProjectionWork work = workFor(runtime);
        long started = System.nanoTime();
        Cursor cursor = cursor(runtime, state);
        work.cursorPath(System.nanoTime() - started);
        started = System.nanoTime();
        projectPendingFirstVisibility(level, ledger, runtime, state, cursor);
        work.firstVisibilityPath(System.nanoTime() - started);
        started = System.nanoTime();
        retireStaleWorksiteStaging(level, ledger, cursor);
        work.stagingRetirementPath(System.nanoTime() - started);
        started = System.nanoTime();
        tick(FrontierV3AftermathPhysicalWorld.minecraft(level), runtime, state, cursor);
        work.deferredProjectionPath(System.nanoTime() - started);
    }
    static void tick(FrontierV3AftermathPhysicalWorld world, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        if (runtime.canonicalState().isEmpty()) return;
        FrontierWorldState state = runtime.decodedState().orElse(null);
        if (state == null) return;
        ProjectionWork work = workFor(runtime);
        long started = System.nanoTime();
        Cursor cursor = cursor(runtime, state);
        work.cursorPath(System.nanoTime() - started);
        started = System.nanoTime();
        tick(world, runtime, state, cursor);
        work.deferredProjectionPath(System.nanoTime() - started);
    }
    static void refresh(FrontierV3AftermathPhysicalWorld world, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                        FrontierWorldState state) {
        ProjectionWork work = workFor(runtime);
        long started = System.nanoTime();
        Cursor cursor = cursor(runtime, Objects.requireNonNull(state, "state"));
        work.cursorPath(System.nanoTime() - started);
        started = System.nanoTime();
        tick(world, runtime, state, cursor);
        work.deferredProjectionPath(System.nanoTime() - started);
    }
    private static Cursor cursor(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state) {
        Cursor cursor = CURSORS.get(runtime);
        ProjectionWork work = workFor(runtime);
        if (cursor != null) work.compatibilityChecks++;
        if (cursor == null || !cursor.input().matches(state)) {
            work.freshnessConstructions++;
            FrontierGrayboxPlan.StructuralInput input = FrontierGrayboxPlan.structuralInput(state);
            if (cursor == null || !input.equals(cursor.input())) {
                if (cursor != null && cursor.input().matchesStableBaseline(input)) {
                    if (cursor.input().matchesDynamicHiveOverlay(input)) {
                        work.lastRefresh("STRUCTURAL_STABLE_BASELINE", "WORKSITE_STAGING");
                        cursor = cursor.withWorksiteStaging(input, state);
                    } else {
                        work.lastRefresh("DYNAMIC_HIVE_OVERLAY", "DYNAMIC_HIVE_OVERLAY");
                        cursor = cursor.withDynamicHiveOverlay(input, state);
                    }
                } else {
                    String trigger = cursor == null ? "INITIAL_CURSOR" : "STRUCTURAL_INPUT_CHANGED:"
                            + input.stableBaselineDifference(cursor.input());
                    work.lastRefresh(trigger, "COMPILE_STABLE_BASELINE");
                    work.planCompilations++;
                    FrontierGrayboxPlan plan = FrontierGrayboxPlan.compileStableStructuralBaseline(state);
                    cursor = Cursor.from(input, plan, cursor, state);
                }
                CURSORS.put(runtime, cursor);
            } else {
                work.lastRefresh("STRUCTURAL_INPUT_EQUAL", "INPUT_REFRESH");
                cursor.replaceInput(input);
            }
        }
        return cursor;
    }
    private static void tick(FrontierV3AftermathPhysicalWorld world, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                             FrontierWorldState state, Cursor cursor) {
        FrontierV3GrayboxLedger ledger = world.ledger();
        int count = 0;
        GrayboxCell pendingLoss = pendingAftermathLoss(world, state, cursor).orElse(null);
        if (pendingLoss != null) {
            retainKnownLoss(world, pendingLoss);
            count++;
        }
        for (; count < MAX_CELLS_PER_TICK; count++) {
            GrayboxCell cell = cursor.nextNaturallyLoaded(candidate -> world.naturallyLoaded(candidate.position())
                    && allowsDeferredProjection(runtime, candidate)).orElse(null);
            if (cell == null) return;
            PhysicalDelta delta = state.physicalDeltas().get(cell.position());
            if (delta != null) {
                if (matchesKnownLoss(delta, cell)) retainKnownLoss(world, cell);
                continue;
            }
            project(world, cell);
        }
    }
    private static Optional<GrayboxCell> pendingAftermathLoss(FrontierV3AftermathPhysicalWorld world, FrontierWorldState state,
                                                                Cursor cursor) {
        return state.deferredAftermath().entries().values().stream().filter(aftermath -> !aftermath.terminal())
                .map(DeferredAftermath::nextPending).filter(Objects::nonNull)
                .sorted(Comparator.comparingLong((DeferredAftermathCell cell) -> io.farfrontier.palemirror.frontier.v3.model.DeferredAftermath.chunkKey(cell.position()))
                        .thenComparingInt(cell -> cell.position().x()).thenComparingInt(cell -> cell.position().y())
                        .thenComparingInt(cell -> cell.position().z()))
                .filter(cell -> world.naturallyLoaded(cell.position())).map(cell -> cursor.structuralCellAt(cell.position()))
                .flatMap(Optional::stream).filter(cell -> {
                    PhysicalDelta delta = state.physicalDeltas().get(cell.position());
                    return delta != null && matchesKnownLoss(delta, cell);
                }).findFirst();
    }
    static void forget(FrontierV3ServerRuntime<?, ?> runtime) { CURSORS.remove(runtime); WORK.remove(runtime); }
    static Optional<FrontierGrayboxPlan> publishedStructuralBaseline(FrontierV3ServerRuntime<?, ?> runtime) {
        Cursor cursor = CURSORS.get(runtime);
        return cursor == null || cursor.structuralBaseline == null
                ? Optional.empty() : Optional.of(cursor.structuralBaseline);
    }
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
    static void forgetFirstVisibility(FrontierV3ServerRuntime<?, ?> runtime) {
        FIRST_VISIBILITY.remove(runtime); PLAYER_INGRESS.remove(runtime);
        FIRST_VISIBILITY_WORK.remove(runtime);
        DYNAMIC_CATCH_UP.remove(runtime);
        PENDING_FIRST_VISIBILITY.remove(runtime); PRIORITY_FIRST_VISIBILITY.remove(runtime);
        PENDING_HIVE_FENCES.remove(runtime); PRIORITY_HIVE_FENCES.remove(runtime);
        PENDING_SETTLEMENT_FENCES.remove(runtime); PRIORITY_SETTLEMENT_FENCES.remove(runtime);
    }
    static void observeNaturalChunkLoad(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, ChunkPos chunk) {
        Objects.requireNonNull(level, "first visibility level"); Objects.requireNonNull(runtime, "first visibility runtime");
        Objects.requireNonNull(chunk, "first visibility chunk");
        if (runtime.decodedState().isEmpty() || !level.hasChunk(chunk.x, chunk.z)) return;
        Cursor cursor = CURSORS.get(runtime);
        // Natural chunk arrival is the first lawful chance to make immutable plan geometry
        // current.  Do not wait for an EntityJoin ingress event: that made roads and settlement
        // surfaces visibly arrive after the player.  This only retains already loaded plan work;
        // it neither force-loads a neighbor nor changes the shared eight-cell writer budget.
        if (naturalFirstVisibilityCandidate(cursor, chunk)) retainFirstVisibility(runtime, chunk, false);
        FirstVisibilityRecord record = FIRST_VISIBILITY.getOrDefault(runtime, Map.of()).get(chunk);
        if (record != null && record.status() == FirstVisibility.PENDING) firstVisibilityQueue(runtime, true).add(chunk);
        if (record != null && record.status() == FirstVisibility.READY) {
            FIRST_VISIBILITY.get(runtime).put(chunk, new FirstVisibilityRecord(
                    FirstVisibility.STATIC_CURRENT, record.revision(), record.cells()));
            DYNAMIC_CATCH_UP.computeIfAbsent(runtime, ignored -> new java.util.LinkedHashSet<>()).add(chunk);
        }
    }
    static boolean naturalFirstVisibilityCandidate(Cursor cursor, ChunkPos chunk) {
        return cursor != null && !cursor.cellsIn(chunk).isEmpty();
    }
    static void observePlayerIngress(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, ChunkPos chunk) {
        Objects.requireNonNull(runtime, "first visibility runtime"); Objects.requireNonNull(chunk, "first visibility chunk");
        FrontierWorldState state = runtime.decodedState().orElse(null);
        if (state == null) return;
        boolean newIngress = PLAYER_INGRESS.computeIfAbsent(runtime, ignored -> new java.util.LinkedHashSet<>()).add(chunk);
        if (!newIngress) return;
        retainResourceSiteVisibility(runtime, state, chunk);
        retainFirstVisibility(runtime, chunk, true);
        retainSettlementVisibility(runtime, chunk);
        retainSiblingHiveVisibility(runtime, chunk, true);
    }
    private static void retainResourceSiteVisibility(FrontierV3ServerRuntime<?, ?> runtime, FrontierWorldState state, ChunkPos ingress) {
        state.resourceSiteDescriptors().values().stream()
                .filter(site -> resourceSiteIngressMatches(ingress, site))
                .flatMap(site -> site.managedSlots().stream())
                .map(position -> new ChunkPos(position.x() >> 4, position.z() >> 4))
                .forEach(chunk -> retainFirstVisibility(runtime, chunk, true));
    }
    private static void retainSettlementVisibility(FrontierV3ServerRuntime<?, ?> runtime, ChunkPos ingress) {
        Cursor cursor = CURSORS.get(runtime);
        if (cursor == null) {
            settlementFenceQueue(runtime, true).add(ingress);
            return;
        }
        cursor.settlementVisibilityChunks(ingress).forEach(chunk -> retainFirstVisibility(runtime, chunk, true));
    }
    static boolean resourceSitePlayerIngressed(FrontierV3ServerRuntime<?, ?> runtime, ResourceSite site) {
        return PLAYER_INGRESS.getOrDefault(runtime, java.util.Set.of()).stream()
                .anyMatch(ingress -> resourceSiteIngressMatches(ingress, site));
    }
    static boolean resourceSiteProjectionDemanded(FrontierV3ServerRuntime<?, ?> runtime, ServerLevel level, ResourceSite site) {
        boolean currentPlayerDemand = level.players().stream()
                .filter(player -> !player.isSpectator())
                .anyMatch(player -> site.managedSlots().stream().anyMatch(position ->
                        player.getChunkTrackingView().contains(position.x() >> 4, position.z() >> 4)));
        // Current presentation demand is wider than physical work eligibility. Holding a
        // visible chunk while requiring the player to approach it would deadlock preparation.
        return resourceSiteProjectionDemanded(resourceSitePlayerIngressed(runtime, site), currentPlayerDemand);
    }
    static boolean resourceSiteProjectionDemanded(boolean retainedIngress, boolean currentPlayerDemand) {
        return retainedIngress && currentPlayerDemand;
    }
    static boolean resourceSiteIngressMatches(ChunkPos ingress, ResourceSite site) {
        return site.managedSlots().stream().map(position -> new ChunkPos(position.x() >> 4, position.z() >> 4))
                .anyMatch(field -> Math.abs(field.x - ingress.x) <= 1 && Math.abs(field.z - ingress.z) <= 1);
    }
    private static void retainSiblingHiveVisibility(FrontierV3ServerRuntime<?, ?> runtime, ChunkPos ingress, boolean priority) {
        workFor(runtime).siblingHiveFenceRetentions++;
        Cursor cursor = CURSORS.get(runtime);
        if (cursor == null || cursor.hiveExpectations == null) {
            hiveFenceQueue(runtime, priority).add(ingress);
            return;
        }
        cursor.hiveVisibilityChunks(ingress).forEach(chunk -> retainFirstVisibility(runtime, chunk, priority));
    }
    private static void retainFirstVisibility(FrontierV3ServerRuntime<?, ?> runtime, ChunkPos chunk, boolean priority) {
        Map<ChunkPos, FirstVisibilityRecord> records = FIRST_VISIBILITY.computeIfAbsent(runtime, ignored -> new LinkedHashMap<>());
        FirstVisibilityRecord record = records.putIfAbsent(chunk, new FirstVisibilityRecord(FirstVisibility.PENDING, -1L, 0));
        if (record == null || record.status() == FirstVisibility.PENDING) firstVisibilityQueue(runtime, priority).add(chunk);
    }
    private static void projectPendingFirstVisibility(ServerLevel level, FrontierV3GrayboxLedger ledger,
                                                       FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                                       FrontierWorldState state, Cursor cursor) {
        Map<ChunkPos, FirstVisibilityRecord> records = FIRST_VISIBILITY.get(runtime);
        if (records == null) return;
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
            long revision = runtime.canonicalState().orElseThrow().revision().value();
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
        private GrayboxCell current;
        boolean complete() { return pending.isEmpty(); }
        boolean blocked() { return blocked; }
        int cells() { return cells; }
    }
    static void completeDynamicCatchUp(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        long revision = runtime.canonicalState().orElseThrow().revision().value();
        Map<ChunkPos, FirstVisibilityRecord> records = FIRST_VISIBILITY.get(runtime);
        if (records == null) return;
        ChunkPos chunk;
        var pending = DYNAMIC_CATCH_UP.get(runtime);
        // Allocation safety ceiling: bound discovery as well as mutation. Requeued owners
        // retain FIFO fairness; a stalled chunk cannot multiply this turn's proof work.
        int attempts = pending == null ? 0 : Math.min(pending.size(), 32);
        while (attempts-- > 0 && (chunk = poll(pending)) != null) {
            FirstVisibilityRecord record = records.get(chunk);
            if (record != null && record.status() == FirstVisibility.STATIC_CURRENT) {
                if (!FrontierV3HotHandoff.inspect(level, runtime, chunk).ready()) {
                    pending.add(chunk);
                    continue;
                }
                records.replace(chunk, record, new FirstVisibilityRecord(FirstVisibility.READY, revision, record.cells()));
            }
        }
    }
    static boolean sceneEligible(FrontierV3ServerRuntime<?, ?> runtime, BlockPosition position) {
        FirstVisibilityRecord visibility = FIRST_VISIBILITY.getOrDefault(runtime, Map.of()).get(new ChunkPos(position.x() >> 4, position.z() >> 4));
        return visibility == null || visibility.status() == FirstVisibility.READY;
    }
    static boolean staticVisibilityComplete(FrontierV3ServerRuntime<?, ?> runtime, ChunkPos chunk) {
        FirstVisibilityRecord visibility = FIRST_VISIBILITY.getOrDefault(runtime, Map.of()).get(chunk);
        return visibility == null || visibility.status() == FirstVisibility.STATIC_CURRENT || visibility.status() == FirstVisibility.READY;
    }
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
                ledger.defer(toMinecraft(delta.position())); // preserve a visible physical block; canonical reduction owns terminal truth
            }
            return BlockBreakObservation.ACCEPTED;
        } catch (RuntimeException failed) {
            return BlockBreakObservation.REJECTED;
        }
    }
    static Optional<PhysicalDeltaObserved> preparePhysicalDelta(ServerLevel level, FrontierV3GrayboxLedger ledger,
                                                                 BlockPos position, String cause) {
        FrontierV3GrayboxLedger.Claim claim = ledger.claim(position);
        if (claim == null || claim.deferred()) return Optional.empty();
        GrayboxMaterial material;
        GrayboxSemanticPart part;
        io.farfrontier.palemirror.frontier.v3.model.PhysicalDeltaSemanticTargetKind targetKind;
        try {
            material = GrayboxMaterial.valueOf(claim.material());
            part = GrayboxSemanticPart.valueOf(claim.semanticPart());
            targetKind = io.farfrontier.palemirror.frontier.v3.model.PhysicalDeltaSemanticTargetKind.fromWireTag(claim.targetTag());
        } catch (IllegalArgumentException malformed) {
            ledger.defer(position);
            return Optional.empty();
        }
        if (!level.getBlockState(position).equals(material(material))) {
            ledger.defer(position);
            return Optional.empty();
        }
        return Optional.of(new PhysicalDeltaObserved(new PhysicalDelta(
                new io.farfrontier.palemirror.frontier.v3.model.BlockPosition(position.getX(), position.getY(), position.getZ()),
                PhysicalDeltaKind.KNOWN_SEMANTIC_LOSS, Optional.of(new io.farfrontier.palemirror.frontier.v3.model.PhysicalDeltaSemanticTarget(
                targetKind, new io.farfrontier.palemirror.frontier.v3.api.SubjectId(claim.owner()))),
                Optional.of(part), cause)));
    }
    static Optional<List<PhysicalDelta>> preparePhysicalDeltas(ServerLevel level, FrontierV3GrayboxLedger ledger,
                                                                 BlockPos position, String cause) {
        Optional<PhysicalDeltaObserved> direct = preparePhysicalDelta(level, ledger, position, cause);
        if (direct.isEmpty()) return Optional.empty();
        List<PhysicalDelta> deltas = new ArrayList<>();
        deltas.add(direct.orElseThrow().delta());
        return Optional.of(List.copyOf(deltas));
    }
    static Optional<StructureDamaged> prepareStructureDamage(ServerLevel level, FrontierV3GrayboxLedger ledger,
                                                              BlockPos position, String cause) {
        Optional<PhysicalDeltaObserved> observed = preparePhysicalDelta(level, ledger, position, cause);
        if (observed.isEmpty() || observed.orElseThrow().delta().semanticTarget().filter(target -> target.kind()
                == io.farfrontier.palemirror.frontier.v3.model.PhysicalDeltaSemanticTargetKind.SETTLEMENT_STRUCTURE).isEmpty()) return Optional.empty();
        PhysicalDelta delta = observed.orElseThrow().delta();
        return Optional.of(new StructureDamaged(
                delta.semanticTarget().orElseThrow().subjectId(), delta.position(), delta.semanticPart().orElseThrow(), cause));
    }
    static ProjectionResult project(ServerLevel level, FrontierV3GrayboxLedger ledger, GrayboxCell cell) {
        return project(FrontierV3AftermathPhysicalWorld.minecraft(level), cell);
    }
    static ProjectionResult project(FrontierV3AftermathPhysicalWorld world, GrayboxCell cell) {
        io.farfrontier.palemirror.frontier.v3.model.BlockPosition position = cell.position();
        if (!world.naturallyLoaded(position)) return ProjectionResult.DEFERRED;
        FrontierV3GrayboxLedger ledger = world.ledger();
        FrontierV3GrayboxLedger.Claim prior = ledger.claim(toMinecraft(cell));
        if (prior != null) {
            if (prior.deferred() || !matches(prior, cell)) return ProjectionResult.DEFERRED;
            if (world.hasMaterial(position, cell.material())) return ProjectionResult.CURRENT;
            ledger.defer(toMinecraft(cell));
            return ProjectionResult.DEFERRED;
        }
        if (!world.isAir(position)) {
            ledger.obstructed(toMinecraft(cell), cell.ownerId().value(), cell.semanticTarget().kind().wireTag(), cell.material().name(), cell.semanticPart().name());
            return ProjectionResult.CONFLICT;
        }
        if (requiresSupport(cell.semanticPart()) && world.isAir(cell.position().offset(0, -1, 0))) return ProjectionResult.DEFERRED;
        ledger.ensureCapacityFor(toMinecraft(cell));
        if (!world.placeMaterial(position, cell.material())) return ProjectionResult.DEFERRED;
        ledger.applied(toMinecraft(cell), cell.ownerId().value(), cell.semanticTarget().kind().wireTag(), cell.material().name(), cell.semanticPart().name());
        return ProjectionResult.APPLIED;
    }
    static void retainKnownLoss(ServerLevel level, FrontierV3GrayboxLedger ledger, GrayboxCell cell) {
        retainKnownLoss(FrontierV3AftermathPhysicalWorld.minecraft(level), cell);
    }
    static void retainKnownLoss(FrontierV3AftermathPhysicalWorld world, GrayboxCell cell) {
        if (!world.naturallyLoaded(cell.position()) || !world.isAir(cell.position())) return;
        world.ledger().damaged(toMinecraft(cell), cell.ownerId().value(), cell.semanticTarget().kind().wireTag(), cell.material().name(), cell.semanticPart().name());
    }
    static boolean matchesKnownLoss(PhysicalDelta delta, GrayboxCell cell) {
        return delta.kind() == PhysicalDeltaKind.KNOWN_SEMANTIC_LOSS
                && delta.semanticTarget().equals(Optional.of(cell.semanticTarget()))
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
            case HIVE_COCOON -> Blocks.WHITE_CONCRETE.defaultBlockState();
            case HIVE_MORPHER -> Blocks.LIME_CONCRETE.defaultBlockState();
            case HIVE_SPORULATOR -> Blocks.ORANGE_CONCRETE.defaultBlockState();
            case HIVE_SENSOR -> Blocks.LIGHT_BLUE_CONCRETE.defaultBlockState();
            case ROUTE -> Blocks.GRAY_CONCRETE.defaultBlockState();
            case ROUTE_FOUNDATION -> Blocks.GRAY_CONCRETE.defaultBlockState();
            case WORKSITE -> Blocks.LIGHT_BLUE_CONCRETE.defaultBlockState();
            case INFECTION -> throw new IllegalArgumentException("infection requires the dynamic overlay executor");
        };
    }
    private static void retireStaleWorksiteStaging(ServerLevel level, FrontierV3GrayboxLedger ledger, Cursor cursor) {
        for (FrontierV3GrayboxLedger.ClaimAt staged : ledger.claimsWithSemanticPart(GrayboxSemanticPart.WORKSITE_STAGING.name())) {
            BlockPos position = staged.position();
            if (cursor.retainsWorksiteStaging(position) || !level.hasChunkAt(position)) continue;
            FrontierV3GrayboxLedger.Claim claim = staged.claim();
            GrayboxMaterial stagedMaterial;
            try {
                stagedMaterial = GrayboxMaterial.valueOf(claim.material());
            } catch (IllegalArgumentException malformed) {
                ledger.defer(position); continue;
            }
            if (claim.deferred() || stagedMaterial != GrayboxMaterial.WORKSITE
                    || !level.getBlockState(position).equals(material(stagedMaterial))) {
                ledger.defer(position); continue;
            }
            if (!level.setBlock(position, Blocks.AIR.defaultBlockState(), 3) || !level.getBlockState(position).isAir()) continue;
            ledger.retire(position, claim.owner(), claim.targetTag(), claim.material(), claim.semanticPart());
        }
    }
    private static boolean requiresSupport(GrayboxSemanticPart part) {
        return part == GrayboxSemanticPart.FOUNDATION || part == GrayboxSemanticPart.ROUTE_FOUNDATION || part == GrayboxSemanticPart.ROUTE_SURFACE
                || part == GrayboxSemanticPart.PUBLIC_ACCESS_SURFACE || part == GrayboxSemanticPart.WORKSITE_STAGING;
    }
    private static boolean matches(FrontierV3GrayboxLedger.Claim claim, GrayboxCell cell) {
        return claim.owner().equals(cell.ownerId().value()) && claim.targetTag() == cell.semanticTarget().kind().wireTag() && claim.material().equals(cell.material().name())
                && claim.semanticPart().equals(cell.semanticPart().name());
    }
    private static BlockPos toMinecraft(GrayboxCell cell) {
        return new BlockPos(cell.position().x(), cell.position().y(), cell.position().z());
    }
    private static BlockPos toMinecraft(io.farfrontier.palemirror.frontier.v3.model.BlockPosition position) {
        return new BlockPos(position.x(), position.y(), position.z());
    }
    static final class Cursor {
        private FrontierGrayboxPlan.StructuralInput input;
        private final FrontierGrayboxPlan structuralBaseline;
        private final FrontierGrayboxPlan dynamicHiveOverlay;
        private final Map<Long, Integer> baselineStructuralCeilings;
        private final Map<Long, Integer> structuralCeilings;
        private final FrontierV3HiveFoundryAudit.HiveExpectations baselineHiveExpectations;
        private final FrontierV3HiveFoundryAudit.HiveExpectations hiveExpectations;
        private final Map<ChunkPos, List<GrayboxCell>> hiveVisibilityCells;
        private final Map<ChunkPos, List<ChunkPos>> hiveVisibilityChunks;
        private final Map<ChunkPos, List<ChunkPos>> settlementVisibilityChunks;
        private final List<ChunkCells> chunks;
        private int nextChunkIndex;
        private final List<GrayboxCell> dynamicHiveCells;
        private final Map<ChunkKey, List<GrayboxCell>> dynamicHiveCellsByChunk;
        private int nextDynamicHiveIndex;
        private final List<GrayboxCell> worksiteStagingCells;
        private int nextWorksiteStagingIndex;
        private boolean preferWorksiteStaging;
        private Cursor(FrontierGrayboxPlan.StructuralInput input, FrontierGrayboxPlan structuralBaseline, FrontierGrayboxPlan dynamicHiveOverlay,
                       Map<Long, Integer> baselineStructuralCeilings, Map<Long, Integer> structuralCeilings,
                       FrontierV3HiveFoundryAudit.HiveExpectations baselineHiveExpectations,
                       FrontierV3HiveFoundryAudit.HiveExpectations hiveExpectations, Map<ChunkPos, List<GrayboxCell>> hiveVisibilityCells,
                       Map<ChunkPos, List<ChunkPos>> hiveVisibilityChunks, Map<ChunkPos, List<ChunkPos>> settlementVisibilityChunks,
                       List<ChunkCells> chunks, int nextChunkIndex, java.util.Set<BlockPos> activeWorksiteStaging,
                       List<GrayboxCell> dynamicHiveCells, int nextDynamicHiveIndex,
                       List<GrayboxCell> worksiteStagingCells, int nextWorksiteStagingIndex, boolean preferWorksiteStaging) {
            this.input = input;
            this.structuralBaseline = structuralBaseline;
            this.dynamicHiveOverlay = dynamicHiveOverlay;
            this.baselineStructuralCeilings = baselineStructuralCeilings;
            this.structuralCeilings = structuralCeilings;
            this.baselineHiveExpectations = baselineHiveExpectations;
            this.hiveExpectations = hiveExpectations;
            this.hiveVisibilityCells = hiveVisibilityCells;
            this.hiveVisibilityChunks = hiveVisibilityChunks;
            this.settlementVisibilityChunks = settlementVisibilityChunks;
            this.chunks = chunks;
            this.nextChunkIndex = nextChunkIndex;
            this.dynamicHiveCells = dynamicHiveCells;
            this.dynamicHiveCellsByChunk = dynamicHiveCells.stream().collect(java.util.stream.Collectors.groupingBy(ChunkKey::of,
                    LinkedHashMap::new, java.util.stream.Collectors.toUnmodifiableList()));
            this.nextDynamicHiveIndex = dynamicHiveIndex(dynamicHiveCells, nextDynamicHiveIndex);
            this.activeWorksiteStaging = activeWorksiteStaging;
            this.worksiteStagingCells = worksiteStagingCells;
            this.nextWorksiteStagingIndex = nextWorksiteStagingIndex;
            this.preferWorksiteStaging = preferWorksiteStaging;
        }
        private final java.util.Set<BlockPos> activeWorksiteStaging;
        static Cursor from(FrontierGrayboxPlan.StructuralInput input, FrontierGrayboxPlan plan, Cursor prior, FrontierWorldState state) {
            FrontierGrayboxPlan dynamic = FrontierGrayboxPlan.compileDynamicHiveOverlay(state);
            List<GrayboxCell> cells = plan.cells().values().stream().sorted(Comparator
                    .comparingInt((GrayboxCell cell) -> cell.position().y())
                    .thenComparingInt(cell -> cell.position().x()).thenComparingInt(cell -> cell.position().z())).toList();
            List<GrayboxCell> dynamicCells = dynamic.cells().values().stream().sorted(Comparator
                    .comparingInt((GrayboxCell cell) -> cell.position().y())
                    .thenComparingInt(cell -> cell.position().x()).thenComparingInt(cell -> cell.position().z())).toList();
            FrontierV3HiveFoundryAudit.HiveExpectations baselineExpectations = FrontierV3HiveFoundryAudit.expectations(state, plan);
            FrontierV3HiveFoundryAudit.HiveExpectations expectations = FrontierV3HiveFoundryAudit.withDynamicOverlay(state, baselineExpectations, dynamic);
            Map<Long, Integer> baselineCeilings = structuralCeilings(plan);
            return fromCells(input, plan, dynamic, baselineCeilings, new OverlayCeilings(baselineCeilings, structuralCeilings(dynamic)),
                    baselineExpectations, expectations, state, cells, prior,
                    stagingPositions(input), dynamicCells, prior == null ? 0 : prior.nextDynamicHiveIndex, stagingCells(input, plan, dynamic));
        }
        private static List<GrayboxCell> stagingCells(FrontierGrayboxPlan.StructuralInput input, FrontierGrayboxPlan baseline,
                                                      FrontierGrayboxPlan dynamicOverlay) {
            return input.activeWorksiteStaging().entrySet().stream().flatMap(entry -> entry.getValue().stream()
                    .filter(position -> !baseline.cells().containsKey(position) && !dynamicOverlay.cells().containsKey(position))
                    .map(position -> new GrayboxCell(position, new io.farfrontier.palemirror.frontier.v3.model.PhysicalDeltaSemanticTarget(
                            io.farfrontier.palemirror.frontier.v3.model.PhysicalDeltaSemanticTargetKind.ROUTE_CONSTRUCTION, entry.getKey()), GrayboxMaterial.WORKSITE, GrayboxSemanticPart.WORKSITE_STAGING)))
                    .sorted(Comparator
                    .comparingInt((GrayboxCell cell) -> cell.position().y())
                    .thenComparingInt(cell -> cell.position().x()).thenComparingInt(cell -> cell.position().z())).toList();
        }
        private static java.util.Set<BlockPos> stagingPositions(FrontierGrayboxPlan.StructuralInput input) {
            return input.activeWorksiteStaging().values().stream().flatMap(java.util.Collection::stream)
                    .map(FrontierV3GrayboxExecutor::toMinecraft).collect(java.util.stream.Collectors.toUnmodifiableSet());
        }
        static Cursor fromCells(FrontierGrayboxPlan.StructuralInput input, List<GrayboxCell> cells, Cursor prior) {
            return fromCells(input, null, null, Map.of(), Map.of(), null, null, null, cells, prior, java.util.Set.of(), List.of(), 0, List.of());
        }
        private static Cursor fromCells(FrontierGrayboxPlan.StructuralInput input, FrontierGrayboxPlan structuralBaseline, FrontierGrayboxPlan dynamicHiveOverlay,
                                        Map<Long, Integer> baselineStructuralCeilings, Map<Long, Integer> structuralCeilings,
                                        FrontierV3HiveFoundryAudit.HiveExpectations baselineHiveExpectations,
                                        FrontierV3HiveFoundryAudit.HiveExpectations hiveExpectations, FrontierWorldState state,
                                        List<GrayboxCell> cells, Cursor prior,
                                        java.util.Set<BlockPos> activeWorksiteStaging, List<GrayboxCell> dynamicHiveCells,
                                        int nextDynamicHiveIndex, List<GrayboxCell> worksiteStagingCells) {
            Map<ChunkKey, List<GrayboxCell>> grouped = new LinkedHashMap<>();
            cells.forEach(cell -> grouped.computeIfAbsent(ChunkKey.of(cell), ignored -> new ArrayList<>()).add(cell));
            List<ChunkCells> chunks = grouped.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(entry -> {
                ChunkCells before = prior == null ? null : prior.chunk(entry.getKey());
                return new ChunkCells(entry.getKey(), List.copyOf(entry.getValue()), before);
            }).toList();
            int next = prior == null || chunks.isEmpty() ? 0 : indexOf(chunks, prior.nextChunkKey());
            Map<ChunkPos, List<GrayboxCell>> hiveCells = hiveVisibilityFence(hiveExpectations);
            return new Cursor(input, structuralBaseline, dynamicHiveOverlay, baselineStructuralCeilings, structuralCeilings, baselineHiveExpectations, hiveExpectations,
                    hiveCells, hiveVisibilityChunks(hiveCells),
                    FrontierV3SettlementVisibilityIndex.compile(state, structuralBaseline), chunks, next, activeWorksiteStaging,
                    dynamicHiveCells, nextDynamicHiveIndex, worksiteStagingCells, worksiteIndex(worksiteStagingCells, prior == null ? 0 : prior.nextWorksiteStagingIndex),
                    prior == null || prior.preferWorksiteStaging);
        }
        static Cursor fromCells(List<GrayboxCell> cells, Cursor prior) {
            return fromCells(null, cells, prior);
        }
        FrontierGrayboxPlan.StructuralInput input() { return input; }
        void replaceInput(FrontierGrayboxPlan.StructuralInput replacement) { input = Objects.requireNonNull(replacement, "structural input"); }
        Cursor withWorksiteStaging(FrontierGrayboxPlan.StructuralInput replacement, FrontierWorldState state) {
            if (structuralBaseline == null) throw new IllegalStateException("test-only cursor has no structural provider baseline");
            List<GrayboxCell> staging = stagingCells(replacement, structuralBaseline, dynamicHiveOverlay);
            return new Cursor(replacement, structuralBaseline, dynamicHiveOverlay, baselineStructuralCeilings, structuralCeilings, baselineHiveExpectations, hiveExpectations, hiveVisibilityCells,
                    hiveVisibilityChunks, settlementVisibilityChunks, chunks, nextChunkIndex, stagingPositions(replacement),
                    dynamicHiveCells, nextDynamicHiveIndex, staging, worksiteIndex(staging, nextWorksiteStagingIndex), preferWorksiteStaging);
        }
        Cursor withDynamicHiveOverlay(FrontierGrayboxPlan.StructuralInput replacement, FrontierWorldState state) {
            if (structuralBaseline == null) throw new IllegalStateException("test-only cursor has no structural provider baseline");
            FrontierGrayboxPlan dynamic = FrontierGrayboxPlan.compileDynamicHiveOverlay(state);
            List<GrayboxCell> dynamicCells = dynamic.cells().values().stream().sorted(Comparator
                    .comparingInt((GrayboxCell cell) -> cell.position().y())
                    .thenComparingInt(cell -> cell.position().x()).thenComparingInt(cell -> cell.position().z())).toList();
            FrontierV3HiveFoundryAudit.HiveExpectations expectations = FrontierV3HiveFoundryAudit.withDynamicOverlay(state,
                    baselineHiveExpectations, dynamic);
            Map<ChunkPos, List<GrayboxCell>> hiveCells = hiveVisibilityFence(expectations);
            return new Cursor(replacement, structuralBaseline, dynamic,
                    baselineStructuralCeilings, new OverlayCeilings(baselineStructuralCeilings, structuralCeilings(dynamic)), baselineHiveExpectations, expectations, hiveCells,
                    hiveVisibilityChunks(hiveCells), settlementVisibilityChunks, chunks, nextChunkIndex, stagingPositions(replacement),
                    dynamicCells, nextDynamicHiveIndex, stagingCells(replacement, structuralBaseline, dynamic), nextWorksiteStagingIndex, preferWorksiteStaging);
        }
        FrontierSettlementAssaultBattlefield.Provider providerSnapshot(FrontierWorldState state, ProjectionWork work) {
            if (structuralBaseline == null) throw new IllegalStateException("test-only cursor has no structural provider baseline");
            return new ProjectionProviderSnapshot(structuralBaseline, dynamicHiveOverlay, state.physicalDeltas(), work);
        }
        boolean retainsWorksiteStaging(BlockPos position) { return activeWorksiteStaging.contains(position); }
        List<GrayboxCell> cellsIn(ChunkPos chunk) {
            ChunkCells selected = chunk(new ChunkKey(chunk.x, chunk.z));
            List<GrayboxCell> dynamic = dynamicHiveCellsByChunk.getOrDefault(new ChunkKey(chunk.x, chunk.z), List.of());
            if (selected == null) return dynamic;
            if (dynamic.isEmpty()) return selected.cells();
            List<GrayboxCell> combined = new ArrayList<>(selected.cells()); combined.addAll(dynamic);
            return List.copyOf(combined);
        }
        List<GrayboxCell> hiveVisibilityCells(ChunkPos ingress) { return hiveVisibilityCells.getOrDefault(ingress, List.of()); }
        List<ChunkPos> hiveVisibilityChunks(ChunkPos ingress) { return hiveVisibilityChunks.getOrDefault(ingress, List.of()); }
        List<ChunkPos> settlementVisibilityChunks(ChunkPos ingress) { return settlementVisibilityChunks.getOrDefault(ingress, List.of()); }
        Optional<GrayboxCell> nextNaturallyLoaded(Predicate<GrayboxCell> loaded) {
            if (preferWorksiteStaging) {
                Optional<GrayboxCell> staging = nextNaturallyLoadedWorksiteStaging(loaded);
                if (staging.isPresent()) {
                    preferWorksiteStaging = false;
                    return staging;
                }
            }
            Optional<GrayboxCell> dynamic = nextNaturallyLoadedDynamicHive(loaded);
            if (dynamic.isPresent()) return dynamic;
            if (chunks.isEmpty()) return nextNaturallyLoadedWorksiteStaging(loaded);
            for (int attempts = 0; attempts < Math.min(chunks.size(), MAX_NATURAL_CHUNK_PROBES_PER_CELL); attempts++) {
                ChunkCells chunk = chunks.get(nextChunkIndex);
                nextChunkIndex = (nextChunkIndex + 1) % chunks.size();
                if (loaded.test(chunk.sample())) {
                    preferWorksiteStaging = true;
                    return Optional.of(chunk.next());
                }
            }
            return nextNaturallyLoadedWorksiteStaging(loaded);
        }
        private Optional<GrayboxCell> nextNaturallyLoadedWorksiteStaging(Predicate<GrayboxCell> loaded) {
            for (int attempts = 0; attempts < Math.min(worksiteStagingCells.size(), MAX_WORKSITE_STAGING_PROBES_PER_CELL); attempts++) {
                GrayboxCell cell = worksiteStagingCells.get(nextWorksiteStagingIndex);
                nextWorksiteStagingIndex = (nextWorksiteStagingIndex + 1) % worksiteStagingCells.size();
                if (loaded.test(cell)) return Optional.of(cell);
            }
            return Optional.empty();
        }
        private Optional<GrayboxCell> nextNaturallyLoadedDynamicHive(Predicate<GrayboxCell> loaded) {
            for (int attempts = 0; attempts < Math.min(dynamicHiveCells.size(), MAX_WORKSITE_STAGING_PROBES_PER_CELL); attempts++) {
                GrayboxCell cell = dynamicHiveCells.get(nextDynamicHiveIndex);
                nextDynamicHiveIndex = (nextDynamicHiveIndex + 1) % dynamicHiveCells.size();
                if (loaded.test(cell)) return Optional.of(cell);
            }
            return Optional.empty();
        }
        private static int worksiteIndex(List<GrayboxCell> cells, int index) {
            return cells.isEmpty() ? 0 : Math.floorMod(index, cells.size());
        }
        private static int dynamicHiveIndex(List<GrayboxCell> cells, int index) {
            return cells.isEmpty() ? 0 : Math.floorMod(index, cells.size());
        }
        private ChunkCells chunk(ChunkKey key) {
            return chunks.stream().filter(chunk -> chunk.key().equals(key)).findFirst().orElse(null);
        }
        Optional<GrayboxCell> structuralCellAt(BlockPosition position) {
            return structuralBaseline == null ? Optional.empty() : Optional.ofNullable(structuralBaseline.cells().get(position));
        }
        static Map<ChunkPos, List<GrayboxCell>> hiveVisibilityFence(FrontierV3HiveFoundryAudit.HiveExpectations expectations) {
            if (expectations == null) return Map.of();
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
    private record ProjectionProviderSnapshot(FrontierGrayboxPlan structuralBaseline, FrontierGrayboxPlan dynamicHiveOverlay,
                                              Map<io.farfrontier.palemirror.frontier.v3.model.BlockPosition, PhysicalDelta> physicalDeltas,
                                              ProjectionWork work)
            implements FrontierSettlementAssaultBattlefield.Provider {
        @Override public Optional<GrayboxCell> cellAt(io.farfrontier.palemirror.frontier.v3.model.BlockPosition position) {
            work.pointQueries++;
            if (physicalDeltas.containsKey(position)) return Optional.empty();
            GrayboxCell dynamic = dynamicHiveOverlay.cells().get(position);
            return Optional.ofNullable(dynamic == null ? structuralBaseline.cells().get(position) : dynamic);
        }
    }
    private static final class OverlayCeilings extends java.util.AbstractMap<Long, Integer> {
        private final Map<Long, Integer> baseline;
        private final Map<Long, Integer> dynamic;
        private OverlayCeilings(Map<Long, Integer> baseline, Map<Long, Integer> dynamic) {
            this.baseline = Objects.requireNonNull(baseline, "baseline ceilings");
            this.dynamic = Objects.requireNonNull(dynamic, "dynamic ceilings");
        }
        @Override public Integer get(Object key) {
            Integer overlay = dynamic.get(key);
            Integer base = baseline.get(key);
            if (overlay == null) return base;
            return base == null ? overlay : Math.max(base, overlay);
        }
        @Override public boolean containsKey(Object key) { return dynamic.containsKey(key) || baseline.containsKey(key); }
        @Override public java.util.Set<Entry<Long, Integer>> entrySet() {
            java.util.Map<Long, Integer> combined = new java.util.LinkedHashMap<>(baseline);
            dynamic.forEach((key, ceiling) -> combined.merge(key, ceiling, Math::max));
            return java.util.Collections.unmodifiableMap(combined).entrySet();
        }
    }
    static ProjectionWorkSnapshot projectionWork(FrontierV3ServerRuntime<?, ?> runtime) { return workFor(runtime).snapshot(); }
    static void resetProjectionWork(FrontierV3ServerRuntime<?, ?> runtime) { WORK.put(runtime, new ProjectionWork()); }
    private static ProjectionWork workFor(FrontierV3ServerRuntime<?, ?> runtime) { return WORK.computeIfAbsent(runtime, ignored -> new ProjectionWork()); }
    static final class ProjectionWork {
        private int compatibilityChecks, freshnessConstructions, planCompilations, providerAcquisitions, pointQueries, siblingHiveFenceRetentions;
        private String lastTrigger = "UNINITIALIZED", lastDisposition = "NONE";
        private String lastCompileTrigger = "NONE";
        private ProjectionCost cursorPath = ProjectionCost.empty(), firstVisibilityPath = ProjectionCost.empty();
        private ProjectionCost stagingRetirementPath = ProjectionCost.empty(), deferredProjectionPath = ProjectionCost.empty();
        void lastRefresh(String trigger, String disposition) {
            lastTrigger = trigger; lastDisposition = disposition;
            if ("COMPILE_STABLE_BASELINE".equals(disposition)) lastCompileTrigger = trigger;
        }
        void cursorPath(long elapsedNanos) { cursorPath = cursorPath.observe(elapsedNanos); }
        void firstVisibilityPath(long elapsedNanos) { firstVisibilityPath = firstVisibilityPath.observe(elapsedNanos); }
        void stagingRetirementPath(long elapsedNanos) { stagingRetirementPath = stagingRetirementPath.observe(elapsedNanos); }
        void deferredProjectionPath(long elapsedNanos) { deferredProjectionPath = deferredProjectionPath.observe(elapsedNanos); }
        ProjectionWorkSnapshot snapshot() {
            return new ProjectionWorkSnapshot(compatibilityChecks, freshnessConstructions, planCompilations,
                    providerAcquisitions, pointQueries, siblingHiveFenceRetentions, lastTrigger, lastDisposition,
                    lastCompileTrigger, cursorPath, firstVisibilityPath, stagingRetirementPath, deferredProjectionPath);
        }
    }
    record ProjectionCost(long calls, long totalNanos, long maxNanos) {
        static ProjectionCost empty() { return new ProjectionCost(0, 0, 0); }
        ProjectionCost observe(long elapsedNanos) {
            long normalized = Math.max(0, elapsedNanos);
            return new ProjectionCost(Math.addExact(calls, 1), Math.addExact(totalNanos, normalized), Math.max(maxNanos, normalized));
        }
    }
    record ProjectionWorkSnapshot(int compatibilityChecks, int freshnessConstructions, int planCompilations, int providerAcquisitions,
                                  int pointQueries, int siblingHiveFenceRetentions, String lastTrigger, String lastDisposition,
                                  String lastCompileTrigger, ProjectionCost cursorPath, ProjectionCost firstVisibilityPath,
                                  ProjectionCost stagingRetirementPath, ProjectionCost deferredProjectionPath) { }
}
