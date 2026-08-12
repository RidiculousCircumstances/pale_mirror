package io.farfrontier.palemirror.visuals.gametest;

import io.farfrontier.palemirror.visuals.PaleMirrorVisualsMod;
import io.farfrontier.palemirror.api.VisualPoint;
import io.farfrontier.palemirror.visuals.genesis.FrontierClimate;
import io.farfrontier.palemirror.visuals.genesis.FrontierRegionPlanner;
import io.farfrontier.palemirror.visuals.runtime.AuthoredRegionSeedNbt;
import io.farfrontier.palemirror.visuals.threat.ThreatHeartEntity;
import io.farfrontier.palemirror.visuals.threat.VisualEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(PaleMirrorVisualsMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class VisualsGameTests {
    private VisualsGameTests() { }

    @GameTest(templateNamespace = "pale_mirror_visuals", template = "gametest_empty", timeoutTicks = 20)
    public static void threatHeartPersistsCanonicalVisualIdentity(GameTestHelper helper) {
        ThreatHeartEntity heart = VisualEntityTypes.THREAT_HEART.get().create(helper.getLevel());
        helper.assertTrue(heart != null, "Threat Heart must have a registered entity factory");
        heart.moveTo(helper.absolutePos(new BlockPos(0, 4, 0)), 0, 0);
        heart.facilityId("pale_mirror:test_facility"); heart.setStage(4);
        helper.getLevel().addFreshEntity(heart);
        CompoundTag saved = new CompoundTag(); heart.saveWithoutId(saved);
        ThreatHeartEntity restored = VisualEntityTypes.THREAT_HEART.get().create(helper.getLevel());
        helper.assertTrue(restored != null, "Threat Heart must reload from its exact entity type");
        restored.load(saved);
        helper.assertValueEqual(restored.facilityId(), "pale_mirror:test_facility",
                "visual carrier must persist its canonical facility identity");
        helper.assertValueEqual(restored.stage(), 4, "visual carrier must persist the projected threat stage");
        heart.discard(); helper.succeed();
    }

    @GameTest(templateNamespace = "pale_mirror_visuals", template = "gametest_empty", timeoutTicks = 20)
    public static void authoredManifestRoundTripsExactly(GameTestHelper helper) {
        var seed = new FrontierRegionPlanner().plan(918273L, 1, new VisualPoint(8000, 72, -4000),
                FrontierClimate.COLD_TAIGA);
        helper.assertValueEqual(AuthoredRegionSeedNbt.read(AuthoredRegionSeedNbt.write(seed)), seed,
                "persisted authored manifest must preserve every identity and geometry fact");
        helper.succeed();
    }
}
