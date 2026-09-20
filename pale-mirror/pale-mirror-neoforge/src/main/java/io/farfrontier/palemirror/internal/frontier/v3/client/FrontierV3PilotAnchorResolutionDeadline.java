package io.farfrontier.palemirror.internal.frontier.v3.client;

/**
 * Wall-clock deadline vocabulary for a client-side read-only diagnostic anchor.
 *
 * <p>A recovered server may restore or advance its game clock before the
 * persistent client receives its first replacement-world tick.  Anchor lookup
 * is an ordinary client/server request, so its bounded wait must not inherit a
 * clock discontinuity from that recovered world.</p>
 */
final class FrontierV3PilotAnchorResolutionDeadline {
    private FrontierV3PilotAnchorResolutionDeadline() { }

    static boolean pollDue(long nowNanos, long lastRequestNanos) {
        return lastRequestNanos < 0L || nowNanos - lastRequestNanos >= 1_000_000_000L;
    }

    static boolean timedOut(long nowNanos, long startedNanos, long timeoutMillis) {
        return nowNanos - startedNanos >= timeoutMillis * 1_000_000L;
    }
}
