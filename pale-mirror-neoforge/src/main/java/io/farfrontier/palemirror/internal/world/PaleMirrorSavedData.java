package io.farfrontier.palemirror.internal.world;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.nio.file.Path;
import io.farfrontier.palemirror.domain.DomainEvent;
import io.farfrontier.palemirror.domain.DomainEventType;
import io.farfrontier.palemirror.domain.DomainEventSummary;
import io.farfrontier.palemirror.domain.DomainStateValidator;
import io.farfrontier.palemirror.domain.FacilityState;
import io.farfrontier.palemirror.domain.GatePhaseRef;
import io.farfrontier.palemirror.domain.GatePlanRef;
import io.farfrontier.palemirror.domain.InfectionSourceId;
import io.farfrontier.palemirror.domain.FacilityStatus;
import io.farfrontier.palemirror.domain.ScenarioInstance;
import io.farfrontier.palemirror.domain.ScenarioStatus;
import io.farfrontier.palemirror.domain.ScenarioArchetype;
import io.farfrontier.palemirror.domain.SourceGateStatus;
import io.farfrontier.palemirror.domain.SourceGateState;
import io.farfrontier.palemirror.domain.StoryAudienceId;
import io.farfrontier.palemirror.domain.ThreatTier;
import io.farfrontier.palemirror.domain.WorldObjectId;
import io.farfrontier.palemirror.domain.WorldState;
import io.farfrontier.palemirror.domain.WorldStateHydration;
import io.farfrontier.palemirror.internal.materialization.JobState;
import io.farfrontier.palemirror.internal.materialization.MaterializationJob;
import io.farfrontier.palemirror.internal.materialization.MaterializationOperation;
import io.farfrontier.palemirror.internal.materialization.MaterializationOperationType;
import io.farfrontier.palemirror.internal.materialization.OperationState;
import io.farfrontier.palemirror.internal.materialization.MaterializationJobRegistry;
import io.farfrontier.palemirror.internal.materialization.SemanticSlotLedger;
import io.farfrontier.palemirror.internal.materialization.ParcelLedger;
import io.farfrontier.palemirror.internal.observation.ReconciliationLedger;
import io.farfrontier.palemirror.internal.effect.EffectLease;
import io.farfrontier.palemirror.internal.effect.EffectLeaseLedger;
import io.farfrontier.palemirror.internal.quarantine.QuarantineLedger;
import io.farfrontier.palemirror.internal.quarantine.QuarantineRecord;
import io.farfrontier.palemirror.internal.combat.ThreatCombatLedger;
import io.farfrontier.palemirror.internal.economy.EconomyPresentationCodec;
import io.farfrontier.palemirror.internal.economy.ResourceTransferLedger;
import io.farfrontier.palemirror.internal.economy.SettlementDepotRecord;
import io.farfrontier.palemirror.internal.settlement.DisplacementPresentationCodec;
import io.farfrontier.palemirror.internal.settlement.RefugeeCampRecord;
import io.farfrontier.palemirror.internal.settlement.RefugeeAnchorPermitLedger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
/** One global server-world store, physically hosted in the Overworld data storage. */
public final class PaleMirrorSavedData extends SavedData {
    public static final String DATA_NAME = "pale_mirror";
    static final int CURRENT_SCHEMA = 36;
    private final WorldState worldState;
    private final Map<WorldObjectId, TestMineRecord> testMines;
    private final Map<String, StoryAudienceId> audienceMappings;
    private final ReconciliationLedger reconciliationLedger;
    private final WorldObjectRegistry worldRegistry;
    private final EffectLeaseLedger effectLeases;
    private final QuarantineLedger quarantine;
    private final ThreatCombatLedger threatCombat;
    private final Map<String, CampaignRegionRecord> campaignRegions;
    private final Map<WorldObjectId, SettlementObservationRecord> settlementObservations;
    private final ResourceTransferLedger resourceTransfers;
    private final Map<WorldObjectId, SettlementDepotRecord> settlementDepots;
    private final Map<String, RefugeeCampRecord> refugeeCamps;
    private final RefugeeAnchorPermitLedger refugeeAnchorPermits;
    private final Map<String, CampaignCommissioningRecord> campaignCommissioning;
    private final Map<String, VanillaMinecartRouteRecord> vanillaMinecartRoutes;
    private final MaterializationJobRegistry materializationJobs;
    private final SemanticSlotLedger semanticSlots;
    private final ResidentJourneyLeaseLedger residentJourneyLeases;
    private final ResidentIdentityLedger residentIdentities;
    private final ParcelLedger parcels;
    public PaleMirrorSavedData() {
        this(WorldStateHydration.builder().schemaVersion(CURRENT_SCHEMA).build(), new LinkedHashMap<>(), new LinkedHashMap<>(),
                new ReconciliationLedger(), new WorldObjectRegistry(),
                new EffectLeaseLedger(), new QuarantineLedger(), new ThreatCombatLedger(), new LinkedHashMap<>(), new LinkedHashMap<>(),
                new ResourceTransferLedger(), new LinkedHashMap<>(), new LinkedHashMap<>(), new RefugeeAnchorPermitLedger(),
                new LinkedHashMap<>(), new LinkedHashMap<>(), new MaterializationJobRegistry(), new SemanticSlotLedger(),
                new ResidentJourneyLeaseLedger(), new ResidentIdentityLedger(), new ParcelLedger());
    }
    private PaleMirrorSavedData(WorldState worldState, Map<WorldObjectId, TestMineRecord> testMines,
                                Map<String, StoryAudienceId> audienceMappings, ReconciliationLedger reconciliationLedger,
                                WorldObjectRegistry worldRegistry, EffectLeaseLedger effectLeases, QuarantineLedger quarantine,
                                ThreatCombatLedger threatCombat, Map<String, CampaignRegionRecord> campaignRegions,
                                Map<WorldObjectId, SettlementObservationRecord> settlementObservations,
                                ResourceTransferLedger resourceTransfers,
                                Map<WorldObjectId, SettlementDepotRecord> settlementDepots,
                                Map<String, RefugeeCampRecord> refugeeCamps, RefugeeAnchorPermitLedger refugeeAnchorPermits,
                                Map<String, CampaignCommissioningRecord> campaignCommissioning,
                                Map<String, VanillaMinecartRouteRecord> vanillaMinecartRoutes,
                                MaterializationJobRegistry materializationJobs, SemanticSlotLedger semanticSlots,
                                ResidentJourneyLeaseLedger residentJourneyLeases,
                                ResidentIdentityLedger residentIdentities, ParcelLedger parcels) {
        this.worldState = worldState;
        this.testMines = testMines;
        this.audienceMappings = audienceMappings;
        this.reconciliationLedger = reconciliationLedger;
        this.worldRegistry = worldRegistry;
        this.effectLeases = effectLeases;
        this.quarantine = quarantine;
        this.threatCombat = threatCombat;
        this.campaignRegions = campaignRegions;
        this.settlementObservations = settlementObservations;
        this.resourceTransfers = resourceTransfers;
        this.settlementDepots = settlementDepots;
        this.refugeeCamps = refugeeCamps;
        this.refugeeAnchorPermits = refugeeAnchorPermits;
        this.campaignCommissioning = campaignCommissioning;
        this.vanillaMinecartRoutes = vanillaMinecartRoutes;
        this.materializationJobs = materializationJobs;
        this.semanticSlots = semanticSlots;
        this.residentJourneyLeases = residentJourneyLeases;
        this.residentIdentities = residentIdentities;
        this.parcels = parcels;
    }
    public static PaleMirrorSavedData get(ServerLevel overworld) {
        return overworld.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(PaleMirrorSavedData::new, PaleMirrorSavedData::load, DataFixTypes.SAVED_DATA_COMMAND_STORAGE), DATA_NAME);
    }
    /**
     * DimensionDataStorage logs and replaces a broken SavedData load by design.
     * Pale Mirror cannot permit that behaviour for an incompatible canonical
     * snapshot, so startup validates the file before DimensionDataStorage sees it.
     */
    public static void assertCompatibleData(Path worldRoot) {
        PaleMirrorSavedDataCompatibility.assertCompatible(worldRoot, DATA_NAME,
                PaleMirrorSavedData::isMigratable, PaleMirrorSavedData::incompatibleSchema);
    }
    public WorldState worldState() { return worldState; }
    public Map<WorldObjectId, TestMineRecord> testMines() { return testMines; }
    public Map<String, StoryAudienceId> audienceMappings() { return audienceMappings; }
    public ReconciliationLedger reconciliationLedger() { return reconciliationLedger; }
    public WorldObjectRegistry worldRegistry() { return worldRegistry; }
    public EffectLeaseLedger effectLeases() { return effectLeases; }
    public QuarantineLedger quarantine() { return quarantine; }
    public ThreatCombatLedger threatCombat() { return threatCombat; }
    public Map<String, CampaignRegionRecord> campaignRegions() { return campaignRegions; }
    /** Physical evidence from read-only village observers; never a second canonical settlement model. */
    public Map<WorldObjectId, SettlementObservationRecord> settlementObservations() { return settlementObservations; }
    public ResourceTransferLedger resourceTransfers() { return resourceTransfers; } public Map<WorldObjectId, SettlementDepotRecord> settlementDepots() { return settlementDepots; }
    public Map<String, RefugeeCampRecord> refugeeCamps() { return refugeeCamps; }
    public RefugeeAnchorPermitLedger refugeeAnchorPermits() { return refugeeAnchorPermits; }
    public Map<String, CampaignCommissioningRecord> campaignCommissioning() { return campaignCommissioning; }
    /** Physical vanilla route jobs; the domain RouteContract remains cargo authority. */
    public Map<String, VanillaMinecartRouteRecord> vanillaMinecartRoutes() { return vanillaMinecartRoutes; }
    public MaterializationJobRegistry materializationJobs() { return materializationJobs; }
    public SemanticSlotLedger semanticSlots() { return semanticSlots; }
    public ResidentJourneyLeaseLedger residentJourneyLeases() { return residentJourneyLeases; }
    public ResidentIdentityLedger residentIdentities() { return residentIdentities; }
    public ParcelLedger parcels() { return parcels; }
    public boolean observeSettlement(io.farfrontier.palemirror.internal.adapter.SettlementObservation observation) {
        SettlementObservationRecord record = settlementObservations.get(observation.settlementId());
        if (record == null) {
            record = new SettlementObservationRecord(observation);
            settlementObservations.put(record.id(), record);
            if (worldRegistry.find(record.id()).isEmpty()) worldRegistry.register(record.registryEntry());
            return true;
        }
        return record.observe(observation);
    }
    public void registerTestMine(TestMineRecord mine) {
        worldRegistry.register(mine.object());
        testMines.put(mine.id(), mine);
    }
    public static PaleMirrorSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        int version = tag.contains("schemaVersion", Tag.TAG_INT) ? tag.getInt("schemaVersion") : 0;
        if (version > CURRENT_SCHEMA) {
            throw new IllegalStateException("Pale Mirror data schema " + version + " is newer than this mod supports");
        }
        if (!isMigratable(version)) throw incompatibleSchema(version);
        CompoundTag snapshot = tag.getCompound("snapshot");
        WorldState state = readState(snapshot);
        WorldObjectRegistry registry = WorldPresentationCodec.readRegistry(tag.getList("worldObjects", Tag.TAG_COMPOUND));
        Map<WorldObjectId, TestMineRecord> mines = new LinkedHashMap<>();
        for (Tag element : tag.getList("testMines", Tag.TAG_COMPOUND)) {
            TestMineRecord mine = TestMineCodec.read((CompoundTag) element, registry);
            mines.put(mine.id(), mine);
        }
        Map<String, StoryAudienceId> audiences = new LinkedHashMap<>();
        for (Tag element : tag.getList("audiences", Tag.TAG_COMPOUND)) {
            CompoundTag value = (CompoundTag) element;
            audiences.put(value.getString("key"), new StoryAudienceId(value.getString("id")));
        }
        List<String> observations = new ArrayList<>();
        for (Tag element : tag.getList("reconciledObservations", Tag.TAG_STRING)) observations.add(element.getAsString());
        Map<String, EffectLease> leases = new LinkedHashMap<>();
        for (Tag element : tag.getList("effectLeases", Tag.TAG_COMPOUND)) {
            io.farfrontier.palemirror.internal.effect.EffectLease lease = PaleMirrorAuxiliaryPresentationCodec.readEffectLease((CompoundTag) element);
            leases.put(lease.id(), lease);
        }
        Map<String, QuarantineRecord> quarantine = new LinkedHashMap<>();
        for (Tag element : tag.getList("quarantine", Tag.TAG_COMPOUND)) {
            io.farfrontier.palemirror.internal.quarantine.QuarantineRecord record = PaleMirrorAuxiliaryPresentationCodec.readQuarantine((CompoundTag) element);
            quarantine.put(record.id(), record);
        }
        Map<String, CampaignRegionRecord> campaignRegions = CampaignRegionPresentationCodec.read(tag);
        Map<String, CampaignCommissioningRecord> commissioning = CampaignCommissioningCodec.read(tag, version);
        Map<String, VanillaMinecartRouteRecord> minecartRoutes = VanillaMinecartRouteCodec.read(tag, version);
        if (version == 24 && !campaignRegions.isEmpty()) campaignRegions.values().forEach(region ->
                commissioning.putIfAbsent(region.id(), CampaignCommissioningRecord.legacyDisabled(region.id(),
                        region.dimensionId(), region.settlementAnchor())));
        PaleMirrorSavedData loaded = new PaleMirrorSavedData(state, mines, audiences, new ReconciliationLedger(observations), registry,
                new EffectLeaseLedger(leases), new QuarantineLedger(quarantine), ThreatCombatPresentationCodec.read(tag),
                campaignRegions, SettlementObservationCodec.read(tag), EconomyPresentationCodec.readLedger(tag), EconomyPresentationCodec.readDepots(tag),
                DisplacementPresentationCodec.read(tag), DisplacementPresentationCodec.readPermits(tag), commissioning, minecartRoutes,
                MaterializationJobCodec.read(tag), SemanticSlotCodec.read(tag, registries), ResidentJourneyLeaseCodec.read(tag),
                ResidentIdentityCodec.read(tag), ParcelCodec.read(tag));
        loaded.validateIntegrity();
        return loaded;
    }
    private static IllegalStateException incompatibleSchema(int version) {
        return new IllegalStateException("Pale Mirror data schema " + version + " is not compatible with schema "
                + CURRENT_SCHEMA + ". Back up the old world before resetting its Pale Mirror data.");
    }
    private static boolean isMigratable(int version) {
        return version == CURRENT_SCHEMA;
    }
    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        validateIntegrity();
        tag.putInt("schemaVersion", CURRENT_SCHEMA);
        tag.put("snapshot", writeState(worldState));
        ListTag objects = new ListTag();
        worldRegistry.entries().forEach(entry -> objects.add(WorldPresentationCodec.writeRegistryEntry(entry)));
        tag.put("worldObjects", objects);
        ListTag mines = new ListTag();
        testMines.values().forEach(mine -> mines.add(TestMineCodec.write(mine)));
        tag.put("testMines", mines);
        ListTag audiences = new ListTag();
        audienceMappings.forEach((key, value) -> {
            CompoundTag audience = new CompoundTag();
            audience.putString("key", key);
            audience.putString("id", value.value());
            audiences.add(audience);
        });
        tag.put("audiences", audiences);
        ListTag observations = new ListTag();
        reconciliationLedger.appliedIds().forEach(value -> observations.add(net.minecraft.nbt.StringTag.valueOf(value)));
        tag.put("reconciledObservations", observations);
        ListTag effectLeases = new ListTag();
        this.effectLeases.leases().forEach(lease -> effectLeases.add(PaleMirrorAuxiliaryPresentationCodec.writeEffectLease(lease)));
        tag.put("effectLeases", effectLeases);
        ListTag quarantine = new ListTag();
        this.quarantine.records().forEach(record -> quarantine.add(PaleMirrorAuxiliaryPresentationCodec.writeQuarantine(record)));
        tag.put("quarantine", quarantine);
        ThreatCombatPresentationCodec.write(tag, threatCombat);
        CampaignRegionPresentationCodec.write(tag, campaignRegions);
        SettlementObservationCodec.write(tag, settlementObservations);
        EconomyPresentationCodec.write(tag, resourceTransfers, settlementDepots);
        DisplacementPresentationCodec.write(tag, refugeeCamps, refugeeAnchorPermits);
        CampaignCommissioningCodec.write(tag, campaignCommissioning);
        VanillaMinecartRouteCodec.write(tag, vanillaMinecartRoutes);
        MaterializationJobCodec.write(tag, materializationJobs);
        SemanticSlotCodec.write(tag, semanticSlots);
        ResidentJourneyLeaseCodec.write(tag, residentJourneyLeases);
        ResidentIdentityCodec.write(tag, residentIdentities);
        ParcelCodec.write(tag, parcels);
        return tag;
    }

    private void validateIntegrity() {
        DomainStateValidator.validate(worldState, CURRENT_SCHEMA);
        SavedDataIntegrityValidator.validate(this);
    }
    private static CompoundTag writeState(WorldState state) {
        CompoundTag tag = new CompoundTag();
        tag.putLong("simulationStep", state.simulationStep());
        tag.putLong("eventSequence", state.eventSequence());
        ListTag facilities = new ListTag();
        state.facilities().forEach(value -> {
            CompoundTag facility = new CompoundTag();
            facility.putString("id", value.id().value());
            facility.putString("infectionSource", value.infectionSource().value());
            facility.putInt("normalProduction", value.normalProduction());
            facility.putInt("threshold", value.infectionThreshold());
            facility.putInt("pressure", value.infectionPressure());
            facility.putInt("currentProduction", value.currentProduction());
            facility.putInt("recoverySteps", value.recoveryStepsRemaining());
            facility.putLong("desiredRevision", value.desiredRevision());
            facility.putLong("observedRevision", value.observedRevision());
            facility.putString("status", value.status().name());
            facility.putString("threatTier", value.threatTier().name());
            facility.putLong("threatStartedAtStep", value.threatStartedAtStep());
            facility.put("gate", writeGateState(value.gate()));
            facilities.add(facility);
        });
        tag.put("facilities", facilities);
        ListTag scenarios = new ListTag();
        state.scenarios().forEach(value -> {
            CompoundTag scenario = new CompoundTag();
            scenario.putString("id", value.id());
            scenario.putString("source", value.sourceEventId());
            scenario.putString("target", value.target().value());
            scenario.putString("audience", value.audience().value());
            scenario.putString("definition", value.definitionId());
            scenario.putString("definitionVersion", value.definitionVersion());
            scenario.putString("encounterProfile", value.encounterProfileId());
            scenario.putString("encounterProfileVersion", value.encounterProfileVersion());
            scenario.putString("archetype", value.archetype().name());
            ListTag stages = new ListTag();
            value.pinnedStages().forEach(stage -> stages.add(net.minecraft.nbt.StringTag.valueOf(stage)));
            scenario.put("pinnedStages", stages);
            ListTag capabilities = new ListTag();
            value.requiredCapabilities().forEach(capability -> capabilities.add(net.minecraft.nbt.StringTag.valueOf(capability)));
            scenario.put("requiredCapabilities", capabilities);
            scenario.putString("status", value.status().name());
            if (value.resumeStatus() != null) scenario.putString("resumeStatus", value.resumeStatus().name());
            scenario.putString("blockedReason", value.blockedReason());
            scenario.putString("resolutionOutcome", value.resolutionOutcome());
            scenarios.add(scenario);
        });
        tag.put("scenarios", scenarios);
        RegionalStateCodec.write(tag, state);
        ListTag events = new ListTag();
        state.history().forEach(value -> {
            CompoundTag event = new CompoundTag();
            event.putString("id", value.eventId());
            event.putString("type", value.type().name());
            event.putString("subject", value.subject().value());
            event.putLong("step", value.simulationStep());
            event.putString("causation", value.causationId() == null ? "" : value.causationId());
            event.putString("correlation", value.correlationId() == null ? "" : value.correlationId());
            events.add(event);
        });
        tag.put("events", events);
        ListTag eventSummaries = new ListTag();
        state.historySummaries().forEach(value -> {
            CompoundTag summary = new CompoundTag();
            summary.putString("subject", value.subject().value());
            summary.putString("type", value.type().name());
            summary.putLong("count", value.count());
            summary.putLong("firstStep", value.firstStep());
            summary.putLong("lastStep", value.lastStep());
            eventSummaries.add(summary);
        });
        tag.put("eventSummaries", eventSummaries);
        ListTag cooldowns = new ListTag();
        state.narratorCooldowns().forEach((audience, availableAt) -> {
            CompoundTag cooldown = new CompoundTag();
            cooldown.putString("audience", audience.value());
            cooldown.putLong("availableAt", availableAt);
            cooldowns.add(cooldown);
        });
        tag.put("narratorCooldowns", cooldowns);
        return tag;
    }
    private static CompoundTag writeGateState(SourceGateState gate) {
        CompoundTag tag = new CompoundTag();
        tag.putString("status", gate.status().name());
        tag.putInt("phase", gate.currentPhaseIndex());
        ListTag destroyed = new ListTag();
        gate.destroyedPartIds().forEach(value -> destroyed.add(net.minecraft.nbt.StringTag.valueOf(value)));
        tag.put("destroyedParts", destroyed);
        gate.plan().ifPresent(plan -> {
            CompoundTag serialized = new CompoundTag();
            serialized.putString("id", plan.id());
            serialized.putString("version", plan.version());
            ListTag phases = new ListTag();
            plan.phases().forEach(phase -> {
                CompoundTag value = new CompoundTag();
                value.putString("id", phase.id());
                ListTag parts = new ListTag();
                phase.requiredPartIds().forEach(part -> parts.add(net.minecraft.nbt.StringTag.valueOf(part)));
                value.put("parts", parts);
                phases.add(value);
            });
            serialized.put("phases", phases);
            ListTag parameters = new ListTag();
            plan.opaqueParameters().forEach((key, value) -> {
                CompoundTag parameter = new CompoundTag();
                parameter.putString("key", key);
                parameter.putString("value", value);
                parameters.add(parameter);
            });
            serialized.put("parameters", parameters);
            tag.put("plan", serialized);
        });
        return tag;
    }
    private static SourceGateState readGateState(CompoundTag tag) {
        SourceGateStatus status = SourceGateStatus.valueOf(tag.contains("status", Tag.TAG_STRING)
                ? tag.getString("status") : SourceGateStatus.INACTIVE.name());
        GatePlanRef plan = null;
        if (tag.contains("plan", Tag.TAG_COMPOUND)) {
            CompoundTag serialized = tag.getCompound("plan");
            List<GatePhaseRef> phases = new ArrayList<>();
            for (Tag element : serialized.getList("phases", Tag.TAG_COMPOUND)) {
                CompoundTag phase = (CompoundTag) element;
                phases.add(new GatePhaseRef(phase.getString("id"), stringList(phase.getList("parts", Tag.TAG_STRING))));
            }
            Map<String, String> parameters = new LinkedHashMap<>();
            for (Tag element : serialized.getList("parameters", Tag.TAG_COMPOUND)) {
                CompoundTag parameter = (CompoundTag) element;
                parameters.put(parameter.getString("key"), parameter.getString("value"));
            }
            plan = new GatePlanRef(serialized.getString("id"), serialized.getString("version"), phases, parameters);
        }
        return new SourceGateState(status, plan, tag.getInt("phase"),
                new java.util.LinkedHashSet<>(stringList(tag.getList("destroyedParts", Tag.TAG_STRING))));
    }
    private static WorldState readState(CompoundTag tag) {
        WorldStateHydration.Builder state = WorldStateHydration.builder()
                .schemaVersion(CURRENT_SCHEMA)
                .simulationStep(tag.getLong("simulationStep"));
        long eventSequence = tag.getLong("eventSequence");
        for (Tag element : tag.getList("facilities", Tag.TAG_COMPOUND)) {
            CompoundTag value = (CompoundTag) element;
            SourceGateState gate = readGateState(value.getCompound("gate"));
            state.facility(new FacilityState(new WorldObjectId(value.getString("id")),
                    new InfectionSourceId(value.getString("infectionSource")), value.getInt("normalProduction"),
                    value.getInt("threshold"), value.getInt("pressure"), value.getInt("currentProduction"),
                    value.getInt("recoverySteps"), value.getLong("desiredRevision"), value.getLong("observedRevision"),
                    FacilityStatus.valueOf(value.getString("status")),
                    ThreatTier.valueOf(value.contains("threatTier", Tag.TAG_STRING) ? value.getString("threatTier") : "DORMANT"),
                    value.getLong("threatStartedAtStep"), gate));
        }
        for (Tag element : tag.getList("scenarios", Tag.TAG_COMPOUND)) {
            CompoundTag value = (CompoundTag) element;
            List<String> stages = stringList(value.getList("pinnedStages", Tag.TAG_STRING));
            List<String> capabilities = stringList(value.getList("requiredCapabilities", Tag.TAG_STRING));
            state.scenario(new ScenarioInstance(value.getString("id"), value.getString("source"),
                    new WorldObjectId(value.getString("target")), new StoryAudienceId(value.getString("audience")),
                    value.getString("definition"), value.getString("definitionVersion"), stages, capabilities,
                    value.getString("encounterProfile"), value.getString("encounterProfileVersion"),
                    ScenarioArchetype.valueOf(value.contains("archetype", Tag.TAG_STRING)
                            ? value.getString("archetype") : ScenarioArchetype.INVESTIGATION_RECOVERY.name()),
                    ScenarioStatus.valueOf(value.getString("status")),
                    value.contains("resumeStatus", Tag.TAG_STRING) ? ScenarioStatus.valueOf(value.getString("resumeStatus")) : null,
                    value.getString("blockedReason"), value.getString("resolutionOutcome")));
        }
        RegionalStateCodec.read(tag, state);
        for (Tag element : tag.getList("eventSummaries", Tag.TAG_COMPOUND)) {
            CompoundTag value = (CompoundTag) element;
            state.eventSummary(new DomainEventSummary(new WorldObjectId(value.getString("subject")),
                    DomainEventType.valueOf(value.getString("type")), value.getLong("count"),
                    value.getLong("firstStep"), value.getLong("lastStep")));
        }
        for (Tag element : tag.getList("events", Tag.TAG_COMPOUND)) {
            CompoundTag value = (CompoundTag) element;
            String id = value.getString("id");
            state.event(new DomainEvent(id, DomainEventType.valueOf(value.getString("type")),
                    new WorldObjectId(value.getString("subject")), value.getLong("step"),
                    value.getString("causation"), value.getString("correlation")));
            int suffix = id.lastIndexOf(':');
            if (suffix >= 0) {
                try { eventSequence = Math.max(eventSequence, Long.parseLong(id.substring(suffix + 1))); } catch (NumberFormatException ignored) { }
            }
        }
        state.eventSequence(eventSequence);
        for (Tag element : tag.getList("narratorCooldowns", Tag.TAG_COMPOUND)) {
            CompoundTag cooldown = (CompoundTag) element;
            state.cooldown(new StoryAudienceId(cooldown.getString("audience")), cooldown.getLong("availableAt"));
        }
        return state.build();
    }
    private static List<String> stringList(ListTag tags) {
        List<String> values = new ArrayList<>();
        for (Tag tag : tags) values.add(tag.getAsString());
        return values;
    }
}
