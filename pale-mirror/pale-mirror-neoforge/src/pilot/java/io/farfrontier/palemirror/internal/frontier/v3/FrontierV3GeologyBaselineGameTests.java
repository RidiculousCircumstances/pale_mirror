package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.internal.world.GeneratedGeology;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import java.util.HashMap;

/** The native block registry owns block-state codecs; no fake Forge bootstrap in a unit JVM. */
@GameTestHolder("pale_mirror")
@net.neoforged.neoforge.gametest.PrefixGameTestTemplate(false)
public final class FrontierV3GeologyBaselineGameTests {
    private FrontierV3GeologyBaselineGameTests() { }
    @GameTest(batch = "pm-frontier-v3-shared-storage", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 40)
    public static void immutableNativeBaselineRoundTripsWithExactBlockProperties(GameTestHelper helper) {
        var mutable = new BlockPos.MutableBlockPos(-121, 62, 43);
        var blocks = new HashMap<BlockPos, net.minecraft.world.level.block.state.BlockState>();
        blocks.put(mutable, Blocks.COAL_ORE.defaultBlockState());
        var baseline = new GeneratedGeology("test:provider", "configuration", 125, blocks);
        mutable.set(0, 0, 0); blocks.clear();
        helper.assertTrue(baseline.blocks().get(new BlockPos(-121, 62, 43)).is(Blocks.COAL_ORE), "inputs cannot mutate generation history");
        var bytes = GeneratedGeology.CODEC.encodeStart(NbtOps.INSTANCE, baseline).getOrThrow();
        var recovered = GeneratedGeology.CODEC.parse(NbtOps.INSTANCE, bytes).getOrThrow();
        helper.assertTrue(baseline.equals(recovered), "native codec preserves the exact baseline");
        var malformed = ((net.minecraft.nbt.CompoundTag) bytes).copy();
        var retainedBlocks = malformed.getCompound("blocks");
        retainedBlocks.put("bad-address", retainedBlocks.get(retainedBlocks.getAllKeys().iterator().next()));
        helper.assertTrue(GeneratedGeology.CODEC.parse(NbtOps.INSTANCE, malformed).error().isPresent(), "malformed address must fail visibly");
        helper.succeed();
    }
}
