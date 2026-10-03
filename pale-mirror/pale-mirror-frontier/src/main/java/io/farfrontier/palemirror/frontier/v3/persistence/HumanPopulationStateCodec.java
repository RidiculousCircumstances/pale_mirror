package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder;
import io.farfrontier.palemirror.frontier.v3.model.navigation.TimedKnownRoute;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

/** Schema fragment for exact households, people, birth permits and COLD migration journeys. */
final class HumanPopulationStateCodec {
    private HumanPopulationStateCodec() { }

    static void write(DataOutputStream output, HumanPopulation population) throws IOException {
        FrontierWorldStateCodec.writeCount(output, population.households().size());
        for (Household household : population.households().values().stream().sorted(Comparator.comparing(Household::id)).toList()) {
            FrontierWorldStateCodec.writeString(output, household.id().value()); FrontierWorldStateCodec.writeString(output, household.settlementId().value());
        }
        FrontierWorldStateCodec.writeCount(output, population.residents().size());
        for (ResidentProfile resident : population.residents().values().stream().sorted(Comparator.comparing(ResidentProfile::id)).toList()) writeProfile(output, resident);
        FrontierWorldStateCodec.writeCount(output, population.birthJobs().size());
        for (ResidentBirthJob job : population.birthJobs().values().stream().sorted(Comparator.comparing(ResidentBirthJob::id)).toList()) {
            FrontierWorldStateCodec.writeString(output, job.id().value()); FrontierWorldStateCodec.writeString(output, job.settlementId().value());
            FrontierWorldStateCodec.writeString(output, job.householdId().value()); FrontierWorldStateCodec.writeString(output, job.foodItemId().value());
            FrontierWorldStateCodec.writeString(output, job.foodCommitmentId().value()); writeProfile(output, job.resident()); FrontierWorldStateCodec.writePosition(output, job.position());
        }
        FrontierWorldStateCodec.writeCount(output, population.health().size());
        for (Map.Entry<SubjectId, ResidentHealth> entry : population.health().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            FrontierWorldStateCodec.writeString(output, entry.getKey().value()); output.writeByte(entry.getValue().status().wireTag()); output.writeLong(entry.getValue().sinceTick());
            HumanPopulationPayloadCodecs.writeStarvation(output, entry.getValue().starvation());
        }
        FrontierWorldStateCodec.writeCount(output, population.nutrition().size());
        for (Map.Entry<SubjectId, ResidentNutrition> entry : population.nutrition().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            FrontierWorldStateCodec.writeString(output, entry.getKey().value()); output.writeByte(entry.getValue().status().wireTag());
            FrontierWorldStateCodec.writeCount(output, entry.getValue().satietyUnits());
            output.writeLong(entry.getValue().lastEvaluatedTick()); output.writeLong(entry.getValue().fractionalProgress());
        }
        FrontierWorldStateCodec.writeCount(output, population.quarantines().size());
        for (Map.Entry<SubjectId, SettlementQuarantine> entry : population.quarantines().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            FrontierWorldStateCodec.writeString(output, entry.getKey().value()); output.writeByte(entry.getValue().status().wireTag()); output.writeLong(entry.getValue().sinceTick());
        }
        FrontierWorldStateCodec.writeCount(output, population.migrations().size());
        for (ResidentMigrationJourney journey : population.migrations().values().stream().sorted(Comparator.comparing(ResidentMigrationJourney::residentId)).toList()) {
            FrontierWorldStateCodec.writeString(output, journey.residentId().value()); FrontierWorldStateCodec.writeString(output, journey.originSettlementId().value());
            FrontierWorldStateCodec.writeString(output, journey.destinationHouseholdId().value()); FrontierWorldStateCodec.writeString(output, journey.destinationSettlementId().value());
            FrontierWorldStateCodec.writeCount(output, journey.route().size());
            for (BlockPosition position : journey.route()) FrontierWorldStateCodec.writePosition(output, position);
            FrontierWorldStateCodec.writeCount(output, journey.routeIndex()); output.writeByte(journey.status().wireTag()); output.writeBoolean(journey.blockReason().isPresent());
            if (journey.blockReason().isPresent()) output.writeByte(journey.blockReason().orElseThrow().wireTag());
            ActorExecutionStateCodec.writeId(output, journey.executionId());
        }
        FrontierWorldStateCodec.writeCount(output, population.provisions().size());
        for (SettlementProvision provision : population.provisions().values().stream().sorted(Comparator.comparing(SettlementProvision::settlementId)).toList()) {
            FrontierWorldStateCodec.writeString(output, provision.settlementId().value()); FrontierWorldStateCodec.writeCount(output, provision.cycleOrdinal());
            output.writeLong(provision.startedAtTick()); FrontierWorldStateCodec.writeCount(output, provision.requiredRations());
            FrontierWorldStateCodec.writeCount(output, provision.fulfilledRations()); FrontierWorldStateCodec.writeCount(output, provision.recipientIds().size());
            for (SubjectId recipient : provision.recipientIds()) FrontierWorldStateCodec.writeString(output, recipient.value());
            FrontierWorldStateCodec.writeCount(output, provision.allocations().size());
            for (SettlementRationAllocation allocation : provision.allocations()) {
                FrontierWorldStateCodec.writeString(output, allocation.itemId().value()); output.writeBoolean(allocation.fungible());
                if (allocation.fungible()) {
                    FrontierWorldStateCodec.writeString(output, allocation.fungibleSource().orElseThrow().accountId().value());
                    FrontierWorldStateCodec.writeString(output, allocation.fungibleSource().orElseThrow().claimId().value());
                }
                FrontierWorldStateCodec.writeCount(output, allocation.recipientIds().size());
                for (SubjectId recipient : allocation.recipientIds()) FrontierWorldStateCodec.writeString(output, recipient.value());
            }
            FrontierWorldStateCodec.writeCount(output, provision.nextAllocation()); output.writeByte(provision.status().wireTag()); output.writeBoolean(provision.activeIntentId().isPresent());
            if (provision.activeIntentId().isPresent()) FrontierWorldStateCodec.writeString(output, provision.activeIntentId().orElseThrow().value());
        }
        FrontierWorldStateCodec.writeCount(output, population.medicalOperations().size());
        for (MedicalEvacuationOperation operation : population.medicalOperations().values().stream().sorted(Comparator.comparing(MedicalEvacuationOperation::id)).toList()) {
            FrontierWorldStateCodec.writeString(output, operation.id().value()); FrontierWorldStateCodec.writeString(output, operation.settlementId().value());
            FrontierWorldStateCodec.writeString(output, operation.patientId().value()); FrontierWorldStateCodec.writeString(output, operation.infirmaryId().value());
            FrontierWorldStateCodec.writeString(output, operation.team().id().value()); FrontierWorldStateCodec.writeString(output, operation.team().leaderId().value());
            FrontierWorldStateCodec.writeCount(output, operation.team().memberIds().size());
            for (SubjectId member : operation.team().memberIds()) FrontierWorldStateCodec.writeString(output, member.value());
            FrontierWorldStateCodec.writeString(output, operation.supplyItemId().value()); FrontierWorldStateCodec.writeString(output, operation.consumptionIntentId().value());
            output.writeByte(operation.status().wireTag()); output.writeLong(operation.terminalAtTick());
        }
        FrontierWorldStateCodec.writeCount(output, population.schedules().size());
        for (Map.Entry<SubjectId, SettlementDailySchedule> entry : population.schedules().entrySet().stream()
                .sorted(Map.Entry.comparingByKey()).toList()) {
            FrontierWorldStateCodec.writeString(output, entry.getKey().value());
            output.writeInt(entry.getValue().dayTicks());
            FrontierWorldStateCodec.writeCount(output, entry.getValue().segments().size());
            for (SettlementDailySchedule.Segment segment : entry.getValue().segments()) {
                output.writeInt(segment.startInclusive()); output.writeInt(segment.endExclusive());
                output.writeByte(FrontierWireTags.tag(segment.window()));
            }
        }
        FrontierWorldStateCodec.writeCount(output, population.meals().size());
        for (ResidentMeal meal : population.meals().values().stream().sorted(Comparator.comparing(ResidentMeal::residentId)).toList()) {
            writeMeal(output, meal);
        }
    }

    static HumanPopulation read(DataInputStream input, boolean hasHealth, boolean hasMigrations, boolean hasProvisions, boolean hasNutrition,
                                boolean hasCapabilityProfile, boolean hasMedicalOperations, boolean hasMedicalTerminalTick,
                                boolean hasResidentLife) throws IOException {
        Map<SubjectId, Household> households = new LinkedHashMap<>();
        for (int index = 0, count = FrontierWorldStateCodec.readCount(input); index < count; index++) {
            SubjectId id = new SubjectId(FrontierWorldStateCodec.readString(input));
            if (households.put(id, new Household(id, new SubjectId(FrontierWorldStateCodec.readString(input)))) != null) throw new IllegalArgumentException("duplicate household id");
        }
        Map<SubjectId, ResidentProfile> residents = new LinkedHashMap<>();
        for (int index = 0, count = FrontierWorldStateCodec.readCount(input); index < count; index++) {
            ResidentProfile resident = readProfile(input, hasCapabilityProfile);
            if (residents.put(resident.id(), resident) != null) throw new IllegalArgumentException("duplicate resident id");
        }
        Map<SubjectId, ResidentBirthJob> birthJobs = new LinkedHashMap<>();
        for (int index = 0, count = FrontierWorldStateCodec.readCount(input); index < count; index++) {
            SubjectId id = new SubjectId(FrontierWorldStateCodec.readString(input)); SubjectId settlement = new SubjectId(FrontierWorldStateCodec.readString(input));
            SubjectId household = new SubjectId(FrontierWorldStateCodec.readString(input)); SubjectId food = new SubjectId(FrontierWorldStateCodec.readString(input));
            ResidentBirthJob job = new ResidentBirthJob(id, settlement, household, food, new SubjectId(FrontierWorldStateCodec.readString(input)),
                    readProfile(input, hasCapabilityProfile), FrontierWorldStateCodec.readPosition(input));
            if (birthJobs.put(id, job) != null) throw new IllegalArgumentException("duplicate resident birth job");
        }
        if (!hasHealth) return new HumanPopulation(households, residents, birthJobs);
        Map<SubjectId, ResidentHealth> health = new LinkedHashMap<>();
        for (int index = 0, count = FrontierWorldStateCodec.readCount(input); index < count; index++) {
            SubjectId id = new SubjectId(FrontierWorldStateCodec.readString(input)); int status = input.readUnsignedByte();
            if (status >= ResidentHealthStatus.values().length || health.put(id, new ResidentHealth(FrontierWireTags.require(ResidentHealthStatus.class, status), input.readLong(),
                    HumanPopulationPayloadCodecs.readStarvation(input))) != null) {
                throw new IllegalArgumentException("invalid or duplicate resident health");
            }
        }
        Map<SubjectId, ResidentNutrition> nutrition = new LinkedHashMap<>();
        if (hasNutrition) {
            for (int index = 0, count = FrontierWorldStateCodec.readCount(input); index < count; index++) {
                SubjectId id = new SubjectId(FrontierWorldStateCodec.readString(input)); int status = input.readUnsignedByte();
                if (status >= ResidentNutritionStatus.values().length || nutrition.put(id, new ResidentNutrition(FrontierWireTags.require(ResidentNutritionStatus.class, status),
                        FrontierWorldStateCodec.readCount(input), input.readLong(), input.readLong())) != null) {
                    throw new IllegalArgumentException("invalid or duplicate resident nutrition");
                }
            }
        }
        Map<SubjectId, SettlementQuarantine> quarantines = new LinkedHashMap<>();
        for (int index = 0, count = FrontierWorldStateCodec.readCount(input); index < count; index++) {
            SubjectId id = new SubjectId(FrontierWorldStateCodec.readString(input)); int status = input.readUnsignedByte();
            if (status >= SettlementQuarantineStatus.values().length
                    || quarantines.put(id, new SettlementQuarantine(FrontierWireTags.require(SettlementQuarantineStatus.class, status), input.readLong())) != null) {
                throw new IllegalArgumentException("invalid or duplicate settlement quarantine");
            }
        }
        if (!hasMigrations) return new HumanPopulation(households, residents, birthJobs, health, quarantines);
        Map<SubjectId, ResidentMigrationJourney> migrations = new LinkedHashMap<>();
        for (int index = 0, count = FrontierWorldStateCodec.readCount(input); index < count; index++) {
            SubjectId resident = new SubjectId(FrontierWorldStateCodec.readString(input)); SubjectId origin = new SubjectId(FrontierWorldStateCodec.readString(input));
            SubjectId household = new SubjectId(FrontierWorldStateCodec.readString(input)); SubjectId destination = new SubjectId(FrontierWorldStateCodec.readString(input));
            java.util.List<BlockPosition> route = new java.util.ArrayList<>();
            for (int point = 0, points = FrontierWorldStateCodec.readCount(input); point < points; point++) route.add(FrontierWorldStateCodec.readPosition(input));
            int routeIndex = FrontierWorldStateCodec.readCount(input); int status = input.readUnsignedByte(); boolean blocked = input.readBoolean();
            if (status >= ResidentMigrationStatus.values().length) throw new IllegalArgumentException("unknown resident migration status");
            java.util.Optional<ResidentMigrationBlockReason> reason = blocked
                    ? java.util.Optional.of(readBlockReason(input)) : java.util.Optional.empty();
            ResidentMigrationJourney journey = new ResidentMigrationJourney(resident, origin, household, destination, route, routeIndex,
                    FrontierWireTags.require(ResidentMigrationStatus.class, status), reason, ActorExecutionStateCodec.readId(input));
            if (migrations.put(resident, journey) != null) throw new IllegalArgumentException("duplicate resident migration journey");
        }
        if (!hasProvisions) return new HumanPopulation(households, residents, birthJobs, health, quarantines, migrations);
        Map<SubjectId, SettlementProvision> provisions = new LinkedHashMap<>();
        for (int index = 0, count = FrontierWorldStateCodec.readCount(input); index < count; index++) {
            SubjectId settlement = new SubjectId(FrontierWorldStateCodec.readString(input)); int cycle = FrontierWorldStateCodec.readCount(input);
            long startedAt = input.readLong(); int required = FrontierWorldStateCodec.readCount(input); int fulfilled = FrontierWorldStateCodec.readCount(input);
            java.util.List<SubjectId> recipients = new java.util.ArrayList<>();
            if (hasNutrition) {
                for (int recipient = 0, recipientCount = FrontierWorldStateCodec.readCount(input); recipient < recipientCount; recipient++) {
                    recipients.add(new SubjectId(FrontierWorldStateCodec.readString(input)));
                }
            } else recipients.addAll(legacyRecipients(residents, settlement, required));
            java.util.List<SettlementRationAllocation> allocations = new java.util.ArrayList<>();
            for (int allocation = 0, allocationCount = FrontierWorldStateCodec.readCount(input); allocation < allocationCount; allocation++) {
                SubjectId item = new SubjectId(FrontierWorldStateCodec.readString(input)); boolean fungible = input.readBoolean();
                SettlementRationAllocation.FungibleSource source = fungible ? new SettlementRationAllocation.FungibleSource(
                        new SubjectId(FrontierWorldStateCodec.readString(input)), new SubjectId(FrontierWorldStateCodec.readString(input))) : null;
                java.util.List<SubjectId> allocationRecipients = new java.util.ArrayList<>();
                if (hasNutrition) {
                    for (int recipient = 0, recipientCount = FrontierWorldStateCodec.readCount(input); recipient < recipientCount; recipient++) {
                        allocationRecipients.add(new SubjectId(FrontierWorldStateCodec.readString(input)));
                    }
                } else {
                    int legacyCount = FrontierWorldStateCodec.readCount(input); int start = allocations.stream().mapToInt(SettlementRationAllocation::count).sum();
                    allocationRecipients.addAll(recipients.subList(start, Math.min(Math.addExact(start, legacyCount), recipients.size())));
                }
                allocations.add(fungible ? new SettlementRationAllocation(item, allocationRecipients, java.util.Optional.of(source))
                        : new SettlementRationAllocation(item, allocationRecipients));
            }
            int next = FrontierWorldStateCodec.readCount(input); int status = input.readUnsignedByte(); boolean active = input.readBoolean();
            if (status >= SettlementProvisionStatus.values().length) throw new IllegalArgumentException("unknown settlement provision status");
            java.util.Optional<PhysicalIntentId> intent = active ? java.util.Optional.of(new PhysicalIntentId(FrontierWorldStateCodec.readString(input))) : java.util.Optional.empty();
            SettlementProvision provision = new SettlementProvision(settlement, cycle, startedAt, required, fulfilled, recipients, allocations, next,
                    FrontierWireTags.require(SettlementProvisionStatus.class, status), intent);
            if (provisions.put(settlement, provision) != null) throw new IllegalArgumentException("duplicate settlement provision");
        }
        if (!hasNutrition) nutrition = legacyNutrition(residents, provisions);
        Map<SubjectId, MedicalEvacuationOperation> medicalOperations = new LinkedHashMap<>();
        if (hasMedicalOperations) {
            for (int index = 0, count = FrontierWorldStateCodec.readCount(input); index < count; index++) {
                SubjectId id = new SubjectId(FrontierWorldStateCodec.readString(input)); SubjectId settlement = new SubjectId(FrontierWorldStateCodec.readString(input));
                SubjectId patient = new SubjectId(FrontierWorldStateCodec.readString(input)); SubjectId infirmary = new SubjectId(FrontierWorldStateCodec.readString(input));
                SubjectId teamId = new SubjectId(FrontierWorldStateCodec.readString(input)); SubjectId leader = new SubjectId(FrontierWorldStateCodec.readString(input));
                java.util.List<SubjectId> members = new java.util.ArrayList<>();
                for (int member = 0, memberCount = FrontierWorldStateCodec.readCount(input); member < memberCount; member++) members.add(new SubjectId(FrontierWorldStateCodec.readString(input)));
                SubjectId supply = new SubjectId(FrontierWorldStateCodec.readString(input));
                PhysicalIntentId intent = new PhysicalIntentId(FrontierWorldStateCodec.readString(input)); int status = input.readUnsignedByte();
                if (status >= MedicalEvacuationStatus.values().length) throw new IllegalArgumentException("unknown medical operation status");
                MedicalEvacuationTeam team = new MedicalEvacuationTeam(teamId, id, settlement, leader, members);
                MedicalEvacuationStatus medicalStatus = FrontierWireTags.require(MedicalEvacuationStatus.class, status);
                long terminalAtTick = hasMedicalTerminalTick ? input.readLong() : medicalStatus == MedicalEvacuationStatus.COMPLETED ? 0L : -1L;
                MedicalEvacuationOperation operation = new MedicalEvacuationOperation(id, settlement, patient, infirmary, team, supply, intent,
                        medicalStatus, terminalAtTick);
                if (medicalOperations.put(id, operation) != null) throw new IllegalArgumentException("duplicate medical operation id");
            }
        }
        if (!hasResidentLife) return new HumanPopulation(households, residents, birthJobs, health, quarantines,
                migrations, provisions, nutrition, medicalOperations);
        Map<SubjectId, SettlementDailySchedule> schedules = new LinkedHashMap<>();
        for (int index = 0, count = FrontierWorldStateCodec.readCount(input); index < count; index++) {
            SubjectId id = new SubjectId(FrontierWorldStateCodec.readString(input));
            int dayTicks = input.readInt();
            java.util.List<SettlementDailySchedule.Segment> segments = new java.util.ArrayList<>();
            for (int segment = 0, segmentCount = FrontierWorldStateCodec.readCount(input);
                 segment < segmentCount; segment++) {
                int start = input.readInt(), end = input.readInt(), tag = input.readUnsignedByte();
                segments.add(new SettlementDailySchedule.Segment(start, end,
                        FrontierWireTags.require(SettlementDailySchedule.Window.class, tag)));
            }
            if (schedules.put(id, new SettlementDailySchedule(dayTicks, segments)) != null)
                throw new IllegalArgumentException("duplicate settlement schedule");
        }
        Map<SubjectId, ResidentMeal> meals = new LinkedHashMap<>();
        for (int index = 0, count = FrontierWorldStateCodec.readCount(input); index < count; index++) {
            ResidentMeal meal = readMeal(input);
            if (meals.put(meal.residentId(), meal) != null) throw new IllegalArgumentException("duplicate resident meal");
        }
        return new HumanPopulation(households, residents, birthJobs, health, quarantines, migrations,
                provisions, nutrition, medicalOperations, schedules, meals);
    }

    static void writeMeal(DataOutputStream output, ResidentMeal meal) throws IOException {
        FrontierWorldStateCodec.writeString(output, meal.residentId().value());
        FrontierWorldStateCodec.writeString(output, meal.settlementId().value());
        FrontierWorldStateCodec.writeString(output, meal.depotId().value());
        FrontierWorldStateCodec.writePosition(output, meal.clearingSurface().support());
        FrontierWorldStateCodec.writeString(output, meal.sourceAccountId().value());
        FrontierWorldStateCodec.writeString(output, meal.actorAccountId().value());
        writeFoodPortion(output, meal.portion());
        FrontierWorldStateCodec.writeString(output, meal.claimId().value());
        ActorExecutionStateCodec.writeId(output, meal.executionId());
        output.writeBoolean(meal.retainedWorkOwner().isPresent());
        if (meal.retainedWorkOwner().isPresent()) FrontierWorldStateCodec.writeString(output, meal.retainedWorkOwner().orElseThrow().value());
        output.writeByte(FrontierWireTags.tag(meal.phase())); output.writeLong(meal.startedAtTick());
        output.writeBoolean(meal.waitReason().isPresent());
        if (meal.waitReason().isPresent()) output.writeByte(FrontierWireTags.tag(meal.waitReason().orElseThrow()));
        output.writeBoolean(meal.pendingPhysicalStep().isPresent());
        if (meal.pendingPhysicalStep().isPresent()) {
            ResidentMealPhysicalStep step = meal.pendingPhysicalStep().orElseThrow();
            writeMealPhysicalStep(output, step);
        }
        output.writeBoolean(meal.coldTravel().isPresent());
        if (meal.coldTravel().isPresent()) {
            TimedKnownRoute travel = meal.coldTravel().orElseThrow();
            output.writeLong(travel.departedAtTick()); output.writeLong(travel.ticksPerEdge());
            output.writeLong(travel.authorityEpoch());
            FrontierWorldStateCodec.writeCount(output, travel.route().size());
            for (SurfaceAnchor surface : travel.route()) FrontierWorldStateCodec.writePosition(output, surface.support());
        }
    }

    static void writeFoodPortion(DataOutputStream output, FoodPortion portion) throws IOException {
        FrontierWorldStateCodec.writeString(output, portion.itemKind()); output.writeInt(portion.nutritionPerItem());
        output.writeByte(portion.lotQuantities().size());
        for (var entry : new java.util.TreeMap<>(portion.lotQuantities()).entrySet()) {
            FrontierWorldStateCodec.writeString(output, entry.getKey().value()); output.writeByte(entry.getValue());
        }
    }

    static FoodPortion readFoodPortion(DataInputStream input) throws IOException {
        String kind = FrontierWorldStateCodec.readString(input); int nutrition = input.readInt();
        int count = input.readUnsignedByte();
        if (count < 1 || count > 64) throw new IllegalArgumentException("invalid portion lot count");
        Map<SubjectId, Integer> lots = new LinkedHashMap<>();
        for (int i = 0; i < count; i++)
            if (lots.put(new SubjectId(FrontierWorldStateCodec.readString(input)), input.readUnsignedByte()) != null)
                throw new IllegalArgumentException("duplicate portion lot");
        return new FoodPortion(kind, nutrition, lots);
    }

    static void writeMealPhysicalStep(DataOutputStream output, ResidentMealPhysicalStep step) throws IOException {
        output.writeByte(FrontierWireTags.tag(step.phase())); output.writeByte(step.sourceCounts().size());
        for (var entry : new java.util.TreeMap<>(step.sourceCounts()).entrySet()) {
            output.writeByte(entry.getKey()); output.writeByte(entry.getValue());
        }
        output.writeByte(step.consumptionQuantity()); output.writeLong(step.sourceEpoch());
        output.writeLong(step.destinationEpoch()); output.writeLong(step.ambientRevision());
        ActorExecutionStateCodec.writeId(output, step.executionId());
    }

    static ResidentMealPhysicalStep readMealPhysicalStep(DataInputStream input) throws IOException {
        var phase = FrontierWireTags.require(ResidentMeal.Phase.class, input.readUnsignedByte());
        int count = input.readUnsignedByte();
        if (count > 27) throw new IllegalArgumentException("too many meal source slots");
        Map<Integer, Integer> sources = new LinkedHashMap<>();
        for (int i = 0; i < count; i++)
            if (sources.put(input.readUnsignedByte(), input.readUnsignedByte()) != null)
                throw new IllegalArgumentException("duplicate meal source slot");
        return new ResidentMealPhysicalStep(phase, sources, input.readUnsignedByte(),
                input.readLong(), input.readLong(), input.readLong(), ActorExecutionStateCodec.readId(input));
    }

    static ResidentMeal readMeal(DataInputStream input) throws IOException {
        SubjectId resident = new SubjectId(FrontierWorldStateCodec.readString(input));
        SubjectId settlement = new SubjectId(FrontierWorldStateCodec.readString(input));
        SubjectId depot = new SubjectId(FrontierWorldStateCodec.readString(input));
        SurfaceAnchor clearing = new SurfaceAnchor(FrontierWorldStateCodec.readPosition(input));
        SubjectId source = new SubjectId(FrontierWorldStateCodec.readString(input));
        SubjectId actor = new SubjectId(FrontierWorldStateCodec.readString(input));
        FoodPortion portion = readFoodPortion(input);
        SubjectId claim = new SubjectId(FrontierWorldStateCodec.readString(input));
        var executionId = ActorExecutionStateCodec.readId(input);
        java.util.Optional<SubjectId> retained = input.readBoolean()
                ? java.util.Optional.of(new SubjectId(FrontierWorldStateCodec.readString(input)))
                : java.util.Optional.empty();
        int phase = input.readUnsignedByte(); long started = input.readLong();
        java.util.Optional<ResidentActivityChoice.Wait> wait = java.util.Optional.empty();
        if (input.readBoolean()) {
            int tag = input.readUnsignedByte();
            wait = java.util.Optional.of(FrontierWireTags.require(ResidentActivityChoice.Wait.class, tag));
        }
        java.util.Optional<ResidentMealPhysicalStep> pending = java.util.Optional.empty();
        if (input.readBoolean()) pending = java.util.Optional.of(readMealPhysicalStep(input));
        ResidentMeal meal = new ResidentMeal(resident, settlement, depot, clearing, source, actor, portion,
                claim, retained, FrontierWireTags.require(ResidentMeal.Phase.class, phase), started, wait, pending, executionId);
        if (!input.readBoolean()) return meal;
        long departedAt = input.readLong(), ticksPerEdge = input.readLong(), epoch = input.readLong();
        int length = FrontierWorldStateCodec.readCount(input);
        if (length < 1 || length > TimedKnownRoute.MAX_SURFACES)
            throw new IllegalArgumentException("meal COLD route length is invalid");
        java.util.ArrayList<SurfaceAnchor> route = new java.util.ArrayList<>(length);
        for (int index = 0; index < length; index++)
            route.add(new SurfaceAnchor(FrontierWorldStateCodec.readPosition(input)));
        MovementOrder order = new MovementOrder(resident, resident, FrontierWireTags.tag(meal.phase()), 1L,
                java.util.List.of(route.getLast()), TraversalCapability.PEDESTRIAN,
                MovementOrder.ArrivalPolicy.EXACT_STATION);
        return meal.withColdTravel(new TimedKnownRoute(order, route, departedAt, ticksPerEdge, epoch));
    }

    private static java.util.List<SubjectId> legacyRecipients(Map<SubjectId, ResidentProfile> residents, SubjectId settlement, int required) {
        return residents.values().stream().filter(resident -> resident.settlementId().equals(settlement)).map(ResidentProfile::id).sorted().limit(required).toList();
    }

    private static Map<SubjectId, ResidentNutrition> legacyNutrition(Map<SubjectId, ResidentProfile> residents, Map<SubjectId, SettlementProvision> provisions) {
        Map<SubjectId, ResidentNutrition> values = new LinkedHashMap<>();
        residents.values().forEach(resident -> values.put(resident.id(), ResidentNutrition.nourishedAt(
                provisions.get(resident.settlementId()).cycleOrdinal())));
        return values;
    }

    private static ResidentMigrationBlockReason readBlockReason(DataInputStream input) throws IOException {
        int reason = input.readUnsignedByte();
        if (reason >= ResidentMigrationBlockReason.values().length) throw new IllegalArgumentException("unknown resident migration block reason");
        return FrontierWireTags.require(ResidentMigrationBlockReason.class, reason);
    }

    private static void writeProfile(DataOutputStream output, ResidentProfile resident) throws IOException {
        FrontierWorldStateCodec.writeString(output, resident.id().value()); FrontierWorldStateCodec.writeString(output, resident.householdId().value());
        FrontierWorldStateCodec.writeString(output, resident.settlementId().value()); output.writeByte(resident.role().wireTag());
        output.writeByte(resident.profession().wireTag()); output.writeLong(resident.birthTick());
        for (ResidentSkill skill : ResidentSkill.values()) output.writeByte(resident.skill(skill));
        for (HumanCapability capability : HumanCapability.values()) output.writeByte(resident.capability(capability));
        ResidentCharacteristics characteristics = resident.characteristics();
        output.writeByte(characteristics.version()); output.writeInt(characteristics.baseMetabolismPermille());
        FrontierWorldStateCodec.writeCount(output, characteristics.metabolismModifiers().size());
        for (var entry : characteristics.metabolismModifiers().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            FrontierWorldStateCodec.writeString(output, entry.getKey().value());
            output.writeInt(entry.getValue().deltaPermille());
        }
        WorkStateCodec.writeModifiers(output, characteristics.workModifiers());
    }

    private static ResidentProfile readProfile(DataInputStream input, boolean hasCapabilityProfile) throws IOException {
        SubjectId id = new SubjectId(FrontierWorldStateCodec.readString(input)); SubjectId household = new SubjectId(FrontierWorldStateCodec.readString(input));
        SubjectId settlement = new SubjectId(FrontierWorldStateCodec.readString(input)); int role = input.readUnsignedByte();
        if (role >= ResidentRole.values().length) throw new IllegalArgumentException("unknown resident role");
        ResidentProfession profession = null;
        if (hasCapabilityProfile) {
            int professionTag = input.readUnsignedByte();
            if (professionTag >= ResidentProfession.values().length) throw new IllegalArgumentException("unknown resident profession");
            profession = FrontierWireTags.require(ResidentProfession.class, professionTag);
        }
        long birthTick = input.readLong(); var skills = new java.util.EnumMap<ResidentSkill, Integer>(ResidentSkill.class);
        for (ResidentSkill skill : ResidentSkill.values()) skills.put(skill, input.readUnsignedByte());
        if (!hasCapabilityProfile) return new ResidentProfile(id, household, settlement, FrontierWireTags.require(ResidentRole.class, role), birthTick, skills);
        var capabilities = new java.util.EnumMap<HumanCapability, Integer>(HumanCapability.class);
        for (HumanCapability capability : HumanCapability.values()) capabilities.put(capability, input.readUnsignedByte());
        int version = input.readUnsignedByte(); int base = input.readInt();
        Map<SubjectId, ResidentCharacteristics.MetabolismModifier> modifiers = new LinkedHashMap<>();
        for (int index = 0, count = FrontierWorldStateCodec.readCount(input); index < count; index++) {
            SubjectId source = new SubjectId(FrontierWorldStateCodec.readString(input));
            var modifier = new ResidentCharacteristics.MetabolismModifier(source, input.readInt());
            if (modifiers.put(source, modifier) != null) throw new IllegalArgumentException("duplicate resident characteristic modifier");
        }
        return new ResidentProfile(id, household, settlement, FrontierWireTags.require(ResidentRole.class, role), profession,
                birthTick, skills, capabilities, new ResidentCharacteristics(version, base, modifiers, WorkStateCodec.readModifiers(input)));
    }
}
