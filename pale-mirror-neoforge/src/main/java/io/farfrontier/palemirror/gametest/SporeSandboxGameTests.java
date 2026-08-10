package io.farfrontier.palemirror.gametest;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.domain.FacilityStatus;
import io.farfrontier.palemirror.domain.InfectionSourceId;
import io.farfrontier.palemirror.domain.ThreatTier;
import io.farfrontier.palemirror.internal.PaleMirrorRuntime;
import io.farfrontier.palemirror.internal.adapter.AdapterRegistry;
import io.farfrontier.palemirror.internal.integration.spore.SporeSandboxAdapter;
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
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Verifies the isolated Spore vertical slice against the pinned native runtime when present. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SporeSandboxGameTests {
    private SporeSandboxGameTests() { }

    @SuppressWarnings("removal")
    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 140)
    public static void pmOwnsSporeSiteAndKeepsNativeFormsDormant(GameTestHelper helper) {
        if (AdapterRegistry.spore().health().status() != io.farfrontier.palemirror.api.AdapterHealth.Status.AVAILABLE) {
            helper.succeed();
            return;
        }
        ServerLevel level = helper.getLevel();
        PaleMirrorRuntime runtime = PaleMirrorRuntime.forServer(level.getServer());
        BlockPos playerStart = helper.absolutePos(new BlockPos(0, 3, 0));
        BlockPos siteAnchor = playerStart.above(2).offset(16, 0, 0);
        reset(level);
        clearMineVolume(level, siteAnchor);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setPos(playerStart.getX() + 0.5D, playerStart.getY(), playerStart.getZ() + 0.5D);
        TestMineRecord site = runtime.createSporeTestMine(player);
        helper.assertValueEqual(site.anchor(), siteAnchor, "Spore source placement must be deterministic and non-overlapping");

        runtime.advanceSimulation(1);
        String scenarioId = runtime.offered(runtime.audienceFor(player)).getFirst().id();
        helper.assertValueEqual(PaleMirrorSavedData.get(level.getServer().overworld()).worldState().facility(site.id()).orElseThrow()
                .infectionSource(), InfectionSourceId.SPORE, "source must be canonical facility state");
        helper.assertTrue(runtime.accept(scenarioId, runtime.audienceFor(player)), "Spore scenario must use the normal PM acceptance flow");
        player.setPos(siteAnchor.getX() + 0.5D, siteAnchor.getY() + 2.0D, siteAnchor.getZ() + 0.5D);
        tick(runtime, 5);

        LivingEntity human = (LivingEntity) level.getEntity(site.encounter().actor("dormant_infected_human").orElseThrow().entityId());
        helper.assertTrue(human != null, "Spore foothold must materialize exactly one persisted native actor");
        helper.assertValueEqual(BuiltInRegistries.ENTITY_TYPE.getKey(human.getType()).toString(), "spore:inf_human",
                "Spore adapter must use only its pinned registered entity contract");
        helper.assertTrue(human instanceof net.minecraft.world.entity.Mob mob && mob.isNoAi(),
                "PM must keep a native Spore form dormant so it cannot own spread or terrain conversion");
        helper.assertValueEqual(human.getPersistentData().getString(SporeSandboxAdapter.PROFILE_KEY),
                "pale_mirror:spore_infected_human", "native actor must retain PM source provenance");
        runtime.publish(new EncounterActorDestroyed("forged-wrong-source", site.id(), InfectionSourceId.CRIMSON,
                "dormant_infected_human", human.getUUID()));
        helper.assertValueEqual(site.encounter().actor("dormant_infected_human").orElseThrow().status().name(), "ACTIVE",
                "reconciliation must reject an actor observation whose source disagrees with canonical facility state");
        var footholdCell = site.biomeCells().stream().filter(cell -> cell.infectionStage() == InfectionBiomeStage.FOOTHOLD)
                .findFirst().orElseThrow();
        helper.assertValueEqual(level.getBlockState(footholdCell.position()).getBlock(), Blocks.MOSS_BLOCK,
                "Spore foothold must use PM's bounded fungal palette rather than native terrain spread");
        CompoundTag snapshot = PaleMirrorSavedData.get(level.getServer().overworld()).save(new CompoundTag(), level.registryAccess());
        helper.assertValueEqual(PaleMirrorSavedData.load(snapshot, level.registryAccess()).worldState().facility(site.id()).orElseThrow()
                .infectionSource(), InfectionSourceId.SPORE, "restart snapshot must retain the canonical source identity");

        helper.assertTrue(!human.hurt(level.damageSources().generic(), 10_000.0F),
                "dormant Spore form must reject damage so native death/remains code cannot edit the world");
        helper.assertValueEqual(PaleMirrorSavedData.get(level.getServer().overworld()).worldState().facility(site.id()).orElseThrow()
                .status(), FacilityStatus.INFECTED, "native form damage must not resolve the PM-owned controller");

        runtime.advanceSimulation(12);
        tick(runtime, 6);
        helper.assertValueEqual(PaleMirrorSavedData.get(level.getServer().overworld()).worldState().facility(site.id()).orElseThrow()
                .threatTier(), ThreatTier.INFESTED, "PM simulation, not Spore evolution, must select the next roster tier");
        LivingEntity braiomil = (LivingEntity) level.getEntity(site.encounter().actor("dormant_braiomil").orElseThrow().entityId());
        helper.assertTrue(braiomil instanceof net.minecraft.world.entity.Mob mob && mob.isNoAi(),
                "second native form must also remain dormant under PM control");
        helper.assertValueEqual(BuiltInRegistries.ENTITY_TYPE.getKey(braiomil.getType()).toString(), "spore:braiomil",
                "PM tier selection must materialize the second pinned Spore form exactly once");
        LivingEntity controller = (LivingEntity) level.getEntity(site.anchorId());
        helper.assertTrue(controller != null, "PM-owned controller must remain independently materialized beside native forms");
        controller.die(level.damageSources().generic());
        runtime.advanceSimulation(1);
        tick(runtime, 5);
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
        data.worldState().facilities().clear();
        data.worldState().scenarios().clear();
        data.worldState().settlements().clear();
        data.worldState().narratorCooldowns().clear();
        data.worldState().history().clear();
        data.worldState().setSimulationStep(0);
        data.worldState().setEventSequence(0);
        data.setDirty();
    }
}
