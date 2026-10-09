package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.CauseChain;
import io.farfrontier.palemirror.frontier.v3.api.CheckpointImage;
import io.farfrontier.palemirror.frontier.v3.api.CommandId;
import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.FixedRatio;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierInfectionOverlayPlan;
import io.farfrontier.palemirror.frontier.v3.model.FrontierGrayboxPlan;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.model.InfectionCell;
import io.farfrontier.palemirror.frontier.v3.model.InfectionOverlayCell;
import io.farfrontier.palemirror.frontier.v3.model.InfectionOverlayStage;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalDelta;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalDeltaKind;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalDeltaObserved;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Bounded loaded-chunk projection of sparse canonical infection into owned surface markers. */
final class FrontierV3InfectionOverlayExecutor {
    private static final int MAX_CELLS_PER_TICK = 64;
    private static final Map<FrontierV3ServerRuntime<?, ?>, Cursor> CURSORS = new IdentityHashMap<>();
    private static final Map<FrontierV3ServerRuntime<?, ?>, ProjectionWork> WORK = new IdentityHashMap<>();

    enum BlockBreakObservation { UNMANAGED, ACCEPTED, REJECTED }
    enum ProjectionResult { APPLIED, CURRENT, UPDATED, RETRACTED, CONFLICT, DEFERRED }

    private FrontierV3InfectionOverlayExecutor() { }

    static void tick(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        if (runtime.canonicalState().isEmpty()) return;
        FrontierWorldState state = runtime.decodedState().orElse(null);
        if (state == null) return;
        // The structural owner has already published this immutable plan earlier in the same
        // physical turn.  Dynamic surface discovery must respect every declared tissue cell,
        // not only the lower cell that happened to be materialized before the overlay turn.
        FrontierGrayboxPlan structuralBaseline = FrontierV3GrayboxExecutor.publishedStructuralBaseline(runtime).orElse(null);
        if (structuralBaseline == null) return;
        Map<Long, Integer> structuralCeilings = FrontierV3GrayboxExecutor.publishedStructuralCeilings(runtime).orElse(Map.of());
        Cursor cursor = cursor(runtime, state, FrontierV3InfectionOverlayLedger.get(level));
        FrontierV3InfectionOverlayLedger ledger = FrontierV3InfectionOverlayLedger.get(level);
        int budget = MAX_CELLS_PER_TICK;
        while (budget-- > 0 && cursor.hasRetraction()) reconcileRetraction(level, ledger, cursor.nextRetraction(), state);
        while (budget-- > 0 && cursor.hasDesired()) project(level, runtime, ledger, cursor.nextDesired(), state, structuralBaseline, structuralCeilings);
    }

    static void forget(FrontierV3ServerRuntime<?, ?> runtime) { CURSORS.remove(runtime); WORK.remove(runtime); }

    /**
     * Keeps the dynamic plan on its own canonical contributor boundary.  A resource receipt,
     * scene lease, or physical observation advances the checkpoint revision very frequently,
     * but none changes the sparse infection field.  Resetting a global round-robin cursor on
     * every such revision both recompiles the complete overlay and starves later cells forever.
     */
    private static Cursor cursor(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state,
                                 FrontierV3InfectionOverlayLedger ledger) {
        Cursor cursor = CURSORS.get(runtime);
        ProjectionWork work = workFor(runtime);
        if (cursor != null) work.compatibilityChecks++;
        if (cursor == null || !cursor.input().matches(state)) {
            work.freshnessConstructions++;
            OverlayInput input = new OverlayInput(state.bootstrap(), state.infection());
            if (cursor == null || !input.equals(cursor.input())) {
                work.planCompilations++;
                cursor = Cursor.from(input, FrontierInfectionOverlayPlan.compile(state), ledger);
                CURSORS.put(runtime, cursor);
            } else {
                cursor.replaceInput(input);
            }
        }
        return cursor;
    }

    /**
     * Returns the exact dynamic overlay already compiled by the overlay owner for this runtime.
     * Read-only presentation gates may inspect this snapshot, but must not compile a second
     * whole-world overlay from their per-board or per-cocoon hot path.
     */
    static Optional<Map<InfectionCell, InfectionOverlayCell>> publishedOverlay(FrontierV3ServerRuntime<?, ?> runtime) {
        Cursor cursor = CURSORS.get(runtime);
        return cursor == null ? Optional.empty() : Optional.of(cursor.desiredByCell);
    }

    static BlockBreakObservation observeBlockBreak(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, ServerLevel level,
                                                   BlockPos position, String cause) {
        FrontierV3InfectionOverlayLedger ledger = FrontierV3InfectionOverlayLedger.get(level);
        InfectionCell cell = ledger.cellAt(position).orElse(null);
        if (cell == null) return BlockBreakObservation.UNMANAGED;
        FrontierV3InfectionOverlayLedger.Claim claim = ledger.claim(cell);
        if (claim == null || !claim.active() || !level.getBlockState(position).equals(material(claim.stage()))) {
            if (claim != null) ledger.defer(cell);
            return BlockBreakObservation.UNMANAGED;
        }
        try {
            io.farfrontier.palemirror.frontier.v3.api.FrontierCanonicalState<?> checkpoint = runtime.canonicalState().orElseThrow(() -> new IllegalStateException("v3 runtime is inactive"));
            CommandId id = new CommandId("executor:infection-overlay-break-r" + checkpoint.revision().value() + "-p" + position.asLong());
            PhysicalDeltaObserved observed = new PhysicalDeltaObserved(new PhysicalDelta(canonical(position), PhysicalDeltaKind.UNKNOWN_SCAR,
                    Optional.empty(), Optional.empty(), cause));
            CommandResult result = runtime.submit(new FrontierCommand(1, id, checkpoint.worldId(), checkpoint.revision(), checkpoint.instant(),
                    FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(id), observed))
                    .orElseThrow(() -> new IllegalStateException("v3 runtime is inactive"));
            if (!(result instanceof CommandResult.Accepted)) return BlockBreakObservation.REJECTED;
            ledger.defer(cell); // mirror preserves the physical block; canonical reduction owns any terminal incident
            return BlockBreakObservation.ACCEPTED;
        } catch (RuntimeException failed) {
            return BlockBreakObservation.REJECTED;
        }
    }

    static ProjectionResult project(ServerLevel level, FrontierV3InfectionOverlayLedger ledger, InfectionOverlayCell desired,
                                    FrontierWorldState state) {
        return project(level, null, ledger, desired, state, null, Map.of());
    }

    private static ProjectionResult project(ServerLevel level, FrontierV3ServerRuntime<?, ?> runtime, FrontierV3InfectionOverlayLedger ledger, InfectionOverlayCell desired,
                                            FrontierWorldState state, FrontierGrayboxPlan structuralBaseline,
                                            Map<Long, Integer> structuralCeilings) {
        // A player ingress can retain the static organ shell and this dynamic 4x4 surface in
        // one physical turn.  Do not claim a surface while that exact retained shell is still
        // being written: a later normal static write would turn our own partial arrival into a
        // permanent overlay conflict.  This reads at most the four chunks touched by one
        // declared patch, creates no demand, and leaves unrelated ordinary chunks untouched.
        if (!staticVisibilityComplete(runtime, desired)) return ProjectionResult.DEFERRED;
        FrontierV3InfectionOverlayLedger.Claim claim = ledger.claim(desired.cell());
        if (claim != null) return projectClaim(level, ledger, desired, state, claim);
        List<BlockPos> positions = discoveredPatch(level, desired, structuralBaseline, structuralCeilings);
        if (positions == null) return ProjectionResult.DEFERRED;
        if (positions.stream().anyMatch(position -> state.physicalDeltas().containsKey(canonical(position)) || !level.getBlockState(position).isAir())) {
            ledger.blocked(desired.cell(), positions, desired.stage());
            return ProjectionResult.CONFLICT;
        }
        ledger.ensureCapacityFor(desired.cell());
        ledger.prepare(desired.cell(), positions, desired.stage());
        ledger.persist(level);
        BlockState expected = material(desired.stage());
        if (!replace(level, positions, expected)) return ProjectionResult.DEFERRED;
        ledger.activate(desired.cell()); ledger.persist(level);
        return ProjectionResult.APPLIED;
    }

    private static boolean staticVisibilityComplete(FrontierV3ServerRuntime<?, ?> runtime, InfectionOverlayCell desired) {
        if (runtime == null) return true; // fixture projection has no player-ingress fence
        return desired.surfaceColumns().stream().map(column -> new net.minecraft.world.level.ChunkPos(column.x() >> 4, column.z() >> 4))
                .allMatch(chunk -> FrontierV3GrayboxExecutor.staticVisibilityComplete(runtime, chunk));
    }

    private static ProjectionResult projectClaim(ServerLevel level, FrontierV3InfectionOverlayLedger ledger, InfectionOverlayCell desired,
                                                 FrontierWorldState state, FrontierV3InfectionOverlayLedger.Claim claim) {
        if (claim.deferred()) return ProjectionResult.CONFLICT;
        List<BlockPos> positions = claim.blockPositions();
        if (positions.stream().anyMatch(position -> !level.hasChunkAt(position))) return ProjectionResult.DEFERRED;
        if (claim.prepared()) return reconcilePrepared(level, ledger, desired, state, claim, positions);
        if (claim.cleared()) {
            if (positions.stream().anyMatch(position -> !level.getBlockState(position).isAir())) { ledger.defer(desired.cell()); return ProjectionResult.CONFLICT; }
            ledger.prepareReplacement(desired.cell(), desired.stage()); ledger.persist(level);
            return reconcilePrepared(level,ledger,desired,state,ledger.claim(desired.cell()),positions);
        }
        if (positions.stream().anyMatch(position -> state.physicalDeltas().containsKey(canonical(position))
                || !level.getBlockState(position).equals(material(claim.stage())))) {
            ledger.defer(desired.cell()); return ProjectionResult.CONFLICT;
        }
        if (claim.stage() == desired.stage()) return ProjectionResult.CURRENT;
        ledger.prepareReplacement(desired.cell(), desired.stage()); ledger.persist(level);
        return reconcilePrepared(level,ledger,desired,state,ledger.claim(desired.cell()),positions);
    }

    /**
     * A prepared patch is the only recovery authority for a first physical write.  It may finish
     * only when the world still proves that no column was written; a full exact postcondition is
     * accepted, but every mixed or foreign result becomes a conflict rather than a repair job.
     */
    private static ProjectionResult reconcilePrepared(ServerLevel level, FrontierV3InfectionOverlayLedger ledger,
                                                      InfectionOverlayCell desired, FrontierWorldState state,
                                                      FrontierV3InfectionOverlayLedger.Claim claim,
                                                      List<BlockPos> positions) {
        if (positions.stream().anyMatch(position -> state.physicalDeltas().containsKey(canonical(position)))) {
            ledger.defer(desired.cell()); return ProjectionResult.CONFLICT;
        }
        BlockState preparedMaterial = material(claim.stage());
        if (positions.stream().allMatch(position -> level.getBlockState(position).equals(preparedMaterial))) {
            ledger.activate(desired.cell()); ledger.persist(level); return ProjectionResult.APPLIED;
        }
        BlockState before = claim.predecessor().map(FrontierV3InfectionOverlayExecutor::material).orElse(Blocks.AIR.defaultBlockState());
        if (positions.stream().allMatch(position -> level.getBlockState(position).equals(before))) {
            ledger.retargetPrepared(desired.cell(), desired.stage());
            ledger.persist(level);
            BlockState expected = material(desired.stage());
            if (!replace(level, positions, expected)) return ProjectionResult.DEFERRED;
            ledger.activate(desired.cell()); ledger.persist(level); return ProjectionResult.APPLIED;
        }
        ledger.defer(desired.cell()); return ProjectionResult.CONFLICT;
    }

    static ProjectionResult reconcileRetraction(ServerLevel level, FrontierV3InfectionOverlayLedger ledger,
                                                Map.Entry<InfectionCell, FrontierV3InfectionOverlayLedger.Claim> entry,
                                                FrontierWorldState state) {
        InfectionCell cell = entry.getKey(); FrontierV3InfectionOverlayLedger.Claim claim = entry.getValue();
        if (claim.deferred()) return ProjectionResult.CONFLICT;
        List<BlockPos> positions = claim.blockPositions();
        if (positions.stream().anyMatch(position -> !level.hasChunkAt(position))) return ProjectionResult.DEFERRED;
        if (claim.cleared()) {
            if (positions.stream().anyMatch(position -> !level.getBlockState(position).isAir())) { ledger.defer(cell); return ProjectionResult.CONFLICT; }
            ledger.forgetRetracted(cell); return ProjectionResult.RETRACTED;
        }
        if (positions.stream().anyMatch(position -> state.physicalDeltas().containsKey(canonical(position))
                || !level.getBlockState(position).equals(material(claim.stage())))) {
            ledger.defer(cell); return ProjectionResult.CONFLICT;
        }
        if (!replace(level, positions, Blocks.AIR.defaultBlockState())) return ProjectionResult.DEFERRED;
        ledger.forgetRetracted(cell); return ProjectionResult.RETRACTED;
    }

    static BlockState material(InfectionOverlayStage stage) {
        return switch (stage) {
            case TRACE -> Blocks.LIME_CARPET.defaultBlockState();
            case INFESTED -> Blocks.PURPLE_CARPET.defaultBlockState();
            case BLOOM -> Blocks.MAGENTA_CARPET.defaultBlockState();
            case SATURATED -> Blocks.RED_CARPET.defaultBlockState();
        };
    }

    private static List<BlockPos> discoveredPatch(ServerLevel level, InfectionOverlayCell desired, FrontierGrayboxPlan structuralBaseline,
                                                  Map<Long, Integer> structuralCeilings) {
        List<BlockPos> positions = new java.util.ArrayList<>(FrontierV3InfectionOverlayLedger.PATCH_COLUMNS);
        FrontierV3GrayboxLedger structural = FrontierV3GrayboxLedger.get(level);
        for (InfectionOverlayCell.SurfaceColumn column : desired.surfaceColumns()) {
            BlockPos columnProbe = new BlockPos(column.x(), 0, column.z());
            if (!level.hasChunkAt(columnProbe)) return null;
            int terrainSurface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.x(), column.z());
            int projectedSurface = surfaceY(terrainSurface, structuralCeilings.get(FrontierV3GrayboxExecutor.columnKey(column.x(), column.z())));
            BlockPos position = new BlockPos(column.x(), projectedSurface, column.z());
            if (!level.hasChunkAt(position)) return null;
            // The heightmap can legitimately select the top of an already-current PM organ.
            // Infection is an owned surface layer in that case, so place its marker immediately
            // above the proved structural cell.  An unclaimed/foreign non-air baseline remains a
            // conflict below; this is not a repair or a way to overwrite it.
            while (isStructuralCell(structural, structuralBaseline, position)) {
                position = position.above();
                if (!level.hasChunkAt(position)) return null;
            }
            // A prior interrupted v3 write can itself become the motion-blocking surface.  Keep
            // that exact carpet in the failed baseline so a later retry conflicts rather than
            // silently placing another carpet one block above it.
            if (isOverlayMaterial(level.getBlockState(position.below()))) position = position.below();
            positions.add(position);
        }
        return List.copyOf(positions);
    }

    private static boolean isStructuralCell(FrontierV3GrayboxLedger structural, FrontierGrayboxPlan structuralBaseline, BlockPos position) {
        FrontierV3GrayboxLedger.Claim claim = structural.claim(position);
        return claim != null && !claim.deferred()
                || structuralBaseline != null && structuralBaseline.cells().containsKey(canonical(position));
    }

    /** Chooses air directly above a retained structural column without scanning the whole plan at call time. */
    static int surfaceY(int terrainSurface, Integer structuralCeiling) {
        return structuralCeiling == null ? terrainSurface : Math.max(terrainSurface, Math.addExact(structuralCeiling, 1));
    }

    private static boolean replace(ServerLevel level, List<BlockPos> positions, BlockState expected) {
        for (BlockPos position : positions) if (!level.setBlock(position, expected, 3)) return false;
        return positions.stream().allMatch(position -> level.getBlockState(position).equals(expected));
    }

    private static boolean isOverlayMaterial(BlockState state) {
        return state.equals(material(InfectionOverlayStage.TRACE)) || state.equals(material(InfectionOverlayStage.INFESTED))
                || state.equals(material(InfectionOverlayStage.BLOOM)) || state.equals(material(InfectionOverlayStage.SATURATED));
    }

    private static BlockPosition canonical(BlockPos position) { return new BlockPosition(position.getX(), position.getY(), position.getZ()); }

    private static final class Cursor {
        private OverlayInput input;
        private final List<InfectionOverlayCell> desired;
        private final Map<InfectionCell, InfectionOverlayCell> desiredByCell;
        private final List<Map.Entry<InfectionCell, FrontierV3InfectionOverlayLedger.Claim>> retractions;
        private int desiredIndex;
        private int retractionIndex;

        private Cursor(OverlayInput input, List<InfectionOverlayCell> desired,
                       List<Map.Entry<InfectionCell, FrontierV3InfectionOverlayLedger.Claim>> retractions) {
            this.input = input; this.desired = desired;
            this.desiredByCell = desired.stream().collect(java.util.stream.Collectors.toUnmodifiableMap(InfectionOverlayCell::cell, value -> value));
            this.retractions = retractions;
        }
        static Cursor from(OverlayInput input, FrontierInfectionOverlayPlan plan, FrontierV3InfectionOverlayLedger ledger) {
            List<InfectionOverlayCell> desired = plan.cells().values().stream().sorted(Comparator.comparingInt((InfectionOverlayCell cell) -> cell.cell().x())
                    .thenComparingInt(cell -> cell.cell().z())).toList();
            Set<InfectionCell> active = plan.cells().keySet();
            List<Map.Entry<InfectionCell, FrontierV3InfectionOverlayLedger.Claim>> retractions = ledger.orderedClaims().stream()
                    .filter(entry -> !active.contains(entry.getKey())).toList();
            return new Cursor(input, desired, retractions);
        }
        OverlayInput input() { return input; }
        void replaceInput(OverlayInput input) { this.input = input; }
        boolean hasDesired() { return !desired.isEmpty(); }
        boolean hasRetraction() { return retractionIndex < retractions.size(); }
        InfectionOverlayCell nextDesired() {
            InfectionOverlayCell next = desired.get(desiredIndex); desiredIndex = (desiredIndex + 1) % desired.size(); return next;
        }
        Map.Entry<InfectionCell, FrontierV3InfectionOverlayLedger.Claim> nextRetraction() { return retractions.get(retractionIndex++); }
    }

    /** Fixed-cost freshness test; value equality is permitted only after the infection index changed identity. */
    private static final class OverlayInput {
        private final io.farfrontier.palemirror.frontier.v3.model.FrontierBootstrap bootstrap;
        private final Map<InfectionCell, FixedRatio> infection;
        private OverlayInput(io.farfrontier.palemirror.frontier.v3.model.FrontierBootstrap bootstrap, Map<InfectionCell, FixedRatio> infection) {
            this.bootstrap = bootstrap; this.infection = infection;
        }
        boolean matches(FrontierWorldState state) { return bootstrap == state.bootstrap() && infection == state.infection(); }
        @Override public boolean equals(Object other) {
            return this == other || other instanceof OverlayInput input && bootstrap.equals(input.bootstrap) && infection.equals(input.infection);
        }
        @Override public int hashCode() { return java.util.Objects.hash(bootstrap, infection); }
    }

    static ProjectionWorkSnapshot projectionWork(FrontierV3ServerRuntime<?, ?> runtime) { return workFor(runtime).snapshot(); }
    static void resetProjectionWork(FrontierV3ServerRuntime<?, ?> runtime) { WORK.put(runtime, new ProjectionWork()); }
    private static ProjectionWork workFor(FrontierV3ServerRuntime<?, ?> runtime) { return WORK.computeIfAbsent(runtime, ignored -> new ProjectionWork()); }
    static final class ProjectionWork {
        private int compatibilityChecks, freshnessConstructions, planCompilations;
        ProjectionWorkSnapshot snapshot() { return new ProjectionWorkSnapshot(compatibilityChecks, freshnessConstructions, planCompilations); }
    }
    record ProjectionWorkSnapshot(int compatibilityChecks, int freshnessConstructions, int planCompilations) { }
}
