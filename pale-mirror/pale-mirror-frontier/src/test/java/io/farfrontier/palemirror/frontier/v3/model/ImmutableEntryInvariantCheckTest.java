package io.farfrontier.palemirror.frontier.v3.model;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ImmutableEntryInvariantCheckTest {
    private record Row(String id, int revision) { }
    @Test void changedPairsBranchesFailedIngressAndFullAuditRetainExactIdentityChecks() {
        var checks = new ImmutableEntryInvariantCheck<String, Row>(4); var epoch = new Object();
        var visits = new AtomicInteger();
        BiConsumer<String, Row> check = (key, row) -> {
            visits.incrementAndGet();
            if (!key.equals(row.id())) throw new IllegalArgumentException("foreign row identity");
        };
        var source = ImmutableLookupMap.copyOf(Map.of("a", new Row("a", 0), "b", new Row("b", 0)));
        checks.validate(epoch, source, check); assertEquals(2, visits.get());
        var changed = ImmutableLookupMap.withEntry(source, "b", new Row("b", 1));
        visits.set(0); checks.validate(epoch, changed, check); assertEquals(1, visits.get());
        visits.set(0); checks.validate(epoch, source, check); assertEquals(1, visits.get(), "restoring an older branch checks its actual changed row");
        visits.set(0); ImmutableInvariantCheck.full(() -> { checks.validate(epoch, source, check); return null; });
        assertEquals(2, visits.get(), "complete ingress audits cannot inherit delta evidence");
        var forged = ImmutableLookupMap.withEntry(changed, "a", new Row("other", 2));
        assertThrows(IllegalArgumentException.class, () -> checks.validate(epoch, forged, check));
        assertThrows(IllegalArgumentException.class, () -> checks.validate(epoch, forged, check), "failed candidates never become trusted predecessors");
        assertDoesNotThrow(() -> checks.validate(epoch, source, check));
        var removed = ImmutableLookupMap.withoutEntry(changed, "b");
        assertDoesNotThrow(() -> checks.validate(epoch, removed, check));
        assertThrows(IllegalArgumentException.class, () -> checks.validate(epoch,
                ImmutableLookupMap.withEntry(removed, "b", new Row("wrong", 3)), check));
        var mutable = new HashMap<>(source); checks.validate(epoch, mutable, check);
        mutable.put("a", new Row("forged", 1));
        assertThrows(IllegalArgumentException.class, () -> checks.validate(epoch, mutable, check), "mutable ingress cannot validate itself by object identity");
        visits.set(0); checks.validate(new Object(), source, check); assertEquals(2, visits.get());
    }
}
