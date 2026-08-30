package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.api.FixedRatio; import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.FixedPosition; import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId; import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus; import io.farfrontier.palemirror.frontier.v3.api.PhysicalPostcondition;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId; import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId; import io.farfrontier.palemirror.frontier.v3.api.WorldId;
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
public final class FrontierWorldStateCodec implements StateCodec<FrontierWorldState> { private static final int MAGIC = 0x4656334D, LEGACY_VERSION = 42, VERSION = 56, MAX_ENTRIES = 65_535;
    private final FrontierBootstrap pinnedBootstrap;

    /** Generic codec for independent snapshots and cross-world test fixtures. */
    public FrontierWorldStateCodec() { this.pinnedBootstrap = null; }

    /**
     * Runtime-local codec that reuses the immutable genesis profile after proving the snapshot belongs to it.
     * This never weakens the serialized world/seed boundary: a foreign header fails before its mutable state is read.
     */
    FrontierWorldStateCodec(FrontierBootstrap pinnedBootstrap) { this.pinnedBootstrap = java.util.Objects.requireNonNull(pinnedBootstrap, "pinnedBootstrap"); }

    @Override public byte[] encode(FrontierWorldState state) {
        try {
            verifyPinnedBootstrap(state.bootstrap());
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                output.writeInt(MAGIC); output.writeByte(VERSION);
                writeString(output, state.bootstrap().worldId().value()); output.writeLong(state.bootstrap().seed());
                writeActors(output, state.actorLocations());
                writeStructures(output, state.structureConditions());
                writeStructureDamage(output, state.structureDamage());
                writePhysicalDeltas(output, state.physicalDeltas());
                writeInfection(output, state.infection());
                writeHiveColony(output, state.hiveColony());
                writeInventory(output, state.inventory());
                writeProductionJobs(output, state.productionJobs());
                writeContracts(output, state.contracts());
                writeOperations(output, state.operations());
                writePhysicalIntents(output, state.physicalIntents());
                PhysicalEffectObservationStateCodec.write(output, state.physicalObservations());
                writeSceneLeases(output, state.sceneLeases());
                AmbientLeaseStateCodec.write(output, state.ambientLeases());
                RouteConstructionStateCodec.write(output, state.routeConstructions());
                RouteTopologyStateCodec.write(output, state.routeTopology());
                StrategicPlanStateCodec.write(output, state.strategicPlans());
                HumanPopulationStateCodec.write(output, state.humanPopulation());
                ResourceSiteStateCodec.write(output, state.resourceSites());
            }
            return bytes.toByteArray();
        } catch (IOException impossible) { throw new IllegalStateException("in-memory Frontier v3 state encoding failed", impossible); }
    }

    @Override public FrontierWorldState decode(byte[] encoded) {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(encoded))) {
            if (input.readInt() != MAGIC) throw new IllegalArgumentException("unknown Frontier v3 state magic");
            int version = input.readUnsignedByte();
            if (version != 41 && version != LEGACY_VERSION && version != 43 && version != 44 && version != 45
                    && version != 46 && version != 47 && version != 48 && version != 49 && version != 50 && version != 51 && version != 52 && version != 53 && version != 54 && version != 55 && version != VERSION) {
                throw new IllegalArgumentException("unknown Frontier v3 state version");
            }
            FrontierBootstrap bootstrap = bootstrapFor(new WorldId(readString(input)), input.readLong());
            Map<SubjectId, ActorLocation> actors = readActors(input); Map<SubjectId, StructureCondition> structures = readStructures(input);
            Map<SubjectId, StructureDamage> structureDamage = readStructureDamage(input);
            Map<BlockPosition, PhysicalDelta> physicalDeltas = readPhysicalDeltas(input);
            Map<InfectionCell, FixedRatio> infection = readInfection(input); HiveColony colony = readHiveColony(input);
            FrontierWorldState state = new FrontierWorldState(bootstrap, actors, structures, infection, readInventory(input), readProductionJobs(input),
                    readContracts(input), readOperations(input, version >= 50, version >= 51, version >= 54, version >= 55),
                    readPhysicalIntents(input), PhysicalEffectObservationStateCodec.read(input), readSceneLeases(input, version), colony, structureDamage, physicalDeltas,
                    AmbientLeaseStateCodec.read(input), RouteConstructionStateCodec.read(input, version >= 45), RouteTopologyStateCodec.read(input, bootstrap),
                    StrategicPlanStateCodec.read(input), HumanPopulationStateCodec.read(input, version >= 47, version >= 48),
                    ResourceSiteStateCodec.read(input));
            if (input.available() != 0) throw new IllegalArgumentException("trailing Frontier v3 state bytes");
            return state;
        } catch (IOException error) { throw new IllegalArgumentException("truncated Frontier v3 state", error); }
    }

    private FrontierBootstrap bootstrapFor(WorldId worldId, long seed) {
        if (pinnedBootstrap == null) return FrontierBootstrapper.create(worldId, seed);
        if (!pinnedBootstrap.worldId().equals(worldId) || pinnedBootstrap.seed() != seed) {
            throw new IllegalArgumentException("Frontier v3 state belongs to a different pinned bootstrap");
        }
        return pinnedBootstrap;
    }

    private void verifyPinnedBootstrap(FrontierBootstrap bootstrap) {
        if (pinnedBootstrap != null && (!pinnedBootstrap.worldId().equals(bootstrap.worldId()) || pinnedBootstrap.seed() != bootstrap.seed())) {
            throw new IllegalArgumentException("cannot encode Frontier v3 state for a different pinned bootstrap");
        }
    }

    private static void writeActors(DataOutputStream output, Map<SubjectId, ActorLocation> values) throws IOException {
        writeCount(output, values.size());
        for (Map.Entry<SubjectId, ActorLocation> entry : values.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            writeString(output, entry.getKey().value()); writePosition(output, entry.getValue().position());
            output.writeByte(entry.getValue().condition().status().ordinal()); output.writeLong(entry.getValue().condition().health().raw());
        }
    }
    private static Map<SubjectId, ActorLocation> readActors(DataInputStream input) throws IOException {
        Map<SubjectId, ActorLocation> values = new LinkedHashMap<>();
        for (int index = 0, count = readCount(input); index < count; index++) {
            SubjectId id = new SubjectId(readString(input));
            BlockPosition position = readPosition(input); int status = input.readUnsignedByte();
            if (status >= ActorLifeStatus.values().length
                    || values.put(id, new ActorLocation(position, new ActorCondition(ActorLifeStatus.values()[status], new FixedScalar(input.readLong())))) != null) {
                throw new IllegalArgumentException("invalid or duplicate actor state id: " + id.value());
            }
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
    private static void writeStructureDamage(DataOutputStream output, Map<SubjectId, StructureDamage> values) throws IOException {
        writeCount(output, values.size());
        for (StructureDamage damage : values.values().stream().sorted(Comparator.comparing(StructureDamage::structureId)).toList()) {
            writeString(output, damage.structureId().value()); writeCount(output, damage.cells().size());
            for (Map.Entry<BlockPosition, StructureDamage.DamageCell> entry : damage.cells().entrySet().stream()
                    .sorted(Comparator.comparingInt((Map.Entry<BlockPosition, StructureDamage.DamageCell> entry) -> entry.getKey().x())
                            .thenComparingInt(entry -> entry.getKey().y()).thenComparingInt(entry -> entry.getKey().z())).toList()) {
                writePosition(output, entry.getKey()); output.writeByte(entry.getValue().semanticPart().ordinal()); writeString(output, entry.getValue().cause());
            }
        }
    }
    private static Map<SubjectId, StructureDamage> readStructureDamage(DataInputStream input) throws IOException {
        Map<SubjectId, StructureDamage> values = new LinkedHashMap<>();
        for (int index = 0, count = readCount(input); index < count; index++) {
            SubjectId structure = new SubjectId(readString(input)); Map<BlockPosition, StructureDamage.DamageCell> cells = new LinkedHashMap<>();
            for (int cell = 0, cellCount = readCount(input); cell < cellCount; cell++) {
                BlockPosition position = readPosition(input); int part = input.readUnsignedByte();
                if (part >= GrayboxSemanticPart.values().length
                        || cells.put(position, new StructureDamage.DamageCell(GrayboxSemanticPart.values()[part], readString(input))) != null) {
                    throw new IllegalArgumentException("invalid or duplicate structure damage cell");
                }
            }
            StructureDamage damage = new StructureDamage(structure, cells);
            if (values.put(structure, damage) != null) throw new IllegalArgumentException("duplicate structure damage identity");
        }
        return values;
    }
    private static void writePhysicalDeltas(DataOutputStream output, Map<BlockPosition, PhysicalDelta> values) throws IOException {
        writeCount(output, values.size());
        for (PhysicalDelta delta : values.values().stream().sorted(Comparator.comparingInt((PhysicalDelta value) -> value.position().x())
                .thenComparingInt(value -> value.position().y()).thenComparingInt(value -> value.position().z())).toList()) {
            writePosition(output, delta.position()); output.writeByte(delta.kind().ordinal()); writeString(output, delta.cause());
            output.writeBoolean(delta.ownerId().isPresent());
            if (delta.ownerId().isPresent()) writeString(output, delta.ownerId().orElseThrow().value());
            output.writeBoolean(delta.semanticPart().isPresent());
            if (delta.semanticPart().isPresent()) output.writeByte(delta.semanticPart().orElseThrow().ordinal());
        }
    }
    private static Map<BlockPosition, PhysicalDelta> readPhysicalDeltas(DataInputStream input) throws IOException {
        Map<BlockPosition, PhysicalDelta> values = new LinkedHashMap<>();
        for (int index = 0, count = readCount(input); index < count; index++) {
            BlockPosition position = readPosition(input); int kind = input.readUnsignedByte(); String cause = readString(input);
            boolean ownerPresent = input.readBoolean(); java.util.Optional<SubjectId> owner = ownerPresent
                    ? java.util.Optional.of(new SubjectId(readString(input))) : java.util.Optional.empty();
            boolean partPresent = input.readBoolean(); java.util.Optional<GrayboxSemanticPart> part = partPresent
                    ? java.util.Optional.of(readSemanticPart(input)) : java.util.Optional.empty();
            if (kind >= PhysicalDeltaKind.values().length || values.put(position,
                    new PhysicalDelta(position, PhysicalDeltaKind.values()[kind], owner, part, cause)) != null) {
                throw new IllegalArgumentException("invalid or duplicate physical delta");
            }
        }
        return values;
    }
    private static GrayboxSemanticPart readSemanticPart(DataInputStream input) throws IOException {
        int ordinal = input.readUnsignedByte();
        if (ordinal >= GrayboxSemanticPart.values().length) throw new IllegalArgumentException("unknown graybox semantic part");
        return GrayboxSemanticPart.values()[ordinal];
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
    private static void writeHiveColony(DataOutputStream output, HiveColony colony) throws IOException {
        writeCount(output, colony.addedOrgans().size());
        for (HiveOrgan organ : colony.addedOrgans().values().stream().sorted(Comparator.comparing(HiveOrgan::id)).toList()) {
            writeString(output, organ.id().value()); writeString(output, organ.hiveId().value()); writeString(output, organ.nestId().value());
            output.writeByte(organ.kind().ordinal()); writePosition(output, organ.anchor()); output.writeBoolean(organ.containerId().isPresent());
            if (organ.containerId().isPresent()) writeString(output, organ.containerId().orElseThrow().value());
        }
        writeCount(output, colony.spawnedBioforms().size());
        for (Bioform bioform : colony.spawnedBioforms().values().stream().sorted(Comparator.comparing(Bioform::id)).toList()) {
            writeString(output, bioform.id().value()); writeString(output, bioform.hiveId().value()); writeString(output, bioform.nestId().value());
            output.writeByte(bioform.role().ordinal()); writePosition(output, bioform.position());
        }
        writeCount(output, colony.growthJobs().size());
        for (HiveGrowthJob job : colony.growthJobs().values().stream().sorted(Comparator.comparing(HiveGrowthJob::id)).toList()) {
            writeString(output, job.id().value()); writeString(output, job.hiveId().value()); writeString(output, job.nestId().value()); writeString(output, job.consumedItemId().value()); writeString(output, job.consumptionIntentId().value());
            HiveOrgan organ = job.organ(); writeString(output, organ.id().value()); output.writeByte(organ.kind().ordinal()); writePosition(output, organ.anchor());
            Bioform bioform = job.bioform(); writeString(output, bioform.id().value()); output.writeByte(bioform.role().ordinal()); writePosition(output, bioform.position());
        }
    }
    private static HiveColony readHiveColony(DataInputStream input) throws IOException {
        Map<SubjectId, HiveOrgan> organs = new LinkedHashMap<>();
        for (int index = 0, count = readCount(input); index < count; index++) {
            SubjectId id = new SubjectId(readString(input)); SubjectId hive = new SubjectId(readString(input)); SubjectId nest = new SubjectId(readString(input));
            int kind = input.readUnsignedByte(); BlockPosition anchor = readPosition(input); boolean hasContainer = input.readBoolean();
            java.util.Optional<SubjectId> container = hasContainer ? java.util.Optional.of(new SubjectId(readString(input))) : java.util.Optional.empty();
            if (kind >= HiveOrganKind.values().length || organs.put(id, new HiveOrgan(id, hive, nest, HiveOrganKind.values()[kind], anchor, container)) != null) {
                throw new IllegalArgumentException("invalid or duplicate added hive organ");
            }
        }
        Map<SubjectId, Bioform> bioforms = new LinkedHashMap<>();
        for (int index = 0, count = readCount(input); index < count; index++) {
            SubjectId id = new SubjectId(readString(input)); SubjectId hive = new SubjectId(readString(input)); SubjectId nest = new SubjectId(readString(input));
            int role = input.readUnsignedByte(); BlockPosition position = readPosition(input);
            if (role >= BioformRole.values().length || bioforms.put(id, new Bioform(id, hive, nest, BioformRole.values()[role], position)) != null) {
                throw new IllegalArgumentException("invalid or duplicate spawned bioform");
            }
        }
        Map<SubjectId, HiveGrowthJob> jobs = new LinkedHashMap<>();
        for (int index = 0, count = readCount(input); index < count; index++) {
            SubjectId id = new SubjectId(readString(input)); SubjectId hive = new SubjectId(readString(input)); SubjectId nest = new SubjectId(readString(input)); SubjectId item = new SubjectId(readString(input));
            io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId consumption = new io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId(readString(input));
            SubjectId organId = new SubjectId(readString(input)); int kind = input.readUnsignedByte(); BlockPosition anchor = readPosition(input);
            SubjectId bioformId = new SubjectId(readString(input)); int role = input.readUnsignedByte(); BlockPosition position = readPosition(input);
            if (kind >= HiveOrganKind.values().length || role >= BioformRole.values().length || jobs.put(id, new HiveGrowthJob(id, hive, nest, item, consumption,
                    new HiveOrgan(organId, hive, nest, HiveOrganKind.values()[kind], anchor, java.util.Optional.empty()),
                    new Bioform(bioformId, hive, nest, BioformRole.values()[role], position))) != null) throw new IllegalArgumentException("invalid or duplicate hive growth job");
        }
        return new HiveColony(organs, bioforms, jobs);
    }
    private static void writeInventory(DataOutputStream output, ExactInventory inventory) throws IOException {
        writeCount(output, inventory.containers().size());
        for (ContainerRecord value : inventory.containers().values().stream().sorted(java.util.Comparator.comparing(ContainerRecord::id)).toList()) {
            writeString(output, value.id().value()); writeString(output, value.ownerId().value()); output.writeByte(value.slotCount());
        }
        writeCount(output, inventory.surfaces().size());
        for (ContainerSurface surface : inventory.surfaces().values().stream().sorted(Comparator.comparing(ContainerSurface::containerId)).toList()) {
            writeString(output, surface.containerId().value()); writePosition(output, surface.position()); output.writeByte(surface.status().ordinal());
        }
        writeCount(output, inventory.items().size());
        for (ExactItemStack value : inventory.items().values().stream().sorted(java.util.Comparator.comparing(ExactItemStack::id)).toList()) {
            writeString(output, value.id().value()); writeString(output, value.economicOwnerId().value()); writeString(output, value.itemKind()); output.writeByte(value.count()); writeCustody(output, value.custody());
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
        writeCount(output, inventory.worldCarrierItems().size());
        for (Map.Entry<UUID, List<SubjectId>> entry : inventory.worldCarrierItems().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            writeString(output, entry.getKey().toString()); writeCount(output, entry.getValue().size());
            for (SubjectId item : entry.getValue()) writeString(output, item.value());
        }
        writeCount(output, inventory.conflicts().size());
        for (InventoryConflict conflict : inventory.conflicts().values().stream().sorted(Comparator.comparing(InventoryConflict::id)).toList()) {
            writeString(output, conflict.id().value()); writeString(output, conflict.subjectId().value()); writeString(output, conflict.containerId().value());
            output.writeByte(conflict.slot()); output.writeByte(conflict.kind().ordinal());
        }
    }
    private static ExactInventory readInventory(DataInputStream input) throws IOException {
        Map<SubjectId, ContainerRecord> containers = new LinkedHashMap<>();
        for (int index = 0, count = readCount(input); index < count; index++) {
            SubjectId id = new SubjectId(readString(input));
            if (containers.put(id, new ContainerRecord(id, new SubjectId(readString(input)), input.readUnsignedByte())) != null) throw new IllegalArgumentException("duplicate container id");
        }
        Map<SubjectId, ContainerSurface> surfaces = new LinkedHashMap<>();
        for (int index = 0, count = readCount(input); index < count; index++) {
            SubjectId id = new SubjectId(readString(input)); BlockPosition position = readPosition(input); int status = input.readUnsignedByte();
            if (status >= ContainerSurfaceStatus.values().length || surfaces.put(id, new ContainerSurface(id, position, ContainerSurfaceStatus.values()[status])) != null) throw new IllegalArgumentException("invalid or duplicate container surface");
        }
        Map<SubjectId, ExactItemStack> items = new LinkedHashMap<>();
        for (int index = 0, count = readCount(input); index < count; index++) {
            SubjectId id = new SubjectId(readString(input));
            if (items.put(id, new ExactItemStack(id, new SubjectId(readString(input)), readString(input), input.readUnsignedByte(), readCustody(input))) != null) throw new IllegalArgumentException("duplicate item stack id");
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
        Map<UUID, List<SubjectId>> carriers = new LinkedHashMap<>();
        for (int index = 0, count = readCount(input); index < count; index++) {
            UUID carrier = UUID.fromString(readString(input)); java.util.ArrayList<SubjectId> itemIds = new java.util.ArrayList<>();
            for (int item = 0, itemCount = readCount(input); item < itemCount; item++) itemIds.add(new SubjectId(readString(input)));
            if (carriers.put(carrier, itemIds) != null) throw new IllegalArgumentException("duplicate world carrier custody id");
        }
        Map<SubjectId, InventoryConflict> conflicts = new LinkedHashMap<>();
        for (int index = 0, count = readCount(input); index < count; index++) {
            SubjectId id = new SubjectId(readString(input)); SubjectId item = new SubjectId(readString(input)); SubjectId container = new SubjectId(readString(input));
            int slot = input.readUnsignedByte(); int kind = input.readUnsignedByte();
            if (kind >= InventoryConflictKind.values().length
                    || conflicts.put(id, new InventoryConflict(id, item, container, slot, InventoryConflictKind.values()[kind])) != null) {
                throw new IllegalArgumentException("invalid or duplicate inventory conflict");
            }
        }
        return new ExactInventory(containers, items, cargo, players, carriers, conflicts, surfaces);
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
    } private static void writeContracts(DataOutputStream output, Map<SubjectId, SupplyContract> contracts) throws IOException {
        writeCount(output, contracts.size()); for (SupplyContract contract : contracts.values().stream().sorted(java.util.Comparator.comparing(SupplyContract::id)).toList()) {
            writeString(output, contract.id().value()); writeString(output, contract.settlementId().value()); writeString(output, contract.recipientId().value());
            writeString(output, contract.cargoId().value()); writeString(output, contract.itemKind()); output.writeByte(contract.itemCount()); output.writeByte(contract.status().ordinal());
        }
    } private static Map<SubjectId, SupplyContract> readContracts(DataInputStream input) throws IOException {
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
            output.writeByte(operation.routeIndex()); output.writeByte(operation.stage().wireCode());
            output.writeBoolean(operation.activeAssembly().isPresent());
            if (operation.activeAssembly().isPresent()) writeAssembly(output, operation.activeAssembly().orElseThrow());
            output.writeBoolean(operation.activeTravel().isPresent());
            if (operation.activeTravel().isPresent()) writeTravel(output, operation.activeTravel().orElseThrow());
        }
    }
    private static Map<SubjectId, RouteOperation> readOperations(DataInputStream input, boolean hasTravel, boolean hasAssembly, boolean hasAssemblyDeferral,
                                                                   boolean hasDeferralObstruction) throws IOException {
        Map<SubjectId, RouteOperation> operations = new LinkedHashMap<>();
        for (int index = 0, count = readCount(input); index < count; index++) {
            SubjectId id = new SubjectId(readString(input)); SubjectId settlement = new SubjectId(readString(input)); SubjectId cargo = new SubjectId(readString(input));
            SubjectId destination = new SubjectId(readString(input)); java.util.ArrayList<SubjectId> participants = new java.util.ArrayList<>();
            for (int participant = 0, participantCount = readCount(input); participant < participantCount; participant++) participants.add(new SubjectId(readString(input)));
            java.util.ArrayList<BlockPosition> route = new java.util.ArrayList<>();
            for (int point = 0, pointCount = readCount(input); point < pointCount; point++) route.add(readPosition(input));
            int routeIndex = input.readUnsignedByte(); int stage = input.readUnsignedByte();
            java.util.Optional<OperationAssembly> assembly = hasAssembly && input.readBoolean() ? java.util.Optional.of(readAssembly(input, hasAssemblyDeferral, hasDeferralObstruction)) : java.util.Optional.empty();
            java.util.Optional<OperationTravel> travel = hasTravel && input.readBoolean() ? java.util.Optional.of(readTravel(input)) : java.util.Optional.empty();
            if (operations.put(id, new RouteOperation(id, settlement, cargo, destination, participants, route, routeIndex, OperationStage.fromWireCode(stage), assembly, travel)) != null) {
                throw new IllegalArgumentException("invalid or duplicate route operation");
            }
        }
        return operations;
    }
    private static void writeTravel(DataOutputStream output, OperationTravel travel) throws IOException {
        writeCount(output, travel.corridor().size()); for (BlockPosition point : travel.corridor()) writePosition(output, point);
        writeCount(output, travel.cursor()); writeCount(output, travel.formation().size());
        for (var entry : travel.formation().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) { writeString(output, entry.getKey().value()); writePosition(output, entry.getValue()); }
        writePosition(output, travel.cargoAnchor());
    }
    private static OperationTravel readTravel(DataInputStream input) throws IOException {
        java.util.ArrayList<BlockPosition> corridor = new java.util.ArrayList<>(); for (int index = 0, count = readCount(input); index < count; index++) corridor.add(readPosition(input));
        int cursor = readCount(input); Map<SubjectId, BlockPosition> formation = new LinkedHashMap<>();
        for (int index = 0, count = readCount(input); index < count; index++) formation.put(new SubjectId(readString(input)), readPosition(input));
        return new OperationTravel(corridor, cursor, formation, readPosition(input));
    }
    private static void writeAssembly(DataOutputStream output, OperationAssembly assembly) throws IOException {
        writeString(output, assembly.cargoCarrierId().value()); writeCount(output, assembly.members().size());
        for (var entry : assembly.members().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            writeString(output, entry.getKey().value()); writeCount(output, entry.getValue().corridor().size());
            for (BlockPosition point : entry.getValue().corridor()) writePosition(output, point);
            writeCount(output, entry.getValue().cursor());
        }
        output.writeBoolean(assembly.deferral().isPresent());
        if (assembly.deferral().isPresent()) {
            OperationAssemblyDeferral deferred = assembly.deferral().orElseThrow(); writeString(output, deferred.actorId().value());
            writePosition(output, deferred.target()); writePosition(output, deferred.obstructionFloor()); output.writeByte(deferred.reason().ordinal());
        }
    }
    private static OperationAssembly readAssembly(DataInputStream input, boolean hasDeferral, boolean hasDeferralObstruction) throws IOException {
        SubjectId carrier = new SubjectId(readString(input)); Map<SubjectId, OperationAssembly.Member> members = new LinkedHashMap<>();
        for (int index = 0, count = readCount(input); index < count; index++) {
            SubjectId actor = new SubjectId(readString(input)); List<BlockPosition> corridor = new java.util.ArrayList<>();
            for (int cell = 0, cellCount = readCount(input); cell < cellCount; cell++) corridor.add(readPosition(input));
            if (members.put(actor, new OperationAssembly.Member(corridor, readCount(input))) != null) throw new IllegalArgumentException("duplicate operation assembly member");
        }
        java.util.Optional<OperationAssemblyDeferral> deferral = java.util.Optional.empty();
        if (hasDeferral && input.readBoolean()) {
            SubjectId actor = new SubjectId(readString(input)); BlockPosition target = readPosition(input);
            BlockPosition obstruction = hasDeferralObstruction ? readPosition(input) : target; int reason = input.readUnsignedByte();
            if (reason >= OperationAssemblyDeferral.Reason.values().length) throw new IllegalArgumentException("unknown operation assembly deferral reason");
            deferral = java.util.Optional.of(new OperationAssemblyDeferral(actor, target, obstruction, OperationAssemblyDeferral.Reason.values()[reason]));
        }
        return new OperationAssembly(members, carrier, deferral);
    }
    private static void writeSceneLeases(DataOutputStream output, Map<SceneLeaseId, SceneLease> leases) throws IOException {
        writeCount(output, leases.size());
        for (SceneLease lease : leases.values().stream().sorted(Comparator.comparing(SceneLease::id)).toList()) {
            writeString(output, lease.id().value()); writeString(output, lease.worldId().value()); writeString(output, lease.operationId().value()); writeString(output, lease.cargoId().value());
            output.writeBoolean(lease.engagementId().isPresent()); if (lease.engagementId().isPresent()) writeString(output, lease.engagementId().orElseThrow().value());
            writePosition(output, lease.handoffPosition()); writePosition(output, lease.cargoPosition()); output.writeLong(lease.handoffInstant().ticks()); output.writeLong(lease.revision()); output.writeByte(lease.status().ordinal());
            writeCount(output, lease.members().size());
            for (SceneMember member : lease.members()) { writeString(output, member.actorId().value()); writeString(output, member.entityId().toString()); writePosition(output, lease.memberPosition(member.actorId())); }
            writeCount(output, lease.ambientHandoffActorIds().size());
            for (SubjectId actor : lease.ambientHandoffActorIds().stream().sorted().toList()) writeString(output, actor.value());
            output.writeBoolean(lease.recoveryEvidence().isPresent());
            if (lease.recoveryEvidence().isPresent()) {
                SceneRecoveryEvidence evidence = lease.recoveryEvidence().orElseThrow();
                writeCount(output, evidence.missingActorIds().size());
                for (SubjectId actor : evidence.missingActorIds().stream().sorted().toList()) writeString(output, actor.value());
                output.writeBoolean(evidence.missingCargoCarrier());
            }
        }
    }
    private static Map<SceneLeaseId, SceneLease> readSceneLeases(DataInputStream input, int version) throws IOException {
        Map<SceneLeaseId, SceneLease> leases = new LinkedHashMap<>();
        int count = readCount(input);
        if (version < 53 && count > 0) throw new IllegalArgumentException("legacy scene lease lacks exact member or cargo positions");
        for (int index = 0; index < count; index++) {
            SceneLeaseId id = new SceneLeaseId(readString(input)); WorldId world = new WorldId(readString(input)); SubjectId operation = new SubjectId(readString(input)); SubjectId cargo = new SubjectId(readString(input));
            java.util.Optional<SubjectId> engagement = input.readBoolean() ? java.util.Optional.of(new SubjectId(readString(input))) : java.util.Optional.empty();
            BlockPosition handoff = readPosition(input); BlockPosition cargoPosition = readPosition(input); long instant = input.readLong(); long revision = input.readLong(); int status = input.readUnsignedByte();
            if (status >= SceneLeaseStatus.values().length) throw new IllegalArgumentException("unknown scene lease status");
            java.util.ArrayList<SceneMember> members = new java.util.ArrayList<>(); Map<SubjectId, BlockPosition> memberPositions = new LinkedHashMap<>();
            for (int member = 0, memberCount = readCount(input); member < memberCount; member++) {
                SubjectId actor = new SubjectId(readString(input)); members.add(new SceneMember(actor, UUID.fromString(readString(input))));
                memberPositions.put(actor, readPosition(input));
            }
            java.util.Set<SubjectId> handoffActors = new java.util.LinkedHashSet<>();
            for (int actor = 0, actorCount = readCount(input); actor < actorCount; actor++) handoffActors.add(new SubjectId(readString(input)));
            java.util.Optional<SceneRecoveryEvidence> recovery = java.util.Optional.empty();
            if (version >= VERSION && input.readBoolean()) {
                java.util.Set<SubjectId> missing = new java.util.LinkedHashSet<>();
                for (int actor = 0, actorCount = readCount(input); actor < actorCount; actor++) missing.add(new SubjectId(readString(input)));
                recovery = java.util.Optional.of(new SceneRecoveryEvidence(missing, input.readBoolean()));
            }
            SceneLease lease = new SceneLease(id, world, operation, cargo, handoff, cargoPosition, new io.farfrontier.palemirror.frontier.v3.api.SimInstant(instant), revision,
                    SceneLeaseStatus.values()[status], engagement, members, memberPositions, handoffActors, recovery);
            if (leases.put(id, lease) != null) throw new IllegalArgumentException("duplicate scene lease id");
        }
        return leases;
    }
    private static void writePhysicalIntents(DataOutputStream output, Map<PhysicalIntentId, PhysicalIntent> intents) throws IOException {
        writeCount(output, intents.size());
        for (PhysicalIntent intent : intents.values().stream().sorted(Comparator.comparing(PhysicalIntent::id)).toList()) {
            writeString(output, intent.id().value()); output.writeByte(intent.kind().ordinal()); output.writeByte(intent.status().ordinal());
            writeString(output, intent.causeSubjectId().value()); writeCount(output, intent.subjectIds().size());
            for (SubjectId subject : intent.subjectIds()) writeString(output, subject.value());
            output.writeLong(intent.origin().x().raw()); output.writeLong(intent.origin().y().raw()); output.writeLong(intent.origin().z().raw());
            output.writeByte(intent.radiusBlocks()); output.writeByte(intent.postcondition().ordinal());
            output.writeBoolean(intent.postconditionObservationId().isPresent());
            if (intent.postconditionObservationId().isPresent()) writeString(output, intent.postconditionObservationId().orElseThrow().value());
        }
    }
    private static Map<PhysicalIntentId, PhysicalIntent> readPhysicalIntents(DataInputStream input) throws IOException {
        Map<PhysicalIntentId, PhysicalIntent> intents = new LinkedHashMap<>();
        for (int index = 0, count = readCount(input); index < count; index++) {
            PhysicalIntentId id = new PhysicalIntentId(readString(input)); int kind = input.readUnsignedByte(); int status = input.readUnsignedByte();
            SubjectId cause = new SubjectId(readString(input)); java.util.ArrayList<SubjectId> subjects = new java.util.ArrayList<>();
            for (int subject = 0, subjectCount = readCount(input); subject < subjectCount; subject++) subjects.add(new SubjectId(readString(input)));
            FixedPosition origin = new FixedPosition(new FixedScalar(input.readLong()), new FixedScalar(input.readLong()), new FixedScalar(input.readLong()));
            int radius = input.readUnsignedByte(); int postcondition = input.readUnsignedByte(); boolean observed = input.readBoolean();
            java.util.Optional<io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId> observation = observed
                    ? java.util.Optional.of(new io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId(readString(input))) : java.util.Optional.empty();
            if (kind >= PhysicalIntentKind.values().length || status >= PhysicalIntentStatus.values().length || postcondition >= PhysicalPostcondition.values().length
                    || intents.put(id, new PhysicalIntent(id, PhysicalIntentKind.values()[kind], PhysicalIntentStatus.values()[status], cause, subjects, origin, radius, PhysicalPostcondition.values()[postcondition], observation)) != null) {
                throw new IllegalArgumentException("invalid or duplicate physical intent");
            }
        }
        return intents;
    }
    static void writeCustody(DataOutputStream output, InventoryCustody custody) throws IOException {
        if (custody instanceof InventoryCustody.ContainerSlot slot) { output.writeByte(0); writeString(output, slot.containerId().value()); output.writeByte(slot.slot()); }
        else if (custody instanceof InventoryCustody.Cargo cargo) { output.writeByte(1); writeString(output, cargo.cargoId().value()); }
        else if (custody instanceof InventoryCustody.Player player) { output.writeByte(2); writeString(output, player.playerId().toString()); }
        else { output.writeByte(3); writeString(output, ((InventoryCustody.WorldCarrier) custody).carrierId().toString()); }
    }
    static InventoryCustody readCustody(DataInputStream input) throws IOException {
        return switch (input.readUnsignedByte()) {
            case 0 -> new InventoryCustody.ContainerSlot(new SubjectId(readString(input)), input.readUnsignedByte());
            case 1 -> new InventoryCustody.Cargo(new SubjectId(readString(input)));
            case 2 -> new InventoryCustody.Player(UUID.fromString(readString(input)));
            case 3 -> new InventoryCustody.WorldCarrier(UUID.fromString(readString(input)));
            default -> throw new IllegalArgumentException("unknown inventory custody");
        };
    }
    static void writePosition(DataOutputStream output, BlockPosition position) throws IOException {
        output.writeInt(position.x()); output.writeInt(position.y()); output.writeInt(position.z());
    }
    static BlockPosition readPosition(DataInputStream input) throws IOException { return new BlockPosition(input.readInt(), input.readInt(), input.readInt()); }
    static void writeCount(DataOutputStream output, int count) throws IOException {
        if (count > MAX_ENTRIES) throw new IllegalArgumentException("too many Frontier v3 state entries");
        output.writeShort(count);
    }
    static int readCount(DataInputStream input) throws IOException { return input.readUnsignedShort(); }
    static void writeString(DataOutputStream output, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8); if (bytes.length > 256) throw new IllegalArgumentException("state identifier is too long");
        output.writeShort(bytes.length); output.write(bytes);
    }
    static String readString(DataInputStream input) throws IOException {
        int length = input.readUnsignedShort(); if (length > 256) throw new IllegalArgumentException("state identifier is too long");
        byte[] bytes = input.readNBytes(length); if (bytes.length != length) throw new IOException("truncated state identifier");
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
