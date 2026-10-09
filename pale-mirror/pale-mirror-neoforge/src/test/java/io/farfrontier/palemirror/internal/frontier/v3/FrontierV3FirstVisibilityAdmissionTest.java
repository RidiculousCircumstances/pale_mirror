package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class FrontierV3FirstVisibilityAdmissionTest {
    @Test void wholeUnchangedChunkCanBeCheckedWithoutSpendingWrites() {
        var world = new World();
        var cells = cells(151);
        cells.forEach(cell -> {
            world.materials.put(cell.position(), cell.material());
            world.ledger.applied(block(cell), cell.ownerId().value(), cell.semanticTarget().kind().wireTag(),
                    cell.material().name(), cell.semanticPart().name());
        });
        var work = new FirstVisibilityWork(cells);
        var budget = new FrontierV3FirstVisibilityBudget(256, 8, 3_000_000, () -> 0L);
        int checked = 0;
        while (!work.complete() && budget.takeRead()) {
            work.observe(FrontierV3GrayboxExecutor.project(world, work.next(), budget::takeWrite));
            checked++;
        }
        assertEquals(151, checked);
        assertTrue(work.complete());
        assertFalse(work.blocked());
        assertEquals(0, world.writes);
        for (int i = 0; i < 8; i++) assertTrue(budget.takeWrite());
        assertFalse(budget.takeWrite(), "read-only inspection did not consume any physical allowance");
    }

    @Test void exhaustedWritesYieldWithoutFalseConflictOrLostCells() {
        var world = new World();
        var work = new FirstVisibilityWork(cells(9));
        var budget = new FrontierV3FirstVisibilityBudget(16, 8, 3_000_000, () -> 0L);
        while (!work.complete() && budget.takeRead())
            work.observe(FrontierV3GrayboxExecutor.project(world, work.next(), budget::takeWrite));
        assertEquals(8, world.writes);
        assertFalse(work.complete(), "ninth mutation is still retained");
        assertFalse(work.blocked(), "a budget yield is not physical obstruction");
        var next = new FrontierV3FirstVisibilityBudget(16, 8, 3_000_000, () -> 0L);
        assertTrue(next.takeRead());
        work.observe(FrontierV3GrayboxExecutor.project(world, work.next(), next::takeWrite));
        assertTrue(work.complete());
        assertEquals(9, world.writes);
    }

    @Test void foreignMaterialCannotBeOverwrittenOrClassifiedBeforeMutationAdmission() {
        var world = new World();
        var cell = cells(1).getFirst();
        world.materials.put(cell.position(), GrayboxMaterial.HALL);
        assertEquals(FrontierV3GrayboxExecutor.ProjectionResult.YIELDED,
                FrontierV3GrayboxExecutor.project(world, cell, () -> false));
        assertNull(world.ledger.claim(block(cell)));
        assertEquals(FrontierV3GrayboxExecutor.ProjectionResult.CONFLICT,
                FrontierV3GrayboxExecutor.project(world, cell, () -> true));
        assertEquals(GrayboxMaterial.HALL, world.materials.get(cell.position()));
        assertEquals(0, world.writes);
        assertNotNull(world.ledger.claim(block(cell)), "classified obstruction keeps its provenance");
    }

    @Test void realUnavailableSurfaceStillCompletesABlockedPass() {
        var work = new FirstVisibilityWork(cells(1));
        work.next(); work.observe(FrontierV3GrayboxExecutor.ProjectionResult.DEFERRED);
        assertTrue(work.blocked());
        assertFalse(work.complete());
    }

    @Test void admissionIsBoundedByBothReadCountAndTimeButCannotStarveFirstAttempt() {
        var clock = new AtomicLong();
        var timeBudget = new FrontierV3FirstVisibilityBudget(256, 8, 3_000_000, clock::get);
        clock.set(4_000_000);
        assertTrue(timeBudget.takeRead());
        assertTrue(timeBudget.takeWrite(), "an admitted operation may finish its whole safe effect");
        assertFalse(timeBudget.takeRead());
        var countBudget = new FrontierV3FirstVisibilityBudget(2, 8, 3_000_000, () -> 0L);
        assertTrue(countBudget.takeRead()); assertTrue(countBudget.takeRead());
        assertFalse(countBudget.takeRead());
    }

    @Test void nearAdmissionAlternatesWithFifoAndConsidersEveryObserver() {
        var far = new ChunkPos(30, 30);
        var near = new ChunkPos(1, 0);
        var anotherPlayer = new ChunkPos(-20, -20);
        var queue = new LinkedHashSet<>(List.of(far, near, anotherPlayer));
        var observers = List.of(new ChunkPos(0, 0), anotherPlayer);
        assertEquals(anotherPlayer, FrontierV3GrayboxExecutor.pollVisibility(queue, observers, true));
        assertEquals(far, FrontierV3GrayboxExecutor.pollVisibility(queue, observers, false),
                "the FIFO turn admits old distant work instead of starving it behind players");
        assertEquals(near, FrontierV3GrayboxExecutor.pollVisibility(queue, observers, true));
        assertNull(FrontierV3GrayboxExecutor.pollVisibility(queue, observers, true));
    }

    @Test void admittedPresentationDoesNotReplayInitialStaticOrDynamicChecks() {
        assertTrue(FrontierV3GrayboxExecutor.FirstVisibility.READY.presentable(() -> {
            throw new AssertionError("already admitted presentation must not replay the initial handoff");
        }));
        assertFalse(FrontierV3GrayboxExecutor.FirstVisibility.PENDING.presentable(() -> {
            throw new AssertionError("static admission has not completed");
        }));
        assertFalse(FrontierV3GrayboxExecutor.FirstVisibility.STATIC_CURRENT.presentable(() ->
                new FrontierV3HotHandoff.Review(1, List.of(new FrontierV3HotHandoff.Check(
                        new SubjectId("site:waiting"), FrontierV3HotHandoff.Status.WAITING, "initialization")))));
    }

    private static List<GrayboxCell> cells(int count) {
        var result = new ArrayList<GrayboxCell>();
        for (int i = 0; i < count; i++) result.add(new GrayboxCell(new BlockPosition(i % 16, 64, i / 16),
                new PhysicalDeltaSemanticTarget(PhysicalDeltaSemanticTargetKind.ROUTE_NETWORK, new SubjectId("route:fixture")),
                GrayboxMaterial.ROUTE, GrayboxSemanticPart.ROUTE_SURFACE));
        return result;
    }
    private static BlockPos block(GrayboxCell cell) {
        return new BlockPos(cell.position().x(), cell.position().y(), cell.position().z());
    }
    private static final class World implements FrontierV3AftermathPhysicalWorld {
        final FrontierV3GrayboxLedger ledger = FrontierV3GrayboxLedger.inMemory();
        final Map<BlockPosition, GrayboxMaterial> materials = new HashMap<>();
        int writes;
        public boolean naturallyLoaded(BlockPosition p) { return true; }
        public boolean isAir(BlockPosition p) { return p.y() > 63 && !materials.containsKey(p); }
        public boolean hasMaterial(BlockPosition p, GrayboxMaterial m) { return materials.get(p) == m; }
        public boolean placeMaterial(BlockPosition p, GrayboxMaterial m) { writes++; materials.put(p, m); return true; }
        public boolean clear(BlockPosition p) { throw new AssertionError("first visibility must not erase material"); }
        public FrontierV3GrayboxLedger ledger() { return ledger; }
    }
}
