package io.farfrontier.palemirror.frontier.reference;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Mutable source port of Python {@code FieldPost}; {@link ReferenceFieldWarfare} owns every instance. */
public final class ReferenceFieldPost {
    private final int id;
    private final ReferenceFieldPostKind kind;
    private final int x;
    private final int y;
    private final int campaignId;
    private final int leaderId;
    private final Set<Integer> contributors;
    private final Map<ReferenceResource, Double> stock = emptyStock();
    private final Map<Integer, Double> garrisonBySettlement = new LinkedHashMap<>();
    private final Map<Integer, Double> woundedBySettlement = new LinkedHashMap<>();
    private final Map<Integer, List<String>> residentIdsBySettlement = new LinkedHashMap<>();
    private final Map<Integer, List<String>> woundedResidentIdsBySettlement = new LinkedHashMap<>();
    private final Set<ReferenceFieldModuleKind> modules = new LinkedHashSet<>();
    private final Map<ReferenceFieldModuleKind, Integer> moduleProjects = new LinkedHashMap<>();
    private final double cargoScale;
    private final int createdDay;
    private double integrity;
    private int buildDaysRemaining;
    private ReferenceFieldPostStatus status = ReferenceFieldPostStatus.BUILDING;
    private int isolationDays;
    private int lastSuppliedDay;
    private Integer destroyedDay;

    ReferenceFieldPost(int id, ReferenceFieldPostKind kind, int x, int y, int campaignId, int leaderId,
                       Set<Integer> contributors, Map<Integer, Double> garrisonBySettlement,
                       Map<Integer, List<String>> residentIdsBySettlement, double cargoScale,
                       int lastSuppliedDay, int createdDay) {
        this.id = id;
        this.kind = Objects.requireNonNull(kind, "kind");
        this.x = x;
        this.y = y;
        this.campaignId = campaignId;
        this.leaderId = leaderId;
        this.contributors = Collections.unmodifiableSet(new LinkedHashSet<>(Objects.requireNonNull(contributors, "contributors")));
        this.integrity = ReferenceFieldRules.postIntegrity(kind);
        this.buildDaysRemaining = ReferenceFieldRules.postBuildDays(kind);
        this.garrisonBySettlement.putAll(Objects.requireNonNull(garrisonBySettlement, "garrisonBySettlement"));
        copyResidents(Objects.requireNonNull(residentIdsBySettlement, "residentIdsBySettlement"), this.residentIdsBySettlement);
        if (!Double.isFinite(cargoScale) || cargoScale <= 0.0d) throw new IllegalArgumentException("cargoScale must be positive and finite");
        this.cargoScale = cargoScale;
        this.lastSuppliedDay = lastSuppliedDay;
        this.createdDay = createdDay;
    }

    public int id() { return id; }
    public ReferenceFieldPostKind kind() { return kind; }
    public int x() { return x; }
    public int y() { return y; }
    public int campaignId() { return campaignId; }
    public int leaderId() { return leaderId; }
    public Set<Integer> contributors() { return contributors; }
    public double integrity() { return integrity; }
    void integrity(double value) { integrity = value; }
    public int buildDaysRemaining() { return buildDaysRemaining; }
    void buildDaysRemaining(int value) { buildDaysRemaining = value; }
    public ReferenceFieldPostStatus status() { return status; }
    void status(ReferenceFieldPostStatus value) { status = Objects.requireNonNull(value, "status"); }
    public Set<ReferenceFieldModuleKind> modules() { return Collections.unmodifiableSet(new LinkedHashSet<>(modules)); }
    Set<ReferenceFieldModuleKind> mutableModules() { return modules; }
    public Map<ReferenceFieldModuleKind, Integer> moduleProjects() { return immutableOrdered(moduleProjects); }
    Map<ReferenceFieldModuleKind, Integer> mutableModuleProjects() { return moduleProjects; }
    public Map<ReferenceResource, Double> stock() { return Collections.unmodifiableMap(new EnumMap<>(stock)); }
    public double stock(ReferenceResource resource) { return stock.get(Objects.requireNonNull(resource, "resource")); }
    void stock(ReferenceResource resource, double value) { stock.put(Objects.requireNonNull(resource, "resource"), value); }
    public Map<Integer, Double> garrisonBySettlement() { return immutableOrdered(garrisonBySettlement); }
    Map<Integer, Double> mutableGarrisonBySettlement() { return garrisonBySettlement; }
    public Map<Integer, Double> woundedBySettlement() { return immutableOrdered(woundedBySettlement); }
    Map<Integer, Double> mutableWoundedBySettlement() { return woundedBySettlement; }
    public Map<Integer, List<String>> residentIdsBySettlement() { return immutableResidents(residentIdsBySettlement); }
    Map<Integer, List<String>> mutableResidentIdsBySettlement() { return residentIdsBySettlement; }
    public Map<Integer, List<String>> woundedResidentIdsBySettlement() { return immutableResidents(woundedResidentIdsBySettlement); }
    Map<Integer, List<String>> mutableWoundedResidentIdsBySettlement() { return woundedResidentIdsBySettlement; }
    public double cargoScale() { return cargoScale; }
    public int isolationDays() { return isolationDays; }
    void isolationDays(int value) { isolationDays = value; }
    public int lastSuppliedDay() { return lastSuppliedDay; }
    void lastSuppliedDay(int value) { lastSuppliedDay = value; }
    public int createdDay() { return createdDay; }
    public Integer destroyedDay() { return destroyedDay; }
    void destroyedDay(Integer value) { destroyedDay = value; }

    public double garrison() { return garrisonBySettlement.values().stream().mapToDouble(Double::doubleValue).sum(); }
    public double wounded() { return woundedBySettlement.values().stream().mapToDouble(Double::doubleValue).sum(); }
    public boolean hasModule(ReferenceFieldModuleKind module) { return modules.contains(Objects.requireNonNull(module, "module")); }
    public double storageCapacity() {
        double capacity = ReferenceFieldRules.storageCapacity(kind) / cargoScale;
        return hasModule(ReferenceFieldModuleKind.DEPOT) ? capacity * ReferenceFieldRules.depotStorageMultiplier() : capacity;
    }
    public double storedVolume() {
        return stock.entrySet().stream().mapToDouble(entry -> Math.max(0.0d, entry.getValue())
                * ReferenceFieldRules.storageVolume(entry.getKey())).sum();
    }
    public double freeStorage() { return Math.max(0.0d, storageCapacity() - storedVolume()); }

    /** Python priority-order cargo acceptance; omitted resources remain unaccepted. */
    public Map<ReferenceResource, Double> receiveCargo(Map<ReferenceResource, Double> cargo) {
        Map<ReferenceResource, Double> required = Objects.requireNonNull(cargo, "cargo");
        Map<ReferenceResource, Double> accepted = new LinkedHashMap<>();
        double remaining = freeStorage();
        for (ReferenceResource resource : CARGO_PRIORITY) {
            double amount = required.getOrDefault(resource, 0.0d);
            double unitVolume = ReferenceFieldRules.storageVolume(resource);
            double delivered = Math.min(Math.max(0.0d, amount), remaining / Math.max(1.0e-9d, unitVolume));
            if (delivered <= 0.0d) continue;
            stock(resource, stock(resource) + delivered);
            accepted.put(resource, delivered);
            remaining -= delivered * unitVolume;
        }
        return Collections.unmodifiableMap(accepted);
    }

    private static final ReferenceResource[] CARGO_PRIORITY = {
            ReferenceResource.MEDICINE, ReferenceResource.FOOD, ReferenceResource.AMMO, ReferenceResource.WEAPONS,
            ReferenceResource.ENERGY, ReferenceResource.TOOLS, ReferenceResource.ORE, ReferenceResource.TIMBER, ReferenceResource.SEEDS
    };

    private static Map<ReferenceResource, Double> emptyStock() {
        EnumMap<ReferenceResource, Double> result = new EnumMap<>(ReferenceResource.class);
        for (ReferenceResource resource : ReferenceResource.values()) result.put(resource, 0.0d);
        return result;
    }

    private static <K, V> Map<K, V> immutableOrdered(Map<K, V> values) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    private static Map<Integer, List<String>> immutableResidents(Map<Integer, List<String>> values) {
        Map<Integer, List<String>> result = new LinkedHashMap<>();
        for (Map.Entry<Integer, List<String>> entry : values.entrySet()) result.put(entry.getKey(), List.copyOf(entry.getValue()));
        return Collections.unmodifiableMap(result);
    }

    private static void copyResidents(Map<Integer, List<String>> source, Map<Integer, List<String>> destination) {
        for (Map.Entry<Integer, List<String>> entry : source.entrySet()) destination.put(entry.getKey(), new ArrayList<>(entry.getValue()));
    }
}
