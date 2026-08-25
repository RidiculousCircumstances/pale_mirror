package io.farfrontier.palemirror.frontier.reference;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Canonical operation ported from Python {@code Operation}.
 *
 * <p>The operation owns named persons while it is active.  Minecraft
 * entities are only materializations of those names, never substitutes for
 * these maps.</p>
 */
public final class ReferenceOperation {
    private final int id;
    private final ReferenceAgentKind side;
    private final ReferenceOperationKind kind;
    private final ReferenceAgentRef owner;
    private final ReferenceTargetRef target;
    private final int startedDay;
    private final double originX;
    private final double originY;
    private final EnumMap<ReferenceResource, Double> supplies = emptyResources();
    private final LinkedHashMap<Integer, EnumMap<ReferenceResource, Double>> contributors = new LinkedHashMap<>();
    private final LinkedHashMap<Integer, Double> personnelBySettlement = new LinkedHashMap<>();
    private final LinkedHashMap<Integer, EnumMap<ReferenceHumanUnitKind, Double>> unitCompositionBySettlement = new LinkedHashMap<>();
    private final EnumMap<ReferenceHumanUnitKind, Double> unitLosses = new EnumMap<>(ReferenceHumanUnitKind.class);
    private final LinkedHashSet<Integer> detectedBy = new LinkedHashSet<>();
    private final LinkedHashMap<String, Object> details = new LinkedHashMap<>();
    private final ArrayList<ReferencePoint> waypoints = new ArrayList<>();
    private final ArrayList<ReferencePoint> returnWaypoints = new ArrayList<>();
    private final EnumMap<ReferenceResource, Double> cargo = emptyResources();
    private final LinkedHashMap<Integer, Double> evacuatedWoundedBySettlement = new LinkedHashMap<>();
    private final LinkedHashMap<Integer, List<String>> residentIdsBySettlement = new LinkedHashMap<>();
    private final LinkedHashMap<Integer, List<String>> woundedResidentIdsBySettlement = new LinkedHashMap<>();
    private double x;
    private double y;
    private double personnel;
    private double committedPersonnel;
    private double power;
    private ReferenceFormationPhase phase = ReferenceFormationPhase.SCREEN;
    private Integer objectiveId;
    private ReferenceOperationStatus status = ReferenceOperationStatus.ASSEMBLING;
    private int stationDays;
    private int unsuppliedDays;
    private Integer linkedSwarmId;
    private String outcome;
    private boolean resolved;
    private Integer finishedDay;
    private int waypointIndex;
    private ReferenceTargetRef origin;
    private Integer engagementId;

    public ReferenceOperation(int id, ReferenceAgentKind side, ReferenceOperationKind kind, ReferenceAgentRef owner,
                              ReferenceTargetRef target, int startedDay, double x, double y, double originX, double originY) {
        this.id = id;
        this.side = Objects.requireNonNull(side, "side");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.owner = Objects.requireNonNull(owner, "owner");
        this.target = Objects.requireNonNull(target, "target");
        this.startedDay = startedDay;
        this.x = x;
        this.y = y;
        this.originX = originX;
        this.originY = originY;
        for (ReferenceHumanUnitKind role : ReferenceHumanUnitKind.values()) unitLosses.put(role, 0.0d);
    }

    public int id() { return id; }
    public ReferenceAgentKind side() { return side; }
    public ReferenceOperationKind kind() { return kind; }
    public ReferenceAgentRef owner() { return owner; }
    public ReferenceTargetRef target() { return target; }
    public int startedDay() { return startedDay; }
    public double x() { return x; }
    public void x(double value) { x = value; }
    public double y() { return y; }
    public void y(double value) { y = value; }
    public void position(double valueX, double valueY) { x = valueX; y = valueY; }
    public double originX() { return originX; }
    public double originY() { return originY; }
    public double personnel() { return personnel; }
    public void personnel(double value) { personnel = value; }
    public double committedPersonnel() { return committedPersonnel; }
    public void committedPersonnel(double value) { committedPersonnel = value; }
    public double power() { return power; }
    public void power(double value) { power = value; }
    public ReferenceFormationPhase phase() { return phase; }
    public void phase(ReferenceFormationPhase value) { phase = Objects.requireNonNull(value, "phase"); }
    public Integer objectiveId() { return objectiveId; }
    public void objectiveId(Integer value) { objectiveId = value; }
    public ReferenceOperationStatus status() { return status; }
    public void status(ReferenceOperationStatus value) { status = Objects.requireNonNull(value, "status"); }
    public int stationDays() { return stationDays; }
    public void stationDays(int value) { stationDays = value; }
    public int unsuppliedDays() { return unsuppliedDays; }
    public void unsuppliedDays(int value) { unsuppliedDays = value; }
    public Set<Integer> detectedBy() { return Collections.unmodifiableSet(new LinkedHashSet<>(detectedBy)); }
    Set<Integer> mutableDetectedBy() { return detectedBy; }
    public Integer linkedSwarmId() { return linkedSwarmId; }
    public void linkedSwarmId(Integer value) { linkedSwarmId = value; }
    public String outcome() { return outcome; }
    public void outcome(String value) { outcome = value; }
    public boolean resolved() { return resolved; }
    public void resolved(boolean value) { resolved = value; }
    public Integer finishedDay() { return finishedDay; }
    public void finishedDay(Integer value) { finishedDay = value; }
    public int waypointIndex() { return waypointIndex; }
    public void waypointIndex(int value) { waypointIndex = value; }
    public ReferenceTargetRef origin() { return origin; }
    public void origin(ReferenceTargetRef value) { origin = value; }
    public Integer engagementId() { return engagementId; }
    public void engagementId(Integer value) { engagementId = value; }

    public Map<ReferenceResource, Double> supplies() { return immutableResources(supplies); }
    EnumMap<ReferenceResource, Double> mutableSupplies() { return supplies; }
    public Map<Integer, Map<ReferenceResource, Double>> contributors() { return immutableResourceMatrix(contributors); }
    void contributors(Map<Integer, ? extends Map<ReferenceResource, Double>> values) { copyResourceMatrix(values, contributors); }
    public Map<Integer, Double> personnelBySettlement() { return immutableOrdered(personnelBySettlement); }
    Map<Integer, Double> mutablePersonnelBySettlement() { return personnelBySettlement; }
    public Map<Integer, Map<ReferenceHumanUnitKind, Double>> unitCompositionBySettlement() { return immutableRoleMatrix(unitCompositionBySettlement); }
    Map<Integer, EnumMap<ReferenceHumanUnitKind, Double>> mutableUnitCompositionBySettlement() { return unitCompositionBySettlement; }
    public Map<ReferenceHumanUnitKind, Double> unitLosses() { return Collections.unmodifiableMap(new EnumMap<>(unitLosses)); }
    EnumMap<ReferenceHumanUnitKind, Double> mutableUnitLosses() { return unitLosses; }
    public Map<String, Object> details() { return Collections.unmodifiableMap(new LinkedHashMap<>(details)); }
    Map<String, Object> mutableDetails() { return details; }
    public List<ReferencePoint> waypoints() { return List.copyOf(waypoints); }
    void waypoints(List<ReferencePoint> values) { waypoints.clear(); waypoints.addAll(Objects.requireNonNull(values, "waypoints")); }
    public List<ReferencePoint> returnWaypoints() { return List.copyOf(returnWaypoints); }
    void returnWaypoints(List<ReferencePoint> values) { returnWaypoints.clear(); returnWaypoints.addAll(Objects.requireNonNull(values, "returnWaypoints")); }
    public Map<ReferenceResource, Double> cargo() { return immutableResources(cargo); }
    void cargo(Map<ReferenceResource, Double> values) { cargo.clear(); cargo.putAll(Objects.requireNonNull(values, "cargo")); }
    public Map<Integer, Double> evacuatedWoundedBySettlement() { return immutableOrdered(evacuatedWoundedBySettlement); }
    Map<Integer, Double> mutableEvacuatedWoundedBySettlement() { return evacuatedWoundedBySettlement; }
    public Map<Integer, List<String>> residentIdsBySettlement() { return immutableResidents(residentIdsBySettlement); }
    Map<Integer, List<String>> mutableResidentIdsBySettlement() { return residentIdsBySettlement; }
    public Map<Integer, List<String>> woundedResidentIdsBySettlement() { return immutableResidents(woundedResidentIdsBySettlement); }
    Map<Integer, List<String>> mutableWoundedResidentIdsBySettlement() { return woundedResidentIdsBySettlement; }

    void supplies(Map<ReferenceResource, Double> values) { supplies.clear(); supplies.putAll(Objects.requireNonNull(values, "supplies")); }
    void personnelBySettlement(Map<Integer, Double> values) { personnelBySettlement.clear(); personnelBySettlement.putAll(Objects.requireNonNull(values, "personnelBySettlement")); }
    void unitCompositionBySettlement(Map<Integer, ? extends Map<ReferenceHumanUnitKind, Double>> values) { copyRoleMatrix(values, unitCompositionBySettlement); }
    void residentIdsBySettlement(Map<Integer, List<String>> values) { copyResidents(values, residentIdsBySettlement); }
    void woundedResidentIdsBySettlement(Map<Integer, List<String>> values) { copyResidents(values, woundedResidentIdsBySettlement); }
    void details(Map<String, Object> values) { details.clear(); details.putAll(Objects.requireNonNull(values, "details")); }

    private static EnumMap<ReferenceResource, Double> emptyResources() {
        EnumMap<ReferenceResource, Double> result = new EnumMap<>(ReferenceResource.class);
        for (ReferenceResource resource : ReferenceResource.values()) result.put(resource, 0.0d);
        return result;
    }
    private static Map<ReferenceResource, Double> immutableResources(Map<ReferenceResource, Double> values) {
        return Collections.unmodifiableMap(new EnumMap<>(values));
    }
    private static <K, V> Map<K, V> immutableOrdered(Map<K, V> values) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }
    private static Map<Integer, Map<ReferenceResource, Double>> immutableResourceMatrix(Map<Integer, EnumMap<ReferenceResource, Double>> values) {
        Map<Integer, Map<ReferenceResource, Double>> result = new LinkedHashMap<>();
        values.forEach((id, value) -> result.put(id, immutableResources(value)));
        return Collections.unmodifiableMap(result);
    }
    private static Map<Integer, Map<ReferenceHumanUnitKind, Double>> immutableRoleMatrix(Map<Integer, EnumMap<ReferenceHumanUnitKind, Double>> values) {
        Map<Integer, Map<ReferenceHumanUnitKind, Double>> result = new LinkedHashMap<>();
        values.forEach((id, value) -> result.put(id, Collections.unmodifiableMap(new EnumMap<>(value))));
        return Collections.unmodifiableMap(result);
    }
    private static Map<Integer, List<String>> immutableResidents(Map<Integer, List<String>> values) {
        Map<Integer, List<String>> result = new LinkedHashMap<>();
        values.forEach((id, ids) -> result.put(id, List.copyOf(ids)));
        return Collections.unmodifiableMap(result);
    }
    private static void copyResourceMatrix(Map<Integer, ? extends Map<ReferenceResource, Double>> source,
                                           Map<Integer, EnumMap<ReferenceResource, Double>> destination) {
        destination.clear();
        source.forEach((id, values) -> { EnumMap<ReferenceResource, Double> copy = new EnumMap<>(ReferenceResource.class); copy.putAll(values); destination.put(id, copy); });
    }
    private static void copyRoleMatrix(Map<Integer, ? extends Map<ReferenceHumanUnitKind, Double>> source,
                                       Map<Integer, EnumMap<ReferenceHumanUnitKind, Double>> destination) {
        destination.clear();
        source.forEach((id, values) -> { EnumMap<ReferenceHumanUnitKind, Double> copy = new EnumMap<>(ReferenceHumanUnitKind.class); copy.putAll(values); destination.put(id, copy); });
    }
    private static void copyResidents(Map<Integer, List<String>> source, Map<Integer, List<String>> destination) {
        destination.clear();
        source.forEach((id, values) -> destination.put(id, new ArrayList<>(values)));
    }
}
