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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityMobGriefingEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Exercises the low-tech physical provider independently of campaign setup. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class VanillaMinecartRouteGameTests {
    private VanillaMinecartRouteGameTests() { }

    @GameTest(batch = "pm-vanilla-minecart-protection", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void endermenCannotGriefButOtherMobPolicyIsUnchanged(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        EnderMan enderman = EntityType.ENDERMAN.create(level);
        Zombie zombie = EntityType.ZOMBIE.create(level);
        helper.assertTrue(enderman != null && zombie != null, "test mobs must be constructible");

        EntityMobGriefingEvent endermanEvent = new EntityMobGriefingEvent(level, enderman);
        NeoForge.EVENT_BUS.post(endermanEvent);
        helper.assertFalse(endermanEvent.canGrief(),
                "Endermen must never remove supports or place carried blocks around PM infrastructure");

        EntityMobGriefingEvent zombieEvent = new EntityMobGriefingEvent(level, zombie);
        boolean configuredPolicy = zombieEvent.canGrief();
        NeoForge.EVENT_BUS.post(zombieEvent);
        helper.assertValueEqual(zombieEvent.canGrief(), configuredPolicy,
                "the Enderman-specific protection must not alter other mobs or the global mobGriefing rule");
        helper.succeed();
    }

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

    @GameTest(batch = "pm-vanilla-minecart-recovery", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void damageAndRepresentativeCarrierSurviveRestart(GameTestHelper helper) {
        if (GameTestProfiles.createAdapterOnly()) { helper.succeed(); return; }
        PaleMirrorSavedData data = PaleMirrorSavedData.get(helper.getLevel().getServer().overworld());
        reset(data);
        BlockPos start = helper.absolutePos(new BlockPos(0, 6, 0));
        VanillaMinecartRouteRecord record = VanillaMinecartRouteRecord.planned("pale_mirror:recovery_test",
                helper.getLevel().dimension().location().toString(), "pale_mirror:recovery_route", start, start.east(8));
        BlockPos rail = record.railPosition(0);
        record.capture(rail, "minecraft:air");
        record.approve(rail, "minecraft:rail");
        record.suspend(rail, "rail removed");
        record.observeRepresentativeCart(java.util.UUID.randomUUID(), java.util.UUID.randomUUID());
        record.moveCart(2.5D);
        data.vanillaMinecartRoutes().put(record.regionId(), record);

        CompoundTag snapshot = data.save(new CompoundTag(), helper.getLevel().registryAccess());
        VanillaMinecartRouteRecord reloaded = PaleMirrorSavedData.load(snapshot, helper.getLevel().registryAccess())
                .vanillaMinecartRoutes().get(record.regionId());

        helper.assertValueEqual(reloaded.status(), VanillaMinecartRouteStatus.SUSPENDED,
                "restart must preserve the fail-closed route state");
        helper.assertTrue(reloaded.damagedCriticalCells().contains(rail.asLong()),
                "restart must preserve the exact critical repair target");
        helper.assertValueEqual(reloaded.representativeCartId(), record.representativeCartId(),
                "restart must preserve the visual carrier identity");
        helper.assertValueEqual(reloaded.cartProgress(), 2.5D,
                "restart must preserve bounded representative movement progress");
        helper.assertTrue(reloaded.repaired(rail) && reloaded.repairComplete(),
                "restoring the registered rail postcondition must make the route resumable");
        helper.succeed();
    }

    @GameTest(batch = "pm-vanilla-minecart-multi-repair", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void suspendedRouteDiscoversAndReconcilesMultipleLoadedBreaks(GameTestHelper helper) {
        if (GameTestProfiles.createAdapterOnly()) { helper.succeed(); return; }
        ServerLevel level = helper.getLevel();
        PaleMirrorSavedData data = PaleMirrorSavedData.get(level.getServer().overworld());
        reset(data);
        BlockPos start = helper.absolutePos(new BlockPos(0, 6, 0));
        VanillaMinecartRouteRecord record = VanillaMinecartRouteRecord.planned("pale_mirror:multi_repair_test",
                level.dimension().location().toString(), "pale_mirror:multi_repair_route", start, start.east(8));
        BlockPos first = record.railPosition(0);
        BlockPos second = record.railPosition(1);
        String rail = Blocks.RAIL.defaultBlockState().toString();
        record.capture(first, Blocks.AIR.defaultBlockState().toString());
        record.approve(first, rail);
        record.capture(second, Blocks.AIR.defaultBlockState().toString());
        record.approve(second, rail);
        record.suspend(first, "first known break");
        level.setBlock(first.below(), Blocks.GRAVEL.defaultBlockState(), 3);
        level.setBlock(second.below(), Blocks.GRAVEL.defaultBlockState(), 3);
        level.setBlock(first, Blocks.RAIL.defaultBlockState(), 3);
        level.setBlock(second, Blocks.AIR.defaultBlockState(), 3);
        data.vanillaMinecartRoutes().put(record.regionId(), record);

        VanillaMinecartRouteRuntime.tick(level.getServer(), data, new DomainServices().commands());
        helper.assertValueEqual(record.status(), VanillaMinecartRouteStatus.SUSPENDED,
                "repairing one cell must not resume a route while another loaded break remains");
        helper.assertValueEqual(record.damagedCriticalCellCount(), 1,
                "the repaired cell must clear while the newly discovered break remains tracked");
        helper.assertValueEqual(record.firstDamagedCriticalCell(), second,
                "repair diagnostics must advance to the next exact loaded break");

        level.setBlock(second, Blocks.RAIL.defaultBlockState(), 3);
        VanillaMinecartRouteRuntime.tick(level.getServer(), data, new DomainServices().commands());
        helper.assertValueEqual(record.status(), VanillaMinecartRouteStatus.ACTIVE,
                "the physical route must resume as soon as all known loaded breaks match provenance");
        helper.assertValueEqual(record.damagedCriticalCellCount(), 0,
                "completed repairs must clear the bounded damage set");
        helper.succeed();
    }

    private static void reset(PaleMirrorSavedData data) {
        data.vanillaMinecartRoutes().clear();
        data.worldState().clearRegionalState();
        data.worldState().setSimulationStep(0);
        data.worldState().setEventSequence(0);
        data.setDirty();
    }
}
