package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.combat.*;
import io.farfrontier.palemirror.frontier.v3.model.seating.SeatState;

import io.farfrontier.palemirror.frontier.v3.model.combat.CombatStyleCatalog;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
                              Map<SubjectId, ResidentNutrition> nutrition,
                              Map<SubjectId, MedicalEvacuationOperation> medicalOperations,
                              Map<SubjectId, SettlementDailySchedule> schedules,
                              Map<SubjectId, ResidentMeal> meals,
                              Map<SubjectId, ResidentMealResourceObligation> mealResourceObligations,
                              io.farfrontier.palemirror.frontier.v3.model.clinical.ClinicalState clinical,
                              io.farfrontier.palemirror.frontier.v3.model.rest.RestPlaceState restPlaces, SeatState seats) {
    public static final int MAX_HOUSEHOLDS = SimulationCapacity.MAX_HOUSEHOLDS;
    public static final int MAX_RESIDENTS = SimulationCapacity.MAX_RESIDENTS;
    public static final int MAX_BIRTH_JOBS = 1_024;
    public static final int MAX_MIGRATIONS = 256;
    /** One retained provision owner per inhabited settlement; a settlement necessarily has a household. */
    public static final int MAX_PROVISIONS = MAX_HOUSEHOLDS;
    private static final ImmutableInputView<Map<SubjectId, java.util.List<ResidentProfile>>> RESIDENTS_BY_SETTLEMENT = new ImmutableInputView<>();

    /** Derived membership only. Life, migration, activity and permission are
     * still assessed from current state by the selecting owner. */
    public java.util.List<ResidentProfile> residentsIn(SubjectId settlement) {
        return RESIDENTS_BY_SETTLEMENT.get(java.util.List.of(residents), () -> {
            var grouped = new LinkedHashMap<SubjectId, java.util.List<ResidentProfile>>();
            residents.values().forEach(resident -> grouped.computeIfAbsent(resident.settlementId(),
                    ignored -> new java.util.ArrayList<>()).add(resident));
            grouped.replaceAll((home, people) -> java.util.List.copyOf(people));
            return Map.copyOf(grouped);
        }).getOrDefault(settlement, java.util.List.of());
    }

    public HumanPopulation {
        Objects.requireNonNull(clinical);Objects.requireNonNull(restPlaces);Objects.requireNonNull(seats);
        households = immutable(households, "households"); residents = immutable(residents, "residents"); birthJobs = immutable(birthJobs, "resident birth jobs");
        health = immutable(health, "resident health"); quarantines = immutable(quarantines, "settlement quarantines"); migrations = immutable(migrations, "resident migrations");
        provisions = immutable(provisions, "settlement provisions"); nutrition = immutable(nutrition, "resident nutrition");
        medicalOperations = immutable(medicalOperations, "medical operations");
        schedules = immutable(schedules, "settlement schedules"); meals = immutable(meals, "resident meals");
        mealResourceObligations = immutable(mealResourceObligations, "retired meal resource obligations");
        if (households.size() > MAX_HOUSEHOLDS || residents.size() > MAX_RESIDENTS || birthJobs.size() > MAX_BIRTH_JOBS
                || migrations.size() > MAX_MIGRATIONS || provisions.size() > MAX_PROVISIONS) {
            throw new IllegalArgumentException("human population retention limit exceeded");
        }
        ClinicalPopulationReferences.validate(residents,clinical,restPlaces);
        HumanPopulationValidation.validate(new HumanPopulationValidation.Inputs(households, residents, birthJobs, health,
                quarantines, migrations, provisions, nutrition, medicalOperations, schedules, meals, mealResourceObligations));
    }

    /** Unit/bootstrap convenience; production reconstructions explicitly retain clinical and rest ownership. */
    public HumanPopulation(Map<SubjectId,Household> households,Map<SubjectId,ResidentProfile> residents,
            Map<SubjectId,ResidentBirthJob> birthJobs,Map<SubjectId,ResidentHealth> health,Map<SubjectId,SettlementQuarantine> quarantines,
            Map<SubjectId,ResidentMigrationJourney> migrations,Map<SubjectId,SettlementProvision> provisions,Map<SubjectId,ResidentNutrition> nutrition,
            Map<SubjectId,MedicalEvacuationOperation> medicalOperations,Map<SubjectId,SettlementDailySchedule> schedules,
            Map<SubjectId,ResidentMeal> meals,Map<SubjectId,ResidentMealResourceObligation> mealResourceObligations){
        this(households,residents,birthJobs,health,quarantines,migrations,provisions,nutrition,medicalOperations,schedules,meals,mealResourceObligations,
                io.farfrontier.palemirror.frontier.v3.model.clinical.ClinicalState.empty(),io.farfrontier.palemirror.frontier.v3.model.rest.RestPlaceState.empty(),SeatState.empty());
    }
    public HumanPopulation withClinical(io.farfrontier.palemirror.frontier.v3.model.clinical.ClinicalState value){
        return new HumanPopulation(households,residents,birthJobs,health,quarantines,migrations,provisions,nutrition,medicalOperations,schedules,meals,mealResourceObligations,value,restPlaces,seats);
    }
    public HumanPopulation withRestPlaces(io.farfrontier.palemirror.frontier.v3.model.rest.RestPlaceState value){
        return new HumanPopulation(households,residents,birthJobs,health,quarantines,migrations,provisions,nutrition,medicalOperations,schedules,meals,mealResourceObligations,clinical,value,seats);
    }

    public HumanPopulation withSeats(SeatState value) {
        return new HumanPopulation(households,residents,birthJobs,health,quarantines,migrations,provisions,nutrition,
                medicalOperations,schedules,meals,mealResourceObligations,clinical,restPlaces,value);
    }

    /** Constructor retained for existing fixtures and the pre-cut provision model. */
    public HumanPopulation(Map<SubjectId, Household> households, Map<SubjectId, ResidentProfile> residents,
                           Map<SubjectId, ResidentBirthJob> birthJobs, Map<SubjectId, ResidentHealth> health,
                           Map<SubjectId, SettlementQuarantine> quarantines,
                           Map<SubjectId, ResidentMigrationJourney> migrations,
                           Map<SubjectId, SettlementProvision> provisions,
                           Map<SubjectId, ResidentNutrition> nutrition,
                           Map<SubjectId, MedicalEvacuationOperation> medicalOperations) {
        this(households, residents, birthJobs, health, quarantines, migrations, provisions, nutrition,
                medicalOperations, initialSchedules(residents), Map.of(), Map.of());
    }

    /** Compatibility constructor for population-only fixtures; all exact people start healthy and settlements normal. */
    public HumanPopulation(Map<SubjectId, Household> households, Map<SubjectId, ResidentProfile> residents,
                           Map<SubjectId, ResidentBirthJob> birthJobs) {
        this(households, residents, birthJobs, healthy(residents), normalQuarantines(residents), Map.of(), initialProvisions(residents), nourished(residents), Map.of());
    }

    /** Compatibility constructor for fixtures that explicitly provide health and quarantine state. */
    public HumanPopulation(Map<SubjectId, Household> households, Map<SubjectId, ResidentProfile> residents,
                           Map<SubjectId, ResidentBirthJob> birthJobs, Map<SubjectId, ResidentHealth> health,
                           Map<SubjectId, SettlementQuarantine> quarantines) {
        this(households, residents, birthJobs, health, quarantines, Map.of(), initialProvisions(residents), nourished(residents), Map.of());
    }

    /** Compatibility constructor for a persisted population preceding provision tracking. */
    public HumanPopulation(Map<SubjectId, Household> households, Map<SubjectId, ResidentProfile> residents,
                           Map<SubjectId, ResidentBirthJob> birthJobs, Map<SubjectId, ResidentHealth> health,
                           Map<SubjectId, SettlementQuarantine> quarantines, Map<SubjectId, ResidentMigrationJourney> migrations) {
        this(households, residents, birthJobs, health, quarantines, migrations, initialProvisions(residents), nourished(residents), Map.of());
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
                ResidentProfile profile = new ResidentProfile(resident.id(), householdId, settlement.id(), resident.role(),
                        ResidentNames.create(bootstrap.seed(), resident.id()),
                        -((long) (18 + (ordinal % 43)) * 24_000L * 360L), skills(resident.role(), ordinal),CombatStyleCatalog.standard().forBirth(bootstrap.seed(),resident.id()),
            CombatProfileGeneration.create(bootstrap.seed(),resident.id().value().hashCode()));
                profile = profile.withCharacteristics(ResidentCharacteristics.initial(
                        bootstrap.ruleset().residentLife(), resident.id()));
                residents.put(resident.id(), profile);
            }
        }
        return new HumanPopulation(households, residents, Map.of(), healthy(residents), normalQuarantines(residents),
                Map.of(), Map.of(), nourished(residents, bootstrap.ruleset().residentLife()), Map.of(),
                initialSchedules(residents, bootstrap.ruleset().residentLife()), Map.of(), Map.of());
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
    public HumanPopulation withStarvation(SubjectId residentId, ResidentStarvation condition) {
        ResidentHealth current = health.get(residentId);
        if (current == null) throw new IllegalArgumentException("unknown starvation resident");
        var next = ImmutableLookupMap.withEntry(health, residentId, current.withStarvation(condition));
        return new HumanPopulation(households, residents, birthJobs, next, quarantines, migrations,
provisions, nutrition, medicalOperations, schedules, meals, mealResourceObligations, clinical, restPlaces, seats);
    }
    public ResidentNutrition nutrition(SubjectId id) {
        ResidentNutrition value = nutrition.get(Objects.requireNonNull(id, "resident nutrition resident"));
        if (value == null) throw new IllegalArgumentException("unknown resident nutrition subject");
        return value;
    }
    public HumanPopulation changeMetabolism(SubjectId residentId, ResidentCharacteristics expected,
                                            ResidentCharacteristics nextCharacteristics, long atTick,
                                            FrontierRuleset.ResidentLife rules) {
        ResidentProfile profile = resident(residentId);
        if (profile == null || !profile.characteristics().equals(expected))
            throw new IllegalArgumentException("metabolism edit lacks its exact previous resident state");
        int oldRate = expected.effectiveMetabolismPermille(atTick);
        ResidentNutrition integrated = nutrition(residentId).accrueThrough(atTick, rules, oldRate);
        var nextResidents = ImmutableLookupMap.withEntry(residents, residentId, profile.withCharacteristics(nextCharacteristics));
        var nextNutrition = ImmutableLookupMap.withEntry(nutrition, residentId, integrated);
        return new HumanPopulation(households, nextResidents, birthJobs, health, quarantines,
                migrations, provisions, nextNutrition, medicalOperations, schedules, meals, mealResourceObligations, clinical, restPlaces, seats);
    }
    public HumanPopulation changeWorkModifiers(SubjectId residentId, ResidentWorkModifiers expected,
                                               ResidentWorkModifiers nextModifiers) {
        ResidentProfile profile = resident(residentId);
        if (profile == null || !profile.characteristics().workModifiers().equals(expected))
            throw new IllegalArgumentException("work-stat edit lacks its exact previous resident state");
        var nextResidents = ImmutableLookupMap.withEntry(residents, residentId, profile.withCharacteristics(
                profile.characteristics().withWorkModifiers(Objects.requireNonNull(nextModifiers))));
        return new HumanPopulation(households, nextResidents, birthJobs, health, quarantines,
                migrations, provisions, nutrition, medicalOperations, schedules, meals, mealResourceObligations, clinical, restPlaces, seats);
    }
    public SettlementDailySchedule schedule(SubjectId settlementId) {
        SettlementDailySchedule policy = schedules.get(Objects.requireNonNull(settlementId, "schedule settlement"));
        if (policy == null) throw new IllegalArgumentException("unknown settlement schedule");
        return policy;
    }
    public HumanPopulation withSchedule(SubjectId settlementId, SettlementDailySchedule nextPolicy) {
        schedule(settlementId);
        var next = ImmutableLookupMap.withEntry(schedules, settlementId, Objects.requireNonNull(nextPolicy, "new settlement schedule"));
        return new HumanPopulation(households, residents, birthJobs, health, quarantines, migrations,
                provisions, nutrition, medicalOperations, next, meals, mealResourceObligations, clinical, restPlaces, seats);
    }
    public HumanPopulation withMeal(ResidentMeal meal) {
        Objects.requireNonNull(meal, "resident meal");
        if (meals.containsKey(meal.residentId())) throw new IllegalArgumentException("resident already has a retained meal");
        Map<SubjectId, ResidentMeal> next = ImmutableLookupMap.withEntry(meals, meal.residentId(), meal);
        return new HumanPopulation(households, residents, birthJobs, health, quarantines, migrations,
                provisions, nutrition, medicalOperations, schedules, next, mealResourceObligations, clinical, restPlaces, seats);
    }
    public HumanPopulation advanceMeal(ResidentMeal expected, ResidentMeal nextMeal) {
        if (!expected.residentId().equals(nextMeal.residentId()) || !expected.equals(meals.get(expected.residentId())))
            throw new IllegalArgumentException("meal transition lacks its exact retained predecessor");
        Map<SubjectId, ResidentMeal> next = ImmutableLookupMap.withEntry(meals, expected.residentId(), nextMeal);
        return new HumanPopulation(households, residents, birthJobs, health, quarantines, migrations,
                provisions, nutrition, medicalOperations, schedules, next, mealResourceObligations, clinical, restPlaces, seats);
    }
    public HumanPopulation completeMeal(ResidentMeal expected) {
        if (!expected.equals(meals.get(expected.residentId()))
                || expected.phase() != ResidentMeal.Phase.CONSUME)
            throw new IllegalArgumentException("meal completion lacks its exact consumed predecessor");
        Map<SubjectId, ResidentMeal> next = ImmutableLookupMap.withoutEntry(meals, expected.residentId());
        return new HumanPopulation(households, residents, birthJobs, health, quarantines, migrations,
                provisions, nutrition, medicalOperations, schedules, next, mealResourceObligations, clinical, restPlaces, seats);
    }
    /** An external stock exit may retire only an unconsumed, unfenced source claim. */
    public HumanPopulation abandonMealSource(ResidentMeal expected) {
        if (!expected.equals(meals.get(expected.residentId()))
                || expected.phase() != ResidentMeal.Phase.MOVE && expected.phase() != ResidentMeal.Phase.TAKE
                || expected.pendingPhysicalStep().isPresent())
            throw new IllegalArgumentException("meal source loss cannot discard an in-flight or consumed hand");
        Map<SubjectId, ResidentMeal> next = ImmutableLookupMap.withoutEntry(meals, expected.residentId());
        return new HumanPopulation(households, residents, birthJobs, health, quarantines, migrations,
                provisions, nutrition, medicalOperations, schedules, next, mealResourceObligations, clinical, restPlaces, seats);
    }
    /** Fatality retires activity, but never its possibly applied effect or carried portion. */
    public HumanPopulation retainMealResources(ResidentMeal expected, ResidentMealResourceObligation obligation) {
        if (!expected.equals(meals.get(expected.residentId()))
                || mealResourceObligations.containsKey(expected.residentId())
                || !obligation.equals(ResidentMealResourceObligation.retain(expected, obligation.body(), obligation.retiredAtTick())))
            throw new IllegalArgumentException("meal resource retirement lacks its exact active predecessor");
        Map<SubjectId, ResidentMeal> active = new LinkedHashMap<>(meals);
        active.remove(expected.residentId());
        Map<SubjectId, ResidentMealResourceObligation> retained = new LinkedHashMap<>(mealResourceObligations);
        retained.put(expected.residentId(), obligation);
        return new HumanPopulation(households, residents, birthJobs, health, quarantines, migrations,
                provisions, nutrition, medicalOperations, schedules, active, retained, clinical, restPlaces, seats);
    }
    public HumanPopulation settleMealResources(ResidentMealResourceObligation expected,
                                               Optional<ResidentMealResourceObligation> replacement) {
        Objects.requireNonNull(replacement, "resource obligation replacement");
        if (!expected.equals(mealResourceObligations.get(expected.residentId()))
                || replacement.isPresent() && (!replacement.orElseThrow().body().equals(expected.body())
                    || !replacement.orElseThrow().executionId().equals(expected.executionId())
                    || !replacement.orElseThrow().portion().equals(expected.portion())
                    || !replacement.orElseThrow().settlementId().equals(expected.settlementId())
                    || !replacement.orElseThrow().source().equals(expected.source())
                    || !replacement.orElseThrow().sourceAccountId().equals(expected.sourceAccountId())
                    || !replacement.orElseThrow().actorAccountId().equals(expected.actorAccountId())
                    || replacement.orElseThrow().retiredAtTick() != expected.retiredAtTick()))
            throw new IllegalArgumentException("meal resource settlement lost its exact retired predecessor");
        Map<SubjectId, ResidentMealResourceObligation> next = new LinkedHashMap<>(mealResourceObligations);
        if (replacement.isPresent()) next.put(expected.residentId(), replacement.orElseThrow());
        else next.remove(expected.residentId());
        return new HumanPopulation(households, residents, birthJobs, health, quarantines, migrations,
                provisions, nutrition, medicalOperations, schedules, meals, next, clinical, restPlaces, seats);
    }
    public SettlementQuarantine quarantine(SubjectId settlementId) { return quarantines.get(settlementId); }
    public boolean quarantined(SubjectId settlementId) {
        SettlementQuarantine policy = quarantine(settlementId);
        if (policy == null) throw new IllegalArgumentException("unknown settlement quarantine: " + settlementId.value());
        return policy.status() == SettlementQuarantineStatus.QUARANTINED;
    }

    public HumanPopulation add(ResidentProfile resident) {
        return add(resident, FrontierRuleset.ResidentLife.initial());
    }

    public HumanPopulation add(ResidentProfile resident, FrontierRuleset.ResidentLife rules) {
        Objects.requireNonNull(resident, "resident");
        if (residents.containsKey(resident.id())) throw new IllegalArgumentException("resident identity already exists");
        if (!households.containsKey(resident.householdId())) throw new IllegalArgumentException("resident birth needs an existing household");
        Map<SubjectId, ResidentProfile> next = new LinkedHashMap<>(residents); next.put(resident.id(), resident);
        return new HumanPopulation(households, next, birthJobs, withHealthy(health, resident.id(), resident.birthTick()), quarantines, migrations, provisions,
                withNourished(nutrition, resident.id(), resident.birthTick(), rules), medicalOperations, schedules, meals, mealResourceObligations, clinical, restPlaces, seats);
    }

    /** Replaces mutable social capability data without changing one person's identity or home membership. */
    public HumanPopulation withProfile(ResidentProfile resident) {
        Objects.requireNonNull(resident, "resident profile");
        ResidentProfile current = residents.get(resident.id());
        if (current == null || !current.householdId().equals(resident.householdId()) || !current.settlementId().equals(resident.settlementId())) {
            throw new IllegalArgumentException("resident profile replacement must preserve exact household and settlement membership");
        }
        if (!current.name().equals(resident.name()))
            throw new IllegalArgumentException("profile updates must preserve the resident's declared name");
        Map<SubjectId, ResidentProfile> next = new LinkedHashMap<>(residents); next.put(resident.id(), resident);
        return new HumanPopulation(households, next, birthJobs, health, quarantines, migrations, provisions, nutrition, medicalOperations, schedules, meals, mealResourceObligations, clinical, restPlaces, seats);
    }

    public HumanPopulation migrate(SubjectId residentId, SubjectId householdId, SubjectId settlementId) {
        ResidentProfile resident = residents.get(Objects.requireNonNull(residentId, "resident id"));
        if (resident == null) throw new IllegalArgumentException("unknown resident");
        if (meals.containsKey(residentId)) throw new IllegalArgumentException("resident cannot migrate during a retained meal");
        Household household = households.get(Objects.requireNonNull(householdId, "household id"));
        if (household == null || !household.settlementId().equals(settlementId)) throw new IllegalArgumentException("migration needs a household in its destination settlement");
        Map<SubjectId, ResidentProfile> next = new LinkedHashMap<>(residents); next.put(residentId, resident.relocated(settlementId, householdId));
        return new HumanPopulation(households, next, birthJobs, health, quarantines, migrations, provisions, nutrition, medicalOperations, schedules, meals, mealResourceObligations, clinical, restPlaces, seats);
    }

    public HumanPopulation startBirth(ResidentBirthJob job) {
        Objects.requireNonNull(job, "resident birth job");
        if (birthJobs.containsKey(job.id()) || residents.containsKey(job.resident().id())) throw new IllegalArgumentException("resident birth identity already exists");
        Map<SubjectId, ResidentBirthJob> next = new LinkedHashMap<>(birthJobs); next.put(job.id(), job);
        return new HumanPopulation(households, residents, next, health, quarantines, migrations, provisions, nutrition, medicalOperations, schedules, meals, mealResourceObligations, clinical, restPlaces, seats);
    }

    public HumanPopulation completeBirth(SubjectId jobId) {
        return completeBirth(jobId, FrontierRuleset.ResidentLife.initial());
    }

    public HumanPopulation completeBirth(SubjectId jobId, FrontierRuleset.ResidentLife rules) {
        ResidentBirthJob job = birthJobs.get(Objects.requireNonNull(jobId, "resident birth job id"));
        if (job == null || residents.containsKey(job.resident().id())) throw new IllegalArgumentException("resident birth completion lacks a unique output");
        Map<SubjectId, ResidentBirthJob> next = new LinkedHashMap<>(birthJobs); next.remove(jobId);
        Map<SubjectId, ResidentProfile> nextResidents = new LinkedHashMap<>(residents); nextResidents.put(job.resident().id(), job.resident());
        return new HumanPopulation(households, nextResidents, next, withHealthy(health, job.resident().id(), job.resident().birthTick()), quarantines, migrations,
                provisions, withNourished(nutrition, job.resident().id(), job.resident().birthTick(), rules), medicalOperations, schedules, meals, mealResourceObligations, clinical, restPlaces, seats);
    }

    public HumanPopulation cancelBirth(SubjectId jobId) {
        if (!birthJobs.containsKey(Objects.requireNonNull(jobId, "resident birth job id"))) throw new IllegalArgumentException("unknown resident birth job");
        Map<SubjectId, ResidentBirthJob> next = new LinkedHashMap<>(birthJobs); next.remove(jobId);
        return new HumanPopulation(households, residents, next, health, quarantines, migrations, provisions, nutrition, medicalOperations, schedules, meals, mealResourceObligations, clinical, restPlaces, seats);
    }

    public HumanPopulation transitionHealth(SubjectId residentId, ResidentHealthStatus nextStatus, long tick) {
        ResidentHealth current = health.get(Objects.requireNonNull(residentId, "resident health resident"));
        if (current == null) throw new IllegalArgumentException("unknown resident health subject");
        var next = ImmutableLookupMap.withEntry(health, residentId, current.transition(nextStatus, tick));
        return new HumanPopulation(households, residents, birthJobs, next, quarantines, migrations, provisions, nutrition, medicalOperations, schedules, meals, mealResourceObligations, clinical, restPlaces, seats);
    }

    /** Applies one exact food outcome once to one exact resident. */
    public HumanPopulation resolveNutrition(SubjectId residentId, int cycle, boolean fed) {
        ResidentNutrition current = nutrition(residentId);
        var next = ImmutableLookupMap.withEntry(nutrition, residentId, fed ? current.fed(cycle) : current.missed(cycle));
        return new HumanPopulation(households, residents, birthJobs, health, quarantines, migrations, provisions, next, medicalOperations, schedules, meals, mealResourceObligations, clinical, restPlaces, seats);
    }

    /** Evaluates exactly one person's need at a canonical instant, without a population scan. */
    public HumanPopulation accrueHunger(SubjectId residentId, long canonicalTick) {
        return accrueHunger(residentId, canonicalTick, FrontierRuleset.ResidentLife.initial());
    }

    public HumanPopulation accrueHunger(SubjectId residentId, long canonicalTick,
                                        FrontierRuleset.ResidentLife rules) {
        ResidentNutrition current = nutrition(residentId);
        ResidentNutrition advanced = current.accrueThrough(canonicalTick, rules,
                resident(residentId).characteristics().effectiveMetabolismPermille(canonicalTick));
        if (advanced.equals(current)) return this;
        var next = ImmutableLookupMap.withEntry(nutrition, residentId, advanced);
        return new HumanPopulation(households, residents, birthJobs, health, quarantines, migrations, provisions, next, medicalOperations, schedules, meals, mealResourceObligations, clinical, restPlaces, seats);
    }

    /** Retires one confirmed bread consumption for one exact resident; process checks life. */
    public HumanPopulation consumeResidentBread(SubjectId residentId, long canonicalTick) {
        return consumeResidentBread(residentId, canonicalTick, FrontierRuleset.ResidentLife.initial());
    }

    public HumanPopulation consumeResidentBread(SubjectId residentId, long canonicalTick,
                                                FrontierRuleset.ResidentLife rules) {
        return consumeResidentFood(residentId, canonicalTick, rules.breadNutritionUnits(), rules);
    }

    /** Food retirement owner supplies nutrition from its exact registered consumed portion. */
    public HumanPopulation consumeResidentFood(SubjectId residentId, long canonicalTick, int nutritionUnits,
                                                FrontierRuleset.ResidentLife rules) {
        ResidentNutrition advanced = nutrition(residentId).consumeNutritionAt(canonicalTick, nutritionUnits, rules,
                resident(residentId).characteristics().effectiveMetabolismPermille(canonicalTick));
        var next = ImmutableLookupMap.withEntry(nutrition, residentId, advanced);
        return new HumanPopulation(households, residents, birthJobs, health, quarantines, migrations, provisions, next, medicalOperations, schedules, meals, mealResourceObligations, clinical, restPlaces, seats);
    }

    public HumanPopulation transitionQuarantine(SubjectId settlementId, SettlementQuarantineStatus nextStatus, long tick) {
        SettlementQuarantine current = quarantines.get(Objects.requireNonNull(settlementId, "quarantine settlement"));
        if (current == null) throw new IllegalArgumentException("unknown settlement quarantine");
        Map<SubjectId, SettlementQuarantine> next = new LinkedHashMap<>(quarantines); next.put(settlementId, current.transition(nextStatus, tick));
        return new HumanPopulation(households, residents, birthJobs, health, next, migrations, provisions, nutrition, medicalOperations, schedules, meals, mealResourceObligations, clinical, restPlaces, seats);
    }

    public ResidentMigrationJourney migration(SubjectId residentId) { return migrations.get(residentId); }

    public HumanPopulation startMigration(ResidentMigrationJourney journey) {
        Objects.requireNonNull(journey, "migration journey");
        if (meals.containsKey(journey.residentId())) throw new IllegalArgumentException("resident cannot start migration during a retained meal");
        if (migrations.containsKey(journey.residentId())) throw new IllegalArgumentException("resident already has an active migration journey");
        Map<SubjectId, ResidentMigrationJourney> next = new LinkedHashMap<>(migrations); next.put(journey.residentId(), journey);
        return new HumanPopulation(households, residents, birthJobs, health, quarantines, next, provisions, nutrition, medicalOperations, schedules, meals, mealResourceObligations, clinical, restPlaces, seats);
    }

    public HumanPopulation advanceMigration(SubjectId residentId, int nextRouteIndex) {
        ResidentMigrationJourney journey = requireMigration(residentId);
        Map<SubjectId, ResidentMigrationJourney> next = new LinkedHashMap<>(migrations); next.put(residentId, journey.advanceTo(nextRouteIndex));
        return new HumanPopulation(households, residents, birthJobs, health, quarantines, next, provisions, nutrition, medicalOperations, schedules, meals, mealResourceObligations, clinical, restPlaces, seats);
    }

    public HumanPopulation replaceMigration(ResidentMigrationJourney expected, ResidentMigrationJourney replacement) {
        if (!requireMigration(expected.residentId()).equals(expected)
                || !expected.residentId().equals(replacement.residentId())
                || !expected.executionId().equals(replacement.executionId())
                || !expected.route().equals(replacement.route())
                || !expected.originSettlementId().equals(replacement.originSettlementId())
                || !expected.destinationSettlementId().equals(replacement.destinationSettlementId())
                || !expected.destinationHouseholdId().equals(replacement.destinationHouseholdId()))
            throw new IllegalArgumentException("migration replacement lost its exact retained owner, route or destination");
        Map<SubjectId, ResidentMigrationJourney> next = new LinkedHashMap<>(migrations);
        next.put(expected.residentId(), replacement);
        return new HumanPopulation(households, residents, birthJobs, health, quarantines, next, provisions, nutrition,
                medicalOperations, schedules, meals, mealResourceObligations, clinical, restPlaces, seats);
    }

    public long inboundHousingReservations(SubjectId settlementId) {
        return migrations.values().stream().filter(journey -> journey.destinationSettlementId().equals(settlementId)).count();
    }

    public HumanPopulation blockMigration(SubjectId residentId, ResidentMigrationBlockReason reason) {
        ResidentMigrationJourney journey = requireMigration(residentId);
        Map<SubjectId, ResidentMigrationJourney> next = new LinkedHashMap<>(migrations); next.put(residentId, journey.block(reason));
        return new HumanPopulation(households, residents, birthJobs, health, quarantines, next, provisions, nutrition, medicalOperations, schedules, meals, mealResourceObligations, clinical, restPlaces, seats);
    }

    public HumanPopulation resumeMigration(SubjectId residentId) {
        ResidentMigrationJourney journey = requireMigration(residentId);
        Map<SubjectId, ResidentMigrationJourney> next = new LinkedHashMap<>(migrations); next.put(residentId, journey.resume());
        return new HumanPopulation(households, residents, birthJobs, health, quarantines, next, provisions, nutrition, medicalOperations, schedules, meals, mealResourceObligations, clinical, restPlaces, seats);
    }

    public HumanPopulation completeMigration(SubjectId residentId, SubjectId householdId, SubjectId settlementId) {
        ResidentMigrationJourney journey = requireMigration(residentId);
        if (!journey.arriving() || !journey.destinationHouseholdId().equals(householdId) || !journey.destinationSettlementId().equals(settlementId)) {
            throw new IllegalArgumentException("resident migration completion does not match its journey");
        }
        Map<SubjectId, ResidentProfile> nextResidents = new LinkedHashMap<>(residents);
        nextResidents.put(residentId, residents.get(residentId).relocated(settlementId, householdId));
        Map<SubjectId, ResidentMigrationJourney> nextMigrations = new LinkedHashMap<>(migrations); nextMigrations.remove(residentId);
        return new HumanPopulation(households, nextResidents, birthJobs, health, quarantines, nextMigrations, provisions, nutrition, medicalOperations, schedules, meals, mealResourceObligations, clinical, restPlaces, seats);
    }

    public HumanPopulation cancelMigration(SubjectId residentId) {
        if (!migrations.containsKey(residentId)) return this;
        Map<SubjectId, ResidentMigrationJourney> next = new LinkedHashMap<>(migrations); next.remove(residentId);
        return new HumanPopulation(households, residents, birthJobs, health, quarantines, next, provisions, nutrition, medicalOperations, schedules, meals, mealResourceObligations, clinical, restPlaces, seats);
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
        return new HumanPopulation(households, residents, birthJobs, health, quarantines, migrations, next, nutrition, medicalOperations, schedules, meals, mealResourceObligations, clinical, restPlaces, seats);
    }

    public HumanPopulation startMedicalOperation(MedicalEvacuationOperation operation) {
        Objects.requireNonNull(operation, "medical operation");
        if (medicalOperations.containsKey(operation.id()) || medicalOperations.values().stream().anyMatch(active -> active.active()
                && (active.patientId().equals(operation.patientId()) || active.team().memberIds().stream().anyMatch(operation.team().memberIds()::contains)))) {
            throw new IllegalArgumentException("medical operation conflicts with an active exact patient or team");
        }
        Map<SubjectId, MedicalEvacuationOperation> next = compactCompletedMedicalOperations(); next.put(operation.id(), operation);
        return new HumanPopulation(households, residents, birthJobs, health, quarantines, migrations, provisions, nutrition, next, schedules, meals, mealResourceObligations, clinical, restPlaces, seats);
    }

    public HumanPopulation transitionMedicalOperation(SubjectId operationId, MedicalEvacuationStatus status, long atTick) {
        MedicalEvacuationOperation operation = medicalOperations.get(Objects.requireNonNull(operationId, "medical operation"));
        if (operation == null) throw new IllegalArgumentException("unknown medical operation");
        Map<SubjectId, MedicalEvacuationOperation> next = new LinkedHashMap<>(medicalOperations); next.put(operationId, operation.withStatus(status, atTick));
        return new HumanPopulation(households, residents, birthJobs, health, quarantines, migrations, provisions, nutrition, next, schedules, meals, mealResourceObligations, clinical, restPlaces, seats);
    }

    private Map<SubjectId, MedicalEvacuationOperation> compactCompletedMedicalOperations() {
        java.util.List<MedicalEvacuationOperation> removable = medicalOperations.values().stream().filter(MedicalEvacuationOperation::compactable)
                .sorted(java.util.Comparator.comparingLong(MedicalEvacuationOperation::terminalAtTick).thenComparing(MedicalEvacuationOperation::id)).toList();
        int discard = Math.max(0, removable.size() - MedicalEvacuationOperation.RETAINED_COMPLETED);
        if (discard == 0) return new LinkedHashMap<>(medicalOperations);
        Set<SubjectId> remove = removable.stream().limit(discard).map(MedicalEvacuationOperation::id).collect(java.util.stream.Collectors.toSet());
        Map<SubjectId, MedicalEvacuationOperation> next = new LinkedHashMap<>(medicalOperations); remove.forEach(next::remove);
        return next;
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

    private static Map<SubjectId, SettlementDailySchedule> initialSchedules(Map<SubjectId, ResidentProfile> residents) {
        return initialSchedules(residents, FrontierRuleset.ResidentLife.initial());
    }

    private static Map<SubjectId, SettlementDailySchedule> initialSchedules(Map<SubjectId, ResidentProfile> residents,
                                                                             FrontierRuleset.ResidentLife rules) {
        Map<SubjectId, SettlementDailySchedule> values = new LinkedHashMap<>();
        residents.values().stream().map(ResidentProfile::settlementId).distinct()
                .forEach(id -> values.put(id, SettlementDailySchedule.from(rules)));
        return values;
    }

    private static Map<SubjectId, ResidentNutrition> nourished(Map<SubjectId, ResidentProfile> residents) {
        return nourished(residents, FrontierRuleset.ResidentLife.initial());
    }

    private static Map<SubjectId, ResidentNutrition> nourished(Map<SubjectId, ResidentProfile> residents,
                                                            FrontierRuleset.ResidentLife rules) {
        Map<SubjectId, ResidentNutrition> values = new LinkedHashMap<>();
        residents.values().forEach(resident -> values.put(resident.id(), ResidentNutrition.nourishedAtTick(0, rules)));
        return values;
    }

    private static Map<SubjectId, ResidentHealth> withHealthy(Map<SubjectId, ResidentHealth> source, SubjectId id, long tick) {
        Map<SubjectId, ResidentHealth> next = new LinkedHashMap<>(source);
        if (next.putIfAbsent(id, ResidentHealth.healthyAt(tick)) != null) throw new IllegalArgumentException("resident health identity already exists");
        return next;
    }

    private static Map<SubjectId, ResidentNutrition> withNourished(Map<SubjectId, ResidentNutrition> source, SubjectId id,
                                                                    long birthTick, FrontierRuleset.ResidentLife rules) {
        Map<SubjectId, ResidentNutrition> next = new LinkedHashMap<>(source);
        if (next.putIfAbsent(id, ResidentNutrition.nourishedAtTick(birthTick, rules)) != null)
            throw new IllegalArgumentException("resident nutrition identity already exists");
        return next;
    }

    private ResidentMigrationJourney requireMigration(SubjectId residentId) {
        ResidentMigrationJourney journey = migrations.get(Objects.requireNonNull(residentId, "migration resident"));
        if (journey == null) throw new IllegalArgumentException("resident has no active migration journey");
        return journey;
    }

    private static <K, V> Map<K, V> immutable(Map<K, V> source, String name) {
        return ImmutableLookupMap.copyOf(Objects.requireNonNull(source, name));
    }
}
