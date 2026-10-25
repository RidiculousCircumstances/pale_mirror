package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class FrontierV3ClearanceProbeTest {
    @Test void acceptedButLaterStalledDestinationIsNotSelectedAgainInTheSamePass() {
        var actor = new SubjectId("resident:clearance");
        var execution = new ActorExecutionId(actor, ActorActivityKind.PRESENCE, actor, 1);
        var search = new FrontierV3ClearanceProbe(new ActorActuationId(new ActorBodyId(actor, 1), execution),
                SurfaceAnchor.at(0, 63, 0), new Object());
        var blocked = SurfaceAnchor.at(1, 63, 0); var next = SurfaceAnchor.at(2, 63, 0);
        assertTrue(search.test(blocked, () -> true));
        search.reject(blocked); search.nextTurn();
        assertFalse(search.test(blocked, () -> { fail("stalled destination cannot win again before the next pass"); return true; }));
        assertTrue(search.test(next, () -> true));
    }
    @Test void ninthReachableDestinationSurvivesBudgetYieldAndOldGenerationCannotReuseThePass() {
        var actor = new SubjectId("resident:test");
        var execution = new ActorExecutionId(actor, ActorActivityKind.PRESENCE, actor, 1);
        var authority = new ActorActuationId(new ActorBodyId(actor, 1), execution);
        var origin = SurfaceAnchor.at(0, 63, 0); var geometry = new Object();
        var search = new FrontierV3ClearanceProbe(authority, origin, geometry);
        var calls = new AtomicInteger();
        for (int i = 1; i <= 8; i++) assertFalse(search.test(SurfaceAnchor.at(i, 63, 0), () -> { calls.incrementAndGet(); return false; }));
        var ninth = SurfaceAnchor.at(9, 63, 0);
        assertThrows(FrontierV3ClearanceProbe.Pending.class, () -> search.test(ninth, () -> { calls.incrementAndGet(); return true; }));
        assertEquals(8, calls.get()); assertEquals(8, search.rejectedCount());
        search.nextTurn();
        for (int i = 1; i <= 8; i++) assertFalse(search.test(SurfaceAnchor.at(i, 63, 0), () -> { fail("must resume beyond tested candidates"); return true; }));
        assertTrue(search.test(ninth, () -> { calls.incrementAndGet(); return true; }));
        assertEquals(9, calls.get()); assertEquals(1, search.probes());
        assertTrue(search.matches(authority, origin, geometry));
        assertFalse(search.matches(authority, ninth, geometry));
        assertFalse(search.matches(authority, origin, new Object()));
        assertFalse(search.matches(new ActorActuationId(new ActorBodyId(actor, 2), execution), origin, geometry));
        assertFalse(search.matches(new ActorActuationId(authority.body(), new ActorExecutionId(actor, ActorActivityKind.PRESENCE, actor, 2)), origin, geometry));
    }
}
