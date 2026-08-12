package io.farfrontier.palemirror.gametest;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.domain.WorldObjectId;
import io.farfrontier.palemirror.internal.debug.RuntimeDebugNavigator;
import io.farfrontier.palemirror.internal.debug.RuntimeDebugService;
import io.farfrontier.palemirror.internal.world.PaleMirrorSavedData;
import io.farfrontier.palemirror.internal.world.WorldObjectLifecycle;
import io.farfrontier.palemirror.internal.world.WorldObjectRegistryEntry;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Regression coverage for bounded, non-blocking operator navigation. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RuntimeDebugNavigatorGameTests {
    private RuntimeDebugNavigatorGameTests() { }

    @GameTest(batch = "pm-runtime-debug-teleport", templateNamespace = "minecraft",
            template = "bastion/mobs/empty", timeoutTicks = 40)
    @SuppressWarnings("removal")
    public static void debugTeleportDefersProspectiveChunkWithoutBlocking(GameTestHelper helper) {
        if (GameTestProfiles.createAdapterOnly()) { helper.succeed(); return; }
        ServerLevel level = helper.getLevel();
        PaleMirrorSavedData data = PaleMirrorSavedData.get(level.getServer().overworld());
        LivingRegionGameTests.reset(data);
        BlockPos remote = new BlockPos(2_000_000, 70, 2_000_000);
        ChunkPos remoteChunk = new ChunkPos(remote);
        helper.assertTrue(level.getChunkSource().getChunkNow(remoteChunk.x, remoteChunk.z) == null,
                "the regression target must begin as a prospective chunk");
        WorldObjectId id = new WorldObjectId("pale_mirror:deferred_debug_target");
        data.worldRegistry().register(new WorldObjectRegistryEntry(id,
                level.dimension().location().toString(), remote, remote, remote,
                "pale_mirror:test_debug_target", "1", WorldObjectLifecycle.REPRESENTED));
        var player = helper.makeMockServerPlayerInLevel();
        BlockPos before = player.blockPosition();
        var navigator = new RuntimeDebugNavigator(level.getServer(), data);
        RuntimeDebugService.ActionResult result = navigator.teleportObject(player, id);
        helper.assertTrue(result.success() && result.message().contains("Preparing destination"),
                "an unloaded destination must become an asynchronous preparation, not a blocking height query");
        helper.assertValueEqual(player.blockPosition(), before,
                "the player must remain in the loaded source chunk while preparation is pending");
        helper.assertValueEqual(navigator.pendingTeleportCount(), 1,
                "one bounded runtime request must own the prospective chunk ticket");
        navigator.close();
        helper.assertValueEqual(navigator.pendingTeleportCount(), 0,
                "closing the navigator must release all runtime teleport requests");
        helper.assertTrue(level.getChunkSource().getChunkNow(remoteChunk.x, remoteChunk.z) == null,
                "requesting and cancelling in one tick must not synchronously generate the destination");
        data.worldRegistry().clear();
        helper.succeed();
    }
}
