package io.farfrontier.palemirror.internal.frontier.v3;

import java.util.Objects;
import java.util.Set;

/** Receipt grammar only. It neither advances the clock nor removes a hold. */
final class FrontierV3FastForwardReceiptValidation {
    private FrontierV3FastForwardReceiptValidation() { }
    private static boolean zeroRelease(String status, Long admitted, Long reached) {
        return "RELEASED".equals(status) && Objects.equals(admitted, 0L) && Objects.equals(reached, 0L);
    }
    static void request(long id, String kind, int ticks, Long target, Long admitted, Long reached, String status, String reason) {
        if (id < 1L || !Set.of("RELATIVE", "ABSOLUTE").contains(kind) || ticks < 0
                || !Set.of("QUEUED", "COMPLETED", "HELD", "REJECTED", "RELEASED").contains(status)
                || "RELATIVE".equals(kind) && (ticks < 1 || target == null && !"REJECTED".equals(status))
                || "ABSOLUTE".equals(kind) && (target == null || target < 0L || target == 0L && !(ticks == 0 && zeroRelease(status, admitted, reached)))
                || Set.of("QUEUED", "COMPLETED", "HELD", "RELEASED").contains(status) && admitted == null
                || Set.of("COMPLETED", "HELD", "RELEASED").contains(status) && reached == null
                || "REJECTED".equals(status) != (reason != null))
            throw new IllegalArgumentException("invalid fast-forward request receipt");
    }
    static void target(long id, long target, Long admitted, Long reached, String status, String failure) {
        if (id < 1L || target < 0L || target == 0L && !zeroRelease(status, admitted, reached)
                || !Set.of("ADVANCING", "HELD", "REJECTED", "RELEASED").contains(status)
                || (failure == null) != !"REJECTED".equals(status)
                || Set.of("HELD", "RELEASED").contains(status) && !Objects.equals(reached, target)
                || reached != null && reached < 0L || admitted != null && admitted < 0L)
            throw new IllegalArgumentException("invalid fast-forward target outcome");
    }
}
