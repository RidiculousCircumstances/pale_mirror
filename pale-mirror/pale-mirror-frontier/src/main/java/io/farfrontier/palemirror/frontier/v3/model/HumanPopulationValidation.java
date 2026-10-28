package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** Population-owned component invariants. Successful immutable dependencies may
 * be shared; changed rows/references are checked before publication, and full
 * ingress/recovery audits deliberately ignore prior evidence. */
final class HumanPopulationValidation {
    record Inputs(Map<SubjectId, Household> households, Map<SubjectId, ResidentProfile> residents,
                  Map<SubjectId, ResidentBirthJob> birthJobs, Map<SubjectId, ResidentHealth> health,
                  Map<SubjectId, SettlementQuarantine> quarantines, Map<SubjectId, ResidentMigrationJourney> migrations,
                  Map<SubjectId, SettlementProvision> provisions, Map<SubjectId, ResidentNutrition> nutrition,
                  Map<SubjectId, MedicalEvacuationOperation> medicalOperations, Map<SubjectId, SettlementDailySchedule> schedules,
                  Map<SubjectId, ResidentMeal> meals, Map<SubjectId, ResidentMealResourceObligation> mealResourceObligations) { }
    private static final ImmutableInvariantCheck<Void> HOUSEHOLDS = new ImmutableInvariantCheck<>();
    private static final ImmutableInvariantCheck<Void> RESIDENTS = new ImmutableInvariantCheck<>();
    private static final ImmutableInvariantCheck<Void> HEALTH = new ImmutableInvariantCheck<>();
    private static final ImmutableInvariantCheck<Void> NUTRITION = new ImmutableInvariantCheck<>();
    private static final ImmutableInvariantCheck<Void> QUARANTINES = new ImmutableInvariantCheck<>();
    private static final ImmutableInvariantCheck<Void> SCHEDULES = new ImmutableInvariantCheck<>();
    private static final ImmutableInvariantCheck<Void> PROVISIONS = new ImmutableInvariantCheck<>();
    private static final ImmutableInvariantCheck<Void> BIRTHS = new ImmutableInvariantCheck<>();
    private static final ImmutableInvariantCheck<Void> MIGRATIONS = new ImmutableInvariantCheck<>();
    private static final ImmutableInvariantCheck<Void> MEDICAL = new ImmutableInvariantCheck<>();
    private static final ImmutableInvariantCheck<Set<SubjectId>> INHABITED = new ImmutableInvariantCheck<>();
    private static final ImmutableReferenceIndex.Source<Inputs, SubjectId, ResidentProfile> RESIDENT =
            new ImmutableReferenceIndex.Source<>(Inputs::residents);
    private static final ImmutableReferenceIndex.Source<Inputs, SubjectId, ResidentMigrationJourney> MIGRATION =
            new ImmutableReferenceIndex.Source<>(Inputs::migrations);
    private static final ImmutableReferenceIndex.Source<Inputs, SubjectId, ResidentMeal> ACTIVE_MEAL =
            new ImmutableReferenceIndex.Source<>(Inputs::meals);
    private static final ImmutableReferenceIndex<Inputs, SubjectId, ResidentMeal> MEALS = new ImmutableReferenceIndex<>(
            HumanPopulation.MAX_RESIDENTS, 2, List.of(RESIDENT, MIGRATION), (input, meal) ->
                    List.of(RESIDENT.at(meal.residentId()), MIGRATION.at(meal.residentId())),
            (input, id, meal) -> {
                var resident = input.residents().get(id);
                if (resident == null || !id.equals(meal.residentId())
                        || !resident.settlementId().equals(meal.settlementId()) || input.migrations().containsKey(id))
                    throw new IllegalArgumentException("retained meal must belong to one current resident and settlement");
            });
    private static final ImmutableReferenceIndex<Inputs, SubjectId, ResidentMealResourceObligation> OBLIGATIONS = new ImmutableReferenceIndex<>(
            HumanPopulation.MAX_RESIDENTS, 3, List.of(RESIDENT, MIGRATION, ACTIVE_MEAL), (input, obligation) ->
                    List.of(RESIDENT.at(obligation.residentId()), MIGRATION.at(obligation.residentId()), ACTIVE_MEAL.at(obligation.residentId())),
            (input, id, obligation) -> {
                var resident = input.residents().get(id);
                if (resident == null || !id.equals(obligation.residentId())
                        || !resident.settlementId().equals(obligation.settlementId())
                        || input.meals().containsKey(id) || input.migrations().containsKey(id))
                    throw new IllegalArgumentException("retired meal resources require one separate exact resident owner");
            });
    static void validate(Inputs input) {
        var households = input.households();
        var residents = input.residents();
        var birthJobs = input.birthJobs();
        var health = input.health();
        var quarantines = input.quarantines();
        var migrations = input.migrations();
        var provisions = input.provisions();
        var nutrition = input.nutrition();
        var medicalOperations = input.medicalOperations();
        var schedules = input.schedules();
        var meals = input.meals();
        var mealResourceObligations = input.mealResourceObligations();
        HOUSEHOLDS.validate(() -> {
            for (Map.Entry<SubjectId, Household> entry : households.entrySet()) {
                if (!entry.getKey().equals(entry.getValue().id())) throw new IllegalArgumentException("household map key must match identity");
            }
        }, households);
        RESIDENTS.validate(() -> {
            for (Map.Entry<SubjectId, ResidentProfile> entry : residents.entrySet()) {
                ResidentProfile resident = entry.getValue();
                if (!entry.getKey().equals(resident.id())) throw new IllegalArgumentException("resident map key must match identity");
                Household household = households.get(resident.householdId());
                if (household == null || !household.settlementId().equals(resident.settlementId())) {
                    throw new IllegalArgumentException("resident must belong to a household in the same settlement");
                }
            }
        }, residents, households);
        HEALTH.validate(() -> {
            if (!health.keySet().equals(residents.keySet())) throw new IllegalArgumentException("resident health must cover every and only exact resident");
        }, health, residents);
        NUTRITION.validate(() -> {
            if (!nutrition.keySet().equals(residents.keySet())) throw new IllegalArgumentException("resident nutrition must cover every and only exact resident");
        }, nutrition, residents);
        var residentSettlements = INHABITED.value(() -> residents.values().stream().map(ResidentProfile::settlementId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet()), residents);
        QUARANTINES.validate(() -> {
            if (!quarantines.keySet().containsAll(residentSettlements)) throw new IllegalArgumentException("settlement quarantine must cover every resident settlement");
        }, quarantines, residentSettlements);
        SCHEDULES.validate(() -> {
            if (!schedules.keySet().containsAll(residentSettlements) || schedules.size() > HumanPopulation.MAX_HOUSEHOLDS)
                throw new IllegalArgumentException("daily schedule must cover every inhabited settlement");
        }, schedules, residentSettlements);
        MEALS.validate(HumanPopulationValidation.class, meals, input);
        if (mealResourceObligations.size() > HumanPopulation.MAX_RESIDENTS)
            throw new IllegalArgumentException("retired meal resource retention limit exceeded");
        OBLIGATIONS.validate(HumanPopulationValidation.class, mealResourceObligations, input);
        PROVISIONS.validate(() -> {
            for (Map.Entry<SubjectId, SettlementProvision> entry : provisions.entrySet()) {
                if (!entry.getKey().equals(entry.getValue().settlementId())) throw new IllegalArgumentException("settlement provision map key must match identity");
            }
        }, provisions);
        BIRTHS.validate(() -> {
            java.util.Set<SubjectId> commitments = new java.util.HashSet<>();
            for (Map.Entry<SubjectId, ResidentBirthJob> entry : birthJobs.entrySet()) {
                ResidentBirthJob job = entry.getValue(); Household household = households.get(job.householdId());
                if (!entry.getKey().equals(job.id()) || household == null || !household.settlementId().equals(job.settlementId())
                        || residents.containsKey(job.resident().id()) || !commitments.add(job.foodCommitmentId())) {
                    throw new IllegalArgumentException("resident birth job has invalid exact identity or household");
                }
            }
        }, birthJobs, households, residents);
        MIGRATIONS.validate(() -> {
            for (Map.Entry<SubjectId, ResidentMigrationJourney> entry : migrations.entrySet()) {
                ResidentMigrationJourney journey = entry.getValue(); ResidentProfile resident = residents.get(journey.residentId());
                Household destination = households.get(journey.destinationHouseholdId());
                if (!entry.getKey().equals(journey.residentId()) || resident == null || !resident.settlementId().equals(journey.originSettlementId())
                        || destination == null || !destination.settlementId().equals(journey.destinationSettlementId())) {
                    throw new IllegalArgumentException("migration journey must bind one resident, origin and destination household");
                }
            }
        }, migrations, residents, households);
        MEDICAL.validate(() -> {
            if (medicalOperations.size() > MedicalEvacuationOperation.MAX_RETAINED) throw new IllegalArgumentException("medical operation retention limit exceeded");
            for (Map.Entry<SubjectId, MedicalEvacuationOperation> entry : medicalOperations.entrySet()) {
                MedicalEvacuationOperation operation = entry.getValue();
                ResidentProfile patient = residents.get(operation.patientId());
                boolean foreignMember = false;
                for (SubjectId member : operation.team().memberIds()) {
                    ResidentProfile medic = residents.get(member);
                    if (medic == null || !medic.settlementId().equals(operation.settlementId())) { foreignMember = true; break; }
                }
                if (!entry.getKey().equals(operation.id()) || patient == null || !patient.settlementId().equals(operation.settlementId()) || foreignMember) {
                    throw new IllegalArgumentException("medical operation must retain local exact patient and team");
                }
            }
        }, medicalOperations, residents);
    }
    private HumanPopulationValidation() { }
}
