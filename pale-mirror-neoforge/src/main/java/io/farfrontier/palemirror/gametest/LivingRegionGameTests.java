package io.farfrontier.palemirror.gametest;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.domain.DomainServices;
import io.farfrontier.palemirror.domain.ResourceKind;
import io.farfrontier.palemirror.domain.WorldObjectId;
import io.farfrontier.palemirror.internal.adapter.SettlementObservation;
import io.farfrontier.palemirror.internal.adapter.VanillaVillageSettlementAdapter;
import io.farfrontier.palemirror.internal.world.CampaignRegionBootstrapper;
import io.farfrontier.palemirror.internal.world.PaleMirrorSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Exercises a living region bound to physical settlement evidence without building a replacement village. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LivingRegionGameTests {
    private LivingRegionGameTests() { }

    @GameTest(batch = "pm-living-region", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 40)
    public static void ironhillPlanIsCanonicalAndRestartSafe(GameTestHelper helper) {
        if (GameTestProfiles.createAdapterOnly()) { helper.succeed(); return; }
        ServerLevel level = helper.getLevel();
        PaleMirrorSavedData data = PaleMirrorSavedData.get(level.getServer().overworld());
        reset(data);
        BlockPos villageAnchor = helper.absolutePos(new net.minecraft.core.BlockPos(0, 2, 0));
        data.observeSettlement(observation(level, villageAnchor, 4, 1));
        CampaignRegionBootstrapper.tick(level.getServer(), data, new DomainServices().commands());

        var region = data.worldState().livingRegion(CampaignRegionBootstrapper.IRONHILL_ID).orElseThrow();
        var settlement = data.worldState().settlement(region.settlementId()).orElseThrow();
        helper.assertValueEqual(region.primaryFacilityId(), CampaignRegionBootstrapper.MINE17,
                "Ironhill must have one canonical primary mine");
        helper.assertValueEqual(settlement.population(), 4, "canonical population starts from the observed settlement signal");
        helper.assertValueEqual(settlement.stock(ResourceKind.IRON), 4,
                "the strategic stock is scaled from the observed settlement rather than a fake template population");
        helper.assertValueEqual(data.worldState().route(CampaignRegionBootstrapper.RED_VALLEY_ROUTE).orElseThrow().status().name(),
                "PLANNED", "the fallback supply route must require a later physical observation");
        helper.assertTrue(data.campaignRegions().containsKey(CampaignRegionBootstrapper.IRONHILL_ID),
                "physical coordinates are persisted separately from the canonical region aggregate");
        var presentation = data.campaignRegions().get(CampaignRegionBootstrapper.IRONHILL_ID);
        presentation.observeRouteEndpoint(true, 7, 18, "vehicle-a");
        presentation.observeRouteEndpoint(false, 8, 18, "vehicle-b");
        helper.assertValueEqual(presentation.certifiedRouteCapacity(8, 8), 0,
                "two unrelated trains must not certify a scheduled supply route");
        presentation.observeRouteEndpoint(false, 9, 18, "vehicle-a");
        helper.assertValueEqual(presentation.certifiedRouteCapacity(9, 8), 18,
                "the same observed vehicle at both endpoints certifies real traversal capacity");

        CompoundTag snapshot = data.save(new CompoundTag(), level.registryAccess());
        PaleMirrorSavedData reloaded = PaleMirrorSavedData.load(snapshot, level.registryAccess());
        helper.assertValueEqual(reloaded.worldState().livingRegion(CampaignRegionBootstrapper.IRONHILL_ID).orElseThrow()
                .alternateRouteId(), CampaignRegionBootstrapper.RED_VALLEY_ROUTE,
                "restart snapshot must retain the alternate route identity");
        helper.assertTrue(reloaded.campaignRegions().containsKey(CampaignRegionBootstrapper.IRONHILL_ID),
                "restart snapshot must retain regional presentation work");
        helper.assertValueEqual(reloaded.campaignRegions().get(CampaignRegionBootstrapper.IRONHILL_ID).destinationVehicleId(),
                "vehicle-a", "restart snapshot must retain the opaque Create vehicle proof");
        helper.succeed();
    }

    @GameTest(batch = "pm-village-observer", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 40)
    public static void vanillaVillageObserverRequiresStableSignalsAndOnlyRecordsFacts(GameTestHelper helper) {
        if (GameTestProfiles.createAdapterOnly()) { helper.succeed(); return; }
        ServerLevel level = helper.getLevel();
        PaleMirrorSavedData data = PaleMirrorSavedData.get(level.getServer().overworld());
        reset(data);
        BlockPos anchor = helper.absolutePos(new net.minecraft.core.BlockPos(8, 2, 0));
        level.setBlock(anchor, Blocks.BELL.defaultBlockState(), 3);
        level.setBlock(anchor.east(), Blocks.RED_BED.defaultBlockState(), 3);
        level.setBlock(anchor.west(), Blocks.BLUE_BED.defaultBlockState(), 3);
        spawnVillager(level, anchor.north());
        var adapter = new VanillaVillageSettlementAdapter();
        helper.assertTrue(adapter.observeNearby(level, anchor).isEmpty(),
                "one wandering villager must not become a PM settlement");
        spawnVillager(level, anchor.south());
        helper.runAfterDelay(1, () -> {
            var observations = adapter.observeNearby(level, anchor);
            helper.assertTrue(!observations.isEmpty(), "two villagers and a bell must yield a stable external observation");
            var observation = observations.getFirst();
            helper.assertValueEqual(observation.anchor(), anchor, "the bell must create a stable external settlement identity");
            helper.assertTrue(data.observeSettlement(observation), "first external physical observation must be persisted");
            helper.assertValueEqual(level.getBlockState(anchor).getBlock(), Blocks.BELL,
                    "read-only observation must not replace a player/world-owned village landmark");
            helper.assertValueEqual(data.worldRegistry().require(observation.settlementId()).templateId(), "minecraft:observed_village",
                    "registry provenance must explicitly describe an observed, not PM-materialized settlement");
            helper.succeed();
        });
    }

    private static SettlementObservation observation(ServerLevel level, BlockPos anchor, int population, int guards) {
        return new SettlementObservation(new WorldObjectId("pale_mirror:test_observed_village"),
                level.dimension().location().toString(), anchor, anchor.offset(-20, -4, -20), anchor.offset(20, 8, 20),
                population, guards, level.getGameTime(), "minecraft:loaded_village_signals_v1");
    }

    private static void spawnVillager(ServerLevel level, BlockPos position) {
        Villager villager = EntityType.VILLAGER.create(level);
        if (villager == null) throw new IllegalStateException("Could not create vanilla villager");
        villager.moveTo(position, 0.0F, 0.0F);
        level.addFreshEntity(villager);
    }

    private static void reset(PaleMirrorSavedData data) {
        data.testMines().clear();
        data.worldRegistry().clear();
        data.campaignRegions().clear();
        data.settlementObservations().clear();
        data.audienceMappings().clear();
        data.reconciliationLedger().clear();
        data.effectLeases().clear();
        data.quarantine().clear();
        data.threatCombat().clear();
        data.worldState().facilities().clear();
        data.worldState().scenarios().clear();
        data.worldState().settlements().clear();
        data.worldState().routes().clear();
        data.worldState().migrantGroups().clear();
        data.worldState().livingRegions().clear();
        data.worldState().narratorCooldowns().clear();
        data.worldState().history().clear();
        data.worldState().setSimulationStep(0);
        data.worldState().setEventSequence(0);
        data.setDirty();
    }
}
