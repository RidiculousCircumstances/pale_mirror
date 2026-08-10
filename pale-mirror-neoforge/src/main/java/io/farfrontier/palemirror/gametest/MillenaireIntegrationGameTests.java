package io.farfrontier.palemirror.gametest;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.api.AdapterHealth;
import io.farfrontier.palemirror.api.Capability;
import io.farfrontier.palemirror.internal.adapter.AdapterRegistry;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Runtime contract check; reflection and native identities remain inside the integration package. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MillenaireIntegrationGameTests {
    private MillenaireIntegrationGameTests() { }

    @GameTest(batch = "pm-millenaire-read-only", templateNamespace = "minecraft",
            template = "bastion/mobs/empty", timeoutTicks = 40)
    public static void pinnedReadOnlySurfaceIsAvailable(GameTestHelper helper) throws Exception {
        if (!Boolean.getBoolean("pale_mirror.millenaire_adapter_only")) { helper.succeed(); return; }
        var adapter = AdapterRegistry.settlementAdapters().stream()
                .filter(value -> value.id().equals("pale_mirror:millenaire_settlement_observer"))
                .findFirst().orElseThrow();
        AdapterHealth health = adapter.health();
        helper.assertValueEqual(health.status(), AdapterHealth.Status.AVAILABLE,
                "the checksum-pinned native settlement read surface must pass self-check");
        helper.assertTrue(health.capabilities().contains(Capability.SETTLEMENT_NATIVE_RECONCILIATION),
                "the adapter must declare its restricted reconciliation capability");
        var level = helper.getLevel();
        java.util.UUID fixtureId = java.util.UUID.fromString("a9177275-f617-43bb-a587-13c2ed51a42d");
        ClassLoader loader = MillenaireIntegrationGameTests.class.getClassLoader();
        Class<?> idType = Class.forName("org.millenaire.village.VillageId", false, loader);
        Object nativeId = idType.getConstructor(java.util.UUID.class).newInstance(fixtureId);
        Class<?> villageType = Class.forName("org.millenaire.village.Village", false, loader);
        net.minecraft.core.BlockPos center = helper.absolutePos(new net.minecraft.core.BlockPos(0, 2, 0));
        Object village = villageType.getConstructor(idType, net.minecraft.resources.ResourceLocation.class,
                        net.minecraft.resources.ResourceLocation.class, net.minecraft.core.BlockPos.class)
                .newInstance(nativeId, net.minecraft.resources.ResourceLocation.parse("millenaire:norman"),
                        net.minecraft.resources.ResourceLocation.parse("millenaire:norman/hamlet"), center);
        Class<?> savedType = Class.forName("org.millenaire.village.VillageSavedData", false, loader);
        Object saved = savedType.getMethod("get", net.minecraft.server.level.ServerLevel.class).invoke(null, level);
        Object manager = savedType.getMethod("getVillageManager").invoke(saved);
        manager.getClass().getMethod("addVillage", villageType).invoke(manager, village);
        try {
            int recordsBefore = ((java.util.Map<?, ?>) villageType.getMethod("getVillagerRecords").invoke(village)).size();
            int buildingsBefore = ((java.util.List<?>) villageType.getMethod("getBuildings").invoke(village)).size();
            var observations = adapter.observeNearby(level, center);
            helper.assertValueEqual(observations.size(), 1, "the native SavedData fixture must produce one bounded observation");
            var observation = observations.getFirst();
            helper.assertValueEqual(observation.nativeReference(), fixtureId.toString(),
                    "the persisted adapter reference must be the native village UUID");
            helper.assertValueEqual(observation.authorityProfileId(), "pale_mirror:native_reconciled",
                    "native settlement facts must select the restricted source-neutral authority profile");
            helper.assertTrue(!AdapterRegistry.campaignEligible(
                    new io.farfrontier.palemirror.internal.world.SettlementObservationRecord(observation)),
                    "native villages must remain excluded from the authored campaign by default");
            helper.assertValueEqual(((java.util.Map<?, ?>) villageType.getMethod("getVillagerRecords").invoke(village)).size(),
                    recordsBefore, "read-only observation must not alter native residents");
            helper.assertValueEqual(((java.util.List<?>) villageType.getMethod("getBuildings").invoke(village)).size(),
                    buildingsBefore, "read-only observation must not alter native construction");
        } finally {
            manager.getClass().getMethod("removeVillage", idType).invoke(manager, nativeId);
        }
        helper.succeed();
    }
}
