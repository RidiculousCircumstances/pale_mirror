package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.CheckpointImage;
import io.farfrontier.palemirror.frontier.v3.api.Revision;
import io.farfrontier.palemirror.frontier.v3.model.FrontierGrayboxPlan;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.model.GrayboxCell;
import io.farfrontier.palemirror.frontier.v3.model.GrayboxMaterial;
import io.farfrontier.palemirror.frontier.v3.model.GrayboxSemanticPart;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

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

    enum ProjectionResult { APPLIED, CURRENT, CONFLICT, DEFERRED }

    private FrontierV3GrayboxExecutor() { }

    static void tick(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        CheckpointImage checkpoint = runtime.checkpointImage().orElse(null);
        if (checkpoint == null) return;
        Cursor cursor = CURSORS.get(runtime);
        if (cursor == null || !cursor.revision().equals(checkpoint.revision())) {
            FrontierGrayboxPlan plan = FrontierGrayboxPlan.compile(new FrontierWorldStateCodec().decode(checkpoint.canonicalState()));
            cursor = Cursor.from(checkpoint.revision(), plan, cursor);
            CURSORS.put(runtime, cursor);
        }
        int examined = Math.min(MAX_CELLS_PER_TICK, cursor.cells().size());
        for (int count = 0; count < examined; count++) {
            GrayboxCell cell = cursor.next();
            if (level.hasChunkAt(toMinecraft(cell))) project(level, FrontierV3GrayboxLedger.get(level), cell);
        }
    }

    static void forget(FrontierV3ServerRuntime<?, ?> runtime) { CURSORS.remove(runtime); }

    /** Applies exactly one loaded desired cell; exposed package-private for negative GameTests. */
    static ProjectionResult project(ServerLevel level, FrontierV3GrayboxLedger ledger, GrayboxCell cell) {
        BlockPos position = toMinecraft(cell);
        if (!level.hasChunkAt(position)) return ProjectionResult.DEFERRED;
        BlockState expected = material(cell.material());
        FrontierV3GrayboxLedger.Claim prior = ledger.claim(position);
        if (prior != null) {
            if (prior.conflicted() || !matches(prior, cell)) return ProjectionResult.CONFLICT;
            if (level.getBlockState(position).equals(expected)) return ProjectionResult.CURRENT;
            ledger.conflict(position);
            return ProjectionResult.CONFLICT;
        }
        if (!level.getBlockState(position).isAir()) {
            ledger.obstructed(position, cell.ownerId().value(), cell.material().name(), cell.semanticPart().name());
            return ProjectionResult.CONFLICT;
        }
        if (requiresSupport(cell.semanticPart()) && level.getBlockState(position.below()).isAir()) return ProjectionResult.DEFERRED;
        // Reserve capacity before mutating the world: an exhausted provenance ledger is fail-closed.
        ledger.ensureCapacityFor(position);
        if (!level.setBlock(position, expected, 3) || !level.getBlockState(position).equals(expected)) {
            return ProjectionResult.DEFERRED;
        }
        ledger.applied(position, cell.ownerId().value(), cell.material().name(), cell.semanticPart().name());
        return ProjectionResult.APPLIED;
    }

    static BlockState material(GrayboxMaterial material) {
        return switch (material) {
            case HALL -> Blocks.WHITE_CONCRETE.defaultBlockState();
            case HOUSING -> Blocks.ORANGE_CONCRETE.defaultBlockState();
            case FARM -> Blocks.LIME_CONCRETE.defaultBlockState();
            case WORKSHOP -> Blocks.BLUE_CONCRETE.defaultBlockState();
            case DEPOT -> Blocks.YELLOW_CONCRETE.defaultBlockState();
            case INFIRMARY -> Blocks.PINK_CONCRETE.defaultBlockState();
            case HIVE_HEART -> Blocks.RED_CONCRETE.defaultBlockState();
            case HIVE_BROOD -> Blocks.PURPLE_CONCRETE.defaultBlockState();
            case HIVE_STORE -> Blocks.MAGENTA_CONCRETE.defaultBlockState();
            case ROUTE -> Blocks.GRAY_CONCRETE.defaultBlockState();
            case INFECTION -> throw new IllegalArgumentException("infection requires the dynamic overlay executor");
        };
    }

    private static boolean requiresSupport(GrayboxSemanticPart part) {
        return part == GrayboxSemanticPart.FOUNDATION || part == GrayboxSemanticPart.ROUTE_SURFACE;
    }
    private static boolean matches(FrontierV3GrayboxLedger.Claim claim, GrayboxCell cell) {
        return claim.owner().equals(cell.ownerId().value()) && claim.material().equals(cell.material().name())
                && claim.semanticPart().equals(cell.semanticPart().name());
    }
    private static BlockPos toMinecraft(GrayboxCell cell) {
        return new BlockPos(cell.position().x(), cell.position().y(), cell.position().z());
    }

    private static final class Cursor {
        private final Revision revision;
        private final List<GrayboxCell> cells;
        private int nextIndex;

        private Cursor(Revision revision, List<GrayboxCell> cells, int nextIndex) {
            this.revision = revision;
            this.cells = cells;
            this.nextIndex = nextIndex;
        }
        static Cursor from(Revision revision, FrontierGrayboxPlan plan, Cursor prior) {
            List<GrayboxCell> cells = plan.cells().values().stream().sorted(Comparator
                    .comparingInt((GrayboxCell cell) -> cell.position().y())
                    .thenComparingInt(cell -> cell.position().x()).thenComparingInt(cell -> cell.position().z())).toList();
            int next = prior != null && prior.cells.equals(cells) ? prior.nextIndex % Math.max(1, cells.size()) : 0;
            return new Cursor(revision, cells, next);
        }
        Revision revision() { return revision; }
        List<GrayboxCell> cells() { return cells; }
        GrayboxCell next() {
            if (cells.isEmpty()) throw new IllegalStateException("empty graybox cursor has no next cell");
            GrayboxCell cell = cells.get(nextIndex);
            nextIndex = (nextIndex + 1) % cells.size();
            return cell;
        }
    }
}
