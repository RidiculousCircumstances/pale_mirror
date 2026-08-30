package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.kernel.PayloadCodec;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/** Wire codecs owned by the human-population boundary. */
final class HumanPopulationPayloadCodecs {
    private HumanPopulationPayloadCodecs() { }

    static PayloadCodec born() {
        return new PayloadCodec() {
            @Override public String type() { return "frontier.resident_born"; }
            @Override public byte[] encode(FrontierPayload payload) { return FrontierWorldPayloadCodecs.encodeProduction(output -> {
                ResidentBorn birth = (ResidentBorn) payload; writeProfile(output, birth.resident()); FrontierWorldPayloadCodecs.writePosition(output, birth.position());
            }); }
            @Override public FrontierPayload decode(byte[] bytes) { return FrontierWorldPayloadCodecs.decodeProduction(bytes,
                    input -> new ResidentBorn(readProfile(input), FrontierWorldPayloadCodecs.readPosition(input))); }
        };
    }

    static PayloadCodec migrated() {
        return new PayloadCodec() {
            @Override public String type() { return "frontier.resident_migrated"; }
            @Override public byte[] encode(FrontierPayload payload) { return FrontierWorldPayloadCodecs.encodeProduction(output -> {
                ResidentMigrated migration = (ResidentMigrated) payload;
                FrontierWorldPayloadCodecs.writeSubject(output, migration.residentId()); FrontierWorldPayloadCodecs.writeSubject(output, migration.destinationHouseholdId());
                FrontierWorldPayloadCodecs.writeSubject(output, migration.destinationSettlementId()); FrontierWorldPayloadCodecs.writePosition(output, migration.destination());
            }); }
            @Override public FrontierPayload decode(byte[] bytes) { return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> new ResidentMigrated(
                    FrontierWorldPayloadCodecs.readSubject(input).value(), FrontierWorldPayloadCodecs.readSubject(input).value(),
                    FrontierWorldPayloadCodecs.readSubject(input).value(), FrontierWorldPayloadCodecs.readPosition(input))); }
        };
    }

    static PayloadCodec birthStarted() {
        return new PayloadCodec() {
            @Override public String type() { return "frontier.resident_birth_started"; }
            @Override public byte[] encode(FrontierPayload payload) { return FrontierWorldPayloadCodecs.encodeProduction(output -> writeBirthJob(output, ((ResidentBirthStarted) payload).job())); }
            @Override public FrontierPayload decode(byte[] bytes) { return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> new ResidentBirthStarted(readBirthJob(input))); }
        };
    }

    static PayloadCodec birthCancelled() {
        return new PayloadCodec() {
            @Override public String type() { return "frontier.resident_birth_cancelled"; }
            @Override public byte[] encode(FrontierPayload payload) { return FrontierWorldPayloadCodecs.encodeProduction(output ->
                    FrontierWorldPayloadCodecs.writeSubject(output, ((ResidentBirthCancelled) payload).jobId())); }
            @Override public FrontierPayload decode(byte[] bytes) { return FrontierWorldPayloadCodecs.decodeProduction(bytes,
                    input -> new ResidentBirthCancelled(FrontierWorldPayloadCodecs.readSubject(input).value())); }
        };
    }

    static PayloadCodec migrationStarted() {
        return new PayloadCodec() {
            @Override public String type() { return "frontier.resident_migration_started"; }
            @Override public byte[] encode(FrontierPayload payload) { return FrontierWorldPayloadCodecs.encodeProduction(output -> writeJourney(output, ((ResidentMigrationStarted) payload).journey())); }
            @Override public FrontierPayload decode(byte[] bytes) { return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> new ResidentMigrationStarted(readJourney(input))); }
        };
    }

    static PayloadCodec migrationAdvanced() {
        return new PayloadCodec() {
            @Override public String type() { return "frontier.resident_migration_advanced"; }
            @Override public byte[] encode(FrontierPayload payload) { return FrontierWorldPayloadCodecs.encodeProduction(output -> {
                ResidentMigrationAdvanced advanced = (ResidentMigrationAdvanced) payload;
                FrontierWorldPayloadCodecs.writeSubject(output, advanced.residentId()); FrontierWorldStateCodec.writeCount(output, advanced.nextRouteIndex());
            }); }
            @Override public FrontierPayload decode(byte[] bytes) { return FrontierWorldPayloadCodecs.decodeProduction(bytes,
                    input -> new ResidentMigrationAdvanced(FrontierWorldPayloadCodecs.readSubject(input).value(), FrontierWorldStateCodec.readCount(input))); }
        };
    }

    static PayloadCodec migrationBlocked() {
        return new PayloadCodec() {
            @Override public String type() { return "frontier.resident_migration_blocked"; }
            @Override public byte[] encode(FrontierPayload payload) { return FrontierWorldPayloadCodecs.encodeProduction(output -> {
                ResidentMigrationBlocked blocked = (ResidentMigrationBlocked) payload;
                FrontierWorldPayloadCodecs.writeSubject(output, blocked.residentId()); output.writeByte(blocked.reason().ordinal());
            }); }
            @Override public FrontierPayload decode(byte[] bytes) { return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> {
                var resident = FrontierWorldPayloadCodecs.readSubject(input); int reason = input.readUnsignedByte();
                if (reason >= ResidentMigrationBlockReason.values().length) throw new IllegalArgumentException("unknown resident migration block reason");
                return new ResidentMigrationBlocked(resident.value(), ResidentMigrationBlockReason.values()[reason]);
            }); }
        };
    }

    static PayloadCodec migrationResumed() {
        return new PayloadCodec() {
            @Override public String type() { return "frontier.resident_migration_resumed"; }
            @Override public byte[] encode(FrontierPayload payload) { return FrontierWorldPayloadCodecs.encodeProduction(output ->
                    FrontierWorldPayloadCodecs.writeSubject(output, ((ResidentMigrationResumed) payload).residentId())); }
            @Override public FrontierPayload decode(byte[] bytes) { return FrontierWorldPayloadCodecs.decodeProduction(bytes,
                    input -> new ResidentMigrationResumed(FrontierWorldPayloadCodecs.readSubject(input).value())); }
        };
    }

    private static void writeProfile(DataOutputStream output, ResidentProfile resident) throws IOException {
        FrontierWorldPayloadCodecs.writeSubject(output, resident.id()); FrontierWorldPayloadCodecs.writeSubject(output, resident.householdId());
        FrontierWorldPayloadCodecs.writeSubject(output, resident.settlementId()); output.writeByte(resident.role().ordinal()); output.writeLong(resident.birthTick());
        for (ResidentSkill skill : ResidentSkill.values()) output.writeByte(resident.skill(skill));
    }
    private static ResidentProfile readProfile(DataInputStream input) throws IOException {
        var id = FrontierWorldPayloadCodecs.readSubject(input); var household = FrontierWorldPayloadCodecs.readSubject(input); var settlement = FrontierWorldPayloadCodecs.readSubject(input);
        int role = input.readUnsignedByte(); if (role >= ResidentRole.values().length) throw new IllegalArgumentException("unknown resident role");
        long birthTick = input.readLong(); var skills = new java.util.EnumMap<ResidentSkill, Integer>(ResidentSkill.class);
        for (ResidentSkill skill : ResidentSkill.values()) skills.put(skill, input.readUnsignedByte());
        return new ResidentProfile(id.value(), household.value(), settlement.value(), ResidentRole.values()[role], birthTick, skills);
    }

    private static void writeBirthJob(DataOutputStream output, ResidentBirthJob job) throws IOException {
        FrontierWorldPayloadCodecs.writeSubject(output, job.id()); FrontierWorldPayloadCodecs.writeSubject(output, job.settlementId());
        FrontierWorldPayloadCodecs.writeSubject(output, job.householdId()); FrontierWorldPayloadCodecs.writeSubject(output, job.foodItemId());
        FrontierWorldPayloadCodecs.writeString(output, job.consumptionIntentId().value()); writeProfile(output, job.resident());
        FrontierWorldPayloadCodecs.writePosition(output, job.position());
    }

    private static ResidentBirthJob readBirthJob(DataInputStream input) throws IOException {
        var id = FrontierWorldPayloadCodecs.readSubject(input); var settlement = FrontierWorldPayloadCodecs.readSubject(input);
        var household = FrontierWorldPayloadCodecs.readSubject(input); var food = FrontierWorldPayloadCodecs.readSubject(input);
        return new ResidentBirthJob(id.value(), settlement.value(), household.value(), food.value(),
                new io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId(FrontierWorldPayloadCodecs.readString(input)),
                readProfile(input), FrontierWorldPayloadCodecs.readPosition(input));
    }

    private static void writeJourney(DataOutputStream output, ResidentMigrationJourney journey) throws IOException {
        FrontierWorldPayloadCodecs.writeSubject(output, journey.residentId()); FrontierWorldPayloadCodecs.writeSubject(output, journey.originSettlementId());
        FrontierWorldPayloadCodecs.writeSubject(output, journey.destinationHouseholdId()); FrontierWorldPayloadCodecs.writeSubject(output, journey.destinationSettlementId());
        FrontierWorldStateCodec.writeCount(output, journey.route().size()); for (BlockPosition position : journey.route()) FrontierWorldPayloadCodecs.writePosition(output, position);
        FrontierWorldStateCodec.writeCount(output, journey.routeIndex()); output.writeByte(journey.status().ordinal()); output.writeBoolean(journey.blockReason().isPresent());
        if (journey.blockReason().isPresent()) output.writeByte(journey.blockReason().orElseThrow().ordinal());
    }

    private static ResidentMigrationJourney readJourney(DataInputStream input) throws IOException {
        var resident = FrontierWorldPayloadCodecs.readSubject(input); var origin = FrontierWorldPayloadCodecs.readSubject(input);
        var household = FrontierWorldPayloadCodecs.readSubject(input); var destination = FrontierWorldPayloadCodecs.readSubject(input);
        java.util.List<BlockPosition> route = new java.util.ArrayList<>();
        for (int index = 0, count = FrontierWorldStateCodec.readCount(input); index < count; index++) route.add(FrontierWorldPayloadCodecs.readPosition(input));
        int routeIndex = FrontierWorldStateCodec.readCount(input); int status = input.readUnsignedByte(); boolean blocked = input.readBoolean();
        if (status >= ResidentMigrationStatus.values().length) throw new IllegalArgumentException("unknown resident migration status");
        java.util.Optional<ResidentMigrationBlockReason> reason = java.util.Optional.empty();
        if (blocked) {
            int value = input.readUnsignedByte();
            if (value >= ResidentMigrationBlockReason.values().length) throw new IllegalArgumentException("unknown resident migration block reason");
            reason = java.util.Optional.of(ResidentMigrationBlockReason.values()[value]);
        }
        return new ResidentMigrationJourney(resident.value(), origin.value(), household.value(), destination.value(), route, routeIndex,
                ResidentMigrationStatus.values()[status], reason);
    }
}
