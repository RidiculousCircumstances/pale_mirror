package io.farfrontier.palemirror.visuals.gametest;

import io.farfrontier.palemirror.api.AuthoredRegionSeed;
import io.farfrontier.palemirror.api.VisualPoint;
import io.farfrontier.palemirror.api.VisualStateProjection;
import io.farfrontier.palemirror.visuals.PaleMirrorVisualsMod;
import io.farfrontier.palemirror.visuals.genesis.FrontierClimate;
import io.farfrontier.palemirror.visuals.genesis.FrontierRegionPlanner;
import io.farfrontier.palemirror.visuals.runtime.AuthoredVisualProvider;
import io.farfrontier.palemirror.visuals.threat.ThreatHeartEntity;
import io.farfrontier.palemirror.visuals.threat.VisualEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(PaleMirrorVisualsMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DynamicVisualProjectionGameTests {
    private DynamicVisualProjectionGameTests() { }

    @GameTest(templateNamespace = "pale_mirror_visuals", template = "gametest_empty", timeoutTicks = 20)
    public static void retainedProjectionReconcilesAfterItsPhysicalCarrierBecomesAvailable(GameTestHelper helper) {
        AuthoredRegionSeed seed = new FrontierRegionPlanner().plan(912837L, 57,
                new VisualPoint(8000, 72, -4000), FrontierClimate.TEMPERATE);
        AuthoredVisualProvider.INSTANCE.markers().observe(seed);
        String facilityId = seed.planId() + "_mine";
        VisualStateProjection projection = new VisualStateProjection(facilityId, 4L, 991L,
                "INFECTED", "INTACT", "UNAVAILABLE", "CRITICAL", "0", "SIEGE", "UNKNOWN");

        // Delivery happens while the authored MineSite itself is not loaded.
        helper.assertTrue(!helper.getLevel().hasChunkAt(block(seed.primaryMineSite().portal())),
                "the test MineSite must remain outside the loaded GameTest area");
        AuthoredVisualProvider.INSTANCE.applyProjection(helper.getLevel(), projection);

        ThreatHeartEntity heart = VisualEntityTypes.THREAT_HEART.get().create(helper.getLevel());
        helper.assertTrue(heart != null, "Threat Heart must have a registered entity factory");
        heart.moveTo(helper.absolutePos(new BlockPos(0, 4, 0)), 0, 0);
        heart.facilityId(facilityId);
        heart.setStage(1);
        helper.getLevel().addFreshEntity(heart);

        AuthoredVisualProvider.INSTANCE.tickVisuals(helper.getLevel());
        helper.assertValueEqual(heart.stage(), 3,
                "the retained desired projection must reconcile without a new canonical revision");
        heart.discard();
        helper.succeed();
    }

    private static BlockPos block(VisualPoint point) {
        return new BlockPos(point.x(), point.y(), point.z());
    }
}
