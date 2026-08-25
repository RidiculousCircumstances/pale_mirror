package io.farfrontier.palemirror.frontier;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * One bounded, supplied human coalition advancing on a real hive.  This is a
 * strategic record in its own right: it is deliberately not a field response
 * to one assault, because it may survive several local incidents and can end
 * by destroying the actual hive Core.
 */
public final class FrontierCampaign {
    static final int MINIMUM_PARTICIPANTS = 2;
    public enum Kind { HIVE_CLEARANCE }
    /** Names deliberately match pale_mirror_ai.simulation.v2.FrontPhase. */
    public enum Phase { RECON, ASSEMBLE, ESTABLISH, CORDON, CLEAR, HOLD, RESTORE, WITHDRAW, COMPLETE, FAILED }

    private final String id;
    private final String leaderSettlementId;
    private final String targetHiveId;
    private final String targetOrganId;
    private final Kind kind;
    private final List<String> contributorSettlementIds;
    private final List<String> participantIds;
    private final long startedDay;
    private final int transitDays;
    private FrontierPoint position;
    private Phase phase = Phase.RECON;
    private int transitProgress;
    private int phaseDays;
    private int supplyRiskPermille;
    private int supplyReadinessPermille;
    private long finishedDay = -1;
    private long revision;

    FrontierCampaign(String id, String leaderSettlementId, String targetHiveId, String targetOrganId, Kind kind,
                     List<String> contributorSettlementIds, List<String> participantIds, long startedDay,
                     int transitDays, FrontierPoint origin) {
        this.id = required(id, "id");
        this.leaderSettlementId = required(leaderSettlementId, "leaderSettlementId");
        this.targetHiveId = required(targetHiveId, "targetHiveId");
        this.targetOrganId = required(targetOrganId, "targetOrganId");
        this.kind = Objects.requireNonNull(kind, "kind");
        if (contributorSettlementIds == null || contributorSettlementIds.size() < 2 || contributorSettlementIds.size() > 3
                || new LinkedHashSet<>(contributorSettlementIds).size() != contributorSettlementIds.size()
                || !contributorSettlementIds.contains(leaderSettlementId)) {
            throw new IllegalArgumentException("campaign contributors must be a bounded coalition containing its leader");
        }
        if (participantIds == null || participantIds.size() < MINIMUM_PARTICIPANTS || participantIds.size() > 12
                || new LinkedHashSet<>(participantIds).size() != participantIds.size()) {
            throw new IllegalArgumentException("campaign participants must be unique and bounded");
        }
        this.contributorSettlementIds = List.copyOf(contributorSettlementIds);
        this.participantIds = List.copyOf(participantIds);
        if (startedDay < 1 || transitDays < 1) throw new IllegalArgumentException("invalid campaign schedule");
        this.startedDay = startedDay;
        this.transitDays = transitDays;
        this.position = Objects.requireNonNull(origin, "origin");
    }

    public String id() { return id; }
    public String leaderSettlementId() { return leaderSettlementId; }
    public String targetHiveId() { return targetHiveId; }
    public String targetOrganId() { return targetOrganId; }
    public Kind kind() { return kind; }
    public List<String> contributorSettlementIds() { return contributorSettlementIds; }
    public List<String> participantIds() { return participantIds; }
    public long startedDay() { return startedDay; }
    public int transitDays() { return transitDays; }
    public FrontierPoint position() { return position; }
    public Phase phase() { return phase; }
    public int transitProgress() { return transitProgress; }
    public int phaseDays() { return phaseDays; }
    public int supplyRiskPermille() { return supplyRiskPermille; }
    public int supplyReadinessPermille() { return supplyReadinessPermille; }
    public long finishedDay() { return finishedDay; }
    public long revision() { return revision; }
    public String materializationId() { return "frontier:campaign:" + id; }
    public boolean terminal() { return phase == Phase.COMPLETE || phase == Phase.FAILED; }

    boolean advance(Phase expected, Phase next) {
        if (phase != expected) return false;
        phase = next;
        phaseDays = 0;
        revision++;
        return true;
    }

    boolean advanceEstablish(FrontierPoint origin, FrontierPoint target) {
        if (phase != Phase.ESTABLISH) return false;
        transitProgress = Math.min(transitDays, transitProgress + 1);
        position = interpolate(origin, target, transitProgress, transitDays);
        phaseDays++;
        if (transitProgress == transitDays) {
            phase = Phase.CORDON;
            phaseDays = 0;
        }
        revision++;
        return true;
    }

    boolean phaseDay() {
        if (terminal() || phase == Phase.ESTABLISH || phase == Phase.WITHDRAW) return false;
        phaseDays++;
        revision++;
        return true;
    }

    boolean beginWithdraw() {
        if (terminal() || phase == Phase.WITHDRAW) return false;
        phase = Phase.WITHDRAW;
        transitProgress = 0;
        phaseDays = 0;
        revision++;
        return true;
    }

    boolean advanceWithdraw(FrontierPoint target, FrontierPoint origin, long day) {
        if (phase != Phase.WITHDRAW) return false;
        transitProgress = Math.min(transitDays, transitProgress + 1);
        position = interpolate(target, origin, transitProgress, transitDays);
        phaseDays++;
        if (transitProgress == transitDays) {
            phase = Phase.COMPLETE;
            finishedDay = day;
        }
        revision++;
        return true;
    }

    boolean fail(long day) {
        if (terminal()) return false;
        phase = Phase.FAILED;
        finishedDay = day;
        revision++;
        return true;
    }

    boolean reconcileSupply(int riskPermille, int readinessPermille) {
        if (riskPermille < 0 || riskPermille > 1_000 || readinessPermille < 0 || readinessPermille > 1_000) {
            throw new IllegalArgumentException("invalid campaign supply ledger");
        }
        if (supplyRiskPermille == riskPermille && supplyReadinessPermille == readinessPermille) return false;
        supplyRiskPermille = riskPermille;
        supplyReadinessPermille = readinessPermille;
        revision++;
        return true;
    }

    void restore(Phase phase, FrontierPoint position, int transitProgress, int phaseDays, int supplyRiskPermille,
                 int supplyReadinessPermille, long finishedDay, long revision) {
        this.phase = Objects.requireNonNull(phase, "phase");
        this.position = Objects.requireNonNull(position, "position");
        if (transitProgress < 0 || transitProgress > transitDays || phaseDays < 0 || supplyRiskPermille < 0
                || supplyRiskPermille > 1_000 || supplyReadinessPermille < 0 || supplyReadinessPermille > 1_000
                || revision < 0 || (terminal() && finishedDay < startedDay) || (!terminal() && finishedDay != -1)) {
            throw new IllegalArgumentException("invalid persisted campaign state");
        }
        this.transitProgress = transitProgress;
        this.phaseDays = phaseDays;
        this.supplyRiskPermille = supplyRiskPermille;
        this.supplyReadinessPermille = supplyReadinessPermille;
        this.finishedDay = finishedDay;
        this.revision = revision;
    }

    static String idFor(String leaderSettlementId, String targetHiveId, long startedDay) {
        return "frontier:campaign:" + leaderSettlementId.substring(leaderSettlementId.lastIndexOf(':') + 1) + ":"
                + targetHiveId.substring(targetHiveId.lastIndexOf(':') + 1) + ":day:"
                + String.format(Locale.ROOT, "%08d", startedDay);
    }

    static int travelDays(FrontierPoint origin, FrontierPoint target) { return FrontierBalance.humanTravelDays(origin, target); }

    private static FrontierPoint interpolate(FrontierPoint origin, FrontierPoint target, int progress, int total) {
        return new FrontierPoint(origin.x() + Math.round((target.x() - origin.x()) * progress / (float) total),
                origin.z() + Math.round((target.z() - origin.z()) * progress / (float) total));
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value;
    }
}
