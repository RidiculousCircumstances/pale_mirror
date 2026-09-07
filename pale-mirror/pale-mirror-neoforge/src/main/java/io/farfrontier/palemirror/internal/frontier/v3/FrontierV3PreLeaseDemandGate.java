package io.farfrontier.palemirror.internal.frontier.v3;

/**
 * A future typed scene may retain an ambient body only while that body's canonical surface is
 * physically demanded.  A global process candidate alone is not physical authority and must
 * not freeze its COLD continuation in an unloaded area.
 */
final class FrontierV3PreLeaseDemandGate {
    private FrontierV3PreLeaseDemandGate() { }

    static boolean holdsForTypedHandoff(boolean hasTypedPreLease, boolean locallyDemanded) {
        return hasTypedPreLease && locallyDemanded;
    }
}
