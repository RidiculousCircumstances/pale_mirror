package io.farfrontier.palemirror.gametest;

import java.util.UUID;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.domain.DomainServices;
import io.farfrontier.palemirror.domain.ResourceKind;
import io.farfrontier.palemirror.domain.ScenarioInstance;
import io.farfrontier.palemirror.domain.ScenarioStatus;
import io.farfrontier.palemirror.domain.StoryAudienceId;
import io.farfrontier.palemirror.domain.WorldObjectId;
import io.farfrontier.palemirror.internal.adapter.SettlementObservation;
import io.farfrontier.palemirror.internal.adapter.VanillaVillageSettlementAdapter;
import io.farfrontier.palemirror.internal.world.CampaignRegionBootstrapper;
import io.farfrontier.palemirror.internal.world.RegionBindings;
import io.farfrontier.palemirror.internal.world.PaleMirrorSavedData;
import io.farfrontier.palemirror.internal.economy.SettlementDepotRuntime;
import io.farfrontier.palemirror.internal.debug.RuntimeDebugService;
import io.farfrontier.palemirror.internal.debug.RuntimeDebugNavigator;
import io.farfrontier.palemirror.internal.presentation.ScenarioCommandPresentation;
import io.farfrontier.palemirror.internal.presentation.atlas.RegionalAtlasProjection;
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
        CampaignRegionBootstrapper.bindCandidate(level.getServer(), data, new DomainServices().commands(),
                new WorldObjectId("pale_mirror:test_observed_village"));
        SettlementDepotRuntime.tick(level.getServer(), data);
        SettlementDepotRuntime.tick(level.getServer(), data);
        SettlementDepotRuntime.tick(level.getServer(), data);
        RegionBindings bindings = RegionBindings.forObserved(level.getServer().overworld().getSeed(),
                new WorldObjectId("pale_mirror:test_observed_village"));
        var region = data.worldState().livingRegion(bindings.regionId()).orElseThrow();
        var audience = io.farfrontier.palemirror.domain.StoryAudienceId.globalTestAudience();
        var setupCommands = new DomainServices().commands();
        setupCommands.execute(data.worldState(), new io.farfrontier.palemirror.domain.DomainCommand.DiscoverLivingRegion(region.id(), audience, "gametest:discover"));
        setupCommands.execute(data.worldState(), new io.farfrontier.palemirror.domain.DomainCommand.DiscoverRegionalFeature(region.id(), audience,
                io.farfrontier.palemirror.domain.KnownRegionalFeature.DEPOT, "gametest:depot"));
        setupCommands.execute(data.worldState(), new io.farfrontier.palemirror.domain.DomainCommand.ObserveAudienceRegionAccess(region.id(), audience,
                io.farfrontier.palemirror.domain.AudienceRegionReachability.LOCAL, true, data.worldState().simulationStep(), "gametest:access"));
        var settlement = data.worldState().community(region.communityId()).orElseThrow();
        helper.assertValueEqual(region.primaryFacilityId(), bindings.primaryMineId(),
                "a region instance must have its deterministically derived primary mine");
        helper.assertValueEqual(data.worldState().population(settlement.id()), 4,
                "canonical population starts from the observed settlement signal");
        helper.assertValueEqual(data.worldState().settlementDevelopment(settlement.id()).orElseThrow().housingCapacity(), 4,
                "initial housing starts at observed population and grows only through verified development");
        helper.assertValueEqual(data.worldState().economy(region.communityId()).orElseThrow().require(ResourceKind.IRON).stock(), 6,
                "the strategic stock is scaled from the observed settlement rather than a fake template population");
        helper.assertValueEqual(data.worldState().routeContract(bindings.alternateRouteId()).orElseThrow().status().name(),
                "PLANNED", "the fallback supply route must require a later physical observation");
        helper.assertValueEqual(data.worldState().routeContract(region.primaryRouteId()).orElseThrow().provider(),
                io.farfrontier.palemirror.domain.RouteProvider.VANILLA_MINECART,
                "a fresh small settlement must receive a narrow vanilla baseline route, not industrial Create track");
        helper.assertTrue(data.campaignRegions().containsKey(bindings.regionId()),
                "physical coordinates are persisted separately from the canonical region aggregate");
        var depot = data.settlementDepots().get(bindings.communityId());
        helper.assertTrue(depot != null && depot.state() == io.farfrontier.palemirror.internal.economy.SettlementDepotState.ACTIVE,
                "a PM-owned supply depot must materialize without replacing observed village blocks");
        helper.assertValueEqual(level.getBlockState(depot.interactionPosition()).getBlock(), Blocks.BARREL,
                "the depot interaction endpoint must have a verified physical postcondition");
        var atlas = RegionalAtlasProjection.snapshot(data, region.primaryAudience(), "", false).snapshot();
        var atlasCards = atlas.getList("regions", net.minecraft.nbt.Tag.TAG_COMPOUND);
        helper.assertValueEqual(atlasCards.size(), 1,
                "the client Atlas must receive one bounded card for the audience-owned region");
        helper.assertTrue(!atlasCards.getCompound(0).getString("name").isBlank()
                        && !atlasCards.getCompound(0).getString("name").contains("pale_mirror:"),
                "the Atlas must expose a player-facing region name rather than require an opaque region id");
        helper.assertTrue(!atlasCards.getCompound(0).contains("regionId"),
                "the client Atlas must not need an opaque region id to render a region card");
        var presentation = data.campaignRegions().get(bindings.regionId());
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
        helper.assertValueEqual(reloaded.worldState().livingRegion(bindings.regionId()).orElseThrow()
                .alternateRouteId(), bindings.alternateRouteId(),
                "restart snapshot must retain the alternate route identity");
        helper.assertTrue(reloaded.campaignRegions().containsKey(bindings.regionId()),
                "restart snapshot must retain regional presentation work");
        helper.assertValueEqual(reloaded.campaignRegions().get(bindings.regionId()).destinationVehicleId(),
                "vehicle-a", "restart snapshot must retain the opaque Create vehicle proof");
        helper.assertValueEqual(reloaded.settlementObservations().get(region.placeId()).lastDamageAttribution(),
                io.farfrontier.palemirror.domain.DamageAttribution.PLAYER,
                "restart snapshot must retain causal attribution for confirmed physical evidence");
        helper.assertValueEqual(reloaded.settlementDepots().get(region.communityId()).anchor(), depot.anchor(),
                "restart snapshot must pin the selected depot footprint");
        helper.assertValueEqual(reloaded.worldState().population(region.communityId()), 4,
                "restart snapshot must retain population groups as the only macro-population source");
        helper.assertValueEqual(reloaded.worldState().settlementDevelopment(region.communityId()).orElseThrow().prosperity(), 25,
                "restart snapshot must retain positive development state");
        helper.assertTrue(reloaded.worldState().regionKnowledge(audience, region.id()).orElseThrow().knows(
                io.farfrontier.palemirror.domain.KnownRegionalFeature.DEPOT), "restart must retain audience-scoped discovery");
        helper.assertTrue(reloaded.worldState().regionAccess(audience, region.id()).orElseThrow().present(), "restart must retain fairness evidence");
        helper.assertValueEqual(reloaded.worldState().settlementAuthorityProfile(region.communityId()).orElseThrow().profileId(),
                "pale_mirror:pm_managed", "restart snapshot must retain the immutable settlement authority contract");
        CompoundTag schema32 = snapshot.copy();
        schema32.putInt("schemaVersion", 32);
        try {
            PaleMirrorSavedData.load(schema32, level.registryAccess());
            throw new AssertionError("schema v32 must not be retrofitted with authored settlements");
        } catch (IllegalStateException expected) {
            helper.assertTrue(expected.getMessage().contains("not compatible with schema 33"),
                    "fresh-world rejection must explain the exact schema boundary");
        }
        helper.succeed();
    }
    @GameTest(batch = "pm-rail-authority", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 40)
    public static void railwayAuthorityPersistsProvenanceAndFailsClosedOnConflict(GameTestHelper helper) {
        if (GameTestProfiles.createAdapterOnly()) { helper.succeed(); return; }
        ServerLevel level = helper.getLevel();
        PaleMirrorSavedData data = PaleMirrorSavedData.get(level.getServer().overworld());
        reset(data);
        BlockPos start = helper.absolutePos(new BlockPos(0, 2, 0));
        var record = io.farfrontier.palemirror.internal.world.CampaignCommissioningRecord.planned(
                "pale_mirror:test_region", level.dimension().location().toString(), "pm:test:rail", "pm:test:service",
                start, start.east(16), start, net.minecraft.core.Direction.Axis.X, net.minecraft.core.Direction.EAST,
                32, "Test Origin", "Test Destination");
        record.railBuilding("test-plan", "test-head");
        data.campaignCommissioning().put(record.regionId(), record);

        helper.assertTrue(record.authorize(level, start, Blocks.AIR.defaultBlockState(), Blocks.GRAVEL.defaultBlockState()),
                "an ordinary first-touch cell inside the persisted envelope must be recordable");
        helper.assertTrue(record.authorize(level, start.east(), Blocks.CHEST.defaultBlockState(),
                        Blocks.GRAVEL.defaultBlockState()),
                "first-generation infrastructure may replace a breakable worldgen block entity inside its envelope");
        helper.assertTrue(record.authorize(level, start.south(), Blocks.AIR.defaultBlockState(),
                        Blocks.CHEST.defaultBlockState()),
                "the trusted railway provider may place its own block-entity representation during first generation");
        helper.assertTrue(record.authorize(level, start, Blocks.DIAMOND_BLOCK.defaultBlockState(),
                        Blocks.AIR.defaultBlockState()),
                "first-generation construction must tolerate repeated provider transformations");
        record.observeBaselineArrival(1);
        record.railBuilding("repair-plan", "repair-native");
        helper.assertTrue(!record.authorize(level, start, Blocks.DIAMOND_BLOCK.defaultBlockState(),
                        Blocks.GRAVEL.defaultBlockState()),
                "after commissioning, a known cell changed outside PM must become a conflict");
        helper.assertValueEqual(record.status(),
                io.farfrontier.palemirror.internal.world.CampaignCommissioningStatus.BLOCKED,
                "a provenance mismatch must stop repair reconciliation");

        CompoundTag snapshot = data.save(new CompoundTag(), level.registryAccess());
        var reloaded = PaleMirrorSavedData.load(snapshot, level.registryAccess()).campaignCommissioning().get(record.regionId());
        helper.assertTrue(reloaded != null && reloaded.railCells().get(start.asLong()).conflicted(),
                "restart must retain the baseline, last approved state and conflict marker");
        helper.succeed();
    }
    @GameTest(batch = "pm-autonomous-region", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 400)
    public static void campaignMineSitesCommissionWithoutAPlayerVisitingThem(GameTestHelper helper) {
        if (GameTestProfiles.createAdapterOnly()) { helper.succeed(); return; }
        ServerLevel level = helper.getLevel();
        PaleMirrorSavedData data = PaleMirrorSavedData.get(level.getServer().overworld());
        reset(data);
        BlockPos settlement = helper.absolutePos(new BlockPos(0, 2, 0));
        BlockPos primary = helper.absolutePos(new BlockPos(2048, 0, 2048));
        BlockPos alternate = primary.east(32);
        // The assertion is about PM commissioning without a player.  Load the
        // remote test columns explicitly so this small GameTest world does not
        // depend on asynchronous chunk-ticket timing.
        loadMineFootprint(level, primary);
        loadMineFootprint(level, alternate);
        level.setBlock(primary.atY(40), Blocks.DIRT.defaultBlockState(), 3);
        level.setBlock(alternate.atY(40), Blocks.DIRT.defaultBlockState(), 3);
        int surfaceY = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                primary.getX(), primary.getZ());
        level.setBlock(new BlockPos(primary.getX() - 2, surfaceY - 1, primary.getZ() - 6),
                Blocks.TERRACOTTA.defaultBlockState(), 3);
        RegionBindings bindings = RegionBindings.forObserved(level.getServer().overworld().getSeed(),
                new WorldObjectId("pale_mirror:autonomous_region"));
        var record = campaignRecord(level, settlement, primary, alternate, bindings.regionId());
        data.campaignRegions().put(record.id(), record);

        for (int operation = 0; operation < 8; operation++) CampaignRegionBootstrapper.advancePhysicalPlan(level, data, record);
        helper.assertTrue(record.status() != io.farfrontier.palemirror.internal.world.CampaignRegionPresentationStatus.BLOCKED,
                "natural remote terrain must not block autonomous MineSite commissioning: " + record.diagnostic());
        helper.assertValueEqual(record.status(),
                io.farfrontier.palemirror.internal.world.CampaignRegionPresentationStatus.MATERIALIZED,
                "both authored MineSites must materialize without a player visiting either column");
        helper.assertTrue(data.testMines().containsKey(bindings.primaryMineId())
                        && data.testMines().containsKey(bindings.alternateMineId()),
                "autonomous commissioning must persist both physical MineSite identities");
        helper.succeed();
    }

    @GameTest(batch = "pm-autonomous-region", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 400)
    public static void autonomousMineSitePreflightPreservesProtectedBlocksBeforeWrites(GameTestHelper helper) {
        if (GameTestProfiles.createAdapterOnly()) { helper.succeed(); return; }
        ServerLevel level = helper.getLevel();
        PaleMirrorSavedData data = PaleMirrorSavedData.get(level.getServer().overworld());
        reset(data);
        BlockPos settlement = helper.absolutePos(new BlockPos(0, 2, 0));
        BlockPos primary = helper.absolutePos(new BlockPos(-2048, 0, -2048));
        loadMineFootprint(level, primary);
        level.setBlock(primary.atY(40), Blocks.BEDROCK.defaultBlockState(), 3);
        int surfaceY = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                primary.getX(), primary.getZ());
        BlockPos conflict = new BlockPos(primary.getX(), surfaceY - 1, primary.getZ());
        RegionBindings bindings = RegionBindings.forObserved(level.getServer().overworld().getSeed(),
                new WorldObjectId("pale_mirror:blocked_region"));
        var record = campaignRecord(level, settlement, primary, primary.east(32), bindings.regionId());
        data.campaignRegions().put(record.id(), record);

        CampaignRegionBootstrapper.advancePhysicalPlan(level, data, record);
        helper.assertValueEqual(record.status(),
                io.farfrontier.palemirror.internal.world.CampaignRegionPresentationStatus.BLOCKED,
                "a protected cell in the future MineSite must block the complete site before physical writes");
        helper.assertValueEqual(level.getBlockState(conflict).getBlock(), Blocks.BEDROCK,
                "failed autonomous preflight must preserve an unbreakable block");
        helper.assertTrue(data.testMines().isEmpty(),
                "failed preflight must not register a partially materialized MineSite");
        helper.succeed();
    }
    @GameTest(batch = "pm-village-observer", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 40)
    public static void vanillaVillageObserverRequiresStableSignalsAndOnlyRecordsFacts(GameTestHelper helper) {
        if (GameTestProfiles.createAdapterOnly()) { helper.succeed(); return; }
        ServerLevel level = helper.getLevel();
        PaleMirrorSavedData data = PaleMirrorSavedData.get(level.getServer().overworld());
        reset(data);
        BlockPos focus = helper.absolutePos(new net.minecraft.core.BlockPos(0, 2, 0));
        BlockPos anchor = focus.offset(48, 24, 0);
        level.setBlock(anchor, Blocks.BELL.defaultBlockState(), 3);
        level.setBlock(anchor.east(), Blocks.RED_BED.defaultBlockState(), 3);
        level.setBlock(anchor.west(), Blocks.BLUE_BED.defaultBlockState(), 3);
        spawnVillager(level, anchor.north());
        var adapter = new VanillaVillageSettlementAdapter();
        helper.assertTrue(adapter.observeNearby(level, focus).isEmpty(),
                "one wandering villager must not become a PM settlement");
        spawnVillager(level, anchor.south());
        helper.runAfterDelay(1, () -> {
            var observations = adapter.observeNearby(level, focus);
            helper.assertTrue(!observations.isEmpty(),
                    "a large vertical village must be observable beyond the legacy sixteen-block landmark cube");
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
    @GameTest(batch = "pm-runtime-debug", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 40)
    @SuppressWarnings("removal")
    public static void manualDiscoveryAndExplicitBindUseCanonicalPipeline(GameTestHelper helper) {
        if (GameTestProfiles.createAdapterOnly()) { helper.succeed(); return; }
        ServerLevel level = helper.getLevel();
        PaleMirrorSavedData data = PaleMirrorSavedData.get(level.getServer().overworld());
        reset(data);
        BlockPos anchor = helper.absolutePos(new BlockPos(0, 2, 0));
        long observedAt = level.getGameTime();
        data.observeSettlement(observation(level, anchor, 4, 1, observedAt));
        data.observeSettlement(observation(level, anchor, 4, 1, observedAt + 200));
        data.observeSettlement(observation(level, anchor, 4, 1, observedAt + 400));
        DomainServices services = new DomainServices();

        CampaignRegionBootstrapper.tick(level.getServer(), data, services.commands(), false);
        RegionBindings bindings = RegionBindings.forObserved(level.getServer().overworld().getSeed(),
                new WorldObjectId("pale_mirror:test_observed_village"));
        helper.assertTrue(data.worldState().livingRegion(bindings.regionId()).isEmpty(),
                "MANUAL discovery must keep collecting evidence without binding it");
        CampaignRegionBootstrapper.bindCandidate(level.getServer(), data, services.commands(),
                new WorldObjectId("pale_mirror:test_observed_village"));
        helper.assertValueEqual(data.worldState().livingRegion(bindings.regionId()).orElseThrow().placeId(),
                new WorldObjectId("pale_mirror:test_observed_village"),
                "explicit selection must use the production registration pipeline");
        var player = helper.makeMockServerPlayerInLevel();
        var navigator = new RuntimeDebugNavigator(level.getServer(), data);
        var settlementLine = navigator.settlements(player).get(1);
        helper.assertTrue(settlementLine.getString().contains(anchor.getX() + "," + anchor.getY())
                        && settlementLine.getSiblings().getLast().getStyle().getClickEvent().getValue()
                        .equals("/pale_mirror debug tp settlement pale_mirror:test_observed_village"),
                "settlement listing must expose coordinates and an exact clickable teleport command");
        helper.assertTrue(navigator.teleportSettlement(player,
                new WorldObjectId("pale_mirror:test_observed_village")).success(),
                "a persisted observed settlement anchor must be teleportable by an explicit operator action");
        helper.assertTrue(!navigator.teleportMine(player, bindings.primaryMineId()).success(),
                "a merely planned mine must reject teleport rather than pretending it has a physical anchor");
        helper.assertTrue(navigator.mines(player).stream().anyMatch(line -> line.getString().contains("PLANNED")),
                "the mine listing must still expose planned coordinates when teleport is unavailable");
        helper.succeed();
    }

    @GameTest(batch = "pm-runtime-debug", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 40)
    @SuppressWarnings("removal")
    public static void offeredScenarioRendersReadableClickableAcceptCommand(GameTestHelper helper) {
        if (GameTestProfiles.createAdapterOnly()) { helper.succeed(); return; }
        ServerLevel level = helper.getLevel();
        var scenario = new ScenarioInstance("pm:scenario:48", "pm:event:48",
                new WorldObjectId("pale_mirror:mine"), new StoryAudienceId("pm:audience:test"),
                "pale_mirror:investigation_recovery", "1", ScenarioStatus.OFFERED);
        var component = ScenarioCommandPresentation.offered(java.util.List.of(scenario));
        var click = component.getSiblings().getLast().getStyle().getClickEvent();
        helper.assertTrue(component.getString().contains("Mine recovery [OFFERED] [ACCEPT]")
                        && !component.getString().contains("pm:scenario:48")
                        && click != null
                        && click.getAction() == net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND
                        && click.getValue().equals("/pale_mirror scenario accept pm:scenario:48"),
                "offered scenarios must expose an exact clickable accept command using the complete opaque ID");
        helper.assertValueEqual(ScenarioCommandPresentation.offered(java.util.List.of()).getString(),
                "No offered scenarios.", "an empty offer list must remain explicit");
        var parsed = level.getServer().getCommands().getDispatcher().parse(
                "pale_mirror scenario accept pm:scenario:48", level.getServer().createCommandSourceStack());
        helper.assertTrue(!parsed.getReader().canRead() && parsed.getExceptions().isEmpty(),
                "the command tree must consume the complete colon-bearing opaque scenario ID");
        helper.succeed();
    }

    @GameTest(batch = "pm-runtime-debug", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 40)
    public static void resetIsTwoPhaseAndFailsClosedOncePhysicalWorkStarts(GameTestHelper helper) {
        if (GameTestProfiles.createAdapterOnly()) { helper.succeed(); return; }
        ServerLevel level = helper.getLevel();
        PaleMirrorSavedData data = PaleMirrorSavedData.get(level.getServer().overworld());
        reset(data);
        BlockPos anchor = helper.absolutePos(new BlockPos(0, 2, 0));
        long observedAt = level.getGameTime();
        data.observeSettlement(observation(level, anchor, 4, 1, observedAt));
        data.observeSettlement(observation(level, anchor, 4, 1, observedAt + 200));
        data.observeSettlement(observation(level, anchor, 4, 1, observedAt + 400));
        DomainServices services = new DomainServices();
        CampaignRegionBootstrapper.bindCandidate(level.getServer(), data, services.commands(),
                new WorldObjectId("pale_mirror:test_observed_village"));
        RuntimeDebugService debug = new RuntimeDebugService();
        UUID operator = UUID.randomUUID();
        var preview = debug.previewReset(operator, observedAt + 400, data);
        helper.assertTrue(preview.success(), "a wholly abstract plan must produce a reset authorization");
        helper.assertTrue(!debug.confirmReset(operator, "wrong-token", observedAt + 400, data).success(),
                "confirmation must be tied to the exact operator token");
        RegionBindings bindings = RegionBindings.forObserved(level.getServer().overworld().getSeed(),
                new WorldObjectId("pale_mirror:test_observed_village"));
        helper.assertTrue(data.worldState().livingRegion(bindings.regionId()).isPresent(),
                "a rejected confirmation must preserve canonical state");

        var presentation = data.campaignRegions().get(bindings.regionId());
        presentation.resolvePendingMineAnchor(anchor.offset(64, 8, 0));
        presentation.startOperation();
        helper.assertTrue(!debug.previewReset(operator, observedAt + 401, data).success(),
                "a RUNNING physical job must fail closed even before a block postcondition is known");

        reset(data);
        data.observeSettlement(observation(level, anchor, 4, 1, observedAt));
        data.observeSettlement(observation(level, anchor, 4, 1, observedAt + 200));
        data.observeSettlement(observation(level, anchor, 4, 1, observedAt + 400));
        CampaignRegionBootstrapper.bindCandidate(level.getServer(), data, services.commands(),
                new WorldObjectId("pale_mirror:test_observed_village"));
        var recoveryPreview = debug.previewReset(operator, observedAt + 402, data);
        String token = recoveryPreview.message().substring(recoveryPreview.message().lastIndexOf(' ') + 1);
        helper.assertTrue(debug.confirmReset(operator, token, observedAt + 402, data).success(),
                "a fresh preview must recover the maintenance workflow after unsafe work is removed externally");
        helper.assertTrue(data.worldState().livingRegions().isEmpty() && data.settlementObservations().isEmpty(),
                "successful reset must clear abstract canonical state and observations together");
        helper.assertValueEqual(debug.discoveryMode(), RuntimeDebugService.DiscoveryMode.MANUAL,
                "successful reset must prevent immediate automatic re-registration in the same runtime");
        helper.succeed();
    }

    static SettlementObservation observation(ServerLevel level, BlockPos anchor, int population, int guards,
                                                     long observedAt) {
        return observation(level, new WorldObjectId("pale_mirror:test_observed_village"), anchor, population, guards, observedAt);
    }

    static SettlementObservation observation(ServerLevel level, WorldObjectId id, BlockPos anchor,
                                                     int population, int guards, long observedAt) {
        java.util.List<io.farfrontier.palemirror.internal.adapter.SettlementRepresentativeObservation> representatives
                = new java.util.ArrayList<>();
        for (int index = 0; index < population; index++) representatives.add(
                new io.farfrontier.palemirror.internal.adapter.SettlementRepresentativeObservation(
                        "resident-" + index, io.farfrontier.palemirror.domain.SettlementCohort.CIVILIANS));
        for (int index = 0; index < guards; index++) representatives.add(
                new io.farfrontier.palemirror.internal.adapter.SettlementRepresentativeObservation(
                        "guard-" + index, io.farfrontier.palemirror.domain.SettlementCohort.GUARDS));
        return new SettlementObservation(id,
                level.dimension().location().toString(), anchor, anchor.offset(-20, -4, -20), anchor.offset(20, 8, 20),
                population, guards, observedAt, true, representatives, "minecraft:loaded_village_signals_v2");
    }

    private static void spawnVillager(ServerLevel level, BlockPos position) {
        Villager villager = EntityType.VILLAGER.create(level);
        if (villager == null) throw new IllegalStateException("Could not create vanilla villager");
        villager.moveTo(position, 0.0F, 0.0F);
        level.addFreshEntity(villager);
    }

    static BlockPos surface(ServerLevel level, BlockPos column) {
        return new BlockPos(column.getX(), level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                column.getX(), column.getZ()), column.getZ());
    }

    private static void loadMineFootprint(ServerLevel level, BlockPos column) {
        for (int chunkX = (column.getX() - 16) >> 4; chunkX <= (column.getX() + 16) >> 4; chunkX++) {
            for (int chunkZ = (column.getZ() - 16) >> 4; chunkZ <= (column.getZ() + 96) >> 4; chunkZ++) {
                level.getChunk(chunkX, chunkZ);
            }
        }
    }

    private static io.farfrontier.palemirror.internal.world.CampaignRegionRecord campaignRecord(
            ServerLevel level, BlockPos settlement, BlockPos primary, BlockPos alternate, String id) {
        return new io.farfrontier.palemirror.internal.world.CampaignRegionRecord(id,
                level.dimension().location().toString(), new WorldObjectId("pale_mirror:test_place"), settlement,
                primary, alternate, null, null,
                io.farfrontier.palemirror.internal.world.CampaignRegionPresentationStatus.PLANNED,
                "", 0, -1, -1, 0, 0, "", "");
    }

    static void reset(PaleMirrorSavedData data) {
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
        data.refugeeAnchorPermits().clear();
        data.campaignCommissioning().clear();
        data.vanillaMinecartRoutes().clear();
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
