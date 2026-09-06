package io.farfrontier.palemirror.internal.integration.distanthorizons;

/** Pure hysteresis policy for protecting live chunk generation from presentation work. */
final class TravelLoadController {
    enum Mode { NORMAL, FAST_TRAVEL }

    private final double enterSpeed;
    private final double exitSpeed;
    private final int recoveryTicks;
    private Mode mode = Mode.NORMAL;
    private int slowTicks;

    TravelLoadController(double enterSpeed, double exitSpeed, int recoveryTicks) {
        if (enterSpeed <= exitSpeed) throw new IllegalArgumentException("enterSpeed must exceed exitSpeed");
        if (exitSpeed < 0.0D || recoveryTicks <= 0) throw new IllegalArgumentException("invalid travel policy");
        this.enterSpeed = enterSpeed;
        this.exitSpeed = exitSpeed;
        this.recoveryTicks = recoveryTicks;
    }

    Mode observe(double maximumSpeed, boolean discontinuity, int elapsedTicks) {
        if (discontinuity || maximumSpeed >= enterSpeed) {
            mode = Mode.FAST_TRAVEL;
            slowTicks = 0;
            return mode;
        }
        if (mode == Mode.FAST_TRAVEL) {
            if (maximumSpeed <= exitSpeed) {
                slowTicks = Math.min(recoveryTicks, slowTicks + Math.max(0, elapsedTicks));
                if (slowTicks >= recoveryTicks) mode = Mode.NORMAL;
            } else {
                slowTicks = 0;
            }
        }
        return mode;
    }

    Mode mode() { return mode; }

    int recoveryTicksRemaining() {
        return mode == Mode.NORMAL ? 0 : Math.max(0, recoveryTicks - slowTicks);
    }
}
