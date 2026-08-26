package io.farfrontier.palemirror.visuals.runtime;

import io.farfrontier.palemirror.api.GenesisReadiness;

/** Explicit admission boundary between terrain-authored campaigns and a Frontier graybox world. */
final class AuthoredGenesisAdmission {
    private static final String GRAYBOX_DIAGNOSTIC =
            "Authored region genesis is disabled; the Frontier graybox projector owns materialization.";

    private AuthoredGenesisAdmission() { }

    static Decision resolve(boolean authoredGenesisEnabled) {
        return authoredGenesisEnabled
                ? new Decision(true, GenesisReadiness.planning())
                : new Decision(false, new GenesisReadiness(GenesisReadiness.State.READY, 0,
                        "frontier-graybox", GRAYBOX_DIAGNOSTIC));
    }

    record Decision(boolean planAuthoredRegions, GenesisReadiness readiness) { }
}
