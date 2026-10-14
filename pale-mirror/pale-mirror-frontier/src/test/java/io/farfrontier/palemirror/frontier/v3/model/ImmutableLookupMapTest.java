package io.farfrontier.palemirror.frontier.v3.model;

import org.junit.jupiter.api.Test;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ImmutableLookupMapTest {
    @Test void snapshotIsDefensiveOrderedAndCannotBeMutatedThroughViews() {
        var source = new LinkedHashMap<String, Integer>();
        source.put("BB", 2); source.put("Aa", 1); // equal String hash, distinct identities
        var snapshot = ImmutableLookupMap.copyOf(source);
        source.clear();
        assertEquals(List.of("BB", "Aa"), List.copyOf(snapshot.keySet()));
        assertEquals(Map.of("BB", 2, "Aa", 1), snapshot);
        assertEquals(1, snapshot.get("Aa"));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.put("x", 3));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.keySet().remove("BB"));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.entrySet().iterator().next().setValue(4));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.values().clear());
        assertSame(snapshot, ImmutableLookupMap.copyOf(snapshot));
        assertThrows(NullPointerException.class, () -> ImmutableLookupMap.copyOf(java.util.Collections.singletonMap("x", null)));
    }
}
