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
        pass.run(3, ready::get, stage -> {
            seen.add(stage); clock.addAndGet(2); ready.set(false);
        });
        assertEquals(List.of(0), seen);
        clock.addAndGet(1000); // No physical work while the real journal is pending.
        ready.set(true);
        pass.run(3, ready::get, stage -> { seen.add(stage); clock.incrementAndGet(); });
        pass.run(3, ready::get, stage -> fail("a completed round cannot actuate twice"));
        assertEquals(List.of(0, 1, 2), seen);
        assertEquals(4, pass.elapsed());
        assertFalse(pass.begin(10), "a wake does not open another native tick");
        assertTrue(pass.begin(11));
        pass.run(3, ready::get, seen::add);
        assertEquals(List.of(0, 1, 2, 0, 1, 2), seen);
    }
    @Test void spentPreparationBudgetCannotSkipAnAdmittedEffectWindow() {
        var clock = new AtomicLong(); var pass = new FrontierV3PhysicalPass(clock::get);
        var ready = new AtomicBoolean(true); var seen = new ArrayList<Integer>();
        var preparation = new FrontierV3PreparationAdmission(List.of("terrain", "worksite"));
        pass.begin(20); var admission = preparation.begin(5, pass::elapsed);
        pass.run(4, ready::get, stage -> {
            seen.add(stage);
            if(stage==0) { assertTrue(admission.admit("terrain")); clock.addAndGet(6); ready.set(false); }
        });
        assertEquals(List.of(0),seen);
        clock.addAndGet(1000); ready.set(true);
        pass.run(4, ready::get, stage -> {
            seen.add(stage);
            if(stage==1) assertFalse(admission.admit("worksite"),"new preparation remains budgeted after a durability wake");
            if(stage==2) clock.incrementAndGet(); // an exact pending effect's finite window
        });
        assertEquals(List.of(0,1,2,3),seen,"effect continuation is not deferred for a background preparation overrun");
        assertEquals(7,pass.elapsed(),"idle durability time grants neither new budget nor measured work");
        pass.run(4,ready::get,stage->fail("an admitted effect cannot be attempted twice in one native tick"));
        pass.begin(21);pass.run(4,ready::get,seen::add);
        assertEquals(List.of(0,1,2,3,0,1,2,3),seen);
    }
    @Test void recoveredHeadroomRestoresCadenceWithoutChangingDependencyOrderOrRenewingAWakeBudget() {
        var clock = new AtomicLong(); var pass = new FrontierV3PhysicalPass(clock::get);
        var ready = new AtomicBoolean(true); var seen = new ArrayList<Integer>();
        pass.begin(30);
        pass.run(4, ready::get, stage -> { seen.add(stage); clock.addAndGet(6); ready.set(false); });
        assertEquals(List.of(0), seen);
        pass.begin(31);
        ready.set(false);
        pass.run(4, ready::get, stage -> fail("a new tick cannot bypass an unpublished predecessor"));
        ready.set(true);
        pass.run(4, ready::get, stage -> { seen.add(stage); clock.incrementAndGet(); });
        assertEquals(List.of(0, 1, 2, 3, 0), seen);
        pass.run(4, ready::get, stage -> fail("durability wake cannot repeat an executor"));
        pass.begin(32);
        pass.run(4, ready::get, stage -> { seen.add(stage); clock.incrementAndGet(); });
        assertEquals(List.of(0, 1, 2, 3, 0, 1, 2, 3, 0), seen,
                "cheap effects resume on successive native ticks, without an empty tail-only round");
    }
}
