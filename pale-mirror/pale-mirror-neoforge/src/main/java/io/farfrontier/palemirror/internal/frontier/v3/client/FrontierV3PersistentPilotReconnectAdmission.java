package io.farfrontier.palemirror.internal.frontier.v3.client;

/** Pure transport-boundary admission for the development-only persistent pilot. */
final class FrontierV3PersistentPilotReconnectAdmission {
    private FrontierV3PersistentPilotReconnectAdmission() { }

    static boolean eligible(boolean awaitingResume, boolean liveConnection) {
        return awaitingResume && !liveConnection;
    }
}
