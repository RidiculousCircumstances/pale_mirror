package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.model.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.neoforged.neoforge.gametest.*;
import java.util.List;
import java.util.Optional;
import io.farfrontier.palemirror.frontier.v3.model.geometry.NavigationObservation;
import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.persistence.*;
import io.farfrontier.palemirror.frontier.v3.kernel.TransactionRecord;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;

/** Native shape/state boundary, including negative evidence; not a whole-product acceptance. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FrontierV3NavigationShapeGameTests {
    private FrontierV3NavigationShapeGameTests() { }
    @GameTest(batch = "pm-frontier-v3-scene-local-navigation", templateNamespace = "minecraft",
            template = "bastion/mobs/empty", timeoutTicks = 60)
    public static void neighbourWriteFencesOnlyItsDependentsAndObservationPublishesTorchAsKnownEmpty(GameTestHelper helper) {
        var level = helper.getLevel(); var pos = helper.absolutePos(new BlockPos(3, 2, 3));
        var neighbourPos = pos.north();
        var secondPos = pos.south();
        var origin = new BlockPosition(pos.getX(), pos.getY(), pos.getZ());
        var neighbour = new BlockPosition(neighbourPos.getX(), neighbourPos.getY(), neighbourPos.getZ());
        var second = new BlockPosition(secondPos.getX(), secondPos.getY(), secondPos.getZ());
        var bootstrap = FrontierV3AmbientPhysicsGameTests.translatedBootstrap(FrontierBootstrapper.create(
                new WorldId("frontier:neighbour-observation"), 41), pos.getX(), pos.getY() - 64, pos.getZ());
        level.setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
        level.setBlock(neighbourPos, Blocks.STONE.defaultBlockState(), 2);
        level.setBlock(secondPos, Blocks.STONE.defaultBlockState(), 2);
        var runtime = FrontierV3ServerRuntime.start(FrontierWorldRuntimeDefinition.configuration(bootstrap), new EphemeralStore(), 10_000);
        try {
            var sample = FrontierV3NavigationShapes.capture(level, origin).cell().orElseThrow();
            var evidence = NavigationObservation.known(sample, java.util.Map.of(neighbour, Optional.of(FrontierV3BlockStates.describe(Blocks.STONE.defaultBlockState()))));
            FrontierV3CommandSubmission.submit(runtime, "shape-dependent", "test",
                    new io.farfrontier.palemirror.frontier.v3.model.geometry.NavigationGeometryObserved(0, java.util.Map.of(origin, evidence,
                            second, FrontierV3NavigationShapes.capture(level, second))));
            long beforeBatch = runtime.decodedState().orElseThrow().navigationGeometry().revision();
            FrontierV3NavigationObservation.preparePlannedWrites(level, runtime, List.of(
                    new FrontierV3ManagedBlockWrites.PlannedWrite(neighbourPos, Blocks.TORCH.defaultBlockState()),
                    new FrontierV3ManagedBlockWrites.PlannedWrite(secondPos, Blocks.AIR.defaultBlockState())));
            helper.assertTrue(runtime.decodedState().orElseThrow().navigationGeometry().revision() == beforeBatch + 1,
                    "both planned writes and contextual dependents share one durable navigation transition");
            helper.assertTrue(FrontierV3NavigationObservation.blockWrite(level, runtime, neighbourPos, Blocks.TORCH.defaultBlockState(), false),
                    "ordinary physical mutation can install its durable fence");
            var fenced = runtime.decodedState().orElseThrow().navigationGeometry();
            helper.assertTrue(fenced.revision() == beforeBatch + 1
                            && fenced.cells().get(second).kind() == NavigationObservation.Kind.WRITE_PENDING,
                    "per-block validation preserves the exact batch fence without another canonical commit");
            helper.assertTrue(level.getBlockState(secondPos).is(Blocks.STONE), "preparation must not perform a physical write");
            helper.assertTrue(fenced.cells().get(origin).kind() == NavigationObservation.Kind.WRITE_PENDING
                    && fenced.cells().get(neighbour).kind() == NavigationObservation.Kind.WRITE_PENDING,
                    "both the changed cell and exact contextual dependent are fenced before effects");
            level.setBlock(neighbourPos, Blocks.TORCH.defaultBlockState(), 2);
            FrontierV3NavigationObservation.blockWrite(level, runtime, neighbourPos, Blocks.TORCH.defaultBlockState(), true);
            FrontierV3NavigationObservation.finishNativeWrites(level, runtime, List.of(
                    new FrontierV3ManagedBlockWrites.PlannedWrite(neighbourPos, Blocks.TORCH.defaultBlockState())));
            var observed = runtime.decodedState().orElseThrow().navigationGeometry();
            helper.assertTrue(observed.cells().get(neighbour).cell().orElseThrow().shape().boxes().isEmpty(),
                    "a real torch publishes empty collision, never an unresolved fence");
            helper.assertTrue(observed.cells().get(origin).cell().isPresent(), "dependent is resampled without actor movement or a timer retry");
            helper.assertTrue(observed.cells().get(second).kind() == NavigationObservation.Kind.WRITE_PENDING,
                    "a prepared but unattempted second write is not silently closed with its old native image");
        } finally {
            runtime.shutdown(); level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2); level.setBlock(neighbourPos, Blocks.AIR.defaultBlockState(), 2);
            level.setBlock(secondPos, Blocks.AIR.defaultBlockState(), 2);
        }
        helper.succeed();
    }
    @GameTest(batch = "pm-frontier-v3-scene-local-navigation", templateNamespace = "minecraft",
            template = "bastion/mobs/empty", timeoutTicks = 60)
    public static void realObservationProducerRegistersColdDefinitionsWithoutMaterializingAWorld(GameTestHelper helper) {
        var config = FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:shape-native-producer"),
                20260918065L, FrontierRulesets.installed("frontier-v3-quarry-graybox-r13"));
        var runtime = FrontierV3ServerRuntime.start(config, new EphemeralStore(), 10_000);
        try {
            FrontierV3NavigationObservation.tick(helper.getLevel(), runtime);
            var state = runtime.decodedState().orElseThrow();
            helper.assertTrue(runtime.status().kind() == FrontierV3RuntimeStatus.Kind.ACTIVE, "native geometry command must not quarantine");
            helper.assertTrue(!state.navigationGeometry().definitions().isEmpty(), "actual physical producer must own and publish its registered event");
            helper.assertTrue(state.navigationGeometry().cells().isEmpty(), "known COLD declarations require no physical block observations");
            for (var block : KnownNavigationBlocks.forState(state).values())
                helper.assertTrue(state.navigationGeometry().definitions().containsKey(block), "graybox source block has a shape definition: " + block);
            for (var deposit : state.extractionSites().deposits().values()) {
                var site = deposit.site();
                var endpoint = state.inventory().containers().values().stream().flatMap(container -> container.storage().stream())
                        .map(ResourceStoragePolicy::endpoint).filter(value -> value instanceof ShipmentEndpoint.ExtractiveSite storage
                            && storage.siteId().equals(site.id())).findFirst().orElseThrow();
                var knowledge = ResourceStorageNavigation.forEndpoint(state, endpoint);
                helper.assertTrue(!knowledge.plannedBetween(site.layout().entrance(), site.layout().storagePort()).isEmpty(),
                        "native catalog must preserve the actual COLD quarry entrance/storage approach");
                helper.assertTrue(!knowledge.plannedBetween(site.layout().storagePort(), site.layout().cells().getFirst().workstation()).isEmpty(),
                        "native catalog must preserve the actual COLD storage/work approach");
            }
        } finally { runtime.shutdown(); }
        helper.succeed();
    }
    @GameTest(batch = "pm-frontier-v3-scene-local-navigation", templateNamespace = "minecraft",
            template = "bastion/mobs/empty", timeoutTicks = 60)
    public static void unknownWriteFenceSurvivesWalRecoveryAndObservedShapeSurvivesCheckpoint(GameTestHelper helper) throws java.io.IOException {
        var directory = java.nio.file.Files.createTempDirectory(java.nio.file.Path.of("."), "navigation-recovery-");
        var nativePos = helper.absolutePos(new BlockPos(3, 2, 3));
        var bootstrap = FrontierV3AmbientPhysicsGameTests.translatedBootstrap(FrontierBootstrapper.create(
                new WorldId("frontier:shape-recovery"), 41), nativePos.getX(), nativePos.getY() - 64, nativePos.getZ());
        var config = FrontierWorldRuntimeDefinition.configuration(bootstrap);
        var store = new FrontierFileStore(directory, FrontierWorldRuntimeDefinition.payloadCodecs());
        var first = FrontierV3ServerRuntime.start(config, store, 10_000);
        var p = new BlockPosition(nativePos.getX(), nativePos.getY(), nativePos.getZ());
        helper.getLevel().setBlock(nativePos, Blocks.STONE.defaultBlockState(), 2);
        try {
            var fence = new io.farfrontier.palemirror.frontier.v3.model.geometry.NavigationGeometryObserved(0,
                    java.util.Map.of(p, NavigationObservation.unavailable(NavigationObservation.Kind.WRITE_PENDING)));
            helper.assertTrue(FrontierV3CommandSubmission.submitResult(first, "shape-fence", "test", fence) instanceof CommandResult.Accepted,
                    "write fence must be durable before physical mutation");
        } finally { store.close(); } // Abrupt process boundary: no runtime checkpoint.
        var second = FrontierV3ServerRuntime.start(config, new FrontierFileStore(directory, FrontierWorldRuntimeDefinition.payloadCodecs()), 10_000);
        try {
            var recovered = second.decodedState().orElseThrow();
            helper.assertTrue(recovered.navigationGeometry().cells().containsKey(p) && recovered.navigationGeometry().cells().get(p).kind() == NavigationObservation.Kind.WRITE_PENDING,
                    "WAL recovery must not turn an unresolved physical write into bootstrap air");
            FrontierV3NavigationObservation.tick(helper.getLevel(), second);
            helper.assertTrue(second.decodedState().orElseThrow().navigationGeometry().cells().get(p).cell().orElseThrow()
                    .shape().topAt(.5, .5).orElseThrow() == 1, "recovery observes the real preimage without an actor, route request or effect replay");
            helper.assertTrue(FrontierV3NavigationObservation.blockWrite(helper.getLevel(), second, nativePos,
                    Blocks.OAK_SLAB.defaultBlockState(), false), "new actual write needs its own durable fence");
            helper.getLevel().setBlock(nativePos, Blocks.OAK_SLAB.defaultBlockState(), 2);
            FrontierV3NavigationObservation.blockWrite(helper.getLevel(), second, nativePos, Blocks.OAK_SLAB.defaultBlockState(), true);
            FrontierV3NavigationObservation.finishNativeWrites(helper.getLevel(), second, List.of(
                    new FrontierV3ManagedBlockWrites.PlannedWrite(nativePos, Blocks.OAK_SLAB.defaultBlockState())));
        } finally { second.shutdown(); }
        var third = FrontierV3ServerRuntime.start(config, new FrontierFileStore(directory, FrontierWorldRuntimeDefinition.payloadCodecs()), 10_000);
        try {
            helper.assertTrue(third.decodedState().orElseThrow().navigationGeometry().cells().get(p).cell().orElseThrow().shape()
                    .topAt(.5, .5).orElseThrow() == .5, "checkpoint recovery retains native fractional support");
        } finally { third.shutdown(); }
        helper.getLevel().setBlock(nativePos, Blocks.AIR.defaultBlockState(), 2);
        helper.succeed();
    }
    @GameTest(batch = "pm-frontier-v3-scene-local-navigation", templateNamespace = "minecraft",
            template = "bastion/mobs/empty", timeoutTicks = 40)
    public static void retainedShapesMatchMinecraftAndResolveDeclaredColdStates(GameTestHelper helper) {
        var level = helper.getLevel(); var pos = helper.absolutePos(new BlockPos(3, 2, 3));
        var support = new BlockPosition(pos.getX(), pos.getY(), pos.getZ());
        for (var state : List.of(Blocks.OAK_SLAB.defaultBlockState(),
                Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP),
                Blocks.STONE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH),
                Blocks.STONE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST),
                Blocks.OAK_FENCE.defaultBlockState(), Blocks.FARMLAND.defaultBlockState(),
                Blocks.TORCH.defaultBlockState(), Blocks.WALL_TORCH.defaultBlockState())) {
            level.setBlock(pos, state, 2);
            var captured = FrontierV3NavigationShapes.capture(level, support).cell().orElseThrow();
            var declared = FrontierV3NavigationShapes.declared(state, support, level.getHeight(), level.getMinBuildHeight()).cell().orElseThrow();
            helper.assertTrue(captured.equals(declared), "COLD declared state and HOT state need identical geometry: " + state);
            helper.assertTrue(captured.shape().boxes().size() == state.getCollisionShape(level, pos).toAabbs().size(),
                    "adapter retains native boxes, not a full-cube approximation");
            helper.assertTrue(FrontierV3BlockStates.resolve(captured.observedState().orElseThrow()).equals(state),
                    "exact complete registry identity roundtrip");
        }
        level.setBlock(pos, Blocks.OAK_SLAB.defaultBlockState(), 2);
        helper.assertTrue(FrontierV3SemanticMovement.point(level, new SurfaceAnchor(support)).y == pos.getY() + .5,
                "HOT target uses fractional slab height");
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
        helper.succeed();
    }
    @GameTest(batch = "pm-frontier-v3-scene-local-navigation", templateNamespace = "minecraft",
            template = "bastion/mobs/empty", timeoutTicks = 40)
    public static void entityDependentShapesAndHazardsCannotMasqueradeAsDryAir(GameTestHelper helper) {
        var level = helper.getLevel(); var pos = helper.absolutePos(new BlockPos(3, 2, 3));
        var point = new BlockPosition(pos.getX(), pos.getY(), pos.getZ());
        level.setBlock(pos, Blocks.POWDER_SNOW.defaultBlockState(), 2);
        helper.assertTrue(FrontierV3NavigationShapes.capture(level, point).kind() == NavigationObservation.Kind.DYNAMIC_SHAPE_UNSUPPORTED,
                "powder snow needs an entity context, not an empty-context universal answer");
        var lava = FrontierV3NavigationShapes.declared(Blocks.LAVA.defaultBlockState(), point, level.getHeight(), level.getMinBuildHeight());
        helper.assertTrue(lava.cell().isPresent() && !lava.cell().orElseThrow().dry(), "fluid is a known hazard, not unknown geometry");
        var water = FrontierV3NavigationShapes.declared(Blocks.WATER.defaultBlockState(), point, level.getHeight(), level.getMinBuildHeight());
        helper.assertTrue(water.cell().isPresent() && !water.cell().orElseThrow().dry(), "irrigation must not poison neighbouring dry contacts");
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
        helper.succeed();
    }
    @GameTest(batch = "pm-frontier-v3-scene-local-navigation", templateNamespace = "minecraft",
            template = "bastion/mobs/empty", timeoutTicks = 40)
    public static void quarantinedOwnerRefusesMutationBeforeItsPhysicalEffects(GameTestHelper helper) {
        var level = helper.getLevel(); var pos = helper.absolutePos(new BlockPos(3, 2, 3));
        var point = new BlockPosition(pos.getX(), pos.getY(), pos.getZ());
        var bootstrap = FrontierV3AmbientPhysicsGameTests.translatedBootstrap(FrontierBootstrapper.create(
                new WorldId("frontier:shape-refusal"), 41), pos.getX(), pos.getY() - 64, pos.getZ());
        var runtime = FrontierV3ServerRuntime.start(FrontierWorldRuntimeDefinition.configuration(bootstrap), new EphemeralStore(), 10_000);
        try {
            level.setBlock(pos, Blocks.DIRT.defaultBlockState(), 2);
            helper.assertTrue(runtime.decodedState().orElseThrow().bootstrap().bounds().contains(point), "fixture must exercise managed bounds");
            var observed = FrontierV3NavigationShapes.capture(level, point);
            helper.assertTrue(FrontierV3CommandSubmission.submitResult(runtime, "shape-observed", "test",
                    new io.farfrontier.palemirror.frontier.v3.model.geometry.NavigationGeometryObserved(0,
                            java.util.Map.of(point, observed))) instanceof CommandResult.Accepted, "observed fixture admitted");
            var before = runtime.decodedState().orElseThrow();
            runtime.quarantine(new IllegalStateException("test: unavailable durable writer"));
            helper.assertTrue(!FrontierV3NavigationObservation.blockWrite(level, runtime, pos, Blocks.AIR.defaultBlockState(), false),
                    "failed preflight must not authorize destruction or its drops");
            helper.assertTrue(runtime.passiveOwnershipState().orElseThrow() == before, "refusal cannot alter canonical knowledge");
            helper.assertTrue(level.getBlockState(pos).is(Blocks.DIRT), "preflight cannot perform the physical action");
        } finally {
            runtime.shutdown();
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
        }
        helper.succeed();
    }
    private static final class EphemeralStore implements FrontierStore {
        public RecoveryImage recover(WorldId world) { return new RecoveryImage(world, Optional.empty(), List.of()); }
        public AppendReceipt append(TransactionRecord transaction, Durability durability) {
            return new AppendReceipt(transaction.id(), transaction.revision(), durability, transaction.revision().value());
        }
        public SnapshotReceipt installSnapshot(SnapshotRecord snapshot) { throw new UnsupportedOperationException("shape boundary does not checkpoint"); }
        public CompactionReceipt compact(WorldId world, Revision revision) { throw new UnsupportedOperationException("shape boundary does not compact"); }
    }
}
