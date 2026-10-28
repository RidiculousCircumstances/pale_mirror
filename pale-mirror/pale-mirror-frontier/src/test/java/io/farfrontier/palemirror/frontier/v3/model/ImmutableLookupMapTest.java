package io.farfrontier.palemirror.frontier.v3.model;

import org.junit.jupiter.api.Test;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ImmutableLookupMapTest {
    @Test void membershipSnapshotIsDefensiveAndImmutableIncludingAbsentMutations() {
        var source = new java.util.ArrayList<>(List.of("BB", "Aa", "BB"));
        var snapshot = ImmutableLookupSet.copyOf(source); source.clear();
        assertEquals(java.util.Set.of("BB", "Aa"), snapshot);
        assertEquals(List.of("BB", "Aa"), List.copyOf(snapshot));
        assertTrue(snapshot.contains("BB")); assertFalse(snapshot.contains("missing"));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.iterator().remove());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.remove("missing"));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.removeIf(value -> false));
        assertSame(snapshot, ImmutableLookupSet.copyOf(snapshot));
        assertThrows(NullPointerException.class, () -> ImmutableLookupSet.copyOf(java.util.Collections.singleton(null)));
    }
    @Test void directChangesAreExactAndMutableOrNonAdjacentSourcesStillReceiveCompleteComparison() {
        var before = ImmutableLookupMap.copyOf(Map.of("Aa", 1, "BB", 2));
        var changed = ImmutableLookupMap.withEntry(before, "Aa", 3);
        assertEquals(java.util.Set.of("Aa"), ((ImmutableMapImage<String, Integer>) changed).changedKeysFrom(before).orElseThrow());
        assertTrue(ImmutableMapChanges.sameKeys(before, changed));
        var differences = new java.util.ArrayList<ImmutableMapChanges.Change<String, Integer>>();
        ImmutableMapChanges.forEach(before, changed, differences::add);
        assertEquals(List.of(new ImmutableMapChanges.Change<>("Aa", 1, 3)), differences);
        var later = ImmutableLookupMap.withEntry(changed, "new", 4);
        differences.clear(); ImmutableMapChanges.forEach(before, later, differences::add);
        assertEquals(List.of("Aa", "new"), differences.stream().map(ImmutableMapChanges.Change::key).sorted().toList());
        assertFalse(ImmutableMapChanges.sameKeys(before, ImmutableLookupMap.withoutEntry(later, "BB")),
                "a non-adjacent remove/add cannot certify equal membership from equal sizes");
        var removed = ImmutableLookupMap.withoutEntry(changed, "BB");
        differences.clear(); ImmutableMapChanges.forEach(changed, removed, differences::add);
        assertEquals(List.of(new ImmutableMapChanges.Change<>("BB", 2, null)), differences);
        var mutable = new LinkedHashMap<>(before); var snapshot = ImmutableLookupMap.withEntry(mutable, "Aa", 5);
        mutable.put("BB", 99);
        differences.clear(); ImmutableMapChanges.forEach(mutable, snapshot, differences::add);
        assertEquals(List.of("Aa", "BB"), differences.stream().map(ImmutableMapChanges.Change::key).sorted().toList(),
                "a mutable source cannot certify that no other key changed after its defensive copy");
        mutable.remove("BB"); mutable.put("later", 9);
        assertFalse(ImmutableMapChanges.sameKeys(mutable, snapshot));
        assertEquals(Map.of("Aa", 1, "BB", 2), before);
    }
    @Test void membershipRemovalDistinguishesReplacedValuesAdditionsAndNonAdjacentRetirement() {
        var before = ImmutableLookupMap.copyOf(Map.of("Aa", 1, "BB", 2));
        var changed = ImmutableLookupMap.withEntry(before, "Aa", 3);
        var removed = new java.util.ArrayList<String>();
        ImmutableMapChanges.forEachRemovedKey(before, changed, removed::add);
        assertTrue(removed.isEmpty());
        var added = ImmutableLookupMap.withEntry(changed, "new", 4);
        ImmutableMapChanges.forEachRemovedKey(before, added, removed::add);
        assertTrue(removed.isEmpty());
        var retired = ImmutableLookupMap.withoutEntry(added, "BB");
        ImmutableMapChanges.forEachRemovedKey(before, retired, removed::add);
        assertEquals(List.of("BB"), removed);
        removed.clear();
        ImmutableMapChanges.forEachRemovedKey(new LinkedHashMap<>(before), Map.of("new", 9), removed::add);
        assertEquals(java.util.Set.of("Aa", "BB"), java.util.Set.copyOf(removed));
    }
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
        var replaced = ImmutableLookupMap.withEntry(snapshot, "BB", 7);
        assertEquals(List.of("BB", "Aa"), List.copyOf(replaced.keySet()));
        assertEquals(Map.of("BB", 7, "Aa", 1), replaced);
        assertEquals(2, snapshot.get("BB"));
        assertThrows(UnsupportedOperationException.class, () -> replaced.entrySet().iterator().next().setValue(8));
        assertThrows(NullPointerException.class, () -> ImmutableLookupMap.withEntry(snapshot, "x", null));
        assertSame(snapshot, ImmutableLookupMap.copyOf(snapshot));
        assertThrows(NullPointerException.class, () -> ImmutableLookupMap.copyOf(java.util.Collections.singletonMap("x", null)));
    }
}
