package io.farfrontier.palemirror.internal.frontier.v3;

/** One admitted canonical ceiling; elapsed durability publication is progress too. */
record FrontierV3FastForwardInterval(long target) {
    FrontierV3FastForwardInterval {
        if (target < 0) throw new IllegalArgumentException("negative fast-forward ceiling");
    }
    static java.util.OptionalInt admittedDelta(long current, long target, int maximum) {
        if (current < 0 || target <= current) return java.util.OptionalInt.empty();
        long delta;
        try { delta = Math.subtractExact(target, current); }
        catch (ArithmeticException ignored) { return java.util.OptionalInt.empty(); }
        return delta > maximum ? java.util.OptionalInt.empty() : java.util.OptionalInt.of((int) delta);
    }
    int remainingAt(long current) {
        if (current < 0 || target < current) throw new IllegalArgumentException("fast-forward crossed its admitted canonical ceiling");
        return Math.toIntExact(target - current);
    }
}
