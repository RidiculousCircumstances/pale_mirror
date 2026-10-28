package io.farfrontier.palemirror.frontier.v3.model;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ImmutableSetContributionsTest {
    @Test void sharedValuesSurviveOneOwnerRetirementAndSnapshotsRemainIndependent() {
        var projection = new ImmutableSetContributions<String, String>(2);
        var draft = new HashSet<>(Set.of("shared", "left"));
        var first = projection.replaceAll(Map.of("a", draft, "b", Set.of("shared", "right")));
        draft.clear();
        assertEquals(Set.of("shared", "left", "right"), first);
        assertThrows(UnsupportedOperationException.class, () -> first.clear());
        assertSame(first, projection.replaceAll(Map.of("a", Set.of("shared", "left"))));
        var retired = projection.replaceAll(Map.of("a", Set.of()));
        assertEquals(Set.of("shared", "right"), retired);
        assertEquals(Set.of("shared", "left", "right"), first);
        assertTrue(projection.replaceAll(Map.of("b", Set.of())).isEmpty());
    }

    @Test void atomicReplacementDoesNotLoseAnUnchangedUnionOrAdmitAnUnboundedSource() {
        var projection = new ImmutableSetContributions<String, String>(1);
        var accepted = projection.replaceAll(Map.of("a", Set.of("shared")));
        assertThrows(IllegalArgumentException.class, () -> projection.replaceAll(Map.of("b", Set.of("other"))));
        assertSame(accepted, projection.snapshot(), "a rejected source must not mutate the accepted union");
        assertSame(accepted, projection.replaceAll(Map.of("a", Set.of(), "b", Set.of("shared"))));
        assertEquals(Set.of("shared"), projection.snapshot());
    }
}
