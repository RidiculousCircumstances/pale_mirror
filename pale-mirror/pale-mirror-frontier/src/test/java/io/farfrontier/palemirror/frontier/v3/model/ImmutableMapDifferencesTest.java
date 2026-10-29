package io.farfrontier.palemirror.frontier.v3.model;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ImmutableMapDifferencesTest {
    private record Value(int quantity) { }
    @Test void identityAndValueDifferencesRemainDistinctAcrossBranchesAndMutableIngressIsNeverMemoized() {
        var first = new Value(1); var before = ImmutableLookupMap.copyOf(Map.of("a", first));
        var equal = ImmutableLookupMap.withEntry(before, "a", new Value(1));
        var keys = new HashSet<String>();
        ImmutableMapChanges.forEachIdentityChangedKey(before, equal, keys::add); assertEquals(Set.of("a"), keys);
        var changes = new ArrayList<ImmutableMapChanges.Change<String, Value>>();
        ImmutableMapChanges.forEach(before, equal, changes::add); assertTrue(changes.isEmpty());
        assertTrue(ImmutableMapChanges.sameKeys(before, equal));
        var added = ImmutableLookupMap.withEntry(before, "b", new Value(2));
        assertFalse(ImmutableMapChanges.sameKeys(before, added));
        var removed = ImmutableLookupMap.withoutEntry(added, "a"); keys.clear();
        ImmutableMapChanges.forEachRemovedKey(added, removed, keys::add); assertEquals(Set.of("a"), keys);
        keys.clear(); ImmutableMapChanges.forEachIdentityChangedKey(added, before, keys::add); assertEquals(Set.of("b"), keys);
        var mutable = new HashMap<>(before); mutable.put("b", new Value(3));
        keys.clear(); ImmutableMapChanges.forEachIdentityChangedKey(before, mutable, keys::add); assertEquals(Set.of("b"), keys);
        mutable.remove("b"); mutable.put("a", new Value(4));
        keys.clear(); ImmutableMapChanges.forEachIdentityChangedKey(before, mutable, keys::add); assertEquals(Set.of("a"), keys);
    }
    @Test void unrelatedImagesKeepTheOriginalProducerTraversalOrder() {
        var source = new LinkedHashMap<String, Value>();
        for (var key : List.of("z", "a", "m")) source.put(key, new Value(1));
        var before = ImmutableLookupMap.copyOf(source);
        source.replaceAll((key, value) -> new Value(2));
        var after = ImmutableLookupMap.copyOf(source); var visited = new ArrayList<String>();
        ImmutableMapChanges.forEachIdentityChangedKey(before, after, visited::add);
        assertEquals(List.of("z", "a", "m"), visited);
        visited.clear(); ImmutableMapChanges.forEachIdentityChangedKey(before, after, visited::add);
        assertEquals(List.of("z", "a", "m"), visited, "reusing a difference cannot reorder consumers");
    }

}
