package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration;
import io.farfrontier.palemirror.frontier.v3.kernel.WorkBudget;
import io.farfrontier.palemirror.frontier.v3.model.DeferredAftermath;
import io.farfrontier.palemirror.frontier.v3.model.DeferredAftermathCell;
import io.farfrontier.palemirror.frontier.v3.model.DeferredAftermathCellStatus;
import io.farfrontier.palemirror.frontier.v3.model.FrontierV3FixtureCatalog;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierStore;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Focused production-seam evidence: an ordinary scheduled COLD combat action creates its own
 * aftermath, then the lifecycle calls the complete physical registry on the actual GameTest
 * ServerLevel. It deliberately makes no claim about natural player demand or restart.
 */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FrontierV3ProductionPhysicalTurnGameTests {
    private FrontierV3ProductionPhysicalTurnGameTests() { }

    @GameTest(batch = "pm-frontier-v3-scene-aftermath", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 80)
    public static void lifecyclePhysicalTurnLeavesAnOrdinaryUnavailableColdAftermathPending(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = runtime(level);
        try {
            FrontierWorldState before = runtime.decodedState().orElseThrow();
            helper.assertTrue(before.deferredAftermath().entries().isEmpty(),
                    "the fixture retains only the exact COLD combat precondition, not an aftermath");
            helper.assertTrue(before.physicalDeltas().isEmpty(),
                    "the fixture retains no preinstalled physical loss");

            runtime.tick(new WorkBudget(64, 512)).orElseThrow();
            DeferredAftermath aftermath = runtime.decodedState().orElseThrow().deferredAftermath().entries().values().iterator().next();
            DeferredAftermathCell cell = aftermath.cellAt(0);
            BlockPos target = new BlockPos(cell.position().x(), cell.position().y(), cell.position().z());
            helper.assertValueEqual(cell.status(), DeferredAftermathCellStatus.PENDING,
                    "the ordinary due action creates one pending COLD aftermath before physical ownership runs");
            helper.assertTrue(!level.getChunkSource().hasChunk(target.getX() >> 4, target.getZ() >> 4),
                    "the COLD target remains naturally unavailable to this GameTest turn");
            helper.assertTrue(FrontierV3GrayboxLedger.get(level).claim(target) == null,
                    "the real SavedData ledger begins untouched at the unavailable target");

            FrontierV3ServerLifecycle.runPhysicalTurn(level, runtime);
            DeferredAftermath pending = runtime.decodedState().orElseThrow().deferredAftermath().entries().get(aftermath.id());
            helper.assertValueEqual(pending.cellAt(0).status(), DeferredAftermathCellStatus.PENDING,
                    "the complete production registry leaves unavailable work pending without force-loading or replay");
            helper.assertTrue(FrontierV3GrayboxLedger.get(level).claim(target) == null,
                    "the complete production registry does not manufacture a tombstone before natural availability");
            helper.assertTrue(hasWal(level.getServer().getWorldPath(LevelResource.ROOT)),
                    "the ordinary due action is durably appended below the disposable GameTest world root");
            long revision = runtime.canonicalState().orElseThrow().revision().value();
            FrontierV3ServerLifecycle.runPhysicalTurn(level, runtime);
            helper.assertValueEqual(runtime.canonicalState().orElseThrow().revision().value(), revision,
                    "a repeated unavailable physical turn does not manufacture an aftermath revision");
        } finally {
            FrontierV3GrayboxExecutor.forget(runtime);
            runtime.shutdown();
        }
        helper.succeed();
    }

    @GameTest(batch = "pm-frontier-v3-scene-aftermath", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 80)
    public static void firstVisibilityDefersChunkLoadMaterializationUntilRegisteredProjectionTurn(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = runtime(level);
        try {
            ChunkPos loaded = level.getChunkAt(helper.absolutePos(BlockPos.ZERO)).getPos();
            FrontierV3GrayboxExecutor.observeNaturalChunkLoad(level, runtime, loaded);
            FrontierV3GrayboxExecutor.FirstVisibilitySnapshot queued = FrontierV3GrayboxExecutor.firstVisibility(runtime,
                    loaded.x + "," + loaded.z);
            helper.assertValueEqual(queued.status(), "PENDING",
                    "ChunkEvent.Load only fences first visibility; it must not mutate blocks or re-enter ChunkMap from vanilla's load callback");
            helper.assertTrue(!FrontierV3GrayboxExecutor.sceneEligible(runtime,
                            new io.farfrontier.palemirror.frontier.v3.model.BlockPosition(loaded.getMinBlockX(), 64, loaded.getMinBlockZ())),
                    "a just-loaded chunk remains ineligible until the registered physical projection turn completes");

            FrontierV3ServerLifecycle.runPhysicalTurn(level, runtime);
            FrontierV3GrayboxExecutor.FirstVisibilitySnapshot completed = FrontierV3GrayboxExecutor.firstVisibility(runtime,
                    loaded.x + "," + loaded.z);
            helper.assertValueEqual(completed.status(), "READY",
                    "the ordinary projection/effect composition, not the ChunkEvent callback, completes static and dynamic first visibility");
        } finally {
            FrontierV3GrayboxExecutor.forgetFirstVisibility(runtime);
            FrontierV3GrayboxExecutor.forget(runtime);
            runtime.shutdown();
        }
        helper.succeed();
    }

    @GameTest(batch = "pm-frontier-v3-scene-aftermath", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 80)
    public static void playerIngressFencesAnUnloadedDestinationWithoutCreatingAChunk(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = runtime(level);
        try {
            ChunkPos destination = new ChunkPos(loadedChunk(level, helper).x + 96, loadedChunk(level, helper).z + 96);
            helper.assertTrue(!level.hasChunk(destination.x, destination.z),
                    "the destination has no installed holder at the transfer event boundary");
            FrontierV3GrayboxExecutor.observePlayerIngress(runtime, destination);
            FrontierV3GrayboxExecutor.FirstVisibilitySnapshot queued = FrontierV3GrayboxExecutor.firstVisibility(runtime,
                    destination.x + "," + destination.z);
            helper.assertValueEqual(queued.status(), "PENDING",
                    "player ingress retains the exposure fence without asking ChunkMap to create a holder");
            helper.assertTrue(!FrontierV3GrayboxExecutor.sceneEligible(runtime,
                            new io.farfrontier.palemirror.frontier.v3.model.BlockPosition(destination.getMinBlockX(), 64, destination.getMinBlockZ())),
                    "an ingress-fenced destination cannot admit a scene before natural availability and the registered projection turn");
            helper.assertTrue(!level.hasChunk(destination.x, destination.z),
                    "the read-only player ingress fence neither creates a ticket nor force-loads its destination");
        } finally {
            FrontierV3GrayboxExecutor.forgetFirstVisibility(runtime);
            FrontierV3GrayboxExecutor.forget(runtime);
            runtime.shutdown();
        }
        helper.succeed();
    }

    @GameTest(batch = "pm-frontier-v3-scene-aftermath", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 80)
    public static void registeredTurnProjectsAnAlreadyPresentPlayerDestination(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = runtime(level);
        try {
            var player = helper.makeMockServerPlayerInLevel();
            ChunkPos destination = loadedChunk(level, helper);
            player.absMoveTo(destination.getMinBlockX() + 8.5D, 64.0D, destination.getMinBlockZ() + 8.5D,
                    player.getYRot(), player.getXRot());
            helper.assertValueEqual(FrontierV3GrayboxExecutor.firstVisibility(runtime, destination.x + "," + destination.z).status(),
                    "UNOBSERVED", "the player destination has no synthetic prior projection record");
            FrontierV3ServerLifecycle.runObservedPhysicalTurn(level, runtime);
            helper.assertValueEqual(FrontierV3GrayboxExecutor.firstVisibility(runtime, destination.x + "," + destination.z).status(),
                    "READY", "the registered production turn fences and projects an already-present destination before scene consumption");
        } finally {
            FrontierV3GrayboxExecutor.forgetFirstVisibility(runtime);
            FrontierV3GrayboxExecutor.forget(runtime);
            runtime.shutdown();
        }
        helper.succeed();
    }

    private static ChunkPos loadedChunk(ServerLevel level, GameTestHelper helper) {
        return level.getChunkAt(helper.absolutePos(BlockPos.ZERO)).getPos();
    }

    private static FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime(ServerLevel level) {
        WorldId world = new WorldId("frontier:production-physical-turn-" + level.getSeed());
        FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> configuration =
                FrontierV3FixtureCatalog.coldBomberAftermathConfiguration(world, 91L);
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
