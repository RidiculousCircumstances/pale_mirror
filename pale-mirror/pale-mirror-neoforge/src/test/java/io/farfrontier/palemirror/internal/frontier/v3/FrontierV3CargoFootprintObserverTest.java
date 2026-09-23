package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.CargoCarrierIdentity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Path;
import java.util.OptionalLong;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class FrontierV3CargoFootprintObserverTest {
    @TempDir Path directory;

    @Test void serializedMovedCartExtendsExactBirthBeforeWriteAndDoesNotLosePriorAttempt() throws IOException {
        var birth = birth(1); var successor = birth(2);
        var archive = new FrontierV3CargoFootprintArchive(directory, birth.world());
        archive.retain(birth); archive.retain(successor);
        var moved = new ChunkPos(8, 9);
        var serialized = chunk(moved, birth);
        var unchanged = serialized.copy();
        FrontierV3CargoFootprintObserver.beforeWrite(archive, birth.world(), moved, serialized);
        assertEquals(unchanged, serialized, "observer does not rewrite Minecraft save data");
        assertEquals(birth.include(moved.toLong()), archive.read(birth.entity(), 1).orElseThrow());
        assertEquals(successor, archive.read(birth.entity(), 2).orElseThrow());
        var reopened = new FrontierV3CargoFootprintArchive(directory, birth.world());
        assertEquals(2, reopened.inventory().size());
        assertTrue(reopened.read(birth.entity(), 1).orElseThrow().chunks().contains(birth.chunks().iterator().next()));
    }

    @Test void oldOrForgedCarrierCannotInventBirthOrOverwriteFinalRemoval() throws IOException {
        var birth = birth(1); var archive = new FrontierV3CargoFootprintArchive(directory, birth.world());
        var moved = new ChunkPos(8, 9); var serialized = chunk(moved, birth);
        assertThrows(IOException.class, () -> FrontierV3CargoFootprintObserver.beforeWrite(archive, birth.world(), moved, serialized));
        assertTrue(archive.inventory().isEmpty());
        archive.retain(birth.removedAt(moved.toLong()));
        FrontierV3CargoFootprintObserver.beforeWrite(archive, birth.world(), moved, serialized);
        assertEquals(birth.removedAt(moved.toLong()), archive.read(birth.entity(), 1).orElseThrow());
        var returned = new ChunkPos(10, 11);
        FrontierV3CargoFootprintObserver.beforeWrite(archive, birth.world(), returned, chunk(returned, birth));
        assertEquals(birth.removedAt(moved.toLong()).include(returned.toLong()), archive.read(birth.entity(), 1).orElseThrow());
        assertThrows(IOException.class, () -> FrontierV3CargoFootprintObserver.beforeWrite(archive, birth.world(), new ChunkPos(99, 99), serialized));
    }

    @Test void emptyColumnNeverBecomesDestructionEvidence() throws IOException {
        var birth = birth(1); var archive = new FrontierV3CargoFootprintArchive(directory, birth.world()); archive.retain(birth);
        FrontierV3CargoFootprintObserver.beforeWrite(archive, birth.world(), new ChunkPos(8, 9), null);
        assertEquals(birth, archive.read(birth.entity(), 1).orElseThrow());
        assertFalse(birth.coversRemovedFootprint(birth.chunks()));
    }

    private static FrontierV3CargoRetirementFootprint birth(long epoch) {
        var world = new WorldId("frontier:footprint-observer"); var lease = new SceneLeaseId("lease:footprint");
        var cargo = new SubjectId("cargo:footprint");
        return new FrontierV3CargoRetirementFootprint(world, lease, cargo, CargoCarrierIdentity.id(world, lease, cargo),
                1, epoch, Set.of(new ChunkPos(1, 2).toLong()), OptionalLong.empty());
    }

    private static CompoundTag chunk(ChunkPos position, FrontierV3CargoRetirementFootprint birth) {
        var entity = new CompoundTag(); entity.putUUID("UUID", birth.entity()); entity.putString("id", "minecraft:chest_minecart");
        var persistent = new CompoundTag(); persistent.put(FrontierV3CargoFootprintObserver.KEY, birth.save()); entity.put("NeoForgeData", persistent);
        var entities = new ListTag(); entities.add(entity);
        var chunk = new CompoundTag(); chunk.putIntArray("Position", new int[]{position.x, position.z}); chunk.put("Entities", entities);
        return chunk;
    }
}
