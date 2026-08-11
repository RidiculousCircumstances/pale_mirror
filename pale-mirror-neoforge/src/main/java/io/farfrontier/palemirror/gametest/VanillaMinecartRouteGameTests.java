package io.farfrontier.palemirror.gametest;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.domain.DomainServices;
import io.farfrontier.palemirror.internal.world.PaleMirrorSavedData;
import io.farfrontier.palemirror.internal.world.VanillaMinecartRouteRecord;
import io.farfrontier.palemirror.internal.world.VanillaMinecartRouteRuntime;
import io.farfrontier.palemirror.internal.world.VanillaMinecartRouteStatus;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Exercises the low-tech physical provider independently of campaign setup. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class VanillaMinecartRouteGameTests {
    private VanillaMinecartRouteGameTests() { }

    @GameTest(batch = "pm-vanilla-minecart-build", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 80)
    public static void corridorBuildsFromPersistedProvenance(GameTestHelper helper) {
        if (GameTestProfiles.createAdapterOnly()) { helper.succeed(); return; }
        ServerLevel level = helper.getLevel();
        PaleMirrorSavedData data = PaleMirrorSavedData.get(level.getServer().overworld());
        reset(data);
        BlockPos start = helper.absolutePos(new BlockPos(0, 12, 0));
        for (int index = 0; index <= 1; index++) for (int y = -2; y <= 4; y++)
            level.setBlock(start.east(index).above(y), Blocks.AIR.defaultBlockState(), 3);
        VanillaMinecartRouteRecord record = VanillaMinecartRouteRecord.planned(
                "pale_mirror:minecart_test", level.dimension().location().toString(), "pale_mirror:test_route",
                start, start.east());
        data.vanillaMinecartRoutes().put(record.regionId(), record);

        helper.runAfterDelay(20, () -> {
            for (int step = 0; step < 12; step++)
                VanillaMinecartRouteRuntime.tick(level.getServer(), data, new DomainServices().commands());
            helper.assertTrue(record.status() == VanillaMinecartRouteStatus.VERIFYING,
                    "all loaded vanilla route segments must be physically postcondition-checked before activation: " + record.diagnostic());
            helper.assertValueEqual(record.completedSegmentCount(), record.segmentCount(),
                    "the persisted route plan must complete every bounded segment exactly once");
            helper.assertValueEqual(level.getBlockState(record.railPosition(1)).getBlock(), Blocks.RAIL,
                    "the narrow corridor must use vanilla rail rather than Create track");
            helper.assertValueEqual(level.getBlockState(record.target().relative(record.direction())).getBlock(), Blocks.OAK_FENCE,
                    "the receiving endpoint must be a readable low-tech platform rather than an abstract rail stop");
            CompoundTag snapshot = data.save(new CompoundTag(), level.registryAccess());
            VanillaMinecartRouteRecord reloaded = PaleMirrorSavedData.load(snapshot, level.registryAccess())
                    .vanillaMinecartRoutes().get(record.regionId());
            helper.assertTrue(reloaded != null && reloaded.completedSegmentCount() == record.segmentCount(),
                    "restart must retain vanilla-route progress and its provenance cells");
            helper.succeed();
        });
    }

    @GameTest(batch = "pm-vanilla-minecart-conflict", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 40)
    public static void corridorFailsClosedBeforeProtectedWrite(GameTestHelper helper) {
        if (GameTestProfiles.createAdapterOnly()) { helper.succeed(); return; }
        ServerLevel level = helper.getLevel();
        PaleMirrorSavedData data = PaleMirrorSavedData.get(level.getServer().overworld());
        reset(data);
        BlockPos start = helper.absolutePos(new BlockPos(0, 4, 0));
        level.setBlock(start, Blocks.CHEST.defaultBlockState(), 3);
        VanillaMinecartRouteRecord record = VanillaMinecartRouteRecord.planned(
                "pale_mirror:minecart_conflict", level.dimension().location().toString(), "pale_mirror:conflict_route",
                start, start.east(8));
        data.vanillaMinecartRoutes().put(record.regionId(), record);

        helper.runAfterDelay(8, () -> {
            VanillaMinecartRouteRuntime.tick(level.getServer(), data, new DomainServices().commands());
            helper.assertValueEqual(record.status(), VanillaMinecartRouteStatus.BLOCKED,
                    "a stateful cell on a fresh PM corridor must block before any destructive write");
            helper.assertValueEqual(level.getBlockState(start).getBlock(), Blocks.CHEST,
                    "the protected block entity must survive an aborted vanilla corridor preflight");
            helper.succeed();
        });
    }

    private static void reset(PaleMirrorSavedData data) {
        data.vanillaMinecartRoutes().clear();
        data.worldState().clearRegionalState();
        data.worldState().setSimulationStep(0);
        data.worldState().setEventSequence(0);
        data.setDirty();
    }
}
