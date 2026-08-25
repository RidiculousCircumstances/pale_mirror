package io.farfrontier.palemirror.frontier.reference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ReferenceGrayboxStateDocumentTest {
    @Test
    void preservesTheCompleteThirtyDayCanonicalDocumentByteForByte() {
        ReferenceWorld world = new ReferenceWorld(ReferenceWorldConfig.graybox1To40(42L));
        for (int day = 0; day < 30; day++) world.tick();
        Map<String, Object> original = ReferenceGrayboxCanonicalState.capture(world);

        byte[] encoded = ReferenceGrayboxStateDocument.encode(original);
        Map<String, Object> decoded = ReferenceGrayboxStateDocument.decode(encoded);

        assertEquals(ReferenceV2PublicSnapshot.canonicalJson(original), ReferenceV2PublicSnapshot.canonicalJson(decoded));
        assertArrayEquals(encoded, ReferenceGrayboxStateDocument.encode(decoded));
    }

    @Test
    void rejectsWrongRootAndCorruptOrTrailingWireData() {
        assertThrows(IllegalArgumentException.class, () -> ReferenceGrayboxStateDocument.encode(Map.of("codec", "wrong")));
        assertThrows(IllegalArgumentException.class, () -> ReferenceGrayboxStateDocument.decode(new byte[] {1, 2, 3}));

        byte[] valid = ReferenceGrayboxStateDocument.encode(ReferenceGrayboxCanonicalState.capture(
                new ReferenceWorld(ReferenceWorldConfig.graybox1To40(42L))));
        byte[] trailing = java.util.Arrays.copyOf(valid, valid.length + 1);
        assertThrows(IllegalArgumentException.class, () -> ReferenceGrayboxStateDocument.decode(trailing));
    }
}
