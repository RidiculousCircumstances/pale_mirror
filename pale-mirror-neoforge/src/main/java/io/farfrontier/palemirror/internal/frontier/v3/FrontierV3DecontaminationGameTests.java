package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.model.DecontaminationPolicy;
import io.farfrontier.palemirror.frontier.v3.model.ExactItemStack;
import io.farfrontier.palemirror.frontier.v3.model.FrontierBootstrapper;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.InfectionCell;
import io.farfrontier.palemirror.frontier.v3.model.InfectionOverlayStage;
import io.farfrontier.palemirror.frontier.v3.model.InventoryCustody;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Optional;
import java.util.Map;

/** Materialized exact-reagent and overlay-provenance checks for v3 decontamination. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FrontierV3DecontaminationGameTests {
    private FrontierV3DecontaminationGameTests() { }

    @GameTest(batch = "pm-frontier-v3-decontamination", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void reagentReducesAndThenClearsOnlyItsOwnedInfectionMarker(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); BlockPos marker = helper.absolutePos(new BlockPos(30, 8, 0)), chestPosition = marker.east(2);
        level.setBlock(chestPosition.below(), Blocks.STONE.defaultBlockState(), 3); level.setBlock(chestPosition, Blocks.CHEST.defaultBlockState(), 3);
        ChestBlockEntity chest = (ChestBlockEntity) level.getBlockEntity(chestPosition); InfectionCell cell = new InfectionCell(7, 9);
        SubjectId container = new SubjectId("container:decontamination-game-test"), itemId = new SubjectId("item:decontamination-game-test");
        ExactItemStack two = new ExactItemStack(itemId, new SubjectId("settlement:1"), DecontaminationPolicy.REAGENT, 2, new InventoryCustody.ContainerSlot(container, 0));
        chest.setItem(0, FrontierV3CargoHandoffExecutor.materializedStack(two)); level.setBlock(marker, FrontierV3InfectionOverlayExecutor.material(InfectionOverlayStage.BLOOM), 3);
        FrontierV3InfectionOverlayLedger ledger = FrontierV3InfectionOverlayLedger.get(level); ledger.applied(cell, marker, InfectionOverlayStage.BLOOM);
        FrontierV3DecontaminationExecutor.Target reduce = new FrontierV3DecontaminationExecutor.Target(cell, marker, two, container, 0, chestPosition,
                500_000L, 250_000L, InfectionOverlayStage.BLOOM, Optional.of(InfectionOverlayStage.INFESTED));
        CompoundTag beforeReduction = ledger.save(new CompoundTag(), level.registryAccess());
        helper.assertTrue(FrontierV3DecontaminationExecutor.applyOne(level, ledger, reduce, chest), "one exact reagent reduces its owned marker");
        helper.assertTrue(chest.getItem(0).getCount() == 1 && level.getBlockState(marker).equals(FrontierV3InfectionOverlayExecutor.material(InfectionOverlayStage.INFESTED)),
                "the physical stack and marker severity change together");
        ledger = FrontierV3InfectionOverlayLedger.load(beforeReduction, level.registryAccess());
        helper.assertTrue(FrontierV3DecontaminationExecutor.recoverLedgerPostcondition(level, ledger, reduce)
                        && ledger.claim(cell).stage() == InfectionOverlayStage.INFESTED,
                "restart reconstructs only the owned stage when the block and exact consumed stack prove the reduction");
        ExactItemStack one = new ExactItemStack(itemId, new SubjectId("settlement:1"), DecontaminationPolicy.REAGENT, 1, new InventoryCustody.ContainerSlot(container, 0));
        FrontierV3DecontaminationExecutor.Target clear = new FrontierV3DecontaminationExecutor.Target(cell, marker, one, container, 0, chestPosition,
                250_000L, 0L, InfectionOverlayStage.INFESTED, Optional.empty());
        CompoundTag beforeClear = ledger.save(new CompoundTag(), level.registryAccess());
        helper.assertTrue(FrontierV3DecontaminationExecutor.applyOne(level, ledger, clear, chest), "the final exact reagent clears the owned marker");
        helper.assertTrue(chest.getItem(0).isEmpty() && level.getBlockState(marker).isAir(), "the final exact reagent clears only its owned marker");
        ledger = FrontierV3InfectionOverlayLedger.load(beforeClear, level.registryAccess());
        helper.assertTrue(FrontierV3DecontaminationExecutor.recoverLedgerPostcondition(level, ledger, clear) && ledger.claim(cell).cleared(),
                "restart reconstructs a cleared-owned lease before canonical confirmation");
        CompoundTag serialized = ledger.save(new CompoundTag(), level.registryAccess());
        ledger = FrontierV3InfectionOverlayLedger.load(serialized, level.registryAccess());
        helper.assertTrue(ledger.claim(cell).cleared(), "a restart retains the cleared-marker lease for postcondition inspection");
        helper.assertTrue(FrontierV3InfectionOverlayExecutor.reconcileRetraction(level, ledger, Map.entry(cell, ledger.claim(cell)),
                        FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:decontamination-game-test"), 104L)))
                        == FrontierV3InfectionOverlayExecutor.ProjectionResult.RETRACTED && ledger.claim(cell) == null,
                "confirmed retraction frees the owned air-baseline claim after recovery");
        helper.succeed();
    }
}
