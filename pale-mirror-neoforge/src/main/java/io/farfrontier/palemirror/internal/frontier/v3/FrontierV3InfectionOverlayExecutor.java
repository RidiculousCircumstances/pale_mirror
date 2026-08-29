package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.CauseChain;
import io.farfrontier.palemirror.frontier.v3.api.CheckpointImage;
import io.farfrontier.palemirror.frontier.v3.api.CommandId;
import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.Revision;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierInfectionOverlayPlan;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldRuntimeDefinition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldStateCodec;
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

    enum BlockBreakObservation { UNMANAGED, ACCEPTED, REJECTED }
    enum ProjectionResult { APPLIED, CURRENT, UPDATED, RETRACTED, CONFLICT, DEFERRED }

    private FrontierV3InfectionOverlayExecutor() { }

    static void tick(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        CheckpointImage checkpoint = runtime.checkpointImage().orElse(null);
        if (checkpoint == null) return;
        FrontierWorldState state = runtime.decodedState().orElse(null);
        if (state == null) return;
        Cursor cursor = CURSORS.get(runtime);
        if (cursor == null || !cursor.revision().equals(checkpoint.revision())) {
            FrontierInfectionOverlayPlan plan = FrontierInfectionOverlayPlan.compile(state);
            cursor = Cursor.from(checkpoint.revision(), plan, FrontierV3InfectionOverlayLedger.get(level));
            CURSORS.put(runtime, cursor);
        }
        FrontierV3InfectionOverlayLedger ledger = FrontierV3InfectionOverlayLedger.get(level);
        int budget = MAX_CELLS_PER_TICK;
        while (budget-- > 0 && cursor.hasRetraction()) reconcileRetraction(level, ledger, cursor.nextRetraction(), state);
        while (budget-- > 0 && cursor.hasDesired()) project(level, ledger, cursor.nextDesired(), state);
    }

    static void forget(FrontierV3ServerRuntime<?, ?> runtime) { CURSORS.remove(runtime); }

    static BlockBreakObservation observeBlockBreak(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, ServerLevel level,
                                                   BlockPos position, String cause) {
        FrontierV3InfectionOverlayLedger ledger = FrontierV3InfectionOverlayLedger.get(level);
        InfectionCell cell = ledger.cellAt(position).orElse(null);
        if (cell == null) return BlockBreakObservation.UNMANAGED;
        FrontierV3InfectionOverlayLedger.Claim claim = ledger.claim(cell);
        if (claim == null || !claim.active() || !level.getBlockState(position).equals(material(claim.stage()))) {
            if (claim != null) ledger.conflict(cell);
            return BlockBreakObservation.UNMANAGED;
        }
        try {
            CheckpointImage checkpoint = runtime.checkpointImage().orElseThrow(() -> new IllegalStateException("v3 runtime is inactive"));
            CommandId id = new CommandId("executor:infection-overlay-break-r" + checkpoint.revision().value() + "-p" + position.asLong());
            PhysicalDeltaObserved observed = new PhysicalDeltaObserved(new PhysicalDelta(canonical(position), PhysicalDeltaKind.UNKNOWN_SCAR,
                    Optional.empty(), Optional.empty(), cause));
            CommandResult result = runtime.submit(new FrontierCommand(1, id, checkpoint.worldId(), checkpoint.revision(), checkpoint.instant(),
                    FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(id), observed))
                    .orElseThrow(() -> new IllegalStateException("v3 runtime is inactive"));
            if (!(result instanceof CommandResult.Accepted)) return BlockBreakObservation.REJECTED;
            ledger.conflict(cell); // only after WAL acknowledgement, immediately before Minecraft breaks the marker
            return BlockBreakObservation.ACCEPTED;
        } catch (RuntimeException failed) {
            return BlockBreakObservation.REJECTED;
        }
    }

    static ProjectionResult project(ServerLevel level, FrontierV3InfectionOverlayLedger ledger, InfectionOverlayCell desired,
                                    FrontierWorldState state) {
        FrontierV3InfectionOverlayLedger.Claim claim = ledger.claim(desired.cell());
        if (claim != null) return projectClaim(level, ledger, desired, state, claim);
        List<BlockPos> positions = discoveredPatch(level, desired);
        if (positions == null) return ProjectionResult.DEFERRED;
        if (positions.stream().anyMatch(position -> state.physicalDeltas().containsKey(canonical(position)) || !level.getBlockState(position).isAir())) {
            ledger.blocked(desired.cell(), positions, desired.stage());
            return ProjectionResult.CONFLICT;
        }
        ledger.ensureCapacityFor(desired.cell());
        ledger.prepare(desired.cell(), positions, desired.stage());
        BlockState expected = material(desired.stage());
        if (!replace(level, positions, expected)) return ProjectionResult.DEFERRED;
        ledger.activate(desired.cell());
        return ProjectionResult.APPLIED;
    }

    private static ProjectionResult projectClaim(ServerLevel level, FrontierV3InfectionOverlayLedger ledger, InfectionOverlayCell desired,
                                                 FrontierWorldState state, FrontierV3InfectionOverlayLedger.Claim claim) {
        if (claim.conflicted()) return ProjectionResult.CONFLICT;
        List<BlockPos> positions = claim.blockPositions();
        if (positions.stream().anyMatch(position -> !level.hasChunkAt(position))) return ProjectionResult.DEFERRED;
        if (claim.prepared()) return reconcilePrepared(level, ledger, desired, state, claim, positions);
        if (claim.cleared()) {
            if (positions.stream().anyMatch(position -> !level.getBlockState(position).isAir())) { ledger.conflict(desired.cell()); return ProjectionResult.CONFLICT; }
            BlockState expected = material(desired.stage());
            if (!replace(level, positions, expected)) return ProjectionResult.DEFERRED;
            ledger.updateStage(desired.cell(), desired.stage()); return ProjectionResult.UPDATED;
        }
        if (positions.stream().anyMatch(position -> state.physicalDeltas().containsKey(canonical(position))
                || !level.getBlockState(position).equals(material(claim.stage())))) {
            ledger.conflict(desired.cell()); return ProjectionResult.CONFLICT;
        }
        if (claim.stage() == desired.stage()) return ProjectionResult.CURRENT;
        BlockState expected = material(desired.stage());
        if (!replace(level, positions, expected)) return ProjectionResult.DEFERRED;
        ledger.updateStage(desired.cell(), desired.stage()); return ProjectionResult.UPDATED;
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
            ledger.conflict(desired.cell()); return ProjectionResult.CONFLICT;
        }
        BlockState preparedMaterial = material(claim.stage());
        if (positions.stream().allMatch(position -> level.getBlockState(position).equals(preparedMaterial))) {
            ledger.activate(desired.cell()); return ProjectionResult.APPLIED;
        }
        if (positions.stream().allMatch(position -> level.getBlockState(position).isAir())) {
            ledger.retargetPrepared(desired.cell(), desired.stage());
            BlockState expected = material(desired.stage());
            if (!replace(level, positions, expected)) return ProjectionResult.DEFERRED;
            ledger.activate(desired.cell()); return ProjectionResult.APPLIED;
        }
        ledger.conflict(desired.cell()); return ProjectionResult.CONFLICT;
    }

    static ProjectionResult reconcileRetraction(ServerLevel level, FrontierV3InfectionOverlayLedger ledger,
                                                Map.Entry<InfectionCell, FrontierV3InfectionOverlayLedger.Claim> entry,
                                                FrontierWorldState state) {
        InfectionCell cell = entry.getKey(); FrontierV3InfectionOverlayLedger.Claim claim = entry.getValue();
        if (claim.conflicted()) return ProjectionResult.CONFLICT;
        List<BlockPos> positions = claim.blockPositions();
        if (positions.stream().anyMatch(position -> !level.hasChunkAt(position))) return ProjectionResult.DEFERRED;
        if (claim.cleared()) {
            if (positions.stream().anyMatch(position -> !level.getBlockState(position).isAir())) { ledger.conflict(cell); return ProjectionResult.CONFLICT; }
            ledger.forgetRetracted(cell); return ProjectionResult.RETRACTED;
        }
        if (positions.stream().anyMatch(position -> state.physicalDeltas().containsKey(canonical(position))
                || !level.getBlockState(position).equals(material(claim.stage())))) {
            ledger.conflict(cell); return ProjectionResult.CONFLICT;
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

    private static List<BlockPos> discoveredPatch(ServerLevel level, InfectionOverlayCell desired) {
        List<BlockPos> positions = new java.util.ArrayList<>(FrontierV3InfectionOverlayLedger.PATCH_COLUMNS);
        for (InfectionOverlayCell.SurfaceColumn column : desired.surfaceColumns()) {
            BlockPos columnProbe = new BlockPos(column.x(), 0, column.z());
            if (!level.hasChunkAt(columnProbe)) return null;
            BlockPos position = new BlockPos(column.x(), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.x(), column.z()), column.z());
            if (!level.hasChunkAt(position)) return null;
            // A prior interrupted v3 write can itself become the motion-blocking surface.  Keep
            // that exact carpet in the failed baseline so a later retry conflicts rather than
            // silently placing another carpet one block above it.
            if (isOverlayMaterial(level.getBlockState(position.below()))) position = position.below();
            positions.add(position);
        }
        return List.copyOf(positions);
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
        private final Revision revision;
        private final List<InfectionOverlayCell> desired;
        private final List<Map.Entry<InfectionCell, FrontierV3InfectionOverlayLedger.Claim>> retractions;
        private int desiredIndex;
        private int retractionIndex;

        private Cursor(Revision revision, List<InfectionOverlayCell> desired,
                       List<Map.Entry<InfectionCell, FrontierV3InfectionOverlayLedger.Claim>> retractions) {
            this.revision = revision; this.desired = desired; this.retractions = retractions;
        }
        static Cursor from(Revision revision, FrontierInfectionOverlayPlan plan, FrontierV3InfectionOverlayLedger ledger) {
            List<InfectionOverlayCell> desired = plan.cells().values().stream().sorted(Comparator.comparingInt((InfectionOverlayCell cell) -> cell.cell().x())
                    .thenComparingInt(cell -> cell.cell().z())).toList();
            Set<InfectionCell> active = plan.cells().keySet();
            List<Map.Entry<InfectionCell, FrontierV3InfectionOverlayLedger.Claim>> retractions = ledger.orderedClaims().stream()
                    .filter(entry -> !active.contains(entry.getKey())).toList();
            return new Cursor(revision, desired, retractions);
        }
        Revision revision() { return revision; }
        boolean hasDesired() { return !desired.isEmpty(); }
        boolean hasRetraction() { return retractionIndex < retractions.size(); }
        InfectionOverlayCell nextDesired() {
            InfectionOverlayCell next = desired.get(desiredIndex); desiredIndex = (desiredIndex + 1) % desired.size(); return next;
        }
        Map.Entry<InfectionCell, FrontierV3InfectionOverlayLedger.Claim> nextRetraction() { return retractions.get(retractionIndex++); }
    }
}
