package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FixedRatio;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.FixedPosition;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalPostcondition;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.StateCodec;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Versioned exact state codec. Snapshot checksumming is owned by the persistence envelope. */
public final class FrontierWorldStateCodec implements StateCodec<FrontierWorldState> {
    private static final int MAGIC = 0x4656334D;
    private static final int VERSION = 6;
    private static final int MAX_ENTRIES = 65_535;

    @Override public byte[] encode(FrontierWorldState state) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                output.writeInt(MAGIC); output.writeByte(VERSION);
                writeString(output, state.bootstrap().worldId().value()); output.writeLong(state.bootstrap().seed());
                writeActors(output, state.actorLocations());
                writeStructures(output, state.structureConditions());
                writeInfection(output, state.infection());
                writeInventory(output, state.inventory());
                writeProductionJobs(output, state.productionJobs());
                writeContracts(output, state.contracts());
                writeOperations(output, state.operations());
                writePhysicalIntents(output, state.physicalIntents());
            }
            return bytes.toByteArray();
        } catch (IOException impossible) { throw new IllegalStateException("in-memory Frontier v3 state encoding failed", impossible); }
    }

    @Override public FrontierWorldState decode(byte[] encoded) {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(encoded))) {
            if (input.readInt() != MAGIC) throw new IllegalArgumentException("unknown Frontier v3 state magic");
            if (input.readUnsignedByte() != VERSION) throw new IllegalArgumentException("unknown Frontier v3 state version");
            FrontierBootstrap bootstrap = FrontierBootstrapper.create(new WorldId(readString(input)), input.readLong());
            FrontierWorldState state = new FrontierWorldState(bootstrap, readActors(input), readStructures(input), readInfection(input),
                    readInventory(input), readProductionJobs(input), readContracts(input), readOperations(input), readPhysicalIntents(input));
            if (input.available() != 0) throw new IllegalArgumentException("trailing Frontier v3 state bytes");
            return state;
        } catch (IOException error) { throw new IllegalArgumentException("truncated Frontier v3 state", error); }
    }

    private static void writeActors(DataOutputStream output, Map<SubjectId, ActorLocation> values) throws IOException {
        writeCount(output, values.size());
        for (Map.Entry<SubjectId, ActorLocation> entry : values.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            writeString(output, entry.getKey().value()); writePosition(output, entry.getValue().position());
        }
    }
    private static Map<SubjectId, ActorLocation> readActors(DataInputStream input) throws IOException {
        Map<SubjectId, ActorLocation> values = new LinkedHashMap<>();
        for (int index = 0, count = readCount(input); index < count; index++) {
            SubjectId id = new SubjectId(readString(input));
            if (values.put(id, new ActorLocation(readPosition(input))) != null) throw new IllegalArgumentException("duplicate actor state id: " + id.value());
        }
        return values;
    }
    private static void writeStructures(DataOutputStream output, Map<SubjectId, StructureCondition> values) throws IOException {
        writeCount(output, values.size());
        for (Map.Entry<SubjectId, StructureCondition> entry : values.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            writeString(output, entry.getKey().value()); output.writeByte(entry.getValue().ordinal());
        }
    }
    private static Map<SubjectId, StructureCondition> readStructures(DataInputStream input) throws IOException {
        Map<SubjectId, StructureCondition> values = new LinkedHashMap<>();
        for (int index = 0, count = readCount(input); index < count; index++) {
            SubjectId id = new SubjectId(readString(input));
            int ordinal = input.readUnsignedByte();
            if (ordinal >= StructureCondition.values().length || values.put(id, StructureCondition.values()[ordinal]) != null) throw new IllegalArgumentException("invalid or duplicate structure state");
        }
        return values;
    }
    private static void writeInfection(DataOutputStream output, Map<InfectionCell, FixedRatio> values) throws IOException {
        writeCount(output, values.size());
        for (Map.Entry<InfectionCell, FixedRatio> entry : values.entrySet().stream().sorted(Comparator.comparingInt((Map.Entry<InfectionCell, FixedRatio> entry) -> entry.getKey().x()).thenComparingInt(entry -> entry.getKey().z())).toList()) {
            output.writeInt(entry.getKey().x()); output.writeInt(entry.getKey().z()); output.writeLong(entry.getValue().value().raw());
        }
    }
    private static Map<InfectionCell, FixedRatio> readInfection(DataInputStream input) throws IOException {
        Map<InfectionCell, FixedRatio> values = new LinkedHashMap<>();
        for (int index = 0, count = readCount(input); index < count; index++) {
            InfectionCell cell = new InfectionCell(input.readInt(), input.readInt());
            if (values.put(cell, new FixedRatio(new FixedScalar(input.readLong()))) != null) throw new IllegalArgumentException("duplicate infection cell");
        }
        return values;
    }
    private static void writeInventory(DataOutputStream output, ExactInventory inventory) throws IOException {
        writeCount(output, inventory.containers().size());
        for (ContainerRecord value : inventory.containers().values().stream().sorted(java.util.Comparator.comparing(ContainerRecord::id)).toList()) {
            writeString(output, value.id().value()); writeString(output, value.ownerId().value()); output.writeByte(value.slotCount());
        }
        writeCount(output, inventory.items().size());
        for (ExactItemStack value : inventory.items().values().stream().sorted(java.util.Comparator.comparing(ExactItemStack::id)).toList()) {
            writeString(output, value.id().value()); writeString(output, value.itemKind()); output.writeByte(value.count()); writeCustody(output, value.custody());
        }
        writeCount(output, inventory.cargo().size());
        for (CargoBatch value : inventory.cargo().values().stream().sorted(java.util.Comparator.comparing(CargoBatch::id)).toList()) {
            writeString(output, value.id().value()); writeString(output, value.ownerId().value()); writeCount(output, value.itemIds().size());
            for (SubjectId item : value.itemIds()) writeString(output, item.value());
        }
        writeCount(output, inventory.playerItems().size());
        for (Map.Entry<UUID, List<SubjectId>> entry : inventory.playerItems().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            writeString(output, entry.getKey().toString()); writeCount(output, entry.getValue().size());
            for (SubjectId item : entry.getValue()) writeString(output, item.value());
        }
    }
    private static ExactInventory readInventory(DataInputStream input) throws IOException {
        Map<SubjectId, ContainerRecord> containers = new LinkedHashMap<>();
        for (int index = 0, count = readCount(input); index < count; index++) {
            SubjectId id = new SubjectId(readString(input));
            if (containers.put(id, new ContainerRecord(id, new SubjectId(readString(input)), input.readUnsignedByte())) != null) throw new IllegalArgumentException("duplicate container id");
        }
        Map<SubjectId, ExactItemStack> items = new LinkedHashMap<>();
        for (int index = 0, count = readCount(input); index < count; index++) {
            SubjectId id = new SubjectId(readString(input));
            if (items.put(id, new ExactItemStack(id, readString(input), input.readUnsignedByte(), readCustody(input))) != null) throw new IllegalArgumentException("duplicate item stack id");
        }
        Map<SubjectId, CargoBatch> cargo = new LinkedHashMap<>();
        for (int index = 0, count = readCount(input); index < count; index++) {
            SubjectId id = new SubjectId(readString(input)); SubjectId owner = new SubjectId(readString(input));
            java.util.ArrayList<SubjectId> itemIds = new java.util.ArrayList<>();
            for (int item = 0, itemCount = readCount(input); item < itemCount; item++) itemIds.add(new SubjectId(readString(input)));
            if (cargo.put(id, new CargoBatch(id, owner, itemIds)) != null) throw new IllegalArgumentException("duplicate cargo id");
        }
        Map<UUID, List<SubjectId>> players = new LinkedHashMap<>();
        for (int index = 0, count = readCount(input); index < count; index++) {
            UUID player = UUID.fromString(readString(input)); java.util.ArrayList<SubjectId> itemIds = new java.util.ArrayList<>();
            for (int item = 0, itemCount = readCount(input); item < itemCount; item++) itemIds.add(new SubjectId(readString(input)));
            if (players.put(player, itemIds) != null) throw new IllegalArgumentException("duplicate player custody id");
        }
        return new ExactInventory(containers, items, cargo, players);
    }
    private static void writeProductionJobs(DataOutputStream output, Map<SubjectId, ProductionJob> jobs) throws IOException {
        writeCount(output, jobs.size());
        for (ProductionJob job : jobs.values().stream().sorted(java.util.Comparator.comparing(ProductionJob::id)).toList()) {
            writeString(output, job.id().value()); writeString(output, job.settlementId().value()); writeString(output, job.facilityId().value());
            writeString(output, job.workerId().value()); writeString(output, job.consumedItemId().value()); writeString(output, job.outputItemId().value());
            writeString(output, job.outputItemKind()); output.writeByte(job.outputCount());
        }
    }
    private static Map<SubjectId, ProductionJob> readProductionJobs(DataInputStream input) throws IOException {
        Map<SubjectId, ProductionJob> jobs = new LinkedHashMap<>();
        for (int index = 0, count = readCount(input); index < count; index++) {
            SubjectId id = new SubjectId(readString(input));
            ProductionJob job = new ProductionJob(id, new SubjectId(readString(input)), new SubjectId(readString(input)),
                    new SubjectId(readString(input)), new SubjectId(readString(input)), new SubjectId(readString(input)), readString(input), input.readUnsignedByte());
            if (jobs.put(id, job) != null) throw new IllegalArgumentException("duplicate production job id");
        }
        return jobs;
    }
    private static void writeContracts(DataOutputStream output, Map<SubjectId, SupplyContract> contracts) throws IOException {
        writeCount(output, contracts.size());
        for (SupplyContract contract : contracts.values().stream().sorted(java.util.Comparator.comparing(SupplyContract::id)).toList()) {
            writeString(output, contract.id().value()); writeString(output, contract.settlementId().value()); writeString(output, contract.recipientId().value());
            writeString(output, contract.cargoId().value()); writeString(output, contract.itemKind()); output.writeByte(contract.itemCount()); output.writeByte(contract.status().ordinal());
        }
    }
    private static Map<SubjectId, SupplyContract> readContracts(DataInputStream input) throws IOException {
        Map<SubjectId, SupplyContract> contracts = new LinkedHashMap<>();
        for (int index = 0, count = readCount(input); index < count; index++) {
            SubjectId id = new SubjectId(readString(input)); SubjectId settlement = new SubjectId(readString(input)); SubjectId recipient = new SubjectId(readString(input)); SubjectId cargo = new SubjectId(readString(input));
            String kind = readString(input); int itemCount = input.readUnsignedByte(); int status = input.readUnsignedByte();
            if (status >= ContractStatus.values().length
                    || contracts.put(id, new SupplyContract(id, settlement, recipient, cargo, kind, itemCount, ContractStatus.values()[status])) != null) {
                throw new IllegalArgumentException("invalid or duplicate supply contract");
            }
        }
        return contracts;
    }
    private static void writeOperations(DataOutputStream output, Map<SubjectId, RouteOperation> operations) throws IOException {
        writeCount(output, operations.size());
        for (RouteOperation operation : operations.values().stream().sorted(Comparator.comparing(RouteOperation::id)).toList()) {
            writeString(output, operation.id().value()); writeString(output, operation.settlementId().value()); writeString(output, operation.cargoId().value());
            writeString(output, operation.destinationId().value()); writeCount(output, operation.participantIds().size());
            for (SubjectId participant : operation.participantIds()) writeString(output, participant.value());
            writeCount(output, operation.route().size());
            for (BlockPosition point : operation.route()) writePosition(output, point);
            output.writeByte(operation.routeIndex()); output.writeByte(operation.stage().ordinal());
        }
    }
    private static Map<SubjectId, RouteOperation> readOperations(DataInputStream input) throws IOException {
        Map<SubjectId, RouteOperation> operations = new LinkedHashMap<>();
        for (int index = 0, count = readCount(input); index < count; index++) {
            SubjectId id = new SubjectId(readString(input)); SubjectId settlement = new SubjectId(readString(input)); SubjectId cargo = new SubjectId(readString(input));
            SubjectId destination = new SubjectId(readString(input)); java.util.ArrayList<SubjectId> participants = new java.util.ArrayList<>();
            for (int participant = 0, participantCount = readCount(input); participant < participantCount; participant++) participants.add(new SubjectId(readString(input)));
            java.util.ArrayList<BlockPosition> route = new java.util.ArrayList<>();
            for (int point = 0, pointCount = readCount(input); point < pointCount; point++) route.add(readPosition(input));
            int routeIndex = input.readUnsignedByte(); int stage = input.readUnsignedByte();
            if (stage >= OperationStage.values().length || operations.put(id, new RouteOperation(id, settlement, cargo, destination, participants, route, routeIndex, OperationStage.values()[stage])) != null) {
                throw new IllegalArgumentException("invalid or duplicate route operation");
            }
        }
        return operations;
    }
    private static void writePhysicalIntents(DataOutputStream output, Map<PhysicalIntentId, PhysicalIntent> intents) throws IOException {
        writeCount(output, intents.size());
        for (PhysicalIntent intent : intents.values().stream().sorted(Comparator.comparing(PhysicalIntent::id)).toList()) {
            writeString(output, intent.id().value()); output.writeByte(intent.kind().ordinal()); output.writeByte(intent.status().ordinal());
            writeString(output, intent.causeSubjectId().value()); writeCount(output, intent.subjectIds().size());
            for (SubjectId subject : intent.subjectIds()) writeString(output, subject.value());
            output.writeLong(intent.origin().x().raw()); output.writeLong(intent.origin().y().raw()); output.writeLong(intent.origin().z().raw());
            output.writeByte(intent.radiusBlocks()); output.writeByte(intent.postcondition().ordinal());
        }
    }
    private static Map<PhysicalIntentId, PhysicalIntent> readPhysicalIntents(DataInputStream input) throws IOException {
        Map<PhysicalIntentId, PhysicalIntent> intents = new LinkedHashMap<>();
        for (int index = 0, count = readCount(input); index < count; index++) {
            PhysicalIntentId id = new PhysicalIntentId(readString(input)); int kind = input.readUnsignedByte(); int status = input.readUnsignedByte();
            SubjectId cause = new SubjectId(readString(input)); java.util.ArrayList<SubjectId> subjects = new java.util.ArrayList<>();
            for (int subject = 0, subjectCount = readCount(input); subject < subjectCount; subject++) subjects.add(new SubjectId(readString(input)));
            FixedPosition origin = new FixedPosition(new FixedScalar(input.readLong()), new FixedScalar(input.readLong()), new FixedScalar(input.readLong()));
            int radius = input.readUnsignedByte(); int postcondition = input.readUnsignedByte();
            if (kind >= PhysicalIntentKind.values().length || status >= PhysicalIntentStatus.values().length || postcondition >= PhysicalPostcondition.values().length
                    || intents.put(id, new PhysicalIntent(id, PhysicalIntentKind.values()[kind], PhysicalIntentStatus.values()[status], cause, subjects, origin, radius, PhysicalPostcondition.values()[postcondition])) != null) {
                throw new IllegalArgumentException("invalid or duplicate physical intent");
            }
        }
        return intents;
    }
    private static void writeCustody(DataOutputStream output, InventoryCustody custody) throws IOException {
        if (custody instanceof InventoryCustody.ContainerSlot slot) { output.writeByte(0); writeString(output, slot.containerId().value()); output.writeByte(slot.slot()); }
        else if (custody instanceof InventoryCustody.Cargo cargo) { output.writeByte(1); writeString(output, cargo.cargoId().value()); }
        else { output.writeByte(2); writeString(output, ((InventoryCustody.Player) custody).playerId().toString()); }
    }
    private static InventoryCustody readCustody(DataInputStream input) throws IOException {
        return switch (input.readUnsignedByte()) {
            case 0 -> new InventoryCustody.ContainerSlot(new SubjectId(readString(input)), input.readUnsignedByte());
            case 1 -> new InventoryCustody.Cargo(new SubjectId(readString(input)));
            case 2 -> new InventoryCustody.Player(UUID.fromString(readString(input)));
            default -> throw new IllegalArgumentException("unknown inventory custody");
        };
    }
    private static void writePosition(DataOutputStream output, BlockPosition position) throws IOException {
        output.writeInt(position.x()); output.writeInt(position.y()); output.writeInt(position.z());
    }
    private static BlockPosition readPosition(DataInputStream input) throws IOException {
        return new BlockPosition(input.readInt(), input.readInt(), input.readInt());
    }
    private static void writeCount(DataOutputStream output, int count) throws IOException {
        if (count > MAX_ENTRIES) throw new IllegalArgumentException("too many Frontier v3 state entries");
        output.writeShort(count);
    }
    private static int readCount(DataInputStream input) throws IOException { return input.readUnsignedShort(); }
    private static void writeString(DataOutputStream output, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 256) throw new IllegalArgumentException("state identifier is too long");
        output.writeShort(bytes.length); output.write(bytes);
    }
    private static String readString(DataInputStream input) throws IOException {
        int length = input.readUnsignedShort();
        if (length > 256) throw new IllegalArgumentException("state identifier is too long");
        byte[] bytes = input.readNBytes(length);
        if (bytes.length != length) throw new IOException("truncated state identifier");
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
