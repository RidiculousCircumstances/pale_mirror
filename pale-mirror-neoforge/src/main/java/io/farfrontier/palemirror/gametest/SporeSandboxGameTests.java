package io.farfrontier.palemirror.gametest;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.domain.FacilityStatus;
import io.farfrontier.palemirror.domain.InfectionSourceId;
import io.farfrontier.palemirror.domain.WorldObjectId;
import io.farfrontier.palemirror.domain.ThreatTier;
import io.farfrontier.palemirror.internal.PaleMirrorRuntime;
import io.farfrontier.palemirror.internal.adapter.AdapterRegistry;
import io.farfrontier.palemirror.internal.integration.spore.SporeSandboxAdapter;
import io.farfrontier.palemirror.internal.integration.spore.SporeRuntimeFirewall;
import io.farfrontier.palemirror.internal.adapter.SourceItemFirewall;
import io.farfrontier.palemirror.internal.combat.PmProjectileRef;
import io.farfrontier.palemirror.internal.combat.PmProjectileRuntime;
import io.farfrontier.palemirror.internal.observation.EncounterActorDestroyed;
import io.farfrontier.palemirror.internal.world.InfectionBiomeStage;
import io.farfrontier.palemirror.internal.world.PaleMirrorSavedData;
import io.farfrontier.palemirror.internal.world.TestMineRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.core.component.DataComponents;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Verifies the isolated Spore vertical slice against the pinned native runtime when present. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SporeSandboxGameTests {
    private SporeSandboxGameTests() { }

    @SuppressWarnings("removal")
    @GameTest(batch = "pm-spore-sandbox", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 140)
    public static void pmOwnsSporeSiteAndKeepsNativeFormsDormant(GameTestHelper helper) {
        if (GameTestProfiles.createAdapterOnly()) { helper.succeed(); return; }
        if (AdapterRegistry.sourceAdapter(new InfectionSourceId("pale_mirror:spore")).health().status() != io.farfrontier.palemirror.api.AdapterHealth.Status.AVAILABLE) {
            helper.succeed();
            return;
        }
        ServerLevel level = helper.getLevel();
        helper.assertTrue(SporeRuntimeFirewall.hookObserved(),
                "exact-version Spore runtime hooks must be active before PM materializes a source actor");
        PaleMirrorRuntime runtime = PaleMirrorRuntime.forServer(level.getServer());
        ItemStack crimsonEncodedLegacyItem = new ItemStack(Items.NETHERITE_SWORD);
        crimsonEncodedLegacyItem.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(5_450_080));
        helper.assertValueEqual(SourceItemFirewall.classify(crimsonEncodedLegacyItem).orElseThrow().sourceId(), "pale_mirror:crimson",
                "excluded Crimson custom-model stacks must be identified before source use hooks can run");
        BlockPos playerStart = helper.absolutePos(new BlockPos(0, 3, 0));
        BlockPos siteAnchor = playerStart.above(2).offset(16, 0, 0);
        reset(level);
        clearMineVolume(level, siteAnchor);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setPos(playerStart.getX() + 0.5D, playerStart.getY(), playerStart.getZ() + 0.5D);
        TestMineRecord site = runtime.registerThreatSite(player, new WorldObjectId("pale_mirror:spore_test_mine"), new InfectionSourceId("pale_mirror:spore"));
        helper.assertValueEqual(site.anchor(), siteAnchor, "Spore source placement must be deterministic and non-overlapping");

        runtime.advanceSimulation(1);
        String scenarioId = runtime.offered(runtime.audienceFor(player)).getFirst().id();
        helper.assertValueEqual(PaleMirrorSavedData.get(level.getServer().overworld()).worldState().facility(site.id()).orElseThrow()
                .infectionSource(), new InfectionSourceId("pale_mirror:spore"), "source must be canonical facility state");
        helper.assertTrue(runtime.accept(scenarioId, runtime.audienceFor(player)), "Spore scenario must use the normal PM acceptance flow");
        player.setPos(siteAnchor.getX() + 0.5D, siteAnchor.getY() + 2.0D, siteAnchor.getZ() + 0.5D);
        // The persisted job now has overlay, anchor and two foothold actor
        // operations.  Keep this test's wait explicit rather than relaxing
        // the scheduler's one-operation-per-site work budget.
        tick(runtime, 7);

        LivingEntity human = (LivingEntity) level.getEntity(site.encounter().actor("dormant_infected_human").orElseThrow().entityId());
        LivingEntity husk = (LivingEntity) level.getEntity(site.encounter().actor("dormant_infected_husk").orElseThrow().entityId());
        helper.assertValueEqual(site.encounter().compositionId(), "foothold_patrol",
                "PM must persist the selected exact-tier composition before materializing native actors");
        helper.assertTrue(human != null, "Spore foothold must materialize exactly one persisted native actor");
        helper.assertTrue(husk != null && husk instanceof net.minecraft.world.entity.Mob huskMob && huskMob.isNoAi(),
                "foothold composition must materialize a second constrained native form");
        helper.assertValueEqual(BuiltInRegistries.ENTITY_TYPE.getKey(human.getType()).toString(), "spore:inf_human",
                "Spore adapter must use only its pinned registered entity contract");
        helper.assertTrue(human instanceof net.minecraft.world.entity.Mob mob && mob.isNoAi(),
                "PM must keep a native Spore form constrained so it cannot own spread or terrain conversion");
        helper.assertValueEqual(human.getPersistentData().getString(SporeSandboxAdapter.PROFILE_KEY),
                "pale_mirror:spore_infected_human", "native actor must retain PM source provenance");
        human.moveTo(site.anchor().getX() + 0.5D, site.anchor().getY() + 1.0D, site.anchor().getZ() + 0.5D);
        site.encounter().scheduleMovement("dormant_infected_human", 0L, 0);
        runtime.tick();
        helper.assertTrue(site.contains(human.blockPosition()) && !human.blockPosition().equals(site.anchor()),
                "PM patrol must move a constrained form only to a safe cell inside its registered site");
        helper.assertTrue(human instanceof net.minecraft.world.entity.Mob patrolMob && patrolMob.isNoAi(),
                "PM movement must not enable native Spore AI");
        runtime.publish(new EncounterActorDestroyed("forged-wrong-source", site.id(), new InfectionSourceId("pale_mirror:crimson"),
                "dormant_infected_human", human.getUUID()));
        helper.assertValueEqual(site.encounter().actor("dormant_infected_human").orElseThrow().status().name(), "ACTIVE",
                "reconciliation must reject an actor observation whose source disagrees with canonical facility state");
        var footholdCell = site.biomeCells().stream().filter(cell -> cell.infectionStage() == InfectionBiomeStage.FOOTHOLD)
                .findFirst().orElseThrow();
        helper.assertValueEqual(level.getBlockState(footholdCell.position()).getBlock(), Blocks.MOSS_BLOCK,
                "Spore foothold must use PM's bounded fungal palette rather than native terrain spread");
        CompoundTag snapshot = PaleMirrorSavedData.get(level.getServer().overworld()).save(new CompoundTag(), level.registryAccess());
        helper.assertValueEqual(PaleMirrorSavedData.load(snapshot, level.registryAccess()).worldState().facility(site.id()).orElseThrow()
                .infectionSource(), new InfectionSourceId("pale_mirror:spore"), "restart snapshot must retain the canonical source identity");
        CompoundTag legacySnapshot = snapshot.copy();
        legacySnapshot.putInt("schemaVersion", 16);
        try {
            PaleMirrorSavedData.load(legacySnapshot, level.registryAccess());
            throw new AssertionError("schema v16 must not be inferred as a v19 observed-settlement snapshot");
        } catch (IllegalStateException expected) {
            helper.assertTrue(expected.getMessage().contains("requires a new world"),
                    "legacy snapshot rejection must preserve the explicit new-world boundary");
        }

        int initialCombatHealth = site.encounter().actor("dormant_infected_human").orElseThrow().combatHitPoints();
        helper.assertValueEqual(initialCombatHealth, 18, "PM must persist the audited Spore combat health instead of native health");
        player.setPos(human.getX(), human.getY(), human.getZ() + 1.0D);
        helper.assertTrue(!human.hurt(level.damageSources().playerAttack(player), 3.0F),
                "PM must consume a player hit before Spore native damage hooks run");
        helper.assertValueEqual(site.encounter().actor("dormant_infected_human").orElseThrow().combatHitPoints(),
                initialCombatHealth - 3, "PM-owned encounter health must record a non-lethal player hit");
        CompoundTag combatSnapshot = PaleMirrorSavedData.get(level.getServer().overworld()).save(new CompoundTag(), level.registryAccess());
        helper.assertValueEqual(PaleMirrorSavedData.load(combatSnapshot, level.registryAccess()).testMines().get(site.id())
                .encounter().actor("dormant_infected_human").orElseThrow().combatHitPoints(), initialCombatHealth - 3,
                "restart snapshot must retain PM-owned Spore combat health");
        helper.assertTrue(PaleMirrorSavedData.load(combatSnapshot, level.registryAccess()).testMines().get(site.id())
                        .encounter().actor("dormant_infected_human").orElseThrow().nextMovementTick() > 0,
                "restart snapshot must retain PM movement scheduling independently from combat");
        int actionsBefore = site.encounter().actor("dormant_infected_human").orElseThrow().actionCounter();
        site.encounter().scheduleRuntime("dormant_infected_human", 0L);
        runtime.tick();
        var afterCombatAction = site.encounter().actor("dormant_infected_human").orElseThrow();
        helper.assertValueEqual(afterCombatAction.actionCounter(), actionsBefore + 2,
                "PM constrained-combat runtime must schedule one bounded attack while a player is inside the site");
        helper.assertTrue(afterCombatAction.nextRuntimeTick() > 0,
                "PM must persist the next combat cooldown rather than relying on native Spore AI");
        var attackLease = PaleMirrorSavedData.get(level.getServer().overworld()).effectLeases().leases().stream()
                .filter(lease -> lease.kind().equals("direct_attack") && lease.facilityId().equals(site.id().value()))
                .findFirst().orElseThrow();
        helper.assertValueEqual(attackLease.state().name(), "COMPLETED",
                "Spore damage must pass through a persisted PM effect lease before touching the player");
        helper.assertValueEqual(PaleMirrorSavedData.load(PaleMirrorSavedData.get(level.getServer().overworld())
                        .save(new CompoundTag(), level.registryAccess()), level.registryAccess()).effectLeases().find(attackLease.id())
                        .orElseThrow().state().name(), "COMPLETED",
                "completed PM effect leases must survive a restart snapshot for deduplication");

        int afterPlayerHit = site.encounter().actor("dormant_infected_human").orElseThrow().combatHitPoints();
        helper.assertTrue(!human.hurt(level.damageSources().generic(), 10_000.0F),
                "PM must consume an external vanilla damage source before native Spore death can run");
        helper.assertTrue(level.getEntity(human.getUUID()) == null,
                "a lethal external source must still become a PM discard without native remains");
        helper.assertValueEqual(PaleMirrorSavedData.get(level.getServer().overworld()).worldState().facility(site.id()).orElseThrow()
                .status(), FacilityStatus.INFECTED, "native form damage must not resolve the PM-owned controller");

        helper.assertTrue(level.getEntity(human.getUUID()) == null, "safe PM defeat must discard the native actor immediately");
        helper.assertValueEqual(site.encounter().actor("dormant_infected_human").orElseThrow().status().name(), "DEFEATED",
                "safe actor defeat must reconcile exactly once as a presentation-only fact");
        for (int x = -4; x <= 4; x++) for (int y = 0; y <= 4; y++) for (int z = -4; z <= 4; z++) {
            helper.assertTrue(!BuiltInRegistries.BLOCK.getKey(level.getBlockState(site.anchor().offset(x, y, z)).getBlock())
                    .getNamespace().equals("spore"), "safe PM defeat must never leave native Spore remains or growth");
        }

        runtime.advanceSimulation(12);
        tick(runtime, 6);
        helper.assertValueEqual(PaleMirrorSavedData.get(level.getServer().overworld()).worldState().facility(site.id()).orElseThrow()
                .threatTier(), ThreatTier.INFESTED, "PM simulation, not Spore evolution, must select the next roster tier");
        helper.assertValueEqual(site.encounter().compositionId(), "infested_hunt",
                "a new PM desired revision must select and persist the INFESTED composition exactly once");
        LivingEntity braiomil = (LivingEntity) level.getEntity(site.encounter().actor("dormant_braiomil").orElseThrow().entityId());
        helper.assertTrue(braiomil instanceof net.minecraft.world.entity.Mob mob && mob.isNoAi(),
                "second native form must also remain constrained under PM control");
        helper.assertValueEqual(BuiltInRegistries.ENTITY_TYPE.getKey(braiomil.getType()).toString(), "spore:braiomil",
                "PM tier selection must materialize the second pinned Spore form exactly once");
        braiomil.moveTo(site.anchor().getX() - 2.5D, site.anchor().getY() + 1.0D, site.anchor().getZ() + 0.5D);
        player.setPos(site.anchor().getX() + 3.5D, site.anchor().getY() + 1.0D, site.anchor().getZ() + 0.5D);
        site.encounter().scheduleMovement("dormant_braiomil", 0L, 0);
        runtime.tick();
        helper.assertTrue(braiomil.getX() > site.anchor().getX() - 2.5D && site.contains(braiomil.blockPosition()),
                "PM pursuit must take a bounded safe step toward a player without native navigation");
        helper.assertValueEqual(site.encounter().actor("dormant_infected_human").orElseThrow().status().name(), "DEFEATED",
                "a safely defeated Spore actor must not respawn when PM later changes its desired revision");
        net.minecraft.world.entity.EntityType<?> huskType = BuiltInRegistries.ENTITY_TYPE.get(net.minecraft.resources.ResourceLocation.parse("spore:inf_husk"));
        net.minecraft.world.entity.Entity unmanaged = huskType.create(level);
        helper.assertTrue(unmanaged != null && !level.addFreshEntity(unmanaged),
                "full PM isolation must reject a native Spore entity without PM provenance");
        runtime.advanceSimulation(24);
        tick(runtime, 10);
        helper.assertValueEqual(PaleMirrorSavedData.get(level.getServer().overworld()).worldState().facility(site.id()).orElseThrow()
                .threatTier(), ThreatTier.SIEGE, "PM must progress to the SIEGE composition without invoking Spore evolution");
        helper.assertValueEqual(site.encounter().compositionId(), "siege_pressure",
                "SIEGE must use its exact persisted composition, never an eligible lower-tier fallback");
        LivingEntity spitter = (LivingEntity) level.getEntity(site.encounter().actor("dormant_spitter").orElseThrow().entityId());
        helper.assertTrue(spitter instanceof net.minecraft.world.entity.Mob spitterMob && spitterMob.isNoAi(),
                "PM must materialize the audited ranged Spore form without enabling its native projectile goal");
        helper.assertValueEqual(BuiltInRegistries.ENTITY_TYPE.getKey(spitter.getType()).toString(), "spore:spitter",
                "SIEGE composition must retain the pinned Spitter registry identity");
        // Native AcidBall remains a visual carrier only. Its PM record binds
        // exactly one player target, so a controller/anchor can never become
        // an accidental projectile target while the carrier crosses the site.
        player.setPos(spitter.getX() + 3.0D, spitter.getY(), spitter.getZ());
        PaleMirrorSavedData projectileData = PaleMirrorSavedData.get(level.getServer().overworld());
        String spitterKey = io.farfrontier.palemirror.internal.combat.ThreatCombatLedger.actorKey("spore", site.id().value(),
                "encounter", "dormant_spitter");
        helper.assertTrue(PmProjectileRuntime.launchVisualCarrier(projectileData, level, "spore", site.id().value(),
                        spitterKey, "dormant_spitter", spitter, player, "spore:acid_ball",
                        BuiltInRegistries.ENTITY_TYPE.get(net.minecraft.resources.ResourceLocation.parse("spore:acid_ball")),
                        SporeSandboxAdapter.PROJECTILE_ROLE, 5.0F, level.getServer().overworld().getGameTime(), 10_000L),
                "PM must be able to create the audited AcidBall solely through its persisted projectile pipeline");
        PmProjectileRef sporeProjectile = projectileData.threatCombat().projectiles().stream()
                .filter(ref -> ref.sourceId().equals("spore") && ref.targetId() != null && ref.targetId().equals(player.getUUID()))
                .max(java.util.Comparator.comparing(PmProjectileRef::id)).orElseThrow();
        helper.assertValueEqual(sporeProjectile.targetId(), player.getUUID(),
                "a PM Spitter projectile must persist its one selected player target");
        helper.assertValueEqual(PaleMirrorSavedData.load(projectileData.save(new CompoundTag(), level.registryAccess()), level.registryAccess())
                        .threatCombat().projectile(sporeProjectile.id()).orElseThrow().targetId(), player.getUUID(),
                "a restart snapshot must retain the projectile target rather than widening its damage authority");
        Entity carrier = level.getEntity(sporeProjectile.entityId());
        helper.assertTrue(carrier instanceof Projectile, "Spitter must materialize the pinned AcidBall only as a PM visual carrier");
        LivingEntity controllerBeforeImpact = (LivingEntity) level.getEntity(site.anchorId());
        helper.assertTrue(controllerBeforeImpact != null, "PM controller must exist before a hostile visual carrier resolves");
        float controllerHealth = controllerBeforeImpact.getHealth();
        helper.assertTrue(PmProjectileRuntime.handleImpact(level.getServer(), (Projectile) carrier,
                        new EntityHitResult(controllerBeforeImpact)),
                "PM must intercept the native visual carrier impact path");
        helper.assertTrue(Math.abs(controllerBeforeImpact.getHealth() - controllerHealth) < 0.001F,
                "a Spitter visual carrier must never damage the PM controller or another non-target entity");
        runtime.advanceSimulation(36);
        tick(runtime, 12);
        helper.assertValueEqual(PaleMirrorSavedData.get(level.getServer().overworld()).worldState().facility(site.id()).orElseThrow()
                .threatTier(), ThreatTier.APEX, "PM must select the APEX composition deterministically");
        helper.assertValueEqual(site.encounter().compositionId(), "apex_pressure",
                "APEX composition must be persisted independently from the prior SIEGE composition");
        helper.assertTrue(level.getEntity(site.encounter().actor("apex_spitter").orElseThrow().entityId()) != null,
                "APEX must materialize its additional audited pressure form exactly once");
        LivingEntity controller = (LivingEntity) level.getEntity(site.anchorId());
        helper.assertTrue(controller != null, "PM-owned controller must remain independently materialized beside native forms");
        controller.die(level.damageSources().generic());
        runtime.advanceSimulation(1);
        tick(runtime, 12);
        helper.assertValueEqual(PaleMirrorSavedData.get(level.getServer().overworld()).worldState().facility(site.id()).orElseThrow()
                .status(), FacilityStatus.OPERATIONAL, "only PM controller clearance may resolve the Spore site");
        helper.assertTrue(level.getEntity(braiomil.getUUID()) == null,
                "PM cleanup must discard its native presentation form without invoking its death path");
        helper.succeed();
    }

    private static void tick(PaleMirrorRuntime runtime, int count) {
        for (int index = 0; index < count; index++) runtime.tick();
    }

    private static void clearMineVolume(ServerLevel level, BlockPos anchor) {
        for (int x = -4; x <= 4; x++) for (int y = 0; y <= 4; y++) for (int z = -4; z <= 4; z++) {
            level.setBlock(anchor.offset(x, y, z), Blocks.AIR.defaultBlockState(), 3);
        }
    }

    private static void reset(ServerLevel level) {
        PaleMirrorSavedData data = PaleMirrorSavedData.get(level.getServer().overworld());
        data.testMines().clear();
        data.worldRegistry().clear();
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
        data.campaignRegions().clear();
        data.settlementObservations().clear();
        data.worldState().narratorCooldowns().clear();
        data.worldState().history().clear();
        data.worldState().setSimulationStep(0);
        data.worldState().setEventSequence(0);
        data.setDirty();
    }
}
