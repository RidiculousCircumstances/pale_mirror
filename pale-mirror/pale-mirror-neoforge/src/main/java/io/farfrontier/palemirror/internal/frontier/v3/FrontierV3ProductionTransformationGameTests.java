package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.ExactItemStack;
import io.farfrontier.palemirror.frontier.v3.model.InventoryCustody;
import io.farfrontier.palemirror.frontier.v3.model.ProductionJob;
import io.farfrontier.palemirror.frontier.v3.model.ProductionTransformationStateSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Loaded-world regression for the production input/output surface boundary. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FrontierV3ProductionTransformationGameTests {
    private FrontierV3ProductionTransformationGameTests() { }

    @GameTest(batch = "pm-frontier-v3-production", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void replacesOnlyOneExactOwnedInputAndLeavesAnInspectableOutput(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); BlockPos chestPosition = helper.absolutePos(new BlockPos(30, 8, 0));
        level.setBlock(chestPosition.below(), Blocks.STONE.defaultBlockState(), 3); level.setBlock(chestPosition, Blocks.CHEST.defaultBlockState(), 3);
        ChestBlockEntity chest = (ChestBlockEntity) level.getBlockEntity(chestPosition);
        SubjectId settlement = new SubjectId("settlement:1"), container = new SubjectId("container:1-depot");
        SubjectId inputId = new SubjectId("item:production-input-game-test"), outputId = new SubjectId("item:production-output-game-test");
        InventoryCustody.ContainerSlot slot = new InventoryCustody.ContainerSlot(container, 0);
        ExactItemStack input = new ExactItemStack(inputId, settlement, "minecraft:wheat", 64, slot);
        ExactItemStack output = new ExactItemStack(outputId, settlement, "minecraft:bread", 64, slot);
        ProductionJob job = new ProductionJob(new SubjectId("job:production-game-test"), settlement, new SubjectId("structure:1-workshop"),
                new SubjectId("resident:1-3"), inputId, outputId, "minecraft:bread", 64);
        ProductionTransformationStateSupport.Target target = new ProductionTransformationStateSupport.Target(job, input, output, slot,
                new BlockPosition(chestPosition.getX(), chestPosition.getY(), chestPosition.getZ()));
        chest.setItem(0, FrontierV3CargoHandoffExecutor.materializedStack(input));

        helper.assertTrue(FrontierV3ProductionTransformationExecutor.replace(chest, target),
                "the executor may transform only the exact owned wheat stack after durable admission");
        helper.assertTrue(FrontierV3ProductionTransformationExecutor.matchesOutput(chest, target),
                "the exact tagged bread is an inspectable postcondition after a crash window");
        chest.setItem(0, FrontierV3CargoHandoffExecutor.materializedStack(input)); chest.getItem(0).shrink(1);
        helper.assertTrue(!FrontierV3ProductionTransformationExecutor.replace(chest, target),
                "an altered player/world stack remains conflict evidence and is never transformed");
        helper.succeed();
    }

    @GameTest(batch = "pm-frontier-v3-production", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void runningProductionRecoveryPrecedesGenericContainerDriftAudit(GameTestHelper helper) {
        var diagnostics = FrontierV3PhysicalExecutors.registry().diagnostics();
        int production = diagnostics.stream().map(FrontierV3PhysicalExecutorRegistry.Diagnostic::id)
                .toList().indexOf("production-transformation");
        int containers = diagnostics.stream().map(FrontierV3PhysicalExecutorRegistry.Diagnostic::id)
                .toList().indexOf("container-surfaces");
        helper.assertTrue(production >= 0 && containers >= 0 && production < containers,
                "a persisted production effect must reconcile before its physical slot is audited as player/world drift");
        helper.assertTrue(diagnostics.stream().filter(value -> value.id().equals("container-surfaces")).findFirst()
                        .orElseThrow().dependencies().contains("production-transformation"),
                "the recovery order must be a declared graph edge, not incidental source order");
        helper.succeed();
    }
}
