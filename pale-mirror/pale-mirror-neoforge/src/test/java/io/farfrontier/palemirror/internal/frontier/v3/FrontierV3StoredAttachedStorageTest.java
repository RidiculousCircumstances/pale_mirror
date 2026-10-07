package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import net.minecraft.nbt.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FrontierV3StoredAttachedStorageTest {
    @Test void vanillaCargoAddressesExcludeSaddleAndRetainBothEndSlots() {
        var entity = entity();
        var items = new ListTag(); items.add(stack(0, "minecraft:bread", 7)); items.add(stack(14, "minecraft:stone", 64));
        entity.put("Items", items);
        var evidence = FrontierV3StoredAttachedStorage.fromEntitySave(entity).orElseThrow();
        assertEquals(new SubjectId("container:pack-test"), evidence.containerId());
        assertEquals(7, evidence.slots().getFirst().count());
        assertEquals(64, evidence.slots().getLast().count());
        assertEquals(evidence, FrontierV3StoredAttachedStorage.load(evidence.save()));
        var changed = entity.copy(); changed.getList("Items", Tag.TAG_COMPOUND).getCompound(0).putInt("count", 6);
        assertNotEquals(evidence, FrontierV3StoredAttachedStorage.fromEntitySave(changed).orElseThrow());
    }
    @Test void duplicateOutOfRangeAndUnclassifiedSlotsCannotProveSavedCustody() {
        for (int invalid : new int[] {15, 255}) {
            var entity = entity(); var items = new ListTag(); items.add(stack(invalid, "minecraft:bread", 1)); entity.put("Items", items);
            assertTrue(FrontierV3StoredAttachedStorage.fromEntitySave(entity).isEmpty());
        }
        var entity = entity(); var items = new ListTag(); items.add(stack(0, "minecraft:bread", 1)); items.add(stack(0, "minecraft:stone", 1)); entity.put("Items", items);
        assertTrue(FrontierV3StoredAttachedStorage.fromEntitySave(entity).isEmpty());
        items.remove(1); var components = new CompoundTag(); components.putString("minecraft:custom_name", "claimed"); items.getCompound(0).put("components", components);
        assertTrue(FrontierV3StoredAttachedStorage.fromEntitySave(entity).isEmpty());
    }
    private static CompoundTag entity() {
        var entity = new CompoundTag(); entity.putString("id", "minecraft:donkey"); entity.putBoolean("ChestedHorse", true);
        var metadata = new CompoundTag(); metadata.putString(FrontierV3ExactItemPresentation.CONTAINER_ID_KEY, "container:pack-test");
        metadata.putString(FrontierV3ReferenceContainerCustodyExecutor.REPLICA_PROVENANCE_KEY, "replica:test"); entity.put("NeoForgeData", metadata);
        return entity;
    }
    private static CompoundTag stack(int slot, String kind, int count) {
        var tag = new CompoundTag(); tag.putByte("Slot", (byte) slot); tag.putString("id", kind); tag.putInt("count", count); return tag;
    }
}
