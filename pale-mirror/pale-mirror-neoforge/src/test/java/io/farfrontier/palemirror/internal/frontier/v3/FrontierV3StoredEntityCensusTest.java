package io.farfrontier.palemirror.internal.frontier.v3;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

class FrontierV3StoredEntityCensusTest {
    private static final UUID BODY = UUID.fromString("b48b4b19-9c19-42ed-a288-cba47103fe39");

    @Test void namedIdentityPresenceRequiresTheWholeStoredColumnInventory() {
        var chunk = new ChunkPos(-1, 2);
        var data = new CompoundTag(); data.putIntArray("Position", new int[]{chunk.x, chunk.z});
        var entity = new CompoundTag(); entity.putUUID("UUID", BODY);
        var list = new ListTag(); list.add(entity); data.put("Entities", list);
        assertEquals(Set.of(BODY), FrontierV3StoredEntityCensus.select(Optional.of(data), chunk, Set.of(BODY)).targets());
        assertTrue(FrontierV3StoredEntityCensus.select(Optional.empty(), chunk, Set.of(BODY)).targets().isEmpty());
        assertThrows(IllegalStateException.class,
                () -> FrontierV3StoredEntityCensus.select(Optional.of(data), new ChunkPos(0, 2), Set.of(BODY)));
        list.add(entity.copy());
        assertThrows(IllegalStateException.class,
                () -> FrontierV3StoredEntityCensus.select(Optional.of(data), chunk, Set.of(BODY)));
    }

    @Test void uniquenessRequiresExactlyOneExpectedColumn() {
        var expected = new ChunkPos(1, 3); var other = new ChunkPos(2, 3);
        assertTrue(new FrontierV3StoredEntityCensus.Result(Map.of(BODY, Set.of(expected)), 1).uniqueAt(BODY, expected));
        assertFalse(new FrontierV3StoredEntityCensus.Result(Map.of(), 1).uniqueAt(BODY, expected));
        assertFalse(new FrontierV3StoredEntityCensus.Result(Map.of(BODY, Set.of(expected, other)), 1).uniqueAt(BODY, expected));
        assertFalse(new FrontierV3StoredEntityCensus.Result(Map.of(BODY, Set.of(other)), 1).uniqueAt(BODY, expected));
    }

    @Test void asynchronousWholeColumnPassRetainsDuplicateIdentityWithoutLoadingChunks() {
        var first = new ChunkPos(0, 0); var second = new ChunkPos(1, 0);
        var reader = new FrontierV3StoredEntityInventoryAccess() {
            @Override public CompletableFuture<Void> frontierV3$synchronizeStoredEntities() {
                return CompletableFuture.completedFuture(null);
            }
            @Override public CompletableFuture<Optional<CompoundTag>> frontierV3$readStoredEntityChunkAfterSync(ChunkPos chunk) {
                var data = new CompoundTag(); data.putIntArray("Position", new int[]{chunk.x, chunk.z});
                var body = new CompoundTag(); body.putUUID("UUID", BODY);
                var entities = new ListTag(); entities.add(body); data.put("Entities", entities);
                return CompletableFuture.completedFuture(Optional.of(data));
            }
            @Override public CompletableFuture<Optional<CompoundTag>> frontierV3$readStoredEntityChunk(ChunkPos chunk) {
                return frontierV3$readStoredEntityChunkAfterSync(chunk);
            }
        };
        var sightings = FrontierV3StoredEntityCensus.scanBatch(reader, java.util.List.of(first, second),
                Set.of(BODY), 0, new java.util.HashMap<>()).join();
        assertEquals(Set.of(first, second), sightings.get(BODY));
        assertFalse(new FrontierV3StoredEntityCensus.Result(sightings, 0).uniqueAt(BODY, first));
    }
}
