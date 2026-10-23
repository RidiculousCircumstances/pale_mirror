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
            Runnable apply = () -> {
                helper.assertTrue(FrontierV3ManagedBlockWrites.preparePlannedWrites(level, runtime, writes),
                        "the durable continuation must retain the same complete fence");
                var result = FrontierV3MinecraftOperation.apply(level, declaration, List.of(port),
                        () -> level.getBlockState(pos).is(Blocks.COAL_ORE), () -> {
                            helper.assertTrue(FrontierV3NavigationObservation.blockWrite(level, runtime, pos,
                                    Blocks.AIR.defaultBlockState(), false), "native write cannot discover a deferred fence inside its attempt");
                            effects.incrementAndGet(); return level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                        }, () -> level.getBlockState(pos).isAir());
                helper.assertTrue(result.complete(), "one admitted terrain mutation must settle its common operation");
            };
            delay.set(true);
            runtime.runPersistenceTurn(() -> helper.assertFalse(
                    FrontierV3ManagedBlockWrites.preparePlannedWrites(level, runtime, writes, apply),
                    "asynchronous canonical durability is waiting, not an admitted mutation"));
            helper.assertTrue(!tasks.isEmpty() && effects.get() == 0 && operations.find(declaration).isEmpty()
                    && level.getBlockState(pos).is(Blocks.COAL_ORE), "no native ticket or effect may precede the geometry receipt");
            delay.set(false); while (!tasks.isEmpty()) tasks.remove().run();
            runtime.runPersistenceTurn(() -> { });
            helper.assertTrue(effects.get() == 1 && level.getBlockState(pos).isAir(), "durability resumes the effect exactly once");
            apply.run();
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
}
