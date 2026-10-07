package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.BodyPosition;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class FrontierV3StoredEntityInspectionTest {
    private static final UUID ENTITY = UUID.fromString("52711fc4-6b44-4efa-b605-d1b6200db98d");

    private static CompoundTag storedChunk() {
        var entity = new CompoundTag(); entity.putUUID("UUID", ENTITY);
        entity.putString("id", "minecraft:villager");
        var position = new ListTag(); position.add(DoubleTag.valueOf(4.5));
        position.add(DoubleTag.valueOf(65.0)); position.add(DoubleTag.valueOf(5.5));
        entity.put("Pos", position); entity.put("Items", new ListTag());
        var entities = new ListTag(); entities.add(entity);
        var chunk = new CompoundTag(); chunk.putIntArray("Position", new int[]{0, 0});
        chunk.put("Entities", entities); return chunk;
    }

    @Test void foreignStoredIdentityIsPresenceNotOwnedRecoveryEvidence() {
        var chunk = storedChunk(); var other = UUID.randomUUID();
        var snapshot = FrontierV3StoredEntityInspection.select(chunk, new ChunkPos(0, 0), Set.of(ENTITY, other), null);
        assertEquals(Set.of(ENTITY), snapshot.present());
        assertTrue(snapshot.actors().isEmpty(), "a UUID without an owner declaration cannot authorize recovery");
        assertTrue(snapshot.actors().isEmpty());
        var invalid = chunk.copy();
        invalid.getList("Entities", net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0).remove("Items");
        var malformed = FrontierV3StoredEntityInspection.select(invalid, new ChunkPos(0, 0), Set.of(ENTITY), null);
        assertEquals(Set.of(ENTITY), malformed.present(), "invalid target is not proof of absence");
        assertTrue(malformed.actors().isEmpty());
    }


    @Test void misplacedOrDuplicateStoredIdentityIsNotAnInspectionResult() {
        var chunk = storedChunk();
        assertThrows(IllegalStateException.class, () -> FrontierV3StoredEntityInspection.select(chunk, new ChunkPos(1, 0), Set.of(ENTITY), null));
        chunk.getList("Entities", net.minecraft.nbt.Tag.TAG_COMPOUND).add(
                chunk.getList("Entities", net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0).copy());
        assertThrows(IllegalStateException.class, () -> FrontierV3StoredEntityInspection.select(chunk, new ChunkPos(0, 0), Set.of(ENTITY), null));
        assertTrue(FrontierV3StoredEntityInspection.select(null, new ChunkPos(0, 0), Set.of(ENTITY), null).present().isEmpty());
    }
}
