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
import io.farfrontier.palemirror.internal.world.CampaignCommissioningRecord;
import io.farfrontier.palemirror.internal.world.PaleMirrorSavedData;

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
        helper.assertValueEqual(planned.constructionPolicy(),
                io.farfrontier.palemirror.internal.adapter.RailConstructionPolicy.LOADED_CHUNKS_ONLY,
                "production plan must never authorize provider-driven chunk generation");
        helper.assertTrue(planned.totalSegments() > 0 && planned.completedSegments() == 0,
                "the complete immutable segment plan must persist before any physical work");
        helper.succeed();
    }

    @GameTest(batch = "pm-managed-railway-chunks", templateNamespace = "minecraft",
            template = "bastion/mobs/empty", timeoutTicks = 80)
    public static void productionPlanNeverGeneratesUnvisitedRouteChunks(GameTestHelper helper) {
        if (!ModList.get().isLoaded("railwaysuntold")) { helper.succeed(); return; }
        var adapter = AdapterRegistry.managedRailway();
        BlockPos start = helper.absolutePos(new BlockPos(1, 130, 1));
        BlockPos target = start.offset(4096, 0, 0);
        helper.assertTrue(!helper.getLevel().getChunkSource().hasChunk(target.getX() >> 4, target.getZ() >> 4),
                "far route target must begin naturally unloaded");

        String suffix = Long.toUnsignedString(start.asLong());
        String connectionId = "pm:gametest:chunk_route:" + suffix;
        var record = CampaignCommissioningRecord.planned("pale_mirror:chunk_test:" + suffix,
                helper.getLevel().dimension().location().toString(), connectionId,
                "pm:gametest:chunk_service:" + suffix, start, target, start, Direction.Axis.X,
                Direction.EAST, 5000, "Chunk Origin", "Chunk Destination");
        PaleMirrorSavedData data = PaleMirrorSavedData.get(helper.getLevel().getServer().overworld());
        data.campaignCommissioning().put(record.regionId(), record);
        data.setDirty();
        var planned = adapter.plan(helper.getLevel(), new RailConnectionRequest(connectionId, start, target,
                Direction.Axis.X, 5000));
        record.railBuilding(planned.planHash(), planned.nativeReference());
        adapter.start(helper.getLevel(), connectionId);

        helper.runAfterDelay(20, () -> {
            var observed = adapter.connection(helper.getLevel(), connectionId).orElseThrow();
            helper.assertTrue(observed.completedSegments() > 0
                            && observed.completedSegments() < observed.totalSegments(),
                    "naturally loaded segments must materialize while the unvisited remainder stays planned");
            helper.assertTrue(observed.nativeReference().isBlank(),
                    "chunk-driven construction must not create a legacy expansion head");
            helper.assertTrue(!helper.getLevel().getChunkSource().hasChunk(target.getX() >> 4, target.getZ() >> 4),
                    "loaded-chunks-only construction must not generate the far endpoint");
            helper.succeed();
        });
    }
}
