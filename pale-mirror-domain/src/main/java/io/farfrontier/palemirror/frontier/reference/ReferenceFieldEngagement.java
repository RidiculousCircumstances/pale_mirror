package io.farfrontier.palemirror.frontier.reference;

import java.util.Objects;

/** Mutable source port of Python {@code Engagement}; field warfare owns its lifecycle. */
public final class ReferenceFieldEngagement {
    private final int id;
    private final ReferenceEngagementKind kind;
    private final double x;
    private final double y;
    private final int createdDay;
    private final Integer campaignId;
    private final Integer postId;
    private final Integer operationId;
    private final Integer nestId;
    private final Integer swarmId;
    private ReferenceEngagementStatus status = ReferenceEngagementStatus.ACTIVE;
    private int days;
    private double attackerPower;
    private double defenderPower;
    private double killed;
    private double wounded;

    ReferenceFieldEngagement(int id, ReferenceEngagementKind kind, double x, double y, int createdDay,
                             Integer campaignId, Integer postId, Integer operationId, Integer nestId, Integer swarmId) {
        this.id = id;
        this.kind = Objects.requireNonNull(kind, "kind");
        this.x = x;
        this.y = y;
        this.createdDay = createdDay;
        this.campaignId = campaignId;
        this.postId = postId;
        this.operationId = operationId;
        this.nestId = nestId;
        this.swarmId = swarmId;
    }

    public int id() { return id; }
    public ReferenceEngagementKind kind() { return kind; }
    public double x() { return x; }
    public double y() { return y; }
    public int createdDay() { return createdDay; }
    public Integer campaignId() { return campaignId; }
    public Integer postId() { return postId; }
    public Integer operationId() { return operationId; }
    public Integer nestId() { return nestId; }
    public Integer swarmId() { return swarmId; }
    public ReferenceEngagementStatus status() { return status; }
    void status(ReferenceEngagementStatus value) { status = Objects.requireNonNull(value, "status"); }
    public int days() { return days; }
    void days(int value) { days = value; }
    public double attackerPower() { return attackerPower; }
    void attackerPower(double value) { attackerPower = value; }
    public double defenderPower() { return defenderPower; }
    void defenderPower(double value) { defenderPower = value; }
    public double killed() { return killed; }
    void killed(double value) { killed = value; }
    public double wounded() { return wounded; }
    void wounded(double value) { wounded = value; }
}
