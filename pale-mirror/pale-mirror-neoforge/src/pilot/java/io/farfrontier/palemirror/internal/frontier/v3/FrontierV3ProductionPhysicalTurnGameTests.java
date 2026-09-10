package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration;
import io.farfrontier.palemirror.frontier.v3.model.DeferredAftermath;
import io.farfrontier.palemirror.frontier.v3.model.DeferredAftermathCell;
import io.farfrontier.palemirror.frontier.v3.model.DeferredAftermathCellStatus;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldStateUpdate;
import io.farfrontier.palemirror.frontier.v3.model.GrayboxMaterial;
import io.farfrontier.palemirror.frontier.v3.model.GrayboxSemanticPart;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierStore;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.OptionalLong;

/**
 * Focused production-seam evidence: this calls the lifecycle's complete physical turn on the
 * actual GameTest ServerLevel.  It deliberately makes no claim about player demand or restart.
 */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FrontierV3ProductionPhysicalTurnGameTests {
    private FrontierV3ProductionPhysicalTurnGameTests() { }

    @GameTest(batch = "pm-frontier-v3-scene-aftermath", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 80)
    public static void lifecyclePhysicalTurnUsesSavedDataAndFileBackedRuntimeWithoutOverwritingForeignMaterial(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos target = helper.absolutePos(new BlockPos(0, 8, 0));
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = runtime(level, target);
        DeferredAftermath aftermath = runtime.decodedState().orElseThrow().deferredAftermath().entries().values().iterator().next();
        try {
            level.setBlock(target, Blocks.DIAMOND_BLOCK.defaultBlockState(), 3);
            FrontierV3ServerLifecycle.runPhysicalTurn(level, runtime);
            DeferredAftermath conflicted = runtime.decodedState().orElseThrow().deferredAftermath().entries().get(aftermath.id());
            helper.assertValueEqual(conflicted.cellAt(0).status(), DeferredAftermathCellStatus.CONFLICTED,
                    "the exact complete production turn classifies foreign material locally");
            helper.assertTrue(level.getBlockState(target).is(Blocks.DIAMOND_BLOCK),
                    "the production turn preserves foreign material rather than rewriting it");
            helper.assertTrue(FrontierV3GrayboxLedger.get(level).claim(target) == null,
                    "foreign material receives no manufactured SavedData tombstone or adoption claim");
            helper.assertTrue(hasWal(level.getServer().getWorldPath(LevelResource.ROOT)),
                    "the physical-turn command is durably appended below the disposable GameTest world root");
            long revision = runtime.canonicalState().orElseThrow().revision().value();
            FrontierV3ServerLifecycle.runPhysicalTurn(level, runtime);
            helper.assertValueEqual(runtime.canonicalState().orElseThrow().revision().value(), revision,
                    "a terminal aftermath is idempotent through the complete registry");
        } finally {
            FrontierV3GrayboxExecutor.forget(runtime);
            runtime.shutdown();
        }
        helper.succeed();
    }

    private static FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime(ServerLevel level, BlockPos target) {
        WorldId world = new WorldId("frontier:production-physical-turn-" + target.asLong());
        FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> base = FrontierWorldRuntimeDefinition.configuration(world, 91L);
        FrontierWorldState initial = base.initialState(); SubjectId owner = initial.bootstrap().hive().id();
        DeferredAftermath aftermath = new DeferredAftermath(new SubjectId("aftermath:production-physical-turn-" + target.asLong()), owner,
                new SubjectId("bioform:production-physical-turn"), 0L, OptionalLong.empty(), "test:complete-physical-turn",
                io.farfrontier.palemirror.frontier.v3.model.DeferredAftermathKnowledge.KNOWN_CLEAR, 0L,
                List.of(new DeferredAftermathCell(new BlockPosition(target.getX(), target.getY(), target.getZ()), owner, GrayboxMaterial.HALL,
                        GrayboxSemanticPart.FOUNDATION, DeferredAftermathCellStatus.PENDING)), 0);
        FrontierWorldState prepared = initial.withChanges(FrontierWorldStateUpdate.begin().deferredAftermath(initial.deferredAftermath().prepare(aftermath)));
        FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> configuration = new FrontierEngineConfiguration<>(base.worldId(), prepared,
                base.initialInstant(), base.commandPlanner(), base.scheduledPlanner(), base.reducer(), base.stateCodec(), base.projectionMapper(), base.limits(),
                List.of(), base.transactionCommitter(), base.stateValidator(), base.executionMetrics());
        FrontierStore store = new FrontierFileStore(level.getServer().getWorldPath(LevelResource.ROOT), FrontierWorldRuntimeDefinition.payloadCodecs());
        return FrontierV3ServerRuntime.start(configuration, store, 10_000);
    }

    private static boolean hasWal(Path root) {
        try (var paths = Files.walk(root.resolve("frontier-v3"))) {
            return paths.anyMatch(path -> path.getFileName().toString().startsWith("wal-"));
        } catch (java.io.IOException missing) {
            return false;
        }
    }
}
