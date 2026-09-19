package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration;
import io.farfrontier.palemirror.frontier.v3.kernel.TransactionRecord;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.persistence.*;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

/** Live effect boundary: natural local visibility may fence one foreign aftermath cell and continue. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FrontierV3DeferredAftermathGameTests {
    private FrontierV3DeferredAftermathGameTests() { }

    @GameTest(batch = "pm-frontier-v3-scene-aftermath", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 40)
    public static void naturallyLoadedForeignAftermathIsFencedLocallyAndTheNextCellStillAdvances(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos first = helper.absolutePos(new BlockPos(36, 8, 0)), second = first.east();
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = runtime(first, second);
        FrontierWorldState initial = runtime.decodedState().orElseThrow(); DeferredAftermath aftermath = initial.deferredAftermath().entries().values().iterator().next();
        for (BlockPos position : List.of(first, second)) {
            level.setBlock(position, Blocks.DIAMOND_BLOCK.defaultBlockState(), 3);
            DeferredAftermathCell cell = aftermath.cells().get(position.equals(first) ? 0 : 1);
            FrontierV3GrayboxLedger.get(level).applied(position, cell.semanticTarget().subjectId().value(), cell.semanticTarget().kind().wireTag(),
                    GrayboxMaterial.HALL.name(), cell.expectedPart().name());
        }

        FrontierV3DeferredAftermathExecutor.tick(level, runtime);
        helper.assertValueEqual(runtime.decodedState().orElseThrow().deferredAftermath().entries().get(aftermath.id()).resolutionCursor(), 1,
                "the naturally loaded foreign block receives only its own durable typed fence");
        helper.assertTrue(level.getBlockState(first).is(Blocks.DIAMOND_BLOCK), "a later player/world block is never overwritten by old COLD authority");
        FrontierV3DeferredAftermathExecutor.tick(level, runtime);
        DeferredAftermath terminal = runtime.decodedState().orElseThrow().deferredAftermath().entries().get(aftermath.id());
        helper.assertTrue(terminal.terminal() && terminal.cells().stream().allMatch(cell -> cell.status() == DeferredAftermathCellStatus.CONFLICTED),
                "one conflicted cell cannot globally stall the remaining bounded footprint");
        helper.assertTrue(level.getBlockState(second).is(Blocks.DIAMOND_BLOCK) && FrontierV3GrayboxLedger.get(level).claim(second).conflicted(),
                "the unrelated foreign cell remains intact with its own typed provenance fence");
        runtime.shutdown(); helper.succeed();
    }

    @GameTest(batch = "pm-frontier-v3-scene-aftermath", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 40)
    public static void naturallyLoadedColdTombstoneAndAirCompleteWithoutForeignConflict(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); BlockPos first = helper.absolutePos(new BlockPos(0, 8, 0));
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = runtime(first, first.east());
        DeferredAftermath aftermath = runtime.decodedState().orElseThrow().deferredAftermath().entries().values().iterator().next();
        DeferredAftermathCell cell = aftermath.cells().getFirst();
        FrontierV3GrayboxLedger ledger = FrontierV3GrayboxLedger.get(level);
        ledger.damaged(first, cell.semanticTarget().subjectId().value(), cell.semanticTarget().kind().wireTag(), cell.expectedMaterial().name(), cell.expectedPart().name());

        FrontierV3DeferredAftermathExecutor.tick(level, runtime);
        helper.assertValueEqual(runtime.decodedState().orElseThrow().deferredAftermath().entries().get(aftermath.id()).cellAt(0).status(),
                DeferredAftermathCellStatus.RUNNING, "the retained COLD tombstone is a durable satisfied-postcondition boundary");
        FrontierV3DeferredAftermathExecutor.tick(level, runtime);
        DeferredAftermath realized = runtime.decodedState().orElseThrow().deferredAftermath().entries().get(aftermath.id());
        helper.assertValueEqual(realized.cellAt(0).status(), DeferredAftermathCellStatus.REALIZED,
                "a matching tombstone plus AIR completes locally instead of becoming foreign drift");
        helper.assertTrue(level.getBlockState(first).isAir() && ledger.claim(first).conflicted(),
                "the executor neither rewrites the COLD scar nor discards its exact provenance");
        runtime.shutdown(); helper.succeed();
    }

    @GameTest(batch = "pm-frontier-v3-scene-aftermath", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 40)
    public static void naturallyLoadedAirWithoutProjectionClaimWaitsForItsDeclaredOwner(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); BlockPos first = helper.absolutePos(new BlockPos(0, 8, 0));
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = runtime(first, first.east());
        DeferredAftermath aftermath = runtime.decodedState().orElseThrow().deferredAftermath().entries().values().iterator().next();

        FrontierV3DeferredAftermathExecutor.tick(level, runtime);
        DeferredAftermath pending = runtime.decodedState().orElseThrow().deferredAftermath().entries().get(aftermath.id());
        helper.assertValueEqual(pending.cellAt(0).status(), DeferredAftermathCellStatus.PENDING,
                "AIR with no projection claim is pending observation, not foreign conflict authority");

        level.setBlock(first, Blocks.DIAMOND_BLOCK.defaultBlockState(), 3);
        FrontierV3DeferredAftermathExecutor.tick(level, runtime);
        DeferredAftermath conflicted = runtime.decodedState().orElseThrow().deferredAftermath().entries().get(aftermath.id());
        helper.assertValueEqual(conflicted.cellAt(0).status(), DeferredAftermathCellStatus.CONFLICTED,
                "positive foreign material remains an isolated local conflict after pending observation");
        helper.assertTrue(level.getBlockState(first).is(Blocks.DIAMOND_BLOCK), "aftermath never overwrites the foreign block");
        runtime.shutdown(); helper.succeed();
    }

    @GameTest(batch = "pm-frontier-v3-scene-aftermath", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 40)
    public static void materializedAftermathRetainsOneExactPreclaimToDamageRevision(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); BlockPos first = helper.absolutePos(new BlockPos(0, 8, 0));
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = runtime(first, first.east());
        DeferredAftermath aftermath = runtime.decodedState().orElseThrow().deferredAftermath().entries().values().iterator().next();
        DeferredAftermathCell cell = aftermath.cells().getFirst();
        level.setBlock(first, FrontierV3GrayboxExecutor.material(cell.expectedMaterial()), 3);
        FrontierV3GrayboxLedger ledger = FrontierV3GrayboxLedger.get(level);
        ledger.applied(first, cell.semanticTarget().subjectId().value(), cell.semanticTarget().kind().wireTag(), cell.expectedMaterial().name(), cell.expectedPart().name());

        FrontierV3DeferredAftermathExecutor.tick(level, runtime);
        DeferredAftermath realized = runtime.decodedState().orElseThrow().deferredAftermath().entries().get(aftermath.id());
        helper.assertValueEqual(realized.cellAt(0).status(), DeferredAftermathCellStatus.REALIZED,
                "the owned materialized cell crosses its one durable preclaim-to-damage boundary");
        helper.assertTrue(level.getBlockState(first).isAir() && ledger.claim(first).revision() == 1L && ledger.claim(first).conflicted(),
                "the terminal receipt keeps the exact post-write revision rather than a pre-write sentinel");
        runtime.shutdown(); helper.succeed();
    }

    private static FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime(BlockPos first, BlockPos second) {
        WorldId world = new WorldId("frontier:deferred-aftermath-game-test");
        FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> base = FrontierWorldRuntimeDefinition.configuration(world, 91L);
        FrontierWorldState initial = base.initialState(); SubjectId owner = initial.bootstrap().hive().id(); SubjectId id = new SubjectId("aftermath:game-test");
        DeferredAftermath aftermath = new DeferredAftermath(id, owner, new SubjectId("bioform:east-3"), 0L, OptionalLong.empty(), "test:foreign-fence",
                DeferredAftermathKnowledge.KNOWN_CLEAR, 0L, List.of(
                new DeferredAftermathCell(new BlockPosition(first.getX(), first.getY(), first.getZ()),
                        new PhysicalDeltaSemanticTarget(PhysicalDeltaSemanticTargetKind.HIVE_ORGAN, owner), GrayboxMaterial.HALL, GrayboxSemanticPart.FOUNDATION, DeferredAftermathCellStatus.PENDING),
                new DeferredAftermathCell(new BlockPosition(second.getX(), second.getY(), second.getZ()),
                        new PhysicalDeltaSemanticTarget(PhysicalDeltaSemanticTargetKind.HIVE_ORGAN, owner), GrayboxMaterial.HALL, GrayboxSemanticPart.FOUNDATION, DeferredAftermathCellStatus.PENDING)), 0);
        FrontierWorldState prepared = initial.withChanges(FrontierWorldStateUpdate.begin().deferredAftermath(initial.deferredAftermath().prepare(aftermath)));
        FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> configuration = new FrontierEngineConfiguration<>(base.worldId(), prepared,
                base.initialInstant(), base.commandPlanner(), base.scheduledPlanner(), base.reducer(), base.stateCodec(), base.projectionMapper(), base.limits(),
                List.of(), base.transactionCommitter(), base.stateValidator(), base.executionMetrics());
        return FrontierV3ServerRuntime.start(configuration, new EphemeralStore(), 10_000);
    }

    private static final class EphemeralStore implements FrontierStore {
        @Override public RecoveryImage recover(WorldId worldId) { return new RecoveryImage(worldId, Optional.empty(), List.of()); }
        @Override public AppendReceipt append(TransactionRecord transaction, Durability durability) { return new AppendReceipt(transaction.id(), transaction.revision(), durability, transaction.revision().value()); }
        @Override public SnapshotReceipt installSnapshot(SnapshotRecord snapshot) { throw new UnsupportedOperationException("GameTest does not checkpoint"); }
        @Override public CompactionReceipt compact(WorldId worldId, io.farfrontier.palemirror.frontier.v3.api.Revision revision) { throw new UnsupportedOperationException("GameTest does not compact"); }
    }
}
