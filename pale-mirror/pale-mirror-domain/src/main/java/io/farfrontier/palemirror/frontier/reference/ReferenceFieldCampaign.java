package io.farfrontier.palemirror.frontier.reference;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** Mutable source port of Python {@code Campaign}; {@link ReferenceFieldWarfare} retains its identity. */
public final class ReferenceFieldCampaign {
    private final int id;
    private final ReferenceCampaignKind kind;
    private final int leaderId;
    private final Set<Integer> contributors;
    private final String targetKind;
    private final Integer targetId;
    private final int targetX;
    private final int targetY;
    private final int createdDay;
    private final Set<Integer> postIds = new LinkedHashSet<>();
    private final Set<Integer> engagementIds = new LinkedHashSet<>();
    private final String reason;
    private ReferenceCampaignPhase phase = ReferenceCampaignPhase.ASSESSMENT;
    private String assessmentSummary = "—";
    private double readiness;
    private double expectedPersonnel;
    private double expectedPower;
    private double expectedDefence;
    private double risk = 1.0d;
    private int cooldownUntil;
    private Integer finishedDay;

    ReferenceFieldCampaign(int id, ReferenceCampaignKind kind, int leaderId, Set<Integer> contributors, String targetKind,
                           Integer targetId, int targetX, int targetY, int createdDay, String reason) {
        this.id = id;
        this.kind = Objects.requireNonNull(kind, "kind");
        this.leaderId = leaderId;
        this.contributors = Collections.unmodifiableSet(new LinkedHashSet<>(Objects.requireNonNull(contributors, "contributors")));
        this.targetKind = Objects.requireNonNull(targetKind, "targetKind");
        this.targetId = targetId;
        this.targetX = targetX;
        this.targetY = targetY;
        this.createdDay = createdDay;
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    public int id() { return id; }
    public ReferenceCampaignKind kind() { return kind; }
    public int leaderId() { return leaderId; }
    public Set<Integer> contributors() { return contributors; }
    public String targetKind() { return targetKind; }
    public Integer targetId() { return targetId; }
    public int targetX() { return targetX; }
    public int targetY() { return targetY; }
    public int createdDay() { return createdDay; }
    public ReferenceCampaignPhase phase() { return phase; }
    void phase(ReferenceCampaignPhase value) { phase = Objects.requireNonNull(value, "phase"); }
    public Set<Integer> postIds() { return Collections.unmodifiableSet(new LinkedHashSet<>(postIds)); }
    Set<Integer> mutablePostIds() { return postIds; }
    public Set<Integer> engagementIds() { return Collections.unmodifiableSet(new LinkedHashSet<>(engagementIds)); }
    Set<Integer> mutableEngagementIds() { return engagementIds; }
    public String reason() { return reason; }
    public String assessmentSummary() { return assessmentSummary; }
    void assessmentSummary(String value) { assessmentSummary = Objects.requireNonNull(value, "assessmentSummary"); }
    public double readiness() { return readiness; }
    void readiness(double value) { readiness = value; }
    public double expectedPersonnel() { return expectedPersonnel; }
    void expectedPersonnel(double value) { expectedPersonnel = value; }
    public double expectedPower() { return expectedPower; }
    void expectedPower(double value) { expectedPower = value; }
    public double expectedDefence() { return expectedDefence; }
    void expectedDefence(double value) { expectedDefence = value; }
    public double risk() { return risk; }
    void risk(double value) { risk = value; }
    public int cooldownUntil() { return cooldownUntil; }
    void cooldownUntil(int value) { cooldownUntil = value; }
    public Integer finishedDay() { return finishedDay; }
    void finishedDay(Integer value) { finishedDay = value; }
}
