package io.farfrontier.palemirror.frontier.reference;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Costed political agreement retained by V2 until its terminal source status. */
public final class ReferenceCoalitionCharter {
    private final int id;
    private final int leaderId;
    private final List<Integer> members;
    private final String target;
    private final int openedDay;
    private final int expiresDay;
    private final LinkedHashMap<Integer, Double> contribution;
    private final LinkedHashMap<Integer, Double> reserveCommitment;
    private final LinkedHashMap<Integer, Double> compensationDue;
    private String status = "offered";
    private String reason = "";

    ReferenceCoalitionCharter(int id, int leaderId, List<Integer> members, String target, int openedDay, int expiresDay,
                              Map<Integer, Double> contribution, Map<Integer, Double> reserveCommitment,
                              Map<Integer, Double> compensationDue) {
        this.id = id;
        this.leaderId = leaderId;
        this.members = List.copyOf(Objects.requireNonNull(members, "members"));
        this.target = Objects.requireNonNull(target, "target");
        this.openedDay = openedDay;
        this.expiresDay = expiresDay;
        this.contribution = new LinkedHashMap<>(Objects.requireNonNull(contribution, "contribution"));
        this.reserveCommitment = new LinkedHashMap<>(Objects.requireNonNull(reserveCommitment, "reserveCommitment"));
        this.compensationDue = new LinkedHashMap<>(Objects.requireNonNull(compensationDue, "compensationDue"));
    }

    public int id() { return id; }
    public int leaderId() { return leaderId; }
    public List<Integer> members() { return members; }
    public String target() { return target; }
    public int openedDay() { return openedDay; }
    public int expiresDay() { return expiresDay; }
    public Map<Integer, Double> contribution() { return immutableOrdered(contribution); }
    public Map<Integer, Double> reserveCommitment() { return immutableOrdered(reserveCommitment); }
    public Map<Integer, Double> compensationDue() { return immutableOrdered(compensationDue); }
    public String status() { return status; }
    public String reason() { return reason; }

    void status(String value) { status = Objects.requireNonNull(value, "status"); }
    void reason(String value) { reason = Objects.requireNonNull(value, "reason"); }

    private static <K, V> Map<K, V> immutableOrdered(Map<K, V> source) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }
}
