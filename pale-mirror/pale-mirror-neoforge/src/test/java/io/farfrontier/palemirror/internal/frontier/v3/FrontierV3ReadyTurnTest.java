package io.farfrontier.palemirror.internal.frontier.v3;

import java.util.ArrayDeque;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FrontierV3ReadyTurnTest {
    @Test void coalescesReadinessWithoutExecutingOnNotifierAndDiscardsClosedRuntime() {
        var owner = new ArrayDeque<Runnable>(); var resumed = new AtomicInteger();
        var turn = new FrontierV3ReadyTurn(owner::add, resumed::incrementAndGet);
        turn.signal(); turn.signal(); turn.signal();
        assertEquals(1, owner.size()); assertEquals(0, resumed.get());
        owner.remove().run(); assertEquals(1, resumed.get());
        turn.signal(); turn.close(); owner.remove().run(); turn.signal();
        assertEquals(1, resumed.get()); assertTrue(owner.isEmpty());
    }
}
