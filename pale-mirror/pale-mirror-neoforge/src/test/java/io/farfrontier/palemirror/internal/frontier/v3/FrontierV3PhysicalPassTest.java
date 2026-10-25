package io.farfrontier.palemirror.internal.frontier.v3;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class FrontierV3PhysicalPassTest {
    @Test void durabilityWakeFinishesTheSuffixWithoutReplayingActorsOrChargingIdleTime() {
        var clock = new AtomicLong();
        var pass = new FrontierV3PhysicalPass(clock::get);
        var ready = new AtomicBoolean(true);
        var seen = new ArrayList<Integer>();
        pass.begin(10);
        pass.run(3, 10, ready::get, stage -> {
            seen.add(stage); clock.addAndGet(2); ready.set(false);
        });
        assertEquals(List.of(0), seen);
        clock.addAndGet(1000); // No physical work while the real journal is pending.
        ready.set(true);
        pass.run(3, 10, ready::get, stage -> { seen.add(stage); clock.incrementAndGet(); });
        pass.run(3, 10, ready::get, stage -> fail("a completed round cannot actuate twice"));
        assertEquals(List.of(0, 1, 2), seen);
        assertEquals(4, pass.elapsed());
        assertFalse(pass.begin(10), "a wake does not open another native tick");
        assertTrue(pass.begin(11));
        pass.run(3, 10, ready::get, seen::add);
        assertEquals(List.of(0, 1, 2, 0, 1, 2), seen);
    }
    @Test void successiveWakesShareOneAllowanceAndCarryUnfinishedStagesIntoTheNextTick() {
        var clock = new AtomicLong();
        var pass = new FrontierV3PhysicalPass(clock::get);
        var ready = new AtomicBoolean(true); var seen = new ArrayList<Integer>();
        pass.begin(20);
        pass.run(3, 5, ready::get, stage -> { seen.add(stage); clock.addAndGet(3); ready.set(false); });
        ready.set(true);
        pass.run(3, 5, ready::get, stage -> { seen.add(stage); clock.addAndGet(3); });
        pass.run(3, 5, ready::get, stage -> fail("another receipt cannot renew the spent allowance"));
        assertEquals(List.of(0, 1), seen);
        pass.begin(21);
        pass.run(3, 5, ready::get, seen::add);
        assertEquals(List.of(0, 1, 2), seen);
    }
}
