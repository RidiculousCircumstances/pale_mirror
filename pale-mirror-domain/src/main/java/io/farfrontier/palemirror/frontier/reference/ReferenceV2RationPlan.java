package io.farfrontier.palemirror.frontier.reference;

import java.util.Objects;

/** Current source V2 food issue limit for one settlement. */
public final class ReferenceV2RationPlan {
    private final int settlementId;
    private double fraction = 1.0d;
    private int issuedDay;
    private String reason = "normal consumption";

    ReferenceV2RationPlan(int settlementId) { this.settlementId = settlementId; }

    public int settlementId() { return settlementId; }
    public double fraction() { return fraction; }
    public int issuedDay() { return issuedDay; }
    public String reason() { return reason; }

    void fraction(double value) { fraction = value; }
    void issuedDay(int value) { issuedDay = value; }
    void reason(String value) { reason = Objects.requireNonNull(value, "reason"); }
}
