package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Loaded residency alone never authorizes a route build or structural-repair world mutation. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FrontierV3PhysicalDemandGameTests {
    private FrontierV3PhysicalDemandGameTests() { }

    @GameTest(batch = "pm-frontier-v3-scene-route-construction", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void anOrdinarilyLoadedChunkWithoutAPlayerDoesNotAuthorizePhysicalEffects(GameTestHelper helper) {
        BlockPos position = helper.absolutePos(new BlockPos(0, 8, 0));
        helper.assertTrue(helper.getLevel().hasChunkAt(position), "the GameTest fixture must provide an ordinarily loaded chunk");
        helper.assertFalse(FrontierV3PhysicalDemand.exists(helper.getLevel(), position),
                "chunk residency alone must not authorize a physical route or repair effect");
        helper.succeed();
    }

    /**
     * A later canonical candidate must still materialize when it is the one the player has
     * naturally loaded.  Selecting one global candidate before consulting demand would make a
     * remote field, workshop or patrol suppress every observed process behind it.
     */
    @GameTest(batch = "pm-frontier-v3-scene-route-construction", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void demandSelectionScansTheCompleteStableCandidateInventory(GameTestHelper helper) {
        BlockPos nearby = helper.absolutePos(new BlockPos(0, 8, 0));
        BlockPos unloaded = nearby.offset(256, 0, 0);
        helper.assertTrue(helper.getLevel().hasChunkAt(nearby), "the demanded candidate must be naturally loaded by the fixture");
        helper.assertFalse(helper.getLevel().hasChunkAt(unloaded), "the earlier candidate must remain naturally unloaded; this test may not force-load it");
        var observer = helper.makeMockServerPlayerInLevel();
        observer.setPos(nearby.getX() + 0.5D, nearby.getY(), nearby.getZ() + 0.5D);
        var selected = FrontierV3SceneExecutor.firstDemandedCandidate(helper.getLevel(),
                java.util.List.of(new DemandCandidate("unloaded-first", new BlockPosition(unloaded.getX(), unloaded.getY(), unloaded.getZ())),
                        new DemandCandidate("nearby-second", new BlockPosition(nearby.getX(), nearby.getY(), nearby.getZ()))),
                DemandCandidate::anchor);
        helper.assertTrue(selected.isPresent(), "the demanded later candidate must not be suppressed by the unloaded first candidate");
        helper.assertValueEqual(selected.orElseThrow().id(), "nearby-second", "selection must preserve stable order among physically demanded candidates");
        helper.succeed();
    }

    private record DemandCandidate(String id, BlockPosition anchor) { }

}
