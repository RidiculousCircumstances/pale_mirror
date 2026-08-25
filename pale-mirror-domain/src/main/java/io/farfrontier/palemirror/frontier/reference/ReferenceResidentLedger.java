package io.farfrontier.palemirror.frontier.reference;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Exact individual-custody port of Python {@code ResidentLedger}.
 *
 * <p>For the discrete graybox profile every record is one person. Death
 * removes exactly that record; operations and migration retain the same
 * record identity. This class deliberately has no population multiplier.</p>
 */
public final class ReferenceResidentLedger {
    private static final List<String> OCCUPATIONS = List.of(
            "farmer", "miner", "forester", "power_worker", "workshop_worker", "armory_worker",
            "clinician", "guard", "scout", "engineer", "medic", "logistics");
    private static final List<String> ECONOMIC_CLASSES = List.of(
            "worker", "worker", "worker", "worker", "worker", "worker", "worker", "worker",
            "worker", "worker", "worker", "owner", "dependent", "dependent", "dependent",
            "dependent", "dependent", "dependent", "dependent", "dependent");
    private static final Comparator<String> ID_ORDER = Comparator.naturalOrder();

    private final int settlementId;
    private final Map<String, ReferenceResident> residents = new HashMap<>();
    private int nextOrdinal = 1;
    private long revision;

    public ReferenceResidentLedger(int settlementId, int initialPeople) {
        if (initialPeople < 0) {
            throw new IllegalArgumentException("initialPeople must be non-negative");
        }
        this.settlementId = settlementId;
        create(initialPeople);
    }

    public int settlementId() {
        return settlementId;
    }

    public int size() {
        return residents.size();
    }

    public int nextOrdinal() {
        return nextOrdinal;
    }

    public long revision() {
        return revision;
    }

    public ReferenceResident resident(String residentId) {
        return residents.get(residentId);
    }

    public List<String> create(int count) {
        if (count < 0) {
            throw new IllegalArgumentException("count must be non-negative");
        }
        List<String> created = new ArrayList<>(count);
        for (int ignored = 0; ignored < count; ignored++) {
            int ordinal = nextOrdinal++;
            String residentId = "resident:" + settlementId + ":" + ordinal;
            residents.put(residentId, new ReferenceResident(
                    residentId,
                    settlementId,
                    OCCUPATIONS.get((ordinal - 1) % OCCUPATIONS.size()),
                    ECONOMIC_CLASSES.get((ordinal - 1) % ECONOMIC_CLASSES.size())));
            created.add(residentId);
        }
        if (!created.isEmpty()) {
            revision++;
        }
        return List.copyOf(created);
    }

    public List<String> livingIds() {
        return sortedIds(ignored -> true);
    }

    public List<String> activeIds() {
        return sortedIds(resident -> resident.condition() == ReferenceResidentCondition.ACTIVE);
    }

    public List<String> idsAt(
            ReferenceResidentLocation location,
            ReferenceResidentCondition condition
    ) {
        Objects.requireNonNull(location, "location");
        return sortedIds(resident -> resident.location() == location
                && (condition == null || resident.condition() == condition));
    }

    public List<String> availableIds() {
        return sortedIds(ReferenceResident::available);
    }

    public List<String> woundedIds() {
        return sortedIds(resident -> resident.condition() == ReferenceResidentCondition.WOUNDED);
    }

    public List<String> workerIds() {
        return sortedIds(resident -> resident.available() && resident.economicClass().equals("worker"));
    }

    public void assignEmployment(Integer companyId, Iterable<String> residentIds) {
        List<String> selected = materialize(residentIds);
        for (String residentId : selected) {
            ReferenceResident resident = residents.get(residentId);
            if (resident == null || !resident.available() || !resident.economicClass().equals("worker")) {
                throw new IllegalArgumentException("resident " + quoted(residentId) + " is not an available worker");
            }
        }
        for (ReferenceResident resident : residents.values()) {
            if (Objects.equals(resident.employerCompanyId(), companyId)) {
                resident.assignEmployer(null);
            }
        }
        for (String residentId : selected) {
            residents.get(residentId).assignEmployer(companyId);
        }
        revision++;
    }

    /**
     * Apply the market-day allocation using Python's one-revision-per-company
     * semantics. The final assignments are simultaneous in effect, but the
     * persisted ledger revision must still match the source's repeated
     * {@code assign_employment} calls and its initial employer-clear pass so
     * physical observations cannot acquire a Java-only revision history.
     */
    void replaceEmployment(Map<Integer, List<String>> assignments) {
        Objects.requireNonNull(assignments, "assignments");
        for (List<String> residentIds : assignments.values()) {
            for (String residentId : residentIds) {
                ReferenceResident resident = residents.get(residentId);
                if (resident == null || !resident.available() || !resident.economicClass().equals("worker")) {
                    throw new IllegalArgumentException("resident " + quoted(residentId) + " is not an available worker");
                }
            }
        }
        for (ReferenceResident resident : residents.values()) resident.assignEmployer(null);
        for (Map.Entry<Integer, List<String>> entry : assignments.entrySet()) {
            for (String residentId : entry.getValue()) residents.get(residentId).assignEmployer(entry.getKey());
        }
        revision += assignments.size() + 1L;
    }

    public List<String> killExpected(double expected, PythonRandom rng) {
        return killExpectedFrom(residents.keySet(), expected, rng);
    }

    public List<String> killExpectedFrom(Iterable<String> candidates, double expected, PythonRandom rng) {
        List<String> selected = sampleExpected(existingCandidates(candidates), expected, rng);
        for (String residentId : selected) {
            residents.remove(residentId);
        }
        if (!selected.isEmpty()) {
            revision++;
        }
        return selected;
    }

    /** Exact materialized death; unlike an expected casualty it never advances the demographic stream. */
    public boolean killExact(String residentId) {
        if (residents.remove(Objects.requireNonNull(residentId, "residentId")) == null) {
            return false;
        }
        revision++;
        return true;
    }

    public List<String> woundExpected(double expected, PythonRandom rng) {
        return woundExpectedFrom(activeIds(), expected, rng);
    }

    public List<String> woundExpectedFrom(Iterable<String> candidates, double expected, PythonRandom rng) {
        List<String> selected = sampleExpected(filterCandidates(
                candidates,
                resident -> resident.condition() == ReferenceResidentCondition.ACTIVE), expected, rng);
        for (String residentId : selected) {
            residents.get(residentId).wound();
        }
        if (!selected.isEmpty()) {
            revision++;
        }
        return selected;
    }

    /** Exact materialized wound; only an active person may become wounded. */
    public boolean woundExact(String residentId) {
        ReferenceResident resident = residents.get(Objects.requireNonNull(residentId, "residentId"));
        if (resident == null || resident.condition() != ReferenceResidentCondition.ACTIVE) {
            return false;
        }
        resident.wound();
        revision++;
        return true;
    }

    public List<String> recoverExpected(double expected, PythonRandom rng) {
        return recoverExpectedFrom(woundedIds(), expected, rng);
    }

    public List<String> recoverExpectedFrom(Iterable<String> candidates, double expected, PythonRandom rng) {
        List<String> selected = sampleExpected(filterCandidates(
                candidates,
                resident -> resident.condition() == ReferenceResidentCondition.WOUNDED), expected, rng);
        for (String residentId : selected) {
            residents.get(residentId).recover();
        }
        if (!selected.isEmpty()) {
            revision++;
        }
        return selected;
    }

    public void deploy(Iterable<String> residentIds, int operationId, Map<String, String> roles) {
        List<String> selected = materialize(residentIds);
        for (String residentId : selected) {
            if (!available(residentId)) {
                throw new IllegalArgumentException("resident " + quoted(residentId)
                        + " is not available for operation " + operationId);
            }
        }
        for (String residentId : selected) {
            residents.get(residentId).moveToOperation(operationId, roles.get(residentId));
        }
        incrementIfChanged(selected);
    }

    public void deployFromFieldPost(
            Iterable<String> residentIds,
            int postId,
            int operationId,
            Map<String, String> roles
    ) {
        List<String> selected = materialize(residentIds);
        for (String residentId : selected) {
            ReferenceResident resident = residents.get(residentId);
            if (resident == null
                    || resident.condition() != ReferenceResidentCondition.ACTIVE
                    || resident.location() != ReferenceResidentLocation.FIELD_POST
                    || !Objects.equals(resident.locationRef(), postId)) {
                throw new IllegalArgumentException("resident " + quoted(residentId)
                        + " is not at field post " + postId);
            }
        }
        for (String residentId : selected) {
            residents.get(residentId).moveToOperation(operationId, roles.get(residentId));
        }
        incrementIfChanged(selected);
    }

    public void returnHome(Iterable<String> residentIds) {
        boolean changed = false;
        for (String residentId : residentIds) {
            ReferenceResident resident = residents.get(residentId);
            if (resident != null) {
                resident.moveToSettlement();
                changed = true;
            }
        }
        if (changed) {
            revision++;
        }
    }

    public void assignFieldPost(Iterable<String> residentIds, int postId) {
        boolean changed = false;
        for (String residentId : residentIds) {
            ReferenceResident resident = residents.get(residentId);
            if (resident != null) {
                resident.moveToFieldPost(postId);
                changed = true;
            }
        }
        if (changed) {
            revision++;
        }
    }

    public void transferWoundedFromFieldPost(
            Iterable<String> residentIds,
            int postId,
            int operationId
    ) {
        List<String> selected = materialize(residentIds);
        for (String residentId : selected) {
            ReferenceResident resident = residents.get(residentId);
            if (resident == null
                    || resident.condition() != ReferenceResidentCondition.WOUNDED
                    || resident.location() != ReferenceResidentLocation.FIELD_POST
                    || !Objects.equals(resident.locationRef(), postId)) {
                throw new IllegalArgumentException("resident " + quoted(residentId)
                        + " is not a wounded patient at field post " + postId);
            }
        }
        for (String residentId : selected) {
            residents.get(residentId).moveWoundedToOperation(operationId);
        }
        incrementIfChanged(selected);
    }

    public List<ReferenceResident> departExpected(double expected, PythonRandom rng) {
        List<String> selected = sampleExpected(availableIds(), expected, rng);
        List<ReferenceResident> travellers = new ArrayList<>(selected.size());
        for (String residentId : selected) {
            ReferenceResident resident = residents.remove(residentId);
            resident.assignEmployer(null);
            resident.clearDeploymentRole();
            travellers.add(resident);
        }
        if (!travellers.isEmpty()) {
            revision++;
        }
        return List.copyOf(travellers);
    }

    public List<String> acceptTransferred(Iterable<ReferenceResident> incoming) {
        List<String> accepted = new ArrayList<>();
        for (ReferenceResident resident : incoming) {
            if (residents.containsKey(resident.id())) {
                throw new IllegalArgumentException("resident " + quoted(resident.id()) + " is already present");
            }
            resident.acceptIntoSettlement(settlementId);
            residents.put(resident.id(), resident);
            accepted.add(resident.id());
        }
        if (!accepted.isEmpty()) {
            revision++;
        }
        return List.copyOf(accepted);
    }

    public void assertValid() {
        for (Map.Entry<String, ReferenceResident> entry : residents.entrySet()) {
            String residentId = entry.getKey();
            ReferenceResident resident = entry.getValue();
            if (!resident.id().equals(residentId)) {
                throw new AssertionError("resident key mismatch for " + residentId);
            }
            if (resident.homeSettlementId() != settlementId) {
                throw new AssertionError("resident " + residentId + " belongs to another settlement");
            }
            if (resident.condition() == ReferenceResidentCondition.WOUNDED
                    && resident.deploymentRole() != null) {
                throw new AssertionError("wounded resident " + residentId + " still has a deployment role");
            }
            if (!List.of("worker", "owner", "dependent").contains(resident.economicClass())) {
                throw new AssertionError("resident " + residentId + " has unknown economic class");
            }
        }
    }

    private List<String> existingCandidates(Iterable<String> candidates) {
        return filterCandidates(candidates, ignored -> true);
    }

    private List<String> filterCandidates(Iterable<String> candidates, Predicate<ReferenceResident> condition) {
        List<String> filtered = new ArrayList<>();
        for (String residentId : candidates) {
            ReferenceResident resident = residents.get(residentId);
            if (resident != null && condition.test(resident)) {
                filtered.add(residentId);
            }
        }
        return filtered;
    }

    private List<String> sortedIds(Predicate<ReferenceResident> condition) {
        return residents.entrySet().stream()
                .filter(entry -> condition.test(entry.getValue()))
                .map(Map.Entry::getKey)
                .sorted(ID_ORDER)
                .toList();
    }

    private static List<String> sampleExpected(Collection<String> candidates, double expected, PythonRandom rng) {
        Objects.requireNonNull(rng, "rng");
        List<String> sorted = candidates.stream().sorted(ID_ORDER).toList();
        if (expected <= 0.0d || sorted.isEmpty()) {
            return List.of();
        }
        double quotient = expected / sorted.size();
        double probability = quotient < 1.0d ? quotient : 1.0d;
        return sorted.stream().filter(ignored -> rng.random() < probability).toList();
    }

    private static List<String> materialize(Iterable<String> residentIds) {
        List<String> selected = new ArrayList<>();
        residentIds.forEach(selected::add);
        return selected;
    }

    private boolean available(String residentId) {
        ReferenceResident resident = residents.get(residentId);
        return resident != null && resident.available();
    }

    private void incrementIfChanged(Collection<String> selected) {
        if (!selected.isEmpty()) {
            revision++;
        }
    }

    private static String quoted(String value) {
        return "'" + value + "'";
    }
}
