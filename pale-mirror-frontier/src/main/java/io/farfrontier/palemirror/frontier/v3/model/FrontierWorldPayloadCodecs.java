package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FixedRatio;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.kernel.KernelPayloadCodecs;
import io.farfrontier.palemirror.frontier.v3.kernel.PayloadCodec;
import io.farfrontier.palemirror.frontier.v3.kernel.PayloadCodecs;

import java.nio.ByteBuffer;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;

/** Complete payload registry for the currently installed v3 world processes. */
public final class FrontierWorldPayloadCodecs {
    private FrontierWorldPayloadCodecs() { }
    public static PayloadCodecs create() {
        return PayloadCodecs.merge(KernelPayloadCodecs.scheduleEffects(), new PayloadCodecs(List.of(
                new InfectionCodec(), new ProductionStartedCodec(), new ProductionCompletedCodec(), new ProductionBlockedCodec(),
                new ContractCreatedCodec(), new CargoLoadedCodec(), new OperationCreatedCodec(), new OperationAdvancedCodec(),
                new OperationColdSuspendedCodec(), new PhysicalIntentPreparedCodec(), new PhysicalIntentTransitionCodec(),
                new SceneLeasePreparedCodec(), new SceneLeaseTransitionCodec(), new SceneLeaseReleasedCodec())));
    }
    private static final class InfectionCodec implements PayloadCodec {
        @Override public String type() { return "frontier.infection_changed"; }
        @Override public byte[] encode(FrontierPayload payload) {
            InfectionChanged changed = (InfectionChanged) payload;
            return ByteBuffer.allocate(16).putInt(changed.cell().x()).putInt(changed.cell().z()).putLong(changed.intensity().value().raw()).array();
        }
        @Override public FrontierPayload decode(byte[] bytes) {
            if (bytes.length != 16) throw new IllegalArgumentException("malformed infection change payload");
            ByteBuffer input = ByteBuffer.wrap(bytes);
            return new InfectionChanged(new InfectionCell(input.getInt(), input.getInt()), new FixedRatio(new FixedScalar(input.getLong())));
        }
    }
    private static final class ProductionStartedCodec implements PayloadCodec {
        @Override public String type() { return "frontier.production_started"; }
        @Override public byte[] encode(FrontierPayload payload) {
            ProductionStarted started = (ProductionStarted) payload;
            return encodeProduction(output -> { writeJob(output, started.job()); writeSubject(output, started.inputItemId()); });
        }
        @Override public FrontierPayload decode(byte[] bytes) {
            return decodeProduction(bytes, input -> {
                ProductionJob job = readJob(input); SubjectIdHolder item = readSubject(input);
                return new ProductionStarted(job, item.value());
            });
        }
    }
    private static final class ProductionCompletedCodec implements PayloadCodec {
        @Override public String type() { return "frontier.production_completed"; }
        @Override public byte[] encode(FrontierPayload payload) {
            ProductionCompleted completed = (ProductionCompleted) payload;
            return encodeProduction(output -> {
                writeSubject(output, completed.jobId()); writeSubject(output, completed.output().id()); writeString(output, completed.output().itemKind());
                output.writeByte(completed.output().count());
                if (!(completed.output().custody() instanceof InventoryCustody.ContainerSlot slot)) throw new IllegalArgumentException("production output must have container custody");
                writeSubject(output, slot.containerId()); output.writeByte(slot.slot());
            });
        }
        @Override public FrontierPayload decode(byte[] bytes) {
            return decodeProduction(bytes, input -> {
                SubjectIdHolder job = readSubject(input); SubjectIdHolder output = readSubject(input); String kind = readString(input); int count = input.readUnsignedByte();
                SubjectIdHolder container = readSubject(input); int slot = input.readUnsignedByte();
                return new ProductionCompleted(job.value(), new ExactItemStack(output.value(), kind, count, new InventoryCustody.ContainerSlot(container.value(), slot)));
            });
        }
    }
    private static final class ProductionBlockedCodec implements PayloadCodec {
        @Override public String type() { return "frontier.production_blocked"; }
        @Override public byte[] encode(FrontierPayload payload) {
            ProductionBlocked blocked = (ProductionBlocked) payload;
            return encodeProduction(output -> { writeSubject(output, blocked.settlementId()); writeSubject(output, blocked.facilityId()); writeSubject(output, blocked.workId()); output.writeByte(blocked.reason().ordinal()); });
        }
        @Override public FrontierPayload decode(byte[] bytes) {
            return decodeProduction(bytes, input -> {
                SubjectIdHolder settlement = readSubject(input); SubjectIdHolder facility = readSubject(input); SubjectIdHolder work = readSubject(input);
                int ordinal = input.readUnsignedByte();
                if (ordinal >= ProductionBlockReason.values().length) throw new IllegalArgumentException("unknown production block reason");
                return new ProductionBlocked(settlement.value(), facility.value(), work.value(), ProductionBlockReason.values()[ordinal]);
            });
        }
    }
    private static final class ContractCreatedCodec implements PayloadCodec {
        @Override public String type() { return "frontier.supply_contract_created"; }
        @Override public byte[] encode(FrontierPayload payload) { return encodeProduction(output -> writeContract(output, ((SupplyContractCreated) payload).contract())); }
        @Override public FrontierPayload decode(byte[] bytes) { return decodeProduction(bytes, input -> new SupplyContractCreated(readContract(input))); }
    }
    private static final class CargoLoadedCodec implements PayloadCodec {
        @Override public String type() { return "frontier.cargo_loaded"; }
        @Override public byte[] encode(FrontierPayload payload) {
            CargoLoaded loaded = (CargoLoaded) payload;
            return encodeProduction(output -> {
                writeSubject(output, loaded.contractId()); writeSubject(output, loaded.cargo().id()); writeSubject(output, loaded.cargo().ownerId());
                output.writeByte(loaded.cargo().itemIds().size());
                for (var item : loaded.cargo().itemIds()) writeSubject(output, item);
            });
        }
        @Override public FrontierPayload decode(byte[] bytes) { return decodeProduction(bytes, input -> {
            SubjectIdHolder contract = readSubject(input); SubjectIdHolder cargo = readSubject(input); SubjectIdHolder owner = readSubject(input); int count = input.readUnsignedByte();
            java.util.ArrayList<io.farfrontier.palemirror.frontier.v3.api.SubjectId> items = new java.util.ArrayList<>(); for (int index = 0; index < count; index++) items.add(readSubject(input).value());
            return new CargoLoaded(contract.value(), new CargoBatch(cargo.value(), owner.value(), items));
        }); }
    }
    private static final class OperationCreatedCodec implements PayloadCodec {
        @Override public String type() { return "frontier.operation_created"; }
        @Override public byte[] encode(FrontierPayload payload) { return encodeProduction(output -> writeOperation(output, ((OperationCreated) payload).operation())); }
        @Override public FrontierPayload decode(byte[] bytes) { return decodeProduction(bytes, input -> new OperationCreated(readOperation(input))); }
    }
    private static final class OperationAdvancedCodec implements PayloadCodec {
        @Override public String type() { return "frontier.operation_advanced"; }
        @Override public byte[] encode(FrontierPayload payload) {
            OperationAdvanced advanced = (OperationAdvanced) payload;
            return encodeProduction(output -> { writeSubject(output, advanced.operationId()); output.writeByte(advanced.routeIndex()); output.writeByte(advanced.stage().ordinal()); });
        }
        @Override public FrontierPayload decode(byte[] bytes) { return decodeProduction(bytes, input -> {
            SubjectIdHolder id = readSubject(input); int routeIndex = input.readUnsignedByte(); int stage = input.readUnsignedByte();
            if (stage >= OperationStage.values().length) throw new IllegalArgumentException("unknown route operation stage");
            return new OperationAdvanced(id.value(), routeIndex, OperationStage.values()[stage]);
        }); }
    }
    private static final class OperationColdSuspendedCodec implements PayloadCodec {
        @Override public String type() { return "frontier.operation_cold_suspended"; }
        @Override public byte[] encode(FrontierPayload payload) { return encodeProduction(output -> { OperationColdSuspended suspended = (OperationColdSuspended) payload;
            writeSubject(output, suspended.operationId()); writeString(output, suspended.leaseId().value()); }); }
        @Override public FrontierPayload decode(byte[] bytes) { return decodeProduction(bytes, input ->
                new OperationColdSuspended(readSubject(input).value(), new io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId(readString(input)))); }
    }
    private static final class PhysicalIntentPreparedCodec implements PayloadCodec {
        @Override public String type() { return "frontier.physical_intent_prepared"; }
        @Override public byte[] encode(FrontierPayload payload) { return encodeProduction(output -> writePhysicalIntent(output, ((PhysicalIntentPrepared) payload).intent())); }
        @Override public FrontierPayload decode(byte[] bytes) { return decodeProduction(bytes, input -> new PhysicalIntentPrepared(readPhysicalIntent(input))); }
    }
    private static final class PhysicalIntentTransitionCodec implements PayloadCodec {
        @Override public String type() { return "frontier.physical_intent_transition"; }
        @Override public byte[] encode(FrontierPayload payload) {
            PhysicalIntentTransition transition = (PhysicalIntentTransition) payload;
            return encodeProduction(output -> { writeString(output, transition.intentId().value()); output.writeByte(transition.status().ordinal());
                output.writeBoolean(transition.observation().isPresent());
                if (transition.observation().isPresent()) writeCargoHandoffObservation(output, transition.observation().orElseThrow()); });
        }
        @Override public FrontierPayload decode(byte[] bytes) { return decodeProduction(bytes, input -> {
            var id = new io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId(readString(input)); int status = input.readUnsignedByte(); boolean observed = input.readBoolean();
            var observation = observed ? java.util.Optional.of(readCargoHandoffObservation(input)) : java.util.Optional.<CargoHandoffObservation>empty();
            if (status >= io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.values().length) throw new IllegalArgumentException("unknown physical intent status");
            return new PhysicalIntentTransition(id, io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.values()[status], observation);
        }); }
    }
    private static final class SceneLeasePreparedCodec implements PayloadCodec {
        @Override public String type() { return "frontier.scene_lease_prepared"; }
        @Override public byte[] encode(FrontierPayload payload) { return encodeProduction(output -> writeSceneLease(output, ((SceneLeasePrepared) payload).lease())); }
        @Override public FrontierPayload decode(byte[] bytes) { return decodeProduction(bytes, input -> new SceneLeasePrepared(readSceneLease(input))); }
    }
    private static final class SceneLeaseTransitionCodec implements PayloadCodec {
        @Override public String type() { return "frontier.scene_lease_transition"; }
        @Override public byte[] encode(FrontierPayload payload) { return encodeProduction(output -> { SceneLeaseTransition transition = (SceneLeaseTransition) payload;
            writeString(output, transition.leaseId().value()); output.writeByte(transition.status().ordinal()); }); }
        @Override public FrontierPayload decode(byte[] bytes) { return decodeProduction(bytes, input -> {
            var id = new io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId(readString(input)); int status = input.readUnsignedByte();
            if (status >= SceneLeaseStatus.values().length) throw new IllegalArgumentException("unknown scene lease status");
            return new SceneLeaseTransition(id, SceneLeaseStatus.values()[status]);
        }); }
    }
    private static final class SceneLeaseReleasedCodec implements PayloadCodec {
        @Override public String type() { return "frontier.scene_lease_released"; }
        @Override public byte[] encode(FrontierPayload payload) { return encodeProduction(output -> {
            SceneLeaseReleased released = (SceneLeaseReleased) payload; writeString(output, released.leaseId().value()); output.writeByte(released.members().size());
            for (SceneMemberPosition member : released.members()) { writeSubject(output, member.actorId()); output.writeInt(member.position().x()); output.writeInt(member.position().y()); output.writeInt(member.position().z()); }
        }); }
        @Override public FrontierPayload decode(byte[] bytes) { return decodeProduction(bytes, input -> {
            var id = new io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId(readString(input)); java.util.ArrayList<SceneMemberPosition> members = new java.util.ArrayList<>();
            for (int index = 0, count = input.readUnsignedByte(); index < count; index++) members.add(new SceneMemberPosition(readSubject(input).value(), new BlockPosition(input.readInt(), input.readInt(), input.readInt())));
            return new SceneLeaseReleased(id, members);
        }); }
    }

    @FunctionalInterface private interface ProductionEncoder { void write(DataOutputStream output) throws IOException; }
    @FunctionalInterface private interface ProductionDecoder { FrontierPayload read(DataInputStream input) throws IOException; }
    private record SubjectIdHolder(io.farfrontier.palemirror.frontier.v3.api.SubjectId value) { }
    private static byte[] encodeProduction(ProductionEncoder encoder) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) { encoder.write(output); }
            return bytes.toByteArray();
        } catch (IOException error) { throw new IllegalStateException("in-memory production payload encoding failed", error); }
    }
    private static FrontierPayload decodeProduction(byte[] bytes, ProductionDecoder decoder) {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            FrontierPayload payload = decoder.read(input);
            if (input.available() != 0) throw new IllegalArgumentException("trailing production payload bytes");
            return payload;
        } catch (IOException error) { throw new IllegalArgumentException("truncated production payload", error); }
    }
    private static void writeJob(DataOutputStream output, ProductionJob job) throws IOException {
        writeSubject(output, job.id()); writeSubject(output, job.settlementId()); writeSubject(output, job.facilityId()); writeSubject(output, job.workerId());
        writeSubject(output, job.consumedItemId()); writeSubject(output, job.outputItemId()); writeString(output, job.outputItemKind()); output.writeByte(job.outputCount());
    }
    private static ProductionJob readJob(DataInputStream input) throws IOException {
        return new ProductionJob(readSubject(input).value(), readSubject(input).value(), readSubject(input).value(), readSubject(input).value(),
                readSubject(input).value(), readSubject(input).value(), readString(input), input.readUnsignedByte());
    }
    private static void writeContract(DataOutputStream output, SupplyContract contract) throws IOException {
        writeSubject(output, contract.id()); writeSubject(output, contract.settlementId()); writeSubject(output, contract.recipientId());
        writeSubject(output, contract.cargoId()); writeString(output, contract.itemKind()); output.writeByte(contract.itemCount()); output.writeByte(contract.status().ordinal());
    }
    private static SupplyContract readContract(DataInputStream input) throws IOException {
        SubjectIdHolder id = readSubject(input); SubjectIdHolder settlement = readSubject(input); SubjectIdHolder recipient = readSubject(input);
        SubjectIdHolder cargo = readSubject(input); String kind = readString(input); int count = input.readUnsignedByte(); int status = input.readUnsignedByte();
        if (status >= ContractStatus.values().length) throw new IllegalArgumentException("unknown contract status");
        return new SupplyContract(id.value(), settlement.value(), recipient.value(), cargo.value(), kind, count, ContractStatus.values()[status]);
    }
    private static void writeOperation(DataOutputStream output, RouteOperation operation) throws IOException {
        writeSubject(output, operation.id()); writeSubject(output, operation.settlementId()); writeSubject(output, operation.cargoId()); writeSubject(output, operation.destinationId());
        output.writeByte(operation.participantIds().size());
        for (var participant : operation.participantIds()) writeSubject(output, participant);
        output.writeByte(operation.route().size());
        for (BlockPosition point : operation.route()) { output.writeInt(point.x()); output.writeInt(point.y()); output.writeInt(point.z()); }
        output.writeByte(operation.routeIndex()); output.writeByte(operation.stage().ordinal());
    }
    private static RouteOperation readOperation(DataInputStream input) throws IOException {
        SubjectIdHolder id = readSubject(input); SubjectIdHolder settlement = readSubject(input); SubjectIdHolder cargo = readSubject(input); SubjectIdHolder destination = readSubject(input);
        java.util.ArrayList<io.farfrontier.palemirror.frontier.v3.api.SubjectId> participants = new java.util.ArrayList<>();
        for (int index = 0, count = input.readUnsignedByte(); index < count; index++) participants.add(readSubject(input).value());
        java.util.ArrayList<BlockPosition> route = new java.util.ArrayList<>();
        for (int index = 0, count = input.readUnsignedByte(); index < count; index++) route.add(new BlockPosition(input.readInt(), input.readInt(), input.readInt()));
        int routeIndex = input.readUnsignedByte(); int stage = input.readUnsignedByte();
        if (stage >= OperationStage.values().length) throw new IllegalArgumentException("unknown route operation stage");
        return new RouteOperation(id.value(), settlement.value(), cargo.value(), destination.value(), participants, route, routeIndex, OperationStage.values()[stage]);
    }
    private static void writePhysicalIntent(DataOutputStream output, io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent intent) throws IOException {
        writeString(output, intent.id().value()); output.writeByte(intent.kind().ordinal()); output.writeByte(intent.status().ordinal());
        writeSubject(output, intent.causeSubjectId()); output.writeByte(intent.subjectIds().size());
        for (var subject : intent.subjectIds()) writeSubject(output, subject);
        output.writeLong(intent.origin().x().raw()); output.writeLong(intent.origin().y().raw()); output.writeLong(intent.origin().z().raw());
        output.writeByte(intent.radiusBlocks()); output.writeByte(intent.postcondition().ordinal()); output.writeBoolean(intent.postconditionObservationId().isPresent());
        if (intent.postconditionObservationId().isPresent()) writeString(output, intent.postconditionObservationId().orElseThrow().value());
    }
    private static io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent readPhysicalIntent(DataInputStream input) throws IOException {
        io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId id = new io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId(readString(input));
        int kind = input.readUnsignedByte(); int status = input.readUnsignedByte(); SubjectIdHolder cause = readSubject(input);
        java.util.ArrayList<io.farfrontier.palemirror.frontier.v3.api.SubjectId> subjects = new java.util.ArrayList<>();
        for (int index = 0, count = input.readUnsignedByte(); index < count; index++) subjects.add(readSubject(input).value());
        io.farfrontier.palemirror.frontier.v3.api.FixedPosition origin = new io.farfrontier.palemirror.frontier.v3.api.FixedPosition(
                new io.farfrontier.palemirror.frontier.v3.api.FixedScalar(input.readLong()), new io.farfrontier.palemirror.frontier.v3.api.FixedScalar(input.readLong()), new io.farfrontier.palemirror.frontier.v3.api.FixedScalar(input.readLong()));
        int radius = input.readUnsignedByte(); int postcondition = input.readUnsignedByte(); boolean observed = input.readBoolean();
        var observation = observed ? java.util.Optional.of(new io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId(readString(input))) : java.util.Optional.<io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId>empty();
        if (kind >= io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.values().length
                || status >= io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.values().length
                || postcondition >= io.farfrontier.palemirror.frontier.v3.api.PhysicalPostcondition.values().length) {
            throw new IllegalArgumentException("unknown physical intent enum value");
        }
        return new io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent(id,
                io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.values()[kind], io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.values()[status],
                cause.value(), subjects, origin, radius, io.farfrontier.palemirror.frontier.v3.api.PhysicalPostcondition.values()[postcondition], observation);
    }
    private static void writeCargoHandoffObservation(DataOutputStream output, CargoHandoffObservation observation) throws IOException {
        writeString(output, observation.id().value()); writeString(output, observation.intentId().value()); writeSubject(output, observation.cargoId());
        output.writeByte(observation.placements().size());
        for (CargoHandoffPlacement placement : observation.placements()) {
            writeSubject(output, placement.itemId()); writeSubject(output, placement.receiverSlot().containerId()); output.writeByte(placement.receiverSlot().slot());
        }
    }
    private static CargoHandoffObservation readCargoHandoffObservation(DataInputStream input) throws IOException {
        var id = new io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId(readString(input));
        var intentId = new io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId(readString(input));
        SubjectIdHolder cargoId = readSubject(input);
        java.util.ArrayList<CargoHandoffPlacement> placements = new java.util.ArrayList<>();
        for (int index = 0, count = input.readUnsignedByte(); index < count; index++) {
            SubjectIdHolder itemId = readSubject(input); SubjectIdHolder receiver = readSubject(input);
            placements.add(new CargoHandoffPlacement(itemId.value(), new InventoryCustody.ContainerSlot(receiver.value(), input.readUnsignedByte())));
        }
        return new CargoHandoffObservation(id, intentId, cargoId.value(), placements);
    }
    private static void writeSceneLease(DataOutputStream output, SceneLease lease) throws IOException {
        writeString(output, lease.id().value()); writeSubject(output, lease.operationId()); writeSubject(output, lease.cargoId());
        output.writeInt(lease.handoffPosition().x()); output.writeInt(lease.handoffPosition().y()); output.writeInt(lease.handoffPosition().z());
        output.writeLong(lease.handoffInstant().ticks()); output.writeLong(lease.revision()); output.writeByte(lease.status().ordinal()); output.writeByte(lease.members().size());
        for (SceneMember member : lease.members()) { writeSubject(output, member.actorId()); writeString(output, member.entityId().toString()); }
    }
    private static SceneLease readSceneLease(DataInputStream input) throws IOException {
        var id = new io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId(readString(input)); SubjectIdHolder operation = readSubject(input); SubjectIdHolder cargo = readSubject(input);
        BlockPosition position = new BlockPosition(input.readInt(), input.readInt(), input.readInt()); long handoff = input.readLong(); long revision = input.readLong(); int status = input.readUnsignedByte();
        if (status >= SceneLeaseStatus.values().length) throw new IllegalArgumentException("unknown scene lease status");
        java.util.ArrayList<SceneMember> members = new java.util.ArrayList<>();
        for (int index = 0, count = input.readUnsignedByte(); index < count; index++) members.add(new SceneMember(readSubject(input).value(), java.util.UUID.fromString(readString(input))));
        return new SceneLease(id, operation.value(), cargo.value(), position, new io.farfrontier.palemirror.frontier.v3.api.SimInstant(handoff), revision, SceneLeaseStatus.values()[status], members);
    }
    private static void writeSubject(DataOutputStream output, io.farfrontier.palemirror.frontier.v3.api.SubjectId value) throws IOException { writeString(output, value.value()); }
    private static SubjectIdHolder readSubject(DataInputStream input) throws IOException { return new SubjectIdHolder(new io.farfrontier.palemirror.frontier.v3.api.SubjectId(readString(input))); }
    private static void writeString(DataOutputStream output, String value) throws IOException {
        byte[] encoded = value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (encoded.length > 256) throw new IllegalArgumentException("production payload field is too long");
        output.writeShort(encoded.length); output.write(encoded);
    }
    private static String readString(DataInputStream input) throws IOException {
        int length = input.readUnsignedShort();
        if (length > 256) throw new IllegalArgumentException("production payload field is too long");
        byte[] encoded = input.readNBytes(length);
        if (encoded.length != length) throw new IOException("truncated production payload field");
        return new String(encoded, java.nio.charset.StandardCharsets.UTF_8);
    }
}
