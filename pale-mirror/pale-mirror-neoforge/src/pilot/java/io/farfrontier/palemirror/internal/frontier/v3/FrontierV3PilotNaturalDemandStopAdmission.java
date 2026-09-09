package io.farfrontier.palemirror.internal.frontier.v3;

import java.util.Objects;

/**
 * One pilot-only, server-thread admission for the natural-demand graceful-stop carrier.
 *
 * <p>The supplied readiness reader is deliberately evaluated by the same command action that
 * consumes this admission and invokes the halt action.  It is not an external receipt fence.
 */
final class FrontierV3PilotNaturalDemandStopAdmission {
    @FunctionalInterface
    interface FreshReadiness {
        FrontierV3PilotNaturalDemandEpisode.Status read();
    }

    private final Object server;
    private final String serverRunId;
    private final long serverPid;
    private final String nonce;
    private boolean consumed;

    FrontierV3PilotNaturalDemandStopAdmission(Object server, String serverRunId, long serverPid, String nonce) {
        this.server = Objects.requireNonNull(server, "server");
        this.serverRunId = requireRunId(serverRunId);
        if (serverPid <= 1L) throw new IllegalArgumentException("server pid is invalid");
        this.serverPid = serverPid;
        this.nonce = requireNonce(nonce);
    }

    /** Performs the sole fresh read immediately before the one permitted ordinary halt action. */
    void admit(Object currentServer, String currentRunId, long currentPid, String suppliedNonce,
            FreshReadiness freshReadiness, Runnable haltAction) {
        if (consumed) throw new IllegalStateException("pilot natural-demand stop admission was already consumed");
        if (currentServer != server || !serverRunId.equals(currentRunId) || currentPid != serverPid || !nonce.equals(suppliedNonce)) {
            throw new IllegalStateException("pilot natural-demand stop admission is stale or foreign");
        }
        if (Objects.requireNonNull(freshReadiness, "fresh readiness").read() != FrontierV3PilotNaturalDemandEpisode.Status.ELIGIBLE) {
            throw new IllegalStateException("pilot natural-demand stop admission is no longer eligible");
        }
        consumed = true;
        Objects.requireNonNull(haltAction, "halt action").run();
    }

    boolean consumed() { return consumed; }
    boolean matchesNonce(String suppliedNonce) { return nonce.equals(suppliedNonce); }

    private static String requireRunId(String value) {
        if (value == null || !value.matches("[0-9a-f-]{36}")) throw new IllegalArgumentException("server run id is invalid");
        return value;
    }

    private static String requireNonce(String value) {
        if (value == null || !value.matches("[0-9a-f-]{36}")) throw new IllegalArgumentException("stop admission nonce is invalid");
        return value;
    }
}
