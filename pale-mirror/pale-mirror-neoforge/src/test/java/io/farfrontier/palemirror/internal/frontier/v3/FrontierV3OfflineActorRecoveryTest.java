package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import net.minecraft.nbt.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class FrontierV3OfflineActorRecoveryTest {
    @TempDir Path world;
    private static final SubjectId ACTOR = new SubjectId("resident:1-1");

    @Test void lockedEntryRejectsWrongTargetsAndRecoversOnlyTheDeclaredWorld() throws Exception {
        Files.write(world.resolve("session.lock"), new byte[0]);
        var settings = new CompoundTag(); settings.putLong("seed", 91L);
        var levelData = new CompoundTag(); levelData.put("WorldGenSettings", settings);
        var level = new CompoundTag(); level.put("Data", levelData);
        NbtIo.writeCompressed(level, world.resolve("level.dat"));
        Path dimension = world.resolve("dimensions/pale_mirror/frontier_graybox");
        Path data = Files.createDirectories(dimension.resolve("data"));
        Path entities = Files.createDirectories(dimension.resolve("entities"));
        Files.write(entities.resolve("r.0.0.mca"), new byte[8192]);
        var root = new CompoundTag(); root.putInt("DataVersion", 3955);
        root.put("data", FrontierV3AmbientCarrierLedger.emptyForTest().save(new CompoundTag(), null));
        Path ledger = data.resolve("pale_mirror_frontier_v3_ambient_carriers_ZnJvbnRpZXI6Z3JheWJveA.dat");
        NbtIo.writeCompressed(root, ledger);
        var state = FrontierV3OfflineActorRecoveryPlanTest.prepared(FrontierV3PhysicalWorld.WORLD_ID);
        var runtime = FrontierV3ServerRuntime.start(FrontierV3OfflineActorRecoveryPlanTest.configuration(state),
                new FrontierFileStore(world, FrontierWorldRuntimeDefinition.payloadCodecs()), 1000);
        runtime.shutdown();
        byte[] before = Files.readAllBytes(ledger);
        assertThrows(IOException.class, () -> FrontierV3OfflineActorRecovery.recover(world, 92L, 0L, ACTOR, 2L, true));
        assertThrows(IOException.class, () -> FrontierV3OfflineActorRecovery.recover(world, 91L, 10L, ACTOR, 2L, true));
        assertThrows(IOException.class, () -> FrontierV3OfflineActorRecovery.recover(world, 91L, 0L, ACTOR, 3L, true));
        try (var channel = FileChannel.open(world.resolve("session.lock"), StandardOpenOption.WRITE);
             var lock = channel.lock()) {
            assertThrows(OverlappingFileLockException.class,
                    () -> FrontierV3OfflineActorRecovery.recover(world, 91L, 0L, ACTOR, 2L, true));
        }
        assertTrue(FrontierV3OfflineActorRecovery.recover(world, 91L, 0L, ACTOR, 2L, false).startsWith("inspected"));
        assertArrayEquals(before, Files.readAllBytes(ledger));
        try (var files = Files.list(data)) { assertEquals(1L, files.count()); }
        String result = FrontierV3OfflineActorRecovery.recover(world, 91L, 0L, ACTOR, 2L, true);
        assertTrue(result.contains("head=2 lease=CLOSED"), result);
        assertEquals(result, FrontierV3OfflineActorRecovery.recover(world, 91L, 0L, ACTOR, 2L, true));
        assertThrows(IOException.class, () -> FrontierV3OfflineActorRecovery.recover(world, 91L, 2L, ACTOR, 2L, true));
        assertTrue(FrontierV3AmbientCarrierLedger.load(NbtIo.readCompressed(ledger,
                NbtAccounter.unlimitedHeap()).getCompound("data"), null).hasCarrier(ACTOR));
    }
}
