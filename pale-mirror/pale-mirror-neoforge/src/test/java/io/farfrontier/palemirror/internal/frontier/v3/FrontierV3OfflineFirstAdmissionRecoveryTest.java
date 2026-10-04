package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import net.minecraft.nbt.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.*;
import java.util.zip.DeflaterOutputStream;
import static org.junit.jupiter.api.Assertions.*;

class FrontierV3OfflineFirstAdmissionRecoveryTest {
    @TempDir Path world;
    private static final SubjectId ACTOR = new SubjectId("resident:1-1");

    @Test void stoppedWorldEntryScansBeforeOneExactProofBackedRearm() throws Exception {
        Files.write(world.resolve("session.lock"), new byte[0]);
        var settings = new CompoundTag(); settings.putLong("seed", 91L);
        var data = new CompoundTag(); data.put("WorldGenSettings", settings);
        var level = new CompoundTag(); level.put("Data", data);
        NbtIo.writeCompressed(level, world.resolve("level.dat"));
        Path dimension = world.resolve("dimensions/pale_mirror/frontier_graybox");
        Path saveData = Files.createDirectories(dimension.resolve("data"));
        Path entities = Files.createDirectories(dimension.resolve("entities"));
        Files.write(entities.resolve("r.0.0.mca"), new byte[8192]);
        var state = FrontierV3OfflineActorRecoveryPlanTest.prepared(FrontierV3PhysicalWorld.WORLD_ID);
        var declaration = FrontierV3AmbientActorExecutor.carrierDeclaration(state, ACTOR, FrontierV3AmbientActorExecutor.entityId(state, ACTOR), FrontierV3ActorCarrierComposition.Representation.LIVE_BODY, 1L);
        var ledger = FrontierV3AmbientCarrierLedger.emptyForTest();
        assertTrue(ledger.registerFirstAdmission(FrontierV3ActorFirstAdmission.neverCreated(
                new FrontierV3ActorFirstAdmission.Identity(ACTOR, declaration.kind(), declaration.entityId()))));
        assertTrue(ledger.beginFirstAdmission(FrontierV3ActorOwnerBinding.body(declaration)));
        var root = new CompoundTag(); root.putInt("DataVersion", 3955); root.put("data", ledger.save(new CompoundTag(), null));
        Path carrierFile = saveData.resolve("pale_mirror_frontier_v3_ambient_carriers_ZnJvbnRpZXI6Z3JheWJveA.dat");
        NbtIo.writeCompressed(root, carrierFile);
        var runtime = FrontierV3ServerRuntime.start(FrontierV3OfflineActorRecoveryPlanTest.configuration(state),
                new FrontierFileStore(world, FrontierWorldRuntimeDefinition.payloadCodecs()), 1000);
        runtime.shutdown();
        byte[] before = Files.readAllBytes(carrierFile);
        assertThrows(IOException.class, () -> FrontierV3OfflineFirstAdmissionRecovery.recover(world, 92L, 0L, ACTOR, 2L, true));
        assertThrows(IOException.class, () -> FrontierV3OfflineFirstAdmissionRecovery.recover(world, 91L, 1L, ACTOR, 2L, true));
        assertThrows(IOException.class, () -> FrontierV3OfflineFirstAdmissionRecovery.recover(world, 91L, 0L, ACTOR, 3L, true));
        try (var channel = FileChannel.open(world.resolve("session.lock"), StandardOpenOption.WRITE);
             var lock = channel.lock()) {
            assertThrows(OverlappingFileLockException.class,
                    () -> FrontierV3OfflineFirstAdmissionRecovery.recover(world, 91L, 0L, ACTOR, 2L, true));
        }
        assertTrue(FrontierV3OfflineFirstAdmissionRecovery.recover(world, 91L, 0L, ACTOR, 2L, false).contains("no writes"));
        assertArrayEquals(before, Files.readAllBytes(carrierFile));
        try (var files = Files.list(saveData)) { assertEquals(1L, files.count()); }
        Files.write(entities.resolve("r.0.0.mca"), regionWithActor(declaration.entityId()));
        assertThrows(IOException.class, () -> FrontierV3OfflineFirstAdmissionRecovery.recover(world, 91L, 0L, ACTOR, 2L, true));
        assertArrayEquals(before, Files.readAllBytes(carrierFile));
        Files.write(entities.resolve("r.0.0.mca"), new byte[8192]);
        String result = FrontierV3OfflineFirstAdmissionRecovery.recover(world, 91L, 0L, ACTOR, 2L, true);
        assertTrue(result.contains("re-armed actor=resident:1-1"), result);
        var rearmed = FrontierV3AmbientCarrierLedger.load(NbtIo.readCompressed(carrierFile,
                NbtAccounter.unlimitedHeap()).getCompound("data"), null).firstAdmission(ACTOR).orElseThrow();
        assertEquals(FrontierV3ActorFirstAdmission.Phase.PROVEN_ABSENT, rearmed.phase());
        assertEquals(declaration.entityId(), rearmed.identity().entityId());
        assertTrue(FrontierV3OfflineFirstAdmissionRecovery.recover(world, 91L, 0L, ACTOR, 2L, true).contains("already re-armed"));
    }

    private static byte[] regionWithActor(java.util.UUID id) throws IOException {
        var body = new CompoundTag(); body.putUUID("UUID", id);
        var entities = new ListTag(); entities.add(body);
        var root = new CompoundTag(); root.put("Entities", entities);
        var compressed = new ByteArrayOutputStream();
        try (var output = new DataOutputStream(new DeflaterOutputStream(compressed))) { NbtIo.write(root, output); }
        byte[] payload = compressed.toByteArray(), region = new byte[12288];
        var header = ByteBuffer.wrap(region); header.putInt(0, (2 << 8) | 1); header.putInt(8192, payload.length + 1);
        region[8196] = 2; System.arraycopy(payload, 0, region, 8197, payload.length);
        return region;
    }
}
