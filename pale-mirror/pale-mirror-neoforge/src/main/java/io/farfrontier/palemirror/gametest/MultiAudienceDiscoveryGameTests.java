package io.farfrontier.palemirror.gametest;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.domain.DomainServices;
import io.farfrontier.palemirror.domain.KnownRegionalFeature;
import io.farfrontier.palemirror.domain.StoryAudienceId;
import io.farfrontier.palemirror.domain.WorldObjectId;
import io.farfrontier.palemirror.internal.world.PaleMirrorSavedData;
import io.farfrontier.palemirror.internal.world.PlayerAudienceEligibility;
import io.farfrontier.palemirror.internal.world.RegionalDiscoveryRuntime;
import io.farfrontier.palemirror.internal.world.StoryAudienceResolver;
import io.farfrontier.palemirror.internal.world.CampaignRegionPresentationStatus;
import io.farfrontier.palemirror.internal.world.CampaignRegionRecord;
import io.farfrontier.palemirror.internal.world.WorldObjectLifecycle;
import io.farfrontier.palemirror.internal.world.WorldObjectRegistryEntry;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Proves that operator observation cannot claim private regional knowledge. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MultiAudienceDiscoveryGameTests {
    private MultiAudienceDiscoveryGameTests() { }

    @GameTest(batch = "pm-living-region", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 40)
    @SuppressWarnings("removal")
    public static void spectatorCannotDiscoverButIndependentAudienceCan(GameTestHelper helper) {
        if (GameTestProfiles.createAdapterOnly()) { helper.succeed(); return; }
        ServerLevel level = helper.getLevel();
        PaleMirrorSavedData data = PaleMirrorSavedData.get(level.getServer().overworld());
        GameTestStateReset.resetAll(data);
        String regionId = "pale_mirror:iron_frontier_spectator_discovery_fixture";
        GameTestStateReset.registerMinimalRouteRegion(data, regionId,
                new WorldObjectId("pale_mirror:spectator_discovery_route"));
        var region = data.worldState().livingRegion(regionId).orElseThrow();
        var player = helper.makeMockServerPlayerInLevel();
        BlockPos anchor = player.blockPosition();
        data.worldRegistry().register(new WorldObjectRegistryEntry(region.placeId(),
                level.dimension().location().toString(), anchor, anchor.offset(-8, -8, -8),
                anchor.offset(8, 8, 8), "pale_mirror:discovery_fixture", "1", WorldObjectLifecycle.REPRESENTED));
        StoryAudienceId audience = new StoryAudienceId("pm:audience:survival-observer");
        java.util.function.Function<net.minecraft.server.level.ServerPlayer, StoryAudienceId> audiences = observed ->
                observed == player ? audience : new StoryAudienceId("pm:audience:other-" + observed.getUUID());
        player.setGameMode(GameType.SPECTATOR);
        helper.assertTrue(!PlayerAudienceEligibility.participates(player),
                "the audience filter must observe the server game-mode transition immediately");
        RegionalDiscoveryRuntime.observePlayers(level.getServer(), data, new DomainServices().commands(),
                audiences, ignored -> { }, (ignored, ignoredRegion, ignoredFeature) -> { });
        helper.assertTrue(data.worldState().regionKnowledge(audience, regionId).isEmpty(),
                "spectator and pmaudit observation must never create canonical regional knowledge");

        data.campaignRegions().put(regionId, new CampaignRegionRecord(regionId,
                level.dimension().location().toString(), region.placeId(), anchor, anchor, anchor.offset(128, 0, 0),
                anchor, anchor.offset(128, 0, 0), CampaignRegionPresentationStatus.MATERIALIZED, "", 2,
                -1, -1, 0, 0, "", ""));
        java.util.ArrayList<KnownRegionalFeature> synchronizedFeatures = new java.util.ArrayList<>();
        player.setGameMode(GameType.SURVIVAL);
        RegionalDiscoveryRuntime.observePlayers(level.getServer(), data, new DomainServices().commands(),
                audiences, ignored -> { }, (ignored, ignoredRegion, feature) -> synchronizedFeatures.add(feature));
        helper.assertTrue(data.worldState().regionKnowledge(audience, regionId)
                        .filter(value -> value.knows(KnownRegionalFeature.SETTLEMENT)).isPresent(),
                "an ordinary independent audience in an authored discovery cell must learn the settlement");
        helper.assertTrue(data.worldState().regionKnowledge(audience, regionId)
                        .filter(value -> value.knows(KnownRegionalFeature.PRIMARY_MINE)).isPresent(),
                "the compatibility footprint must independently reveal its primary mine");
        helper.assertTrue(synchronizedFeatures.contains(KnownRegionalFeature.PRIMARY_MINE),
                "new mine knowledge must immediately enter the negotiated Atlas synchronization path");

        StoryAudienceId personal = StoryAudienceResolver.resolve(data, player);
        new DomainServices().commands().execute(data.worldState(),
                new io.farfrontier.palemirror.domain.DomainCommand.DiscoverLivingRegion(
                        regionId, personal, "gametest:personal-discovery"));
        var team = level.getServer().getScoreboard().addPlayerTeam("pm_audience_test");
        level.getServer().getScoreboard().addPlayerToTeam(player.getScoreboardName(), team);
        StoryAudienceId shared = StoryAudienceResolver.resolve(data, player);
        helper.assertTrue(!shared.equals(personal)
                        && data.worldState().regionKnowledge(shared, regionId).isEmpty(),
                "joining a scoreboard team must select a shared audience without merging personal knowledge");
        level.getServer().getScoreboard().removePlayerFromTeam(player.getScoreboardName(), team);
        helper.assertValueEqual(StoryAudienceResolver.resolve(data, player), personal,
                "leaving a team must restore the stable personal audience and its existing knowledge");
        helper.succeed();
    }
}
