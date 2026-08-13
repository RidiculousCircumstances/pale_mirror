package io.farfrontier.palemirror.gametest;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.domain.FacilityStatus;
import io.farfrontier.palemirror.domain.InfectionSourceId;
import io.farfrontier.palemirror.domain.WorldObjectId;
import io.farfrontier.palemirror.domain.ScenarioStatus;
import io.farfrontier.palemirror.domain.ThreatTier;
import io.farfrontier.palemirror.internal.PaleMirrorRuntime;
import io.farfrontier.palemirror.internal.adapter.AdapterRegistry;
import io.farfrontier.palemirror.internal.integration.crimson.CrimsonSandboxAdapter;
import io.farfrontier.palemirror.internal.adapter.SourceItemFirewall;
import io.farfrontier.palemirror.internal.world.MutableCell;
import io.farfrontier.palemirror.internal.world.InfectionBiomeStage;
import io.farfrontier.palemirror.internal.world.PaleMirrorSavedData;
import io.farfrontier.palemirror.internal.world.TestMineRecord;
import io.farfrontier.palemirror.internal.world.WorldObjectLifecycle;
import io.farfrontier.palemirror.internal.world.EncounterState;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/** Exercises the core slice against a real NeoForge server level and a mock server player. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CoreRecoveryGameTests {
    private CoreRecoveryGameTests() { }

    @SuppressWarnings("removal")
    @GameTest(batch = "pm-passive-infection", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 60)
    public static void infectedMineIsVisibleBeforeScenarioAcceptance(GameTestHelper helper) {
        if (GameTestProfiles.createAdapterOnly()) { helper.succeed(); return; }
        ServerLevel level = helper.getLevel();
        PaleMirrorRuntime runtime = PaleMirrorRuntime.forServer(level.getServer());
        BlockPos anchor = helper.absolutePos(new BlockPos(0, 3, 0));
        resetPaleMirrorState(level);
        clearMineVolume(level, anchor);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setPos(anchor.getX() + 0.5D, anchor.getY() - 2.0D, anchor.getZ() + 0.5D);
        TestMineRecord mine = runtime.registerThreatSite(player, new WorldObjectId("pale_mirror:passive_mine"),
                new InfectionSourceId("pale_mirror:crimson"));

        runtime.advanceSimulation(1);
        String scenarioId = runtime.offered(runtime.audienceFor(player)).getFirst().id();
        tick(runtime, 3);

        helper.assertValueEqual(PaleMirrorSavedData.get(level.getServer().overworld()).worldState()
                .scenario(scenarioId).orElseThrow().status(), ScenarioStatus.OFFERED,
                "passive infection must not accept the player's story implicitly");
        assertBiomeStage(helper, level, mine, ThreatTier.FOOTHOLD);
        helper.assertTrue(mine.anchorId() != null,
                "canonical infection must materialize its PM controller before scenario acceptance");
        helper.assertTrue(mine.encounter().actors().isEmpty() && mine.gate().parts().isEmpty(),
                "combat actors and source gate parts must remain scenario-gated");
        helper.assertValueEqual(job(runtime, mine).operations().size(), 2,
                "the passive job must contain only overlay and PM anchor work");

        helper.assertTrue(runtime.accept(scenarioId, runtime.audienceFor(player)), "the offered story must remain actionable");
        player.setPos(anchor.getX() + 0.5D, anchor.getY() + 2.0D, anchor.getZ() + 0.5D);
        tick(runtime, 3);
        helper.assertValueEqual(PaleMirrorSavedData.get(level.getServer().overworld()).worldState()
                .scenario(scenarioId).orElseThrow().status(), ScenarioStatus.RECOVER,
                "entering the visible infected mine must activate the encounter stage");
        helper.assertTrue(job(runtime, mine).operations().size() > 2,
                "scenario activation must supersede the completed passive job with pinned encounter work");
        helper.succeed();
    }
    @GameTest(batch = "pm-development-persistence", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void projectEscrowSurvivesRestartWithoutBecomingSettlementStock(GameTestHelper helper) {
        if (GameTestProfiles.createAdapterOnly()) { helper.succeed(); return; }
        PaleMirrorSavedData data = PaleMirrorSavedData.get(helper.getLevel().getServer().overworld());
        resetPaleMirrorState(helper.getLevel());
        WorldObjectId communityId = new WorldObjectId("pale_mirror:test_community");
        GameTestStateReset.registerMinimalCommunity(data, communityId);
        WorldObjectId projectSiteId = new WorldObjectId(communityId.value() + "_fixture_destination");
        var intent = new io.farfrontier.palemirror.domain.DevelopmentIntent("pm:test:project",
                communityId,
                io.farfrontier.palemirror.domain.DevelopmentIntentType.UPGRADE_STOREHOUSE,
                projectSiteId, io.farfrontier.palemirror.domain.ResourceKind.IRON,
                24, 8, 0, 7, 24, java.util.Set.of("pm:receipt:one"), "test-v2",
                io.farfrontier.palemirror.domain.DevelopmentIntentState.PLANNED, "");
        new io.farfrontier.palemirror.domain.DomainServices().commands().execute(data.worldState(),
                new io.farfrontier.palemirror.domain.DomainCommand.RegisterDevelopmentIntent(intent, "gametest:fixture"));

        CompoundTag snapshot = data.save(new CompoundTag(), helper.getLevel().registryAccess());
        var reloaded = PaleMirrorSavedData.load(snapshot, helper.getLevel().registryAccess())
                .worldState().developmentIntent(intent.id()).orElseThrow();

        helper.assertValueEqual(reloaded.contributedAmount(), 8, "restart must retain physical project escrow");
        helper.assertValueEqual(reloaded.remainingAmount(), 16, "restart must retain only the unfunded remainder");
        helper.assertTrue(reloaded.contributionReceipts().contains("pm:receipt:one"),
                "restart must retain receipt deduplication authority");
        helper.succeed();
    }
    @SuppressWarnings("removal")
    @GameTest(batch = "pm-core-recovery", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 100)
    public static void testMineRecoversAfterObservedControllerDeath(GameTestHelper helper) {
        if (GameTestProfiles.createAdapterOnly()) { helper.succeed(); return; }
        ServerLevel level = helper.getLevel();
        PaleMirrorRuntime runtime = PaleMirrorRuntime.forServer(level.getServer());
        BlockPos anchor = helper.absolutePos(new BlockPos(0, 3, 0));
        resetPaleMirrorState(level);
        clearMineVolume(level, anchor);

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setPos(anchor.getX() + 0.5, anchor.getY() - 2, anchor.getZ() + 0.5);
        TestMineRecord mine = runtime.registerThreatSite(player, new WorldObjectId("pale_mirror:test_mine"), new InfectionSourceId("pale_mirror:crimson"));

        runtime.advanceSimulation(1);
        var offers = runtime.offered(runtime.audienceFor(player));
        if (offers.isEmpty()) {
            throw new AssertionError("expected core scenario offer after MineInfected; " + runtime.status());
        }
        String scenarioId = offers.getFirst().id();
        helper.assertTrue(runtime.accept(scenarioId, runtime.audienceFor(player)), "scenario must be accepted by its audience");

        player.setPos(anchor.getX() + 0.5, anchor.getY() + 2, anchor.getZ() + 0.5);
        tick(runtime, 2);
        CompoundTag persisted = PaleMirrorSavedData.get(level.getServer().overworld()).save(new CompoundTag(), level.registryAccess());
        helper.assertValueEqual(persisted.getInt("schemaVersion"), 39,
                "fresh-world visual-framework snapshot must record schema v39 before physical work continues");
        PaleMirrorSavedData reloaded = PaleMirrorSavedData.load(persisted, level.registryAccess());
        CompoundTag incompatible = persisted.copy();
        incompatible.putInt("schemaVersion", 19);
        try {
            PaleMirrorSavedData.load(incompatible, level.registryAccess());
            throw new AssertionError("schema v19 must fail closed at the actor-model boundary");
        } catch (IllegalStateException expected) {
            helper.assertTrue(expected.getMessage().contains("Back up the old world"), "schema rejection must explain recovery");
        }
        TestMineRecord reloadedMine = reloaded.testMines().get(mine.id());
        var reloadedJob = reloaded.materializationJobs().activeFor(mine.id().value(), "threat").orElse(null);
        helper.assertTrue(reloadedMine != null && reloadedJob != null,
                "current snapshot must retain the persisted materialization job");
        helper.assertValueEqual(reloadedJob.nextOperationIndex(), 1,
                "current snapshot must retain completed operation progress");
        tick(runtime, 3);

        helper.assertValueEqual(PaleMirrorSavedData.get(level.getServer().overworld()).worldState()
                .facility(mine.id()).orElseThrow().status(), FacilityStatus.INFECTED, "facility status after materialization");
        helper.assertValueEqual(PaleMirrorSavedData.get(level.getServer().overworld()).worldState()
                .scenario(scenarioId).orElseThrow().status(), ScenarioStatus.RECOVER, "scenario status after entering mine");
        helper.assertTrue(mine.anchorId() != null, "PM-owned anchor must be materialized exactly once");
        boolean crimsonAvailable = AdapterRegistry.sourceAdapter(new InfectionSourceId("pale_mirror:crimson")).health().status()
                == io.farfrontier.palemirror.api.AdapterHealth.Status.AVAILABLE;
        UUID crimsonActorId = crimsonAvailable ? mine.encounter().actor("guard_human").orElseThrow().entityId() : null;
        if (crimsonAvailable) {
            LivingEntity crimsonActor = (LivingEntity) level.getEntity(crimsonActorId);
            helper.assertTrue(crimsonActor != null, "sandbox profile must materialize its persisted actor UUID");
            helper.assertTrue(crimsonActor.getTags().contains("Crimsonified_Human"),
                    "sandbox actor must receive Crimson's local actor protocol");
            helper.assertValueEqual(job(runtime, mine).operations().get(2).state().name(), "COMPLETED",
                    "available Crimson actor operation must verify its postcondition");
            CompoundTag actorSnapshot = PaleMirrorSavedData.get(level.getServer().overworld())
                    .save(new CompoundTag(), level.registryAccess());
            PaleMirrorSavedData actorReloaded = PaleMirrorSavedData.load(actorSnapshot, level.registryAccess());
            helper.assertValueEqual(actorReloaded.testMines().get(mine.id()).encounter().actor("guard_human")
                    .orElseThrow().entityId(), crimsonActorId, "restart snapshot must retain Crimson actor identity");
            crimsonActor.die(level.damageSources().generic());
            helper.assertValueEqual(mine.encounter().actor("guard_human").orElseThrow().status().name(), "DEFEATED",
                    "actor death must be observed without resolving the PM-owned controller");
            helper.assertValueEqual(PaleMirrorSavedData.get(level.getServer().overworld()).worldState()
                    .facility(mine.id()).orElseThrow().status(), FacilityStatus.INFECTED,
                    "optional actor death must not change canonical threat state");
            tick(runtime, 2);
            helper.assertValueEqual(mine.encounter().actor("guard_human").orElseThrow().status().name(), "DEFEATED",
                    "a defeated actor must not reactivate until PM changes the desired revision");
            helper.assertValueEqual(mine.encounter().actor("guard_human").orElseThrow().entityId(), crimsonActorId,
                    "a defeated actor must not receive a replacement UUID before a new PM revision");
        } else {
            helper.assertValueEqual(mine.encounter().state(), EncounterState.DEGRADED,
                    "missing Crimson capability must degrade only encounter presentation");
            helper.assertValueEqual(job(runtime, mine).operations().get(2).state().name(), "DEGRADED",
                    "optional Crimson operation must be persisted as degraded rather than blocking recovery");
        }
        helper.assertValueEqual(mine.object().lifecycle(), WorldObjectLifecycle.ACTIVE,
                "registry must record the active physical representation");
        helper.assertValueEqual(mine.nodeCells().size(), 4, "v2 mine must reserve exactly four Node cells");
        helper.assertValueEqual(mine.biomeCells().size(), 66, "v2 mine must reserve the bounded staged-biome cells");
        assertBiomeStage(helper, level, mine, ThreatTier.FOOTHOLD);

        CompoundTag legacy = PaleMirrorSavedData.get(level.getServer().overworld()).save(new CompoundTag(), level.registryAccess()).copy();
        legacy.putInt("schemaVersion", 15);
        try {
            PaleMirrorSavedData.load(legacy, level.registryAccess());
            throw new AssertionError("schema v15 must not be accepted as a v19 observed-settlement snapshot");
        } catch (IllegalStateException expected) {
            helper.assertTrue(expected.getMessage().contains("Back up the old world"),
                    "legacy snapshot rejection must explain the safe recovery boundary");
        }

        LivingEntity anchorEntity = (LivingEntity) level.getEntity(mine.anchorId());
        helper.assertTrue(anchorEntity != null, "materialized anchor must be present by its registered UUID");
        anchorEntity.die(level.damageSources().generic());

        helper.assertValueEqual(PaleMirrorSavedData.get(level.getServer().overworld()).worldState()
                .facility(mine.id()).orElseThrow().status(), FacilityStatus.RECOVERING, "facility status after observed controller death");
        runtime.advanceSimulation(1);
        tick(runtime, 5);

        helper.assertValueEqual(PaleMirrorSavedData.get(level.getServer().overworld()).worldState()
                .facility(mine.id()).orElseThrow().status(), FacilityStatus.OPERATIONAL, "facility status after recovery simulation");
        helper.assertValueEqual(PaleMirrorSavedData.get(level.getServer().overworld()).worldState()
                .scenario(scenarioId).orElseThrow().status(), ScenarioStatus.RESOLVED, "scenario must resolve exactly once");
        helper.assertTrue(mine.anchorId() == null, "destroyed anchor reference must be cleared");
        if (crimsonActorId != null) helper.assertTrue(level.getEntity(crimsonActorId) == null,
                "cleanup must remove only the PM-owned Crimson actor");
        helper.assertValueEqual(mine.object().lifecycle(), WorldObjectLifecycle.REPRESENTED,
                "registry must retain the mine after its active threat is removed");
        helper.assertValueEqual(PaleMirrorSavedData.get(level.getServer().overworld()).worldState()
                .facility(mine.id()).orElseThrow().observedRevision(), 3L,
                "only a verified materialization job may advance the observed revision");
        helper.assertTrue(mine.mutableCells().stream().allMatch(cell -> level.getBlockState(cell.position()).is(Blocks.DEEPSLATE_BRICKS)),
                "overlay cleanup must restore only PM-owned baseline cells");
        helper.succeed();
    }
    @SuppressWarnings("removal")
    @GameTest(batch = "pm-core-items", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 40)
    public static void excludedSourceItemIsQuarantinedAndCannotApplyMelee(GameTestHelper helper) {
        if (GameTestProfiles.createAdapterOnly()) { helper.succeed(); return; }
        ServerLevel level = helper.getLevel();
        resetPaleMirrorState(level);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ItemStack legacyCrimsonStack = new ItemStack(Items.NETHERITE_SWORD);
        legacyCrimsonStack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(5_450_080));
        helper.assertTrue(SourceItemFirewall.blocks(legacyCrimsonStack),
                "private Crimson item model range must be classified as excluded content");
        Zombie target = new Zombie(EntityType.ZOMBIE, level);
        target.setPos(player.getX() + 1.0D, player.getY(), player.getZ());
        level.addFreshEntity(target);
        helper.assertTrue(!legacyCrimsonStack.hurtEnemy(target, player),
                "ItemStack firewall must deny legacy source melee before item behavior runs");
        PaleMirrorRuntime.forServer(level.getServer()).quarantineLegacyItem(player, "crimson", "minecraft:netherite_sword#model=5450080",
                "GameTest legacy stack");
        helper.assertValueEqual(PaleMirrorSavedData.get(level.getServer().overworld()).quarantine().records().size(), 1,
                "legacy item quarantine must be persisted separately from canonical PM world state");
        helper.succeed();
    }
    @SuppressWarnings("removal")
    @GameTest(batch = "pm-core-biome", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 100)
    public static void infectionBiomeProgressesByPmTierAndFailsClosedOnConflict(GameTestHelper helper) {
        if (GameTestProfiles.createAdapterOnly()) { helper.succeed(); return; }
        ServerLevel level = helper.getLevel();
        PaleMirrorRuntime runtime = PaleMirrorRuntime.forServer(level.getServer());
        BlockPos anchor = helper.absolutePos(new BlockPos(0, 3, 0));
        resetPaleMirrorState(level);
        clearMineVolume(level, anchor);

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setPos(anchor.getX() + 0.5D, anchor.getY() - 2.0D, anchor.getZ() + 0.5D);
        TestMineRecord mine = runtime.registerThreatSite(player, new WorldObjectId("pale_mirror:test_mine"), new InfectionSourceId("pale_mirror:crimson"));
        runtime.advanceSimulation(1);
        String scenarioId = runtime.offered(runtime.audienceFor(player)).getFirst().id();
        helper.assertTrue(runtime.accept(scenarioId, runtime.audienceFor(player)), "scenario must be accepted");
        player.setPos(anchor.getX() + 0.5D, anchor.getY() + 2.0D, anchor.getZ() + 0.5D);
        tick(runtime, 3);
        assertBiomeStage(helper, level, mine, ThreatTier.FOOTHOLD);

        runtime.advanceSimulation(12);
        tick(runtime, 2);
        assertBiomeStage(helper, level, mine, ThreatTier.INFESTED);

        runtime.advanceSimulation(24);
        tick(runtime, 2);
        assertBiomeStage(helper, level, mine, ThreatTier.SIEGE);

        runtime.advanceSimulation(36);
        tick(runtime, 2);
        assertBiomeStage(helper, level, mine, ThreatTier.APEX);

        helper.succeed();
    }
    @SuppressWarnings("removal")
    @GameTest(batch = "pm-core-biome-conflict", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 100)
    public static void infectionBiomeDoesNotOverwriteFuturePlayerChanges(GameTestHelper helper) {
        if (GameTestProfiles.createAdapterOnly()) { helper.succeed(); return; }
        ServerLevel level = helper.getLevel();
        PaleMirrorRuntime runtime = PaleMirrorRuntime.forServer(level.getServer());
        BlockPos anchor = helper.absolutePos(new BlockPos(0, 3, 0));
        resetPaleMirrorState(level);
        clearMineVolume(level, anchor);

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setPos(anchor.getX() + 0.5D, anchor.getY() - 2.0D, anchor.getZ() + 0.5D);
        TestMineRecord mine = runtime.registerThreatSite(player, new WorldObjectId("pale_mirror:test_mine"), new InfectionSourceId("pale_mirror:crimson"));
        MutableCell futureCell = mine.biomeCells().stream()
                .filter(cell -> cell.infectionStage() == InfectionBiomeStage.INFESTED).findFirst().orElseThrow();

        runtime.advanceSimulation(1);
        String scenarioId = runtime.offered(runtime.audienceFor(player)).getFirst().id();
        helper.assertTrue(runtime.accept(scenarioId, runtime.audienceFor(player)), "scenario must be accepted");
        player.setPos(anchor.getX() + 0.5D, anchor.getY() + 2.0D, anchor.getZ() + 0.5D);
        tick(runtime, 3);
        assertBiomeStage(helper, level, mine, ThreatTier.FOOTHOLD);
        level.setBlock(futureCell.position(), Blocks.GOLD_BLOCK.defaultBlockState(), 3);
        helper.assertValueEqual(level.getBlockState(futureCell.position()).getBlock(), Blocks.GOLD_BLOCK,
                "FOOTHOLD must not claim an inactive future cell");

        runtime.advanceSimulation(12);
        tick(runtime, 2);
        helper.assertTrue(futureCell.conflicted(), "PM must mark the future cell conflicted when INFESTED tries to own it");
        helper.assertValueEqual(level.getBlockState(futureCell.position()).getBlock(), Blocks.GOLD_BLOCK,
                "PM must not overwrite an unknown/player-owned block");
        helper.assertValueEqual(job(runtime, mine).state().name(), "BLOCKED", "conflict must visibly block the persisted job");
        helper.succeed();
    }
    @SuppressWarnings("removal")
    @GameTest(batch = "pm-crimson-roster", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 100)
    public static void crimsonBaseRosterMaterializesByPmTier(GameTestHelper helper) {
        if (GameTestProfiles.createAdapterOnly()) { helper.succeed(); return; }
        if (AdapterRegistry.sourceAdapter(new InfectionSourceId("pale_mirror:crimson")).health().status() != io.farfrontier.palemirror.api.AdapterHealth.Status.AVAILABLE) {
            helper.succeed();
            return;
        }
        ServerLevel level = helper.getLevel();
        PaleMirrorRuntime runtime = PaleMirrorRuntime.forServer(level.getServer());
        BlockPos anchor = helper.absolutePos(new BlockPos(0, 3, 0));
        resetPaleMirrorState(level);
        clearMineVolume(level, anchor);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setPos(anchor.getX() + 0.5, anchor.getY() - 2, anchor.getZ() + 0.5);
        TestMineRecord mine = runtime.registerThreatSite(player, new WorldObjectId("pale_mirror:test_mine"), new InfectionSourceId("pale_mirror:crimson"));
        runtime.advanceSimulation(1);
        String scenarioId = runtime.offered(runtime.audienceFor(player)).getFirst().id();
        helper.assertTrue(runtime.accept(scenarioId, runtime.audienceFor(player)), "scenario must be accepted");
        player.setPos(anchor.getX() + 0.5, anchor.getY() + 2, anchor.getZ() + 0.5);

        tick(runtime, 5);
        advanceTier(runtime, 12, 8);
        advanceTier(runtime, 24, 11);
        advanceTier(runtime, 36, 22);

        var facility = PaleMirrorSavedData.get(level.getServer().overworld()).worldState().facility(mine.id()).orElseThrow();
        helper.assertValueEqual(facility.threatTier(), ThreatTier.APEX, "PM simulation must reach APEX without Crimson phases");
        helper.assertValueEqual(mine.encounter().actors().size(), 16, "APEX roster must contain audited base, Decayed and special profiles");
        for (var actorRef : mine.encounter().actors()) {
            LivingEntity actor = (LivingEntity) level.getEntity(actorRef.entityId());
            helper.assertTrue(actor != null && actorRef.status().name().equals("ACTIVE"),
                    "each tier-selected profile must materialize exactly once: " + actorRef.slotId());
            helper.assertTrue(actor.getPersistentData().getString(CrimsonSandboxAdapter.PROFILE_KEY)
                    .equals(actorRef.actorProfileId()), "actor profile provenance must survive materialization");
            helper.assertTrue(mine.contains(actor.blockPosition()), "actor must remain inside its PM threat-site bounds");
        }
        Mob rusher = (Mob) level.getEntity(mine.encounter().actor("rusher").orElseThrow().entityId());
        helper.assertValueEqual(rusher.getType(), EntityType.RAVAGER, "Rusher must use its audited Ravager local form");
        player.setPos(anchor.getX() - 3.5D, rusher.getY(), anchor.getZ() - 3.5D);
        rusher.setDeltaMovement(Vec3.ZERO);
        mine.encounter().scheduleRuntime("rusher", 0L);
        runtime.tick();
        helper.assertTrue(rusher.getDeltaMovement().horizontalDistanceSqr() > 0.1D,
                "PM-owned Rusher behavior must dash only toward a player inside its threat site");
        helper.assertTrue(rusher.getPassengers().stream().anyMatch(value -> value.getTags().contains("PM_Crimson_Dash_Pose")),
                "PM-owned Rusher dash must create the bounded CEM dash-pose marker");
        rusher.moveTo(anchor.getX() + 20.5D, rusher.getY(), anchor.getZ() + 20.5D, 0.0F, 0.0F);
        mine.encounter().scheduleRuntime("rusher", 0L);
        runtime.tick();
        helper.assertTrue(mine.contains(rusher.blockPosition()), "PM runtime must return an escaping actor to its owned bounds");
        Mob raptor = (Mob) level.getEntity(mine.encounter().actor("raptor").orElseThrow().entityId());
        helper.assertValueEqual(raptor.getType(), EntityType.ZOMBIE, "Raptor must use its audited Zombie local form");
        player.setPos(raptor.getX(), raptor.getY(), raptor.getZ());
        mine.encounter().scheduleRuntime("raptor", 0L);
        runtime.tick();
        helper.assertTrue(player.hasEffect(net.minecraft.world.effect.MobEffects.POISON),
                "PM-owned Raptor behavior must affect only its selected in-site player target");
        helper.assertTrue(raptor.hasEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY),
                "PM-owned Raptor behavior must retain its local invisibility");
        helper.succeed();
    }

    @SuppressWarnings("removal")
    @GameTest(batch = "pm-crimson-shadow", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 40)
    public static void crimsonSandboxShadowsGlobalTick(GameTestHelper helper) {
        if (GameTestProfiles.createAdapterOnly()) { helper.succeed(); return; }
        if (AdapterRegistry.sourceAdapter(new InfectionSourceId("pale_mirror:crimson")).health().status() != io.farfrontier.palemirror.api.AdapterHealth.Status.AVAILABLE) {
            helper.succeed();
            return;
        }
        ServerLevel level = helper.getLevel();
        Zombie actor = EntityType.ZOMBIE.create(level);
        helper.assertTrue(actor != null, "test zombie must be constructible");
        actor.moveTo(helper.absolutePos(new BlockPos(0, 2, 0)), 0.0F, 0.0F);
        actor.addTag("PM_Crimson_Global_Tick_Test");
        actor.addTag("Crimsonified_Human");
        helper.assertTrue(level.addFreshEntity(actor), "test zombie must enter the level");
        runCommand(level, "scoreboard objectives add Mass dummy");
        runCommand(level, "scoreboard objectives add Second_Timer dummy");
        runCommand(level, "scoreboard objectives add Aggro dummy");
        runCommand(level, "scoreboard players set @e[tag=PM_Crimson_Global_Tick_Test,limit=1] Second_Timer 2");
        runCommand(level, "scoreboard players set @e[tag=PM_Crimson_Global_Tick_Test,limit=1] Aggro 0");
        runCommand(level, "scoreboard players set Second Mass 2");
        helper.runAfterDelay(5, () -> {
            runCommand(level, "execute as @e[tag=PM_Crimson_Global_Tick_Test,limit=1] if score @s Aggro matches 1.. run tag @s add PM_Crimson_Global_Tick_Leaked");
            helper.assertTrue(!actor.getTags().contains("PM_Crimson_Global_Tick_Leaked"),
                    "shadowed crimson_curse:tick must not execute Crimson global actor processing");
            actor.discard();
            helper.succeed();
        });
    }

    private static void clearMineVolume(ServerLevel level, BlockPos anchor) {
        for (int x = -4; x <= 4; x++) {
            for (int y = 0; y <= 4; y++) {
                for (int z = -4; z <= 4; z++) {
                    level.setBlock(anchor.offset(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
    }

    private static void resetPaleMirrorState(ServerLevel level) {
        PaleMirrorSavedData data = PaleMirrorSavedData.get(level.getServer().overworld());
        GameTestStateReset.resetAll(data);
    }

    private static void assertBiomeStage(GameTestHelper helper, ServerLevel level, TestMineRecord mine, ThreatTier tier) {
        for (MutableCell cell : mine.biomeCells()) {
            helper.assertValueEqual(level.getBlockState(cell.position()).getBlock(), expectedBiomeBlock(cell.infectionStage(), tier),
                    "biome palette must match PM tier at " + cell.position());
        }
        helper.assertTrue(mine.nodeCells().stream().allMatch(cell -> level.getBlockState(cell.position()).is(Blocks.DEEPSLATE_BRICKS)
                        || level.getBlockState(cell.position()).is(Blocks.SEA_LANTERN)),
                "biome materialization must not use Node cells as decorative overlays");
    }

    private static net.minecraft.world.level.block.Block expectedBiomeBlock(InfectionBiomeStage stage, ThreatTier tier) {
        if (!stage.activeAt(tier)) return Blocks.DEEPSLATE_BRICKS;
        return switch (stage) {
            case FOOTHOLD -> switch (tier) {
                case FOOTHOLD -> Blocks.NETHERRACK;
                case INFESTED -> Blocks.CRIMSON_NYLIUM;
                case SIEGE, APEX -> Blocks.NETHER_WART_BLOCK;
                case DORMANT -> Blocks.DEEPSLATE_BRICKS;
            };
            case INFESTED -> switch (tier) {
                case INFESTED -> Blocks.NETHERRACK;
                case SIEGE, APEX -> Blocks.CRIMSON_NYLIUM;
                case DORMANT, FOOTHOLD -> Blocks.DEEPSLATE_BRICKS;
            };
            case SIEGE -> switch (tier) {
                case SIEGE -> Blocks.NETHERRACK;
                case APEX -> Blocks.NETHER_WART_BLOCK;
                case DORMANT, FOOTHOLD, INFESTED -> Blocks.DEEPSLATE_BRICKS;
            };
            case APEX -> tier == ThreatTier.APEX ? Blocks.SHROOMLIGHT : Blocks.DEEPSLATE_BRICKS;
            case NODE -> Blocks.DEEPSLATE_BRICKS;
        };
    }

    private static void tick(PaleMirrorRuntime runtime, int count) {
        for (int index = 0; index < count; index++) runtime.tick();
    }

    private static io.farfrontier.palemirror.internal.materialization.MaterializationJob job(
            PaleMirrorRuntime runtime, TestMineRecord mine) {
        var job = runtime.materializationJob(mine.id());
        if (job == null) throw new AssertionError("Missing materialization job for " + mine.id());
        return job;
    }

    private static void advanceTier(PaleMirrorRuntime runtime, int steps, int materializationTicks) {
        runtime.advanceSimulation(steps);
        tick(runtime, materializationTicks);
    }

    private static void runCommand(ServerLevel level, String command) {
        level.getServer().getCommands().performPrefixedCommand(
                level.getServer().createCommandSourceStack().withSuppressedOutput().withPermission(4), command);
    }
}
