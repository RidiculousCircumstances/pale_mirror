package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FrontierV3TerrainReadinessGameTests {
    private FrontierV3TerrainReadinessGameTests() { }

    @GameTest(batch = "pm-frontier-v3-scene-body-lifetime", templateNamespace = "minecraft",
            template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void physicsReadinessNeverTurnsAbsentTerrainIntoBlockingDemand(GameTestHelper helper) {
        var level = helper.getLevel();
        var local = helper.absolutePos(new BlockPos(1, 1, 1));
        helper.assertTrue(FrontierV3LoadedWorld.ready(level, new AABB(local)), "fixture terrain is fully loaded");
        var remote = local.offset(1_000_000, 0, 1_000_000);
        helper.assertTrue(level.getChunkSource().getChunkNow(remote.getX() >> 4, remote.getZ() >> 4) == null,
                "remote column starts absent");
        helper.assertFalse(FrontierV3LoadedWorld.ready(level, new AABB(remote).inflate(1.0D)),
                "physics must await completed terrain");
        helper.assertTrue(level.getChunkSource().getChunkNow(remote.getX() >> 4, remote.getZ() >> 4) == null,
                "readiness inspection cannot load or generate the missing column");
        helper.succeed();
    }
}
