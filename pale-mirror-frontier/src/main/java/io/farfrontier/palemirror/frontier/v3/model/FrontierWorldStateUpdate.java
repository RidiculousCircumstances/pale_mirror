package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FixedRatio;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Typed named replacement set for one immutable {@link FrontierWorldState} transition.
 *
 * <p>The aggregate itself is deliberately the only full-state constructor owner.  Processes
 * name only the sub-aggregates they own here; every omitted component retains the identical
 * previous object. Repeating a component is rejected before the aggregate is constructed,
 * which makes accidental last-write-wins updates impossible. A declared no-op is allowed:
 * planning may correctly revalidate an already-current component without replacing it.</p>
 */
public final class FrontierWorldStateUpdate {
    public enum Component {
        ACTOR_LOCATIONS,
        STRUCTURE_CONDITIONS,
        INFECTION,
        INVENTORY,
        PRODUCTION_JOBS,
        CONTRACTS,
        OPERATIONS,
        LOGISTICS_HISTORY,
        PHYSICAL_INTENTS,
        PHYSICAL_OBSERVATIONS,
        SCENE_LEASES,
        HIVE_COLONY,
        STRUCTURE_DAMAGE,
        PHYSICAL_DELTAS,
        AMBIENT_LEASES,
        ROUTE_CONSTRUCTIONS,
        ROUTE_MAINTENANCES,
        ROUTE_TOPOLOGY,
        STRATEGIC_PLANS,
        HUMAN_POPULATION,
        COMPANIES,
        RESOURCE_SITES
    }

    private final EnumSet<Component> changed = EnumSet.noneOf(Component.class);
    private Map<SubjectId, ActorLocation> actorLocations;
    private Map<SubjectId, StructureCondition> structureConditions;
    private Map<InfectionCell, FixedRatio> infection;
    private ExactInventory inventory;
    private Map<SubjectId, ProductionJob> productionJobs;
    private Map<SubjectId, SupplyContract> contracts;
    private Map<SubjectId, RouteOperation> operations;
    private LogisticsHistory logisticsHistory;
    private Map<PhysicalIntentId, PhysicalIntent> physicalIntents;
    private Map<PhysicalObservationId, PhysicalEffectObservation> physicalObservations;
    private Map<SceneLeaseId, SceneLease> sceneLeases;
    private HiveColony hiveColony;
    private Map<SubjectId, StructureDamage> structureDamage;
    private Map<BlockPosition, PhysicalDelta> physicalDeltas;
    private Map<SubjectId, AmbientActorLease> ambientLeases;
    private Map<SubjectId, RouteConstruction> routeConstructions;
    private Map<SubjectId, RouteMaintenance> routeMaintenances;
    private RouteTopology routeTopology;
    private StrategicPlanState strategicPlans;
    private HumanPopulation humanPopulation;
    private CompanyRegistry companies;
    private ResourceSiteState resourceSites;

    private FrontierWorldStateUpdate() { }

    public static FrontierWorldStateUpdate begin() { return new FrontierWorldStateUpdate(); }

    public Set<Component> changedComponents() { return Set.copyOf(changed); }

    public FrontierWorldStateUpdate actorLocations(Map<SubjectId, ActorLocation> next) {
        mark(Component.ACTOR_LOCATIONS); actorLocations = require(next, "actor locations"); return this;
    }
    public FrontierWorldStateUpdate structureConditions(Map<SubjectId, StructureCondition> next) {
        mark(Component.STRUCTURE_CONDITIONS); structureConditions = require(next, "structure conditions"); return this;
    }
    public FrontierWorldStateUpdate infection(Map<InfectionCell, FixedRatio> next) {
        mark(Component.INFECTION); infection = require(next, "infection"); return this;
    }
    public FrontierWorldStateUpdate inventory(ExactInventory next) {
        mark(Component.INVENTORY); inventory = require(next, "inventory"); return this;
    }
    public FrontierWorldStateUpdate productionJobs(Map<SubjectId, ProductionJob> next) {
        mark(Component.PRODUCTION_JOBS); productionJobs = require(next, "production jobs"); return this;
    }
    public FrontierWorldStateUpdate contracts(Map<SubjectId, SupplyContract> next) {
        mark(Component.CONTRACTS); contracts = require(next, "contracts"); return this;
    }
    public FrontierWorldStateUpdate operations(Map<SubjectId, RouteOperation> next) {
        mark(Component.OPERATIONS); operations = require(next, "operations"); return this;
    }
    public FrontierWorldStateUpdate logisticsHistory(LogisticsHistory next) {
        mark(Component.LOGISTICS_HISTORY); logisticsHistory = require(next, "logistics history"); return this;
    }
    public FrontierWorldStateUpdate physicalIntents(Map<PhysicalIntentId, PhysicalIntent> next) {
        mark(Component.PHYSICAL_INTENTS); physicalIntents = require(next, "physical intents"); return this;
    }
    public FrontierWorldStateUpdate physicalObservations(Map<PhysicalObservationId, PhysicalEffectObservation> next) {
        mark(Component.PHYSICAL_OBSERVATIONS); physicalObservations = require(next, "physical observations"); return this;
    }
    public FrontierWorldStateUpdate sceneLeases(Map<SceneLeaseId, SceneLease> next) {
        mark(Component.SCENE_LEASES); sceneLeases = require(next, "scene leases"); return this;
    }
    public FrontierWorldStateUpdate hiveColony(HiveColony next) {
        mark(Component.HIVE_COLONY); hiveColony = require(next, "hive colony"); return this;
    }
    public FrontierWorldStateUpdate structureDamage(Map<SubjectId, StructureDamage> next) {
        mark(Component.STRUCTURE_DAMAGE); structureDamage = require(next, "structure damage"); return this;
    }
    public FrontierWorldStateUpdate physicalDeltas(Map<BlockPosition, PhysicalDelta> next) {
        mark(Component.PHYSICAL_DELTAS); physicalDeltas = require(next, "physical deltas"); return this;
    }
    public FrontierWorldStateUpdate ambientLeases(Map<SubjectId, AmbientActorLease> next) {
        mark(Component.AMBIENT_LEASES); ambientLeases = require(next, "ambient leases"); return this;
    }
    public FrontierWorldStateUpdate routeConstructions(Map<SubjectId, RouteConstruction> next) {
        mark(Component.ROUTE_CONSTRUCTIONS); routeConstructions = require(next, "route constructions"); return this;
    }
    public FrontierWorldStateUpdate routeMaintenances(Map<SubjectId, RouteMaintenance> next) {
        mark(Component.ROUTE_MAINTENANCES); routeMaintenances = require(next, "route maintenances"); return this;
    }
    public FrontierWorldStateUpdate routeTopology(RouteTopology next) {
        mark(Component.ROUTE_TOPOLOGY); routeTopology = require(next, "route topology"); return this;
    }
    public FrontierWorldStateUpdate strategicPlans(StrategicPlanState next) {
        mark(Component.STRATEGIC_PLANS); strategicPlans = require(next, "strategic plans"); return this;
    }
    public FrontierWorldStateUpdate humanPopulation(HumanPopulation next) {
        mark(Component.HUMAN_POPULATION); humanPopulation = require(next, "human population"); return this;
    }
    public FrontierWorldStateUpdate companies(CompanyRegistry next) {
        mark(Component.COMPANIES); companies = require(next, "companies"); return this;
    }
    public FrontierWorldStateUpdate resourceSites(ResourceSiteState next) {
        mark(Component.RESOURCE_SITES); resourceSites = require(next, "resource sites"); return this;
    }

    Map<SubjectId, ActorLocation> actorLocations(FrontierWorldState state) { return changed(Component.ACTOR_LOCATIONS, actorLocations, state.actorLocations()); }
    Map<SubjectId, StructureCondition> structureConditions(FrontierWorldState state) { return changed(Component.STRUCTURE_CONDITIONS, structureConditions, state.structureConditions()); }
    Map<InfectionCell, FixedRatio> infection(FrontierWorldState state) { return changed(Component.INFECTION, infection, state.infection()); }
    ExactInventory inventory(FrontierWorldState state) { return changed(Component.INVENTORY, inventory, state.inventory()); }
    Map<SubjectId, ProductionJob> productionJobs(FrontierWorldState state) { return changed(Component.PRODUCTION_JOBS, productionJobs, state.productionJobs()); }
    Map<SubjectId, SupplyContract> contracts(FrontierWorldState state) { return changed(Component.CONTRACTS, contracts, state.contracts()); }
    Map<SubjectId, RouteOperation> operations(FrontierWorldState state) { return changed(Component.OPERATIONS, operations, state.operations()); }
    LogisticsHistory logisticsHistory(FrontierWorldState state) { return changed(Component.LOGISTICS_HISTORY, logisticsHistory, state.logisticsHistory()); }
    Map<PhysicalIntentId, PhysicalIntent> physicalIntents(FrontierWorldState state) { return changed(Component.PHYSICAL_INTENTS, physicalIntents, state.physicalIntents()); }
    Map<PhysicalObservationId, PhysicalEffectObservation> physicalObservations(FrontierWorldState state) { return changed(Component.PHYSICAL_OBSERVATIONS, physicalObservations, state.physicalObservations()); }
    Map<SceneLeaseId, SceneLease> sceneLeases(FrontierWorldState state) { return changed(Component.SCENE_LEASES, sceneLeases, state.sceneLeases()); }
    HiveColony hiveColony(FrontierWorldState state) { return changed(Component.HIVE_COLONY, hiveColony, state.hiveColony()); }
    Map<SubjectId, StructureDamage> structureDamage(FrontierWorldState state) { return changed(Component.STRUCTURE_DAMAGE, structureDamage, state.structureDamage()); }
    Map<BlockPosition, PhysicalDelta> physicalDeltas(FrontierWorldState state) { return changed(Component.PHYSICAL_DELTAS, physicalDeltas, state.physicalDeltas()); }
    Map<SubjectId, AmbientActorLease> ambientLeases(FrontierWorldState state) { return changed(Component.AMBIENT_LEASES, ambientLeases, state.ambientLeases()); }
    Map<SubjectId, RouteConstruction> routeConstructions(FrontierWorldState state) { return changed(Component.ROUTE_CONSTRUCTIONS, routeConstructions, state.routeConstructions()); }
    Map<SubjectId, RouteMaintenance> routeMaintenances(FrontierWorldState state) { return changed(Component.ROUTE_MAINTENANCES, routeMaintenances, state.routeMaintenances()); }
    RouteTopology routeTopology(FrontierWorldState state) { return changed(Component.ROUTE_TOPOLOGY, routeTopology, state.routeTopology()); }
    StrategicPlanState strategicPlans(FrontierWorldState state) { return changed(Component.STRATEGIC_PLANS, strategicPlans, state.strategicPlans()); }
    HumanPopulation humanPopulation(FrontierWorldState state) { return changed(Component.HUMAN_POPULATION, humanPopulation, state.humanPopulation()); }
    CompanyRegistry companies(FrontierWorldState state) { return changed(Component.COMPANIES, companies, state.companies()); }
    ResourceSiteState resourceSites(FrontierWorldState state) { return changed(Component.RESOURCE_SITES, resourceSites, state.resourceSites()); }

    private void mark(Component component) {
        if (!changed.add(component)) throw new IllegalStateException("state component is specified more than once: " + component);
    }
    private static <T> T require(T value, String label) { return Objects.requireNonNull(value, label); }
    private <T> T changed(Component component, T replacement, T retained) {
        return changed.contains(component) ? replacement : retained;
    }
}
