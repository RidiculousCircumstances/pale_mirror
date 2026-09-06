package io.farfrontier.palemirror.internal.world;

import io.farfrontier.palemirror.PaleMirrorMod;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Restart regression for a physically damaged graph that was once accepted. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class VanillaMinecartTopologyRecoveryGameTests {
    private VanillaMinecartTopologyRecoveryGameTests() { }

    @GameTest(batch = "pm-vanilla-minecart-topology-recovery", templateNamespace = "minecraft",
            template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void disconnectedPersistedGraphSuspendsOnlyItsRoute(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(0, 6, 0));
        BlockPos middle = start.south();
        BlockPos target = middle.south();
        List<BlockPos> planned = List.of(start, middle, target);
        VanillaMinecartRouteRecord record = VanillaMinecartRouteRecord.authored(
                "pale_mirror:topology_recovery", "minecraft:overworld",
                "pale_mirror:topology_recovery_route", planned);
        Map<Long, String> disconnected = new LinkedHashMap<>();
        planned.forEach(position -> disconnected.put(position.asLong(), RailShape.EAST_WEST.name()));

        record.restoreTopology(disconnected, planned.stream().map(BlockPos::asLong).toList(),
                Set.of(), Set.of(), 0, 12L, middle);

        helper.assertValueEqual(record.status(), VanillaMinecartRouteStatus.SUSPENDED,
                "a disconnected persisted graph must suspend its route instead of rejecting all SavedData");
        helper.assertValueEqual(record.topologyIssue(), middle,
                "the persisted physical issue must survive recovery");
        helper.assertValueEqual(record.acceptedRailPath(), planned.stream().map(BlockPos::asLong).toList(),
                "the last accepted path must remain available for bounded repair verification");
        helper.assertFalse(record.dirtyTopologyChunks().isEmpty(),
                "restart recovery must schedule natural loaded-chunk reobservation");
        helper.succeed();
    }
}
