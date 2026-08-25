package io.farfrontier.palemirror.frontier.reference;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Mutable source port of Python {@code FrontCampaign}; {@link ReferenceV2State}
 * is its only owner.
 */
public final class ReferenceFrontCampaign {
    private final int id;
    private final ReferenceFrontCampaignKind kind;
    private final int leaderId;
    private final List<Integer> contributors;
    private final String targetSector;
    private final int createdDay;
    private final LinkedHashMap<Integer, Double> personnelBySettlement = new LinkedHashMap<>();
    private final LinkedHashMap<Integer, List<String>> residentIdsBySettlement = new LinkedHashMap<>();
    private final LinkedHashMap<Integer, Map<ReferenceHumanUnitKind, Double>> unitCompositionBySettlement = new LinkedHashMap<>();
    private ReferenceFrontPhase phase;
    private Integer fieldCampaignId;
    private Integer postId;
    private int holdDays;
    private final String reason;
    private double risk;
    private String statusReason = "";
    private ReferenceFrontPhase terminalOutcome;

    ReferenceFrontCampaign(int id, ReferenceFrontCampaignKind kind, int leaderId, List<Integer> contributors,
                           String targetSector, int createdDay, ReferenceFrontPhase phase,
                           Map<Integer, Double> personnelBySettlement,
                           Map<Integer, List<String>> residentIdsBySettlement,
                           Map<Integer, Map<ReferenceHumanUnitKind, Double>> unitCompositionBySettlement,
                           String reason) {
        this.id = id;
        this.kind = Objects.requireNonNull(kind, "kind");
        this.leaderId = leaderId;
        this.contributors = List.copyOf(Objects.requireNonNull(contributors, "contributors"));
        this.targetSector = Objects.requireNonNull(targetSector, "targetSector");
        this.createdDay = createdDay;
        this.phase = Objects.requireNonNull(phase, "phase");
        this.personnelBySettlement.putAll(Objects.requireNonNull(personnelBySettlement, "personnelBySettlement"));
        copyResidentIds(Objects.requireNonNull(residentIdsBySettlement, "residentIdsBySettlement"), this.residentIdsBySettlement);
        copyComposition(Objects.requireNonNull(unitCompositionBySettlement, "unitCompositionBySettlement"), this.unitCompositionBySettlement);
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    public int id() { return id; }
    public ReferenceFrontCampaignKind kind() { return kind; }
    public int leaderId() { return leaderId; }
    public List<Integer> contributors() { return contributors; }
    public String targetSector() { return targetSector; }
    public int createdDay() { return createdDay; }
    public ReferenceFrontPhase phase() { return phase; }
    void phase(ReferenceFrontPhase value) { phase = Objects.requireNonNull(value, "phase"); }
    public Map<Integer, Double> personnelBySettlement() { return immutableOrdered(personnelBySettlement); }
    Map<Integer, Double> mutablePersonnelBySettlement() { return personnelBySettlement; }
    public Map<Integer, List<String>> residentIdsBySettlement() { return immutableResidents(residentIdsBySettlement); }
    Map<Integer, List<String>> mutableResidentIdsBySettlement() { return residentIdsBySettlement; }
    public Map<Integer, Map<ReferenceHumanUnitKind, Double>> unitCompositionBySettlement() { return immutableComposition(unitCompositionBySettlement); }
    Map<Integer, Map<ReferenceHumanUnitKind, Double>> mutableUnitCompositionBySettlement() { return unitCompositionBySettlement; }
    public Integer fieldCampaignId() { return fieldCampaignId; }
    void fieldCampaignId(Integer value) { fieldCampaignId = value; }
    public Integer postId() { return postId; }
    void postId(Integer value) { postId = value; }
    public int holdDays() { return holdDays; }
    void holdDays(int value) { holdDays = value; }
    public String reason() { return reason; }
    public double risk() { return risk; }
    void risk(double value) { risk = value; }
    public String statusReason() { return statusReason; }
    void statusReason(String value) { statusReason = Objects.requireNonNull(value, "statusReason"); }
    public ReferenceFrontPhase terminalOutcome() { return terminalOutcome; }
    void terminalOutcome(ReferenceFrontPhase value) { terminalOutcome = value; }
    public double personnel() { return personnelBySettlement.values().stream().mapToDouble(Double::doubleValue).sum(); }

    private static <K, V> Map<K, V> immutableOrdered(Map<K, V> source) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }

    private static Map<Integer, List<String>> immutableResidents(Map<Integer, List<String>> source) {
        LinkedHashMap<Integer, List<String>> result = new LinkedHashMap<>();
        for (Map.Entry<Integer, List<String>> entry : source.entrySet()) result.put(entry.getKey(), List.copyOf(entry.getValue()));
        return Collections.unmodifiableMap(result);
    }

    private static Map<Integer, Map<ReferenceHumanUnitKind, Double>> immutableComposition(
            Map<Integer, Map<ReferenceHumanUnitKind, Double>> source
    ) {
        LinkedHashMap<Integer, Map<ReferenceHumanUnitKind, Double>> result = new LinkedHashMap<>();
        for (Map.Entry<Integer, Map<ReferenceHumanUnitKind, Double>> entry : source.entrySet()) {
            result.put(entry.getKey(), Map.copyOf(entry.getValue()));
        }
        return Collections.unmodifiableMap(result);
    }

    private static void copyResidentIds(Map<Integer, List<String>> source, Map<Integer, List<String>> target) {
        for (Map.Entry<Integer, List<String>> entry : source.entrySet()) target.put(entry.getKey(), new ArrayList<>(entry.getValue()));
    }

    private static void copyComposition(Map<Integer, Map<ReferenceHumanUnitKind, Double>> source,
                                        Map<Integer, Map<ReferenceHumanUnitKind, Double>> target) {
        for (Map.Entry<Integer, Map<ReferenceHumanUnitKind, Double>> entry : source.entrySet()) {
            target.put(entry.getKey(), new LinkedHashMap<>(entry.getValue()));
        }
    }
}
