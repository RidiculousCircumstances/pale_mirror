package io.farfrontier.palemirror.frontier.reference;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Canonical settlement owner ported from Python {@code Settlement}.
 *
 * <p>The source profile retains continuous arithmetic. The graybox profile
 * delegates survival, wounds, custody and growth to its individual ledger and
 * projects its count back into {@code population}; it never keeps a cohort.
 * Economy/trade phase methods are added by their owning Wave 1 ports.</p>
 */
public final class ReferenceSettlement {
    private static final double INTEGRITY_INITIAL = 100.0d;
    private static final int LAST_INVESTMENT_DAY = -10_000;
    private static final double FOOD_SHORTAGE_LOSS = 0.006d;
    private static final double FOOD_SHORTAGE_INTEGRITY_LOSS = 0.12d;
    private static final double DAILY_GROWTH = 0.00015d;
    private static final double GROWTH_THREAT = 0.25d;
    private static final double GROWTH_INTEGRITY = 65.0d;
    private static final double COLLAPSE_POPULATION = 40.0d;
    private static final double SAFE_THREAT = 0.02d;
    private static final double HEALTH_PENALTY = 0.25d;
    private static final double ILLNESS_GAIN_NO_MEDICINE = 0.005d;
    private static final double ILLNESS_GAIN_EXPOSURE = 0.12d;
    private static final double ILLNESS_RECOVERY = 0.012d;
    private static final double ILLNESS_MORTALITY_BASE = 0.0003d;
    private static final double ILLNESS_MORTALITY_EXPOSURE = 0.0027d;
    private static final double ILLNESS_INTEGRITY_LOSS = 0.04d;
    private static final double GROWTH_BURDEN = 0.02d;
    private static final double CLINIC_RECOVERY = 0.55d;
    private static final double UNTREATED_WOUND_MORTALITY = 0.004d;
    private final int id;
    private final String name;
    private final int x;
    private final int y;
    private final ReferenceNaturalPotential natural;
    private final ReferenceFacilities facilities;
    private final ReferenceSimulationProfile profile;
    private final EnumMap<ReferenceResource, Double> stock = emptyStock();
    private final EnumMap<ReferenceResource, Double> dailyProduction = emptyStock();
    private final EnumMap<ReferenceResource, Double> dailyConsumption = emptyStock();
    private final Map<String, Double> primaryCapacity = emptyPrimaryCapacity();
    private PythonRandom populationRng;
    private ReferenceResidentLedger residents;
    private double population;
    private double cash;
    private String doctrine = "trade";
    private boolean alive = true;
    private double integrity = INTEGRITY_INITIAL;
    private double threat;
    private double foodFulfillment = 1.0d;
    private double medicineFulfillment = 1.0d;
    private double illnessBurden;
    private int lastInvestmentDay = LAST_INVESTMENT_DAY;
    private int lastStrategyDay = LAST_INVESTMENT_DAY;
    private double mobilizedPersonnel;
    private double woundedPersonnel;
    public ReferenceSettlement(
            int id,
            String name,
            int x,
            int y,
            double population,
            double cash,
            ReferenceNaturalPotential natural,
            ReferenceFacilities facilities,
            ReferenceSimulationProfile profile
    ) {
        this.id = id;
        this.name = Objects.requireNonNull(name, "name");
        this.x = x;
        this.y = y;
        this.population = population;
        this.cash = cash;
        this.natural = Objects.requireNonNull(natural, "natural");
        this.facilities = Objects.requireNonNull(facilities, "facilities");
        this.profile = Objects.requireNonNull(profile, "profile");
        if (profile.discretePeople()) {
            residents = new ReferenceResidentLedger(id, profile.individualPeopleFromSource(population));
            syncResidentProjection();
        }
    }
    public int id() { return id; }
    public String name() { return name; }
    public int x() { return x; }
    public int y() { return y; }
    public double population() { return population; } void population(double value) { population = Math.max(0.0d, value); }
    public double cash() { return cash; }
    public void cash(double value) { cash = value; }
    public ReferenceNaturalPotential natural() { return natural; }
    public ReferenceFacilities facilities() { return facilities; }
    public ReferenceSimulationProfile profile() { return profile; }
    public boolean alive() { return alive; }
    public void alive(boolean value) { alive = value; }
    public double integrity() { return integrity; }
    public void integrity(double value) { integrity = value; }
    public double threat() { return threat; }
    public void threat(double value) { threat = value; }
    public double foodFulfillment() { return foodFulfillment; }
    public void foodFulfillment(double value) { foodFulfillment = value; }
    public double medicineFulfillment() { return medicineFulfillment; }
    public void medicineFulfillment(double value) { medicineFulfillment = value; }
    public double illnessBurden() { return illnessBurden; }
    public void illnessBurden(double value) { illnessBurden = value; }
    public double mobilizedPersonnel() { return mobilizedPersonnel; }
    /** Operation owner mutation; public read-model consumers cannot alter mobilisation. */
    void mobilizedPersonnel(double value) { mobilizedPersonnel = Math.max(0.0d, value); }
    public double woundedPersonnel() { return woundedPersonnel; } void woundedPersonnel(double value) { woundedPersonnel = Math.max(0.0d, value); }
    public boolean discretePeople() { return residents != null; }
    public ReferenceResidentLedger residents() { return residents; }
    /** World-only named stream; standalone settlements retain their identity-derived fallback. */
    void populationRng(PythonRandom value) { populationRng = Objects.requireNonNull(value, "populationRng"); }
    public String doctrine() { return doctrine; }
    public void doctrine(String value) { doctrine = Objects.requireNonNull(value, "doctrine"); }
    public int lastInvestmentDay() { return lastInvestmentDay; }
    public void lastInvestmentDay(int value) { lastInvestmentDay = value; }
    public int lastStrategyDay() { return lastStrategyDay; }
    public void lastStrategyDay(int value) { lastStrategyDay = value; }
    public double amount(ReferenceResource resource) {
        return stock.get(Objects.requireNonNull(resource, "resource"));
    }
    public void add(ReferenceResource resource, double quantity) {
        if (quantity > 0.0d) {
            stock.merge(Objects.requireNonNull(resource, "resource"), quantity, Double::sum);
        }
    }
    public double remove(ReferenceResource resource, double quantity) {
        ReferenceResource required = Objects.requireNonNull(resource, "resource");
        if (quantity <= 0.0d) {
            return 0.0d;
        }
        double actual = Math.min(amount(required), quantity);
        stock.put(required, amount(required) - actual);
        return actual;
    }
    public Map<ReferenceResource, Double> stock() { return Map.copyOf(stock); }
    public Map<ReferenceResource, Double> dailyProduction() { return Map.copyOf(dailyProduction); }
    public Map<ReferenceResource, Double> dailyConsumption() { return Map.copyOf(dailyConsumption); }
    void resetDailyFlows() {
        for (ReferenceResource resource : ReferenceResource.values()) {
            dailyProduction.put(resource, 0.0d);
            dailyConsumption.put(resource, 0.0d);
        }
    }
    void recordProduction(ReferenceResource resource, double amount) {
        dailyProduction.merge(resource, amount, Double::sum);
    }
    void recordConsumption(ReferenceResource resource, double amount) {
        dailyConsumption.merge(resource, amount, Double::sum);
    }
    public double primaryCapacity(String kind) { return primaryCapacity.getOrDefault(kind, 0.0d); }
    public void primaryCapacity(String kind, double capacity) { primaryCapacity.put(kind, capacity); }
    void replaceStock(Map<ReferenceResource, Double> replacement) {
        Objects.requireNonNull(replacement, "replacement");
        for (ReferenceResource resource : ReferenceResource.values()) {
            stock.put(resource, Math.max(0.0d, replacement.getOrDefault(resource, 0.0d)));
        }
    }
    public boolean canPay(double amount) { return cash + 1.0e-9d >= amount; }
    public double treasury() { return cash; }
    public void treasury(double value) { cash = value; }
    public double removePeople(double expected, String cause) {
        double bounded = nonNegative(expected);
        if (residents == null) {
            double removed = Math.min(population, bounded);
            population = Math.max(0.0d, population - removed);
            return removed;
        }
        List<String> removed = residents.killExpectedFrom(
                residents.idsAt(ReferenceResidentLocation.SETTLEMENT, null), bounded, residentRng());
        syncResidentProjection();
        return removed.size();
    }
    public double removeExposedPeople(Iterable<String> ids, double expected, String cause) {
        if (residents == null) {
            return removePeople(expected, cause);
        }
        List<String> removed = residents.killExpectedFrom(ids, nonNegative(expected), residentRng());
        syncResidentProjection();
        return removed.size();
    }
    public Map<String, List<String>> deployPeople(int operationId, Map<String, Integer> composition) {
        if (residents == null) {
            return Map.of();
        }
        List<String> available = residents.availableIds();
        return deployFromCandidates(available, operationId, composition, null);
    }
    public Map<String, List<String>> deployPeopleFromFieldPost(
            int operationId,
            int postId,
            Map<String, Integer> composition,
            Iterable<String> fieldPostResidentIds
    ) {
        if (residents == null) {
            return Map.of();
        }
        List<String> candidates = new ArrayList<>();
        for (String residentId : fieldPostResidentIds) {
            ReferenceResident resident = residents.resident(residentId);
            if (resident != null
                    && resident.condition() == ReferenceResidentCondition.ACTIVE
                    && resident.location() == ReferenceResidentLocation.FIELD_POST
                    && Objects.equals(resident.locationRef(), postId)) {
                candidates.add(residentId);
            }
        }
        candidates.sort(String::compareTo);
        return deployFromCandidates(candidates, operationId, composition, postId);
    }
    public void returnPeopleHome(Iterable<String> ids) {
        if (residents != null) {
            residents.returnHome(ids);
            syncResidentProjection();
        }
    }
    public void assignPeopleToFieldPost(Iterable<String> ids, int postId) {
        if (residents != null) {
            residents.assignFieldPost(ids, postId);
            syncResidentProjection();
        }
    }
    public void evacuateWoundedFromFieldPost(Iterable<String> ids, int postId, int operationId) {
        if (residents != null) {
            residents.transferWoundedFromFieldPost(ids, postId, operationId);
            syncResidentProjection();
        }
    }
    public List<ReferenceResident> departPeople(double expected, String cause) {
        if (residents == null) {
            removePeople(expected, cause);
            return List.of();
        }
        List<ReferenceResident> departed = residents.departExpected(expected, residentRng());
        syncResidentProjection();
        return departed;
    }
    public double acceptPeople(Iterable<ReferenceResident> incoming, double expected, String cause) {
        if (residents == null) {
            return addPeople(expected, cause);
        }
        List<String> accepted = residents.acceptTransferred(incoming);
        syncResidentProjection();
        return accepted.size();
    }
    public double addPeople(double expected, String cause) {
        double bounded = nonNegative(expected);
        if (residents == null) {
            population += bounded;
            return bounded;
        }
        int whole = (int) bounded;
        double remainder = bounded - whole;
        int created = whole + (residentRng().random() < remainder ? 1 : 0);
        residents.create(created);
        syncResidentProjection();
        return created;
    }
    public double woundPeople(double expected, String cause) {
        if (residents == null) {
            double actual = Math.min(nonNegative(expected), Math.max(0.0d, population - woundedPersonnel));
            woundedPersonnel += actual;
            return actual;
        }
        List<String> wounded = residents.woundExpected(expected, residentRng());
        syncResidentProjection();
        return wounded.size();
    }
    public double woundExposedPeople(Iterable<String> ids, double expected, String cause) {
        if (residents == null) {
            return woundPeople(expected, cause);
        }
        List<String> wounded = residents.woundExpectedFrom(ids, nonNegative(expected), residentRng());
        syncResidentProjection();
        return wounded.size();
    }
    public ReferenceCasualtyResult applyExposedCasualties(
            Iterable<String> ids,
            double killedExpected,
            double woundedExpected,
            String cause
    ) {
        List<String> exposed = materialize(ids);
        if (residents == null) {
            removePeople(killedExpected, cause);
            woundPeople(woundedExpected, cause);
            return new ReferenceCasualtyResult(List.of(), List.of());
        }
        List<String> killed = residents.killExpectedFrom(exposed, nonNegative(killedExpected), residentRng());
        List<String> surviving = exposed.stream().filter(id -> !killed.contains(id)).toList();
        List<String> wounded = residents.woundExpectedFrom(surviving, nonNegative(woundedExpected), residentRng());
        syncResidentProjection();
        return new ReferenceCasualtyResult(killed, wounded);
    }
    public double recoverLocalWoundedPeople(double expected, String cause) {
        if (residents == null) {
            return recoverWoundedPeople(expected, cause);
        }
        List<String> recovered = residents.recoverExpectedFrom(
                residents.idsAt(ReferenceResidentLocation.SETTLEMENT, ReferenceResidentCondition.WOUNDED),
                nonNegative(expected), residentRng());
        syncResidentProjection();
        return recovered.size();
    }
    public double recoverWoundedPeople(double expected, String cause) {
        if (residents == null) {
            double actual = Math.min(nonNegative(expected), woundedPersonnel);
            woundedPersonnel -= actual;
            return actual;
        }
        List<String> recovered = residents.recoverExpected(expected, residentRng());
        syncResidentProjection();
        return recovered.size();
    }
    public List<String> recoverExposedWoundedPeople(Iterable<String> ids, double expected, String cause) {
        if (residents == null) {
            recoverWoundedPeople(expected, cause);
            return List.of();
        }
        List<String> recovered = residents.recoverExpectedFrom(ids, nonNegative(expected), residentRng());
        syncResidentProjection();
        return recovered;
    }
    public double applyPopulationLossFraction(double fraction, String cause) {
        return removePeople(Math.min(1.0d, Math.max(0.0d, fraction)) * population, cause);
    }
    public double laborFactor() {
        double healthFactor = 1.0d - illnessBurden * HEALTH_PENALTY;
        double available = Math.max(0.0d, population - mobilizedPersonnel - woundedPersonnel);
        double supply = Math.max(1.0d, available * 0.34d);
        double demand = facilities.workshop() * 30.0d + facilities.armory() * 34.0d + facilities.clinic() * 24.0d;
        return demand <= 0.0d ? 1.0d : Math.min(1.0d, supply / demand) * healthFactor;
    }
    public String specialization() {
        String specialization = "agricultural";
        double best = primaryCapacity("farm");
        if (primaryCapacity("mine") > best) {
            specialization = "mining";
            best = primaryCapacity("mine");
        }
        if (primaryCapacity("forest") > best) {
            specialization = "forestry";
            best = primaryCapacity("forest");
        }
        if (primaryCapacity("power") > best) {
            specialization = "energy";
            best = primaryCapacity("power");
        }
        if (facilities.workshop() + facilities.armory() > best) {
            specialization = "industrial";
        }
        return specialization;
    }
    public double defenceStrength() {
        return ReferenceSettlementCombat.defenceStrength(this);
    }
    public double combatDefence(double power) {
        return ReferenceSettlementCombat.combatDefence(this, power);
    }
    public ReferenceSwarmAttackResolution resolveSwarmAttack(
            double power,
            double externalDefence,
            double structuralBreach,
            double personnelPressure,
            double medicProtection
    ) {
        return ReferenceSettlementCombat.resolve(
                this, power, externalDefence, structuralBreach, personnelPressure, medicProtection);
    }
    public double infectionExposure() {
        return Math.max(0.0d, (threat - SAFE_THREAT) / (1.0d - SAFE_THREAT));
    }
    public void updateHealth() {
        double shortage = 1.0d - medicineFulfillment;
        double gain = shortage * (ILLNESS_GAIN_NO_MEDICINE + ILLNESS_GAIN_EXPOSURE * infectionExposure());
        double recovery = medicineFulfillment * ILLNESS_RECOVERY;
        illnessBurden = Math.min(1.0d, Math.max(0.0d, illnessBurden + gain - recovery));
    }
    public void endOfDayDemography() {
        if (!alive) {
            return;
        }
        if (foodFulfillment < 1.0d) {
            double shortage = 1.0d - foodFulfillment;
            applyPopulationLossFraction(FOOD_SHORTAGE_LOSS * shortage, "food_shortage");
            integrity = Math.max(0.0d, integrity - FOOD_SHORTAGE_INTEGRITY_LOSS * shortage);
        }
        updateHealth();
        double mortality = illnessBurden * (ILLNESS_MORTALITY_BASE + ILLNESS_MORTALITY_EXPOSURE * infectionExposure());
        applyPopulationLossFraction(mortality, "disease");
        integrity = Math.max(0.0d, integrity - illnessBurden * ILLNESS_INTEGRITY_LOSS);
        double localWounded = residents == null
                ? woundedPersonnel
                : residents.idsAt(ReferenceResidentLocation.SETTLEMENT, ReferenceResidentCondition.WOUNDED).size();
        if (localWounded > 0.0d) {
            double treated = Math.min(localWounded, facilities.clinic() * CLINIC_RECOVERY * medicineFulfillment);
            double recovered = recoverLocalWoundedPeople(treated, "clinic_treatment");
            double untreated = Math.max(0.0d, localWounded - (residents == null ? treated : recovered));
            double deaths = untreated * UNTREATED_WOUND_MORTALITY;
            if (residents == null) {
                woundedPersonnel = Math.max(0.0d, woundedPersonnel - deaths);
                removePeople(deaths, "untreated_wounds");
            } else {
                removeExposedPeople(
                        residents.idsAt(ReferenceResidentLocation.SETTLEMENT, ReferenceResidentCondition.WOUNDED),
                        deaths,
                        "untreated_wounds");
            }
        }
        if (foodFulfillment >= 1.0d && threat < GROWTH_THREAT && integrity > GROWTH_INTEGRITY && illnessBurden < GROWTH_BURDEN) {
            addPeople(population * DAILY_GROWTH, "natural_growth");
        }
        if (population < profile.collapsePopulationFromSource(COLLAPSE_POPULATION) || integrity <= 0.0d) {
            alive = false;
        }
    }
    private Map<String, List<String>> deployFromCandidates(
            List<String> candidates,
            int operationId,
            Map<String, Integer> composition,
            Integer fieldPostId
    ) {
        Map<String, List<String>> assigned = new HashMap<>();
        List<String> used = new ArrayList<>();
        for (String role : composition.keySet().stream().sorted().toList()) {
            int needed = composition.get(role);
            List<String> selected = new ArrayList<>();
            for (String residentId : candidates) {
                if (!used.contains(residentId) && preferredOccupations(role).contains(residents.resident(residentId).occupation())) {
                    selected.add(residentId);
                    if (selected.size() == needed) break;
                }
            }
            for (String residentId : candidates) {
                if (selected.size() == needed) break;
                if (!used.contains(residentId) && !selected.contains(residentId)) selected.add(residentId);
            }
            if (selected.size() != needed) {
                throw new IllegalArgumentException("settlement " + id + " lacks people for operation " + operationId);
            }
            used.addAll(selected);
            assigned.put(role, List.copyOf(selected));
        }
        Map<String, String> roles = new HashMap<>();
        List<String> all = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : assigned.entrySet()) {
            for (String residentId : entry.getValue()) { roles.put(residentId, entry.getKey()); all.add(residentId); }
        }
        if (fieldPostId == null) residents.deploy(all, operationId, roles);
        else residents.deployFromFieldPost(all, fieldPostId, operationId, roles);
        syncResidentProjection();
        return Map.copyOf(assigned);
    }
    private PythonRandom residentRng() {
        if (populationRng == null) populationRng = new PythonRandom(3_000_003L + id);
        return populationRng;
    }
    private static List<String> materialize(Iterable<String> values) {
        List<String> result = new ArrayList<>();
        values.forEach(result::add);
        return result;
    }
    private void syncResidentProjection() {
        residents.assertValid();
        population = residents.size();
        woundedPersonnel = residents.woundedIds().size();
        mobilizedPersonnel = residents.idsAt(ReferenceResidentLocation.OPERATION, ReferenceResidentCondition.ACTIVE).size()
                + residents.idsAt(ReferenceResidentLocation.FIELD_POST, ReferenceResidentCondition.ACTIVE).size();
    }
    void multiplyStock(double factor) {
        for (ReferenceResource resource : ReferenceResource.values()) stock.put(resource, amount(resource) * factor);
    }
    private static List<String> preferredOccupations(String role) {
        return switch (role) {
            case "line" -> List.of("guard");
            case "scout" -> List.of("scout");
            case "assault" -> List.of("guard", "armory_worker");
            case "engineer" -> List.of("engineer", "workshop_worker");
            case "medic" -> List.of("medic", "clinician");
            case "logistics" -> List.of("logistics");
            default -> List.of();
        };
    }
    private static double nonNegative(double value) { return value > 0.0d ? value : 0.0d; }
    private static EnumMap<ReferenceResource, Double> emptyStock() {
        EnumMap<ReferenceResource, Double> result = new EnumMap<>(ReferenceResource.class);
        for (ReferenceResource resource : ReferenceResource.values()) result.put(resource, 0.0d);
        return result;
    }
    private static Map<String, Double> emptyPrimaryCapacity() {
        return new HashMap<>(Map.of("farm", 0.0d, "mine", 0.0d, "forest", 0.0d, "power", 0.0d));
    }
}
