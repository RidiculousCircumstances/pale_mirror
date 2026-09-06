package io.farfrontier.palemirror.frontier.reference;

import java.util.Objects;

/** Mutable loan obligation from Python {@code CreditPosition}. */
public final class ReferenceCreditPosition {
    private final int id;
    private final String borrowerKind;
    private final int borrowerId;
    private final double ratePerDay;
    private final String purpose;
    private final int issuedDay;
    private double principal;
    private String status = "performing";

    public ReferenceCreditPosition(int id, String borrowerKind, int borrowerId, double principal, double ratePerDay, String purpose, int issuedDay) {
        this.id = id;
        this.borrowerKind = Objects.requireNonNull(borrowerKind, "borrowerKind");
        this.borrowerId = borrowerId;
        this.principal = principal;
        this.ratePerDay = ratePerDay;
        this.purpose = Objects.requireNonNull(purpose, "purpose");
        this.issuedDay = issuedDay;
    }

    public int id() { return id; }
    public String borrowerKind() { return borrowerKind; }
    public int borrowerId() { return borrowerId; }
    public double principal() { return principal; }
    public void principal(double value) { principal = value; }
    public double ratePerDay() { return ratePerDay; }
    public String purpose() { return purpose; }
    public int issuedDay() { return issuedDay; }
    public String status() { return status; }
    public void status(String value) { status = Objects.requireNonNull(value, "status"); }
}
