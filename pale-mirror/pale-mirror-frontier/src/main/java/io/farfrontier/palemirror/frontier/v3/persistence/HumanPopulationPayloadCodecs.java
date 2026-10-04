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

    static PayloadCodec starvationIntegrated() {
        return new PayloadCodec() {
            @Override public String type() { return "frontier.resident_starvation_integrated"; }
            @Override public byte[] encode(FrontierPayload payload) {
                return FrontierWorldPayloadCodecs.encodeProduction(output -> {
                    ResidentStarvationIntegrated value = (ResidentStarvationIntegrated) payload;
                    FrontierWorldPayloadCodecs.writeSubject(output, value.residentId());
                    output.writeLong(value.previousNutritionTick()); output.writeLong(value.atTick());
                    writeStarvation(output, value.previous()); writeStarvation(output, value.next());
                });
            }
            @Override public FrontierPayload decode(byte[] bytes) {
                return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> new ResidentStarvationIntegrated(
                        FrontierWorldPayloadCodecs.readSubject(input).value(), input.readLong(), input.readLong(),
                        readStarvation(input), readStarvation(input)));
            }
        };
    }

    static void writeStarvation(java.io.DataOutputStream output, ResidentStarvation value) throws java.io.IOException {
        output.writeInt(value.severityUnits()); output.writeLong(value.exposureRemainder()); output.writeLong(value.recoveryRemainder());
    }

    static ResidentStarvation readStarvation(java.io.DataInputStream input) throws java.io.IOException {
        return new ResidentStarvation(input.readInt(), input.readLong(), input.readLong());
    }

    static PayloadCodec needIntegrated() {
        return new PayloadCodec() {
            @Override public String type() { return "frontier.resident_need_integrated"; }
            @Override public byte[] encode(FrontierPayload payload) {
                return FrontierWorldPayloadCodecs.encodeProduction(output -> {
                    ResidentNeedIntegrated value = (ResidentNeedIntegrated) payload;
                    FrontierWorldPayloadCodecs.writeSubject(output, value.residentId());
                    output.writeLong(value.atTick()); output.writeLong(value.previousTick());
                    output.writeInt(value.satietyUnits()); output.writeLong(value.fractionalProgress());
                });
            }
            @Override public FrontierPayload decode(byte[] bytes) {
                return FrontierWorldPayloadCodecs.decodeProduction(bytes, input ->
                        new ResidentNeedIntegrated(FrontierWorldPayloadCodecs.readSubject(input).value(),
                                input.readLong(), input.readLong(), input.readInt(), input.readLong()));
            }
        };
    }

    static PayloadCodec workModifiersChanged() {
        return new PayloadCodec() {
            @Override public String type() { return "frontier.resident_work_modifiers_changed"; }
            @Override public byte[] encode(FrontierPayload payload) {
                var value = (ResidentWorkModifiersChanged) payload;
                return FrontierWorldPayloadCodecs.encodeProduction(out -> {
                    FrontierWorldPayloadCodecs.writeSubject(out, value.residentId()); out.writeLong(value.atTick());
                    WorkStateCodec.writeModifiers(out, value.previous()); WorkStateCodec.writeModifiers(out, value.next());
                });
            }
            @Override public FrontierPayload decode(byte[] bytes) {
                return FrontierWorldPayloadCodecs.decodeProduction(bytes, in -> new ResidentWorkModifiersChanged(
                        FrontierWorldPayloadCodecs.readSubject(in).value(), in.readLong(),
                        WorkStateCodec.readModifiers(in), WorkStateCodec.readModifiers(in)));
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
        WorkStateCodec.writeModifiers(output, characteristics.workModifiers());
    }

    private static ResidentCharacteristics readCharacteristics(DataInputStream input) throws IOException {
        int version = input.readUnsignedByte(); int base = input.readInt();
        java.util.Map<io.farfrontier.palemirror.frontier.v3.api.SubjectId, ResidentCharacteristics.MetabolismModifier> modifiers = new java.util.LinkedHashMap<>();
        for (int index = 0, count = FrontierWorldStateCodec.readCount(input); index < count; index++) {
            var source = FrontierWorldPayloadCodecs.readSubject(input).value();
            var modifier = new ResidentCharacteristics.MetabolismModifier(source, input.readInt());
            if (modifiers.put(source, modifier) != null) throw new IllegalArgumentException("duplicate metabolism modifier");
        }
        return new ResidentCharacteristics(version, base, modifiers, WorkStateCodec.readModifiers(input));
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
                    ActorExecutionStateCodec.writeId(output, arrived.executionId());
                });
            }
            @Override public FrontierPayload decode(byte[] bytes) {
                return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> new ResidentMealHotArrived(
                        FrontierWorldPayloadCodecs.readSubject(input).value(), input.readLong(),
                        new BodyPosition(input.readInt(), input.readInt(), input.readInt()), ActorExecutionStateCodec.readId(input)));
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
                    HumanPopulationStateCodec.writeMealPhysicalStep(output, step);
                });
            }
            @Override public FrontierPayload decode(byte[] bytes) {
                return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> new ResidentMealHotEffectPrepared(
                        FrontierWorldPayloadCodecs.readSubject(input).value(), HumanPopulationStateCodec.readMealPhysicalStep(input)));
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
                    PhysicalObservationStackCodec.writeStacks(output, observed.remainingSource());
                    PhysicalObservationStackCodec.writeStacks(output, observed.destination());
                    ActorExecutionStateCodec.writeId(output, observed.executionId());
                });
            }
            @Override public FrontierPayload decode(byte[] bytes) {
                return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> new ResidentMealHotEffectObserved(
                        FrontierWorldPayloadCodecs.readSubject(input).value(),
                        FrontierWireTags.require(ResidentMeal.Phase.class, input.readUnsignedByte()),
                        input.readLong(), new BodyPosition(input.readInt(), input.readInt(), input.readInt()),
                        PhysicalObservationStackCodec.readStacks(input), PhysicalObservationStackCodec.readStacks(input), ActorExecutionStateCodec.readId(input)));
            }
        };
    }

    static PayloadCodec mealResourceEffectObserved() {
        return new PayloadCodec() {
            @Override public String type() { return "frontier.resident_meal_resource_effect_observed"; }
            @Override public byte[] encode(FrontierPayload payload) {
                return FrontierWorldPayloadCodecs.encodeProduction(output -> {
                    var observed = (ResidentMealResourceEffectObserved) payload;
                    FrontierWorldPayloadCodecs.writeSubject(output, observed.body().actorId());
                    output.writeLong(observed.body().physicalEpoch());
                    ActorExecutionStateCodec.writeId(output, observed.executionId());
                    HumanPopulationStateCodec.writeMealPhysicalStep(output, observed.step());
                    output.writeByte(FrontierWireTags.tag(observed.outcome()));
                    PhysicalObservationStackCodec.writeStacks(output, observed.remainingSource());
                    PhysicalObservationStackCodec.writeStacks(output, observed.destination());
                });
            }
            @Override public FrontierPayload decode(byte[] bytes) {
                return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> new ResidentMealResourceEffectObserved(
                        new io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId(
                                FrontierWorldPayloadCodecs.readSubject(input).value(), input.readLong()),
                        ActorExecutionStateCodec.readId(input), HumanPopulationStateCodec.readMealPhysicalStep(input),
                        FrontierWireTags.require(ResidentMealResourceEffectObserved.Outcome.class, input.readUnsignedByte()),
                        PhysicalObservationStackCodec.readStacks(input), PhysicalObservationStackCodec.readStacks(input)));
            }
        };
    }

    static PayloadCodec mealPortionDispositionObserved() {
        return new PayloadCodec() {
            @Override public String type() { return "frontier.resident_meal_portion_disposition_observed"; }
            @Override public byte[] encode(FrontierPayload payload) {
                return FrontierWorldPayloadCodecs.encodeProduction(output -> {
                    var observed = (ResidentMealPortionDispositionObserved) payload;
                    FrontierWorldPayloadCodecs.writeSubject(output, observed.body().actorId());
                    output.writeLong(observed.body().physicalEpoch());
                    ActorExecutionStateCodec.writeId(output, observed.executionId());
                    output.writeLong(observed.sourceEpoch());
                    output.writeByte(FrontierWireTags.tag(observed.outcome()));
                    output.writeBoolean(observed.worldCarrier().isPresent());
                    if (observed.worldCarrier().isPresent()) {
                        output.writeLong(observed.worldCarrier().orElseThrow().getMostSignificantBits());
                        output.writeLong(observed.worldCarrier().orElseThrow().getLeastSignificantBits());
                    }
                });
            }
            @Override public FrontierPayload decode(byte[] bytes) {
                return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> new ResidentMealPortionDispositionObserved(
                        new io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId(
                                FrontierWorldPayloadCodecs.readSubject(input).value(), input.readLong()),
                        ActorExecutionStateCodec.readId(input), input.readLong(),
                        FrontierWireTags.require(ResidentMealPortionDispositionObserved.Outcome.class, input.readUnsignedByte()),
                        input.readBoolean() ? java.util.Optional.of(new java.util.UUID(input.readLong(), input.readLong()))
                                : java.util.Optional.empty()));
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
                    FrontierWorldPayloadCodecs.writePosition(output, returned.observedBody().supportingSurface().support());
                    ActorExecutionStateCodec.writeId(output, returned.executionId());
                });
            }
            @Override public FrontierPayload decode(byte[] bytes) {
                return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> new ResidentMealHotReturned(
                        FrontierWorldPayloadCodecs.readSubject(input).value(), input.readLong(),
                        BodyPosition.above(new SurfaceAnchor(FrontierWorldPayloadCodecs.readPosition(input))), ActorExecutionStateCodec.readId(input)));
            }
        };
    }

    static PayloadCodec mealHotAccessCleared() {
        return new PayloadCodec() {
            @Override public String type() { return "frontier.resident_meal_hot_access_cleared"; }
            @Override public byte[] encode(FrontierPayload payload) {
                return FrontierWorldPayloadCodecs.encodeProduction(output -> {
                    ResidentMealHotAccessCleared cleared = (ResidentMealHotAccessCleared) payload;
                    FrontierWorldPayloadCodecs.writeSubject(output, cleared.residentId());
                    output.writeLong(cleared.ambientRevision());
                    FrontierWorldPayloadCodecs.writePosition(output, cleared.observedBody().supportingSurface().support());
                    ActorExecutionStateCodec.writeId(output, cleared.executionId());
                });
            }
            @Override public FrontierPayload decode(byte[] bytes) {
                return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> new ResidentMealHotAccessCleared(
                        FrontierWorldPayloadCodecs.readSubject(input).value(), input.readLong(),
                        BodyPosition.above(new SurfaceAnchor(FrontierWorldPayloadCodecs.readPosition(input))), ActorExecutionStateCodec.readId(input)));
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
                    io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId executionId;
                    FungiblePhysicalObservation.Stack hand;
                    if (materialized) {
                        ResidentMealHotHandMaterialized value = (ResidentMealHotHandMaterialized) payload;
                        id = value.residentId(); revision = value.ambientRevision(); hand = value.observedHand(); executionId = value.executionId();
                    } else {
                        ResidentMealHotHandReleased value = (ResidentMealHotHandReleased) payload;
                        id = value.residentId(); revision = value.ambientRevision(); hand = value.observedHand(); executionId = value.executionId();
                    }
                    FrontierWorldPayloadCodecs.writeSubject(output, id); output.writeLong(revision);
                    PhysicalObservationStackCodec.writeStacks(output, java.util.List.of(hand));
                    ActorExecutionStateCodec.writeId(output, executionId);
                });
            }
            @Override public FrontierPayload decode(byte[] bytes) {
                return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> {
                    var id = FrontierWorldPayloadCodecs.readSubject(input).value(); long revision = input.readLong();
                    var hands = PhysicalObservationStackCodec.readStacks(input);
                    if (hands.size() != 1) throw new IllegalArgumentException("meal hand witness needs one exact stack");
                    var executionId = ActorExecutionStateCodec.readId(input);
                    return materialized ? new ResidentMealHotHandMaterialized(id, revision, hands.getFirst(), executionId)
                            : new ResidentMealHotHandReleased(id, revision, hands.getFirst(), executionId);
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
                    ActorExecutionStateCodec.writeId(output, value.executionId());
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
                    return new ResidentMealColdStep(resident, phase, atTick, next, ActorExecutionStateCodec.readId(input));
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
                ActorExecutionStateCodec.writeId(output, migration.executionId());
            }); }
            @Override public FrontierPayload decode(byte[] bytes) { return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> new ResidentMigrated(
                    FrontierWorldPayloadCodecs.readSubject(input).value(), FrontierWorldPayloadCodecs.readSubject(input).value(),
                    FrontierWorldPayloadCodecs.readSubject(input).value(), FrontierWorldPayloadCodecs.readPosition(input), ActorExecutionStateCodec.readId(input))); }
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
                ActorExecutionStateCodec.writeId(output, advanced.executionId());
                output.writeLong(advanced.routeRevision());
            }); }
            @Override public FrontierPayload decode(byte[] bytes) { return FrontierWorldPayloadCodecs.decodeProduction(bytes,
                    input -> {
                        var resident = FrontierWorldPayloadCodecs.readSubject(input).value(); int next = FrontierWorldStateCodec.readCount(input);
                        var execution = ActorExecutionStateCodec.readId(input);
                        return new ResidentMigrationAdvanced(resident, next, input.readLong(), execution);
                    }); }
        };
    }

    static PayloadCodec migrationRejoinAdvanced() {
        return new PayloadCodec() {
            @Override public String type() { return "frontier.resident_migration_rejoin_advanced"; }
            @Override public byte[] encode(FrontierPayload payload) {
                var value = (ResidentMigrationRejoinAdvanced) payload;
                return FrontierWorldPayloadCodecs.encodeProduction(out -> {
                    FrontierWorldPayloadCodecs.writeSubject(out, value.residentId()); out.writeLong(value.routeRevision());
                    FrontierWorldStateCodec.writeCount(out, value.nextRejoinCursor()); ActorExecutionStateCodec.writeId(out, value.executionId());
                });
            }
            @Override public FrontierPayload decode(byte[] bytes) {
                return FrontierWorldPayloadCodecs.decodeProduction(bytes, in -> new ResidentMigrationRejoinAdvanced(
                        FrontierWorldPayloadCodecs.readSubject(in).value(), in.readLong(), FrontierWorldStateCodec.readCount(in),
                        ActorExecutionStateCodec.readId(in)));
            }
        };
    }

    static PayloadCodec transitAdvanced() {
        return new PayloadCodec() {
            @Override public String type() { return "frontier.resident_transit_advanced"; }
            @Override public byte[] encode(FrontierPayload payload) { return FrontierWorldPayloadCodecs.encodeProduction(output -> {
                ResidentTransitAdvanced advanced = (ResidentTransitAdvanced) payload;
                FrontierWorldPayloadCodecs.writeSubject(output, advanced.residentId()); FrontierWorldStateCodec.writeCount(output, advanced.nextRouteIndex());
                ActorExecutionStateCodec.writeId(output, advanced.executionId());
                output.writeLong(advanced.routeRevision()); output.writeLong(advanced.leaseRevision());
                FrontierWorldPayloadCodecs.writeSubject(output, advanced.bodyId().actorId()); output.writeLong(advanced.bodyId().physicalEpoch());
            }); }
            @Override public FrontierPayload decode(byte[] bytes) { return FrontierWorldPayloadCodecs.decodeProduction(bytes,
                    input -> {
                        var resident = FrontierWorldPayloadCodecs.readSubject(input).value(); int next = FrontierWorldStateCodec.readCount(input);
                        var execution = ActorExecutionStateCodec.readId(input); long routeRevision = input.readLong(), leaseRevision = input.readLong();
                        var body = new io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId(
                                FrontierWorldPayloadCodecs.readSubject(input).value(), input.readLong());
                        return new ResidentTransitAdvanced(resident, next, routeRevision, leaseRevision, execution, body);
                    }); }
        };
    }

    static PayloadCodec migrationBlocked() {
        return new PayloadCodec() {
            @Override public String type() { return "frontier.resident_migration_blocked"; }
            @Override public byte[] encode(FrontierPayload payload) { return FrontierWorldPayloadCodecs.encodeProduction(output -> {
                ResidentMigrationBlocked blocked = (ResidentMigrationBlocked) payload;
                FrontierWorldPayloadCodecs.writeSubject(output, blocked.residentId()); output.writeByte(blocked.reason().wireTag()); FrontierWorldPayloadCodecs.writeDiagnosticTuple(output, blocked.diagnostic());
                ActorExecutionStateCodec.writeId(output, blocked.executionId());
            }); }
            @Override public FrontierPayload decode(byte[] bytes) { return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> {
                var resident = FrontierWorldPayloadCodecs.readSubject(input); int reason = input.readUnsignedByte();
                if (reason >= ResidentMigrationBlockReason.values().length) throw new IllegalArgumentException("unknown resident migration block reason");
                return new ResidentMigrationBlocked(resident.value(), FrontierWireTags.require(ResidentMigrationBlockReason.class, reason), FrontierWorldPayloadCodecs.readDiagnosticTuple(input), ActorExecutionStateCodec.readId(input));
            }); }
        };
    }

    static PayloadCodec migrationResumed() {
        return new PayloadCodec() {
            @Override public String type() { return "frontier.resident_migration_resumed"; }
            @Override public byte[] encode(FrontierPayload payload) { return FrontierWorldPayloadCodecs.encodeProduction(output -> {
                    FrontierWorldPayloadCodecs.writeSubject(output, ((ResidentMigrationResumed) payload).residentId());
                    ActorExecutionStateCodec.writeId(output, ((ResidentMigrationResumed) payload).executionId()); }); }
            @Override public FrontierPayload decode(byte[] bytes) { return FrontierWorldPayloadCodecs.decodeProduction(bytes,
                    input -> new ResidentMigrationResumed(FrontierWorldPayloadCodecs.readSubject(input).value(), ActorExecutionStateCodec.readId(input))); }
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
        ActorExecutionStateCodec.writeId(output, journey.executionId());
        output.writeLong(journey.routeRevision()); TraversalRejoinCodec.write(output, journey.rejoin());
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
                FrontierWireTags.require(ResidentMigrationStatus.class, status), reason, ActorExecutionStateCodec.readId(input),
                input.readLong(), TraversalRejoinCodec.read(input));
    }
}
