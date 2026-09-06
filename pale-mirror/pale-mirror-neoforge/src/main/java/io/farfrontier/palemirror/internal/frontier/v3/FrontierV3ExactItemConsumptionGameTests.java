package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.api.FixedPosition;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalPostcondition;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.model.ContainerSurfaceStatus;
import io.farfrontier.palemirror.frontier.v3.model.ExactItemStack;
import io.farfrontier.palemirror.frontier.v3.model.FrontierBootstrapper;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.InventoryCustody;
import io.farfrontier.palemirror.frontier.v3.model.ResidentBirthJob;
import io.farfrontier.palemirror.frontier.v3.model.ResidentProfile;
import io.farfrontier.palemirror.frontier.v3.model.ResidentRole;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Loaded-world evidence for the non-replayable exact-count consumption boundary. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FrontierV3ExactItemConsumptionGameTests {
    private FrontierV3ExactItemConsumptionGameTests() { }

    @GameTest(batch = "pm-frontier-v3-exact-consumption", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void consumesOnlyTheExactTaggedStackAndLeavesARecoverableEmptySlot(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); BlockPos chestPosition = helper.absolutePos(new BlockPos(30, 8, 0));
        level.setBlock(chestPosition.below(), Blocks.STONE.defaultBlockState(), 3); level.setBlock(chestPosition, Blocks.CHEST.defaultBlockState(), 3);
        ChestBlockEntity chest = (ChestBlockEntity) level.getBlockEntity(chestPosition);
        SubjectId container = new SubjectId("container:exact-consumption-game-test"), itemId = new SubjectId("item:exact-consumption-game-test");
        ExactItemStack item = new ExactItemStack(itemId, new SubjectId("organ:west-store"), "minecraft:rotten_flesh", 64, new InventoryCustody.ContainerSlot(container, 0));
        FrontierV3ExactItemConsumptionExecutor.Target target = new FrontierV3ExactItemConsumptionExecutor.Target(item, container, 0, chestPosition, 64);
        chest.setItem(0, FrontierV3CargoHandoffExecutor.materializedStack(item));
        helper.assertTrue(FrontierV3ExactItemConsumptionExecutor.consume(chest, target), "the identity-tagged canonical stack is physically consumed");
        helper.assertTrue(FrontierV3ExactItemConsumptionExecutor.consumed(chest, target), "an empty exact slot is an inspectable restart postcondition");
        chest.setItem(0, FrontierV3CargoHandoffExecutor.materializedStack(item)); chest.getItem(0).shrink(1);
        helper.assertTrue(!FrontierV3ExactItemConsumptionExecutor.consume(chest, target), "an altered stack is conflict evidence and is never consumed");
        helper.succeed();
    }

    @GameTest(batch = "pm-frontier-v3-exact-consumption", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void birthFoodUsesTheSameGenericExactConsumptionExecutor(GameTestHelper helper) {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:birth-consumption-game-test"), 91L));
        var settlement = state.bootstrap().settlements().getFirst(); SubjectId depot = FrontierWorldState.depotId(settlement.id());
        SubjectId food = new SubjectId("item:birth-consumption-game-test");
        state = state.withInventory(state.inventory().withSurfaceStatus(depot, ContainerSurfaceStatus.PREPARED).withSurfaceStatus(depot, ContainerSurfaceStatus.ACTIVE)
                .store(new ExactItemStack(food, settlement.id(), "minecraft:bread", 64, new InventoryCustody.ContainerSlot(depot, 1))));
        ResidentProfile parent = state.humanPopulation().resident(new SubjectId("resident:1-1"));
        ResidentProfile newborn = new ResidentProfile(new SubjectId("resident:1-born-game-test"), parent.householdId(), settlement.id(), ResidentRole.FARMER, 200L, parent.skills());
        ResidentBirthJob job = new ResidentBirthJob(new SubjectId("job:resident-birth-game-test"), settlement.id(), parent.householdId(), food,
                new PhysicalIntentId("intent:resident-birth-food-game-test"), newborn, settlement.anchor());
        PhysicalIntent intent = new PhysicalIntent(job.consumptionIntentId(), PhysicalIntentKind.EXACT_ITEM_CONSUMPTION, PhysicalIntentStatus.PREPARED,
                job.id(), java.util.List.of(job.id(), food), new FixedPosition(FixedScalar.whole(settlement.anchor().x()), FixedScalar.whole(settlement.anchor().y()),
                FixedScalar.whole(settlement.anchor().z())), 0, PhysicalPostcondition.EXACT_ITEM_CONSUMED_OBSERVED);
        state = state.startResidentBirth(job).preparePhysicalIntent(intent);
        FrontierV3ExactItemConsumptionExecutor.Target target = FrontierV3ExactItemConsumptionExecutor.target(state, intent);
        helper.assertTrue(target != null && target.item().id().equals(food) && target.containerId().equals(depot),
                "the generic executor resolves the active settlement-owned birth food rather than a hive-only target");

        BlockPos chestPosition = helper.absolutePos(new BlockPos(30, 8, 0)); ServerLevel level = helper.getLevel();
        level.setBlock(chestPosition.below(), Blocks.STONE.defaultBlockState(), 3); level.setBlock(chestPosition, Blocks.CHEST.defaultBlockState(), 3);
        ChestBlockEntity chest = (ChestBlockEntity) level.getBlockEntity(chestPosition);
        FrontierV3ExactItemConsumptionExecutor.Target testTarget = new FrontierV3ExactItemConsumptionExecutor.Target(target.item(), target.containerId(), target.slot(), chestPosition, target.count());
        chest.setItem(target.slot(), FrontierV3CargoHandoffExecutor.materializedStack(target.item()));
        helper.assertTrue(target.count() == 1 && FrontierV3ExactItemConsumptionExecutor.consume(chest, testTarget), "the exact tagged birth food consumes one real bread item");
        helper.assertTrue(FrontierV3ExactItemConsumptionExecutor.consumed(chest, testTarget) && chest.getItem(target.slot()).getCount() == 63,
                "the retained tag and exact physical remainder make restart recovery inspectable");
        helper.succeed();
    }
}
