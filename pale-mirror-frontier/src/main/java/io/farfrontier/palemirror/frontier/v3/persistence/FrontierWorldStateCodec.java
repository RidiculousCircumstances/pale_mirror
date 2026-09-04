package io.farfrontier.palemirror.frontier.v3.persistence;
import io.farfrontier.palemirror.frontier.v3.model.*; import io.farfrontier.palemirror.frontier.v3.api.*; import io.farfrontier.palemirror.frontier.v3.kernel.StateCodec; import java.io.*; import java.nio.charset.StandardCharsets; import java.util.*;
/** Versioned exact state codec. Snapshot checksumming is owned by the persistence envelope. */
public final class FrontierWorldStateCodec implements StateCodec<FrontierWorldState> { private static final int MAGIC = 0x4656334D; static final int VERSION = 125; private static final int MAX_ENTRIES = 65_535;
    private final FrontierBootstrap pinnedBootstrap;
    /** Generic codec for independent snapshots and cross-world test fixtures. */
    public FrontierWorldStateCodec() { this.pinnedBootstrap = null; }
    /**
     * Runtime-local codec that reuses the immutable genesis profile after proving the snapshot belongs to it.
     * This never weakens the serialized world/seed boundary: a foreign header fails before its mutable state is read.
    */
    public FrontierWorldStateCodec(FrontierBootstrap pinnedBootstrap) { this.pinnedBootstrap = java.util.Objects.requireNonNull(pinnedBootstrap, "pinnedBootstrap"); }
    @Override public byte[] encode(FrontierWorldState state) {
        try {
            verifyPinnedBootstrap(state.bootstrap());
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                output.writeInt(MAGIC); output.writeByte(VERSION);
                writeString(output, state.bootstrap().worldId().value()); output.writeLong(state.bootstrap().seed()); writeRuleset(output, state.bootstrap().ruleset());
                TerrainSurfacePlanCodec.write(output, state.bootstrap().terrain());
                writeActors(output, state.actorLocations());
                writeStructures(output, state.structureConditions());
                writeStructureDamage(output, state.structureDamage());
                writePhysicalDeltas(output, state.physicalDeltas());
                writeInfection(output, state.infection());
                writeHiveColony(output, state.hiveColony());
                writeEconomicLedger(output, state.inventory().economics());
                writeCompanyRegistry(output, state.companies());
                writeInventory(output, state.inventory());
                writeProductionJobs(output, state.productionJobs());
                SettlementServiceWorkStateCodec.write(output, state.serviceWorks());
                writeContracts(output, state.contracts());
                writeOperations(output, state.operations());
                writeLogisticsHistory(output, state.logisticsHistory());
                PhysicalIntentStateCodec.write(output, state.physicalIntents());
                PhysicalEffectObservationStateCodec.write(output, state.physicalObservations());
                writeSceneLeases(output, state.sceneLeases());
                AmbientLeaseStateCodec.write(output, state.ambientLeases());
                RouteConstructionStateCodec.write(output, state.routeConstructions());
                RouteMaintenanceStateCodec.write(output, state.routeMaintenances());
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
            if (version != VERSION) throw new IllegalArgumentException("Frontier v3 state requires a fresh current-schema world");
            WorldId worldId = new WorldId(readString(input)); long seed = input.readLong();
            FrontierRuleset ruleset = readRuleset(input); TerrainSurfacePlan terrain = TerrainSurfacePlanCodec.read(input);
            FrontierBootstrap bootstrap = bootstrapFor(worldId, seed, ruleset, terrain);
            Map<SubjectId, ActorLocation> actors = readActors(input); Map<SubjectId, StructureCondition> structures = readStructures(input);
            Map<SubjectId, StructureDamage> structureDamage = readStructureDamage(input);
            Map<BlockPosition, PhysicalDelta> physicalDeltas = readPhysicalDeltas(input);
            Map<InfectionCell, FixedRatio> infection = readInfection(input); HiveColony colony = readHiveColony(input, true, true, true);
            EconomicLedger economics = readEconomicLedger(input, true);
            CompanyRegistry companies = readCompanyRegistry(input, true, true, true);
            ExactInventory inventory = readInventory(input, economics); Map<SubjectId, ProductionJob> jobs = readProductionJobs(input, true);
            Map<SubjectId, SettlementServiceWork> serviceWorks = SettlementServiceWorkStateCodec.read(input);
            Map<SubjectId, SupplyContract> contracts = readContracts(input); Map<SubjectId, RouteOperation> operations = readOperations(input, true, true, true, true, true, true, true);
            LogisticsHistory history = readLogisticsHistory(input);
            Map<PhysicalIntentId, PhysicalIntent> intents = PhysicalIntentStateCodec.read(input, true);
            Map<PhysicalObservationId, PhysicalEffectObservation> observations = PhysicalEffectObservationStateCodec.read(input);
            Map<SceneLeaseId, SceneLease> scenes = readSceneLeases(input); Map<SubjectId, AmbientActorLease> ambient = AmbientLeaseStateCodec.read(input);
            Map<SubjectId, RouteConstruction> constructions = RouteConstructionStateCodec.read(input, true, true, true, true);
            Map<SubjectId, RouteMaintenance> maintenances = RouteMaintenanceStateCodec.read(input);
            RouteTopology topology = RouteTopologyStateCodec.read(input, bootstrap);
            constructions = RouteConstructionStateSupport.hydrateWorkCells(bootstrap, topology, constructions);
            StrategicPlanState plans = StrategicPlanStateCodec.read(input, false, true, true, true, true, true, true, true, VERSION);
            HumanPopulation population = HumanPopulationStateCodec.read(input, true, true, true, true, true, true, true);
            ResourceSiteState sites = ResourceSiteStateCodec.read(input);
            FrontierWorldState state = new FrontierWorldState(bootstrap, actors, structures, infection, inventory, jobs, serviceWorks, contracts, operations, history,
                    intents, observations, scenes, colony, structureDamage, physicalDeltas, ambient, constructions, maintenances, topology, plans, population, companies, sites);
            if (input.available() != 0) throw new IllegalArgumentException("trailing Frontier v3 state bytes");
            return state;
        } catch (IOException error) { throw new IllegalArgumentException("truncated Frontier v3 state", error); }
    }
    private FrontierBootstrap bootstrapFor(WorldId worldId, long seed, FrontierRuleset ruleset, TerrainSurfacePlan terrain) {
        if (pinnedBootstrap == null) {
            return FrontierBootstrapCache.resolve(worldId, seed, ruleset, terrain);
        }
        if (!pinnedBootstrap.worldId().equals(worldId) || pinnedBootstrap.seed() != seed || !pinnedBootstrap.ruleset().equals(ruleset)
                || !pinnedBootstrap.terrain().equals(terrain)) {
            throw new IllegalArgumentException("Frontier v3 state belongs to a different pinned bootstrap");
        }
        return pinnedBootstrap;
    }
    private void verifyPinnedBootstrap(FrontierBootstrap bootstrap) {
        if (pinnedBootstrap != null && (!pinnedBootstrap.worldId().equals(bootstrap.worldId()) || pinnedBootstrap.seed() != bootstrap.seed()
                || !pinnedBootstrap.ruleset().equals(bootstrap.ruleset()) || !pinnedBootstrap.terrain().equals(bootstrap.terrain()))) {
            throw new IllegalArgumentException("cannot encode Frontier v3 state for a different pinned bootstrap");
        }
    }
    private static void writeRuleset(DataOutputStream output, FrontierRuleset ruleset) throws IOException {
        writeString(output, ruleset.id()); output.writeInt(ruleset.schemaVersion()); writeString(output, ruleset.contentSha256());
    }
    private static FrontierRuleset readRuleset(DataInputStream input) throws IOException {
        return FrontierRulesets.require(readString(input), input.readInt(), readString(input));
    }
    private static void writeActors(DataOutputStream output, Map<SubjectId, ActorLocation> values) throws IOException {
        writeCount(output, values.size());
        for (Map.Entry<SubjectId, ActorLocation> entry : values.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            writeString(output, entry.getKey().value()); writeBody(output, entry.getValue().body());
            output.writeByte(entry.getValue().condition().status().wireTag()); output.writeLong(entry.getValue().condition().health().raw());
        }
    }
    private static Map<SubjectId, ActorLocation> readActors(DataInputStream input) throws IOException {
        Map<SubjectId, ActorLocation> values = new LinkedHashMap<>();
        for (int index = 0, count = readCount(input); index < count; index++) {
            SubjectId id = new SubjectId(readString(input));
            BodyPosition body = readBody(input); int status = input.readUnsignedByte();
            if (status >= ActorLifeStatus.values().length
                    || values.put(id, new ActorLocation(body, new ActorCondition(FrontierWireTags.require(ActorLifeStatus.class, status), new FixedScalar(input.readLong())))) != null) {
                throw new IllegalArgumentException("invalid or duplicate actor state id: " + id.value());
            }
        }
        return values;
    }
    private static void writeBody(DataOutputStream output, BodyPosition body) throws IOException { output.writeInt(body.x()); output.writeInt(body.y()); output.writeInt(body.z()); }
    private static BodyPosition readBody(DataInputStream input) throws IOException { return new BodyPosition(input.readInt(), input.readInt(), input.readInt()); }
    private static void writeStructures(DataOutputStream output, Map<SubjectId, StructureCondition> values) throws IOException {
        writeCount(output, values.size());
        for (Map.Entry<SubjectId, StructureCondition> entry : values.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            writeString(output, entry.getKey().value()); output.writeByte(entry.getValue().wireTag());
        }
    }
    private static Map<SubjectId, StructureCondition> readStructures(DataInputStream input) throws IOException {
        Map<SubjectId, StructureCondition> values = new LinkedHashMap<>();
        for (int index = 0, count = readCount(input); index < count; index++) {
            SubjectId id = new SubjectId(readString(input));
            int ordinal = input.readUnsignedByte();
            if (ordinal >= StructureCondition.values().length || values.put(id, FrontierWireTags.require(StructureCondition.class, ordinal)) != null) throw new IllegalArgumentException("invalid or duplicate structure state");
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
                writePosition(output, entry.getKey()); output.writeByte(entry.getValue().semanticPart().wireTag()); writeString(output, entry.getValue().cause());
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
                        || cells.put(position, new StructureDamage.DamageCell(FrontierWireTags.require(GrayboxSemanticPart.class, part), readString(input))) != null) {
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
            writePosition(output, delta.position()); output.writeByte(delta.kind().wireTag()); writeString(output, delta.cause());
            output.writeBoolean(delta.ownerId().isPresent());
            if (delta.ownerId().isPresent()) writeString(output, delta.ownerId().orElseThrow().value());
            output.writeBoolean(delta.semanticPart().isPresent());
            if (delta.semanticPart().isPresent()) output.writeByte(delta.semanticPart().orElseThrow().wireTag());
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
                    new PhysicalDelta(position, FrontierWireTags.require(PhysicalDeltaKind.class, kind), owner, part, cause)) != null) {
                throw new IllegalArgumentException("invalid or duplicate physical delta");
            }
        }
        return values;
    }
    private static GrayboxSemanticPart readSemanticPart(DataInputStream input) throws IOException {
        int ordinal = input.readUnsignedByte();
        if (ordinal >= GrayboxSemanticPart.values().length) throw new IllegalArgumentException("unknown graybox semantic part");
        return FrontierWireTags.require(GrayboxSemanticPart.class, ordinal);
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
            output.writeByte(organ.kind().wireTag()); writePosition(output, organ.anchor()); output.writeBoolean(organ.containerId().isPresent());
            if (organ.containerId().isPresent()) writeString(output, organ.containerId().orElseThrow().value());
        }
        writeCount(output, colony.spawnedBioforms().size());
        for (Bioform bioform : colony.spawnedBioforms().values().stream().sorted(Comparator.comparing(Bioform::id)).toList()) {
            writeString(output, bioform.id().value()); writeString(output, bioform.hiveId().value()); writeString(output, bioform.nestId().value());
            BioformProfileStateCodec.write(output, bioform);
        }
        writeCount(output, colony.growthJobs().size());
        for (HiveGrowthJob job : colony.growthJobs().values().stream().sorted(Comparator.comparing(HiveGrowthJob::id)).toList()) {
            writeString(output, job.id().value()); writeString(output, job.hiveId().value()); writeString(output, job.nestId().value()); writeString(output, job.consumedItemId().value()); writeString(output, job.consumptionIntentId().value());
            HiveOrgan organ = job.organ(); writeString(output, organ.id().value()); output.writeByte(organ.kind().wireTag()); writePosition(output, organ.anchor());
            Bioform bioform = job.bioform(); writeString(output, bioform.id().value()); BioformProfileStateCodec.write(output, bioform);
        }
        writeCount(output, colony.nutrientTransfers().size());
        for (HiveNutrientTransfer transfer : colony.nutrientTransfers().values().stream().sorted(Comparator.comparing(HiveNutrientTransfer::id)).toList()) {
            writeString(output, transfer.id().value()); writeString(output, transfer.hiveId().value()); writeString(output, transfer.requesterTaskId().value());
            writeString(output, transfer.sourceStoreId().value()); output.writeByte(transfer.sourceSlot().slot()); writeString(output, transfer.targetStoreId().value()); output.writeByte(transfer.targetSlot().slot());
            writeString(output, transfer.cargoId().value()); writeString(output, transfer.itemId().value()); writeCount(output, transfer.corridor().size());
            for (BlockPosition node : transfer.corridor()) writePosition(output, node);
            output.writeShort(transfer.cursor()); output.writeByte(transfer.phase().wireTag()); output.writeBoolean(transfer.blockReason().isPresent());
            if (transfer.blockReason().isPresent()) output.writeByte(transfer.blockReason().orElseThrow().wireTag());
            output.writeBoolean(transfer.endpointIntentId().isPresent()); if (transfer.endpointIntentId().isPresent()) writeString(output, transfer.endpointIntentId().orElseThrow().value());
        }
        writeCount(output, colony.nutrientReceipts().size());
        for (HiveNutrientReceipt receipt : colony.nutrientReceipts().values().stream().sorted(Comparator.comparing(HiveNutrientReceipt::transferId)).toList()) {
            writeString(output, receipt.transferId().value()); writeString(output, receipt.hiveId().value()); writeString(output, receipt.cargoId().value()); writeString(output, receipt.itemId().value());
            writeString(output, receipt.sourceSlot().containerId().value()); output.writeByte(receipt.sourceSlot().slot()); writeString(output, receipt.targetSlot().containerId().value()); output.writeByte(receipt.targetSlot().slot());
            output.writeByte(receipt.status().wireTag()); output.writeBoolean(receipt.consumedByJobId().isPresent()); if (receipt.consumedByJobId().isPresent()) writeString(output, receipt.consumedByJobId().orElseThrow().value());
        }
        BioformLifecycleStateCodec.write(output, colony.bioformLifecycles());
        HiveMobilizationStateCodec.write(output, colony.mobilizations());
    }
    private static HiveColony readHiveColony(DataInputStream input, boolean hasNutrientTransfers, boolean hasReceiptConsumption, boolean hasEndpointIntent) throws IOException {
        Map<SubjectId, HiveOrgan> organs = new LinkedHashMap<>();
        for (int index = 0, count = readCount(input); index < count; index++) {
            SubjectId id = new SubjectId(readString(input)); SubjectId hive = new SubjectId(readString(input)); SubjectId nest = new SubjectId(readString(input));
            int kind = input.readUnsignedByte(); BlockPosition anchor = readPosition(input); boolean hasContainer = input.readBoolean();
            java.util.Optional<SubjectId> container = hasContainer ? java.util.Optional.of(new SubjectId(readString(input))) : java.util.Optional.empty();
            if (organs.put(id, new HiveOrgan(id, hive, nest, FrontierWireTags.require(HiveOrganKind.class, kind), anchor, container)) != null) {
                throw new IllegalArgumentException("invalid or duplicate added hive organ");
            }
        }
        Map<SubjectId, Bioform> bioforms = new LinkedHashMap<>();
        for (int index = 0, count = readCount(input); index < count; index++) {
            SubjectId id = new SubjectId(readString(input)); SubjectId hive = new SubjectId(readString(input)); SubjectId nest = new SubjectId(readString(input));
            if (bioforms.put(id, BioformProfileStateCodec.read(input, id, hive, nest)) != null) {
                throw new IllegalArgumentException("invalid or duplicate spawned bioform");
            }
        }
        Map<SubjectId, HiveGrowthJob> jobs = new LinkedHashMap<>();
        for (int index = 0, count = readCount(input); index < count; index++) {
            SubjectId id = new SubjectId(readString(input)); SubjectId hive = new SubjectId(readString(input)); SubjectId nest = new SubjectId(readString(input)); SubjectId item = new SubjectId(readString(input));
            io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId consumption = new io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId(readString(input));
            SubjectId organId = new SubjectId(readString(input)); int kind = input.readUnsignedByte(); BlockPosition anchor = readPosition(input);
            SubjectId bioformId = new SubjectId(readString(input)); Bioform bioform = BioformProfileStateCodec.read(input, bioformId, hive, nest);
            if (jobs.put(id, new HiveGrowthJob(id, hive, nest, item, consumption,
                    new HiveOrgan(organId, hive, nest, FrontierWireTags.require(HiveOrganKind.class, kind), anchor, java.util.Optional.empty()),
                    bioform)) != null) throw new IllegalArgumentException("invalid or duplicate hive growth job");
        }
        if (!hasNutrientTransfers) return new HiveColony(organs, bioforms, jobs);
        Map<SubjectId, HiveNutrientTransfer> transfers = new LinkedHashMap<>();
        for (int index = 0, count = readCount(input); index < count; index++) {
            SubjectId id = new SubjectId(readString(input)); SubjectId hive = new SubjectId(readString(input)); SubjectId task = new SubjectId(readString(input));
            SubjectId source = new SubjectId(readString(input)); int sourceSlot = input.readUnsignedByte(); SubjectId target = new SubjectId(readString(input)); int targetSlot = input.readUnsignedByte();
            SubjectId cargo = new SubjectId(readString(input)); SubjectId item = new SubjectId(readString(input)); java.util.ArrayList<BlockPosition> corridor = new java.util.ArrayList<>();
            for (int node = 0, nodes = readCount(input); node < nodes; node++) corridor.add(readPosition(input));
            int cursor = input.readUnsignedShort(), phase = input.readUnsignedByte(); boolean blocked = input.readBoolean(); int reason = blocked ? input.readUnsignedByte() : -1;
            java.util.Optional<io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId> endpoint = hasEndpointIntent && input.readBoolean()
                    ? java.util.Optional.of(new io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId(readString(input))) : java.util.Optional.empty();
            if (phase >= HiveNutrientTransferPhase.values().length || blocked != (phase == HiveNutrientTransferPhase.BLOCKED.wireTag())
                    || blocked && reason >= HiveNutrientTransferBlockReason.values().length || transfers.put(id, new HiveNutrientTransfer(id, hive, task,
                    source, new InventoryCustody.ContainerSlot(source, sourceSlot), target, new InventoryCustody.ContainerSlot(target, targetSlot), cargo, item, corridor, cursor,
                    FrontierWireTags.require(HiveNutrientTransferPhase.class, phase), endpoint, blocked ? java.util.Optional.of(FrontierWireTags.require(HiveNutrientTransferBlockReason.class, reason)) : java.util.Optional.empty())) != null) {
                throw new IllegalArgumentException("invalid or duplicate hive nutrient transfer");
            }
        }
        Map<SubjectId, HiveNutrientReceipt> receipts = new LinkedHashMap<>();
        for (int index = 0, count = readCount(input); index < count; index++) {
            SubjectId transfer = new SubjectId(readString(input)); SubjectId hive = new SubjectId(readString(input)); SubjectId cargo = new SubjectId(readString(input)); SubjectId item = new SubjectId(readString(input));
            SubjectId source = new SubjectId(readString(input)); int sourceSlot = input.readUnsignedByte(); SubjectId target = new SubjectId(readString(input)); int targetSlot = input.readUnsignedByte();
            HiveNutrientReceiptStatus status = HiveNutrientReceiptStatus.STORED; java.util.Optional<SubjectId> consumedBy = java.util.Optional.empty();
            if (hasReceiptConsumption) { int code = input.readUnsignedByte(); boolean present = input.readBoolean(); if (code >= HiveNutrientReceiptStatus.values().length) throw new IllegalArgumentException("invalid hive nutrient receipt status");
                status = FrontierWireTags.require(HiveNutrientReceiptStatus.class, code); consumedBy = present ? java.util.Optional.of(new SubjectId(readString(input))) : java.util.Optional.empty(); }
            if (receipts.put(transfer, new HiveNutrientReceipt(transfer, hive, cargo, item, new InventoryCustody.ContainerSlot(source, sourceSlot), new InventoryCustody.ContainerSlot(target, targetSlot), status, consumedBy)) != null) {
                throw new IllegalArgumentException("duplicate hive nutrient receipt");
            }
        }
        Map<SubjectId, BioformLifecycle> lifecycles = BioformLifecycleStateCodec.read(input);
        Map<SubjectId, HiveMobilization> mobilizations = HiveMobilizationStateCodec.read(input);
        return new HiveColony(organs, bioforms, jobs, transfers, receipts, lifecycles, mobilizations);
    }
    private static void writeEconomicLedger(DataOutputStream output, EconomicLedger ledger) throws IOException {
        writeCount(output, ledger.accounts().size());
        for (EconomicAccount account : ledger.accounts().values().stream().sorted(Comparator.comparing(EconomicAccount::ownerId)).toList()) {
            writeString(output, account.ownerId().value()); output.writeByte(account.ownerKind().wireTag()); output.writeByte(account.status().wireTag());
            output.writeLong(account.balance().raw()); output.writeLong(account.creditLimit().raw());
        }
        writeCount(output, ledger.reservations().size());
        for (FinancialReservation reservation : ledger.reservations().values().stream().sorted(Comparator.comparing(FinancialReservation::id)).toList()) {
            writeString(output, reservation.id().value()); writeString(output, reservation.payerId().value()); writeString(output, reservation.payeeId().value());
            writeString(output, reservation.reasonId().value()); output.writeLong(reservation.amount().raw());
        }
    }
    private static EconomicLedger readEconomicLedger(DataInputStream input, boolean reservationsPresent) throws IOException {
        Map<SubjectId, EconomicAccount> accounts = new LinkedHashMap<>();
        for (int index = 0, count = readCount(input); index < count; index++) {
            SubjectId owner = new SubjectId(readString(input)); int kind = input.readUnsignedByte(); int status = input.readUnsignedByte();
            if (kind >= EconomicOwnerKind.values().length || status >= EconomicAccountStatus.values().length
                    || accounts.put(owner, new EconomicAccount(owner, FrontierWireTags.require(EconomicOwnerKind.class, kind), FrontierWireTags.require(EconomicAccountStatus.class, status),
                    new FixedScalar(input.readLong()), new FixedScalar(input.readLong()))) != null) {
                throw new IllegalArgumentException("invalid or duplicate economic account");
            }
        }
        Map<SubjectId, FinancialReservation> reservations = new LinkedHashMap<>();
        if (reservationsPresent) {
            for (int index = 0, count = readCount(input); index < count; index++) {
                SubjectId id = new SubjectId(readString(input)); FinancialReservation reservation = new FinancialReservation(id,
                        new SubjectId(readString(input)), new SubjectId(readString(input)), new SubjectId(readString(input)), new FixedScalar(input.readLong()));
                if (reservations.put(id, reservation) != null) throw new IllegalArgumentException("duplicate financial reservation");
            }
        }
        return new EconomicLedger(accounts, reservations);
    }
    private static void writeCompanyRegistry(DataOutputStream output, CompanyRegistry registry) throws IOException {
        writeCount(output, registry.companies().size());
        for (Company company : registry.companies().values().stream().sorted(Comparator.comparing(Company::id)).toList()) {
            writeString(output, company.id().value()); writeString(output, company.settlementId().value()); writeString(output, company.founderId().value());
            output.writeByte(company.purpose().wireTag()); output.writeByte(company.status().wireTag()); output.writeLong(company.registeredAtTick());
        }
        writeCount(output, registry.employmentContracts().size());
        for (EmploymentContract contract : registry.employmentContracts().values().stream().sorted(Comparator.comparing(EmploymentContract::id)).toList()) {
            writeString(output, contract.id().value()); writeString(output, contract.companyId().value()); writeString(output, contract.residentId().value());
            output.writeLong(contract.invoicePerCompletedJob().raw()); output.writeLong(contract.wagePerCompletedJob().raw()); output.writeByte(contract.status().wireTag());
            output.writeLong(contract.openedAtTick()); output.writeLong(contract.completedJobs()); output.writeLong(contract.totalWagesPaid().raw());
        }
        writeMarketOrderBook(output, registry.market());
    }
    private static CompanyRegistry readCompanyRegistry(DataInputStream input, boolean hasEmployment, boolean hasMarket, boolean hasMarketOrderJob) throws IOException {
        Map<SubjectId, Company> companies = new LinkedHashMap<>();
        for (int index = 0, count = readCount(input); index < count; index++) {
            SubjectId id = new SubjectId(readString(input)); SubjectId settlement = new SubjectId(readString(input)); SubjectId founder = new SubjectId(readString(input));
            int purpose = input.readUnsignedByte(); int status = input.readUnsignedByte(); long registeredAt = input.readLong();
            if (purpose >= CompanyPurpose.values().length || status >= CompanyStatus.values().length
                    || companies.put(id, new Company(id, settlement, founder, FrontierWireTags.require(CompanyPurpose.class, purpose), FrontierWireTags.require(CompanyStatus.class, status), registeredAt)) != null) {
                throw new IllegalArgumentException("invalid or duplicate company registry entry");
            }
        }
        Map<SubjectId, EmploymentContract> contracts = new LinkedHashMap<>();
        if (hasEmployment) for (int index = 0, count = readCount(input); index < count; index++) {
            SubjectId id = new SubjectId(readString(input)); SubjectId company = new SubjectId(readString(input)); SubjectId resident = new SubjectId(readString(input));
            long invoice = input.readLong(); long wage = input.readLong(); int status = input.readUnsignedByte(); long openedAt = input.readLong();
            long completed = input.readLong(); long totalWages = input.readLong();
            if (status >= EmploymentContractStatus.values().length || contracts.put(id, new EmploymentContract(id, company, resident,
                    new FixedScalar(invoice), new FixedScalar(wage), FrontierWireTags.require(EmploymentContractStatus.class, status), openedAt, completed, new FixedScalar(totalWages))) != null) {
                throw new IllegalArgumentException("invalid or duplicate employment contract registry entry");
            }
        }
        return new CompanyRegistry(companies, contracts, hasMarket ? readMarketOrderBook(input, hasMarketOrderJob) : MarketOrderBook.empty());
    }
    private static void writeMarketOrderBook(DataOutputStream output, MarketOrderBook market) throws IOException {
        writeCount(output, market.demands().size());
        for (MarketDemand demand : market.demands().values().stream().sorted(Comparator.comparing(MarketDemand::id)).toList()) {
            writeString(output, demand.id().value()); writeString(output, demand.buyerId().value()); writeString(output, demand.reasonId().value());
            writeString(output, demand.itemKind()); output.writeInt(demand.itemCount()); output.writeLong(demand.maximumTotalPrice().raw());
            output.writeLong(demand.openedAtTick()); output.writeLong(demand.expiresAtTick()); output.writeByte(demand.status().wireTag());
        }
        writeCount(output, market.quotes().size());
        for (CompanyQuote quote : market.quotes().values().stream().sorted(Comparator.comparing(CompanyQuote::id)).toList()) {
            writeString(output, quote.id().value()); writeString(output, quote.demandId().value()); writeString(output, quote.sellerId().value());
            output.writeInt(quote.itemCount()); output.writeLong(quote.totalPrice().raw()); output.writeLong(quote.quotedAtTick()); output.writeLong(quote.expiresAtTick());
        }
        writeCount(output, market.workOrders().size());
        for (MarketWorkOrder order : market.workOrders().values().stream().sorted(Comparator.comparing(MarketWorkOrder::id)).toList()) {
            writeString(output, order.id().value()); writeString(output, order.demandId().value()); writeString(output, order.quoteId().value());
            writeString(output, order.sellerId().value()); writeString(output, order.taskId().value()); writeString(output, order.jobId().value()); writeString(output, order.reservationId().value());
            output.writeLong(order.acceptedTotalPrice().raw()); output.writeByte(order.status().wireTag());
        }
    }
    private static MarketOrderBook readMarketOrderBook(DataInputStream input, boolean hasMarketOrderJob) throws IOException {
        Map<SubjectId, MarketDemand> demands = new LinkedHashMap<>();
        int orderCount = readCount(input);
        if (!hasMarketOrderJob && orderCount != 0) throw new IllegalArgumentException("v64 market work order requires explicit migration");
        for (int index = 0; index < orderCount; index++) {
            SubjectId id = new SubjectId(readString(input)); SubjectId buyer = new SubjectId(readString(input)); SubjectId reason = new SubjectId(readString(input));
            String itemKind = readString(input); int itemCount = input.readInt(); FixedScalar maximum = new FixedScalar(input.readLong());
            long openedAt = input.readLong(), expiresAt = input.readLong(); int status = input.readUnsignedByte();
            if (status >= MarketDemandStatus.values().length || demands.put(id, new MarketDemand(id, buyer, reason, itemKind, itemCount, maximum,
                    openedAt, expiresAt, FrontierWireTags.require(MarketDemandStatus.class, status))) != null) throw new IllegalArgumentException("invalid or duplicate market demand");
        }
        Map<SubjectId, CompanyQuote> quotes = new LinkedHashMap<>();
        for (int index = 0, count = readCount(input); index < count; index++) {
            SubjectId id = new SubjectId(readString(input)); SubjectId demand = new SubjectId(readString(input)); SubjectId seller = new SubjectId(readString(input));
            int itemCount = input.readInt(); FixedScalar total = new FixedScalar(input.readLong()); long quotedAt = input.readLong(), expiresAt = input.readLong();
            if (quotes.put(id, new CompanyQuote(id, demand, seller, itemCount, total, quotedAt, expiresAt)) != null) throw new IllegalArgumentException("duplicate market quote");
        }
        Map<SubjectId, MarketWorkOrder> orders = new LinkedHashMap<>();
        for (int index = 0, count = readCount(input); index < count; index++) {
            SubjectId id = new SubjectId(readString(input)); SubjectId demand = new SubjectId(readString(input)); SubjectId quote = new SubjectId(readString(input));
            SubjectId seller = new SubjectId(readString(input)); SubjectId task = new SubjectId(readString(input)); SubjectId job = new SubjectId(readString(input)); SubjectId reservation = new SubjectId(readString(input));
            FixedScalar total = new FixedScalar(input.readLong()); int status = input.readUnsignedByte();
            if (status >= MarketWorkOrderStatus.values().length || orders.put(id, new MarketWorkOrder(id, demand, quote, seller, task, job, reservation,
                    total, FrontierWireTags.require(MarketWorkOrderStatus.class, status))) != null) throw new IllegalArgumentException("invalid or duplicate market work order");
        }
        return new MarketOrderBook(demands, quotes, orders);
    }
    private static void writeInventory(DataOutputStream output, ExactInventory inventory) throws IOException {
        writeCount(output, inventory.containers().size());
        for (ContainerRecord value : inventory.containers().values().stream().sorted(java.util.Comparator.comparing(ContainerRecord::id)).toList()) {
            writeString(output, value.id().value()); writeString(output, value.ownerId().value()); output.writeByte(value.slotCount());
        }
        writeCount(output, inventory.surfaces().size());
        for (ContainerSurface surface : inventory.surfaces().values().stream().sorted(Comparator.comparing(ContainerSurface::containerId)).toList()) {
            writeString(output, surface.containerId().value()); writePosition(output, surface.position()); output.writeByte(surface.status().wireTag());
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
            output.writeByte(conflict.slot()); output.writeByte(conflict.kind().wireTag());
        }
    }
    private static ExactInventory readInventory(DataInputStream input, EconomicLedger economics) throws IOException {
        Map<SubjectId, ContainerRecord> containers = new LinkedHashMap<>();
        for (int index = 0, count = readCount(input); index < count; index++) {
            SubjectId id = new SubjectId(readString(input));
            if (containers.put(id, new ContainerRecord(id, new SubjectId(readString(input)), input.readUnsignedByte())) != null) throw new IllegalArgumentException("duplicate container id");
        }
        Map<SubjectId, ContainerSurface> surfaces = new LinkedHashMap<>();
        for (int index = 0, count = readCount(input); index < count; index++) {
            SubjectId id = new SubjectId(readString(input)); BlockPosition position = readPosition(input); int status = input.readUnsignedByte();
            ContainerSurface surface = new ContainerSurface(id, position, FrontierWireTags.require(ContainerSurfaceStatus.class, status));
            if (status >= ContainerSurfaceStatus.values().length || surfaces.put(id, surface) != null) {
                throw new IllegalArgumentException("invalid or duplicate container surface");
            }
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
                    || conflicts.put(id, new InventoryConflict(id, item, container, slot, FrontierWireTags.require(InventoryConflictKind.class, kind))) != null) {
                throw new IllegalArgumentException("invalid or duplicate inventory conflict");
            }
        }
        return new ExactInventory(containers, items, cargo, players, carriers, conflicts, surfaces, economics);
    }
    private static void writeProductionJobs(DataOutputStream output, Map<SubjectId, ProductionJob> jobs) throws IOException {
        writeCount(output, jobs.size());
        for (ProductionJob job : jobs.values().stream().sorted(java.util.Comparator.comparing(ProductionJob::id)).toList()) {
            writeString(output, job.id().value()); writeString(output, job.settlementId().value()); writeString(output, job.facilityId().value());
            writeString(output, job.workerId().value()); writeString(output, job.consumedItemId().value()); writeProductionInputHold(output, job.inputHold()); writeString(output, job.outputItemId().value());
            writeString(output, job.outputItemKind()); output.writeByte(job.outputCount()); ProductionWorkProgressStateCodec.write(output, job.workProgress());
            TraversalTopologyStateCodec.write(output, job.workTraversal()); output.writeShort(job.traversalCursor());
        } }
    private static Map<SubjectId, ProductionJob> readProductionJobs(DataInputStream input, boolean hasInputHold) throws IOException { Map<SubjectId, ProductionJob> jobs = new LinkedHashMap<>();
        for (int index = 0, count = readCount(input); index < count; index++) {
            SubjectId id = new SubjectId(readString(input));
            SubjectId settlement = new SubjectId(readString(input)), facility = new SubjectId(readString(input)), worker = new SubjectId(readString(input));
            SubjectId consumed = new SubjectId(readString(input)); ProductionInputHold hold = hasInputHold ? readProductionInputHold(input, consumed) : new ProductionInputHold.Materialized(consumed);
            SubjectId output = new SubjectId(readString(input)); String outputKind = readString(input); int outputCount = input.readUnsignedByte();
            ProductionWorkProgress progress = ProductionWorkProgressStateCodec.read(input); TraversalTopology traversal = TraversalTopologyStateCodec.read(input);
            ProductionJob job = new ProductionJob(id, settlement, facility, worker, consumed, hold, output, outputKind, outputCount, progress, traversal, input.readUnsignedShort());
            if (jobs.put(id, job) != null) throw new IllegalArgumentException("duplicate production job id");
        }
        return jobs;
    }
    private static void writeProductionInputHold(DataOutputStream output, ProductionInputHold hold) throws IOException {
        if (hold instanceof ProductionInputHold.Materialized) { output.writeByte(0); return; }
        ExactItemStack item = ((ProductionInputHold.Cold) hold).item();
        output.writeByte(1); writeString(output, item.economicOwnerId().value()); writeString(output, item.itemKind()); output.writeByte(item.count()); writeCustody(output, item.custody());
    }
    private static ProductionInputHold readProductionInputHold(DataInputStream input, SubjectId itemId) throws IOException {
        return switch (input.readUnsignedByte()) {
            case 0 -> new ProductionInputHold.Materialized(itemId);
            case 1 -> new ProductionInputHold.Cold(new ExactItemStack(itemId, new SubjectId(readString(input)), readString(input), input.readUnsignedByte(), readCustody(input)));
            default -> throw new IllegalArgumentException("unknown production input hold");
        };
    }
    private static void writeContracts(DataOutputStream output, Map<SubjectId, SupplyContract> contracts) throws IOException {
        writeCount(output, contracts.size()); for (SupplyContract contract : contracts.values().stream().sorted(java.util.Comparator.comparing(SupplyContract::id)).toList()) {
            writeString(output, contract.id().value()); writeString(output, contract.settlementId().value()); writeString(output, contract.recipientId().value());
            writeString(output, contract.cargoId().value()); writeString(output, contract.itemKind()); output.writeByte(contract.itemCount()); output.writeByte(contract.status().wireTag());
        }
    } private static Map<SubjectId, SupplyContract> readContracts(DataInputStream input) throws IOException {
        Map<SubjectId, SupplyContract> contracts = new LinkedHashMap<>();
        for (int index = 0, count = readCount(input); index < count; index++) {
            SubjectId id = new SubjectId(readString(input)); SubjectId settlement = new SubjectId(readString(input)); SubjectId recipient = new SubjectId(readString(input)); SubjectId cargo = new SubjectId(readString(input));
            String kind = readString(input); int itemCount = input.readUnsignedByte(); int status = input.readUnsignedByte();
            if (status >= ContractStatus.values().length
                    || contracts.put(id, new SupplyContract(id, settlement, recipient, cargo, kind, itemCount, FrontierWireTags.require(ContractStatus.class, status))) != null) {
                throw new IllegalArgumentException("invalid or duplicate supply contract");
            }
        }
        return contracts;
    }
    private static void writeOperations(DataOutputStream output, Map<SubjectId, RouteOperation> operations) throws IOException {
        writeCount(output, operations.size());
        for (RouteOperation operation : operations.values().stream().sorted(Comparator.comparing(RouteOperation::id)).toList()) {
            writeString(output, operation.id().value()); writeString(output, operation.settlementId().value()); writeString(output, operation.cargoId().value());
            writeString(output, operation.destinationId().value()); RouteUnitManifestCodec.write(output, operation.unit());
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
                                                                   boolean hasDeferralObstruction, boolean hasRouteUnit, boolean hasTraversalTopology,
                                                                   boolean hasTypedTravelAnchors) throws IOException {
        Map<SubjectId, RouteOperation> operations = new LinkedHashMap<>();
        for (int index = 0, count = readCount(input); index < count; index++) {
            SubjectId id = new SubjectId(readString(input)); SubjectId settlement = new SubjectId(readString(input)); SubjectId cargo = new SubjectId(readString(input));
            SubjectId destination = new SubjectId(readString(input));
            RouteUnitManifest unit = RouteUnitManifestCodec.read(input);
            java.util.ArrayList<BlockPosition> route = new java.util.ArrayList<>();
            for (int point = 0, pointCount = readCount(input); point < pointCount; point++) route.add(readPosition(input));
            int routeIndex = input.readUnsignedByte(); int stage = input.readUnsignedByte();
            java.util.Optional<OperationAssembly> assembly = input.readBoolean() ? java.util.Optional.of(readAssembly(input, true, true)) : java.util.Optional.empty();
            java.util.Optional<OperationTravel> travel = input.readBoolean() ? java.util.Optional.of(readTravel(input, true, true)) : java.util.Optional.empty();
            if (operations.put(id, new RouteOperation(id, settlement, cargo, destination, unit, route, routeIndex, OperationStage.fromWireCode(stage), assembly, travel)) != null) {
                throw new IllegalArgumentException("invalid or duplicate route operation");
            }
        }
        return operations;
    }
    private static void writeLogisticsHistory(DataOutputStream output, LogisticsHistory history) throws IOException {
        output.writeLong(history.deliveredCount()); output.writeLong(history.failedCount()); output.writeLong(history.interruptedCount());
        writeCount(output, history.receipts().size());
        for (TerminalLogisticsReceipt receipt : history.receipts().values().stream().sorted(Comparator.comparing(TerminalLogisticsReceipt::operationId)).toList()) {
            writeString(output, receipt.operationId().value()); writeString(output, receipt.contractId().value()); writeString(output, receipt.cargoId().value());
            writeString(output, receipt.settlementId().value()); writeString(output, receipt.recipientId().value());
            writeCount(output, receipt.participants().size()); for (SubjectId participant : receipt.participants()) writeString(output, participant.value());
            output.writeByte(receipt.outcome().wireTag()); output.writeLong(receipt.terminalAtTick());
        }
    }
    private static LogisticsHistory readLogisticsHistory(DataInputStream input) throws IOException {
        long delivered = input.readLong(), failed = input.readLong(), interrupted = input.readLong();
        Map<SubjectId, TerminalLogisticsReceipt> receipts = new LinkedHashMap<>();
        for (int index = 0, count = readCount(input); index < count; index++) {
            SubjectId operation = new SubjectId(readString(input)); SubjectId contract = new SubjectId(readString(input)); SubjectId cargo = new SubjectId(readString(input));
            SubjectId settlement = new SubjectId(readString(input)); SubjectId recipient = new SubjectId(readString(input));
            java.util.ArrayList<SubjectId> participants = new java.util.ArrayList<>();
            for (int participant = 0, participantCount = readCount(input); participant < participantCount; participant++) participants.add(new SubjectId(readString(input)));
            int outcome = input.readUnsignedByte(); long terminalAt = input.readLong();
            if (outcome >= TerminalLogisticsReceipt.TerminalLogisticsOutcome.values().length
                    || receipts.put(operation, new TerminalLogisticsReceipt(operation, contract, cargo, settlement, recipient, participants,
                    FrontierWireTags.require(TerminalLogisticsReceipt.TerminalLogisticsOutcome.class, outcome), terminalAt)) != null) {
                throw new IllegalArgumentException("invalid or duplicate terminal logistics receipt");
            }
        }
        return new LogisticsHistory(receipts, delivered, failed, interrupted);
    } private static void writeTravel(DataOutputStream output, OperationTravel travel) throws IOException {
        TraversalTopologyStateCodec.write(output, travel.topology());
        writeCount(output, travel.cursor()); writeCount(output, travel.formation().size());
        for (var entry : travel.formation().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            writeString(output, entry.getKey().value());
            BodyPosition body = entry.getValue(); writePosition(output, new BlockPosition(body.x(), body.y(), body.z()));
        }
        writePosition(output, travel.cargoAnchor().surface().support());
    }
    private static OperationTravel readTravel(DataInputStream input, boolean hasTraversalTopology, boolean hasTypedTravelAnchors) throws IOException {
        return readTravelWithTopology(input, TraversalTopologyStateCodec.read(input), true);
    } static OperationTravel readTravelWithTopology(DataInputStream input, TraversalTopology topology, boolean hasTypedTravelAnchors) throws IOException {
        int cursor = readCount(input); Map<SubjectId, BodyPosition> formation = new LinkedHashMap<>();
        for (int index = 0, count = readCount(input); index < count; index++) {
            SubjectId actor = new SubjectId(readString(input)); BlockPosition body = readPosition(input); formation.put(actor,
                    new BodyPosition(body.x(), body.y(), body.z()));
        }
        return new OperationTravel(topology, cursor, formation, TransportAnchor.atSupportCell(readPosition(input)));
    } private static void writeAssembly(DataOutputStream output, OperationAssembly assembly) throws IOException {
        writeString(output, assembly.cargoCarrierId().value()); writeCount(output, assembly.members().size());
        for (var entry : assembly.members().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            writeString(output, entry.getKey().value()); TraversalTopologyStateCodec.write(output, entry.getValue().topology());
            writeCount(output, entry.getValue().cursor());
        }
        output.writeBoolean(assembly.deferral().isPresent());
        if (assembly.deferral().isPresent()) {
            OperationAssemblyDeferral deferred = assembly.deferral().orElseThrow(); writeString(output, deferred.actorId().value());
            writePosition(output, deferred.target().support()); writePosition(output, deferred.obstructionSurface().support()); output.writeByte(deferred.reason().wireTag());
        }
    }
    private static OperationAssembly readAssembly(DataInputStream input, boolean hasDeferral, boolean hasDeferralObstruction) throws IOException {
        SubjectId carrier = new SubjectId(readString(input)); Map<SubjectId, OperationAssembly.Member> members = new LinkedHashMap<>();
        for (int index = 0, count = readCount(input); index < count; index++) {
            SubjectId actor = new SubjectId(readString(input));
            if (members.put(actor, new OperationAssembly.Member(TraversalTopologyStateCodec.read(input), readCount(input))) != null) throw new IllegalArgumentException("duplicate operation assembly member");
        }
        java.util.Optional<OperationAssemblyDeferral> deferral = java.util.Optional.empty();
        if (hasDeferral && input.readBoolean()) {
            SubjectId actor = new SubjectId(readString(input)); BlockPosition target = readPosition(input);
            BlockPosition obstruction = hasDeferralObstruction ? readPosition(input) : target; int reason = input.readUnsignedByte();
            if (reason >= OperationAssemblyDeferral.Reason.values().length) throw new IllegalArgumentException("unknown operation assembly deferral reason");
            deferral = java.util.Optional.of(new OperationAssemblyDeferral(actor, new SurfaceAnchor(target), new SurfaceAnchor(obstruction), FrontierWireTags.require(OperationAssemblyDeferral.Reason.class, reason)));
        }
        return new OperationAssembly(members, carrier, deferral);
    }
    private static void writeSceneLeases(DataOutputStream output, Map<SceneLeaseId, SceneLease> leases) throws IOException {
        writeCount(output, leases.size());
        for (SceneLease lease : leases.values().stream().sorted(Comparator.comparing(SceneLease::id)).toList()) {
            writeString(output, lease.id().value()); writeString(output, lease.worldId().value()); SceneCauseStateCodec.write(output, lease.cause());
            writePosition(output, lease.handoffPosition()); output.writeLong(lease.handoffInstant().ticks()); output.writeLong(lease.revision()); output.writeByte(lease.status().wireTag());
            writeCount(output, lease.members().size());
            for (SceneMember member : lease.members()) {
                writeString(output, member.actorId().value());
                writeString(output, member.entityId().toString());
                BodyPosition position = lease.memberPosition(member.actorId());
                writePosition(output, new BlockPosition(position.x(), position.y(), position.z()));
            }
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
    private static Map<SceneLeaseId, SceneLease> readSceneLeases(DataInputStream input) throws IOException {
        Map<SceneLeaseId, SceneLease> leases = new LinkedHashMap<>();
        int count = readCount(input);
        for (int index = 0; index < count; index++) {
            SceneLeaseId id = new SceneLeaseId(readString(input)); WorldId world = new WorldId(readString(input));
            SceneCause cause = SceneCauseStateCodec.read(input);
            BlockPosition handoff = readPosition(input);
            long instant = input.readLong(); long revision = input.readLong(); int status = input.readUnsignedByte();
            if (status >= SceneLeaseStatus.values().length) throw new IllegalArgumentException("unknown scene lease status");
            java.util.ArrayList<SceneMember> members = new java.util.ArrayList<>(); Map<SubjectId, BodyPosition> memberPositions = new LinkedHashMap<>();
            for (int member = 0, memberCount = readCount(input); member < memberCount; member++) {
                SubjectId actor = new SubjectId(readString(input)); members.add(new SceneMember(actor, UUID.fromString(readString(input))));
                BlockPosition position = readPosition(input);
                memberPositions.put(actor, new BodyPosition(position.x(), position.y(), position.z()));
            }
            java.util.Set<SubjectId> handoffActors = new java.util.LinkedHashSet<>();
            for (int actor = 0, actorCount = readCount(input); actor < actorCount; actor++) handoffActors.add(new SubjectId(readString(input)));
            java.util.Optional<SceneRecoveryEvidence> recovery = java.util.Optional.empty();
            if (input.readBoolean()) {
                java.util.Set<SubjectId> missing = new java.util.LinkedHashSet<>();
                for (int actor = 0, actorCount = readCount(input); actor < actorCount; actor++) missing.add(new SubjectId(readString(input)));
                recovery = java.util.Optional.of(new SceneRecoveryEvidence(missing, input.readBoolean()));
            }
            SceneLease lease = SceneLease.forCause(id, world, cause, handoff, new io.farfrontier.palemirror.frontier.v3.api.SimInstant(instant), revision,
                    FrontierWireTags.require(SceneLeaseStatus.class, status), members, memberPositions, handoffActors, recovery);
            if (leases.put(id, lease) != null) throw new IllegalArgumentException("duplicate scene lease id");
        }
        return leases;
    }
    static void writeCustody(DataOutputStream output, InventoryCustody custody) throws IOException {
        if (custody instanceof InventoryCustody.ContainerSlot slot) { output.writeByte(0); writeString(output, slot.containerId().value()); output.writeByte(slot.slot()); }
        else if (custody instanceof InventoryCustody.Cargo cargo) { output.writeByte(1); writeString(output, cargo.cargoId().value()); }
        else if (custody instanceof InventoryCustody.Player player) { output.writeByte(2); writeString(output, player.playerId().toString()); }
        else if (custody instanceof InventoryCustody.WorldCarrier carrier) { output.writeByte(3); writeString(output, carrier.carrierId().toString()); }
        else { output.writeByte(4); writeString(output, ((InventoryCustody.Actor) custody).actorId().value()); }
    } static InventoryCustody readCustody(DataInputStream input) throws IOException {
        return switch (input.readUnsignedByte()) {
            case 0 -> new InventoryCustody.ContainerSlot(new SubjectId(readString(input)), input.readUnsignedByte());
            case 1 -> new InventoryCustody.Cargo(new SubjectId(readString(input)));
            case 2 -> new InventoryCustody.Player(UUID.fromString(readString(input)));
            case 3 -> new InventoryCustody.WorldCarrier(UUID.fromString(readString(input))); case 4 -> new InventoryCustody.Actor(new SubjectId(readString(input)));
            default -> throw new IllegalArgumentException("unknown inventory custody");
        };
    } static void writePosition(DataOutputStream output, BlockPosition position) throws IOException {
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
