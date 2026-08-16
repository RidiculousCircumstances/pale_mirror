package io.farfrontier.palemirror.visuals.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.farfrontier.palemirror.api.VisualStateProjection;
import org.junit.jupiter.api.Test;

class VisualProjectionLedgerTest {
    @Test
    void desiredProjectionSurvivesUntilAChunkCanReconcileIt() {
        VisualProjectionLedger ledger = new VisualProjectionLedger();
        VisualStateProjection infected = projection(7L, "INFECTED", "FOOTHOLD");

        ledger.accept("minecraft:overworld", infected);

        assertEquals(java.util.List.of(infected), ledger.projections("minecraft:overworld"),
                "delivery must retain desired state without depending on current chunk availability");
        assertEquals(java.util.List.of(), ledger.projections("minecraft:the_nether"));
    }

    @Test
    void oneObjectKeepsOnlyItsLatestCanonicalRevision() {
        VisualProjectionLedger ledger = new VisualProjectionLedger();
        VisualStateProjection infected = projection(7L, "INFECTED", "FOOTHOLD");
        VisualStateProjection siege = projection(8L, "INFECTED", "SIEGE");

        ledger.accept("minecraft:overworld", infected);
        ledger.accept("minecraft:overworld", siege);
        ledger.accept("minecraft:overworld", siege);

        assertEquals(java.util.List.of(siege), ledger.projections("minecraft:overworld"),
                "a new stage must replace rather than duplicate the previous physical intent");
        assertThrows(IllegalStateException.class, () -> ledger.accept("minecraft:overworld",
                new VisualStateProjection(siege.objectId(), siege.desiredRevision(), siege.projectionRevision(),
                        "RECOVERING", siege.integrity(), siege.economy(), siege.crisis(), siege.development(),
                        siege.threatStage(), siege.alternateDispatch())));
    }

    private static VisualStateProjection projection(long revision, String operation, String stage) {
        return new VisualStateProjection("pale_mirror:test_mine", revision, revision, operation,
                "INTACT", "AVAILABLE", "STABLE", "0", stage, "UNKNOWN");
    }
}
