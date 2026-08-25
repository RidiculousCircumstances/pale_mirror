package io.farfrontier.palemirror.frontier.reference;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** Mutable resource/personnel reservation ported from Python {@code Commitment}. */
public final class ReferenceCommitment {
    private final int proposalId;
    private final int settlementId;
    private final EnumMap<ReferenceResource, Double> supplies;
    private final double personnel;
    private final int acceptedDay;
    private String status;

    public ReferenceCommitment(int proposalId, int settlementId, Map<ReferenceResource, Double> supplies,
                               double personnel, int acceptedDay) {
        this(proposalId, settlementId, supplies, personnel, acceptedDay, "reserved");
    }

    public ReferenceCommitment(int proposalId, int settlementId, Map<ReferenceResource, Double> supplies,
                               double personnel, int acceptedDay, String status) {
        this.proposalId = proposalId;
        this.settlementId = settlementId;
        this.supplies = new EnumMap<>(ReferenceResource.class);
        this.supplies.putAll(Objects.requireNonNull(supplies, "supplies"));
        this.personnel = personnel;
        this.acceptedDay = acceptedDay;
        this.status = Objects.requireNonNull(status, "status");
    }

    public int proposalId() { return proposalId; }
    public int settlementId() { return settlementId; }
    public Map<ReferenceResource, Double> supplies() { return Collections.unmodifiableMap(new EnumMap<>(supplies)); }
    public double personnel() { return personnel; }
    public int acceptedDay() { return acceptedDay; }
    public String status() { return status; }
    public void status(String value) { status = Objects.requireNonNull(value, "status"); }
}
