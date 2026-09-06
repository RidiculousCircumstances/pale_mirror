package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.model.SceneCauseKind;
import io.farfrontier.palemirror.frontier.v3.kernel.DeterministicProcessDescriptor;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierSceneBehaviors;
import io.farfrontier.palemirror.frontier.v3.model.LogisticsSceneCause;
import io.farfrontier.palemirror.frontier.v3.model.SceneLease;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Closed composition check for duration-bearing process execution.
 *
 * <p>The inventory is deliberately code, not a prose convention.  A family enters
 * {@link ContractState#ENFORCED} only when it is being corrected to the F0 paired-driver
 * contract.  Deferred legacy families remain visibly inventoried but cannot be falsely
 * registered as paired until their owning F0 slice converts them.  Every enforced family must
 * name exactly one real scheduled COLD kind and exactly one matching registered scene cause.</p>
 */
public final class FrontierDurationProcessDriverRegistry {
    private FrontierDurationProcessDriverRegistry() { }

    public enum Family {
        RESOURCE_SITE_HARVEST("frontier.resource-site-harvest", "ResourceSiteHarvestJob", "F0.1", "resource-sites",
                FrontierProcessSceneSdk.ExecutionArchetype.DURATION_WORK, ResourceSiteHarvestProcess.COLD_PROGRESS_KIND,
                SceneCauseKind.RESOURCE_SITE_HARVEST, ContractState.ENFORCED),
        PRODUCTION_WORK("frontier.production-work", "ProductionJob", "F0.2 revalidation", "economy",
                FrontierProcessSceneSdk.ExecutionArchetype.DURATION_WORK, "frontier.settlement.production.task.complete", SceneCauseKind.PRODUCTION_WORK, ContractState.DEFERRED),
        SETTLEMENT_SERVICE_WORK("frontier.settlement-service-work", "SettlementServiceWork", "F0.2 revalidation", "settlement-service-work",
                FrontierProcessSceneSdk.ExecutionArchetype.DURATION_WORK, "frontier.decontamination.scan", SceneCauseKind.SERVICE_WORK, ContractState.DEFERRED),
        ROUTE_OPERATION("frontier.route-operation", "RouteOperation", "F0.4", "logistics-scenes",
                FrontierProcessSceneSdk.ExecutionArchetype.COORDINATED_TRAVERSAL, "frontier.operation.progress", SceneCauseKind.LOGISTICS, ContractState.DEFERRED),
        ROUTE_PATROL("frontier.route-patrol", "RoutePatrol", "F0.4", "infrastructure",
                FrontierProcessSceneSdk.ExecutionArchetype.COORDINATED_TRAVERSAL, "frontier.route_patrol.progress", SceneCauseKind.ROUTE_PATROL, ContractState.DEFERRED),
        HIVE_MOBILIZATION("frontier.hive-mobilization", "HiveMobilization", "F0.4", "hive",
                FrontierProcessSceneSdk.ExecutionArchetype.COMPOSITE_OPERATION, null, null, ContractState.DEFERRED),
        ROUTE_ENGAGEMENT("frontier.route-engagement", "RouteEngagement", "F0.4", "hive",
                FrontierProcessSceneSdk.ExecutionArchetype.COMPOSITE_OPERATION, null, SceneCauseKind.LOGISTICS, ContractState.DEFERRED),
        SETTLEMENT_ASSAULT("frontier.settlement-assault", "SettlementAssault", "F0.4", "hive",
                FrontierProcessSceneSdk.ExecutionArchetype.COMPOSITE_OPERATION, null, SceneCauseKind.SETTLEMENT_ASSAULT, ContractState.DEFERRED),
        POPULATION_MIGRATION("frontier.population-migration", "ResidentMigrationJourney", "F0.4 revalidation", "population",
                FrontierProcessSceneSdk.ExecutionArchetype.COORDINATED_TRAVERSAL, "frontier.population.migration.progress", null, ContractState.DEFERRED),
        MEDICAL_TREATMENT("frontier.medical-treatment", "MedicalTreatment", "F0.2/F0.5", "settlement-service-work",
                FrontierProcessSceneSdk.ExecutionArchetype.DURATION_WORK, "frontier.decontamination.scan", SceneCauseKind.MEDICAL_TREATMENT, ContractState.DEFERRED),
        ENGINEERING_WORKSITE("frontier.engineering-worksite", "RouteConstruction/RouteMaintenance", "F0.4/F0.5", "infrastructure",
                FrontierProcessSceneSdk.ExecutionArchetype.DURATION_WORK, "frontier.route_maintenance.progress", SceneCauseKind.ENGINEERING_WORKSITE, ContractState.DEFERRED),
        HIVE_GROWTH("frontier.hive-growth", "HiveGrowth", "F0.6 revalidation", "hive",
                FrontierProcessSceneSdk.ExecutionArchetype.SPATIAL_FRONTIER, null, null, ContractState.DEFERRED),
        HIVE_NUTRIENT_TRANSFER("frontier.hive-nutrient-transfer", "HiveNutrientTransfer", "F0.6 revalidation", "hive",
                FrontierProcessSceneSdk.ExecutionArchetype.DURATION_WORK, "frontier.hive.nutrient.transfer.progress", null, ContractState.DEFERRED),
        SETTLEMENT_PROVISION("frontier.settlement-provision", "SettlementProvision", "F0.2 revalidation", "economy",
                FrontierProcessSceneSdk.ExecutionArchetype.ATOMIC_INTENT, null, null, ContractState.DEFERRED),
        RESOURCE_SITE_PREPARATION("frontier.resource-site-preparation", "ResourceSiteLifecycle", "F0.6 revalidation", "resource-sites",
                FrontierProcessSceneSdk.ExecutionArchetype.ATOMIC_INTENT, null, null, ContractState.DEFERRED),
        AMBIENT_ACTOR_CUSTODY("frontier.ambient-actor-custody", "ActorLocation", "F0.2/F0.5", "ambient-actors",
                FrontierProcessSceneSdk.ExecutionArchetype.AMBIENT_CUSTODY, null, null, ContractState.DEFERRED);

        private final FrontierProcessSceneSdk.FamilyKey sdkFamilyKey;
        private final String canonicalOwner;
        private final String correctionSlice;
        private final FrontierProcessSceneSdk.ExecutionArchetype archetype;
        private final String productionProcessId;
        private final String coldScheduledKind;
        private final SceneCauseKind hotSceneCause;
        private final ContractState contractState;

        Family(String sdkFamilyKey, String canonicalOwner, String correctionSlice, String productionProcessId,
               FrontierProcessSceneSdk.ExecutionArchetype archetype, String coldScheduledKind, SceneCauseKind hotSceneCause,
               ContractState contractState) {
            this.sdkFamilyKey = new FrontierProcessSceneSdk.FamilyKey(sdkFamilyKey);
            this.canonicalOwner = Objects.requireNonNull(canonicalOwner, "duration canonical owner");
            this.correctionSlice = Objects.requireNonNull(correctionSlice, "duration correction slice");
            this.archetype = Objects.requireNonNull(archetype, "process/scene archetype");
            this.productionProcessId = Objects.requireNonNull(productionProcessId, "production process owner");
            this.coldScheduledKind = coldScheduledKind;
            this.hotSceneCause = hotSceneCause;
            this.contractState = Objects.requireNonNull(contractState, "duration contract state");
            if (contractState == ContractState.ENFORCED && (coldScheduledKind == null || hotSceneCause == null)) {
                throw new IllegalArgumentException("enforced duration process needs both driver identities");
            }
        }

        public FrontierProcessSceneSdk.FamilyKey sdkFamilyKey() { return sdkFamilyKey; }
        public String canonicalOwner() { return canonicalOwner; }
        public String correctionSlice() { return correctionSlice; }
        public FrontierProcessSceneSdk.ExecutionArchetype archetype() { return archetype; }
        public String productionProcessId() { return productionProcessId; }
        public String coldScheduledKind() { return coldScheduledKind; }
        public SceneCauseKind hotSceneCause() { return hotSceneCause; }
        public ContractState contractState() { return contractState; }

        /**
         * Stable internal SDK declaration.  These are current source vocabulary identifiers,
         * not a second payload registry: the production codec registry remains the authority.
         */
        public FrontierProcessSceneSdk.DescriptorDefinition definition(DeterministicProcessDescriptor production) {
            if (!productionProcessId.equals(Objects.requireNonNull(production, "production process descriptor").id())) {
                throw new IllegalArgumentException("process/scene descriptor bound to wrong production owner: " + this);
            }
            return new FrontierProcessSceneSdk.DescriptorDefinition(sdkFamilyKey, 1, archetype, canonicalOwner,
                    new FrontierProcessSceneSdk.Vocabulary(production.commandPayloadTypes(), production.reducedEventTypes(),
                            production.codecTypes(), production.emittedPayloadTypes()), binding(production), limitsFor(this));
        }

        /** Current production declaration; test adapters never construct a parallel vocabulary. */
        public FrontierProcessSceneSdk.DescriptorDefinition definition() {
            return currentDefinition(this);
        }

        private FrontierProcessSceneSdk.ExecutionBinding binding(DeterministicProcessDescriptor production) {
            String owner = "process:" + production.id();
            String scene = hotSceneCause == null ? owner : "scene:" + hotSceneCause.name().toLowerCase(java.util.Locale.ROOT);
            return switch (archetype) {
                case DURATION_WORK, COORDINATED_TRAVERSAL -> FrontierProcessSceneSdk.ExecutionBinding.paired("schedule:" + coldScheduledKind, scene);
                case COMPOSITE_OPERATION -> FrontierProcessSceneSdk.ExecutionBinding.composite(owner, scene);
                case ATOMIC_INTENT -> FrontierProcessSceneSdk.ExecutionBinding.intent(owner, "process:physical-observation");
                case SPATIAL_FRONTIER -> FrontierProcessSceneSdk.ExecutionBinding.frontier(owner);
                case AMBIENT_CUSTODY -> FrontierProcessSceneSdk.ExecutionBinding.custody(owner);
            };
        }
    }

    public enum ContractState { ENFORCED, DEFERRED }

    /**
     * Exact bounded work accounting supplied by a registered scene behavior.  The descriptor
     * limits are therefore live admission/tick fences rather than descriptive capacities.  A
     * behavior cannot claim a smaller actor/local-space footprint than its retained lease.
     */
    public record SceneWorkUsage(int actors, int cargo, int effects, int localChunks, int navigationNodes,
                                 int observations, int retainedRecords, int retainedBytes, int cadenceTicks, int workWeight) {
        public SceneWorkUsage {
            for (int value : new int[] { actors, cargo, effects, localChunks, navigationNodes, observations, retainedRecords,
                    retainedBytes, cadenceTicks, workWeight }) if (value < 0) throw new IllegalArgumentException("scene work usage cannot be negative");
        }

        public static SceneWorkUsage forLease(SceneLease lease, int cargo, int effects) {
            Objects.requireNonNull(lease, "scene work lease");
            Set<String> chunks = new java.util.HashSet<>(); chunks.add(chunkKey(lease.handoffPosition()));
            lease.memberPositions().values().forEach(position -> chunks.add((position.x() >> 4) + ":" + (position.z() >> 4)));
            int actors = lease.members().size();
            return new SceneWorkUsage(actors, cargo, effects, chunks.size(), actors, 0, actors + 1,
                    Math.addExact(128, Math.multiplyExact(actors, 128)), 1, Math.addExact(actors, Math.addExact(cargo, effects)));
        }

        public SceneWorkUsage withObservations(int nextObservations) {
            return new SceneWorkUsage(actors, cargo, effects, localChunks, navigationNodes, nextObservations, retainedRecords,
                    retainedBytes, cadenceTicks, workWeight);
        }
    }

    /*
     * The SDK is an execution fence, so hot paths must not reconstruct a catalog or descriptor
     * on every Minecraft tick.  These values are built once from the same closed production
     * catalog which runtime composition validates below; callers that need to validate a proposed
     * alternate catalog use the explicit-argument methods instead.
     */
    private static final List<DeterministicProcessDescriptor> CURRENT_PRODUCTION = FrontierWorldProcessCatalog.descriptors();
    private static final Map<String, DeterministicProcessDescriptor> CURRENT_PRODUCTION_BY_ID = productionById(CURRENT_PRODUCTION);
    private static final Map<Family, FrontierProcessSceneSdk.DescriptorDefinition> CURRENT_DEFINITIONS = currentDefinitions();
    private static final SceneAdmissionRegistry CURRENT_SCENE_ADMISSIONS = new SceneAdmissionRegistry(List.of(
            SceneAdmissionBinding.fixed(SceneCauseKind.SETTLEMENT_ASSAULT, Family.SETTLEMENT_ASSAULT, 0, 1),
            SceneAdmissionBinding.fixed(SceneCauseKind.ENGINEERING_WORKSITE, Family.ENGINEERING_WORKSITE, 0, 0),
            SceneAdmissionBinding.fixed(SceneCauseKind.MEDICAL_TREATMENT, Family.MEDICAL_TREATMENT, 0, 0),
            SceneAdmissionBinding.fixed(SceneCauseKind.RESOURCE_SITE_HARVEST, Family.RESOURCE_SITE_HARVEST, 0, 0),
            SceneAdmissionBinding.fixed(SceneCauseKind.PRODUCTION_WORK, Family.PRODUCTION_WORK, 0, 0),
            SceneAdmissionBinding.fixed(SceneCauseKind.SERVICE_WORK, Family.SETTLEMENT_SERVICE_WORK, 0, 0),
            SceneAdmissionBinding.fixed(SceneCauseKind.ROUTE_PATROL, Family.ROUTE_PATROL, 0, 0),
            SceneAdmissionBinding.logistics()));

    public sealed interface Driver permits ColdDriver, HotDriver { Family family(); }

    public record ColdDriver(Family family, String scheduledKind) implements Driver {
        public ColdDriver {
            family = Objects.requireNonNull(family, "duration COLD family");
            if (scheduledKind == null || scheduledKind.isBlank()) throw new IllegalArgumentException("duration COLD scheduled kind");
        }
    }

    public record HotDriver(Family family, SceneCauseKind sceneCause) implements Driver {
        public HotDriver {
            family = Objects.requireNonNull(family, "duration HOT family");
            sceneCause = Objects.requireNonNull(sceneCause, "duration HOT scene cause");
        }
    }

    /** The only F0.1 pair admitted into the active paired-driver contract. */
    public static List<Driver> currentRegistrations() {
        return List.of(new ColdDriver(Family.RESOURCE_SITE_HARVEST, ResourceSiteHarvestProcess.COLD_PROGRESS_KIND),
                new HotDriver(Family.RESOURCE_SITE_HARVEST, SceneCauseKind.RESOURCE_SITE_HARVEST));
    }

    /**
     * Invoked by the world composition root after schedules, scene kinds and the installed
     * persistence codecs exist.  A descriptor vocabulary is not merely documentation: every
     * named durable payload must resolve through the exact production codec registry before a
     * world can execute or recover.
     */
    public static void requireCurrentComposition(List<DeterministicProcessDescriptor> productionDescriptors,
                                                 Set<String> scheduledKinds, Set<SceneCauseKind> sceneCauses,
                                                 Set<String> installedPayloadCodecs) {
        productionDescriptors = List.copyOf(Objects.requireNonNull(productionDescriptors, "production process descriptors"));
        compose(currentRegistrations(), scheduledKinds, sceneCauses);
        requireDescriptorComposition(inventoryDefinitions(productionDescriptors), productionDescriptors, scheduledKinds,
                sceneCauses, installedPayloadCodecs);
        CURRENT_SCENE_ADMISSIONS.requireRegisteredProviders(sceneCauses);
    }

    /**
     * Pure deterministic validator retained for negative composition tests.  It fails closed for
     * missing, duplicate, mismatched or unregistered driver ownership before runtime admission.
     */
    public static void compose(List<? extends Driver> registrations, Set<String> scheduledKinds,
                               Set<SceneCauseKind> sceneCauses) {
        Objects.requireNonNull(registrations, "duration driver registrations");
        Objects.requireNonNull(scheduledKinds, "scheduled kinds");
        Objects.requireNonNull(sceneCauses, "scene causes");
        EnumMap<Family, List<ColdDriver>> cold = new EnumMap<>(Family.class);
        EnumMap<Family, List<HotDriver>> hot = new EnumMap<>(Family.class);
        for (Driver registration : List.copyOf(registrations)) {
            Objects.requireNonNull(registration, "duration driver registration");
            if (registration.family().contractState() != ContractState.ENFORCED) {
                throw new IllegalArgumentException("duration family is not yet eligible for paired-driver registration: " + registration.family());
            }
            if (registration instanceof ColdDriver driver) cold.computeIfAbsent(driver.family(), ignored -> new ArrayList<>()).add(driver);
            else if (registration instanceof HotDriver driver) hot.computeIfAbsent(driver.family(), ignored -> new ArrayList<>()).add(driver);
            else throw new IllegalArgumentException("unknown duration driver registration: " + registration.getClass().getName());
        }
        for (Family family : Family.values()) {
            if (family.contractState() != ContractState.ENFORCED) continue;
            List<ColdDriver> coldDrivers = cold.getOrDefault(family, List.of());
            List<HotDriver> hotDrivers = hot.getOrDefault(family, List.of());
            if (coldDrivers.size() != 1 || hotDrivers.size() != 1) {
                throw new IllegalArgumentException("duration process requires exactly one COLD and one HOT driver: " + family
                        + " cold=" + coldDrivers.size() + " hot=" + hotDrivers.size());
            }
            ColdDriver coldDriver = coldDrivers.getFirst(); HotDriver hotDriver = hotDrivers.getFirst();
            if (!coldDriver.scheduledKind().equals(family.coldScheduledKind()) || !scheduledKinds.contains(coldDriver.scheduledKind())) {
                throw new IllegalArgumentException("duration COLD driver is not the registered canonical schedule: " + family);
            }
            if (hotDriver.sceneCause() != family.hotSceneCause() || !sceneCauses.contains(hotDriver.sceneCause())) {
                throw new IllegalArgumentException("duration HOT driver is not the registered process-specific scene: " + family);
            }
        }
    }

    /** Machine-readable complete inventory, including deferred F0 conversion owners. */
    public static Set<Family> inventory() { return EnumSet.allOf(Family.class); }

    /** Closed production inventory used before runtime composition and SDK recovery/execution. */
    public static List<FrontierProcessSceneSdk.DescriptorDefinition> inventoryDefinitions() {
        return CURRENT_DEFINITIONS.values().stream()
                .sorted(java.util.Comparator.comparing(value -> value.family().value())).toList();
    }

    /**
     * Builds declarations directly from the closed production process registry.  There is no
     * representative payload or parallel vocabulary: changing a command, reducer event, emitted
     * lifecycle fact or codec changes this retained descriptor fence.
     */
    public static List<FrontierProcessSceneSdk.DescriptorDefinition> inventoryDefinitions(List<DeterministicProcessDescriptor> productionDescriptors) {
        Map<String, DeterministicProcessDescriptor> byId = productionById(productionDescriptors);
        return inventory().stream().map(family -> family.definition(requireProduction(family, byId)))
                .sorted(java.util.Comparator.comparing(value -> value.family().value())).toList();
    }

    /** Stable snapshot/recovery fence for the complete descriptor inventory. */
    public static String inventoryFingerprint() {
        String material = inventoryDefinitions().stream().map(FrontierProcessSceneSdk.DescriptorDefinition::canonicalFingerprintMaterial)
                .collect(java.util.stream.Collectors.joining("\n"));
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(material.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("JVM lacks required SHA-256 for process/scene descriptor inventory", unavailable);
        }
    }

    /**
     * Rejects all descriptor drift before a process can be recovered or dispatched: unknown
     * family/version, duplicated owner registration, a wrong archetype/vocabulary/binding, or
     * an adapter that omitted a declared hard bound are all fatal composition errors.
     */
    public static void requireDescriptorComposition(List<FrontierProcessSceneSdk.DescriptorDefinition> declarations,
                                                    List<DeterministicProcessDescriptor> productionDescriptors,
                                                    Set<String> scheduledKinds, Set<SceneCauseKind> sceneCauses,
                                                    Set<String> installedPayloadCodecs) {
        Objects.requireNonNull(declarations, "process/scene descriptor declarations");
        productionDescriptors = List.copyOf(Objects.requireNonNull(productionDescriptors, "production process descriptors"));
        scheduledKinds = Set.copyOf(Objects.requireNonNull(scheduledKinds, "production scheduled kinds"));
        sceneCauses = Set.copyOf(Objects.requireNonNull(sceneCauses, "production scene providers"));
        installedPayloadCodecs = Set.copyOf(Objects.requireNonNull(installedPayloadCodecs, "installed process payload codecs"));
        Map<String, DeterministicProcessDescriptor> productionById = productionById(productionDescriptors);
        Map<FrontierProcessSceneSdk.FamilyKey, FrontierProcessSceneSdk.DescriptorDefinition> expected = inventoryDefinitions(productionDescriptors).stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(FrontierProcessSceneSdk.DescriptorDefinition::family, value -> value));
        Map<FrontierProcessSceneSdk.FamilyKey, FrontierProcessSceneSdk.DescriptorDefinition> actual = new java.util.HashMap<>();
        for (FrontierProcessSceneSdk.DescriptorDefinition declaration : List.copyOf(declarations)) {
            if (declaration == null) throw new IllegalArgumentException("missing process/scene descriptor");
            if (actual.putIfAbsent(declaration.family(), declaration) != null) {
                throw new IllegalArgumentException("duplicate process/scene descriptor: " + declaration.family().value());
            }
            FrontierProcessSceneSdk.DescriptorDefinition reference = expected.get(declaration.family());
            if (reference == null) throw new IllegalArgumentException("unknown process/scene descriptor family: " + declaration.family().value());
            if (!reference.equals(declaration)) {
                throw new IllegalArgumentException("incompatible process/scene descriptor: " + declaration.family().value());
            }
        }
        if (!actual.keySet().equals(expected.keySet())) {
            Set<FrontierProcessSceneSdk.FamilyKey> missing = new java.util.HashSet<>(expected.keySet());
            missing.removeAll(actual.keySet());
            throw new IllegalArgumentException("missing process/scene descriptor families: " + missing);
        }
        for (FrontierProcessSceneSdk.DescriptorDefinition declaration : actual.values()) {
            Set<String> missingCodecs = new java.util.TreeSet<>(declaration.vocabulary().payloadCodecs());
            missingCodecs.removeAll(installedPayloadCodecs);
            if (!missingCodecs.isEmpty()) {
                throw new IllegalArgumentException("process/scene descriptor has no installed production codec: "
                        + declaration.family().value() + " missing=" + missingCodecs);
            }
        }
        for (Family family : inventory()) validateProductionBinding(family, requireProduction(family, productionById), productionById,
                scheduledKinds, sceneCauses);
    }

    /** Admission boundary shared by every registered scene behavior. */
    public static void requireSceneAdmission(Family family, SceneLease lease) {
        requireSceneAdmission(family, lease, SceneWorkUsage.forLease(lease, 0, 0));
    }

    /**
     * Generic command/recovery admission. The model supplies a typed lease capability while this
     * closed process registry selects its descriptor and exact bounded usage. No model class
     * imports the SDK or decides a process-family budget.
     */
    public static void requireSceneAdmission(SceneLease lease) {
        CURRENT_SCENE_ADMISSIONS.requireAdmission(lease);
    }

    /** Every retained lease is re-fenced after snapshot hydration before it can execute. */
    public static void requireRetainedSceneLeases(Iterable<SceneLease> leases) {
        Objects.requireNonNull(leases, "retained scene leases");
        for (SceneLease lease : leases) requireSceneAdmission(Objects.requireNonNull(lease, "retained scene lease"));
    }

    /** Registered behavior passes exact retained-scene usage; every declared bound is enforced. */
    public static void requireSceneAdmission(Family family, SceneLease lease, SceneWorkUsage usage) {
        Objects.requireNonNull(family, "process/scene family"); Objects.requireNonNull(lease, "scene lease"); Objects.requireNonNull(usage, "scene work usage");
        requireSceneProvider(family, lease);
        FrontierProcessSceneSdk.Limits limits = currentDefinition(family).limits();
        if (lease.members().isEmpty() || usage.actors() != lease.members().size() || usage.actors() > limits.maxActors()) {
            throw new IllegalArgumentException("scene actor bound exceeded for " + family);
        }
        Set<String> localChunks = new java.util.HashSet<>();
        localChunks.add(chunkKey(lease.handoffPosition()));
        lease.memberPositions().values().forEach(position -> localChunks.add((position.x() >> 4) + ":" + (position.z() >> 4)));
        if (usage.localChunks() != localChunks.size()) throw new IllegalArgumentException("scene local-space usage does not match retained lease for " + family);
        requireBound(usage.cargo(), limits.maxCargo(), "cargo", family);
        requireBound(usage.effects(), limits.maxEffects(), "effects", family);
        requireBound(usage.localChunks(), limits.maxLocalChunks(), "local chunks", family);
        requireBound(usage.navigationNodes(), limits.maxNavigationNodes(), "navigation nodes", family);
        requireBound(usage.observations(), limits.maxObservationsPerTick(), "observations", family);
        requireBound(usage.retainedRecords(), limits.maxRetainedRecords(), "retained records", family);
        requireBound(usage.retainedBytes(), limits.maxRetainedBytes(), "retained bytes", family);
        if (usage.cadenceTicks() < 1 || usage.cadenceTicks() > limits.cadenceTicks()) throw new IllegalArgumentException("scene cadence bound exceeded for " + family);
        requireBound(usage.workWeight(), limits.maxWorkWeight(), "work weight", family);
    }

    /** Tick boundary: the provider has one bounded observation turn and cannot bypass admission. */
    public static void requireSceneTick(Family family, SceneLease lease, int observations) {
        requireSceneTick(family, lease, SceneWorkUsage.forLease(lease, 0, 0).withObservations(observations));
    }

    /** The same registry binding fences every HOT observation turn after durable admission. */
    public static void requireSceneTick(SceneLease lease, int observations) {
        CURRENT_SCENE_ADMISSIONS.requireTick(lease, observations);
    }

    /** Tick boundary repeats the exact admission ledger; a provider cannot bypass it after HOT acquisition. */
    public static void requireSceneTick(Family family, SceneLease lease, SceneWorkUsage usage) {
        requireSceneAdmission(family, lease, usage);
    }

    /** Closed stable-key lookup used by the internal process/scene SDK composition checks. */
    public static Family requireFamily(FrontierProcessSceneSdk.FamilyKey key) {
        return inventory().stream().filter(family -> family.sdkFamilyKey().equals(Objects.requireNonNull(key, "process/scene family key")))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("unknown duration process family: " + key.value()));
    }

    /**
     * NeoForge's actual closed behavior registry calls this after it has assembled its providers.
     * An enum existing in core is not sufficient: a missing physical behavior must make startup
     * fail before a descriptor can be admitted or ticked.
     */
    public static void requirePhysicalSceneProviders(Set<SceneCauseKind> registeredProviders) {
        registeredProviders = Set.copyOf(Objects.requireNonNull(registeredProviders, "registered scene providers"));
        CURRENT_SCENE_ADMISSIONS.requireRegisteredProviders(registeredProviders);
    }

    /**
     * Closed process-side bridge from a typed scene cause to one descriptor and its live usage.
     * This is deliberately not part of {@code FrontierSceneBehaviors}: canonical model behavior
     * owns semantic lease validity, while SDK family/budget selection belongs to process
     * composition and must remain absent from the model package.
     */
    private record SceneAdmissionBinding(SceneCauseKind kind, Set<Family> families,
                                         Function<SceneLease, Family> familyResolver,
                                         Function<SceneLease, SceneWorkUsage> usageResolver) {
        private SceneAdmissionBinding {
            kind = Objects.requireNonNull(kind, "scene admission cause kind");
            families = Set.copyOf(Objects.requireNonNull(families, "scene admission families"));
            familyResolver = Objects.requireNonNull(familyResolver, "scene admission family resolver");
            usageResolver = Objects.requireNonNull(usageResolver, "scene admission usage resolver");
            if (families.isEmpty()) throw new IllegalArgumentException("scene admission binding needs a family");
            for (Family family : families) {
                if (family.hotSceneCause() != kind) {
                    throw new IllegalArgumentException("scene admission binding family has another scene cause: " + family);
                }
            }
        }

        static SceneAdmissionBinding fixed(SceneCauseKind kind, Family family, int cargo, int effects) {
            if (cargo < 0 || effects < 0) throw new IllegalArgumentException("scene admission fixed usage cannot be negative");
            return new SceneAdmissionBinding(kind, Set.of(family), lease -> family,
                    lease -> SceneWorkUsage.forLease(lease, cargo, effects));
        }

        static SceneAdmissionBinding logistics() {
            return new SceneAdmissionBinding(SceneCauseKind.LOGISTICS, Set.of(Family.ROUTE_OPERATION, Family.ROUTE_ENGAGEMENT),
                    lease -> logisticsCause(lease).engagementId().isPresent() ? Family.ROUTE_ENGAGEMENT : Family.ROUTE_OPERATION,
                    lease -> SceneWorkUsage.forLease(lease, 1, logisticsCause(lease).engagementId().isPresent() ? 1 : 0));
        }

        Family resolveFamily(SceneLease lease) {
            requireKind(lease);
            Family family = Objects.requireNonNull(familyResolver.apply(lease), "scene admission resolved family");
            if (!families.contains(family)) throw new IllegalArgumentException("scene admission resolved an undeclared process family: " + family);
            return family;
        }

        SceneWorkUsage resolveUsage(SceneLease lease) {
            requireKind(lease);
            return Objects.requireNonNull(usageResolver.apply(lease), "scene admission resolved usage");
        }

        private void requireKind(SceneLease lease) {
            if (Objects.requireNonNull(lease, "scene admission lease").cause().kind() != kind) {
                throw new IllegalArgumentException("scene admission binding does not match lease cause: " + kind);
            }
        }

        private static LogisticsSceneCause logisticsCause(SceneLease lease) {
            return FrontierSceneBehaviors.logistics(Objects.requireNonNull(lease, "logistics scene lease"));
        }
    }

    private static final class SceneAdmissionRegistry {
        private final Map<SceneCauseKind, SceneAdmissionBinding> bindings;

        private SceneAdmissionRegistry(List<SceneAdmissionBinding> registrations) {
            EnumMap<SceneCauseKind, SceneAdmissionBinding> collected = new EnumMap<>(SceneCauseKind.class);
            for (SceneAdmissionBinding registration : List.copyOf(Objects.requireNonNull(registrations, "scene admission registrations"))) {
                if (collected.putIfAbsent(Objects.requireNonNull(registration, "scene admission registration").kind(), registration) != null) {
                    throw new IllegalArgumentException("duplicate process/scene admission binding: " + registration.kind());
                }
            }
            Set<SceneCauseKind> missingKinds = EnumSet.allOf(SceneCauseKind.class);
            missingKinds.removeAll(collected.keySet());
            if (!missingKinds.isEmpty()) throw new IllegalArgumentException("missing process/scene admission bindings: " + missingKinds);
            Set<Family> expectedFamilies = EnumSet.noneOf(Family.class);
            for (Family family : inventory()) if (family.hotSceneCause() != null) expectedFamilies.add(family);
            Set<Family> registeredFamilies = EnumSet.noneOf(Family.class);
            for (SceneAdmissionBinding binding : collected.values()) {
                for (Family family : binding.families()) {
                    if (!registeredFamilies.add(family)) throw new IllegalArgumentException("duplicate process/scene admission family: " + family);
                }
            }
            if (!registeredFamilies.equals(expectedFamilies)) {
                throw new IllegalArgumentException("process/scene admission family coverage differs from descriptors");
            }
            this.bindings = Map.copyOf(collected);
        }

        void requireAdmission(SceneLease lease) {
            SceneAdmissionBinding binding = binding(lease);
            requireSceneAdmission(binding.resolveFamily(lease), lease, binding.resolveUsage(lease));
        }

        void requireTick(SceneLease lease, int observations) {
            if (observations < 0) throw new IllegalArgumentException("scene observation count cannot be negative");
            SceneAdmissionBinding binding = binding(lease);
            requireSceneTick(binding.resolveFamily(lease), lease, binding.resolveUsage(lease).withObservations(observations));
        }

        void requireRegisteredProviders(Set<SceneCauseKind> providers) {
            Set<SceneCauseKind> missing = EnumSet.copyOf(bindings.keySet());
            missing.removeAll(providers);
            if (!missing.isEmpty()) throw new IllegalArgumentException("process/scene descriptor has no registered physical provider: " + missing);
        }

        private SceneAdmissionBinding binding(SceneLease lease) {
            SceneCauseKind kind = Objects.requireNonNull(lease, "scene admission lease").cause().kind();
            SceneAdmissionBinding binding = bindings.get(kind);
            if (binding == null) throw new IllegalArgumentException("scene lease has no process/scene admission binding: " + kind);
            return binding;
        }
    }

    /**
     * A generic caller cannot select an arbitrary descriptor for a lease just to inherit its
     * limits.  This admission/tick fence binds a HOT lease to the exact provider recorded in
     * the production descriptor.  A family without a HOT provider is never physically
     * admissible.
     */
    private static void requireSceneProvider(Family family, SceneLease lease) {
        SceneCauseKind expected = family.hotSceneCause();
        if (expected == null || lease.cause().kind() != expected) {
            throw new IllegalArgumentException("scene lease does not match registered process/scene provider: " + family);
        }
        FrontierProcessSceneSdk.DescriptorDefinition descriptor = currentDefinition(family);
        String expectedProvider = "scene:" + expected.name().toLowerCase(java.util.Locale.ROOT);
        FrontierProcessSceneSdk.ExecutionPrimitive primitive = switch (descriptor.archetype()) {
            case DURATION_WORK, COORDINATED_TRAVERSAL -> FrontierProcessSceneSdk.ExecutionPrimitive.HOT_DRIVER;
            // A composite may open a bounded physical child front, but must not pretend that the
            // parent itself is a duration worker.  Its retained child-front provider is the
            // exact production scene binding.
            case COMPOSITE_OPERATION -> FrontierProcessSceneSdk.ExecutionPrimitive.CHILD_FRONTS;
            case ATOMIC_INTENT, SPATIAL_FRONTIER, AMBIENT_CUSTODY -> throw new IllegalArgumentException(
                    "non-scene process family cannot admit a HOT scene: " + family);
        };
        String boundProvider = descriptor.binding().providers().get(primitive);
        if (!expectedProvider.equals(boundProvider)) {
            throw new IllegalStateException("process/scene descriptor HOT provider drift: " + family);
        }
    }

    private static Map<String, DeterministicProcessDescriptor> productionById(List<DeterministicProcessDescriptor> productionDescriptors) {
        Map<String, DeterministicProcessDescriptor> byId = new java.util.LinkedHashMap<>();
        for (DeterministicProcessDescriptor descriptor : List.copyOf(productionDescriptors)) {
            if (descriptor == null) throw new IllegalArgumentException("missing production process descriptor");
            if (byId.putIfAbsent(descriptor.id(), descriptor) != null) throw new IllegalArgumentException("duplicate production process descriptor: " + descriptor.id());
        }
        return Map.copyOf(byId);
    }

    private static DeterministicProcessDescriptor requireProduction(Family family, Map<String, DeterministicProcessDescriptor> byId) {
        DeterministicProcessDescriptor descriptor = byId.get(family.productionProcessId());
        if (descriptor == null) throw new IllegalArgumentException("missing production owner for process/scene family: " + family);
        return descriptor;
    }

    private static void validateProductionBinding(Family family, DeterministicProcessDescriptor production,
                                                  Map<String, DeterministicProcessDescriptor> productionById,
                                                  Set<String> scheduledKinds, Set<SceneCauseKind> sceneCauses) {
        if (family.archetype() == FrontierProcessSceneSdk.ExecutionArchetype.DURATION_WORK
                || family.archetype() == FrontierProcessSceneSdk.ExecutionArchetype.COORDINATED_TRAVERSAL) {
            if (family.coldScheduledKind() == null || !production.scheduledKinds().contains(family.coldScheduledKind())
                    || !scheduledKinds.contains(family.coldScheduledKind())) {
                throw new IllegalArgumentException("process/scene COLD provider is not registered by its production owner: " + family);
            }
        }
        if (family.hotSceneCause() != null && !sceneCauses.contains(family.hotSceneCause())) {
            throw new IllegalArgumentException("process/scene HOT provider is not registered: " + family);
        }
        if (family.archetype() == FrontierProcessSceneSdk.ExecutionArchetype.ATOMIC_INTENT
                && !productionById.containsKey("physical-observation")) {
            throw new IllegalArgumentException("atomic process has no registered physical postcondition provider: " + family);
        }
    }

    private static Map<Family, FrontierProcessSceneSdk.DescriptorDefinition> currentDefinitions() {
        EnumMap<Family, FrontierProcessSceneSdk.DescriptorDefinition> values = new EnumMap<>(Family.class);
        for (Family family : inventory()) values.put(family, family.definition(requireProduction(family, CURRENT_PRODUCTION_BY_ID)));
        return Map.copyOf(values);
    }

    private static FrontierProcessSceneSdk.DescriptorDefinition currentDefinition(Family family) {
        FrontierProcessSceneSdk.DescriptorDefinition definition = CURRENT_DEFINITIONS.get(Objects.requireNonNull(family, "process/scene family"));
        if (definition == null) throw new IllegalStateException("missing current process/scene descriptor: " + family);
        return definition;
    }

    private static String chunkKey(BlockPosition position) { return (position.x() >> 4) + ":" + (position.z() >> 4); }

    private static void requireBound(int value, int maximum, String name, Family family) {
        if (value > maximum) throw new IllegalArgumentException("scene " + name + " bound exceeded for " + family);
    }

    /**
     * Stable limits are deliberately declared per canonical family, not inferred from a broad
     * execution archetype.  Families may share a value today, but changing one is a retained
     * descriptor change (and therefore changes the snapshot fingerprint) rather than silently
     * raising every process of the same shape.
     */
    private static FrontierProcessSceneSdk.Limits limitsFor(Family family) {
        return switch (family) {
            case RESOURCE_SITE_HARVEST -> new FrontierProcessSceneSdk.Limits(1, 1, 1, 4, 1_024, 8, 512, 131_072, 20, 16);
            case PRODUCTION_WORK, SETTLEMENT_SERVICE_WORK, MEDICAL_TREATMENT, ENGINEERING_WORKSITE,
                    HIVE_NUTRIENT_TRANSFER -> new FrontierProcessSceneSdk.Limits(16, 32, 16, 16, 1_024, 32,
                    1_024, 262_144, 2_400, 256);
            case ROUTE_OPERATION, ROUTE_PATROL, POPULATION_MIGRATION -> new FrontierProcessSceneSdk.Limits(64, 32,
                    16, 32, 4_096, 64, 2_048, 524_288, 2_400, 512);
            case HIVE_MOBILIZATION, ROUTE_ENGAGEMENT, SETTLEMENT_ASSAULT -> new FrontierProcessSceneSdk.Limits(128,
                    64, 64, 64, 4_096, 128, 4_096, 1_048_576, 2_400, 1_024);
            case SETTLEMENT_PROVISION, RESOURCE_SITE_PREPARATION -> new FrontierProcessSceneSdk.Limits(8, 64, 8,
                    16, 64, 16, 512, 131_072, 2_400, 128);
            case HIVE_GROWTH -> new FrontierProcessSceneSdk.Limits(32, 32, 256, 64, 4_096, 128, 4_096,
                    1_048_576, 2_400, 1_024);
            case AMBIENT_ACTOR_CUSTODY -> new FrontierProcessSceneSdk.Limits(128, 8, 8, 16, 256, 32, 1_024,
                    262_144, 2_400, 128);
        };
    }
}
