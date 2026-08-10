package io.farfrontier.palemirror.gametest;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.domain.InfectionSourceId;
import io.farfrontier.palemirror.domain.SourceGateStatus;
import io.farfrontier.palemirror.domain.StoryAudienceId;
import io.farfrontier.palemirror.domain.ThreatTier;
import io.farfrontier.palemirror.domain.WorldObjectId;
import io.farfrontier.palemirror.internal.PaleMirrorRuntime;
import io.farfrontier.palemirror.internal.adapter.AdapterRegistry;
import io.farfrontier.palemirror.internal.content.EncounterProfile;
import io.farfrontier.palemirror.internal.adapter.ActorOperationResult;
import io.farfrontier.palemirror.internal.integration.crimson.CrimsonSandboxAdapter;
import io.farfrontier.palemirror.internal.world.EncounterActorRef;
import io.farfrontier.palemirror.internal.world.EncounterRecord;
import io.farfrontier.palemirror.internal.world.EncounterState;
import io.farfrontier.palemirror.internal.world.PaleMirrorSavedData;
import io.farfrontier.palemirror.internal.world.GatePresentationRecord;
import io.farfrontier.palemirror.internal.world.SourceGatePartRef;
import io.farfrontier.palemirror.internal.world.TestMineRecord;
import io.farfrontier.palemirror.internal.world.TestMineTemplate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Full PM clearance chain against the pinned Crimson sandbox, without Crimson global mechanics. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CrimsonSiegeGameTests {
    private static final InfectionSourceId SOURCE = new InfectionSourceId("pale_mirror:crimson");
    private CrimsonSiegeGameTests() { }

    @SuppressWarnings("removal")
    @GameTest(batch = "pm-crimson-gate", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 240)
    public static void pmControlsCrimsonSiegeClearanceChain(GameTestHelper helper) {
        if (GameTestProfiles.createAdapterOnly()) { helper.succeed(); return; }
        if (AdapterRegistry.sourceAdapter(SOURCE).health().status() != io.farfrontier.palemirror.api.AdapterHealth.Status.AVAILABLE) {
            helper.succeed();
            return;
        }
        ServerLevel level = helper.getLevel();
        PaleMirrorRuntime runtime = PaleMirrorRuntime.forServer(level.getServer());
        BlockPos anchor = helper.absolutePos(new BlockPos(0, 3, 0));
        reset(level);
        clearMineVolume(level, anchor);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setPos(anchor.getX() + 0.5D, anchor.getY() - 2.0D, anchor.getZ() + 0.5D);
        TestMineRecord mine = runtime.registerThreatSite(player, new WorldObjectId("pale_mirror:test_mine"), SOURCE);
        runtime.advanceSimulation(1);
        String scenarioId = runtime.offered(runtime.audienceFor(player)).getFirst().id();
        helper.assertTrue(runtime.accept(scenarioId, runtime.audienceFor(player)), "scenario must be accepted");
        player.setPos(anchor.getX() + 0.5D, anchor.getY() + 2.0D, anchor.getZ() + 0.5D);

        tick(runtime, 5);
        advanceTier(runtime, 12, 8);
        advanceTier(runtime, 24, 11);
        advanceTier(runtime, 36, 24);
        var facility = PaleMirrorSavedData.get(level.getServer().overworld()).worldState().facility(mine.id()).orElseThrow();
        helper.assertValueEqual(facility.threatTier(), ThreatTier.APEX, "PM simulation must create the APEX siege");
        helper.assertValueEqual(facility.gate().status(), SourceGateStatus.ACTIVE, "Crimson has no authority over the PM gate state");
        helper.assertValueEqual(mine.gate().parts().size(), 4, "one PM record must exist for each node");
        helper.assertTrue(mine.gate().parts().stream().allMatch(part -> part.profileId().startsWith("pale_mirror:crimson_gate_node")
                && level.getBlockState(part.position()).is(Blocks.SEA_LANTERN)),
                "nodes must use exactly the four predeclared PM mutable cells");

        LivingEntity controller = (LivingEntity) level.getEntity(mine.anchorId());
        helper.assertTrue(controller != null, "the PM controller must exist while the siege is sealed");
        helper.assertTrue(!controller.hurt(level.damageSources().generic(), 10_000.0F),
                "a protected PM controller must reject direct damage before gate clearance");
        controller.die(level.damageSources().generic());
        helper.assertTrue(!controller.isRemoved(), "a protected PM controller must also reject direct death events");

        mine.gate().parts().forEach(part -> {
            level.setBlock(part.position(), Blocks.AIR.defaultBlockState(), 3);
            runtime.gateBlockDestroyed(level, part.position());
        });
        helper.assertValueEqual(facility.gate().currentPhase().orElseThrow().id(), "boss", "all four exact nodes must unlock one boss");
        tick(runtime, 24);
        CompoundTag snapshot = PaleMirrorSavedData.get(level.getServer().overworld()).save(new CompoundTag(), level.registryAccess());
        var reloaded = PaleMirrorSavedData.load(snapshot, level.registryAccess());
        helper.assertTrue(reloaded.testMines().get(mine.id()).gate().part("boss").orElseThrow().entityId() != null,
                "restart snapshot must retain the PM-owned boss identity");
        clearCurrentGate(level, runtime, mine, "boss", "bloodlink_i", helper);
        tick(runtime, 24);
        clearCurrentGate(level, runtime, mine, "bloodlink_i", "bloodlink_ii", helper);
        tick(runtime, 24);
        clearCurrentGate(level, runtime, mine, "bloodlink_ii", "bloodlink_iii", helper);
        tick(runtime, 24);
        clearCurrentGate(level, runtime, mine, "bloodlink_iii", null, helper);

        helper.assertTrue(mine.encounter().actors().size() == 16,
                "normal PM APEX guards must persist while boss and Bloodlinks are cleared");
        controller = (LivingEntity) level.getEntity(mine.anchorId());
        helper.assertTrue(controller != null, "controller must still be the sole PM-owned threat authority");
        controller.die(level.damageSources().generic());
        helper.assertValueEqual(PaleMirrorSavedData.get(level.getServer().overworld()).worldState().facility(mine.id()).orElseThrow()
                .gate().status(), SourceGateStatus.INACTIVE, "clearing the unsealed controller must reset only the canonical gate state");
        helper.succeed();
    }

    @SuppressWarnings("removal")
    @GameTest(batch = "pm-crimson-pummeler", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 60)
    public static void pummelerUsesCrimsonDisplayModelContract(GameTestHelper helper) {
        if (GameTestProfiles.createAdapterOnly()) { helper.succeed(); return; }
        if (AdapterRegistry.sourceAdapter(SOURCE).health().status() != io.farfrontier.palemirror.api.AdapterHealth.Status.AVAILABLE) {
            helper.succeed();
            return;
        }
        ServerLevel level = helper.getLevel();
        BlockPos anchor = helper.absolutePos(new BlockPos(0, 3, 0));
        clearMineVolume(level, anchor);
        TestMineRecord mine = TestMineTemplate.place(level, anchor, new WorldObjectId("pale_mirror:visual_test"),
                StoryAudienceId.globalTestAudience());
        SourceGatePartRef pummeler = new SourceGatePartRef("visual_pummeler", "pale_mirror:pummeler",
                anchor.above(), null, SourceGatePartRef.Status.MISSING);
        mine.setGate(new GatePresentationRecord("pale_mirror:crimson_apex", "1", 1L, java.util.List.of(pummeler), ""));

        ActorOperationResult result = AdapterRegistry.sourceAdapter(SOURCE).ensureGatePart(level, mine, "pm:visual-test", pummeler);
        helper.assertValueEqual(result.status(), ActorOperationResult.Status.MATERIALIZED,
                "Pummeler must materialize only when its visual initializer succeeds");
        LivingEntity entity = (LivingEntity) level.getEntity(mine.gate().part("visual_pummeler").orElseThrow().entityId());
        helper.assertTrue(entity != null && entity.getName().getString().equals("Pummeler"),
                "Pummeler name must select Crimson's client model contract");
        var display = entity.getPassengers().stream().filter(value -> value.getTags().contains("PM_Pummeler_Visual"))
                .findFirst().orElse(null);
        helper.assertTrue(display != null && display.getType() == net.minecraft.world.entity.EntityType.ITEM_DISPLAY,
                "Pummeler must retain its PM-owned Crimson item-display passenger");
        CompoundTag visualData = display.saveWithoutId(new CompoundTag());
        helper.assertValueEqual(visualData.getCompound("item").getString("id"), "minecraft:book",
                "Pummeler display must use the Crimson book model carrier");
        helper.assertValueEqual(visualData.getCompound("item").getCompound("components")
                .getInt("minecraft:custom_model_data"), 5450230,
                "Pummeler display must select Crimson model 5450230");
        AdapterRegistry.sourceAdapter(SOURCE).removeGatePart(level, mine, "visual_pummeler");
        helper.assertTrue(display.isRemoved(), "Pummeler cleanup must remove its PM-owned visual passenger");
        helper.succeed();
    }

    @SuppressWarnings("removal")
    @GameTest(batch = "pm-crimson-presentation", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 60)
    public static void pmPresentationDrivesCrimsonModelFramesAndPhases(GameTestHelper helper) {
        if (GameTestProfiles.createAdapterOnly()) { helper.succeed(); return; }
        if (AdapterRegistry.sourceAdapter(SOURCE).health().status() != io.farfrontier.palemirror.api.AdapterHealth.Status.AVAILABLE) {
            helper.succeed();
            return;
        }
        ServerLevel level = helper.getLevel();
        BlockPos anchor = helper.absolutePos(new BlockPos(0, 3, 0));
        reset(level);
        clearMineVolume(level, anchor);
        TestMineRecord mine = TestMineTemplate.place(level, anchor, new WorldObjectId("pale_mirror:presentation_test"),
                StoryAudienceId.globalTestAudience());
        mine.setEncounter(new EncounterRecord("presentation", "1", "pm:presentation-test", 1L,
                java.util.List.of(new EncounterActorRef("raptor", "pale_mirror:raptor", "minecraft:zombie", null,
                        EncounterActorRef.Status.MISSING, 0L, 0)), EncounterState.ACTIVE, ""));
        SourceGatePartRef bloodlink = new SourceGatePartRef("bloodlink_i", "pale_mirror:bloodlink_i",
                anchor.above(), null, SourceGatePartRef.Status.MISSING);
        SourceGatePartRef osiris = new SourceGatePartRef("osiris", "pale_mirror:osiris",
                anchor.above(2), null, SourceGatePartRef.Status.MISSING);
        mine.setGate(new GatePresentationRecord("pale_mirror:crimson_apex", "1", 1L, java.util.List.of(bloodlink, osiris), ""));
        PaleMirrorSavedData data = PaleMirrorSavedData.get(level.getServer().overworld());
        data.registerTestMine(mine);

        EncounterProfile.ActorSlot raptorSlot = new EncounterProfile.ActorSlot("raptor", "pale_mirror:raptor", ThreatTier.SIEGE);
        helper.assertValueEqual(AdapterRegistry.sourceAdapter(SOURCE).ensureActor(level, mine, "pm:presentation-test", raptorSlot).status(),
                ActorOperationResult.Status.MATERIALIZED, "Raptor must materialize with its model carriers");
        helper.assertValueEqual(AdapterRegistry.sourceAdapter(SOURCE).ensureGatePart(level, mine, "pm:presentation-test", bloodlink).status(),
                ActorOperationResult.Status.MATERIALIZED, "Bloodlink must materialize with its model carrier");
        helper.assertValueEqual(AdapterRegistry.sourceAdapter(SOURCE).ensureGatePart(level, mine, "pm:presentation-test", osiris).status(),
                ActorOperationResult.Status.MATERIALIZED, "Osiris must materialize for its local phase cue");

        Mob raptor = (Mob) level.getEntity(mine.encounter().actor("raptor").orElseThrow().entityId());
        Mob link = (Mob) level.getEntity(mine.gate().part("bloodlink_i").orElseThrow().entityId());
        Mob boss = (Mob) level.getEntity(mine.gate().part("osiris").orElseThrow().entityId());
        helper.assertValueEqual(modelData(raptor, EquipmentSlot.HEAD), 5450192,
                "Raptor must retain Crimson's body model carrier");
        helper.assertValueEqual(modelData(link, EquipmentSlot.HEAD), 5450100,
                "Bloodlink I must start with Crimson's Stage I model carrier");
        var brain = boss.getPassengers().stream().filter(value -> value.getType() == net.minecraft.world.entity.EntityType.MAGMA_CUBE
                        && value.getTags().contains("PM_Osiris_Brain") && value.getName().getString().equals("Osiris Brain"))
                .findFirst().orElse(null);
        helper.assertTrue(brain != null,
                "Osiris must retain its PM-owned Crimson Brain visual passenger");

        raptor.setDeltaMovement(new Vec3(0.2D, 0.0D, 0.0D));
        boss.setHealth(boss.getMaxHealth() * 0.4F);
        AdapterRegistry.tickRuntime(level.getServer(), data);
        helper.assertTrue(modelData(raptor, EquipmentSlot.MAINHAND) >= 5450170
                        && modelData(raptor, EquipmentSlot.MAINHAND) <= 5450180,
                "PM presentation must drive Raptor's audited animated hand-model frame range");
        int bloodlinkFrame = modelData(link, EquipmentSlot.HEAD);
        helper.assertTrue(bloodlinkFrame == 5450100 || bloodlinkFrame == 5450101 || bloodlinkFrame == 5450102
                        || bloodlinkFrame == 5450019 || bloodlinkFrame == 5450013,
                "PM presentation must drive an approved Bloodlink Stage I model frame");
        helper.assertValueEqual(boss.getPersistentData().getInt("pale_mirror_crimson_visual_phase"), 1,
                "PM presentation must emit Osiris's first local phase exactly once");

        AdapterRegistry.sourceAdapter(SOURCE).removeActor(level, mine, "raptor");
        AdapterRegistry.sourceAdapter(SOURCE).removeGatePart(level, mine, "bloodlink_i");
        AdapterRegistry.sourceAdapter(SOURCE).removeGatePart(level, mine, "osiris");
        helper.assertTrue(brain.isRemoved(), "Osiris cleanup must remove its PM-owned Brain visual passenger");
        helper.succeed();
    }

    private static void clearCurrentGate(ServerLevel level, PaleMirrorRuntime runtime, TestMineRecord mine, String slot,
                                         String expectedPhase, GameTestHelper helper) {
        var part = mine.gate().part(slot).orElseThrow();
        LivingEntity entity = (LivingEntity) level.getEntity(part.entityId());
        helper.assertTrue(entity != null && CrimsonSandboxAdapter.isGateEntity(entity),
                "gate must be a single registered PM-owned Crimson entity: " + slot);
        entity.die(level.damageSources().generic());
        var gate = PaleMirrorSavedData.get(level.getServer().overworld()).worldState().facility(mine.id()).orElseThrow().gate();
        if (expectedPhase == null) helper.assertValueEqual(gate.status(), SourceGateStatus.UNSEALED,
                "gate must unseal exactly after its final PM part: " + slot);
        else helper.assertValueEqual(gate.currentPhase().orElseThrow().id(), expectedPhase,
                "gate must advance exactly one PM clearance phase: " + slot);
    }

    private static int modelData(Mob entity, EquipmentSlot slot) {
        CustomModelData modelData = entity.getItemBySlot(slot).get(DataComponents.CUSTOM_MODEL_DATA);
        return modelData == null ? -1 : modelData.value();
    }

    private static void advanceTier(PaleMirrorRuntime runtime, int steps, int ticks) {
        runtime.advanceSimulation(steps);
        tick(runtime, ticks);
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
        data.worldState().narratorCooldowns().clear();
        data.worldState().history().clear();
        data.worldState().setSimulationStep(0);
        data.worldState().setEventSequence(0);
        data.setDirty();
    }
}
