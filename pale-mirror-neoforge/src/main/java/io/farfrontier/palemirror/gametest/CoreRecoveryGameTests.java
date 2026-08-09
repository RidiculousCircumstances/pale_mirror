package io.farfrontier.palemirror.gametest;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.domain.FacilityStatus;
import io.farfrontier.palemirror.domain.ScenarioStatus;
import io.farfrontier.palemirror.domain.SettlementState;
import io.farfrontier.palemirror.internal.PaleMirrorRuntime;
import io.farfrontier.palemirror.internal.world.MutableCell;
import io.farfrontier.palemirror.internal.world.PaleMirrorSavedData;
import io.farfrontier.palemirror.internal.world.TestMineRecord;
import io.farfrontier.palemirror.internal.world.WorldObjectLifecycle;
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

/** Exercises the core slice against a real NeoForge server level and a mock server player. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CoreRecoveryGameTests {
    private CoreRecoveryGameTests() { }

    @SuppressWarnings("removal")
    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 100)
    public static void testMineRecoversAfterObservedControllerDeath(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        PaleMirrorRuntime runtime = PaleMirrorRuntime.forServer(level.getServer());
        BlockPos anchor = helper.absolutePos(new BlockPos(0, 3, 0));
        resetPaleMirrorState(level);
        clearMineVolume(level, anchor);

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setPos(anchor.getX() + 0.5, anchor.getY() - 2, anchor.getZ() + 0.5);
        TestMineRecord mine = runtime.createTestMine(player);

        runtime.advanceSimulation(1);
        SettlementState settlement = PaleMirrorSavedData.get(level.getServer().overworld()).worldState().settlements().stream()
                .findFirst().orElseThrow();
        helper.assertValueEqual(settlement.supplyDisrupted(), true, "mine infection must disrupt settlement iron supply");
        helper.assertValueEqual(settlement.currentDefense(), 30, "supply disruption must lower settlement defense");
        String scenarioId = runtime.offered(runtime.audienceFor(player)).getFirst().id();
        helper.assertTrue(runtime.accept(scenarioId, runtime.audienceFor(player)), "scenario must be accepted by its audience");

        player.setPos(anchor.getX() + 0.5, anchor.getY() + 2, anchor.getZ() + 0.5);
        tick(runtime, 2);
        CompoundTag persisted = PaleMirrorSavedData.get(level.getServer().overworld()).save(new CompoundTag(), level.registryAccess());
        PaleMirrorSavedData reloaded = PaleMirrorSavedData.load(persisted, level.registryAccess());
        helper.assertValueEqual(reloaded.testMines().get(mine.id()).job().nextOperationIndex(), 1,
                "restart snapshot must retain completed operation progress");
        helper.assertValueEqual(reloaded.testMines().get(mine.id()).job().operations().getFirst().state().name(), "COMPLETED",
                "restart snapshot must retain operation postcondition state");
        tick(runtime, 1);

        helper.assertValueEqual(PaleMirrorSavedData.get(level.getServer().overworld()).worldState()
                .facility(mine.id()).orElseThrow().status(), FacilityStatus.INFECTED, "facility status after materialization");
        helper.assertValueEqual(PaleMirrorSavedData.get(level.getServer().overworld()).worldState()
                .scenario(scenarioId).orElseThrow().status(), ScenarioStatus.RECOVER, "scenario status after entering mine");
        helper.assertTrue(mine.controllerId() != null, "TestThreat controller must be materialized exactly once");
        helper.assertValueEqual(mine.object().lifecycle(), WorldObjectLifecycle.ACTIVE,
                "registry must record the active physical representation");
        helper.assertTrue(mine.mutableCells().stream().allMatch(cell -> level.getBlockState(cell.position()).is(Blocks.NETHERRACK)),
                "all PM-owned overlay cells must be materialized");

        LivingEntity controller = (LivingEntity) level.getEntity(mine.controllerId());
        helper.assertTrue(controller != null, "materialized controller must be present by its registered UUID");
        controller.die(level.damageSources().generic());

        helper.assertValueEqual(PaleMirrorSavedData.get(level.getServer().overworld()).worldState()
                .facility(mine.id()).orElseThrow().status(), FacilityStatus.RECOVERING, "facility status after observed controller death");
        runtime.advanceSimulation(1);
        tick(runtime, 3);

        helper.assertValueEqual(PaleMirrorSavedData.get(level.getServer().overworld()).worldState()
                .facility(mine.id()).orElseThrow().status(), FacilityStatus.OPERATIONAL, "facility status after recovery simulation");
        helper.assertValueEqual(PaleMirrorSavedData.get(level.getServer().overworld()).worldState()
                .scenario(scenarioId).orElseThrow().status(), ScenarioStatus.RESOLVED, "scenario must resolve exactly once");
        helper.assertTrue(mine.controllerId() == null, "destroyed controller reference must be cleared");
        helper.assertValueEqual(mine.object().lifecycle(), WorldObjectLifecycle.REPRESENTED,
                "registry must retain the mine after its active threat is removed");
        helper.assertValueEqual(PaleMirrorSavedData.get(level.getServer().overworld()).worldState()
                .facility(mine.id()).orElseThrow().observedRevision(), 3L,
                "only a verified materialization job may advance the observed revision");
        helper.assertValueEqual(settlement.supplyDisrupted(), false, "recovered mine must restore settlement supply");
        helper.assertValueEqual(settlement.currentDefense(), 40, "recovered mine must restore settlement defense");
        helper.assertTrue(mine.mutableCells().stream().allMatch(cell -> level.getBlockState(cell.position()).is(Blocks.DEEPSLATE_BRICKS)),
                "overlay cleanup must restore only PM-owned baseline cells");
        helper.succeed();
    }

    private static void clearMineVolume(ServerLevel level, BlockPos anchor) {
        for (int x = -4; x <= 4; x++) {
            for (int y = 0; y <= 4; y++) {
                for (int z = -4; z <= 4; z++) {
                    level.setBlock(anchor.offset(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
    }

    private static void resetPaleMirrorState(ServerLevel level) {
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

    private static void tick(PaleMirrorRuntime runtime, int count) {
        for (int index = 0; index < count; index++) runtime.tick();
    }
}
