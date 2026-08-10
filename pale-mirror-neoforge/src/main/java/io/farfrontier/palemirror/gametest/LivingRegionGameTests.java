package io.farfrontier.palemirror.gametest;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.domain.DomainServices;
import io.farfrontier.palemirror.domain.ResourceKind;
import io.farfrontier.palemirror.domain.WorldObjectId;
import io.farfrontier.palemirror.internal.adapter.SettlementObservation;
import io.farfrontier.palemirror.internal.adapter.VanillaVillageSettlementAdapter;
import io.farfrontier.palemirror.internal.world.CampaignRegionBootstrapper;
import io.farfrontier.palemirror.internal.world.PaleMirrorSavedData;
import io.farfrontier.palemirror.internal.economy.SettlementDepotRuntime;
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
        long observedAt = level.getGameTime();
        data.observeSettlement(observation(level, villageAnchor, 4, 1, observedAt));
        data.observeSettlement(observation(level, villageAnchor, 4, 1, observedAt + 200));
        data.observeSettlement(observation(level, villageAnchor, 4, 1, observedAt + 400));
        CampaignRegionBootstrapper.tick(level.getServer(), data, new DomainServices().commands());
        SettlementDepotRuntime.tick(level.getServer(), data);
        SettlementDepotRuntime.tick(level.getServer(), data);
        SettlementDepotRuntime.tick(level.getServer(), data);

        var region = data.worldState().livingRegion(CampaignRegionBootstrapper.IRONHILL_ID).orElseThrow();
        var settlement = data.worldState().community(region.communityId()).orElseThrow();
        helper.assertValueEqual(region.primaryFacilityId(), CampaignRegionBootstrapper.MINE17,
                "Ironhill must have one canonical primary mine");
        helper.assertValueEqual(data.worldState().population(settlement.id()), 4,
                "canonical population starts from the observed settlement signal");
        helper.assertValueEqual(data.worldState().economy(region.communityId()).orElseThrow().require(ResourceKind.IRON).stock(), 4,
                "the strategic stock is scaled from the observed settlement rather than a fake template population");
        helper.assertValueEqual(data.worldState().routeContract(CampaignRegionBootstrapper.RED_VALLEY_ROUTE).orElseThrow().status().name(),
                "PLANNED", "the fallback supply route must require a later physical observation");
        helper.assertTrue(data.campaignRegions().containsKey(CampaignRegionBootstrapper.IRONHILL_ID),
                "physical coordinates are persisted separately from the canonical region aggregate");
        var depot = data.settlementDepots().get(CampaignRegionBootstrapper.IRONHILL);
        helper.assertTrue(depot != null && depot.state() == io.farfrontier.palemirror.internal.economy.SettlementDepotState.ACTIVE,
                "a PM-owned supply depot must materialize without replacing observed village blocks");
        helper.assertValueEqual(level.getBlockState(depot.interactionPosition()).getBlock(), Blocks.BARREL,
                "the depot interaction endpoint must have a verified physical postcondition");
        var presentation = data.campaignRegions().get(CampaignRegionBootstrapper.IRONHILL_ID);
        presentation.observeRouteEndpoint(true, 7, 18, "vehicle-a");
        presentation.observeRouteEndpoint(false, 8, 18, "vehicle-b");
        helper.assertValueEqual(presentation.certifiedRouteCapacity(8, 8), 0,
                "two unrelated trains must not certify a scheduled supply route");
        presentation.observeRouteEndpoint(false, 9, 18, "vehicle-a");
        helper.assertValueEqual(presentation.certifiedRouteCapacity(9, 8), 18,
                "the same observed vehicle at both endpoints certifies real traversal capacity");
        var evidence = data.settlementObservations().get(region.placeId());
        helper.assertValueEqual(evidence.reliability(), io.farfrontier.palemirror.domain.EvidenceReliability.STRONG,
                "a complete 400-tick window must create STRONG positive evidence");
        helper.assertValueEqual(evidence.registeredGuards(), 1, "initial guards must become registered representatives");
        helper.assertValueEqual(evidence.freshness(observedAt + 900),
                io.farfrontier.palemirror.domain.ObservationFreshness.STALE,
                "freshness must age independently from reliability");
        helper.assertValueEqual(evidence.freshness(observedAt + 2900),
                io.farfrontier.palemirror.domain.ObservationFreshness.EXPIRED,
                "old positive evidence must eventually expire without implying loss");
        evidence.recordDeath("guard-0", io.farfrontier.palemirror.domain.SettlementCohort.GUARDS,
                io.farfrontier.palemirror.domain.DamageAttribution.PLAYER, observedAt + 401);
        helper.assertValueEqual(evidence.registeredGuards(), 0,
                "confirmed death must remove only the registered guard capability representative");
        helper.assertValueEqual(evidence.lastEvidenceType(),
                io.farfrontier.palemirror.domain.SettlementEvidenceType.REGISTERED_GUARD_DEATH,
                "registered guard death must be a typed confirmed fact");

        CompoundTag snapshot = data.save(new CompoundTag(), level.registryAccess());
        PaleMirrorSavedData reloaded = PaleMirrorSavedData.load(snapshot, level.registryAccess());
        helper.assertValueEqual(reloaded.worldState().livingRegion(CampaignRegionBootstrapper.IRONHILL_ID).orElseThrow()
                .alternateRouteId(), CampaignRegionBootstrapper.RED_VALLEY_ROUTE,
                "restart snapshot must retain the alternate route identity");
        helper.assertTrue(reloaded.campaignRegions().containsKey(CampaignRegionBootstrapper.IRONHILL_ID),
                "restart snapshot must retain regional presentation work");
        helper.assertValueEqual(reloaded.campaignRegions().get(CampaignRegionBootstrapper.IRONHILL_ID).destinationVehicleId(),
                "vehicle-a", "restart snapshot must retain the opaque Create vehicle proof");
        helper.assertValueEqual(reloaded.settlementObservations().get(region.placeId()).lastDamageAttribution(),
                io.farfrontier.palemirror.domain.DamageAttribution.PLAYER,
                "restart snapshot must retain causal attribution for confirmed physical evidence");
        helper.assertValueEqual(reloaded.settlementDepots().get(region.communityId()).anchor(), depot.anchor(),
                "restart snapshot must pin the selected depot footprint");
        helper.assertValueEqual(reloaded.worldState().population(region.communityId()), 4,
                "restart snapshot must retain population groups as the only macro-population source");
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

    private static SettlementObservation observation(ServerLevel level, BlockPos anchor, int population, int guards,
                                                     long observedAt) {
        java.util.List<io.farfrontier.palemirror.internal.adapter.SettlementRepresentativeObservation> representatives
                = new java.util.ArrayList<>();
        for (int index = 0; index < population; index++) representatives.add(
                new io.farfrontier.palemirror.internal.adapter.SettlementRepresentativeObservation(
                        "resident-" + index, io.farfrontier.palemirror.domain.SettlementCohort.CIVILIANS));
        for (int index = 0; index < guards; index++) representatives.add(
                new io.farfrontier.palemirror.internal.adapter.SettlementRepresentativeObservation(
                        "guard-" + index, io.farfrontier.palemirror.domain.SettlementCohort.GUARDS));
        return new SettlementObservation(new WorldObjectId("pale_mirror:test_observed_village"),
                level.dimension().location().toString(), anchor, anchor.offset(-20, -4, -20), anchor.offset(20, 8, 20),
                population, guards, observedAt, true, representatives, "minecraft:loaded_village_signals_v2");
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
        data.resourceTransfers().clear();
        data.settlementDepots().clear();
        data.refugeeCamps().clear();
        data.worldState().facilities().clear();
        data.worldState().scenarios().clear();
        data.worldState().clearRegionalState();
        data.worldState().narratorCooldowns().clear();
        data.worldState().history().clear();
        data.worldState().setSimulationStep(0);
        data.worldState().setEventSequence(0);
        data.setDirty();
    }
}
