package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

/** Schema fragment for exact households, people and in-flight birth permits. */
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
            FrontierWorldStateCodec.writeString(output, entry.getKey().value()); output.writeByte(entry.getValue().status().ordinal()); output.writeLong(entry.getValue().sinceTick());
        }
        FrontierWorldStateCodec.writeCount(output, population.quarantines().size());
        for (Map.Entry<SubjectId, SettlementQuarantine> entry : population.quarantines().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            FrontierWorldStateCodec.writeString(output, entry.getKey().value()); output.writeByte(entry.getValue().status().ordinal()); output.writeLong(entry.getValue().sinceTick());
        }
    }

    static HumanPopulation read(DataInputStream input, boolean hasHealth) throws IOException {
        Map<SubjectId, Household> households = new LinkedHashMap<>();
        for (int index = 0, count = FrontierWorldStateCodec.readCount(input); index < count; index++) {
            SubjectId id = new SubjectId(FrontierWorldStateCodec.readString(input));
            if (households.put(id, new Household(id, new SubjectId(FrontierWorldStateCodec.readString(input)))) != null) throw new IllegalArgumentException("duplicate household id");
        }
        Map<SubjectId, ResidentProfile> residents = new LinkedHashMap<>();
        for (int index = 0, count = FrontierWorldStateCodec.readCount(input); index < count; index++) {
            ResidentProfile resident = readProfile(input);
            if (residents.put(resident.id(), resident) != null) throw new IllegalArgumentException("duplicate resident id");
        }
        Map<SubjectId, ResidentBirthJob> birthJobs = new LinkedHashMap<>();
        for (int index = 0, count = FrontierWorldStateCodec.readCount(input); index < count; index++) {
            SubjectId id = new SubjectId(FrontierWorldStateCodec.readString(input)); SubjectId settlement = new SubjectId(FrontierWorldStateCodec.readString(input));
            SubjectId household = new SubjectId(FrontierWorldStateCodec.readString(input)); SubjectId food = new SubjectId(FrontierWorldStateCodec.readString(input));
            ResidentBirthJob job = new ResidentBirthJob(id, settlement, household, food, new PhysicalIntentId(FrontierWorldStateCodec.readString(input)),
                    readProfile(input), FrontierWorldStateCodec.readPosition(input));
            if (birthJobs.put(id, job) != null) throw new IllegalArgumentException("duplicate resident birth job");
        }
        if (!hasHealth) return new HumanPopulation(households, residents, birthJobs);
        Map<SubjectId, ResidentHealth> health = new LinkedHashMap<>();
        for (int index = 0, count = FrontierWorldStateCodec.readCount(input); index < count; index++) {
            SubjectId id = new SubjectId(FrontierWorldStateCodec.readString(input)); int status = input.readUnsignedByte();
            if (status >= ResidentHealthStatus.values().length || health.put(id, new ResidentHealth(ResidentHealthStatus.values()[status], input.readLong())) != null) {
                throw new IllegalArgumentException("invalid or duplicate resident health");
            }
        }
        Map<SubjectId, SettlementQuarantine> quarantines = new LinkedHashMap<>();
        for (int index = 0, count = FrontierWorldStateCodec.readCount(input); index < count; index++) {
            SubjectId id = new SubjectId(FrontierWorldStateCodec.readString(input)); int status = input.readUnsignedByte();
            if (status >= SettlementQuarantineStatus.values().length
                    || quarantines.put(id, new SettlementQuarantine(SettlementQuarantineStatus.values()[status], input.readLong())) != null) {
                throw new IllegalArgumentException("invalid or duplicate settlement quarantine");
            }
        }
        return new HumanPopulation(households, residents, birthJobs, health, quarantines);
    }

    private static void writeProfile(DataOutputStream output, ResidentProfile resident) throws IOException {
        FrontierWorldStateCodec.writeString(output, resident.id().value()); FrontierWorldStateCodec.writeString(output, resident.householdId().value());
        FrontierWorldStateCodec.writeString(output, resident.settlementId().value()); output.writeByte(resident.role().ordinal()); output.writeLong(resident.birthTick());
        for (ResidentSkill skill : ResidentSkill.values()) output.writeByte(resident.skill(skill));
    }

    private static ResidentProfile readProfile(DataInputStream input) throws IOException {
        SubjectId id = new SubjectId(FrontierWorldStateCodec.readString(input)); SubjectId household = new SubjectId(FrontierWorldStateCodec.readString(input));
        SubjectId settlement = new SubjectId(FrontierWorldStateCodec.readString(input)); int role = input.readUnsignedByte();
        if (role >= ResidentRole.values().length) throw new IllegalArgumentException("unknown resident role");
        long birthTick = input.readLong(); var skills = new java.util.EnumMap<ResidentSkill, Integer>(ResidentSkill.class);
        for (ResidentSkill skill : ResidentSkill.values()) skills.put(skill, input.readUnsignedByte());
        return new ResidentProfile(id, household, settlement, ResidentRole.values()[role], birthTick, skills);
    }
}
