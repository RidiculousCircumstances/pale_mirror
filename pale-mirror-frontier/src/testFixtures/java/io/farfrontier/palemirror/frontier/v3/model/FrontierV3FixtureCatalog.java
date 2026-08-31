package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.process.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;

import io.farfrontier.palemirror.frontier.v3.api.FixedPosition;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalPostcondition;
import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.EngineLimits;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;
import io.farfrontier.palemirror.frontier.v3.kernel.TransactionCommitter;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.function.BiFunction;

/**
 * The sole catalog for deterministic v3 test fixtures.  It is compiled only into the
 * test-fixtures artifact: neither a production server nor its packaged mod can discover,
 * select or link one of these profiles.
 */
public final class FrontierV3FixtureCatalog {
    private static final String RESOURCE = "frontier-v3-pilot-profiles.properties";
    /** Test-only catalog: an alternate immutable ruleset must be named here and in the profile file. */
    private static final Map<String, FrontierRuleset> RULESETS = Map.of("production", FrontierRulesets.production());
    private static final Map<String, BiFunction<WorldId, Long, FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection>>> PROVIDERS = Map.ofEntries(
            Map.entry("world", FrontierWorldRuntimeDefinition::configuration),
            Map.entry("uncontestedSupply", FrontierV3FixtureCatalog::uncontestedSupplyConfiguration),
            Map.entry("autonomousSupplyInterception", FrontierV3FixtureCatalog::autonomousSupplyInterceptionConfiguration),
            Map.entry("hotSceneStrike", FrontierV3FixtureCatalog::hotSceneStrikeConfiguration),
            Map.entry("settlementAssault", FrontierV3FixtureCatalog::settlementAssaultConfiguration),
            Map.entry("hiveGrowth", FrontierV3FixtureCatalog::hiveGrowthConfiguration),
            Map.entry("hiveNutrientTransfer", FrontierV3FixtureCatalog::hiveNutrientTransferConfiguration),
            Map.entry("settlementProvision", FrontierV3FixtureCatalog::settlementProvisionConfiguration),
            Map.entry("routeSceneReturn", FrontierV3FixtureCatalog::routeSceneReturnConfiguration),
            Map.entry("hotScoutSighting", FrontierV3FixtureCatalog::hotScoutSightingConfiguration),
            Map.entry("hotScoutIntercept", FrontierV3FixtureCatalog::hotScoutInterceptConfiguration),
            Map.entry("operationAssembly", FrontierV3FixtureCatalog::operationAssemblyConfiguration),
            Map.entry("healthQuarantine", FrontierV3FixtureCatalog::healthQuarantineConfiguration),
            Map.entry("residentTransit", FrontierV3FixtureCatalog::residentTransitConfiguration),
            Map.entry("productionInputTheft", FrontierV3FixtureCatalog::productionInputTheftConfiguration),
            Map.entry("productionWorkerDeath", FrontierV3FixtureCatalog::productionWorkerDeathConfiguration));
    private static final Catalog CATALOG = loadCatalog();
    public static final String DEFAULT_PROFILE = CATALOG.defaultProfile();
    private static final Map<String, Profile> PROFILES = CATALOG.profiles();

    private FrontierV3FixtureCatalog() { }

    public static FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> configuration(String profileId, WorldId worldId, long seed) {
        Objects.requireNonNull(profileId, "profileId");
        Objects.requireNonNull(worldId, "worldId");
        Profile profile = PROFILES.get(profileId);
        if (profile == null) throw new IllegalArgumentException("unknown Frontier v3 test fixture profile: " + profileId);
        FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> configuration = profile.factory().apply(worldId, seed);
        FrontierRuleset expected = RULESETS.get(profile.rulesetId());
        if (expected == null || !expected.equals(configuration.initialState().bootstrap().ruleset())) {
            throw new IllegalStateException("fixture profile ruleset is undeclared or does not match its canonical bootstrap: " + profile.id());
        }
        return configuration;
    }

    public static Profile profile(String profileId) {
        Profile profile = PROFILES.get(Objects.requireNonNull(profileId, "profileId"));
        if (profile == null) throw new IllegalArgumentException("unknown Frontier v3 test fixture profile: " + profileId);
        return profile;
    }

    public static List<Profile> profiles() { return List.copyOf(PROFILES.values()); }

    public static FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> uncontestedSupplyConfiguration(WorldId worldId, long seed) {
        return withReserve(FrontierWorldRuntimeDefinition.configuration(worldId, seed, false), false);
    }

    public static FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> autonomousSupplyInterceptionConfiguration(WorldId worldId, long seed) {
        return withReserve(FrontierWorldRuntimeDefinition.configuration(worldId, seed, true), true);
    }

    public static FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> hotSceneStrikeConfiguration(WorldId worldId, long seed) {
        FrontierWorldState initial = FrontierDevelopmentScenarios.hotSceneStrikeState(worldId, seed);
        return configured(worldId, initial, new SimInstant(2_600L), List.of(), true);
    }

    public static FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> settlementAssaultConfiguration(WorldId worldId, long seed) {
        FrontierDevelopmentScenarios.SettlementAssaultFixture fixture = FrontierDevelopmentScenarios.settlementAssaultFixture(worldId, seed);
        return configured(worldId, fixture.state(), fixture.instant(), fixture.schedules(), true);
    }

    public static FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> hiveGrowthConfiguration(WorldId worldId, long seed) {
        FrontierDevelopmentScenarios.HiveGrowthFixture fixture = FrontierDevelopmentScenarios.hiveGrowthFixture(worldId, seed);
        return configured(worldId, fixture.state(), fixture.instant(), fixture.schedules(), false);
    }

    public static FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> hiveNutrientTransferConfiguration(WorldId worldId, long seed) {
        FrontierDevelopmentScenarios.HiveNutrientTransferFixture fixture = FrontierDevelopmentScenarios.hiveNutrientTransferFixture(worldId, seed);
        return configured(worldId, fixture.state(), fixture.instant(), fixture.schedules(), false);
    }

    public static FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> settlementProvisionConfiguration(WorldId worldId, long seed) {
        FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> base = FrontierWorldRuntimeDefinition.configuration(worldId, seed, false);
        FrontierWorldState state = base.initialState(); Settlement settlement = state.bootstrap().settlements().getFirst(); SubjectId depot = FrontierWorldState.depotId(settlement.id());
        SubjectId bread = new SubjectId("item:provision-fixture-bread"); int rations = settlement.residents().size();
        ExactItemStack stack = new ExactItemStack(bread, settlement.id(), SettlementProvisionProcess.BREAD, 64, new InventoryCustody.ContainerSlot(depot, 1));
        List<SubjectId> recipients = state.humanPopulation().residents().values().stream().filter(resident -> resident.settlementId().equals(settlement.id()))
                .map(ResidentProfile::id).sorted().toList();
        HumanPopulation population = state.humanPopulation();
        for (SubjectId recipient : recipients) population = population.resolveNutrition(recipient, 1, false);
        SettlementProvision provision = SettlementProvision.started(settlement.id(), 2, 0L, rations, recipients,
                List.of(new SettlementRationAllocation(bread, recipients)));
        PhysicalIntent intent = new PhysicalIntent(new PhysicalIntentId("intent:settlement-provision-1-2-0"), PhysicalIntentKind.EXACT_ITEM_CONSUMPTION,
                PhysicalIntentStatus.PREPARED, settlement.id(), List.of(settlement.id(), bread), new FixedPosition(FixedScalar.whole(settlement.anchor().x()),
                FixedScalar.whole(settlement.anchor().y()), FixedScalar.whole(settlement.anchor().z())), 0, PhysicalPostcondition.EXACT_ITEM_CONSUMED_OBSERVED);
        state = state.withInventory(state.inventory().store(stack)).withHumanPopulation(population.withProvision(provision.beginPhysical(intent.id())))
                .preparePhysicalIntent(intent);
        return new FrontierEngineConfiguration<>(base.worldId(), state, SimInstant.ZERO, base.commandPlanner(), base.scheduledPlanner(), base.reducer(),
                new FrontierWorldStateCodec(state.bootstrap()), base.projectionMapper(), base.limits(), List.of(), base.transactionCommitter(), base.stateValidator());
    }

    public static FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> routeSceneReturnConfiguration(WorldId worldId, long seed) {
        FrontierDevelopmentScenarios.RouteSceneReturnFixture fixture = FrontierDevelopmentScenarios.routeSceneReturnFixture(worldId, seed);
        return configured(worldId, fixture.state(), fixture.instant(), fixture.schedules(), false);
    }

    public static FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> hotScoutSightingConfiguration(WorldId worldId, long seed) {
        FrontierDevelopmentScenarios.RouteSceneReturnFixture fixture = FrontierDevelopmentScenarios.hotScoutSightingFixture(worldId, seed);
        return configured(worldId, fixture.state(), fixture.instant(), fixture.schedules(), false, true);
    }

    public static FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> hotScoutInterceptConfiguration(WorldId worldId, long seed) {
        FrontierDevelopmentScenarios.RouteSceneReturnFixture fixture = FrontierDevelopmentScenarios.hotScoutInterceptFixture(worldId, seed);
        return configured(worldId, fixture.state(), fixture.instant(), fixture.schedules(), false, true);
    }

    public static FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> operationAssemblyConfiguration(WorldId worldId, long seed) {
        FrontierDevelopmentScenarios.OperationAssemblyFixture fixture = FrontierDevelopmentScenarios.operationAssemblyFixture(worldId, seed);
        return configured(worldId, fixture.state(), fixture.instant(), fixture.schedules(), false);
    }

    public static FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> healthQuarantineConfiguration(WorldId worldId, long seed) {
        FrontierDevelopmentScenarios.HealthQuarantineFixture fixture = FrontierDevelopmentScenarios.healthQuarantineFixture(worldId, seed);
        return configured(worldId, fixture.state(), fixture.instant(), fixture.schedules(), true);
    }

    public static FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> residentTransitConfiguration(WorldId worldId, long seed) {
        FrontierDevelopmentScenarios.ResidentTransitFixture fixture = FrontierDevelopmentScenarios.residentTransitFixture(worldId, seed);
        return configured(worldId, fixture.state(), fixture.instant(), fixture.schedules(), true);
    }

    public static FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> productionInputTheftConfiguration(WorldId worldId, long seed) {
        FrontierDevelopmentScenarios.MaterializedProductionFixture fixture = FrontierDevelopmentScenarios.materializedProductionInputTheftFixture(worldId, seed);
        return configured(worldId, fixture.state(), fixture.instant(), fixture.schedules(), false);
    }

    public static FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> productionWorkerDeathConfiguration(WorldId worldId, long seed) {
        FrontierDevelopmentScenarios.MaterializedProductionFixture fixture = FrontierDevelopmentScenarios.materializedProductionWorkerDeathFixture(worldId, seed);
        return configured(worldId, fixture.state(), fixture.instant(), fixture.schedules(), false);
    }

    static Map<String, Profile> catalog(Profile... profiles) {
        Map<String, Profile> result = new LinkedHashMap<>();
        for (Profile profile : profiles) {
            if (result.putIfAbsent(profile.id(), profile) != null) throw new IllegalArgumentException("duplicate Frontier v3 test fixture profile: " + profile.id());
        }
        return Collections.unmodifiableMap(result);
    }

    private static Catalog loadCatalog() {
        Properties properties = new Properties();
        try (InputStream stream = FrontierV3FixtureCatalog.class.getResourceAsStream(RESOURCE)) {
            if (stream == null) throw new IllegalStateException("missing Frontier v3 test fixture catalog resource");
            properties.load(stream);
        } catch (IOException failure) {
            throw new IllegalStateException("cannot read Frontier v3 test fixture catalog", failure);
        }
        String listed = required(properties, "profiles");
        List<Profile> profiles = new java.util.ArrayList<>();
        for (String rawId : listed.split(",", -1)) {
            String id = rawId.trim();
            if (id.isEmpty()) throw new IllegalStateException("blank Frontier v3 test fixture profile id");
            String provider = required(properties, id + ".provider");
            BiFunction<WorldId, Long, FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection>> factory = PROVIDERS.get(provider);
            if (factory == null) throw new IllegalStateException("unknown Frontier v3 test fixture provider: " + provider);
            profiles.add(new Profile(id, provider, required(properties, id + ".ruleset"), required(properties, id + ".source"), required(properties, id + ".runner"),
                    required(properties, id + ".assertion"), factory));
        }
        Map<String, Profile> byId = catalog(profiles.toArray(Profile[]::new));
        String defaultProfile = required(properties, "default");
        if (!byId.containsKey(defaultProfile)) throw new IllegalStateException("Frontier v3 test fixture catalog default is not declared: " + defaultProfile);
        return new Catalog(defaultProfile, byId);
    }

    private static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) throw new IllegalStateException("missing Frontier v3 test fixture catalog entry: " + key);
        return value;
    }

    private static FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> withReserve(FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> base,
                                                                                                           boolean autonomousInterception) {
        Settlement settlement = base.initialState().bootstrap().settlements().getFirst(); SubjectId depot = FrontierWorldState.depotId(settlement.id());
        int remaining = SettlementProvisionProcess.reserveRequirement(base.initialState(), settlement.id()); ExactInventory inventory = base.initialState().inventory(); int ordinal = 0;
        while (remaining > 0) {
            int count = Math.min(63, remaining);
            inventory = inventory.store(new ExactItemStack(new SubjectId("item:development-supply-reserve-" + ordinal), settlement.id(), "minecraft:bread", count,
                    new InventoryCustody.ContainerSlot(depot, ordinal + 1)));
            remaining -= count; ordinal++;
        }
        return configured(base.worldId(), base.initialState().withInventory(inventory), base.initialInstant(), base.initialSchedules(), autonomousInterception);
    }

    private static FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> configured(WorldId worldId, FrontierWorldState state,
                                                                                                         SimInstant instant, List<ScheduledAction> schedules,
                                                                                                         boolean autonomousInterception) {
        return configured(worldId, state, instant, schedules, autonomousInterception, false);
    }

    private static FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> configured(WorldId worldId, FrontierWorldState state,
                                                                                                         SimInstant instant, List<ScheduledAction> schedules,
                                                                                                         boolean autonomousInterception, boolean freezeScoutProgress) {
        return new FrontierEngineConfiguration<>(worldId, state, instant, FrontierWorldRuntimeDefinition::planCommand,
                (candidate, action) -> freezeScoutProgress ? frozenScoutSightingProgress(candidate, action)
                        : FrontierWorldRuntimeDefinition.planScheduled(candidate, action, autonomousInterception),
                FrontierWorldRuntimeDefinition::reduce, new FrontierWorldStateCodec(state.bootstrap()), FrontierWorldProjectionCompiler::compile,
                new EngineLimits(4_096, 1_200L, 4_096), schedules, TransactionCommitter.noOp(), FrontierWorldStateTransitionValidator.INSTANCE);
    }

    private static List<ProposedEvent> frozenScoutSightingProgress(FrontierWorldState state, ScheduledAction action) {
        if (action.subject().equals(new SubjectId("operation:supply-1-2")) && action.kind().equals("frontier.operation.progress")) {
            return List.of(new ProposedEvent(action.subject(), new ScheduleEffect.Cancelled(action.id())));
        }
        return FrontierWorldRuntimeDefinition.planScheduled(state, action, false);
    }

    public record Profile(String id, String provider, String rulesetId, String sourceProfile, String allowedRunner, String requiredAssertion,
                          BiFunction<WorldId, Long, FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection>> factory) {
        public Profile {
            if (id == null || id.isBlank() || provider == null || provider.isBlank() || rulesetId == null || rulesetId.isBlank() || sourceProfile == null || sourceProfile.isBlank() || allowedRunner == null || allowedRunner.isBlank()
                    || requiredAssertion == null || requiredAssertion.isBlank() || factory == null) throw new IllegalArgumentException("invalid Frontier v3 test fixture profile");
        }
    }

    private record Catalog(String defaultProfile, Map<String, Profile> profiles) { }
}
