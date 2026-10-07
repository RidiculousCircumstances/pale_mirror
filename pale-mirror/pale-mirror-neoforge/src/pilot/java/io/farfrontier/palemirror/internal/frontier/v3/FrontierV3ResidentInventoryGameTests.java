package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.model.ActorKind;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Actual entity save/load, including the portable-food hole that vanilla used to compact. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FrontierV3ResidentInventoryGameTests {
    private FrontierV3ResidentInventoryGameTests() { }

    @GameTest(batch = "pm-frontier-v3-scene-body-lifetime", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void managedPocketAddressesSurviveConsumeUnloadAndReload(GameTestHelper helper) {
        var body = managed(helper);
        body.getInventory().setItem(0, new ItemStack(Items.BREAD, 1));
        body.getInventory().setItem(1, new ItemStack(Items.BREAD, 1));
        body.getInventory().setItem(7, new ItemStack(Items.WHEAT, 3));
        var saved = body.saveWithoutId(new CompoundTag());
        helper.assertFalse(saved.contains("Inventory"), "managed inventory must have only the indexed physical schema");
        var restored = EntityType.VILLAGER.create(helper.getLevel());
        restored.load(saved);
        helper.assertValueEqual(restored.getInventory().getItem(0).getCount(), 1, "equal-kind accounts may not merge");
        helper.assertValueEqual(restored.getInventory().getItem(1).getCount(), 1, "second account retains its address");
        helper.assertValueEqual(restored.getInventory().getItem(7).getCount(), 3, "sparse far pocket remains addressed");
        restored.getInventory().setItem(0, ItemStack.EMPTY);
        restored.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        var departed = restored.saveWithoutId(new CompoundTag());
        var joined = EntityType.VILLAGER.create(helper.getLevel());
        joined.load(departed);
        helper.assertTrue(joined.getInventory().getItem(0).isEmpty(), "consumed food must leave its actual hole");
        helper.assertValueEqual(joined.getInventory().getItem(1).getCount(), 1, "remaining food must not move into the hole");
        helper.assertValueEqual(joined.getInventory().getItem(7).getCount(), 3, "other carried resources survive unload unchanged");
        helper.succeed();
    }

    @GameTest(batch = "pm-frontier-v3-scene-body-lifetime", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void unownedVillagerRetainsVanillaInventoryFormat(GameTestHelper helper) {
        var body = EntityType.VILLAGER.create(helper.getLevel());
        body.getInventory().setItem(1, new ItemStack(Items.BREAD, 1));
        var saved = body.saveWithoutId(new CompoundTag());
        helper.assertTrue(saved.contains("Inventory"), "ordinary Minecraft villagers retain vanilla persistence");
        var restored = EntityType.VILLAGER.create(helper.getLevel());
        restored.load(saved);
        helper.assertValueEqual(restored.getInventory().getItem(0).getCount(), 1, "control proves vanilla actually compacts the sparse slot");
        helper.succeed();
    }

    @GameTest(batch = "pm-frontier-v3-scene-body-lifetime", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void malformedPocketImageFailsBeforeInventoryMutation(GameTestHelper helper) {
        var body = managed(helper);
        body.getInventory().setItem(1, new ItemStack(Items.BREAD, 1));
        var saved = body.saveWithoutId(new CompoundTag());
        saved.getCompound("pmv3_indexed_pockets").putInt("format", 2);
        boolean rejected = false;
        try { FrontierV3ResidentInventoryPersistence.read(body, saved); }
        catch (IllegalArgumentException invalid) { rejected = true; }
        helper.assertTrue(rejected, "foreign pocket schema cannot be guessed");
        helper.assertValueEqual(body.getInventory().getItem(1).getCount(), 1, "rejection leaves physical stock untouched");
        saved.remove("pmv3_indexed_pockets");
        rejected = false;
        try { FrontierV3ResidentInventoryPersistence.read(body, saved); }
        catch (IllegalArgumentException invalid) { rejected = true; }
        helper.assertTrue(rejected, "unaddressed historical managed inventory cannot be heuristically restored");
        helper.succeed();
    }

    private static Villager managed(GameTestHelper helper) {
        var actor = new SubjectId("resident:1-1");
        var body = EntityType.VILLAGER.create(helper.getLevel());
        var uuid = ActorBodyId.entityId(new WorldId("frontier:indexed-pocket-test"), actor);
        body.setUUID(uuid);
        FrontierV3ActorCarrierComposition.stamp(body, new FrontierV3ActorCarrierComposition.Declaration(
                actor, ActorKind.RESIDENT, FrontierV3ActorCarrierComposition.Owner.ACTOR_BODY, uuid,
                FrontierV3ActorCarrierComposition.Representation.LIVE_BODY, 0L, 1L));
        return body;
    }
}
