package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.CheckpointImage;
import io.farfrontier.palemirror.frontier.v3.api.CauseChain;
import io.farfrontier.palemirror.frontier.v3.api.CommandId;
import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.model.FrontierGrayboxPlan;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.model.GrayboxCell;
import io.farfrontier.palemirror.frontier.v3.model.GrayboxMaterial;
import io.farfrontier.palemirror.frontier.v3.model.GrayboxSemanticPart;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalDelta;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalDeltaKind;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalDeltaObserved;
import io.farfrontier.palemirror.frontier.v3.model.StructureDamaged;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import net.minecraft.core.BlockPos;
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

    enum ProjectionResult { APPLIED, CURRENT, CONFLICT, DEFERRED }
    enum BlockBreakObservation { UNMANAGED, ACCEPTED, REJECTED }

    private FrontierV3GrayboxExecutor() { }

    static void tick(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        if (runtime.checkpointImage().isEmpty()) return;
        FrontierWorldState state = runtime.decodedState().orElse(null);
        if (state == null) return;
        FrontierGrayboxPlan.StructuralInput input = FrontierGrayboxPlan.structuralInput(state);
        Cursor cursor = CURSORS.get(runtime);
        if (cursor == null || !input.equals(cursor.input())) {
            FrontierGrayboxPlan plan = FrontierGrayboxPlan.compile(state);
            cursor = Cursor.from(input, plan, cursor);
            CURSORS.put(runtime, cursor);
        }
        FrontierV3GrayboxLedger ledger = FrontierV3GrayboxLedger.get(level);
        retireStaleWorksiteStaging(level, ledger, cursor);
        for (int count = 0; count < MAX_CELLS_PER_TICK; count++) {
            GrayboxCell cell = cursor.nextNaturallyLoaded(candidate -> level.hasChunkAt(toMinecraft(candidate))).orElse(null);
            if (cell == null) return;
            project(level, ledger, cell);
        }
    }

    static void forget(FrontierV3ServerRuntime<?, ?> runtime) { CURSORS.remove(runtime); }

    /**
     * Observes a real player break of a still-owned semantic cell before Minecraft removes it.
     * The command is durable first. Only after acceptance does the claim become a conflict, just
     * before Minecraft mutates the block; a rejected observation is reported to the caller so it
     * can cancel the break rather than creating unaccounted physical reality.
     */
    static BlockBreakObservation observeBlockBreak(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, ServerLevel level,
                                                   BlockPos position, String cause) {
        FrontierV3GrayboxLedger ledger = FrontierV3GrayboxLedger.get(level);
        Optional<PhysicalDeltaObserved> observed = preparePhysicalDelta(level, ledger, position, cause);
        if (observed.isEmpty()) return BlockBreakObservation.UNMANAGED;
        try {
            CheckpointImage checkpoint = runtime.checkpointImage().orElseThrow(() -> new IllegalStateException("v3 runtime is inactive"));
            CommandId id = new CommandId("executor:physical-delta-r" + checkpoint.revision().value() + "-p" + position.asLong());
            CommandResult result = runtime.submit(new FrontierCommand(1, id, checkpoint.worldId(), checkpoint.revision(), checkpoint.instant(),
                    FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(id), observed.orElseThrow()))
                    .orElseThrow(() -> new IllegalStateException("v3 runtime is inactive"));
            if (!(result instanceof CommandResult.Accepted)) return BlockBreakObservation.REJECTED;
            PhysicalDelta delta = observed.orElseThrow().delta();
            FrontierV3DiagnosticTrace.record(level.getServer(), FrontierV3DiagnosticTrace.physicalDeltaCorrelation(delta.position()),
                    "physical_delta_observed", delta.ownerId().orElseThrow(), result);
            ledger.conflict(position); // revoke desired-state authority immediately before Minecraft mutates the block
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
            // Routes are semantic paved corridors, not waist-high walls.  A thin gray surface
            // stays plainly visible in graybox; exact Transit bodies use the compiler's adjacent
            // clear lane, so forced HOT motion never treats the route block as pass-through air.
            case ROUTE -> Blocks.GRAY_CARPET.defaultBlockState();
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
        return part == GrayboxSemanticPart.FOUNDATION || part == GrayboxSemanticPart.ROUTE_SURFACE
                || part == GrayboxSemanticPart.PUBLIC_ACCESS_SURFACE || part == GrayboxSemanticPart.WORKSITE_STAGING;
    }
    private static boolean matches(FrontierV3GrayboxLedger.Claim claim, GrayboxCell cell) {
        return claim.owner().equals(cell.ownerId().value()) && claim.material().equals(cell.material().name())
                && claim.semanticPart().equals(cell.semanticPart().name());
    }
    private static BlockPos toMinecraft(GrayboxCell cell) {
        return new BlockPos(cell.position().x(), cell.position().y(), cell.position().z());
    }

    /**
     * Round-robins cells inside naturally loaded plan chunks rather than walking a world-wide
     * list.  A first player visit must therefore make local supports and exact containers
     * available promptly even if lexicographically earlier settlements remain unloaded.
     */
    static final class Cursor {
        private final FrontierGrayboxPlan.StructuralInput input;
        private final List<ChunkCells> chunks;
        private int nextChunkIndex;

        private Cursor(FrontierGrayboxPlan.StructuralInput input, List<ChunkCells> chunks, int nextChunkIndex,
                       java.util.Set<BlockPos> activeWorksiteStaging) {
            this.input = input;
            this.chunks = chunks;
            this.nextChunkIndex = nextChunkIndex;
            this.activeWorksiteStaging = activeWorksiteStaging;
        }
        private final java.util.Set<BlockPos> activeWorksiteStaging;
        static Cursor from(FrontierGrayboxPlan.StructuralInput input, FrontierGrayboxPlan plan, Cursor prior) {
            List<GrayboxCell> cells = plan.cells().values().stream().sorted(Comparator
                    .comparingInt((GrayboxCell cell) -> cell.position().y())
                    .thenComparingInt(cell -> cell.position().x()).thenComparingInt(cell -> cell.position().z())).toList();
            return fromCells(input, cells, prior, plan.cells().values().stream()
                    .filter(cell -> cell.semanticPart() == GrayboxSemanticPart.WORKSITE_STAGING)
                    .map(FrontierV3GrayboxExecutor::toMinecraft).collect(java.util.stream.Collectors.toUnmodifiableSet()));
        }
        static Cursor fromCells(FrontierGrayboxPlan.StructuralInput input, List<GrayboxCell> cells, Cursor prior) {
            return fromCells(input, cells, prior, java.util.Set.of());
        }
        private static Cursor fromCells(FrontierGrayboxPlan.StructuralInput input, List<GrayboxCell> cells, Cursor prior,
                                        java.util.Set<BlockPos> activeWorksiteStaging) {
            Map<ChunkKey, List<GrayboxCell>> grouped = new LinkedHashMap<>();
            cells.forEach(cell -> grouped.computeIfAbsent(ChunkKey.of(cell), ignored -> new ArrayList<>()).add(cell));
            List<ChunkCells> chunks = grouped.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(entry -> {
                ChunkCells before = prior == null ? null : prior.chunk(entry.getKey());
                return new ChunkCells(entry.getKey(), List.copyOf(entry.getValue()), before);
            }).toList();
            int next = prior == null || chunks.isEmpty() ? 0 : indexOf(chunks, prior.nextChunkKey());
            return new Cursor(input, chunks, next, activeWorksiteStaging);
        }
        /** Test-only cell ordering probe; production cursors always retain an exact structural input. */
        static Cursor fromCells(List<GrayboxCell> cells, Cursor prior) {
            return fromCells(null, cells, prior);
        }
        FrontierGrayboxPlan.StructuralInput input() { return input; }
        boolean retainsWorksiteStaging(BlockPos position) { return activeWorksiteStaging.contains(position); }
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
}
