package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.geometry.*;
import io.farfrontier.palemirror.frontier.v3.persistence.*;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

/** Actual terrain/operation boundary under a deliberately deferred canonical force. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FrontierV3TerrainAdmissionGameTests {
    private FrontierV3TerrainAdmissionGameTests() { }
    @GameTest(batch = "pm-frontier-v3-block-extraction", templateNamespace = "minecraft",
            template = "bastion/mobs/empty", timeoutTicks = 80)
    public static void deferredGeologyDoesNotPoisonPreparedNativeOperation(GameTestHelper helper) throws Exception {
        var level = helper.getLevel(); var pos = helper.absolutePos(new BlockPos(2, 2, 2));
        var point = new BlockPosition(pos.getX(), pos.getY(), pos.getZ());
        var bootstrap = FrontierV3AmbientPhysicsGameTests.translatedBootstrap(FrontierBootstrapper.create(
                new WorldId("frontier:field-preparation"), 41, FrontierRulesets.fuelledQuarryGraybox()),
                pos.getX(), pos.getY() - 63, pos.getZ());
        var writes = new ArrayDeque<Runnable>(); var delay = new java.util.concurrent.atomic.AtomicBoolean(false);
        var directory = java.nio.file.Files.createTempDirectory(java.nio.file.Path.of("."), "field-preparation-");
        var disk = new FrontierFileStore(directory, FrontierWorldRuntimeDefinition.payloadCodecs(),
                action -> { if (delay.get()) writes.add(action); else action.run(); });
        var runtime = FrontierV3ServerRuntime.start(FrontierWorldRuntimeDefinition.configuration(bootstrap), disk, 10_000);
        try {
            level.setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
            var port = FrontierV3OperationChunks.current(level, pos);
            var declaration = new FrontierV3OperationReceipt.Declaration(FrontierV3OperationReceipt.Protocol.FIELD_PROJECTION,
                    new SubjectId("test:field-preparation"), "soil-transition", "stone-to-dirt", Set.of(port.identity()));
            var operations = FrontierV3OperationLedger.get(level);
            // Intent may already be durable while another owner still has to withdraw its knowledge.
            var prepared = operations.prepare(declaration); operations.persist(level);
            var effects = new java.util.concurrent.atomic.AtomicInteger();
            var planned = List.of(new FrontierV3ManagedBlockWrites.PlannedWrite(pos, Blocks.DIRT.defaultBlockState()));
            var request = new FrontierV3MinecraftOperation.Request[1];
            request[0] = new FrontierV3MinecraftOperation.Request(declaration, List.of(port),
                    () -> level.getBlockState(pos).is(Blocks.STONE),
                    FrontierV3ManagedBlockWrites.effect(level, runtime, planned, () -> {
                        helper.assertTrue(new FrontierV3ExtractionBlockWrites().observe(level, runtime, pos,
                                Blocks.DIRT.defaultBlockState(), false), "native ingress must not discover a pending geology fence");
                        effects.incrementAndGet(); return level.setBlock(pos, Blocks.DIRT.defaultBlockState(), 3);
                    }, () -> FrontierV3MinecraftOperation.applyDeferred(level, request[0])),
                    () -> level.getBlockState(pos).is(Blocks.DIRT));
            delay.set(true);
            runtime.runEnvironmentalSample(() -> helper.assertTrue(
                    FrontierV3MinecraftOperation.applyDeferred(level, request[0]).status() == FrontierV3MinecraftOperation.Status.WAITING_PREPARATION,
                    "pending prerequisite is a wait, not an unknown attempted effect"));
            helper.assertTrue(!writes.isEmpty() && effects.get() == 0 && operations.unattempted(prepared.ticket())
                    && !operations.unresolvedAttempt(prepared.ticket()), "waiting preserves the original unused permission");
            delay.set(false); while (!writes.isEmpty()) writes.remove().run();
            runtime.runEnvironmentalSample(() -> { });
            helper.assertTrue(effects.get() == 1 && runtime.decodedState().orElseThrow()
                    .extractionSites().geologicalExclusions().positions().contains(point),
                    "durability must wake the retained producer before a second poll");
            helper.assertTrue(FrontierV3MinecraftOperation.applyDeferred(level, request[0]).complete()
                    && effects.get() == 1, "repeat observes the retained receipt without another effect");
        } finally {
            delay.set(false); while (!writes.isEmpty()) writes.remove().run();
            runtime.shutdown(); level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
        }
        helper.succeed();
    }
    @GameTest(batch = "pm-frontier-v3-block-extraction", templateNamespace = "minecraft",
            template = "bastion/mobs/empty", timeoutTicks = 80)
    public static void delayedGeometryNeverConsumesNativeAttemptAndResumesBeforeObservation(GameTestHelper helper) throws Exception {
        var level = helper.getLevel(); var pos = helper.absolutePos(new BlockPos(2, 2, 2));
        var point = new BlockPosition(pos.getX(), pos.getY(), pos.getZ());
        var bootstrap = FrontierV3AmbientPhysicsGameTests.translatedBootstrap(FrontierBootstrapper.create(
                new WorldId("frontier:terrain-admission"), 41), pos.getX(), pos.getY() - 64, pos.getZ());
        var tasks = new ArrayDeque<Runnable>(); var delay = new java.util.concurrent.atomic.AtomicBoolean(false);
        var directory = java.nio.file.Files.createTempDirectory(java.nio.file.Path.of("."), "terrain-admission-");
        var disk = new FrontierFileStore(directory, FrontierWorldRuntimeDefinition.payloadCodecs(),
                action -> { if (delay.get()) tasks.add(action); else action.run(); });
        var runtime = FrontierV3ServerRuntime.start(FrontierWorldRuntimeDefinition.configuration(bootstrap), disk, 10_000);
        try {
            level.setBlock(pos, Blocks.COAL_ORE.defaultBlockState(), 2);
            FrontierV3CommandSubmission.submitResult(runtime, "terrain-fixture", "test",
                    new NavigationGeometryObserved(0, Map.of(point, FrontierV3NavigationShapes.capture(level, point))));
            var port = FrontierV3OperationChunks.current(level, pos);
            var declaration = new FrontierV3OperationReceipt.Declaration(FrontierV3OperationReceipt.Protocol.EXTRACTION_BLOCK,
                    new SubjectId("test:terrain-admission"), "one-coal", "coal-to-air", Set.of(port.identity()));
            var operations = FrontierV3OperationLedger.get(level);
            var effects = new java.util.concurrent.atomic.AtomicInteger();
            var writes = List.of(new FrontierV3ManagedBlockWrites.PlannedWrite(pos, Blocks.AIR.defaultBlockState()));
            class Producer {
                FrontierV3MinecraftOperation.Result apply() {
                    return FrontierV3MinecraftOperation.applyBatch(level, List.of(new FrontierV3MinecraftOperation.Request(
                            declaration, List.of(port), () -> level.getBlockState(pos).is(Blocks.COAL_ORE),
                            FrontierV3ManagedBlockWrites.effect(level, runtime, writes, () -> {
                                helper.assertTrue(FrontierV3NavigationObservation.blockWrite(level, runtime, pos,
                                        Blocks.AIR.defaultBlockState(), false), "native write must find the prepared geometry fence");
                                effects.incrementAndGet(); return level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                            }, this::apply), () -> level.getBlockState(pos).isAir()))).getFirst();
                }
            }
            var producer = new Producer();
            delay.set(true);
            runtime.runPersistenceTurn(() -> helper.assertTrue(producer.apply().status()
                    == FrontierV3MinecraftOperation.Status.WAITING_PREPARATION,
                    "asynchronous canonical durability is waiting, not an admitted mutation"));
            var intent = operations.find(declaration).orElseThrow();
            helper.assertTrue(!tasks.isEmpty() && effects.get() == 0 && operations.unattempted(intent.ticket())
                    && level.getBlockState(pos).is(Blocks.COAL_ORE), "prepared operation retains its unused native permission");
            delay.set(false); while (!tasks.isEmpty()) tasks.remove().run();
            runtime.runPersistenceTurn(() -> { });
            helper.assertTrue(effects.get() == 1 && level.getBlockState(pos).isAir(), "durability resumes the effect exactly once");
            helper.assertTrue(producer.apply().complete(), "confirmed native outcome remains observable");
            helper.assertTrue(effects.get() == 1, "confirmed native receipt observes without mining twice");
            var retained = operations.find(declaration).orElseThrow();
            var recovered = FrontierV3OperationLedger.load(operations.save(new net.minecraft.nbt.CompoundTag(), level.registryAccess()), level.registryAccess());
            helper.assertFalse(recovered.beginAttempt(retained.ticket()), "recovery must not invent a repeat permit from a saved operation");
        } finally {
            delay.set(false); while (!tasks.isEmpty()) tasks.remove().run();
            disk.close(); level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
        }
        helper.succeed();
    }
    @GameTest(batch = "pm-frontier-v3-block-extraction", templateNamespace = "minecraft",
            template = "bastion/mobs/empty", timeoutTicks = 80)
    public static void environmentalGeologyWriteWaitsForItsDurableFence(GameTestHelper helper) throws Exception {
        var level = helper.getLevel(); var pos = helper.absolutePos(new BlockPos(2, 2, 2));
        var point = new BlockPosition(pos.getX(), pos.getY(), pos.getZ());
        var bootstrap = FrontierV3AmbientPhysicsGameTests.translatedBootstrap(FrontierBootstrapper.create(
                new WorldId("frontier:environmental-geology"), 41, FrontierRulesets.fuelledQuarryGraybox()), pos.getX(), pos.getY() - 63, pos.getZ());
        var writes = new ArrayDeque<Runnable>(); var delay = new java.util.concurrent.atomic.AtomicBoolean(false);
        var directory = java.nio.file.Files.createTempDirectory(java.nio.file.Path.of("."), "environmental-geology-");
        var disk = new FrontierFileStore(directory, FrontierWorldRuntimeDefinition.payloadCodecs(),
                action -> { if (delay.get()) writes.add(action); else action.run(); });
        var runtime = FrontierV3ServerRuntime.start(FrontierWorldRuntimeDefinition.configuration(bootstrap), disk, 10_000);
        var owner = new FrontierV3ExtractionBlockWrites();
        try {
            level.setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
            helper.assertTrue(ExtractionTerrainFacts.at(bootstrap, point).isPresent()
                    && FrontierV3WorksiteRegistry.point(runtime.decodedState().orElseThrow(), pos).isEmpty(),
                    "fixture must exercise unowned declared terrain");
            delay.set(true);
            runtime.runEnvironmentalSample(() -> {
                if (owner.observe(level, runtime, pos, Blocks.DIRT.defaultBlockState(), false))
                    level.setBlock(pos, Blocks.DIRT.defaultBlockState(), 2);
            });
            helper.assertTrue(!writes.isEmpty() && level.getBlockState(pos).is(Blocks.STONE),
                    "pending geology durability must not admit the physical write");
            delay.set(false); while (!writes.isEmpty()) writes.remove().run();
            runtime.runEnvironmentalSample(() -> {
                helper.assertTrue(owner.observe(level, runtime, pos, Blocks.DIRT.defaultBlockState(), false),
                        "published predecessor permits the next native sample");
                level.setBlock(pos, Blocks.DIRT.defaultBlockState(), 2);
            });
            helper.assertTrue(level.getBlockState(pos).is(Blocks.DIRT)
                    && runtime.decodedState().orElseThrow().extractionSites().geologicalExclusions().positions().contains(point),
                    "the actual change retains its exact canonical exclusion");
        } finally {
            delay.set(false); while (!writes.isEmpty()) writes.remove().run();
            runtime.shutdown(); level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
        }
        helper.succeed();
    }
}
