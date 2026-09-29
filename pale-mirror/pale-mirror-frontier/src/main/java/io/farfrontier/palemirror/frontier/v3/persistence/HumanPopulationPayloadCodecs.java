package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.model.*;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.kernel.PayloadCodec;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/** Wire codecs owned by the human-population boundary. */
final class HumanPopulationPayloadCodecs {
    private static final int BORN_FORMAT = 0x52424f32;
    private HumanPopulationPayloadCodecs() { }

    static PayloadCodec needIntegrated() {
        return new PayloadCodec() {
            @Override public String type() { return "frontier.resident_need_integrated"; }
            @Override public byte[] encode(FrontierPayload payload) {
                return FrontierWorldPayloadCodecs.encodeProduction(output -> {
                    ResidentNeedIntegrated value = (ResidentNeedIntegrated) payload;
                    FrontierWorldPayloadCodecs.writeSubject(output, value.residentId());
                    output.writeLong(value.atTick()); output.writeLong(value.previousTick());
                    output.writeInt(value.hungerDeficit()); output.writeLong(value.fractionalProgress());
                });
            }
            @Override public FrontierPayload decode(byte[] bytes) {
                return FrontierWorldPayloadCodecs.decodeProduction(bytes, input ->
                        new ResidentNeedIntegrated(FrontierWorldPayloadCodecs.readSubject(input).value(),
                                input.readLong(), input.readLong(), input.readInt(), input.readLong()));
            }
        };
    }

    static PayloadCodec metabolismChanged() {
        return new PayloadCodec() {
            @Override public String type() { return "frontier.resident_metabolism_changed"; }
            @Override public byte[] encode(FrontierPayload payload) {
                return FrontierWorldPayloadCodecs.encodeProduction(output -> {
                    ResidentMetabolismChanged changed = (ResidentMetabolismChanged) payload;
                    FrontierWorldPayloadCodecs.writeSubject(output, changed.residentId());
                    output.writeLong(changed.atTick());
                    writeCharacteristics(output, changed.previous());
                    writeCharacteristics(output, changed.next());
                });
            }
            @Override public FrontierPayload decode(byte[] bytes) {
                return FrontierWorldPayloadCodecs.decodeProduction(bytes, input ->
                        new ResidentMetabolismChanged(FrontierWorldPayloadCodecs.readSubject(input).value(),
                                input.readLong(), readCharacteristics(input), readCharacteristics(input)));
            }
        };
    }

    private static void writeCharacteristics(DataOutputStream output, ResidentCharacteristics characteristics) throws IOException {
        output.writeByte(characteristics.version()); output.writeInt(characteristics.baseMetabolismPermille());
        FrontierWorldStateCodec.writeCount(output, characteristics.metabolismModifiers().size());
        for (var entry : characteristics.metabolismModifiers().entrySet().stream().sorted(java.util.Map.Entry.comparingByKey()).toList()) {
            FrontierWorldPayloadCodecs.writeSubject(output, entry.getKey());
            output.writeInt(entry.getValue().deltaPermille());
        }
    }

    private static ResidentCharacteristics readCharacteristics(DataInputStream input) throws IOException {
        int version = input.readUnsignedByte(); int base = input.readInt();
        java.util.Map<io.farfrontier.palemirror.frontier.v3.api.SubjectId, ResidentCharacteristics.MetabolismModifier> modifiers = new java.util.LinkedHashMap<>();
        for (int index = 0, count = FrontierWorldStateCodec.readCount(input); index < count; index++) {
            var source = FrontierWorldPayloadCodecs.readSubject(input).value();
            var modifier = new ResidentCharacteristics.MetabolismModifier(source, input.readInt());
            if (modifiers.put(source, modifier) != null) throw new IllegalArgumentException("duplicate metabolism modifier");
        }
        return new ResidentCharacteristics(version, base, modifiers);
    }

    static PayloadCodec mealStarted() {
        return new PayloadCodec() {
            @Override public String type() { return "frontier.resident_meal_started"; }
            @Override public byte[] encode(FrontierPayload payload) {
                return FrontierWorldPayloadCodecs.encodeProduction(output ->
                        HumanPopulationStateCodec.writeMeal(output, ((ResidentMealStarted) payload).meal()));
            }
            @Override public FrontierPayload decode(byte[] bytes) {
                return FrontierWorldPayloadCodecs.decodeProduction(bytes,
                        input -> new ResidentMealStarted(HumanPopulationStateCodec.readMeal(input)));
            }
        };
    }

    static PayloadCodec mealHotArrived() {
        return new PayloadCodec() {
            @Override public String type() { return "frontier.resident_meal_hot_arrived"; }
            @Override public byte[] encode(FrontierPayload payload) {
                return FrontierWorldPayloadCodecs.encodeProduction(output -> {
                    ResidentMealHotArrived arrived = (ResidentMealHotArrived) payload;
                    FrontierWorldPayloadCodecs.writeSubject(output, arrived.residentId());
                    output.writeLong(arrived.ambientRevision());
                    output.writeInt(arrived.observedBody().x()); output.writeInt(arrived.observedBody().y());
                    output.writeInt(arrived.observedBody().z());
                });
            }
            @Override public FrontierPayload decode(byte[] bytes) {
                return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> new ResidentMealHotArrived(
                        FrontierWorldPayloadCodecs.readSubject(input).value(), input.readLong(),
                        new BodyPosition(input.readInt(), input.readInt(), input.readInt())));
            }
        };
    }

    static PayloadCodec mealHotEffectPrepared() {
        return new PayloadCodec() {
            @Override public String type() { return "frontier.resident_meal_hot_effect_prepared"; }
            @Override public byte[] encode(FrontierPayload payload) {
                return FrontierWorldPayloadCodecs.encodeProduction(output -> {
                    ResidentMealHotEffectPrepared prepared = (ResidentMealHotEffectPrepared) payload;
                    FrontierWorldPayloadCodecs.writeSubject(output, prepared.residentId());
                    ResidentMealPhysicalStep step = prepared.step();
                    output.writeByte(FrontierWireTags.tag(step.phase())); output.writeByte(step.sourceSlot());
                    output.writeByte(step.sourceCount()); output.writeLong(step.sourceEpoch());
                    output.writeLong(step.destinationEpoch()); output.writeLong(step.ambientRevision());
                });
            }
            @Override public FrontierPayload decode(byte[] bytes) {
                return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> new ResidentMealHotEffectPrepared(
                        FrontierWorldPayloadCodecs.readSubject(input).value(), new ResidentMealPhysicalStep(
                        FrontierWireTags.require(ResidentMeal.Phase.class, input.readUnsignedByte()),
                        input.readByte(), input.readUnsignedByte(), input.readLong(), input.readLong(), input.readLong())));
            }
        };
    }

    static PayloadCodec mealHotEffectObserved() {
        return new PayloadCodec() {
            @Override public String type() { return "frontier.resident_meal_hot_effect_observed"; }
            @Override public byte[] encode(FrontierPayload payload) {
                return FrontierWorldPayloadCodecs.encodeProduction(output -> {
                    ResidentMealHotEffectObserved observed = (ResidentMealHotEffectObserved) payload;
                    FrontierWorldPayloadCodecs.writeSubject(output, observed.residentId());
                    output.writeByte(FrontierWireTags.tag(observed.phase())); output.writeLong(observed.ambientRevision());
                    output.writeInt(observed.observedBody().x()); output.writeInt(observed.observedBody().y());
                    output.writeInt(observed.observedBody().z());
                    BakeryHotEffectObservedCodec.writeStacks(output, observed.remainingSource());
                    BakeryHotEffectObservedCodec.writeStacks(output, observed.destination());
                });
            }
            @Override public FrontierPayload decode(byte[] bytes) {
                return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> new ResidentMealHotEffectObserved(
                        FrontierWorldPayloadCodecs.readSubject(input).value(),
                        FrontierWireTags.require(ResidentMeal.Phase.class, input.readUnsignedByte()),
                        input.readLong(), new BodyPosition(input.readInt(), input.readInt(), input.readInt()),
                        BakeryHotEffectObservedCodec.readStacks(input), BakeryHotEffectObservedCodec.readStacks(input)));
            }
        };
    }

    static PayloadCodec mealHotHandMaterialized() {
        return mealHand("frontier.resident_meal_hot_hand_materialized", true);
    }

    static PayloadCodec mealHotHandReleased() {
        return mealHand("frontier.resident_meal_hot_hand_released", false);
    }

    static PayloadCodec mealHotReturned() {
        return new PayloadCodec() {
            @Override public String type() { return "frontier.resident_meal_hot_returned"; }
            @Override public byte[] encode(FrontierPayload payload) {
                return FrontierWorldPayloadCodecs.encodeProduction(output -> {
                    ResidentMealHotReturned returned = (ResidentMealHotReturned) payload;
                    FrontierWorldPayloadCodecs.writeSubject(output, returned.residentId());
                    output.writeLong(returned.ambientRevision());
                });
            }
            @Override public FrontierPayload decode(byte[] bytes) {
                return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> new ResidentMealHotReturned(
                        FrontierWorldPayloadCodecs.readSubject(input).value(), input.readLong()));
            }
        };
    }

    private static PayloadCodec mealHand(String type, boolean materialized) {
        return new PayloadCodec() {
            @Override public String type() { return type; }
            @Override public byte[] encode(FrontierPayload payload) {
                return FrontierWorldPayloadCodecs.encodeProduction(output -> {
                    io.farfrontier.palemirror.frontier.v3.api.SubjectId id;
                    long revision;
                    FungiblePhysicalObservation.Stack hand;
                    if (materialized) {
                        ResidentMealHotHandMaterialized value = (ResidentMealHotHandMaterialized) payload;
                        id = value.residentId(); revision = value.ambientRevision(); hand = value.observedHand();
                    } else {
                        ResidentMealHotHandReleased value = (ResidentMealHotHandReleased) payload;
                        id = value.residentId(); revision = value.ambientRevision(); hand = value.observedHand();
                    }
                    FrontierWorldPayloadCodecs.writeSubject(output, id); output.writeLong(revision);
                    BakeryHotEffectObservedCodec.writeStacks(output, java.util.List.of(hand));
                });
            }
            @Override public FrontierPayload decode(byte[] bytes) {
                return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> {
                    var id = FrontierWorldPayloadCodecs.readSubject(input).value(); long revision = input.readLong();
                    var hands = BakeryHotEffectObservedCodec.readStacks(input);
                    if (hands.size() != 1) throw new IllegalArgumentException("meal hand witness needs one exact stack");
                    return materialized ? new ResidentMealHotHandMaterialized(id, revision, hands.getFirst())
                            : new ResidentMealHotHandReleased(id, revision, hands.getFirst());
                });
            }
        };
    }

    static PayloadCodec mealColdStep() {
        return new PayloadCodec() {
            @Override public String type() { return "frontier.resident_meal_cold_step"; }
            @Override public byte[] encode(FrontierPayload payload) {
                return FrontierWorldPayloadCodecs.encodeProduction(output -> {
                    ResidentMealColdStep value = (ResidentMealColdStep) payload;
                    FrontierWorldPayloadCodecs.writeSubject(output, value.residentId());
                    output.writeByte(FrontierWireTags.tag(value.expectedPhase()));
                    output.writeLong(value.atTick());
                    output.writeBoolean(value.nextSurface().isPresent());
                    if (value.nextSurface().isPresent()) FrontierWorldPayloadCodecs.writePosition(output,
                            value.nextSurface().orElseThrow().support());
                });
            }
            @Override public FrontierPayload decode(byte[] bytes) {
                return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> {
                    var resident = FrontierWorldPayloadCodecs.readSubject(input).value();
                    var phase = FrontierWireTags.require(ResidentMeal.Phase.class, input.readUnsignedByte());
                    long atTick = input.readLong();
                    var next = input.readBoolean() ? java.util.Optional.of(
                            new SurfaceAnchor(FrontierWorldPayloadCodecs.readPosition(input)))
                            : java.util.Optional.<SurfaceAnchor>empty();
                    return new ResidentMealColdStep(resident, phase, atTick, next);
                });
            }
        };
    }

    static PayloadCodec born() {
        return new PayloadCodec() {
            @Override public String type() { return "frontier.resident_born"; }
            @Override public byte[] encode(FrontierPayload payload) { return FrontierWorldPayloadCodecs.encodeProduction(output -> {
                ResidentBorn birth = (ResidentBorn) payload; ActorBirthIdentityCodec.write(output, birth.birth());
                output.writeInt(BORN_FORMAT); FrontierWorldPayloadCodecs.writeSubject(output, birth.jobId());
                writeProfile(output, birth.resident()); FrontierWorldPayloadCodecs.writePosition(output, birth.position());
            }); }
            @Override public FrontierPayload decode(byte[] bytes) { return FrontierWorldPayloadCodecs.decodeProduction(bytes,
                    input -> {
                        var birth = ActorBirthIdentityCodec.read(input);
                        if (input.readInt() != BORN_FORMAT) throw new IllegalArgumentException("resident birth lacks current job envelope");
                        var jobId = FrontierWorldPayloadCodecs.readSubject(input).value();
                        return new ResidentBorn(jobId, readProfile(input), FrontierWorldPayloadCodecs.readPosition(input), birth);
                    }); }
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

    static PayloadCodec transitAdvanced() {
        return new PayloadCodec() {
            @Override public String type() { return "frontier.resident_transit_advanced"; }
            @Override public byte[] encode(FrontierPayload payload) { return FrontierWorldPayloadCodecs.encodeProduction(output -> {
                ResidentTransitAdvanced advanced = (ResidentTransitAdvanced) payload;
                FrontierWorldPayloadCodecs.writeSubject(output, advanced.residentId()); FrontierWorldStateCodec.writeCount(output, advanced.nextRouteIndex());
            }); }
            @Override public FrontierPayload decode(byte[] bytes) { return FrontierWorldPayloadCodecs.decodeProduction(bytes,
                    input -> new ResidentTransitAdvanced(FrontierWorldPayloadCodecs.readSubject(input).value(), FrontierWorldStateCodec.readCount(input))); }
        };
    }

    static PayloadCodec migrationBlocked() {
        return new PayloadCodec() {
            @Override public String type() { return "frontier.resident_migration_blocked"; }
            @Override public byte[] encode(FrontierPayload payload) { return FrontierWorldPayloadCodecs.encodeProduction(output -> {
                ResidentMigrationBlocked blocked = (ResidentMigrationBlocked) payload;
                FrontierWorldPayloadCodecs.writeSubject(output, blocked.residentId()); output.writeByte(blocked.reason().wireTag()); FrontierWorldPayloadCodecs.writeDiagnosticTuple(output, blocked.diagnostic());
            }); }
            @Override public FrontierPayload decode(byte[] bytes) { return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> {
                var resident = FrontierWorldPayloadCodecs.readSubject(input); int reason = input.readUnsignedByte();
                if (reason >= ResidentMigrationBlockReason.values().length) throw new IllegalArgumentException("unknown resident migration block reason");
                return new ResidentMigrationBlocked(resident.value(), FrontierWireTags.require(ResidentMigrationBlockReason.class, reason), FrontierWorldPayloadCodecs.readDiagnosticTuple(input));
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
        FrontierWorldPayloadCodecs.writeSubject(output, resident.settlementId()); output.writeByte(resident.role().wireTag());
        output.writeByte(resident.profession().wireTag()); output.writeLong(resident.birthTick());
        for (ResidentSkill skill : ResidentSkill.values()) output.writeByte(resident.skill(skill));
        for (HumanCapability capability : HumanCapability.values()) output.writeByte(resident.capability(capability));
        writeCharacteristics(output, resident.characteristics());
    }
    private static ResidentProfile readProfile(DataInputStream input) throws IOException {
        var id = FrontierWorldPayloadCodecs.readSubject(input); var household = FrontierWorldPayloadCodecs.readSubject(input); var settlement = FrontierWorldPayloadCodecs.readSubject(input);
        int role = input.readUnsignedByte(); if (role >= ResidentRole.values().length) throw new IllegalArgumentException("unknown resident role");
        int professionTag = input.readUnsignedByte();
        ResidentProfession profession = FrontierWireTags.require(ResidentProfession.class, professionTag);
        long birthTick = input.readLong(); var skills = new java.util.EnumMap<ResidentSkill, Integer>(ResidentSkill.class);
        for (ResidentSkill skill : ResidentSkill.values()) skills.put(skill, input.readUnsignedByte());
        var capabilities = new java.util.EnumMap<HumanCapability, Integer>(HumanCapability.class);
        for (HumanCapability capability : HumanCapability.values()) capabilities.put(capability, input.readUnsignedByte());
        return new ResidentProfile(id.value(), household.value(), settlement.value(), FrontierWireTags.require(ResidentRole.class, role),
                profession, birthTick, skills, capabilities, readCharacteristics(input));
    }

    private static void writeBirthJob(DataOutputStream output, ResidentBirthJob job) throws IOException {
        FrontierWorldPayloadCodecs.writeSubject(output, job.id()); FrontierWorldPayloadCodecs.writeSubject(output, job.settlementId());
        FrontierWorldPayloadCodecs.writeSubject(output, job.householdId()); FrontierWorldPayloadCodecs.writeSubject(output, job.foodItemId());
        FrontierWorldPayloadCodecs.writeString(output, job.foodCommitmentId().value()); writeProfile(output, job.resident());
        FrontierWorldPayloadCodecs.writePosition(output, job.position());
    }

    private static ResidentBirthJob readBirthJob(DataInputStream input) throws IOException {
        var id = FrontierWorldPayloadCodecs.readSubject(input); var settlement = FrontierWorldPayloadCodecs.readSubject(input);
        var household = FrontierWorldPayloadCodecs.readSubject(input); var food = FrontierWorldPayloadCodecs.readSubject(input);
        return new ResidentBirthJob(id.value(), settlement.value(), household.value(), food.value(),
                new io.farfrontier.palemirror.frontier.v3.api.SubjectId(FrontierWorldPayloadCodecs.readString(input)),
                readProfile(input), FrontierWorldPayloadCodecs.readPosition(input));
    }

    private static void writeJourney(DataOutputStream output, ResidentMigrationJourney journey) throws IOException {
        FrontierWorldPayloadCodecs.writeSubject(output, journey.residentId()); FrontierWorldPayloadCodecs.writeSubject(output, journey.originSettlementId());
        FrontierWorldPayloadCodecs.writeSubject(output, journey.destinationHouseholdId()); FrontierWorldPayloadCodecs.writeSubject(output, journey.destinationSettlementId());
        FrontierWorldStateCodec.writeCount(output, journey.route().size()); for (BlockPosition position : journey.route()) FrontierWorldPayloadCodecs.writePosition(output, position);
        FrontierWorldStateCodec.writeCount(output, journey.routeIndex()); output.writeByte(journey.status().wireTag()); output.writeBoolean(journey.blockReason().isPresent());
        if (journey.blockReason().isPresent()) output.writeByte(journey.blockReason().orElseThrow().wireTag());
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
            reason = java.util.Optional.of(FrontierWireTags.require(ResidentMigrationBlockReason.class, value));
        }
        return new ResidentMigrationJourney(resident.value(), origin.value(), household.value(), destination.value(), route, routeIndex,
                FrontierWireTags.require(ResidentMigrationStatus.class, status), reason);
    }
}
