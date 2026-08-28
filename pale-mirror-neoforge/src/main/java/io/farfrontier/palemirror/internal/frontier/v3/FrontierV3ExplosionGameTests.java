package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.model.GrayboxCell;
import io.farfrontier.palemirror.frontier.v3.model.GrayboxMaterial;
import io.farfrontier.palemirror.frontier.v3.model.GrayboxSemanticPart;
import io.farfrontier.palemirror.PaleMirrorMod;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Materialized persistence boundary for managed v3 blast candidates, before the executor confirms a receipt. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FrontierV3ExplosionGameTests {
    private FrontierV3ExplosionGameTests() { }

    @GameTest(batch = "pm-frontier-v3-explosion", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void managedBlastCandidatesSurviveReloadAndRetainOneCompletion(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); BlockPos known = helper.absolutePos(new BlockPos(40, 8, 0)); BlockPos unknown = known.east();
        level.setBlock(known.below(), Blocks.STONE.defaultBlockState(), 3); level.setBlock(unknown, Blocks.STONE.defaultBlockState(), 3);
        FrontierV3GrayboxLedger provenance = FrontierV3GrayboxLedger.get(level);
        GrayboxCell organ = new GrayboxCell(new io.farfrontier.palemirror.frontier.v3.model.BlockPosition(known.getX(), known.getY(), known.getZ()),
                new io.farfrontier.palemirror.frontier.v3.api.SubjectId("organ:managed-explosion-test"), GrayboxMaterial.HIVE_HEART, GrayboxSemanticPart.HIVE_TISSUE);
        helper.assertValueEqual(FrontierV3GrayboxExecutor.project(level, provenance, organ), FrontierV3GrayboxExecutor.ProjectionResult.APPLIED,
                "managed explosion starts from one exact owned semantic cell");
        PhysicalIntentId intent = new PhysicalIntentId("intent:managed-explosion-test"); FrontierV3ManagedExplosionLedger ledger = FrontierV3ManagedExplosionLedger.get(level);
        helper.assertTrue(ledger.capture(level, level.getGameTime(), intent, java.util.List.of(known, unknown), provenance, position -> true),
                "managed blast retains exact pre-impact candidates before Minecraft changes them");
        CompoundTag encoded = ledger.save(new CompoundTag(), level.registryAccess()); ledger = FrontierV3ManagedExplosionLedger.load(encoded, level.registryAccess());
        level.setBlock(known, Blocks.AIR.defaultBlockState(), 3); level.setBlock(unknown, Blocks.AIR.defaultBlockState(), 3);
        FrontierV3ManagedExplosionLedger.Ready first = ledger.nextReady(level.getGameTime() + 1L).orElseThrow();
        helper.assertTrue(first.candidate().orElseThrow().semantic().isPresent(), "owned pre-impact provenance survives SavedData reload"); ledger.resolve(first, true);
        FrontierV3ManagedExplosionLedger.Ready second = ledger.nextReady(level.getGameTime() + 1L).orElseThrow();
        helper.assertTrue(second.candidate().orElseThrow().semantic().isEmpty(), "unknown terrain remains a retained scar candidate"); ledger.resolve(second, true);
        FrontierV3ManagedExplosionLedger.Completion completion = ledger.complete(ledger.nextReady(level.getGameTime() + 1L).orElseThrow());
        helper.assertValueEqual(completion.affectedBlockCount(), 2, "completion retains the whole observed blast scope");
        helper.assertValueEqual(completion.changedBlockCount(), 2, "completion retains all changed candidates exactly once");
        helper.assertTrue(!ledger.has(intent), "terminal receipt removes only the completed managed pending record"); helper.succeed();
    }
}
