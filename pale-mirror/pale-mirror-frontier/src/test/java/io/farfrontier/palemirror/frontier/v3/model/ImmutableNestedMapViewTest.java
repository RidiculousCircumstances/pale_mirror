package io.farfrontier.palemirror.frontier.v3.model;

import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import static org.junit.jupiter.api.Assertions.*;

class ImmutableNestedMapViewTest {
    private record Row(Map<String, Object> children) { }
    @Test void changedProducerUpdatesOnlyItsChildrenAndRetainsExactOldBranches() {
        var view = new ImmutableNestedMapView<Integer, Row, String, Object>(1024, 2048);
        var epoch = new Object(); var projections = new AtomicInteger();
        Function<Row, Map<String, Object>> project = row -> { projections.incrementAndGet(); return row.children(); };
        var source = new LinkedHashMap<Integer, Row>();
        for (int i = 0; i < 512; i++) source.put(i, new Row(Map.of("child:" + i, new Object())));
        Map<Integer, Row> initial = ImmutableLookupMap.copyOf(source);
        var first = view.project(epoch, initial, project);
        assertEquals(512, first.size()); assertEquals(512, projections.get());
        var nextValue = new Object();
        var changed = ImmutableLookupMap.withEntry(initial, 1, new Row(Map.of("child:1", nextValue)));
        var next = view.project(epoch, changed, project);
        assertEquals(513, projections.get(), "one declared producer changed");
        assertSame(nextValue, next.get("child:1")); assertNotSame(nextValue, first.get("child:1"));
        assertSame(first.get("child:2"), next.get("child:2"));
        assertThrows(UnsupportedOperationException.class, () -> next.remove("child:1"));
        var restored = view.project(epoch, initial, project);
        assertEquals(first, restored); assertSame(first.get("child:1"), restored.get("child:1"));
        var removed = view.project(epoch, ImmutableLookupMap.withoutEntry(initial, 1), project);
        assertFalse(removed.containsKey("child:1")); assertEquals(511, removed.size());
    }
    @Test void atomicTransferDuplicateMutableIngressAndFailureStayExact() {
        var view = new ImmutableNestedMapView<String, Row, String, Object>(3, 3); var epoch = new Object(); var value = new Object();
        Map<String, Row> first = ImmutableLookupMap.copyOf(Map.of("a", new Row(Map.of("x", value)), "b", new Row(Map.of())));
        var original = view.project(epoch, first, Row::children);
        var transferred = ImmutableLookupMap.withEntry(ImmutableLookupMap.withEntry(first, "a", new Row(Map.of())), "b", new Row(Map.of("x", value)));
        assertEquals(original, view.project(epoch, transferred, Row::children));
        var duplicate = ImmutableLookupMap.withEntry(first, "b", new Row(Map.of("x", new Object())));
        assertThrows(IllegalArgumentException.class, () -> view.project(epoch, duplicate, Row::children));
        assertEquals(original, view.project(epoch, first, Row::children), "failed partial index must be discarded");
        var mutable = new LinkedHashMap<>(first); mutable.put("a", new Row(Map.of("y", value)));
        assertFalse(view.project(epoch, mutable, Row::children).containsKey("x"));
        mutable.remove("a"); assertTrue(view.project(epoch, mutable, Row::children).isEmpty());
        assertEquals(original, view.project(new Object(), first, Row::children));
    }
}
