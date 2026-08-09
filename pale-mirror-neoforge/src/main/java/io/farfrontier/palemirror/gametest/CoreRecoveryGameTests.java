package io.farfrontier.palemirror.gametest;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.domain.FacilityStatus;
import io.farfrontier.palemirror.domain.ScenarioStatus;
import io.farfrontier.palemirror.domain.SettlementState;
import io.farfrontier.palemirror.domain.ThreatTier;
import io.farfrontier.palemirror.internal.PaleMirrorRuntime;
import io.farfrontier.palemirror.internal.adapter.AdapterRegistry;
import io.farfrontier.palemirror.internal.integration.crimson.CrimsonSandboxAdapter;
import io.farfrontier.palemirror.internal.world.MutableCell;
import io.farfrontier.palemirror.internal.world.PaleMirrorSavedData;
import io.farfrontier.palemirror.internal.world.TestMineRecord;
import io.farfrontier.palemirror.internal.world.WorldObjectLifecycle;
import io.farfrontier.palemirror.internal.world.EncounterState;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Zombie;
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
    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 100)
    public static void testMineRecoversAfterObservedControllerDeath(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        PaleMirrorRuntime runtime = PaleMirrorRuntime.forServer(level.getServer());
        BlockPos anchor = helper.absolutePos(new BlockPos(0, 3, 0));
        resetPaleMirrorState(level);
        clearMineVolume(level, anchor);

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setPos(anchor.getX() + 0.5, anchor.getY() - 2, anchor.getZ() + 0.5);
        TestMineRecord mine = runtime.createTestMine(player);

        runtime.advanceSimulation(1);
        SettlementState settlement = PaleMirrorSavedData.get(level.getServer().overworld()).worldState().settlements().stream()
                .findFirst().orElseThrow();
        helper.assertValueEqual(settlement.supplyDisrupted(), true, "mine infection must disrupt settlement iron supply");
        helper.assertValueEqual(settlement.currentDefense(), 30, "supply disruption must lower settlement defense");
        String scenarioId = runtime.offered(runtime.audienceFor(player)).getFirst().id();
        helper.assertTrue(runtime.accept(scenarioId, runtime.audienceFor(player)), "scenario must be accepted by its audience");

        player.setPos(anchor.getX() + 0.5, anchor.getY() + 2, anchor.getZ() + 0.5);
        tick(runtime, 2);
        CompoundTag persisted = PaleMirrorSavedData.get(level.getServer().overworld()).save(new CompoundTag(), level.registryAccess());
        PaleMirrorSavedData reloaded = PaleMirrorSavedData.load(persisted, level.registryAccess());
        helper.assertValueEqual(reloaded.testMines().get(mine.id()).job().nextOperationIndex(), 1,
                "restart snapshot must retain completed operation progress");
        helper.assertValueEqual(reloaded.testMines().get(mine.id()).job().operations().getFirst().state().name(), "COMPLETED",
                "restart snapshot must retain operation postcondition state");
        tick(runtime, 3);

        helper.assertValueEqual(PaleMirrorSavedData.get(level.getServer().overworld()).worldState()
                .facility(mine.id()).orElseThrow().status(), FacilityStatus.INFECTED, "facility status after materialization");
        helper.assertValueEqual(PaleMirrorSavedData.get(level.getServer().overworld()).worldState()
                .scenario(scenarioId).orElseThrow().status(), ScenarioStatus.RECOVER, "scenario status after entering mine");
        helper.assertTrue(mine.anchorId() != null, "PM-owned anchor must be materialized exactly once");
        boolean crimsonAvailable = AdapterRegistry.crimson().health().status()
                == io.farfrontier.palemirror.api.AdapterHealth.Status.AVAILABLE;
        UUID crimsonActorId = crimsonAvailable ? mine.encounter().actor("guard_human").orElseThrow().entityId() : null;
        if (crimsonAvailable) {
            LivingEntity crimsonActor = (LivingEntity) level.getEntity(crimsonActorId);
            helper.assertTrue(crimsonActor != null, "sandbox profile must materialize its persisted actor UUID");
            helper.assertTrue(crimsonActor.getTags().contains("Crimsonified_Human"),
                    "sandbox actor must receive Crimson's local actor protocol");
            helper.assertValueEqual(mine.job().operations().get(2).state().name(), "COMPLETED",
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
            helper.assertValueEqual(mine.job().operations().get(2).state().name(), "DEGRADED",
                    "optional Crimson operation must be persisted as degraded rather than blocking recovery");
        }
        helper.assertValueEqual(mine.object().lifecycle(), WorldObjectLifecycle.ACTIVE,
                "registry must record the active physical representation");
        helper.assertTrue(mine.mutableCells().stream().allMatch(cell -> level.getBlockState(cell.position()).is(Blocks.NETHERRACK)),
                "all PM-owned overlay cells must be materialized");

        CompoundTag legacy = PaleMirrorSavedData.get(level.getServer().overworld()).save(new CompoundTag(), level.registryAccess()).copy();
        downgradeV6SnapshotToV5(legacy);
        PaleMirrorSavedData migrated = PaleMirrorSavedData.load(legacy, level.registryAccess());
        helper.assertValueEqual(migrated.testMines().get(mine.id()).anchorId(), mine.anchorId(),
                "v5 controller reference must migrate to the PM anchor reference");
        helper.assertValueEqual(migrated.worldState().scenario(scenarioId).orElseThrow().encounterProfileId(),
                "pale_mirror:crimson_mine_guards", "v5 migration must preserve the pinned encounter profile");
        helper.assertValueEqual(migrated.save(new CompoundTag(), level.registryAccess()).getInt("schemaVersion"), 8,
                "migrated snapshot must be rewritten as schema v8");

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
        helper.assertValueEqual(settlement.supplyDisrupted(), false, "recovered mine must restore settlement supply");
        helper.assertValueEqual(settlement.currentDefense(), 40, "recovered mine must restore settlement defense");
        helper.assertTrue(mine.mutableCells().stream().allMatch(cell -> level.getBlockState(cell.position()).is(Blocks.DEEPSLATE_BRICKS)),
                "overlay cleanup must restore only PM-owned baseline cells");
        helper.succeed();
    }

    @SuppressWarnings("removal")
    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 100)
    public static void crimsonBaseRosterMaterializesByPmTier(GameTestHelper helper) {
        if (AdapterRegistry.crimson().health().status() != io.farfrontier.palemirror.api.AdapterHealth.Status.AVAILABLE) {
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
        TestMineRecord mine = runtime.createTestMine(player);
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
    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 40)
    public static void crimsonSandboxShadowsGlobalTick(GameTestHelper helper) {
        if (AdapterRegistry.crimson().health().status() != io.farfrontier.palemirror.api.AdapterHealth.Status.AVAILABLE) {
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
        data.testMines().clear();
        data.worldRegistry().clear();
        data.audienceMappings().clear();
        data.reconciliationLedger().clear();
        data.worldState().facilities().clear();
        data.worldState().scenarios().clear();
        data.worldState().settlements().clear();
        data.worldState().narratorCooldowns().clear();
        data.worldState().history().clear();
        data.worldState().setSimulationStep(0);
        data.worldState().setEventSequence(0);
        data.setDirty();
    }

    private static void downgradeV6SnapshotToV5(CompoundTag tag) {
        tag.putInt("schemaVersion", 5);
        CompoundTag mine = tag.getList("testMines", Tag.TAG_COMPOUND).getCompound(0);
        mine.putUUID("controller", mine.getUUID("anchor"));
        mine.remove("anchor");
        ListTag operations = mine.getCompound("job").getList("operations", Tag.TAG_COMPOUND);
        for (Tag element : operations) {
            CompoundTag operation = (CompoundTag) element;
            operation.putString("type", switch (operation.getString("type")) {
                case "ENSURE_PM_ANCHOR" -> "ENSURE_TEST_THREAT_CONTROLLER";
                case "REMOVE_PM_ANCHOR" -> "REMOVE_TEST_THREAT_CONTROLLER";
                default -> operation.getString("type");
            });
            operation.remove("target");
        }
        CompoundTag scenario = tag.getCompound("snapshot").getList("scenarios", Tag.TAG_COMPOUND).getCompound(0);
        scenario.remove("encounterProfile");
        scenario.remove("encounterProfileVersion");
        ListTag capabilities = scenario.getList("requiredCapabilities", Tag.TAG_STRING);
        for (int index = 0; index < capabilities.size(); index++) {
            if ("PM_ANCHOR_MATERIALIZATION".equals(capabilities.getString(index))) {
                capabilities.set(index, net.minecraft.nbt.StringTag.valueOf("TEST_THREAT_MATERIALIZATION"));
            }
            if ("PM_ANCHOR_OBSERVATION".equals(capabilities.getString(index))) {
                capabilities.set(index, net.minecraft.nbt.StringTag.valueOf("TEST_THREAT_OBSERVATION"));
            }
        }
    }

    private static void tick(PaleMirrorRuntime runtime, int count) {
        for (int index = 0; index < count; index++) runtime.tick();
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
