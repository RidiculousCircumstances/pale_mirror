package io.farfrontier.palemirror.gametest;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.domain.FacilityState;
import io.farfrontier.palemirror.domain.InfectionSourceId;
import io.farfrontier.palemirror.domain.StoryAudienceId;
import io.farfrontier.palemirror.domain.WorldObjectId;
import io.farfrontier.palemirror.internal.materialization.JobState;
import io.farfrontier.palemirror.internal.materialization.MaterializationJob;
import io.farfrontier.palemirror.internal.materialization.MaterializationJobClass;
import io.farfrontier.palemirror.internal.materialization.OperationState;
import io.farfrontier.palemirror.internal.materialization.TestMineMaterializationTranslator;
import io.farfrontier.palemirror.internal.materialization.TestMineMaterializer;
import io.farfrontier.palemirror.internal.world.EncounterRecord;
import io.farfrontier.palemirror.internal.world.InfectionBiomeStage;
import io.farfrontier.palemirror.internal.world.MutableCell;
import io.farfrontier.palemirror.internal.world.PaleMirrorSavedData;
import io.farfrontier.palemirror.internal.world.TestMineRecord;
import io.farfrontier.palemirror.internal.world.WorldObjectLifecycle;
import io.farfrontier.palemirror.internal.world.WorldObjectRegistryEntry;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.ArrayList;

@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DeferredInfectionMaterializationGameTests {
    private DeferredInfectionMaterializationGameTests() { }

    @SuppressWarnings("removal")
    @GameTest(batch = "pm-passive-infection-deferred", templateNamespace = "minecraft",
            template = "bastion/mobs/empty", timeoutTicks = 200)
    public static void infectionOverlayWaitsForItsChunkAndAppliesEachCellOnce(GameTestHelper helper) {
        if (GameTestProfiles.createAdapterOnly()) { helper.succeed(); return; }
        ServerLevel level = helper.getLevel();
        BlockPos anchor = helper.absolutePos(new BlockPos(0, 3, 0));
        BlockPos deferredCell = anchor.offset(2048, 0, 0);
        helper.assertTrue(!level.hasChunkAt(deferredCell),
                "the recovery test requires an initially unloaded overlay chunk");

        WorldObjectId id = new WorldObjectId("pale_mirror:deferred_overlay_mine");
        MutableCell cell = new MutableCell(deferredCell, MutableCell.UNOBSERVED_BASELINE,
                MutableCell.UNOBSERVED_BASELINE, false,
                InfectionBiomeStage.FOOTHOLD);
        WorldObjectRegistryEntry object = new WorldObjectRegistryEntry(id,
                level.dimension().location().toString(), anchor, anchor, deferredCell,
                "pale_mirror:deferred_overlay_test", "1", WorldObjectLifecycle.REPRESENTED);
        TestMineRecord mine = new TestMineRecord(object, StoryAudienceId.globalTestAudience(), java.util.List.of(cell),
                null, EncounterRecord.none(), null);
        FacilityState facility = new FacilityState(id, new InfectionSourceId("pale_mirror:crimson"), 80, 10, 10);
        facility.infect();
        var plan = new TestMineMaterializationTranslator().translate(facility);
        MaterializationJob job = new MaterializationJob("pm:job:deferred-overlay", id.value(), "threat",
                MaterializationJobClass.CAPABILITY, facility.desiredRevision(), plan.policyId(),
                plan.policyVersion(), JobState.PLANNED, plan.operations(), 0, 0, "");
        TestMineMaterializer materializer = new TestMineMaterializer();
        PaleMirrorSavedData data = PaleMirrorSavedData.get(level.getServer().overworld());

        helper.assertTrue(!materializer.executeNext(level, data, mine, facility, job),
                "the visible controller operation completes before sparse overlay work");
        helper.assertValueEqual(job.operations().getFirst().state(), OperationState.COMPLETED,
                "the nearby visible controller must not wait for a remote overlay chunk");
        helper.assertTrue(!materializer.executeNext(level, data, mine, facility, job),
                "an unavailable chunk must defer rather than complete the overlay");
        helper.assertValueEqual(job.operations().getLast().state(), OperationState.RUNNING,
                "the exact persisted operation must remain pending physical completion");
        helper.assertValueEqual(cell.lastAppliedBlock(), MutableCell.UNOBSERVED_BASELINE,
                "deferred work must not claim an unseen block");

        level.getChunk(deferredCell.getX() >> 4, deferredCell.getZ() >> 4);
        level.setBlock(deferredCell, Blocks.STONE.defaultBlockState(), 3);
        helper.assertTrue(level.hasChunkAt(deferredCell), "the test chunk must now be naturally inspectable");
        helper.assertTrue(materializer.executeNext(level, data, mine, facility, job),
                "the final sparse overlay completes after its chunk becomes available");
        helper.assertValueEqual(job.operations().getLast().state(), OperationState.COMPLETED,
                "the retained operation must complete after chunk availability");
        helper.assertValueEqual(cell.baselineBlock(), "minecraft:stone",
                "the immutable physical baseline must be captured on first natural chunk availability");
        helper.assertValueEqual(level.getBlockState(deferredCell).getBlock(), Blocks.NETHERRACK,
                "the first infection stage must materialize after the chunk becomes available");
        helper.assertValueEqual(cell.lastAppliedBlock(), "minecraft:netherrack",
                "the one physical application must receive durable cell ownership");
        helper.succeed();
    }

    @SuppressWarnings("removal")
    @GameTest(batch = "pm-passive-infection-deferred", templateNamespace = "minecraft",
            template = "bastion/mobs/empty", timeoutTicks = 200)
    public static void authoredSizedOverlayReconcilesIncrementallyWithoutBlocking(GameTestHelper helper) {
        if (GameTestProfiles.createAdapterOnly()) { helper.succeed(); return; }
        ServerLevel level = helper.getLevel();
        BlockPos anchor = helper.absolutePos(new BlockPos(0, 3, 0));
        ArrayList<MutableCell> cells = new ArrayList<>();
        for (int y = 0; y < 2 && cells.size() < TestMineRecord.MAX_MUTABLE_CELLS; y++) {
            for (int x = 0; x < 16 && cells.size() < TestMineRecord.MAX_MUTABLE_CELLS; x++) {
                for (int z = 0; z < 12 && cells.size() < TestMineRecord.MAX_MUTABLE_CELLS; z++) {
                    BlockPos position = anchor.offset(x, y, z);
                    level.setBlock(position, Blocks.STONE.defaultBlockState(), 3);
                    cells.add(new MutableCell(position, "minecraft:stone", "minecraft:stone", false,
                            InfectionBiomeStage.FOOTHOLD));
                }
            }
        }
        helper.assertValueEqual(cells.size(), TestMineRecord.MAX_MUTABLE_CELLS,
                "the regression fixture must match the authored MineSite persistence bound");

        WorldObjectId id = new WorldObjectId("pale_mirror:authored_sized_overlay_mine");
        WorldObjectRegistryEntry object = new WorldObjectRegistryEntry(id,
                level.dimension().location().toString(), anchor, anchor,
                anchor.offset(15, 1, 11), "pale_mirror:authored_overlay_test", "1",
                WorldObjectLifecycle.REPRESENTED);
        TestMineRecord mine = new TestMineRecord(object, StoryAudienceId.globalTestAudience(), cells,
                null, EncounterRecord.none(), null);
        FacilityState facility = new FacilityState(id, new InfectionSourceId("pale_mirror:crimson"), 80, 10, 10);
        facility.infect();
        var plan = new TestMineMaterializationTranslator().translate(facility);
        MaterializationJob job = new MaterializationJob("pm:job:authored-sized-overlay", id.value(), "threat",
                MaterializationJobClass.CAPABILITY, facility.desiredRevision(), plan.policyId(),
                plan.policyVersion(), JobState.PLANNED, plan.operations(), 0, 0, "");
        TestMineMaterializer materializer = new TestMineMaterializer();
        PaleMirrorSavedData data = PaleMirrorSavedData.get(level.getServer().overworld());

        helper.assertTrue(!materializer.executeNext(level, data, mine, facility, job),
                "the visible controller must complete before the large overlay");
        helper.assertTrue(!materializer.executeNext(level, data, mine, facility, job),
                "the first bounded pass must leave an authored-sized overlay running");
        helper.assertValueEqual(job.operations().getLast().state(), OperationState.RUNNING,
                "the overlay must defer rather than block at the old 70-cell limit");
        helper.assertValueEqual(appliedCells(cells), 16,
                "one invocation must reconcile exactly the bounded cell budget");

        int invocations = 1;
        while (job.operations().getLast().state() != OperationState.COMPLETED && invocations++ < 40) {
            materializer.executeNext(level, data, mine, facility, job);
        }
        helper.assertValueEqual(job.operations().getLast().state(), OperationState.COMPLETED,
                "all authored infection cells must eventually reconcile");
        helper.assertValueEqual(appliedCells(cells), TestMineRecord.MAX_MUTABLE_CELLS,
                "every authored infection cell must have durable PM ownership");
        helper.assertTrue(job.state() != JobState.BLOCKED,
                "an authored-sized overlay must never trigger the former bounded-cell blocker");
        helper.succeed();
    }

    private static int appliedCells(java.util.List<MutableCell> cells) {
        return (int) cells.stream().filter(cell -> cell.lastAppliedBlock().equals("minecraft:netherrack")).count();
    }
}
