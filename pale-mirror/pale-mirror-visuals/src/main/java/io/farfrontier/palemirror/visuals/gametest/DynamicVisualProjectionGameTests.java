package io.farfrontier.palemirror.visuals.gametest;

import io.farfrontier.palemirror.api.AuthoredRegionSeed;
import io.farfrontier.palemirror.api.JourneyProjection;
import io.farfrontier.palemirror.api.JourneyResidentLeaseView;
import io.farfrontier.palemirror.api.VisualPoint;
import io.farfrontier.palemirror.api.VisualStateProjection;
import io.farfrontier.palemirror.visuals.PaleMirrorVisualsMod;
import io.farfrontier.palemirror.visuals.genesis.FrontierClimate;
import io.farfrontier.palemirror.visuals.genesis.FrontierRegionPlanner;
import io.farfrontier.palemirror.visuals.runtime.AuthoredVisualProvider;
import io.farfrontier.palemirror.visuals.resident.ManagedResident;
import io.farfrontier.palemirror.visuals.resident.JourneyProjectionRuntime;
import io.farfrontier.palemirror.visuals.threat.ThreatHeartEntity;
import io.farfrontier.palemirror.visuals.threat.VisualEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.AABB;
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

    @GameTest(templateNamespace = "pale_mirror_visuals", template = "gametest_empty", timeoutTicks = 40)
    public static void retainedJourneyProjectionContinuesMaterializingWithoutCanonicalRepublish(GameTestHelper helper) {
        AuthoredRegionSeed seed = new FrontierRegionPlanner().plan(912838L, 58,
                new VisualPoint(9000, 72, -5000), FrontierClimate.TEMPERATE);
        String journeyId = "pale_mirror:test_retained_journey";
        String residentId = seed.residents().getFirst().residentId();
        BlockPos focus = helper.absolutePos(new BlockPos(2, 2, 2));
        JourneyProjection projection = new JourneyProjection(journeyId, seed.planId(), "pale_mirror:test_group",
                1L, "ARRIVED", 1.0D, 0, 1, 1, false,
                java.util.List.of(new VisualPoint(focus.getX() - 4, focus.getY(), focus.getZ()),
                        new VisualPoint(focus.getX(), focus.getY(), focus.getZ())),
                java.util.List.of(), java.util.List.of(),
                java.util.List.of(new JourneyResidentLeaseView(residentId, "READY", 0, 1L)));

        java.util.concurrent.atomic.AtomicBoolean observed = new java.util.concurrent.atomic.AtomicBoolean(false);
        JourneyProjectionRuntime runtime = new JourneyProjectionRuntime((level, point) -> observed.get());
        runtime.reconcile(helper.getLevel(), projection, seed);
        helper.assertValueEqual(journeyCarriers(helper, focus, journeyId), 0,
                "delivery without an observing player must retain intent without spawning");

        observed.set(true);
        helper.assertTrue(helper.getLevel().hasChunkAt(focus), "the retained journey focus must be loaded");
        helper.assertValueEqual(runtime.tick(helper.getLevel(), java.util.List.of(seed)), 1,
                "one retained resident lease must be physically admitted");
        helper.assertValueEqual(journeyCarriers(helper, focus, journeyId), 1,
                "the retained journey must resume its bounded physical projection without a new revision");
        helper.getLevel().getEntitiesOfClass(Villager.class, new AABB(focus).inflate(16),
                entity -> ManagedResident.isJourneyCarrier(entity, journeyId)).forEach(Villager::discard);
        helper.succeed();
    }

    private static int journeyCarriers(GameTestHelper helper, BlockPos focus, String journeyId) {
        return helper.getLevel().getEntitiesOfClass(Villager.class, new AABB(focus).inflate(16),
                entity -> ManagedResident.isJourneyCarrier(entity, journeyId)).size();
    }

    private static BlockPos block(VisualPoint point) {
        return new BlockPos(point.x(), point.y(), point.z());
    }
}
