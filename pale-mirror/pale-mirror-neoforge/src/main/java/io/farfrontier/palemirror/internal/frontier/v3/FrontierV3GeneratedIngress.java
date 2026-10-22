package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import java.util.*;

/** Read-only ingress lane: proving already generated geometry does not need a
 * mutation transaction or wait behind a job's WAL continuation. One shared native
 * turn budget limits all packet candidates; actual repairs stay with their owners. */
final class FrontierV3GeneratedIngress {
    private static final Map<FrontierV3ServerRuntime<?, ?>, Turn> TURNS = new IdentityHashMap<>();
    private record Turn(long tick, FrontierV3FirstVisibilityBudget budget, Set<ChunkPos> checked) { }
    private FrontierV3GeneratedIngress() { }
    static boolean presentationReady(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, ChunkPos chunk) {
        FrontierV3GrayboxExecutor.observeGeneratedIngress(level, runtime, chunk);
        return FrontierV3GrayboxExecutor.presentationReady(runtime, chunk);
    }
    static FrontierV3FirstVisibilityBudget admit(ServerLevel level, FrontierV3ServerRuntime<?, ?> runtime, ChunkPos chunk) {
        var turn = TURNS.get(runtime);
        if (turn == null || turn.tick() != level.getGameTime()) {
            turn = new Turn(level.getGameTime(), new FrontierV3FirstVisibilityBudget(
                    io.farfrontier.palemirror.internal.world.PaleMirrorServerConfig.RUNTIME_WEIGHT_BUDGET.get(), 1,
                    (long) (io.farfrontier.palemirror.internal.world.PaleMirrorServerConfig.RUNTIME_BUDGET_MILLIS.get() * 1_000_000D),
                    System::nanoTime), new HashSet<>());
            TURNS.put(runtime, turn);
        }
        return turn.budget().canRead() && turn.checked().add(chunk) ? turn.budget() : null;
    }
    static boolean current(ServerLevel level, FrontierWorldState state, GrayboxCell cell) {
        var p = cell.position(); var position = new BlockPos(p.x(), p.y(), p.z());
        if (state.physicalDeltas().containsKey(p)) return false;
        var baseline = FrontierV3GeneratedStructures.loaded(level, position).orElse(null);
        if (baseline == null || !cell.equals(baseline.cells().get(position))) return false;
        FrontierV3GenerationConfiguration.require(state.bootstrap(), baseline.seed(), baseline.configuration());
        var claim = FrontierV3GrayboxLedger.get(level).claim(position);
        return FrontierV3GeneratedStructures.claim(cell).equals(claim)
                && level.getBlockState(position).equals(FrontierV3GrayboxExecutor.material(cell.material()));
    }
    static void forget(FrontierV3ServerRuntime<?, ?> runtime) { TURNS.remove(runtime); }
}
