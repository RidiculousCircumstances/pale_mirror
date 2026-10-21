package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import net.minecraft.nbt.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FrontierV3OperationPlayersTest {
    @TempDir Path directory;
    @Test void actualTargetMustContainExactPlayerLifetimeAndOperationNotTheIntendedTemp() throws Exception {
        var id = UUID.randomUUID(); var bag = new CompoundTag();
        var participant = new FrontierV3OperationReceipt.Participant(FrontierV3OperationReceipt.Medium.PLAYER,
                id.toString(), FrontierV3NativeOperationMarkers.lifetime(bag));
        var operation = FrontierV3OperationReceipt.prepare(new FrontierV3OperationReceipt.Declaration(
                FrontierV3OperationReceipt.Protocol.PLAYER_GROUND_PICKUP, new SubjectId("settlement:test"),
                "pickup", "intent", Set.of(participant)), 1);
        FrontierV3NativeOperationMarkers.stamp(bag, operation.nativeReceipt(participant));
        var image = new CompoundTag(); image.putUUID("UUID", id);
        var data = new CompoundTag(); data.put(FrontierV3OperationPlayers.KEY, bag); image.put("NeoForgeData", data);
        var target = directory.resolve(id + ".dat"); var temp = directory.resolve("uninstalled.dat");
        NbtIo.writeCompressed(new CompoundTag(), target); NbtIo.writeCompressed(image, temp);
        assertTrue(FrontierV3OperationPlayers.receipts(id, FrontierV3OperationPlayers.durableImage(target)).isEmpty(),
                "failed replacement cannot acknowledge the intended temporary image");
        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        assertEquals(List.of(operation.nativeReceipt(participant)),
                FrontierV3OperationPlayers.receipts(id, FrontierV3OperationPlayers.durableImage(target)));
        assertThrows(IllegalStateException.class, () -> FrontierV3OperationPlayers.receipts(UUID.randomUUID(), image));
        bag.putLong(FrontierV3NativeOperationMarkers.LIFETIME, participant.incarnation() + 1);
        assertThrows(IllegalStateException.class, () -> FrontierV3OperationPlayers.receipts(id, image));
        Files.writeString(target, "broken native file");
        assertThrows(java.io.IOException.class, () -> FrontierV3OperationPlayers.durableImage(target));
    }
}
