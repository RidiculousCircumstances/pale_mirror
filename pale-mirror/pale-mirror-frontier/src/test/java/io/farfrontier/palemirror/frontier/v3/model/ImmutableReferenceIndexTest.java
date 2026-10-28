package io.farfrontier.palemirror.frontier.v3.model;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ImmutableReferenceIndexTest {
    private record Facts(Map<Integer, String> values, Map<Integer, Integer> links) { }
    private record Declaration(int source) { }
    private static final ImmutableReferenceIndex.Source<Facts, Integer, String> VALUES = new ImmutableReferenceIndex.Source<>(Facts::values);
    private static final ImmutableReferenceIndex.Source<Facts, Integer, Integer> LINKS = new ImmutableReferenceIndex.Source<>(Facts::links);

    @Test void exactReadsInvalidateOnRemovalReplacementFailureAndRecoveryWithoutTrustingOtherRows() {
        var epoch = new Object(); var calls = new AtomicInteger();
        var index = new ImmutableReferenceIndex<Facts, String, Declaration>(4, 4, List.of(VALUES),
                (f, d) -> List.of(VALUES.at(d.source()), VALUES.at(d.source())), (f, id, d) -> {
                    if (!f.values().containsKey(d.source())) throw new IllegalArgumentException("missing declared fact");
                    calls.incrementAndGet();
                });
        var rows = ImmutableLookupMap.copyOf(Map.of("one", new Declaration(1), "two", new Declaration(2)));
        var facts = new Facts(ImmutableLookupMap.copyOf(Map.of(1, "a", 2, "b")), Map.of());
        index.validate(epoch, rows, facts); assertEquals(2, calls.get());
        var unrelated = new Facts(ImmutableLookupMap.withEntry(facts.values(), 3, "other"), Map.of());
        index.validate(epoch, rows, unrelated); assertEquals(2, calls.get());
        var replaced = new Facts(ImmutableLookupMap.withEntry(unrelated.values(), 1, new String("a")), Map.of());
        index.validate(epoch, rows, replaced); assertEquals(3, calls.get(), "equal values are still distinct input evidence");
        var removed = new Facts(ImmutableLookupMap.withoutEntry(replaced.values(), 1), Map.of());
        assertThrows(IllegalArgumentException.class, () -> index.validate(epoch, rows, removed));
        index.validate(epoch, rows, replaced); assertEquals(5, calls.get(), "failed image is discarded completely");
        ImmutableInvariantCheck.full(() -> { index.validate(epoch, rows, replaced); return null; });
        assertEquals(7, calls.get(), "full recovery cannot trust warmed input evidence");
        index.validate(new Object(), rows, replaced); assertEquals(9, calls.get(), "epochs never share checks");
    }

    @Test void exactProjectionEqualitySkipsEquivalentImagesButStillChecksChangedValuesAndFullAudit() {
        var values = new ImmutableReferenceIndex.Source<Facts, Integer, String>(Facts::values, Objects::equals);
        var calls = new AtomicInteger(); var epoch = new Object(); var rows = Map.of("row", new Declaration(1));
        var index = new ImmutableReferenceIndex<Facts, String, Declaration>(2, 2, List.of(values),
                (facts, row) -> List.of(values.at(row.source())), (facts, key, row) -> {
                    if (!"accepted".equals(facts.values().get(row.source()))) throw new IllegalArgumentException("changed declared projection");
                    calls.incrementAndGet();
                });
        var first = new Facts(Map.of(1, new String("accepted")), Map.of());
        var equivalent = new Facts(Map.of(1, new String("accepted")), Map.of());
        index.validate(epoch, rows, first); index.validate(epoch, rows, equivalent); assertEquals(1, calls.get());
        assertEquals("accepted", values.at(1).value(equivalent));
        assertThrows(IllegalArgumentException.class, () -> index.validate(epoch, rows, new Facts(Map.of(1, "foreign"), Map.of())));
        index.validate(epoch, rows, equivalent); assertEquals(2, calls.get());
        ImmutableInvariantCheck.full(() -> { index.validate(epoch, rows, equivalent); return null; });
        assertEquals(3, calls.get());
    }

    @Test void dynamicReferencesRebindBeforeValidationAndWithdrawnRowsLeaveNoStaleReader() {
        var epoch = new Object(); var rows = Map.of("one", new Declaration(10)); var calls = new AtomicInteger();
        var index = new ImmutableReferenceIndex<Facts, String, Declaration>(2, 4, List.of(VALUES, LINKS),
                (f, d) -> List.of(LINKS.at(d.source()), VALUES.at(f.links().get(d.source()))), (f, id, d) -> {
                    if (!f.values().containsKey(f.links().get(d.source()))) throw new IllegalArgumentException("lost target");
                    calls.incrementAndGet();
                });
        var first = new Facts(ImmutableLookupMap.copyOf(Map.of(1, "a", 2, "b")), ImmutableLookupMap.copyOf(Map.of(10, 1)));
        index.validate(epoch, rows, first);
        var redirected = new Facts(first.values(), ImmutableLookupMap.withEntry(first.links(), 10, 2));
        index.validate(epoch, rows, redirected); assertEquals(2, calls.get());
        var oldTargetRemoved = new Facts(ImmutableLookupMap.withoutEntry(first.values(), 1), redirected.links());
        index.validate(epoch, rows, oldTargetRemoved);
        assertEquals(2, calls.get(), "old target is no longer a read dependency");
        var missing = new Facts(Map.of(), redirected.links());
        assertThrows(IllegalArgumentException.class, () -> index.validate(epoch, rows, missing));
        index.validate(epoch, Map.of(), first);
        index.validate(epoch, rows, first); assertEquals(3, calls.get(), "reused row identity receives a fresh check");
        var foreign = new ImmutableReferenceIndex.Source<Facts, Integer, String>(Facts::values);
        var unregistered = new ImmutableReferenceIndex<Facts, String, Declaration>(2, 4, List.of(VALUES),
                (f, d) -> List.of(foreign.at(d.source())), (f, id, d) -> fail("unknown source reached validation"));
        assertThrows(IllegalArgumentException.class, () -> unregistered.validate(epoch, Map.of("other", new Declaration(1)), first));
    }
}
