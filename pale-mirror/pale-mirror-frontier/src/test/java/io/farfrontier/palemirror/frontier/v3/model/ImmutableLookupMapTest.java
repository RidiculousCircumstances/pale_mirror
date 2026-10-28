package io.farfrontier.palemirror.frontier.v3.model;

import org.junit.jupiter.api.Test;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ImmutableLookupMapTest {
    private record CountedKey(int value, java.util.concurrent.atomic.AtomicInteger hashes) {
        @Override public int hashCode() { hashes.incrementAndGet(); return value; }
    }
    @Test void aValidatedLargeRegistrySharesUntouchedStorageAndKeepsPublishedBranchesExact() {
        var hashes = new java.util.concurrent.atomic.AtomicInteger();
        var source = new LinkedHashMap<CountedKey, Integer>();
        for (int i = 0; i < 2048; i++) source.put(new CountedKey(i, hashes), i);
        var initial = FrontierWorldStateSupport.immutableMap(ImmutableLookupMap.copyOf(source), "large ordered fixture");
        var target = source.keySet().iterator().next();
        hashes.set(0);
        var updated = ImmutableLookupMap.withEntry(initial, target, -1);
        assertTrue(hashes.get() < source.size() / 4,
                "one row replacement through a validation wrapper cannot rehash the complete registry");
        assertEquals(0, initial.get(target)); assertEquals(-1, updated.get(target));
        assertEquals(List.copyOf(source.keySet()), List.copyOf(updated.keySet()));
        source.clear();
        assertEquals(2048, initial.size());
        assertThrows(UnsupportedOperationException.class, () -> updated.keySet().remove(target));
        assertThrows(UnsupportedOperationException.class, () -> updated.values().clear());
        assertThrows(UnsupportedOperationException.class, () -> updated.entrySet().iterator().next().setValue(4));
        var deleted = ImmutableLookupMap.withoutEntry(updated, target);
        assertFalse(deleted.containsKey(target)); assertEquals(2047, deleted.size());
        var restored = ImmutableLookupMap.withEntry(deleted, target, 17);
        assertEquals(target, List.copyOf(restored.keySet()).getLast(), "reinsertion retains ordinary insertion order");
        assertEquals(0, initial.get(target)); assertEquals(-1, updated.get(target));
        assertEquals(java.util.Set.of(target), ((ImmutableMapImage<CountedKey, Integer>) updated)
                .changedKeysFrom(initial).orElseThrow());
    }
    @Test void largeRegistryEditsMatchOrderedMapsAcrossCollisionsMembershipAndBranches() {
        var expected = new LinkedHashMap<String, Integer>();
        for (int i = 0; i < 512; i++) expected.put("key:" + i, i);
        expected.put("Aa", 1); expected.put("BB", 2);
        Map<String, Integer> actual = ImmutableLookupMap.copyOf(expected);
        var branch = actual;
        for (int i = 0; i < 200; i++) {
            String key = i % 5 == 0 ? "new:" + i : "key:" + i;
            if (i % 7 == 0) { actual = ImmutableLookupMap.withoutEntry(actual, key); expected.remove(key); }
            else { actual = ImmutableLookupMap.withEntry(actual, key, -i); expected.put(key, -i); }
        }
        actual = ImmutableLookupMap.withEntry(actual, "Aa", 9); expected.put("Aa", 9);
        actual = ImmutableLookupMap.withoutEntry(actual, "BB"); expected.remove("BB");
        assertEquals(expected, actual); assertEquals(actual, expected);
        assertEquals(expected.hashCode(), actual.hashCode());
        assertEquals(List.copyOf(expected.keySet()), List.copyOf(actual.keySet()));
        assertEquals(List.copyOf(expected.values()), List.copyOf(actual.values()));
        assertEquals(1, branch.get("Aa")); assertEquals(2, branch.get("BB"));
        var mutable = new LinkedHashMap<>(expected);
        var defensive = ImmutableLookupMap.withEntry(mutable, "Aa", 11);
        mutable.clear();
        assertEquals(expected.size(), defensive.size()); assertEquals(11, defensive.get("Aa"));
        assertThrows(NullPointerException.class, () -> ImmutableLookupMap.withEntry(branch, "bad", null));
    }
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
