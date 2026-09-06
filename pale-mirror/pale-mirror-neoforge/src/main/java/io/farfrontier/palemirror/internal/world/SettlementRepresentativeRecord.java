package io.farfrontier.palemirror.internal.world;

import java.util.Objects;

import io.farfrontier.palemirror.domain.SettlementCohort;

final class SettlementRepresentativeRecord {
    private final String nativeId;
    private final SettlementCohort cohort;
    private final long firstSeenAt;
    private long lastSeenAt;
    private long consecutiveSeenTicks;
    private RepresentativeMembershipStatus status;

    SettlementRepresentativeRecord(String nativeId, SettlementCohort cohort, long firstSeenAt) {
        this(nativeId, cohort, firstSeenAt, firstSeenAt, 0, RepresentativeMembershipStatus.CANDIDATE);
    }

    SettlementRepresentativeRecord(String nativeId, SettlementCohort cohort, long firstSeenAt, long lastSeenAt,
                                   long consecutiveSeenTicks, RepresentativeMembershipStatus status) {
        if (nativeId == null || nativeId.isBlank() || firstSeenAt < 0 || lastSeenAt < firstSeenAt || consecutiveSeenTicks < 0) {
            throw new IllegalArgumentException("Invalid representative record");
        }
        this.nativeId = nativeId;
        this.cohort = Objects.requireNonNull(cohort, "cohort");
        this.firstSeenAt = firstSeenAt;
        this.lastSeenAt = lastSeenAt;
        this.consecutiveSeenTicks = consecutiveSeenTicks;
        this.status = Objects.requireNonNull(status, "status");
    }

    String nativeId() { return nativeId; }
    SettlementCohort cohort() { return cohort; }
    long firstSeenAt() { return firstSeenAt; }
    long lastSeenAt() { return lastSeenAt; }
    long consecutiveSeenTicks() { return consecutiveSeenTicks; }
    RepresentativeMembershipStatus status() { return status; }
    boolean registered() { return status == RepresentativeMembershipStatus.REGISTERED; }

    void seen(long gameTime, long delta, boolean eligible) {
        if (status == RepresentativeMembershipStatus.LOST) return;
        lastSeenAt = gameTime;
        if (!eligible) return;
        consecutiveSeenTicks += Math.max(0, delta);
        if (consecutiveSeenTicks >= SettlementObservationRecord.MEMBERSHIP_WINDOW_TICKS) {
            status = RepresentativeMembershipStatus.REGISTERED;
        }
    }

    boolean lost() {
        if (status != RepresentativeMembershipStatus.REGISTERED) return false;
        status = RepresentativeMembershipStatus.LOST;
        return true;
    }
}
