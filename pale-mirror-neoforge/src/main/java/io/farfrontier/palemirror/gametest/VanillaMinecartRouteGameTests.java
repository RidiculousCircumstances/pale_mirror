package io.farfrontier.palemirror.gametest;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.domain.DomainServices;
import io.farfrontier.palemirror.domain.WorldObjectId;
import io.farfrontier.palemirror.internal.adapter.AdapterRegistry;
import io.farfrontier.palemirror.internal.integration.vanilla.VanillaMinecartRailAdapter;
import io.farfrontier.palemirror.internal.world.PaleMirrorSavedData;
import io.farfrontier.palemirror.internal.world.VanillaMinecartRouteRecord;
import io.farfrontier.palemirror.internal.world.VanillaMinecartRouteRuntime;
import io.farfrontier.palemirror.internal.world.VanillaMinecartRouteStatus;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityMobGriefingEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Exercises the low-tech physical provider independently of campaign setup. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class VanillaMinecartRouteGameTests {
    private VanillaMinecartRouteGameTests() { }

    @GameTest(batch = "pm-vanilla-minecart-singleton", templateNamespace = "minecraft",
            template = "bastion/mobs/empty", timeoutTicks = 30)
    public static void representativeCarrierDeduplicatesAndIgnoresPhysicalImpulse(GameTestHelper helper) {
        if (GameTestProfiles.createAdapterOnly()) { helper.succeed(); return; }
        ServerLevel level = helper.getLevel();
        PaleMirrorSavedData data = PaleMirrorSavedData.get(level.getServer().overworld());
        reset(data);
        BlockPos start = helper.absolutePos(new BlockPos(0, 6, 0));
        level.setBlock(start.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(start, Blocks.RAIL.defaultBlockState(), 3);
        VanillaMinecartRailAdapter adapter = AdapterRegistry.vanillaMinecartRail();
        String routeId = "pale_mirror:singleton_route";
        var first = adapter.spawnRepresentativeCart(level, start, routeId);
        adapter.spawnRepresentativeCart(level, start, routeId);

        var reconciled = adapter.reconcileRepresentatives(level, routeId, first.cartId(), first.cargoId());
        helper.assertTrue(reconciled.keeper() != null, "one loaded carrier must remain authoritative");
        helper.assertValueEqual(reconciled.keeper().cartId(), first.cartId(),
                "the persisted carrier UUID must win deterministic reconciliation");
        long representatives = java.util.stream.StreamSupport.stream(level.getAllEntities().spliterator(), false)
                .filter(entity -> routeId.equals(entity.getPersistentData().getString("pale_mirror_route")))
                .filter(VanillaMinecartRailAdapter::isRepresentative).count();
        helper.assertValueEqual(representatives, 2L,
                "one cart plus one block-display cargo must survive duplicate cleanup");

        Entity cart = level.getEntity(first.cartId());
        helper.assertTrue(cart != null, "the reconciled carrier must remain loaded");
        cart.noPhysics = false;
        cart.setDeltaMovement(4.0D, 2.0D, -3.0D);
        cart.moveTo(start.getX() + 8.5D, start.getY() + 4.0D, start.getZ() + 0.5D);
        adapter.stabilizeRepresentativeCart(cart, start);
        helper.assertTrue(cart.noPhysics && cart.isInvulnerable() && cart.isNoGravity(),
                "the representative carrier must be a non-physical PM projection");
        helper.assertTrue(cart.getDeltaMovement().lengthSqr() == 0.0D
                        && cart.blockPosition().closerThan(start, 1.0D),
                "player or entity impulses must be discarded and the carrier snapped to its canonical pose");
        helper.assertTrue(cart.getPassengers().stream().anyMatch(entity -> entity.getUUID().equals(first.cargoId())),
                "the visual chest must remain a passenger of the singleton carrier");
        cart.getPassengers().forEach(Entity::discard);
        cart.discard();
        helper.succeed();
    }

    @GameTest(batch = "pm-vanilla-minecart-protection", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void endermenCannotGriefButOtherMobPolicyIsUnchanged(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        EnderMan enderman = EntityType.ENDERMAN.create(level);
        Zombie zombie = EntityType.ZOMBIE.create(level);
        helper.assertTrue(enderman != null && zombie != null, "test mobs must be constructible");

        EntityMobGriefingEvent endermanEvent = new EntityMobGriefingEvent(level, enderman);
        NeoForge.EVENT_BUS.post(endermanEvent);
        helper.assertFalse(endermanEvent.canGrief(),
                "Endermen must never remove supports or place carried blocks around PM infrastructure");

        EntityMobGriefingEvent zombieEvent = new EntityMobGriefingEvent(level, zombie);
        boolean configuredPolicy = zombieEvent.canGrief();
        NeoForge.EVENT_BUS.post(zombieEvent);
        helper.assertValueEqual(zombieEvent.canGrief(), configuredPolicy,
                "the Enderman-specific protection must not alter other mobs or the global mobGriefing rule");
        helper.succeed();
    }

    @GameTest(batch = "pm-vanilla-minecart-build", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 80)
    public static void corridorBuildsFromPersistedProvenance(GameTestHelper helper) {
        if (GameTestProfiles.createAdapterOnly()) { helper.succeed(); return; }
        ServerLevel level = helper.getLevel();
        PaleMirrorSavedData data = PaleMirrorSavedData.get(level.getServer().overworld());
        reset(data);
        BlockPos start = helper.absolutePos(new BlockPos(0, 12, 0));
        for (int index = 0; index <= 1; index++) for (int y = -2; y <= 4; y++)
            level.setBlock(start.east(index).above(y), Blocks.AIR.defaultBlockState(), 3);
        VanillaMinecartRouteRecord record = VanillaMinecartRouteRecord.planned(
                "pale_mirror:minecart_test", level.dimension().location().toString(), "pale_mirror:test_route",
                start, start.east());
        registerCanonicalRoute(data, record);

        helper.runAfterDelay(20, () -> {
            for (int step = 0; step < 12; step++)
                VanillaMinecartRouteRuntime.tick(level.getServer(), data, new DomainServices().commands());
            helper.assertTrue(record.status() == VanillaMinecartRouteStatus.VERIFYING,
                    "all loaded vanilla route segments must be physically postcondition-checked before activation: " + record.diagnostic());
            helper.assertValueEqual(record.completedSegmentCount(), record.segmentCount(),
                    "the persisted route plan must complete every bounded segment exactly once");
            helper.assertValueEqual(level.getBlockState(record.railPosition(1)).getBlock(), Blocks.RAIL,
                    "the narrow corridor must use vanilla rail rather than Create track");
            helper.assertValueEqual(level.getBlockState(record.target().relative(record.direction())).getBlock(), Blocks.OAK_FENCE,
                    "the receiving endpoint must be a readable low-tech platform rather than an abstract rail stop");
            CompoundTag snapshot = data.save(new CompoundTag(), level.registryAccess());
            VanillaMinecartRouteRecord reloaded = PaleMirrorSavedData.load(snapshot, level.registryAccess())
                    .vanillaMinecartRoutes().get(record.regionId());
            helper.assertTrue(reloaded != null && reloaded.completedSegmentCount() == record.segmentCount(),
                    "restart must retain vanilla-route progress and its provenance cells");
            helper.succeed();
        });
    }

    @GameTest(batch = "pm-vanilla-minecart-conflict", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 40)
    public static void corridorFailsClosedBeforeProtectedWrite(GameTestHelper helper) {
        if (GameTestProfiles.createAdapterOnly()) { helper.succeed(); return; }
        ServerLevel level = helper.getLevel();
        PaleMirrorSavedData data = PaleMirrorSavedData.get(level.getServer().overworld());
        reset(data);
        BlockPos start = helper.absolutePos(new BlockPos(0, 4, 0));
        level.setBlock(start, Blocks.CHEST.defaultBlockState(), 3);
        VanillaMinecartRouteRecord record = VanillaMinecartRouteRecord.planned(
                "pale_mirror:minecart_conflict", level.dimension().location().toString(), "pale_mirror:conflict_route",
                start, start.east(8));
        registerCanonicalRoute(data, record);

        helper.runAfterDelay(8, () -> {
            VanillaMinecartRouteRuntime.tick(level.getServer(), data, new DomainServices().commands());
            helper.assertValueEqual(record.status(), VanillaMinecartRouteStatus.BLOCKED,
                    "a stateful cell on a fresh PM corridor must block before any destructive write");
            helper.assertValueEqual(level.getBlockState(start).getBlock(), Blocks.CHEST,
                    "the protected block entity must survive an aborted vanilla corridor preflight");
            helper.succeed();
        });
    }

    @GameTest(batch = "pm-vanilla-minecart-recovery", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void damageAndRepresentativeCarrierSurviveRestart(GameTestHelper helper) {
        if (GameTestProfiles.createAdapterOnly()) { helper.succeed(); return; }
        PaleMirrorSavedData data = PaleMirrorSavedData.get(helper.getLevel().getServer().overworld());
        reset(data);
        BlockPos start = helper.absolutePos(new BlockPos(0, 6, 0));
        VanillaMinecartRouteRecord record = VanillaMinecartRouteRecord.planned("pale_mirror:recovery_test",
                helper.getLevel().dimension().location().toString(), "pale_mirror:recovery_route", start, start.east(8));
        BlockPos rail = record.railPosition(0);
        record.capture(rail, "minecraft:air");
        record.approve(rail, "minecraft:rail");
        record.initializeAuthoredTopology();
        record.disconnectTopology(rail);
        record.observeRepresentativeCart(java.util.UUID.randomUUID(), java.util.UUID.randomUUID());
        record.moveCart(2.5D);
        registerCanonicalRoute(data, record);

        CompoundTag snapshot = data.save(new CompoundTag(), helper.getLevel().registryAccess());
        VanillaMinecartRouteRecord reloaded = PaleMirrorSavedData.load(snapshot, helper.getLevel().registryAccess())
                .vanillaMinecartRoutes().get(record.regionId());

        helper.assertValueEqual(reloaded.status(), VanillaMinecartRouteStatus.SUSPENDED,
                "restart must preserve the fail-closed route state");
        helper.assertValueEqual(reloaded.topologyIssue(), rail,
                "restart must preserve the graph diagnostic without requiring an exact-cell repair");
        helper.assertValueEqual(reloaded.representativeCartId(), record.representativeCartId(),
                "restart must preserve the visual carrier identity");
        helper.assertValueEqual(reloaded.cartProgress(), 2.5D,
                "restart must preserve bounded representative movement progress");
        helper.assertTrue(reloaded.acceptTopology(java.util.stream.IntStream.range(0, reloaded.segmentCount())
                        .mapToObj(reloaded::railPosition).toList()),
                "a connected topology must make the route resumable independently of old provenance");

        CompoundTag schema32 = snapshot.copy();
        schema32.putInt("schemaVersion", 32);
        try {
            PaleMirrorSavedData.load(schema32, helper.getLevel().registryAccess());
            throw new AssertionError("schema v32 must fail closed instead of migrating an old physical graph");
        } catch (IllegalStateException expected) {
            helper.assertTrue(expected.getMessage().contains("schema 36"),
                    "fresh-world rail rejection must identify the schema boundary");
        }
        helper.succeed();
    }

    @GameTest(batch = "pm-vanilla-minecart-multi-repair", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void suspendedRouteDiscoversAndReconcilesMultipleLoadedBreaks(GameTestHelper helper) {
        if (GameTestProfiles.createAdapterOnly()) { helper.succeed(); return; }
        ServerLevel level = helper.getLevel();
        PaleMirrorSavedData data = PaleMirrorSavedData.get(level.getServer().overworld());
        reset(data);
        BlockPos start = helper.absolutePos(new BlockPos(0, 6, 0));
        VanillaMinecartRouteRecord record = VanillaMinecartRouteRecord.planned("pale_mirror:multi_repair_test",
                level.dimension().location().toString(), "pale_mirror:multi_repair_route", start, start.east());
        BlockPos first = record.railPosition(0);
        BlockPos second = record.railPosition(1);
        String rail = Blocks.RAIL.defaultBlockState().toString();
        record.capture(first, Blocks.AIR.defaultBlockState().toString());
        record.approve(first, rail);
        record.capture(second, Blocks.AIR.defaultBlockState().toString());
        record.approve(second, rail);
        record.initializeAuthoredTopology();
        record.disconnectTopology(first);
        level.setBlock(first.below(), Blocks.GRAVEL.defaultBlockState(), 3);
        level.setBlock(second.below(), Blocks.GRAVEL.defaultBlockState(), 3);
        level.setBlock(first, Blocks.RAIL.defaultBlockState(), 3);
        level.setBlock(second, Blocks.AIR.defaultBlockState(), 3);
        registerCanonicalRoute(data, record);

        VanillaMinecartRouteRuntime.tick(level.getServer(), data, new DomainServices().commands());
        helper.assertValueEqual(record.status(), VanillaMinecartRouteStatus.SUSPENDED,
                "repairing one cell must not resume a route while another loaded break remains");
        helper.assertValueEqual(record.topologyIssueCount(), 1,
                "a disconnected observed graph must retain one actionable issue marker");
        helper.assertValueEqual(record.topologyIssue(), second,
                "repair diagnostics must advance to the next missing accepted node");

        level.setBlock(second, Blocks.RAIL.defaultBlockState(), 3);
        VanillaMinecartRouteRuntime.tick(level.getServer(), data, new DomainServices().commands());
        helper.assertValueEqual(record.status(), VanillaMinecartRouteStatus.ACTIVE,
                "the physical route must resume as soon as all known loaded breaks match provenance");
        helper.assertValueEqual(record.topologyIssueCount(), 0,
                "a connected replacement graph must clear its issue marker");
        helper.succeed();
    }

    @GameTest(batch = "pm-vanilla-minecart-topology", templateNamespace = "minecraft",
            template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void playerRerouteIsAcceptedByConnectivityRatherThanExactAuthoredCells(GameTestHelper helper) {
        if (GameTestProfiles.createAdapterOnly()) { helper.succeed(); return; }
        ServerLevel level = helper.getLevel();
        PaleMirrorSavedData data = PaleMirrorSavedData.get(level.getServer().overworld());
        reset(data);
        BlockPos start = helper.absolutePos(new BlockPos(0, 6, 0));
        BlockPos target = start.east(4);
        VanillaMinecartRouteRecord record = VanillaMinecartRouteRecord.planned("pale_mirror:reroute_test",
                level.dimension().location().toString(), "pale_mirror:reroute_route", start, target);
        record.begin();
        record.verify();
        record.initializeAuthoredTopology();
        record.activate();
        registerCanonicalRoute(data, record);

        java.util.LinkedHashMap<BlockPos, RailShape> reroute = new java.util.LinkedHashMap<>();
        reroute.put(start, RailShape.SOUTH_EAST);
        reroute.put(start.south(), RailShape.NORTH_SOUTH);
        reroute.put(start.south(2), RailShape.NORTH_EAST);
        for (int x = 1; x < 4; x++) reroute.put(start.south(2).east(x), RailShape.EAST_WEST);
        reroute.put(target.south(2), RailShape.NORTH_WEST);
        reroute.put(target.south(), RailShape.NORTH_SOUTH);
        reroute.put(target, RailShape.SOUTH_WEST);
        railGraph(level, reroute);
        record.markTopologyChunkDirty(new net.minecraft.world.level.ChunkPos(start));
        record.markTopologyChunkDirty(new net.minecraft.world.level.ChunkPos(target.south(2)));

        // Topology reconciliation is intentionally chunk-budgeted. Give the
        // runtime enough bounded passes to consume both dirty chunks rather
        // than making this test depend on the GameTest's chunk alignment.
        for (int pass = 0; pass < 4; pass++)
            VanillaMinecartRouteRuntime.tick(level.getServer(), data, new DomainServices().commands());
        helper.assertValueEqual(record.status(), VanillaMinecartRouteStatus.ACTIVE,
                "a connected player reroute must restore service without rebuilding the authored cells; diagnostic="
                        + record.diagnostic() + ", graph=" + record.observedRailShapes());
        helper.assertValueEqual(record.acceptedRailPath().size(), 9,
                "the persisted route graph must adopt the complete detour");
        helper.assertTrue(record.acceptedRailPath().contains(start.south(2).east(2).asLong()),
                "the accepted carrier path must follow the player's physical reroute");
        helper.succeed();
    }

    private static void railGraph(ServerLevel level, java.util.Map<BlockPos, RailShape> graph) {
        // This is a synthetic already-finished player graph. Install its final
        // states directly into the GameTest chunks so vanilla's incremental
        // rail neighbour algorithm cannot normalize a half-built corner before
        // the rest of the path exists.
        graph.keySet().forEach(position -> level.getChunkAt(position.below()).setBlockState(
                position.below(), Blocks.STONE.defaultBlockState(), false));
        graph.forEach((position, shape) -> level.getChunkAt(position).setBlockState(position,
                Blocks.RAIL.defaultBlockState().setValue(RailBlock.SHAPE, shape), false));
    }

    private static void registerCanonicalRoute(PaleMirrorSavedData data, VanillaMinecartRouteRecord record) {
        GameTestStateReset.registerMinimalRouteRegion(data, record.regionId(), new WorldObjectId(record.routeId()));
        data.vanillaMinecartRoutes().put(record.regionId(), record);
    }

    private static void reset(PaleMirrorSavedData data) {
        GameTestStateReset.resetAll(data);
    }
}
