package io.farfrontier.palemirror.frontier.reference;

import java.util.Objects;

/** Mutable AI objective ported from Python {@code StrategicObjective}. */
public final class ReferenceStrategicObjective {
    private final int id;
    private final ReferenceAgentRef agent;
    private final ReferenceObjectiveKind kind;
    private final ReferenceTargetRef target;
    private final double utility;
    private final int createdDay;
    private final int committedUntil;
    private final String posture;
    private final String reason;
    private final double directiveWeight;
    private String status;

    public ReferenceStrategicObjective(int id, ReferenceAgentRef agent, ReferenceObjectiveKind kind, ReferenceTargetRef target,
                                       double utility, int createdDay, int committedUntil, String posture, String reason) {
        this(id, agent, kind, target, utility, createdDay, committedUntil, posture, reason, 1.0d, "active");
    }

    public ReferenceStrategicObjective(int id, ReferenceAgentRef agent, ReferenceObjectiveKind kind, ReferenceTargetRef target,
                                       double utility, int createdDay, int committedUntil, String posture, String reason,
                                       double directiveWeight, String status) {
        this.id = id;
        this.agent = Objects.requireNonNull(agent, "agent");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.target = target;
        this.utility = utility;
        this.createdDay = createdDay;
        this.committedUntil = committedUntil;
        this.posture = Objects.requireNonNull(posture, "posture");
        this.reason = Objects.requireNonNull(reason, "reason");
        this.directiveWeight = directiveWeight;
        this.status = Objects.requireNonNull(status, "status");
    }

    public int id() { return id; }
    public ReferenceAgentRef agent() { return agent; }
    public ReferenceObjectiveKind kind() { return kind; }
    public ReferenceTargetRef target() { return target; }
    public double utility() { return utility; }
    public int createdDay() { return createdDay; }
    public int committedUntil() { return committedUntil; }
    public String posture() { return posture; }
    public String reason() { return reason; }
    public double directiveWeight() { return directiveWeight; }
    public String status() { return status; }
    public void status(String value) { status = Objects.requireNonNull(value, "status"); }
}
