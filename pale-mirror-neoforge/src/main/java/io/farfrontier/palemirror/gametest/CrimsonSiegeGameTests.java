package io.farfrontier.palemirror.gametest;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.domain.SiegeStage;
import io.farfrontier.palemirror.domain.ThreatTier;
import io.farfrontier.palemirror.internal.PaleMirrorRuntime;
import io.farfrontier.palemirror.internal.adapter.AdapterRegistry;
import io.farfrontier.palemirror.internal.integration.crimson.CrimsonSandboxAdapter;
import io.farfrontier.palemirror.internal.world.PaleMirrorSavedData;
import io.farfrontier.palemirror.internal.world.SiegePartKind;
import io.farfrontier.palemirror.internal.world.TestMineRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Full PM clearance chain against the pinned Crimson sandbox, without Crimson global mechanics. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CrimsonSiegeGameTests {
    private CrimsonSiegeGameTests() { }

    @SuppressWarnings("removal")
    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 240)
    public static void pmControlsCrimsonSiegeClearanceChain(GameTestHelper helper) {
        if (AdapterRegistry.crimson().health().status() != io.farfrontier.palemirror.api.AdapterHealth.Status.AVAILABLE) {
            helper.succeed();
            return;
        }
        ServerLevel level = helper.getLevel();
        PaleMirrorRuntime runtime = PaleMirrorRuntime.forServer(level.getServer());
        BlockPos anchor = helper.absolutePos(new BlockPos(0, 3, 0));
        reset(level);
        clearMineVolume(level, anchor);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setPos(anchor.getX() + 0.5D, anchor.getY() - 2.0D, anchor.getZ() + 0.5D);
        TestMineRecord mine = runtime.createTestMine(player);
        runtime.advanceSimulation(1);
        String scenarioId = runtime.offered(runtime.audienceFor(player)).getFirst().id();
        helper.assertTrue(runtime.accept(scenarioId, runtime.audienceFor(player)), "scenario must be accepted");
        player.setPos(anchor.getX() + 0.5D, anchor.getY() + 2.0D, anchor.getZ() + 0.5D);

        tick(runtime, 5);
        advanceTier(runtime, 12, 8);
        advanceTier(runtime, 24, 11);
        advanceTier(runtime, 36, 24);
        var facility = PaleMirrorSavedData.get(level.getServer().overworld()).worldState().facility(mine.id()).orElseThrow();
        helper.assertValueEqual(facility.threatTier(), ThreatTier.APEX, "PM simulation must create the APEX siege");
        helper.assertValueEqual(facility.siege().stage(), SiegeStage.NODES, "Crimson has no authority over the PM stage");
        helper.assertValueEqual(mine.siege().parts().size(), 4, "one PM record must exist for each node");
        helper.assertTrue(mine.siege().parts().stream().allMatch(part -> part.kind() == SiegePartKind.NODE
                && level.getBlockState(part.position()).is(Blocks.SEA_LANTERN)),
                "nodes must use exactly the four predeclared PM mutable cells");

        LivingEntity controller = (LivingEntity) level.getEntity(mine.anchorId());
        helper.assertTrue(controller != null, "the PM controller must exist while the siege is sealed");
        helper.assertTrue(!controller.hurt(level.damageSources().generic(), 10_000.0F),
                "a protected PM controller must reject direct damage before gate clearance");
        controller.die(level.damageSources().generic());
        helper.assertTrue(!controller.isRemoved(), "a protected PM controller must also reject direct death events");

        mine.siege().parts().forEach(part -> {
            level.setBlock(part.position(), Blocks.AIR.defaultBlockState(), 3);
            runtime.siegeNodeDestroyed(level, part.position());
        });
        helper.assertValueEqual(facility.siege().stage(), SiegeStage.BOSS, "all four exact nodes must unlock one boss");
        tick(runtime, 24);
        CompoundTag snapshot = PaleMirrorSavedData.get(level.getServer().overworld()).save(new CompoundTag(), level.registryAccess());
        var reloaded = PaleMirrorSavedData.load(snapshot, level.registryAccess());
        helper.assertTrue(reloaded.testMines().get(mine.id()).siege().part("boss").orElseThrow().entityId() != null,
                "restart snapshot must retain the PM-owned boss identity");
        clearCurrentGate(level, runtime, mine, "boss", SiegeStage.BLOODLINK_I, helper);
        tick(runtime, 24);
        clearCurrentGate(level, runtime, mine, "bloodlink_i", SiegeStage.BLOODLINK_II, helper);
        tick(runtime, 24);
        clearCurrentGate(level, runtime, mine, "bloodlink_ii", SiegeStage.BLOODLINK_III, helper);
        tick(runtime, 24);
        clearCurrentGate(level, runtime, mine, "bloodlink_iii", SiegeStage.CONTROLLER_VULNERABLE, helper);

        helper.assertTrue(mine.encounter().actors().size() == 16,
                "normal PM APEX guards must persist while boss and Bloodlinks are cleared");
        controller = (LivingEntity) level.getEntity(mine.anchorId());
        helper.assertTrue(controller != null, "controller must still be the sole PM-owned threat authority");
        controller.die(level.damageSources().generic());
        helper.assertValueEqual(PaleMirrorSavedData.get(level.getServer().overworld()).worldState().facility(mine.id()).orElseThrow()
                .siege().stage(), SiegeStage.INACTIVE, "clearing the unsealed controller must reset only the canonical siege state");
        helper.succeed();
    }

    private static void clearCurrentGate(ServerLevel level, PaleMirrorRuntime runtime, TestMineRecord mine, String slot,
                                         SiegeStage expected, GameTestHelper helper) {
        var part = mine.siege().part(slot).orElseThrow();
        LivingEntity entity = (LivingEntity) level.getEntity(part.entityId());
        helper.assertTrue(entity != null && CrimsonSandboxAdapter.isSiegeEntity(entity),
                "gate must be a single registered PM-owned Crimson entity: " + slot);
        entity.die(level.damageSources().generic());
        helper.assertValueEqual(PaleMirrorSavedData.get(level.getServer().overworld()).worldState().facility(mine.id())
                .orElseThrow().siege().stage(), expected, "gate must advance exactly one PM clearance stage: " + slot);
    }

    private static void advanceTier(PaleMirrorRuntime runtime, int steps, int ticks) {
        runtime.advanceSimulation(steps);
        tick(runtime, ticks);
    }

    private static void tick(PaleMirrorRuntime runtime, int count) {
        for (int index = 0; index < count; index++) runtime.tick();
    }

    private static void clearMineVolume(ServerLevel level, BlockPos anchor) {
        for (int x = -4; x <= 4; x++) for (int y = 0; y <= 4; y++) for (int z = -4; z <= 4; z++) {
            level.setBlock(anchor.offset(x, y, z), Blocks.AIR.defaultBlockState(), 3);
        }
    }

    private static void reset(ServerLevel level) {
        PaleMirrorSavedData data = PaleMirrorSavedData.get(level.getServer().overworld());
        data.testMines().clear();
        data.worldRegistry().clear();
        data.audienceMappings().clear();
        data.reconciliationLedger().clear();
        data.worldState().facilities().clear();
        data.worldState().scenarios().clear();
        data.worldState().settlements().clear();
        data.worldState().narratorCooldowns().clear();
        data.worldState().history().clear();
        data.worldState().setSimulationStep(0);
        data.worldState().setEventSequence(0);
        data.setDirty();
    }
}
