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
                var endpoint = state.inventory().containers().get(site.containerId()).storage().orElseThrow().endpoint();
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
        var config = FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:shape-recovery"), 41);
        var store = new FrontierFileStore(directory, FrontierWorldRuntimeDefinition.payloadCodecs());
        var first = FrontierV3ServerRuntime.start(config, store, 10_000);
        var p = new BlockPosition(20, 64, 20);
        try {
            var fence = new io.farfrontier.palemirror.frontier.v3.model.geometry.NavigationGeometryObserved(0,
                    java.util.Map.of(p, Optional.empty()));
            helper.assertTrue(FrontierV3CommandSubmission.submitResult(first, "shape-fence", "test", fence) instanceof CommandResult.Accepted,
                    "write fence must be durable before physical mutation");
        } finally { store.close(); } // Abrupt process boundary: no runtime checkpoint.
        var second = FrontierV3ServerRuntime.start(config, new FrontierFileStore(directory, FrontierWorldRuntimeDefinition.payloadCodecs()), 10_000);
        try {
            var recovered = second.decodedState().orElseThrow();
            helper.assertTrue(recovered.navigationGeometry().cells().containsKey(p) && recovered.navigationGeometry().cells().get(p).isEmpty(),
                    "WAL recovery must not turn an unresolved physical write into bootstrap air");
            var slab = FrontierV3NavigationShapes.declared(Blocks.OAK_SLAB.defaultBlockState(), p, helper.getLevel().getHeight(), helper.getLevel().getMinBuildHeight());
            FrontierV3CommandSubmission.submit(second, "shape-observed", "test",
                    new io.farfrontier.palemirror.frontier.v3.model.geometry.NavigationGeometryObserved(1, java.util.Map.of(p, slab)));
        } finally { second.shutdown(); }
        var third = FrontierV3ServerRuntime.start(config, new FrontierFileStore(directory, FrontierWorldRuntimeDefinition.payloadCodecs()), 10_000);
        try {
            helper.assertTrue(third.decodedState().orElseThrow().navigationGeometry().cells().get(p).orElseThrow().shape()
                    .topAt(.5, .5).orElseThrow() == .5, "checkpoint recovery retains native fractional support");
        } finally { third.shutdown(); }
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
                Blocks.OAK_FENCE.defaultBlockState(), Blocks.FARMLAND.defaultBlockState())) {
            level.setBlock(pos, state, 2);
            var captured = FrontierV3NavigationShapes.capture(level, support).orElseThrow();
            var declared = FrontierV3NavigationShapes.declared(state, support, level.getHeight(), level.getMinBuildHeight()).orElseThrow();
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
        helper.assertTrue(FrontierV3NavigationShapes.capture(level, point).isEmpty(),
                "powder snow needs an entity context, not an empty-context universal answer");
        var lava = FrontierV3NavigationShapes.declared(Blocks.LAVA.defaultBlockState(), point, level.getHeight(), level.getMinBuildHeight());
        helper.assertTrue(lava.isEmpty() || !lava.orElseThrow().dry(), "empty fluid collision is not permission to walk");
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
