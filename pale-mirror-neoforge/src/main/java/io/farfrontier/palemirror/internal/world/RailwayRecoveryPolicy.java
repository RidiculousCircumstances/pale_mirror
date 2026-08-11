package io.farfrontier.palemirror.internal.world;

import io.farfrontier.palemirror.internal.adapter.FreightServiceStatus;

final class RailwayRecoveryPolicy {
    private RailwayRecoveryPolicy() { }

    static boolean serviceCanResume(FreightServiceStatus status) {
        return status == FreightServiceStatus.PLACING || status == FreightServiceStatus.RUNNING
                || status == FreightServiceStatus.PARKING || status == FreightServiceStatus.PARKED
                || status == FreightServiceStatus.PLAYER_MANAGED;
    }
}
