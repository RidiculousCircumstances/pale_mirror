package io.farfrontier.palemirror.frontier.reference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ReferenceGrayboxStateRootTest {
    @Test
    void readsTheCompleteGrayboxRootBeforeAnyMutableOwnerIsHydrated() {
        ReferenceWorld source = new ReferenceWorld(ReferenceWorldConfig.graybox1To40(42L));
        source.run(5);
        Map<String, Object> document = ReferenceGrayboxStateDocument.decode(
                ReferenceGrayboxStateDocument.encode(ReferenceGrayboxCanonicalState.capture(source)));

        ReferenceGrayboxStateRoot.State root = ReferenceGrayboxStateRoot.read(document.get("reference_state"));

        assertEquals(source.config(), root.config());
        assertEquals(source.day(), root.day());
        assertEquals(source.events(), root.events());
        assertArrayEquals(source.rng().state().words(), root.rng().words());
    }

    @Test
    void rejectsAReferenceStateThatOmitsAnOwner() {
        ReferenceWorld source = new ReferenceWorld(ReferenceWorldConfig.graybox1To40(42L));
        Map<String, Object> envelope = new LinkedHashMap<>(ReferenceGrayboxCanonicalState.capture(source));
        Map<String, Object> reference = new LinkedHashMap<>(ReferenceGrayboxStateReader.object(envelope.get("reference_state"), "reference state"));
        reference.remove("field");

        assertThrows(IllegalArgumentException.class, () -> ReferenceGrayboxStateRoot.read(reference));
    }
}
