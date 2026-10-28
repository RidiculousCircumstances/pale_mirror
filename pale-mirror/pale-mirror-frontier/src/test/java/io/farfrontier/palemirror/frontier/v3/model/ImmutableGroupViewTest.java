package io.farfrontier.palemirror.frontier.v3.model;

import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import static org.junit.jupiter.api.Assertions.*;

class ImmutableGroupViewTest {
    private record Row(Set<String> points, int progress) { }
    @Test void changedProgressKeepsMembershipButCurrentRowsMovesRemovalsBranchesAndOrderRemainExact() {
        var view = new ImmutableGroupView<String, Row, String>();
        var epoch = new Object(); var calls = new AtomicInteger();
        Function<Row, Set<String>> points = row -> { calls.incrementAndGet(); return row.points(); };
        var input = new LinkedHashMap<String, Row>();
        input.put("a", new Row(Set.of("source", "target"), 0));
        input.put("b", new Row(Set.of("target"), 0));
        Map<String, Row> source = ImmutableLookupMap.copyOf(input);
        var original = view.keys(epoch, source, points, "target");
        assertEquals(List.of("a", "b"), original);
        assertEquals(2, calls.get());
        assertFalse(view.uniqueMemberships(epoch, source, points), "two declared producers cannot claim a unique member");
        assertEquals(List.of("a"), view.keys(epoch, source, points, "source"));
        assertEquals(2, calls.get(), "querying another group cannot recompile every producer row");
        var changed = ImmutableLookupMap.withEntry(source, "b", new Row(Set.of("target"), 1));
        assertSame(original, view.keys(epoch, changed, points, "target"));
        assertEquals(3, calls.get(), "a direct immutable replacement reprojects only its declared changed row");
        assertEquals(1, changed.get(original.getLast()).progress(), "candidate identity resolves from the current producer");
        var moved = ImmutableLookupMap.withEntry(changed, "a", new Row(Set.of("other"), 2));
        assertEquals(List.of("b"), view.keys(epoch, moved, points, "target"));
        assertTrue(view.uniqueMemberships(epoch, moved, points), "moving the declared membership resolves the collision");
        assertEquals(List.of("a"), view.keys(epoch, moved, points, "other"));
        assertEquals(List.of(), view.keys(epoch, moved, points, "source"));
        var removed = ImmutableLookupMap.withoutEntry(moved, "b");
        assertEquals(List.of(), view.keys(epoch, removed, points, "target"));
        assertEquals(original, view.keys(epoch, source, points, "target"), "old immutable branches must restore complete membership");
        assertFalse(view.uniqueMemberships(epoch, source, points), "returning to an old branch must restore its collision too");
        var reversed = new LinkedHashMap<String, Row>(); reversed.put("b", input.get("b")); reversed.put("a", input.get("a"));
        assertEquals(List.of("b", "a"), view.keys(epoch, ImmutableLookupMap.copyOf(reversed), points, "target"));
        assertEquals(List.of("a", "b"), original, "published candidate lists never follow later updates");
        assertThrows(UnsupportedOperationException.class, () -> original.add("c"));
    }
    @Test void externalBindingEpochMutableIngressAndFailedProjectionCannotReuseStaleMembership() {
        var view = new ImmutableGroupView<String, Row, String>();
        var epoch = new Object();
        var source = new HashMap<String, Row>(); source.put("a", new Row(Set.of("old"), 0));
        assertEquals(List.of("a"), view.keys(epoch, source, Row::points, "old"));
        source.put("a", new Row(Set.of("new"), 1));
        assertEquals(List.of(), view.keys(epoch, source, Row::points, "old"));
        assertEquals(List.of("a"), view.keys(epoch, source, Row::points, "new"));
        Map<String, Row> immutable = ImmutableLookupMap.copyOf(source);
        assertEquals(List.of("a"), view.keys(epoch, immutable, Row::points, "new"));
        var nextEpoch = new Object();
        assertEquals(List.of("a"), view.keys(nextEpoch, immutable, row -> Set.of("rebound"), "rebound"));
        assertEquals(List.of(), view.keys(nextEpoch, immutable, row -> Set.of("rebound"), "new"));
        var failed = ImmutableLookupMap.withEntry(immutable, "broken", new Row(Set.of("bad"), 0));
        assertThrows(IllegalArgumentException.class, () -> view.keys(nextEpoch, failed, row -> {
            if (row.points().contains("bad")) throw new IllegalArgumentException("invalid declaration");
            return row.points();
        }, "bad"));
        assertEquals(List.of("a"), view.keys(epoch, immutable, Row::points, "new"), "failed partial updates cannot become read evidence");
    }
}
