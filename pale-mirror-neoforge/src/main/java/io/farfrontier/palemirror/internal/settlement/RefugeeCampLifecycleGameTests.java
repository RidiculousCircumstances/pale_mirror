package io.farfrontier.palemirror.internal.settlement;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.api.SemanticSlotKey;
import io.farfrontier.palemirror.domain.PopulationDisposition;
import io.farfrontier.palemirror.domain.WorldObjectId;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Regression coverage for prepared, occupied and returned camp lifecycle. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RefugeeCampLifecycleGameTests {
    private RefugeeCampLifecycleGameTests() { }

    @GameTest(batch = "pm-refugee-camp-lifecycle", templateNamespace = "minecraft",
            template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void preparedCampRetiresOnlyAfterPopulationLeavesAndReturns(GameTestHelper helper) {
        RefugeeCampRecord camp = new RefugeeCampRecord("group", new WorldObjectId("pale_mirror:community"),
                new WorldObjectId("pale_mirror:shelter"), "minecraft:overworld",
                helper.absolutePos(new BlockPos(0, 2, 0)),
                new SemanticSlotKey("pale_mirror:shelter", "camp", "footprint"));

        helper.assertFalse(camp.observe(PopulationDisposition.RESIDENT),
                "a newly prepared camp must remain available while residents are still home");
        helper.assertFalse(camp.retired(), "pre-evacuation residence is not a completed return");
        helper.assertTrue(camp.observe(PopulationDisposition.IN_TRANSIT),
                "the camp must remember that its population actually departed");
        helper.assertFalse(camp.retired(), "an evacuation in transit must keep its destination operational");
        helper.assertFalse(camp.observe(PopulationDisposition.RESETTLED),
                "hosting refugees must not retire their camp");
        helper.assertTrue(camp.observe(PopulationDisposition.RESIDENT),
                "returning home after departure must retire the camp exactly once");
        helper.assertTrue(camp.retired(), "the completed return is the only camp retirement boundary");
        helper.succeed();
    }
}
