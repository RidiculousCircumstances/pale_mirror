package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FrontierV3HandEvidenceGameTests {
    @GameTest(batch = "pm-frontier-v3-hand-evidence", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void usedEquipmentHasTheSameLiveAndVanillaSavedHandEvidence(GameTestHelper helper) {
        var tool = new ItemStack(Items.STONE_PICKAXE);
        FrontierV3ExactItemPresentation.bindExactItemId(tool, new SubjectId("item:used-pick"));
        tool.set(DataComponents.MAX_DAMAGE, 200); tool.set(DataComponents.MAX_STACK_SIZE, 1); tool.setDamageValue(18);
        helper.assertTrue(FrontierV3ActorHandEvidence.capturable(tool), "a usable worn tool must not strand its owner at unload");
        var captured = FrontierV3ActorHandEvidence.observed(tool);
        var saved = (CompoundTag) tool.save(helper.getLevel().registryAccess());
        helper.assertTrue(captured.equals(FrontierV3ActorHandEvidence.saved(saved)), "vanilla save must preserve the exact condition patch");
        saved.getCompound("components").putInt("minecraft:damage", 19);
        helper.assertTrue(!captured.equals(FrontierV3ActorHandEvidence.saved(saved)), "different wear cannot certify the captured hand");
        tool.set(DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("unsupported"));
        helper.assertTrue(!FrontierV3ActorHandEvidence.capturable(tool), "unknown components remain explicit unsupported evidence");
        helper.succeed();
    }
    private FrontierV3HandEvidenceGameTests() { }
}
