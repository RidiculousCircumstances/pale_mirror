package io.farfrontier.palemirror.internal.integration.distanthorizons;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class DistantHorizonsRuntimeTest {
    private static final DistantHorizonsRuntime.Policy POLICY =
            new DistantHorizonsRuntime.Policy(12.0D, 6.0D, 200, 0.35D, 0.05D);

    @Test
    void appliesCacheOnlyOverrideExactlyOnce() {
        TestBridge bridge = new TestBridge(false, false);
        DistantHorizonsRuntime runtime = DistantHorizonsRuntime.forTest(bridge, POLICY);

        runtime.applyOnce();
        runtime.applyOnce();

        assertEquals(1, bridge.applyCalls);
        assertEquals(0.35D, bridge.lastRatio);
        assertTrue(runtime.status().contains("mode=CACHE_ONLY"));
        assertTrue(runtime.status().contains("load=NORMAL"));
        assertTrue(runtime.status().contains("backgroundGenerator=DISABLED"));
        assertTrue(runtime.status().contains("applied=true"));
    }

    @Test
    void throttlesAtFastTravelAndRequiresContinuousRecoveryWindow() {
        TestBridge bridge = new TestBridge(false, false);
        DistantHorizonsRuntime runtime = DistantHorizonsRuntime.forTest(bridge, POLICY);

        runtime.observeForTest(12.0D, false, 20, 20);
        assertEquals(0.05D, bridge.lastRatio);
        assertTrue(runtime.status().contains("load=FAST_TRAVEL"));

        for (int tick = 40; tick < 220; tick += 20) runtime.observeForTest(5.0D, false, 20, tick);
        assertEquals(0.05D, bridge.lastRatio);
        runtime.observeForTest(7.0D, false, 20, 220);
        for (int tick = 240; tick < 420; tick += 20) runtime.observeForTest(5.0D, false, 20, tick);
        assertEquals(0.05D, bridge.lastRatio);
        runtime.observeForTest(5.0D, false, 20, 420);

        assertEquals(0.35D, bridge.lastRatio);
        assertTrue(runtime.status().contains("load=NORMAL"));
    }

    @Test
    void discontinuityImmediatelyThrottles() {
        TestBridge bridge = new TestBridge(false, false);
        DistantHorizonsRuntime runtime = DistantHorizonsRuntime.forTest(bridge, POLICY);

        runtime.observeForTest(0.0D, true, 20, 20);

        assertEquals(0.05D, bridge.lastRatio);
        assertTrue(runtime.status().contains("load=FAST_TRAVEL"));
    }

    @Test
    void dynamicFailureLeavesCacheOnlyOverrideActiveAndVisible() {
        TestBridge bridge = new TestBridge(false, true);
        DistantHorizonsRuntime runtime = DistantHorizonsRuntime.forTest(bridge, POLICY);

        runtime.observeForTest(20.0D, false, 20, 20);

        assertEquals(1, bridge.applyCalls);
        assertEquals(1, bridge.ratioCalls);
        assertEquals(0, bridge.closeCalls);
        assertTrue(runtime.status().contains("applied=true"));
        assertTrue(runtime.status().contains("transition=DEGRADED"));
    }

    @Test
    void exposesInitialFailureAndClearsPartialOverrides() {
        TestBridge bridge = new TestBridge(true, false);
        DistantHorizonsRuntime runtime = DistantHorizonsRuntime.forTest(bridge, POLICY);

        runtime.applyOnce();
        runtime.applyOnce();

        assertEquals(1, bridge.applyCalls);
        assertEquals(1, bridge.closeCalls);
        assertTrue(runtime.status().contains("FAILED"));
        assertFalse(runtime.status().contains("applied=true"));
    }

    private static final class TestBridge implements DistantHorizonsRuntime.Bridge {
        private final boolean failInitial;
        private final boolean failDynamic;
        private int applyCalls;
        private int ratioCalls;
        private int closeCalls;
        private double lastRatio;

        private TestBridge(boolean failInitial, boolean failDynamic) {
            this.failInitial = failInitial;
            this.failDynamic = failDynamic;
        }

        @Override public String version() { return "test"; }

        @Override
        public double applyCacheOnly(double ratio) {
            applyCalls++;
            if (failInitial) throw new IllegalStateException("initial rejected");
            lastRatio = ratio;
            return ratio;
        }

        @Override
        public double setRuntimeRatio(double ratio) {
            ratioCalls++;
            if (failDynamic) throw new IllegalStateException("dynamic rejected");
            lastRatio = ratio;
            return ratio;
        }

        @Override public void close() { closeCalls++; }
    }
}
