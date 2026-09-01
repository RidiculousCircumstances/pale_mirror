package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.model.*;

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
            FrontierWorldStateCodec.writeString(output, job.consumptionIntentId().value()); writeProfile(output, job.resident()); FrontierWorldStateCodec.writePosition(output, job.position());
        }
        FrontierWorldStateCodec.writeCount(output, population.health().size());
        for (Map.Entry<SubjectId, ResidentHealth> entry : population.health().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            FrontierWorldStateCodec.writeString(output, entry.getKey().value()); output.writeByte(entry.getValue().status().wireTag()); output.writeLong(entry.getValue().sinceTick());
        }
        FrontierWorldStateCodec.writeCount(output, population.nutrition().size());
        for (Map.Entry<SubjectId, ResidentNutrition> entry : population.nutrition().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            FrontierWorldStateCodec.writeString(output, entry.getKey().value()); output.writeByte(entry.getValue().status().wireTag());
            FrontierWorldStateCodec.writeCount(output, entry.getValue().consecutiveMissedCycles()); FrontierWorldStateCodec.writeCount(output, entry.getValue().resolvedCycle());
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
        }
        FrontierWorldStateCodec.writeCount(output, population.provisions().size());
        for (SettlementProvision provision : population.provisions().values().stream().sorted(Comparator.comparing(SettlementProvision::settlementId)).toList()) {
            FrontierWorldStateCodec.writeString(output, provision.settlementId().value()); FrontierWorldStateCodec.writeCount(output, provision.cycleOrdinal());
            output.writeLong(provision.startedAtTick()); FrontierWorldStateCodec.writeCount(output, provision.requiredRations());
            FrontierWorldStateCodec.writeCount(output, provision.fulfilledRations()); FrontierWorldStateCodec.writeCount(output, provision.recipientIds().size());
            for (SubjectId recipient : provision.recipientIds()) FrontierWorldStateCodec.writeString(output, recipient.value());
            FrontierWorldStateCodec.writeCount(output, provision.allocations().size());
            for (SettlementRationAllocation allocation : provision.allocations()) {
                FrontierWorldStateCodec.writeString(output, allocation.itemId().value()); FrontierWorldStateCodec.writeCount(output, allocation.recipientIds().size());
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
    }

    static HumanPopulation read(DataInputStream input, boolean hasHealth, boolean hasMigrations, boolean hasProvisions, boolean hasNutrition,
                                boolean hasCapabilityProfile, boolean hasMedicalOperations, boolean hasMedicalTerminalTick) throws IOException {
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
            ResidentBirthJob job = new ResidentBirthJob(id, settlement, household, food, new PhysicalIntentId(FrontierWorldStateCodec.readString(input)),
                    readProfile(input, hasCapabilityProfile), FrontierWorldStateCodec.readPosition(input));
            if (birthJobs.put(id, job) != null) throw new IllegalArgumentException("duplicate resident birth job");
        }
        if (!hasHealth) return new HumanPopulation(households, residents, birthJobs);
        Map<SubjectId, ResidentHealth> health = new LinkedHashMap<>();
        for (int index = 0, count = FrontierWorldStateCodec.readCount(input); index < count; index++) {
            SubjectId id = new SubjectId(FrontierWorldStateCodec.readString(input)); int status = input.readUnsignedByte();
            if (status >= ResidentHealthStatus.values().length || health.put(id, new ResidentHealth(FrontierWireTags.require(ResidentHealthStatus.class, status), input.readLong())) != null) {
                throw new IllegalArgumentException("invalid or duplicate resident health");
            }
        }
        Map<SubjectId, ResidentNutrition> nutrition = new LinkedHashMap<>();
        if (hasNutrition) {
            for (int index = 0, count = FrontierWorldStateCodec.readCount(input); index < count; index++) {
                SubjectId id = new SubjectId(FrontierWorldStateCodec.readString(input)); int status = input.readUnsignedByte();
                if (status >= ResidentNutritionStatus.values().length || nutrition.put(id, new ResidentNutrition(FrontierWireTags.require(ResidentNutritionStatus.class, status),
                        FrontierWorldStateCodec.readCount(input), FrontierWorldStateCodec.readCount(input))) != null) {
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
                    FrontierWireTags.require(ResidentMigrationStatus.class, status), reason);
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
                SubjectId item = new SubjectId(FrontierWorldStateCodec.readString(input)); java.util.List<SubjectId> allocationRecipients = new java.util.ArrayList<>();
                if (hasNutrition) {
                    for (int recipient = 0, recipientCount = FrontierWorldStateCodec.readCount(input); recipient < recipientCount; recipient++) {
                        allocationRecipients.add(new SubjectId(FrontierWorldStateCodec.readString(input)));
                    }
                } else {
                    int legacyCount = FrontierWorldStateCodec.readCount(input); int start = allocations.stream().mapToInt(SettlementRationAllocation::count).sum();
                    allocationRecipients.addAll(recipients.subList(start, Math.min(Math.addExact(start, legacyCount), recipients.size())));
                }
                allocations.add(new SettlementRationAllocation(item, allocationRecipients));
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
        return new HumanPopulation(households, residents, birthJobs, health, quarantines, migrations, provisions, nutrition, medicalOperations);
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
        return new ResidentProfile(id, household, settlement, FrontierWireTags.require(ResidentRole.class, role), profession, birthTick, skills, capabilities);
    }
}
