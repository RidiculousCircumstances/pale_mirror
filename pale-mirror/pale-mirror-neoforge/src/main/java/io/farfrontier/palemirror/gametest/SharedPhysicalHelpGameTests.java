package io.farfrontier.palemirror.gametest;

import java.util.List;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.api.AdapterHealth;
import io.farfrontier.palemirror.domain.DomainCommand;
import io.farfrontier.palemirror.domain.DomainEvent;
import io.farfrontier.palemirror.domain.DomainEventType;
import io.farfrontier.palemirror.domain.DomainServices;
import io.farfrontier.palemirror.domain.InfectionSourceId;
import io.farfrontier.palemirror.domain.KnownRegionalFeature;
import io.farfrontier.palemirror.domain.RouteProvider;
import io.farfrontier.palemirror.domain.ScenarioArchetype;
import io.farfrontier.palemirror.domain.ScenarioDefinitionRef;
import io.farfrontier.palemirror.domain.ScenarioStatus;
import io.farfrontier.palemirror.domain.WorldObjectId;
import io.farfrontier.palemirror.internal.PaleMirrorRuntime;
import io.farfrontier.palemirror.internal.adapter.AdapterRegistry;
import io.farfrontier.palemirror.internal.world.PaleMirrorSavedData;
import io.farfrontier.palemirror.internal.world.TestMineRecord;
import io.farfrontier.palemirror.internal.world.TestMineTemplate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Regression coverage for multi-audience shared physical intervention. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SharedPhysicalHelpGameTests {
    private static final InfectionSourceId CRIMSON = new InfectionSourceId("pale_mirror:crimson");

    private SharedPhysicalHelpGameTests() { }

    @SuppressWarnings("removal")
    @GameTest(batch = "pm-shared-physical-help", templateNamespace = "minecraft",
            template = "bastion/mobs/empty", timeoutTicks = 80)
    public static void informedPlayerEnteringMineMaterializesCrisisActorsWithoutAccepting(GameTestHelper helper) {
        if (GameTestProfiles.createAdapterOnly()
                || AdapterRegistry.sourceAdapter(CRIMSON).health().status() != AdapterHealth.Status.AVAILABLE) {
            helper.succeed();
            return;
        }
        ServerLevel level = helper.getLevel();
        PaleMirrorRuntime runtime = PaleMirrorRuntime.forServer(level.getServer());
        PaleMirrorSavedData data = PaleMirrorSavedData.get(level.getServer().overworld());
        GameTestStateReset.resetAll(data);
        String regionId = "pale_mirror:shared_encounter_fixture";
        GameTestStateReset.registerMinimalRouteRegion(data, regionId,
                new WorldObjectId("pale_mirror:shared_encounter_route"), RouteProvider.PALE_MIRROR, CRIMSON);
        var region = data.worldState().livingRegion(regionId).orElseThrow();
        BlockPos anchor = helper.absolutePos(new BlockPos(0, 3, 0));
        clearMineVolume(level, anchor);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        var audience = runtime.audienceFor(player);
        TestMineRecord mine = TestMineTemplate.place(level, anchor, region.primaryFacilityId(), audience);
        data.registerTestMine(mine);
        var commands = new DomainServices().commands();
        commands.execute(data.worldState(), new DomainCommand.DiscoverLivingRegion(
                region.id(), audience, "gametest:discover-shared-encounter"));
        helper.assertTrue(data.worldState().regionKnowledge(audience, region.id())
                        .filter(knowledge -> knowledge.knows(KnownRegionalFeature.SETTLEMENT)).isPresent(),
                "the entering audience must know the settlement before shared physical help is enabled");
        commands.execute(data.worldState(), new DomainCommand.TriggerFacilityInfection(
                mine.id(), "gametest:infect-shared-encounter"));
        DomainEvent crisis = new DomainEvent("pm:event:shared-encounter-crisis",
                DomainEventType.SETTLEMENT_CRISIS_DETECTED, region.communityId(),
                data.worldState().simulationStep(), "gametest:shared-encounter", region.id());
        commands.execute(data.worldState(), new DomainCommand.OfferScenario(crisis, audience,
                new ScenarioDefinitionRef("pale_mirror:ironhill_supply_crisis", "2",
                        List.of("OFFERED", "ASSESS", "RESPOND", "RESOLVED"), List.of(), 0,
                        "pale_mirror:crimson_mine_guards", "5", ScenarioArchetype.SETTLEMENT_SUPPLY_CRISIS)));
        var scenario = data.worldState().scenarios().stream().filter(value -> value.audience().equals(audience)
                && value.target().equals(region.communityId())).findFirst().orElseThrow();
        helper.assertValueEqual(scenario.status(), ScenarioStatus.OFFERED,
                "the private strategic offer must remain unaccepted");

        player.setPos(anchor.getX() + 0.5D, anchor.getY() + 2.0D, anchor.getZ() + 0.5D);
        for (int tick = 0; tick < 8; tick++) runtime.tick();

        helper.assertValueEqual(scenario.status(), ScenarioStatus.OFFERED,
                "physical combat must not implicitly accept a private strategic offer");
        helper.assertValueEqual(mine.encounter().actors().size(), 2,
                "FOOTHOLD must pin both Crimson guard actors for an informed entering player");
        for (var reference : mine.encounter().actors()) {
            Mob actor = (Mob) level.getEntity(reference.entityId());
            helper.assertTrue(actor != null && reference.status().name().equals("ACTIVE"),
                    "each shared encounter actor must materialize exactly once");
            BlockPos feet = actor.blockPosition();
            helper.assertTrue(level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), Direction.UP),
                    "an encounter actor must stand on a supported physical cell");
            helper.assertTrue(level.noCollision(actor, actor.getBoundingBox()),
                    "an encounter actor must not be hidden inside authored mine blocks");
        }
        helper.succeed();
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
}
