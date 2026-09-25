package io.farfrontier.palemirror.internal.frontier.v3;

import net.minecraft.nbt.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.UUID;
import java.util.zip.DeflaterOutputStream;
import static org.junit.jupiter.api.Assertions.*;

class FrontierV3OfflineActorAbsenceTest {
    @TempDir Path world;
    private static final UUID TARGET = UUID.fromString("941022a1-9431-381b-a497-bb9d553971ac");

    @Test void detectsExactUuidEvenInsidePassengers() throws Exception {
        var passenger = new CompoundTag(); passenger.putUUID("UUID", TARGET);
        var passengers = new ListTag(); passengers.add(passenger);
        var vehicle = new CompoundTag(); vehicle.put("Passengers", passengers);
        assertThrows(IOException.class, () -> FrontierV3OfflineActorAbsence.scan(region(vehicle), TARGET));
        assertEquals(1, FrontierV3OfflineActorAbsence.scan(region(new CompoundTag()), TARGET));
    }

    @Test void unsupportedExternalCorruptAndOverlappingRecordsAreNotAbsence() throws Exception {
        assertThrows(IOException.class, () -> FrontierV3OfflineActorAbsence.scan(new byte[0], TARGET));
        var external = region(new CompoundTag()); external[8196] = (byte) 130;
        assertThrows(IOException.class, () -> FrontierV3OfflineActorAbsence.scan(external, TARGET));
        var truncated = new byte[100];
        assertThrows(IOException.class, () -> FrontierV3OfflineActorAbsence.scan(truncated, TARGET));
        var overlap = region(new CompoundTag()); ByteBuffer.wrap(overlap).putInt(4, (2 << 8) | 1);
        assertThrows(IOException.class, () -> FrontierV3OfflineActorAbsence.scan(overlap, TARGET));
    }

    @Test void holdsSessionLockThroughCallbackAndScansOtherDimensions() throws Exception {
        NbtIo.writeCompressed(new CompoundTag(), world.resolve("level.dat"));
        Files.write(world.resolve("session.lock"), new byte[0]);
        Path entities = Files.createDirectories(world.resolve("dimensions/test/other/entities"));
        Files.write(entities.resolve("r.0.0.mca"), region(new CompoundTag()));
        int count = FrontierV3OfflineActorAbsence.withProof(world, TARGET, proof -> {
            assertEquals(1, proof.entityChunks()); assertEquals(2, proof.files().size());
            assertEquals(64, proof.files().getFirst().sha256().length());
            try(var channel=FileChannel.open(world.resolve("session.lock"), StandardOpenOption.WRITE)) {
                assertThrows(java.nio.channels.OverlappingFileLockException.class, channel::tryLock);
            }
            return proof.entityChunks();
        });
        assertEquals(1, count);
        var actor = new CompoundTag(); actor.putUUID("UUID", TARGET);
        Files.write(entities.resolve("r.0.0.mca"), region(actor));
        assertThrows(IOException.class, () -> FrontierV3OfflineActorAbsence.withProof(world, TARGET, proof -> fail("must not enter repair")));
    }

    @Test void playerAndLevelNestedEntitiesAreNotGlobalAbsence() throws Exception {
        NbtIo.writeCompressed(new CompoundTag(), world.resolve("level.dat"));
        Files.write(world.resolve("session.lock"), new byte[0]);
        Path entities = Files.createDirectories(world.resolve("entities"));
        Files.write(entities.resolve("r.0.0.mca"), region(new CompoundTag()));
        Path playerData = Files.createDirectories(world.resolve("playerdata"));
        var nested = new CompoundTag(); nested.putUUID("UUID", TARGET);
        var rootVehicle = new CompoundTag(); rootVehicle.put("Entity", nested);
        var player = new CompoundTag(); player.put("RootVehicle", rootVehicle);
        Path playerFile = playerData.resolve("941022a1-9431-381b-a497-bb9d553971ac.dat");
        NbtIo.writeCompressed(player, playerFile);
        assertThrows(IOException.class, () -> FrontierV3OfflineActorAbsence.withProof(world, TARGET, proof -> fail("player NBT retains the body")));
        Files.delete(playerFile);
        var level = new CompoundTag(); var data = new CompoundTag(); data.put("Player", player); level.put("Data", data);
        NbtIo.writeCompressed(level, world.resolve("level.dat"));
        assertThrows(IOException.class, () -> FrontierV3OfflineActorAbsence.withProof(world, TARGET, proof -> fail("level NBT retains the body")));
    }

    private static byte[] region(CompoundTag entity) throws IOException {
        var root = new CompoundTag(); var entities = new ListTag(); entities.add(entity); root.put("Entities", entities);
        var compressed = new ByteArrayOutputStream();
        try(var output = new DataOutputStream(new DeflaterOutputStream(compressed))) { NbtIo.write(root, output); }
        var bytes = compressed.toByteArray(); var region = new byte[12288]; var buffer = ByteBuffer.wrap(region);
        buffer.putInt(0, (2 << 8) | 1); buffer.putInt(8192, bytes.length + 1); region[8196] = 2;
        System.arraycopy(bytes, 0, region, 8197, bytes.length); return region;
    }
}
