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
}
