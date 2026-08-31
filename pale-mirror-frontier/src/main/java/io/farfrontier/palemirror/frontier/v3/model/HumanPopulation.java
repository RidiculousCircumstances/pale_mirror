package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Canonical bounded population register. Every person remains an individual;
 * households are relationships, never a cohort multiplier.
 */
public record HumanPopulation(Map<SubjectId, Household> households, Map<SubjectId, ResidentProfile> residents,
                              Map<SubjectId, ResidentBirthJob> birthJobs, Map<SubjectId, ResidentHealth> health,
                              Map<SubjectId, SettlementQuarantine> quarantines,
                              Map<SubjectId, ResidentMigrationJourney> migrations,
                              Map<SubjectId, SettlementProvision> provisions,
                              Map<SubjectId, ResidentNutrition> nutrition) {
    public static final int MAX_HOUSEHOLDS = 1_024;
    public static final int MAX_RESIDENTS = 4_096;
    public static final int MAX_BIRTH_JOBS = 1_024;
    public static final int MAX_MIGRATIONS = 256;
    /** One retained provision owner per inhabited settlement; a settlement necessarily has a household. */
    public static final int MAX_PROVISIONS = MAX_HOUSEHOLDS;

    public HumanPopulation {
        households = immutable(households, "households"); residents = immutable(residents, "residents"); birthJobs = immutable(birthJobs, "resident birth jobs");
        health = immutable(health, "resident health"); quarantines = immutable(quarantines, "settlement quarantines"); migrations = immutable(migrations, "resident migrations");
        provisions = immutable(provisions, "settlement provisions"); nutrition = immutable(nutrition, "resident nutrition");
        if (households.size() > MAX_HOUSEHOLDS || residents.size() > MAX_RESIDENTS || birthJobs.size() > MAX_BIRTH_JOBS
                || migrations.size() > MAX_MIGRATIONS || provisions.size() > MAX_PROVISIONS) {
            throw new IllegalArgumentException("human population retention limit exceeded");
        }
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
        if (!health.keySet().equals(residents.keySet())) throw new IllegalArgumentException("resident health must cover every and only exact resident");
        if (!nutrition.keySet().equals(residents.keySet())) throw new IllegalArgumentException("resident nutrition must cover every and only exact resident");
        java.util.Set<SubjectId> residentSettlements = residents.values().stream().map(ResidentProfile::settlementId).collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (!quarantines.keySet().containsAll(residentSettlements)) throw new IllegalArgumentException("settlement quarantine must cover every resident settlement");
        if (!provisions.keySet().containsAll(residentSettlements)) throw new IllegalArgumentException("settlement provision must cover every resident settlement");
        for (Map.Entry<SubjectId, SettlementProvision> entry : provisions.entrySet()) {
            if (!entry.getKey().equals(entry.getValue().settlementId())) throw new IllegalArgumentException("settlement provision map key must match identity");
        }
        java.util.Set<PhysicalIntentId> intents = new java.util.HashSet<>();
        for (Map.Entry<SubjectId, ResidentBirthJob> entry : birthJobs.entrySet()) {
            ResidentBirthJob job = entry.getValue(); Household household = households.get(job.householdId());
            if (!entry.getKey().equals(job.id()) || household == null || !household.settlementId().equals(job.settlementId())
                    || residents.containsKey(job.resident().id()) || !intents.add(job.consumptionIntentId())) {
                throw new IllegalArgumentException("resident birth job has invalid exact identity or household");
            }
        }
        for (Map.Entry<SubjectId, ResidentMigrationJourney> entry : migrations.entrySet()) {
            ResidentMigrationJourney journey = entry.getValue(); ResidentProfile resident = residents.get(journey.residentId());
            Household destination = households.get(journey.destinationHouseholdId());
            if (!entry.getKey().equals(journey.residentId()) || resident == null || !resident.settlementId().equals(journey.originSettlementId())
                    || destination == null || !destination.settlementId().equals(journey.destinationSettlementId())) {
                throw new IllegalArgumentException("migration journey must bind one resident, origin and destination household");
            }
        }
    }

    /** Compatibility constructor for population-only fixtures; all exact people start healthy and settlements normal. */
    public HumanPopulation(Map<SubjectId, Household> households, Map<SubjectId, ResidentProfile> residents,
                           Map<SubjectId, ResidentBirthJob> birthJobs) {
        this(households, residents, birthJobs, healthy(residents), normalQuarantines(residents), Map.of(), initialProvisions(residents), nourished(residents));
    }

    /** Compatibility constructor for fixtures that explicitly provide health and quarantine state. */
    public HumanPopulation(Map<SubjectId, Household> households, Map<SubjectId, ResidentProfile> residents,
                           Map<SubjectId, ResidentBirthJob> birthJobs, Map<SubjectId, ResidentHealth> health,
                           Map<SubjectId, SettlementQuarantine> quarantines) {
        this(households, residents, birthJobs, health, quarantines, Map.of(), initialProvisions(residents), nourished(residents));
    }

    /** Compatibility constructor for a persisted population preceding provision tracking. */
    public HumanPopulation(Map<SubjectId, Household> households, Map<SubjectId, ResidentProfile> residents,
                           Map<SubjectId, ResidentBirthJob> birthJobs, Map<SubjectId, ResidentHealth> health,
                           Map<SubjectId, SettlementQuarantine> quarantines, Map<SubjectId, ResidentMigrationJourney> migrations) {
        this(households, residents, birthJobs, health, quarantines, migrations, initialProvisions(residents), nourished(residents));
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
        return new HumanPopulation(households, residents, Map.of(), healthy(residents), normalQuarantines(residents), Map.of(), initialProvisions(residents), nourished(residents));
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

    public static Map<ResidentSkill, Integer> birthSkills(int ordinal) {
        return skills(ResidentRole.FARMER, ordinal);
    }

    public Set<SubjectId> residentIds() { return residents.keySet(); }
    public ResidentProfile resident(SubjectId id) { return residents.get(id); }
    public ResidentHealth health(SubjectId id) { return health.get(id); }
    public ResidentNutrition nutrition(SubjectId id) {
        ResidentNutrition value = nutrition.get(Objects.requireNonNull(id, "resident nutrition resident"));
        if (value == null) throw new IllegalArgumentException("unknown resident nutrition subject");
        return value;
    }
    public SettlementQuarantine quarantine(SubjectId settlementId) { return quarantines.get(settlementId); }
    public boolean quarantined(SubjectId settlementId) {
        SettlementQuarantine policy = quarantine(settlementId);
        if (policy == null) throw new IllegalArgumentException("unknown settlement quarantine: " + settlementId.value());
        return policy.status() == SettlementQuarantineStatus.QUARANTINED;
    }

    public HumanPopulation add(ResidentProfile resident) {
        Objects.requireNonNull(resident, "resident");
        if (residents.containsKey(resident.id())) throw new IllegalArgumentException("resident identity already exists");
        if (!households.containsKey(resident.householdId())) throw new IllegalArgumentException("resident birth needs an existing household");
        Map<SubjectId, ResidentProfile> next = new LinkedHashMap<>(residents); next.put(resident.id(), resident);
        return new HumanPopulation(households, next, birthJobs, withHealthy(health, resident.id(), resident.birthTick()), quarantines, migrations, provisions,
                withNourished(nutrition, resident.id()));
    }

    /** Replaces mutable social capability data without changing one person's identity or home membership. */
    public HumanPopulation withProfile(ResidentProfile resident) {
        Objects.requireNonNull(resident, "resident profile");
        ResidentProfile current = residents.get(resident.id());
        if (current == null || !current.householdId().equals(resident.householdId()) || !current.settlementId().equals(resident.settlementId())) {
            throw new IllegalArgumentException("resident profile replacement must preserve exact household and settlement membership");
        }
        Map<SubjectId, ResidentProfile> next = new LinkedHashMap<>(residents); next.put(resident.id(), resident);
        return new HumanPopulation(households, next, birthJobs, health, quarantines, migrations, provisions, nutrition);
    }

    public HumanPopulation migrate(SubjectId residentId, SubjectId householdId, SubjectId settlementId) {
        ResidentProfile resident = residents.get(Objects.requireNonNull(residentId, "resident id"));
        if (resident == null) throw new IllegalArgumentException("unknown resident");
        Household household = households.get(Objects.requireNonNull(householdId, "household id"));
        if (household == null || !household.settlementId().equals(settlementId)) throw new IllegalArgumentException("migration needs a household in its destination settlement");
        Map<SubjectId, ResidentProfile> next = new LinkedHashMap<>(residents); next.put(residentId, resident.relocated(settlementId, householdId));
        return new HumanPopulation(households, next, birthJobs, health, quarantines, migrations, provisions, nutrition);
    }

    public HumanPopulation startBirth(ResidentBirthJob job) {
        Objects.requireNonNull(job, "resident birth job");
        if (birthJobs.containsKey(job.id()) || residents.containsKey(job.resident().id())) throw new IllegalArgumentException("resident birth identity already exists");
        Map<SubjectId, ResidentBirthJob> next = new LinkedHashMap<>(birthJobs); next.put(job.id(), job);
        return new HumanPopulation(households, residents, next, health, quarantines, migrations, provisions, nutrition);
    }

    public HumanPopulation completeBirth(SubjectId jobId) {
        ResidentBirthJob job = birthJobs.get(Objects.requireNonNull(jobId, "resident birth job id"));
        if (job == null || residents.containsKey(job.resident().id())) throw new IllegalArgumentException("resident birth completion lacks a unique output");
        Map<SubjectId, ResidentBirthJob> next = new LinkedHashMap<>(birthJobs); next.remove(jobId);
        Map<SubjectId, ResidentProfile> nextResidents = new LinkedHashMap<>(residents); nextResidents.put(job.resident().id(), job.resident());
        return new HumanPopulation(households, nextResidents, next, withHealthy(health, job.resident().id(), job.resident().birthTick()), quarantines, migrations,
                provisions, withNourished(nutrition, job.resident().id()));
    }

    public HumanPopulation cancelBirth(SubjectId jobId) {
        if (!birthJobs.containsKey(Objects.requireNonNull(jobId, "resident birth job id"))) throw new IllegalArgumentException("unknown resident birth job");
        Map<SubjectId, ResidentBirthJob> next = new LinkedHashMap<>(birthJobs); next.remove(jobId);
        return new HumanPopulation(households, residents, next, health, quarantines, migrations, provisions, nutrition);
    }

    public HumanPopulation transitionHealth(SubjectId residentId, ResidentHealthStatus nextStatus, long tick) {
        ResidentHealth current = health.get(Objects.requireNonNull(residentId, "resident health resident"));
        if (current == null) throw new IllegalArgumentException("unknown resident health subject");
        Map<SubjectId, ResidentHealth> next = new LinkedHashMap<>(health); next.put(residentId, current.transition(nextStatus, tick));
        return new HumanPopulation(households, residents, birthJobs, next, quarantines, migrations, provisions, nutrition);
    }

    /** Applies one exact food outcome once to one exact resident. */
    public HumanPopulation resolveNutrition(SubjectId residentId, int cycle, boolean fed) {
        ResidentNutrition current = nutrition(residentId);
        Map<SubjectId, ResidentNutrition> next = new LinkedHashMap<>(nutrition);
        next.put(residentId, fed ? current.fed(cycle) : current.missed(cycle));
        return new HumanPopulation(households, residents, birthJobs, health, quarantines, migrations, provisions, next);
    }

    public HumanPopulation transitionQuarantine(SubjectId settlementId, SettlementQuarantineStatus nextStatus, long tick) {
        SettlementQuarantine current = quarantines.get(Objects.requireNonNull(settlementId, "quarantine settlement"));
        if (current == null) throw new IllegalArgumentException("unknown settlement quarantine");
        Map<SubjectId, SettlementQuarantine> next = new LinkedHashMap<>(quarantines); next.put(settlementId, current.transition(nextStatus, tick));
        return new HumanPopulation(households, residents, birthJobs, health, next, migrations, provisions, nutrition);
    }

    public ResidentMigrationJourney migration(SubjectId residentId) { return migrations.get(residentId); }

    public HumanPopulation startMigration(ResidentMigrationJourney journey) {
        Objects.requireNonNull(journey, "migration journey");
        if (migrations.containsKey(journey.residentId())) throw new IllegalArgumentException("resident already has an active migration journey");
        Map<SubjectId, ResidentMigrationJourney> next = new LinkedHashMap<>(migrations); next.put(journey.residentId(), journey);
        return new HumanPopulation(households, residents, birthJobs, health, quarantines, next, provisions, nutrition);
    }

    public HumanPopulation advanceMigration(SubjectId residentId, int nextRouteIndex) {
        ResidentMigrationJourney journey = requireMigration(residentId);
        Map<SubjectId, ResidentMigrationJourney> next = new LinkedHashMap<>(migrations); next.put(residentId, journey.advanceTo(nextRouteIndex));
        return new HumanPopulation(households, residents, birthJobs, health, quarantines, next, provisions, nutrition);
    }

    public long inboundHousingReservations(SubjectId settlementId) {
        return migrations.values().stream().filter(journey -> journey.destinationSettlementId().equals(settlementId)).count();
    }

    public HumanPopulation blockMigration(SubjectId residentId, ResidentMigrationBlockReason reason) {
        ResidentMigrationJourney journey = requireMigration(residentId);
        Map<SubjectId, ResidentMigrationJourney> next = new LinkedHashMap<>(migrations); next.put(residentId, journey.block(reason));
        return new HumanPopulation(households, residents, birthJobs, health, quarantines, next, provisions, nutrition);
    }

    public HumanPopulation resumeMigration(SubjectId residentId) {
        ResidentMigrationJourney journey = requireMigration(residentId);
        Map<SubjectId, ResidentMigrationJourney> next = new LinkedHashMap<>(migrations); next.put(residentId, journey.resume());
        return new HumanPopulation(households, residents, birthJobs, health, quarantines, next, provisions, nutrition);
    }

    public HumanPopulation completeMigration(SubjectId residentId, SubjectId householdId, SubjectId settlementId) {
        ResidentMigrationJourney journey = requireMigration(residentId);
        if (!journey.arriving() || !journey.destinationHouseholdId().equals(householdId) || !journey.destinationSettlementId().equals(settlementId)) {
            throw new IllegalArgumentException("resident migration completion does not match its journey");
        }
        Map<SubjectId, ResidentProfile> nextResidents = new LinkedHashMap<>(residents);
        nextResidents.put(residentId, residents.get(residentId).relocated(settlementId, householdId));
        Map<SubjectId, ResidentMigrationJourney> nextMigrations = new LinkedHashMap<>(migrations); nextMigrations.remove(residentId);
        return new HumanPopulation(households, nextResidents, birthJobs, health, quarantines, nextMigrations, provisions, nutrition);
    }

    public HumanPopulation cancelMigration(SubjectId residentId) {
        if (!migrations.containsKey(residentId)) return this;
        Map<SubjectId, ResidentMigrationJourney> next = new LinkedHashMap<>(migrations); next.remove(residentId);
        return new HumanPopulation(households, residents, birthJobs, health, quarantines, next, provisions, nutrition);
    }

    public int activeCases(SubjectId settlementId) {
        return Math.toIntExact(residents.values().stream().filter(resident -> resident.settlementId().equals(settlementId))
                .map(resident -> health.get(resident.id()).status()).filter(status -> status == ResidentHealthStatus.EXPOSED || status == ResidentHealthStatus.INFECTED).count());
    }

    public SettlementProvision provision(SubjectId settlementId) {
        SettlementProvision provision = provisions.get(Objects.requireNonNull(settlementId, "provision settlement"));
        if (provision == null) throw new IllegalArgumentException("unknown settlement provision: " + settlementId.value());
        return provision;
    }

    public HumanPopulation withProvision(SettlementProvision provision) {
        Objects.requireNonNull(provision, "settlement provision");
        if (!provisions.containsKey(provision.settlementId())) throw new IllegalArgumentException("unknown settlement provision");
        Map<SubjectId, SettlementProvision> next = new LinkedHashMap<>(provisions); next.put(provision.settlementId(), provision);
        return new HumanPopulation(households, residents, birthJobs, health, quarantines, migrations, next, nutrition);
    }

    private static Map<SubjectId, ResidentHealth> healthy(Map<SubjectId, ResidentProfile> residents) {
        Map<SubjectId, ResidentHealth> values = new LinkedHashMap<>();
        residents.values().forEach(resident -> values.put(resident.id(), ResidentHealth.healthyAt(resident.birthTick())));
        return values;
    }

    private static Map<SubjectId, SettlementQuarantine> normalQuarantines(Map<SubjectId, ResidentProfile> residents) {
        Map<SubjectId, SettlementQuarantine> values = new LinkedHashMap<>();
        residents.values().stream().map(ResidentProfile::settlementId).distinct().forEach(id -> values.put(id, SettlementQuarantine.normalAt(0L)));
        return values;
    }

    private static Map<SubjectId, SettlementProvision> initialProvisions(Map<SubjectId, ResidentProfile> residents) {
        Map<SubjectId, SettlementProvision> values = new LinkedHashMap<>();
        residents.values().stream().map(ResidentProfile::settlementId).distinct().forEach(id -> values.put(id, SettlementProvision.idle(id)));
        return values;
    }

    private static Map<SubjectId, ResidentNutrition> nourished(Map<SubjectId, ResidentProfile> residents) {
        Map<SubjectId, ResidentNutrition> values = new LinkedHashMap<>();
        residents.values().forEach(resident -> values.put(resident.id(), ResidentNutrition.nourishedAt(0)));
        return values;
    }

    private static Map<SubjectId, ResidentHealth> withHealthy(Map<SubjectId, ResidentHealth> source, SubjectId id, long tick) {
        Map<SubjectId, ResidentHealth> next = new LinkedHashMap<>(source);
        if (next.putIfAbsent(id, ResidentHealth.healthyAt(tick)) != null) throw new IllegalArgumentException("resident health identity already exists");
        return next;
    }

    private static Map<SubjectId, ResidentNutrition> withNourished(Map<SubjectId, ResidentNutrition> source, SubjectId id) {
        Map<SubjectId, ResidentNutrition> next = new LinkedHashMap<>(source);
        if (next.putIfAbsent(id, ResidentNutrition.nourishedAt(0)) != null) throw new IllegalArgumentException("resident nutrition identity already exists");
        return next;
    }

    private ResidentMigrationJourney requireMigration(SubjectId residentId) {
        ResidentMigrationJourney journey = migrations.get(Objects.requireNonNull(residentId, "migration resident"));
        if (journey == null) throw new IllegalArgumentException("resident has no active migration journey");
        return journey;
    }

    private static <K, V> Map<K, V> immutable(Map<K, V> source, String name) {
        Objects.requireNonNull(source, name); LinkedHashMap<K, V> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> copy.put(Objects.requireNonNull(key, name + " key"), Objects.requireNonNull(value, name + " value")));
        return Map.copyOf(copy);
    }
}
