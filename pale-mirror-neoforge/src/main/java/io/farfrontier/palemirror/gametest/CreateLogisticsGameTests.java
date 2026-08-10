package io.farfrontier.palemirror.gametest;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.api.AdapterHealth;
import io.farfrontier.palemirror.internal.adapter.AdapterRegistry;
import io.farfrontier.palemirror.internal.adapter.LogisticsRouteContract;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import io.farfrontier.palemirror.internal.adapter.RailConnectionRequest;
import io.farfrontier.palemirror.internal.adapter.RailConnectionStatus;
import net.minecraft.core.Direction;

/** Runs as a no-op in core-only CI and proves the pinned Create observer loads when the profile is present. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CreateLogisticsGameTests {
    private CreateLogisticsGameTests() { }

    @GameTest(batch = "pm-create-logistics", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 40)
    public static void pinnedCreateProfileExposesReadOnlyLogisticsContract(GameTestHelper helper) {
        if (!ModList.get().isLoaded("create")) {
            helper.succeed();
            return;
        }
        var adapter = AdapterRegistry.all().stream().filter(value -> value.id().equals("pale_mirror:create_logistics"))
                .findFirst().orElseThrow();
        helper.assertValueEqual(adapter.health().status(), AdapterHealth.Status.AVAILABLE,
                "the pinned Create profile must expose the isolated logistics observer");
        BlockPos anchor = helper.absolutePos(BlockPos.ZERO);
        var station = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("create:track_station"));
        helper.assertTrue(station != net.minecraft.world.level.block.Blocks.AIR, "Create track station must be registered");
        helper.getLevel().setBlock(anchor, station.defaultBlockState(), 3);
        var observation = AdapterRegistry.observeLogisticsRoute(helper.getLevel(), new LogisticsRouteContract(
                new io.farfrontier.palemirror.domain.WorldObjectId("pale_mirror:test_logistics"), anchor, anchor,
                "PM Missing Origin", "PM Missing Destination")).orElseThrow();
        helper.assertTrue(observation.observed() && !observation.originTrainPresent() && !observation.destinationTrainPresent(),
                "a loaded but unconfigured world must report an explicit invalid route rather than inventing capacity");
        helper.succeed();
    }

    @GameTest(batch = "pm-managed-railway", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 40)
    public static void privateForkExposesManagedApiAndPersistsPlanBeforeWork(GameTestHelper helper) {
        if (!ModList.get().isLoaded("railwaysuntold")) { helper.succeed(); return; }
        var adapter = AdapterRegistry.managedRailway();
        helper.assertValueEqual(adapter.health().status(), AdapterHealth.Status.AVAILABLE,
                "the exact PM fork must expose managed mode");
        BlockPos start = helper.absolutePos(new BlockPos(1, 2, 1));
        BlockPos target = start.offset(32, 0, 0);
        var planned = adapter.plan(helper.getLevel(), new RailConnectionRequest("pm:gametest-plan", start, target,
                Direction.Axis.X, 64));
        helper.assertValueEqual(planned.status(), RailConnectionStatus.PLANNED,
                "managed connection must be persisted as PLANNED without touching blocks");
        helper.assertTrue(!planned.planHash().isBlank(), "managed plan must expose a stable hash");
        helper.succeed();
    }
}
