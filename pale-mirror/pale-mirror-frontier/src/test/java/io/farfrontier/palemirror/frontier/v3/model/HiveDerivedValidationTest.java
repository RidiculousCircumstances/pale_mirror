package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HiveDerivedValidationTest {
    @Test void immutableOccupancyReuseStillRejectsChangedOverlappingDeclarations() {
        var bootstrap = FrontierBootstrapper.create(new WorldId("frontier:organ-reuse"), 41L);
        var organs = bootstrap.hive().organs();
        var first = FrontierGrayboxPlan.intactOrganOccupancy(organs);
        assertSame(first, FrontierGrayboxPlan.intactOrganOccupancy(new ArrayList<>(organs)));
        assertThrows(UnsupportedOperationException.class, () -> first.clear());
        var original = organs.getFirst();
        var overlapping = new HiveOrgan(new SubjectId("organ:overlap"), original.hiveId(), original.nestId(),
                original.kind(), original.anchor(), original.containerId());
        var changed = new ArrayList<>(organs);
        changed.add(overlapping);
        assertThrows(IllegalArgumentException.class, () -> FrontierGrayboxPlan.intactOrganOccupancy(changed));
        assertSame(first, FrontierGrayboxPlan.intactOrganOccupancy(organs), "a failed derivation must not replace a valid view");
    }

    @Test void successfulColonyProofCannotCoverChangedLifecycleOrBootstrap() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:colony-proof"), 41L));
        var colony = state.hiveColony();
        colony.validateAgainst(state.bootstrap());
        colony.validateAgainst(state.bootstrap());
        assertThrows(IllegalArgumentException.class,
                () -> colony.withBioformLifecycles(Map.of()).validateAgainst(state.bootstrap()));
        var organs = state.bootstrap().hive().organs();
        var original = organs.getFirst();
        var overlapping = new HiveOrgan(new SubjectId("organ:colony-overlap"), original.hiveId(), original.nestId(),
                original.kind(), original.anchor(), original.containerId());
        assertThrows(IllegalArgumentException.class,
                () -> colony.addOrgan(overlapping).validateAgainst(state.bootstrap()));
        var other = FrontierBootstrapper.create(new WorldId("frontier:other-colony"), 42L);
        // A different bootstrap may reuse subject names, but cocoon geometry/ownership
        // must still be examined rather than using the proof for the previous object.
        var newState = FrontierWorldState.initial(other);
        assertDoesNotThrow(() -> newState.hiveColony().validateAgainst(other));
    }
}
