package io.farfrontier.palemirror.frontier.reference;

import java.util.Objects;

/** Source V2 documented payment obligation after a siege requisition. */
public final class ReferenceCompensationClaim {
    private final int id;
    private final int settlementId;
    private final int companyId;
    private final double amount;
    private final int dueDay;
    private final String reason;
    private String status = "pending";

    ReferenceCompensationClaim(int id, int settlementId, int companyId, double amount, int dueDay, String reason) {
        this.id = id;
        this.settlementId = settlementId;
        this.companyId = companyId;
        this.amount = amount;
        this.dueDay = dueDay;
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    public int id() { return id; }
    public int settlementId() { return settlementId; }
    public int companyId() { return companyId; }
    public double amount() { return amount; }
    public int dueDay() { return dueDay; }
    public String reason() { return reason; }
    public String status() { return status; }

    void status(String value) { status = Objects.requireNonNull(value, "status"); }
}
