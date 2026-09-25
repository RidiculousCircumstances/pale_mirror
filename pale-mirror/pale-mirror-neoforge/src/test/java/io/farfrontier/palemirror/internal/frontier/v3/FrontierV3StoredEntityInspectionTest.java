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
    private static final UUID CART = UUID.fromString("52711fc4-6b44-4efa-b605-d1b6200db98d");

    private static CompoundTag storedChunk() {
        var entity = new CompoundTag(); entity.putUUID("UUID", CART);
        entity.putString("id", "minecraft:chest_minecart");
        var position = new ListTag(); position.add(DoubleTag.valueOf(4.5));
        position.add(DoubleTag.valueOf(65.0)); position.add(DoubleTag.valueOf(5.5));
        entity.put("Pos", position); entity.put("Items", new ListTag());
        var owner = new CompoundTag();
        owner.putString(FrontierV3CargoCarrierExecutor.LEASE_KEY, "lease:inspection");
        owner.putString(FrontierV3CargoCarrierExecutor.CARGO_KEY, "cargo:inspection");
        owner.putLong(FrontierV3CargoCarrierExecutor.REVISION_KEY, 3);
        owner.putLong(FrontierV3CargoCarrierExecutor.EPOCH_KEY, 2);
        entity.put("NeoForgeData", owner);
        var entities = new ListTag(); entities.add(entity);
        var chunk = new CompoundTag(); chunk.putIntArray("Position", new int[]{0, 0});
        chunk.put("Entities", entities); return chunk;
    }

    @Test void selectsOnlyExactNamedTypedEvidenceAndRetainsMalformedPresence() {
        var chunk = storedChunk(); var other = UUID.randomUUID();
        var snapshot = FrontierV3StoredEntityInspection.select(chunk, new ChunkPos(0, 0), Set.of(CART, other), null);
        assertEquals(Set.of(CART), snapshot.present());
        assertEquals(Set.of(CART), snapshot.cargo().keySet());
        assertTrue(snapshot.actors().isEmpty());
        var invalid = chunk.copy();
        invalid.getList("Entities", net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0).remove("Items");
        var malformed = FrontierV3StoredEntityInspection.select(invalid, new ChunkPos(0, 0), Set.of(CART), null);
        assertEquals(Set.of(CART), malformed.present(), "invalid target is not proof of absence");
        assertTrue(malformed.cargo().isEmpty());
    }

    @Test void recoveryRequiresTheExactTypedStoredCartNotJustItsUuid() {
        var receipt = new FrontierV3CargoDeparture(new SceneLeaseId("lease:inspection"),
                new SubjectId("cargo:inspection"), CART, 3, 2, new BodyPosition(4, 65, 5),
                IntStream.range(0, FrontierV3CargoDeparture.SLOTS).mapToObj(ignored -> new CompoundTag()).toList());
        var exact = FrontierV3StoredEntityInspection.select(storedChunk(), new ChunkPos(0, 0), Set.of(CART), null);
        assertTrue(exact.matches(receipt));
        var newerEpoch = new FrontierV3CargoDeparture(receipt.leaseId(), receipt.cargoId(), CART,
                receipt.sceneRevision(), 3, receipt.body(), receipt.inventory());
        assertFalse(exact.matches(newerEpoch));
        var malformed = storedChunk();
        malformed.getList("Entities", net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0).remove("Items");
        assertFalse(FrontierV3StoredEntityInspection.select(malformed, new ChunkPos(0, 0), Set.of(CART), null)
                .matches(receipt));
    }

    @Test void misplacedOrDuplicateStoredIdentityIsNotAnInspectionResult() {
        var chunk = storedChunk();
        assertThrows(IllegalStateException.class, () -> FrontierV3StoredEntityInspection.select(chunk, new ChunkPos(1, 0), Set.of(CART), null));
        chunk.getList("Entities", net.minecraft.nbt.Tag.TAG_COMPOUND).add(
                chunk.getList("Entities", net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0).copy());
        assertThrows(IllegalStateException.class, () -> FrontierV3StoredEntityInspection.select(chunk, new ChunkPos(0, 0), Set.of(CART), null));
        assertTrue(FrontierV3StoredEntityInspection.select(null, new ChunkPos(0, 0), Set.of(CART), null).present().isEmpty());
    }
}
