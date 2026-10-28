package io.farfrontier.palemirror.frontier.v3.model;

import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ImmutableRegistrySnapshotsTest {
    @Test void identicalProjectedValuesStillAdvanceTheExactInputBasis() {
        var view = new ImmutableRegistryView<String, String, Boolean>(4);
        var epoch = new Object(); var a = ImmutableLookupMap.copyOf(Map.of("row", "one"));
        var first = view.project(epoch, a, (id, input) -> Boolean.TRUE);
        var b = ImmutableLookupMap.withEntry(a, "row", "two");
        assertSame(first, view.project(epoch, b, (id, input) -> Boolean.TRUE));
        var c = ImmutableLookupMap.withEntry(b, "row", "three");
        assertSame(first, view.project(epoch, c, (before, after) -> {
            assertEquals("two", before); assertEquals("three", after); return false;
        }, (id, input) -> Boolean.TRUE));
    }

    @Test void ordinaryMutableMapsCannotAcquireImmutableSnapshotEvidence() {
        var view = new ImmutableRegistryView<String, String, String>(4, 3);
        var epoch = new Object(); var mutable = new java.util.HashMap<>(Map.of("row", "before"));
        var before = view.project(epoch, mutable, (id, value) -> value);
        mutable.put("row", "after");
        assertEquals("after", view.project(epoch, mutable, (id, value) -> value).get("row"));
        assertEquals("before", before.get("row"));
        var immutable = ImmutableLookupMap.copyOf(Map.of("row", "accepted"));
        view.project(epoch, immutable, (prior, next) -> false, (id, value) -> value);
        var equivalent = ImmutableLookupMap.withEntry(immutable, "row", "equivalent");
        view.project(epoch, equivalent, (prior, next) -> true, (id, value) -> value);
        view.project(epoch, ImmutableLookupMap.withEntry(equivalent, "row", "changed"), (prior, next) -> {
            assertEquals("accepted", prior); return false;
        }, (id, value) -> value);
        var constant = new ImmutableRegistryView<String, String, Boolean>(4, 3);
        var image = constant.project(epoch, mutable, (id, value) -> Boolean.TRUE);
        mutable.put("row", "latest");
        assertSame(image, constant.project(epoch, mutable, (id, value) -> Boolean.TRUE));
        mutable.put("row", "strict");
        constant.project(epoch, mutable, (prior, next) -> {
            assertEquals("latest", prior); assertEquals("strict", next); return false;
        }, (id, value) -> Boolean.TRUE);
    }

    @Test void restoredSnapshotRetainsItsRowBasisAndStrictChecksBypassRetainedImages() {
        var view = new ImmutableRegistryView<String, String, String>(4, 3);
        var epoch = new Object();
        var first = ImmutableLookupMap.copyOf(Map.of("local", "one", "remote", "stable"));
        var second = ImmutableLookupMap.withEntry(first, "local", "two");
        var a = view.project(epoch, first, (id, value) -> value);
        var b = view.project(epoch, second, (id, value) -> value);
        assertSame(a, view.project(epoch, first, (id, value) -> { throw new AssertionError("already computed immutable snapshot"); }));
        var third = ImmutableLookupMap.withEntry(first, "local", "three");
        var c = view.project(epoch, third, (id, value) -> {
            if (id.equals("remote")) throw new AssertionError("restored predecessor lost its unchanged row");
            return value;
        });
        assertEquals(Map.of("local", "three", "remote", "stable"), c);
        assertThrows(IllegalArgumentException.class, () -> view.project(epoch, second, (before, after) -> false,
                (id, value) -> { throw new IllegalArgumentException("strict audit rejects this candidate"); }));
        assertEquals(b, view.project(epoch, second, (id, value) -> value));
        var replacementEpoch = new Object();
        assertEquals("new:two", view.project(replacementEpoch, second, (id, value) -> "new:" + value).get("local"));
        assertEquals("one", a.get("local"));
    }
}
