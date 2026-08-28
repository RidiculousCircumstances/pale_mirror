package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Canonical bounded population register. Every person remains an individual;
 * households are relationships, never a cohort multiplier.
 */
public record HumanPopulation(Map<SubjectId, Household> households, Map<SubjectId, ResidentProfile> residents) {
    public static final int MAX_HOUSEHOLDS = 1_024;
    public static final int MAX_RESIDENTS = 4_096;

    public HumanPopulation {
        households = immutable(households, "households"); residents = immutable(residents, "residents");
        if (households.size() > MAX_HOUSEHOLDS || residents.size() > MAX_RESIDENTS) throw new IllegalArgumentException("human population retention limit exceeded");
        for (Map.Entry<SubjectId, Household> entry : households.entrySet()) {
            if (!entry.getKey().equals(entry.getValue().id())) throw new IllegalArgumentException("household map key must match identity");
        }
        for (Map.Entry<SubjectId, ResidentProfile> entry : residents.entrySet()) {
            ResidentProfile resident = entry.getValue();
            if (!entry.getKey().equals(resident.id())) throw new IllegalArgumentException("resident map key must match identity");
            Household household = households.get(resident.householdId());
            if (household == null || !household.settlementId().equals(resident.settlementId())) {
                throw new IllegalArgumentException("resident must belong to a household in the same settlement");
            }
        }
    }

    public static HumanPopulation bootstrap(FrontierBootstrap bootstrap) {
        Map<SubjectId, Household> households = new LinkedHashMap<>();
        Map<SubjectId, ResidentProfile> residents = new LinkedHashMap<>();
        for (Settlement settlement : bootstrap.settlements()) {
            int householdOrdinal = 0;
            for (int ordinal = 0; ordinal < settlement.residents().size(); ordinal++) {
                if (ordinal % 4 == 0) householdOrdinal++;
                Resident resident = settlement.residents().get(ordinal);
                SubjectId householdId = new SubjectId("household:" + settlement.id().value().substring("settlement:".length()) + "-" + householdOrdinal);
                households.putIfAbsent(householdId, new Household(householdId, settlement.id()));
                residents.put(resident.id(), new ResidentProfile(resident.id(), householdId, settlement.id(), resident.role(),
                        -((long) (18 + (ordinal % 43)) * 24_000L * 360L), skills(resident.role(), ordinal)));
            }
        }
        return new HumanPopulation(households, residents);
    }

    private static Map<ResidentSkill, Integer> skills(ResidentRole role, int ordinal) {
        java.util.EnumMap<ResidentSkill, Integer> values = new java.util.EnumMap<>(ResidentSkill.class);
        for (ResidentSkill skill : ResidentSkill.values()) values.put(skill, 15 + Math.floorMod(ordinal * 11 + skill.ordinal() * 7, 25));
        values.put(switch (role) {
            case FARMER -> ResidentSkill.AGRICULTURE; case BUILDER -> ResidentSkill.BUILDING; case CRAFTER -> ResidentSkill.CRAFTING;
            case GUARD -> ResidentSkill.SECURITY; case MEDIC -> ResidentSkill.MEDICINE; case HAULER -> ResidentSkill.LOGISTICS;
        }, 60 + Math.floorMod(ordinal, 16));
        return values;
    }

    public Set<SubjectId> residentIds() { return residents.keySet(); }
    public ResidentProfile resident(SubjectId id) { return residents.get(id); }

    public HumanPopulation add(ResidentProfile resident) {
        Objects.requireNonNull(resident, "resident");
        if (residents.containsKey(resident.id())) throw new IllegalArgumentException("resident identity already exists");
        if (!households.containsKey(resident.householdId())) throw new IllegalArgumentException("resident birth needs an existing household");
        Map<SubjectId, ResidentProfile> next = new LinkedHashMap<>(residents); next.put(resident.id(), resident);
        return new HumanPopulation(households, next);
    }

    public HumanPopulation migrate(SubjectId residentId, SubjectId householdId, SubjectId settlementId) {
        ResidentProfile resident = residents.get(Objects.requireNonNull(residentId, "resident id"));
        if (resident == null) throw new IllegalArgumentException("unknown resident");
        Household household = households.get(Objects.requireNonNull(householdId, "household id"));
        if (household == null || !household.settlementId().equals(settlementId)) throw new IllegalArgumentException("migration needs a household in its destination settlement");
        Map<SubjectId, ResidentProfile> next = new LinkedHashMap<>(residents); next.put(residentId, resident.relocated(settlementId, householdId));
        return new HumanPopulation(households, next);
    }

    private static <K, V> Map<K, V> immutable(Map<K, V> source, String name) {
        Objects.requireNonNull(source, name); LinkedHashMap<K, V> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> copy.put(Objects.requireNonNull(key, name + " key"), Objects.requireNonNull(value, name + " value")));
        return Map.copyOf(copy);
    }
}
