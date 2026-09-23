package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.BodyPosition;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;

class FrontierV3CargoDepartureTest {
    private static FrontierV3CargoDeparture receipt() {
        return new FrontierV3CargoDeparture(new SceneLeaseId("lease:cargo-departure"), new SubjectId("cargo:departure"),
                UUID.fromString("54147a99-935d-43ed-8f6f-adb0a3b009ad"), 0, 3, new BodyPosition(1, 65, 2),
                IntStream.range(0, 27).mapToObj(ignored -> new CompoundTag()).toList());
    }

    @Test void snapshotCannotBeMutatedThroughItsInputsAccessorsOrSavedImage() {
        var original = receipt();
        original.inventory().getFirst().putString("foreign", "input");
        var saved = original.save();
        assertEquals(original, FrontierV3CargoDeparture.load(saved));
        saved.putLong("epoch", 4);
        assertEquals(3, original.authorityEpoch());
        assertTrue(original.inventory().getFirst().isEmpty());
    }

    @Test void incompleteOrWronglyTypedEvidenceIsNeverAValidEmptyInventory() {
        for (var field : java.util.List.of("lease", "cargo", "entity", "revision", "epoch", "x", "y", "z", "inventory")) {
            var missing = receipt().save(); missing.remove(field);
            assertThrows(RuntimeException.class, () -> FrontierV3CargoDeparture.load(missing), field);
        }
        var malformed = receipt().save(); malformed.putString("epoch", "3");
        assertThrows(IllegalStateException.class, () -> FrontierV3CargoDeparture.load(malformed));
        var truncated = receipt().save(); truncated.getList("inventory", net.minecraft.nbt.Tag.TAG_COMPOUND).remove(0);
        assertThrows(IllegalStateException.class, () -> FrontierV3CargoDeparture.load(truncated));
        var invalid = receipt().save(); invalid.putLong("epoch", 0);
        assertThrows(IllegalArgumentException.class, () -> FrontierV3CargoDeparture.load(invalid));
    }
}
