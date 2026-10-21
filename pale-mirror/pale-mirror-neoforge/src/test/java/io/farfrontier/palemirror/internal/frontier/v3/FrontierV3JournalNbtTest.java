package io.farfrontier.palemirror.internal.frontier.v3;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

class FrontierV3JournalNbtTest {
    @Test void recordEqualityIgnoresCompoundOrderButNotMissingFieldsValuesOrTypes() throws Exception {
        // These distinct names share a hash bucket, exposing insertion order in the wire encoding.
        var first = new CompoundTag(); first.putInt("FB", 1); first.putInt("Ea", 2);
        var reordered = new CompoundTag(); reordered.putInt("Ea", 2); reordered.putInt("FB", 1);
        byte[] original = FrontierV3JournalNbt.encode(first), other = FrontierV3JournalNbt.encode(reordered);
        assertFalse(Arrays.equals(original, other));
        assertTrue(FrontierV3JournalNbt.same(original, other));
        reordered.putInt("FB", 3); assertFalse(FrontierV3JournalNbt.same(original, FrontierV3JournalNbt.encode(reordered)));
        reordered.putLong("FB", 1); assertFalse(FrontierV3JournalNbt.same(original, FrontierV3JournalNbt.encode(reordered)));
        reordered.remove("FB"); assertFalse(FrontierV3JournalNbt.same(original, FrontierV3JournalNbt.encode(reordered)));
        assertThrows(java.io.IOException.class, () -> FrontierV3JournalNbt.same(original, Arrays.copyOf(other, other.length + 1)));
    }
}
