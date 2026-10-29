package io.farfrontier.palemirror.frontier.v3.model;

import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import static org.junit.jupiter.api.Assertions.*;

class ImmutableGroupViewTest {
    private record Row(Set<String> points, int progress) { }
    private record CountedKey(int value, AtomicInteger hashes) {
        @Override public int hashCode() { hashes.incrementAndGet(); return value; }
    }
    @Test void derivedProducerImagesDoNotRepeatWholeRegistryIngressInConsumers() {
        var producer = new ImmutableRegistryView<CountedKey, Row, Row>(1024);
        var consumer = new ImmutableGroupView<CountedKey, Row, String>();
        var epoch = new Object(); var hashes = new AtomicInteger(); var source = new LinkedHashMap<CountedKey, Row>();
        for (int i = 0; i < 512; i++) source.put(new CountedKey(i, hashes), new Row(Set.of("target"), 0));
        var input = ImmutableLookupMap.copyOf(source);
        var snapshot = producer.project(epoch, input, (id, row) -> row);
        var first = consumer.keys(epoch, snapshot, Row::points, "target");
        hashes.set(0);
        assertSame(first, consumer.keys(epoch, producer.project(epoch, input, (id, row) -> row), Row::points, "target"));
        assertTrue(hashes.get() < source.size() / 4, "an unchanged derived registry must not rehash the whole producer catalogue");
        var changedKey = source.keySet().iterator().next();
        var nextRow = new Row(Set.of("target"), 1);
        var changedInput = ImmutableLookupMap.withEntry(input, changedKey, nextRow);
        hashes.set(0);
        var changedSnapshot = producer.project(epoch, changedInput, (id, row) -> row);
        assertSame(first, consumer.keys(epoch, changedSnapshot, Row::points, "target"));
        assertSame(nextRow, changedSnapshot.get(changedKey));
        assertTrue(hashes.get() < source.size() / 4, "one projected row must not copy or rehash every registry entry");
        assertSame(snapshot.get(changedKey), input.get(changedKey), "the retained predecessor stays immutable");
    }

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
        var originalRows = view.rows(epoch, source, points, "target");
        assertEquals(List.of(input.get("a"), input.get("b")), originalRows);
        assertEquals(2, calls.get());
        assertFalse(view.uniqueMemberships(epoch, source, points), "two declared producers cannot claim a unique member");
        assertEquals(List.of("a"), view.keys(epoch, source, points, "source"));
        assertEquals(2, calls.get(), "querying another group cannot recompile every producer row");
        var changed = ImmutableLookupMap.withEntry(source, "b", new Row(Set.of("target"), 1));
        assertSame(original, view.keys(epoch, changed, points, "target"));
        var changedRows = view.rows(epoch, changed, points, "target");
        assertEquals(1, changedRows.getLast().progress(), "stable membership cannot retain stale stock/receipt values");
        assertEquals(0, originalRows.getLast().progress(), "old published row lists remain immutable");
        assertEquals(3, calls.get(), "a direct immutable replacement reprojects only its declared changed row");
        assertEquals(1, changed.get(original.getLast()).progress(), "candidate identity resolves from the current producer");
        var withForeign = ImmutableLookupMap.withEntry(changed, "foreign", new Row(Set.of("elsewhere"), 0));
        var localRows = view.rows(epoch, withForeign, points, "target");
        var foreign = ImmutableLookupMap.withEntry(withForeign, "foreign", new Row(Set.of("elsewhere"), 3));
        assertSame(localRows, view.rows(epoch, foreign, points, "target"), "foreign stock changes must not rebuild a local row list");
        var moved = ImmutableLookupMap.withEntry(foreign, "a", new Row(Set.of("other"), 2));
        assertEquals(List.of("b"), view.keys(epoch, moved, points, "target"));
        assertTrue(view.uniqueMemberships(epoch, moved, points), "moving the declared membership resolves the collision");
        assertEquals(List.of("a"), view.keys(epoch, moved, points, "other"));
        assertEquals(List.of(), view.keys(epoch, moved, points, "source"));
        var removed = ImmutableLookupMap.withoutEntry(moved, "b");
        assertEquals(List.of(), view.keys(epoch, removed, points, "target"));
        assertEquals(original, view.keys(epoch, source, points, "target"), "old immutable branches must restore complete membership");
        assertEquals(originalRows, view.rows(epoch, source, points, "target"), "old branches restore row values too");
        assertEquals(List.of(), view.rows(epoch, removed, points, "target"), "removal cannot retain an inbound obligation");
        assertFalse(view.uniqueMemberships(epoch, source, points), "returning to an old branch must restore its collision too");
        var reversed = new LinkedHashMap<String, Row>(); reversed.put("b", input.get("b")); reversed.put("a", input.get("a"));
        var reverseImage = ImmutableLookupMap.copyOf(reversed);
        assertFalse(view.uniqueMemberships(epoch, reverseImage, points));
        assertEquals(List.of("b", "a"), view.keys(epoch, reverseImage, points, "target"),
                "a membership-only visit cannot retain the preceding producer order");
        assertEquals(List.of("a", "b"), original, "published candidate lists never follow later updates");
        assertThrows(UnsupportedOperationException.class, () -> original.add("c"));
    }
    @Test void orderIndependentRowsTrackQuantityTitleAndRemovalWithoutRankingForeignKeys() {
        var view = new ImmutableGroupView<String, Row, String>(); var epoch = new Object();
        var first = new Row(Set.of("local"), 2); var second = new Row(Set.of("local"), 3);
        var source = ImmutableLookupMap.copyOf(Map.of("a", first, "b", second));
        var original = view.unorderedRows(epoch, source, Row::points, "local");
        assertEquals(Set.of(first, second), Set.copyOf(original));
        var foreign = ImmutableLookupMap.withEntry(source, "c", new Row(Set.of("foreign"), 5));
        assertSame(original, view.unorderedRows(epoch, foreign, Row::points, "local"));
        var replacement = new Row(Set.of("local"), 7);
        var changed = ImmutableLookupMap.withEntry(foreign, "a", replacement);
        assertEquals(Set.of(replacement, second), Set.copyOf(view.unorderedRows(epoch, changed, Row::points, "local")));
        assertEquals(Set.of(first, second), Set.copyOf(original));
        var moved = ImmutableLookupMap.withEntry(changed, "a", new Row(Set.of("foreign"), 7));
        assertEquals(List.of(second), view.unorderedRows(epoch, moved, Row::points, "local"));
        var removed = ImmutableLookupMap.withoutEntry(moved, "b");
        assertTrue(view.unorderedRows(epoch, removed, Row::points, "local").isEmpty());
        assertEquals(Set.of(first, second), Set.copyOf(view.unorderedRows(epoch, source, Row::points, "local")));
        var reversed = new LinkedHashMap<String, Row>(); reversed.put("b", second); reversed.put("a", first);
        var reverseImage = ImmutableLookupMap.copyOf(reversed);
        view.unorderedRows(epoch, reverseImage, Row::points, "local");
        assertEquals(List.of("b", "a"), view.keys(epoch, reverseImage, Row::points, "local"),
                "a membership-only visit cannot supply a stale order to an ordered consumer");
        assertTrue(view.unorderedRows(new Object(), source, row -> Set.of("rebound"), "local").isEmpty());
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
