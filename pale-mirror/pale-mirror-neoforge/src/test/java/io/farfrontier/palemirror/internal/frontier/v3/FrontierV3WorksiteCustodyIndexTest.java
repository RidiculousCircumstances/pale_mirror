package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class FrontierV3WorksiteCustodyIndexTest {
    private record Declaration(SubjectId scope) { }
    private static PhysicalCustodyLease lease(SubjectId scope, PhysicalCustodyLeaseStatus status) {
        return new PhysicalCustodyLease(scope, new SubjectId("object:" + scope.value().replace(':', '-')), new SubjectId("provider:test"), 1, 0, 0, status, null);
    }
    @Test void onlyCurrentObligationsAreSelectedAndWithdrawalOrDeclarationReplacementInvalidatesThem() {
        var first = new Declaration(new SubjectId("scope:first")); var second = new Declaration(new SubjectId("scope:second"));
        var foreign = new SubjectId("scope:foreign"); var version = new Object(); var builds = new AtomicInteger();
        var index = new FrontierV3WorksiteCustodyIndex<Declaration>();
        java.util.function.Supplier<List<Declaration>> declarations = () -> { builds.incrementAndGet(); return List.of(first, second); };
        var leases = Map.of(second.scope(), lease(second.scope(), PhysicalCustodyLeaseStatus.PREPARING),
                foreign, lease(foreign, PhysicalCustodyLeaseStatus.ACQUIRED));
        var selected = index.update(version, declarations, Declaration::scope, leases);
        assertEquals(List.of(second), selected.live()); assertEquals(List.of(second), selected.preparing());
        var unrelated = new HashMap<>(leases); unrelated.put(foreign, lease(foreign, PhysicalCustodyLeaseStatus.RELEASED));
        assertSame(selected.live(), index.update(version, declarations, Declaration::scope, Map.copyOf(unrelated)).live());
        var acquired = Map.of(second.scope(), lease(second.scope(), PhysicalCustodyLeaseStatus.ACQUIRED));
        var active = index.update(version, declarations, Declaration::scope, acquired);
        assertEquals(List.of(second), active.live()); assertTrue(active.preparing().isEmpty());
        assertTrue(index.update(version, declarations, Declaration::scope, Map.of()).live().isEmpty());
        var replacement = new Declaration(second.scope());
        assertSame(replacement, index.update(new Object(), () -> List.of(replacement), Declaration::scope, acquired).live().getFirst());
        assertEquals(1, builds.get(), "unchanged declarations are not enumerated on a custody-only change");
        assertThrows(IllegalArgumentException.class, () -> index.update(new Object(), () -> List.of(first, first), Declaration::scope, acquired));
    }
}
