package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FrontierV3OperationTerrainSavesTest {
    private static final BlockPos POSITION = new BlockPos(-17, 64, 33);
    private static FrontierV3OperationReceipt.NativeReceipt receipt() {
        var participant = new FrontierV3OperationReceipt.Participant(FrontierV3OperationReceipt.Medium.TERRAIN, Long.toString(POSITION.asLong()), 9);
        return FrontierV3OperationReceipt.prepare(new FrontierV3OperationReceipt.Declaration(FrontierV3OperationReceipt.Protocol.STATION_FUEL, new SubjectId("container:exact"),
                "take", "intent", Set.of(participant)), 1).nativeReceipt(participant);
    }
    private static CompoundTag chunk(FrontierV3OperationReceipt.NativeReceipt receipt) {
        var chunk = new ChunkPos(POSITION); var data = new CompoundTag(); data.putInt("xPos", chunk.x); data.putInt("zPos", chunk.z);
        var entity = new CompoundTag(); entity.putInt("x", POSITION.getX()); entity.putInt("y", POSITION.getY()); entity.putInt("z", POSITION.getZ());
        var persistent = new CompoundTag(); persistent.putLong(FrontierV3NativeOperationMarkers.LIFETIME, 9);
        FrontierV3NativeOperationMarkers.stamp(persistent, receipt); entity.put("NeoForgeData", persistent);
        var entities = new ListTag(); entities.add(entity); data.put("block_entities", entities); return data;
    }
    @Test void receiptMustBeCoSerializedWithItsExactBlockEntityAndChunk() {
        var receipt = receipt(); var data = chunk(receipt); var position = new ChunkPos(POSITION);
        assertEquals(List.of(receipt), FrontierV3OperationTerrainSaves.receipts(position, data));
        assertTrue(FrontierV3OperationTerrainSaves.receipts(new ChunkPos(0, 0), data).isEmpty());
        data.getList("block_entities", Tag.TAG_COMPOUND).getCompound(0).putInt("x", POSITION.getX() + 1);
        assertThrows(IllegalStateException.class, () -> FrontierV3OperationTerrainSaves.receipts(position, data));
    }
    @Test void absentOrReplacedNativeEvidenceCannotAcknowledgeTheOperation() {
        var receipt = receipt(); var data = chunk(receipt); var position = new ChunkPos(POSITION);
        var entity = data.getList("block_entities", Tag.TAG_COMPOUND).getCompound(0);
        entity.getCompound("NeoForgeData").putLong(FrontierV3NativeOperationMarkers.LIFETIME, 10);
        assertThrows(IllegalStateException.class, () -> FrontierV3OperationTerrainSaves.receipts(position, data));
        entity.remove("NeoForgeData"); assertTrue(FrontierV3OperationTerrainSaves.receipts(position, data).isEmpty());
    }
}
