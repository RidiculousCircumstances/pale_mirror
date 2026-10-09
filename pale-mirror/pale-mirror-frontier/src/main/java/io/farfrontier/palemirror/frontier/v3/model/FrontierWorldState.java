package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId;
import java.util.Optional;
import io.farfrontier.palemirror.frontier.v3.model.navigation.ActorMovement;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionState;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionGroup;
import io.farfrontier.palemirror.frontier.v3.api.FixedRatio; import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId; import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind; import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId; import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId; import java.util.HashSet; import java.util.LinkedHashMap; import java.util.List; import java.util.Map; import java.util.Objects; import java.util.Set; import java.util.function.Supplier;
    public record FrontierWorldState(FrontierBootstrap bootstrap, Map<SubjectId, ActorLocation> actorLocations,
        Map<SubjectId, StructureCondition> structureConditions, Map<InfectionCell, FixedRatio> infection, ExactInventory inventory, Map<SubjectId, ProductionJob> productionJobs,
        Map<SubjectId, SettlementServiceWork> serviceWorks,
        Map<PhysicalIntentId, PhysicalIntent> physicalIntents, Map<PhysicalObservationId, PhysicalEffectObservation> physicalObservations,
        Map<SceneLeaseId, SceneLease> sceneLeases, HiveColony hiveColony, Map<SubjectId, StructureDamage> structureDamage, Map<BlockPosition, PhysicalDelta> physicalDeltas,
        Map<SubjectId, AmbientActorLease> ambientLeases, Map<SubjectId, RouteConstruction> routeConstructions, Map<SubjectId, RouteMaintenance> routeMaintenances, RouteTopology routeTopology, StrategicPlanState strategicPlans,
        HumanPopulation humanPopulation, CompanyRegistry companies, ResourceSiteState resourceSites, PhysicalReplicaCustodyState replicaCustody,
        DeferredAftermathState deferredAftermath, FencedRecoveryState fencedRecovery, DiagnosticIncidentIndex diagnosticIncidents,
        Map<SubjectId, ActorMovement> actorMovements, ActorExecutionState actorExecutions, ShipmentState shipments,
        io.farfrontier.palemirror.frontier.v3.model.group.UnitGroupState unitGroups,
        io.farfrontier.palemirror.frontier.v3.model.expedition.TransportFleet transportFleet,
        io.farfrontier.palemirror.frontier.v3.model.extraction.ExtractionSiteState extractionSites) {
    /** Construction for worlds with no declared extractive content; current hydration declares it explicitly. */
    public FrontierWorldState(FrontierBootstrap bootstrap, Map<SubjectId, ActorLocation> actorLocations,
        Map<SubjectId, StructureCondition> structureConditions, Map<InfectionCell, FixedRatio> infection, ExactInventory inventory,
        Map<SubjectId, ProductionJob> productionJobs, Map<SubjectId, SettlementServiceWork> serviceWorks,
        Map<PhysicalIntentId, PhysicalIntent> physicalIntents, Map<PhysicalObservationId, PhysicalEffectObservation> physicalObservations,
        Map<SceneLeaseId, SceneLease> sceneLeases, HiveColony hiveColony, Map<SubjectId, StructureDamage> structureDamage,
        Map<BlockPosition, PhysicalDelta> physicalDeltas, Map<SubjectId, AmbientActorLease> ambientLeases,
        Map<SubjectId, RouteConstruction> routeConstructions, Map<SubjectId, RouteMaintenance> routeMaintenances,
        RouteTopology routeTopology, StrategicPlanState strategicPlans, HumanPopulation humanPopulation, CompanyRegistry companies,
        ResourceSiteState resourceSites, PhysicalReplicaCustodyState replicaCustody, DeferredAftermathState deferredAftermath,
        FencedRecoveryState fencedRecovery, DiagnosticIncidentIndex diagnosticIncidents,
        Map<SubjectId, ActorMovement> actorMovements, ActorExecutionState actorExecutions, ShipmentState shipments,
        io.farfrontier.palemirror.frontier.v3.model.group.UnitGroupState unitGroups,
        io.farfrontier.palemirror.frontier.v3.model.expedition.TransportFleet transportFleet) {
        this(bootstrap, actorLocations, structureConditions, infection, inventory, productionJobs, serviceWorks,
                physicalIntents, physicalObservations, sceneLeases, hiveColony, structureDamage, physicalDeltas,
                ambientLeases, routeConstructions, routeMaintenances, routeTopology, strategicPlans, humanPopulation,
                companies, resourceSites, replicaCustody, deferredAftermath, fencedRecovery, diagnosticIncidents,
                actorMovements, actorExecutions, shipments, unitGroups, transportFleet,
                io.farfrontier.palemirror.frontier.v3.model.extraction.ExtractionSiteState.empty());
    }
    /** Construction for cuts with no declared transport assets; never used by current hydration. */
    public FrontierWorldState(FrontierBootstrap bootstrap, Map<SubjectId, ActorLocation> actorLocations,
        Map<SubjectId, StructureCondition> structureConditions, Map<InfectionCell, FixedRatio> infection, ExactInventory inventory,
        Map<SubjectId, ProductionJob> productionJobs, Map<SubjectId, SettlementServiceWork> serviceWorks,
        Map<PhysicalIntentId, PhysicalIntent> physicalIntents, Map<PhysicalObservationId, PhysicalEffectObservation> physicalObservations,
        Map<SceneLeaseId, SceneLease> sceneLeases, HiveColony hiveColony, Map<SubjectId, StructureDamage> structureDamage,
        Map<BlockPosition, PhysicalDelta> physicalDeltas, Map<SubjectId, AmbientActorLease> ambientLeases,
        Map<SubjectId, RouteConstruction> routeConstructions, Map<SubjectId, RouteMaintenance> routeMaintenances,
        RouteTopology routeTopology, StrategicPlanState strategicPlans, HumanPopulation humanPopulation, CompanyRegistry companies,
        ResourceSiteState resourceSites, PhysicalReplicaCustodyState replicaCustody, DeferredAftermathState deferredAftermath,
        FencedRecoveryState fencedRecovery, DiagnosticIncidentIndex diagnosticIncidents,
        Map<SubjectId, ActorMovement> actorMovements, ActorExecutionState actorExecutions, ShipmentState shipments,
        io.farfrontier.palemirror.frontier.v3.model.group.UnitGroupState unitGroups) {
        this(bootstrap, actorLocations, structureConditions, infection, inventory, productionJobs, serviceWorks,
                physicalIntents, physicalObservations, sceneLeases, hiveColony, structureDamage, physicalDeltas,
                ambientLeases, routeConstructions, routeMaintenances, routeTopology, strategicPlans, humanPopulation,
                companies, resourceSites, replicaCustody, deferredAftermath, fencedRecovery, diagnosticIncidents,
                actorMovements, actorExecutions, shipments, unitGroups,
                io.farfrontier.palemirror.frontier.v3.model.expedition.TransportFleet.empty());
    }
    /** Fresh/bootstrap construction before any transport obligations exist. */
    public FrontierWorldState(FrontierBootstrap bootstrap, Map<SubjectId, ActorLocation> actorLocations,
        Map<SubjectId, StructureCondition> structureConditions, Map<InfectionCell, FixedRatio> infection, ExactInventory inventory,
        Map<SubjectId, ProductionJob> productionJobs, Map<SubjectId, SettlementServiceWork> serviceWorks,
        Map<PhysicalIntentId, PhysicalIntent> physicalIntents, Map<PhysicalObservationId, PhysicalEffectObservation> physicalObservations,
        Map<SceneLeaseId, SceneLease> sceneLeases, HiveColony hiveColony, Map<SubjectId, StructureDamage> structureDamage,
        Map<BlockPosition, PhysicalDelta> physicalDeltas, Map<SubjectId, AmbientActorLease> ambientLeases,
        Map<SubjectId, RouteConstruction> routeConstructions, Map<SubjectId, RouteMaintenance> routeMaintenances,
        RouteTopology routeTopology, StrategicPlanState strategicPlans, HumanPopulation humanPopulation, CompanyRegistry companies,
        ResourceSiteState resourceSites, PhysicalReplicaCustodyState replicaCustody, DeferredAftermathState deferredAftermath,
        FencedRecoveryState fencedRecovery, DiagnosticIncidentIndex diagnosticIncidents,
        Map<SubjectId, ActorMovement> actorMovements, ActorExecutionState actorExecutions) {
        this(bootstrap, actorLocations, structureConditions, infection, inventory, productionJobs, serviceWorks, physicalIntents, physicalObservations, sceneLeases, hiveColony, structureDamage,
            physicalDeltas, ambientLeases, routeConstructions, routeMaintenances, routeTopology, strategicPlans, humanPopulation,
            companies, resourceSites, replicaCustody, deferredAftermath, fencedRecovery, diagnosticIncidents,
            actorMovements, actorExecutions, ShipmentState.empty(), io.farfrontier.palemirror.frontier.v3.model.group.UnitGroupState.empty());
    }
    private static final FixedRatio ZERO_INFECTION = new FixedRatio(io.farfrontier.palemirror.frontier.v3.api.FixedScalar.ZERO); private static final int MAX_PHYSICAL_OBSERVATIONS = 4_096;
    public static final int MAX_PHYSICAL_INTENTS = 4_096;
    private static final int MAX_SCENE_LEASES = 1_024, MAX_AMBIENT_LEASES = 4_096, MAX_STRUCTURE_DAMAGE_CELLS = 65_536;
    private static final ThreadLocal<Integer> DEFERRED_FULL_VALIDATION_DEPTH = ThreadLocal.withInitial(() -> 0);
    public FrontierWorldState(FrontierBootstrap bootstrap, Map<SubjectId, ActorLocation> actorLocations,
                              Map<SubjectId, StructureCondition> structureConditions, Map<InfectionCell, FixedRatio> infection,
                              ExactInventory inventory, Map<SubjectId, ProductionJob> productionJobs,
                              Map<SubjectId, SettlementServiceWork> serviceWorks, Map<PhysicalIntentId, PhysicalIntent> physicalIntents,
                              Map<PhysicalObservationId, PhysicalEffectObservation> physicalObservations,
                              Map<SceneLeaseId, SceneLease> sceneLeases, HiveColony hiveColony,
                              Map<SubjectId, StructureDamage> structureDamage, Map<BlockPosition, PhysicalDelta> physicalDeltas,
                              Map<SubjectId, AmbientActorLease> ambientLeases,
                              Map<SubjectId, RouteConstruction> routeConstructions,
                              Map<SubjectId, RouteMaintenance> routeMaintenances, RouteTopology routeTopology,
                              StrategicPlanState strategicPlans, HumanPopulation humanPopulation,
                              CompanyRegistry companies, ResourceSiteState resourceSites,
                              PhysicalReplicaCustodyState replicaCustody, DeferredAftermathState deferredAftermath,
                              FencedRecoveryState fencedRecovery, DiagnosticIncidentIndex diagnosticIncidents) {
        this(bootstrap, actorLocations, structureConditions, infection, inventory, productionJobs, serviceWorks,
                physicalIntents, physicalObservations, sceneLeases,
                hiveColony, structureDamage, physicalDeltas, ambientLeases, routeConstructions, routeMaintenances,
                routeTopology, strategicPlans, humanPopulation, companies, resourceSites, replicaCustody,
                deferredAftermath, fencedRecovery, diagnosticIncidents, Map.of(), ActorExecutionState.empty());
    }
    public FrontierWorldState(FrontierBootstrap bootstrap, Map<SubjectId, ActorLocation> actorLocations,
                              Map<SubjectId, StructureCondition> structureConditions, Map<InfectionCell, FixedRatio> infection, ExactInventory inventory,
                              Map<SubjectId, ProductionJob> productionJobs, Map<SubjectId, SettlementServiceWork> serviceWorks,
                              Map<PhysicalIntentId, PhysicalIntent> physicalIntents, Map<PhysicalObservationId, PhysicalEffectObservation> physicalObservations,
                              Map<SceneLeaseId, SceneLease> sceneLeases, HiveColony hiveColony, Map<SubjectId, StructureDamage> structureDamage,
                              Map<BlockPosition, PhysicalDelta> physicalDeltas, Map<SubjectId, AmbientActorLease> ambientLeases,
                              Map<SubjectId, RouteConstruction> routeConstructions, Map<SubjectId, RouteMaintenance> routeMaintenances, RouteTopology routeTopology,
                              StrategicPlanState strategicPlans, HumanPopulation humanPopulation, CompanyRegistry companies, ResourceSiteState resourceSites,
                              PhysicalReplicaCustodyState replicaCustody) {
        this(bootstrap, actorLocations, structureConditions, infection, inventory, productionJobs, serviceWorks, physicalIntents, physicalObservations, sceneLeases, hiveColony, structureDamage, physicalDeltas, ambientLeases, routeConstructions,
                routeMaintenances, routeTopology, strategicPlans, humanPopulation, companies, resourceSites, replicaCustody, DeferredAftermathState.empty(), FencedRecoveryState.empty(), DiagnosticIncidentIndex.empty());
    }
    public FrontierWorldState(FrontierBootstrap bootstrap, Map<SubjectId, ActorLocation> actorLocations,
                              Map<SubjectId, StructureCondition> structureConditions, Map<InfectionCell, FixedRatio> infection, ExactInventory inventory,
                              Map<SubjectId, ProductionJob> productionJobs, Map<SubjectId, SettlementServiceWork> serviceWorks,
                              Map<PhysicalIntentId, PhysicalIntent> physicalIntents, Map<PhysicalObservationId, PhysicalEffectObservation> physicalObservations,
                              Map<SceneLeaseId, SceneLease> sceneLeases, HiveColony hiveColony, Map<SubjectId, StructureDamage> structureDamage,
                              Map<BlockPosition, PhysicalDelta> physicalDeltas, Map<SubjectId, AmbientActorLease> ambientLeases,
                              Map<SubjectId, RouteConstruction> routeConstructions, Map<SubjectId, RouteMaintenance> routeMaintenances, RouteTopology routeTopology,
                              StrategicPlanState strategicPlans, HumanPopulation humanPopulation, CompanyRegistry companies, ResourceSiteState resourceSites,
                              PhysicalReplicaCustodyState replicaCustody, DeferredAftermathState deferredAftermath) {
        this(bootstrap, actorLocations, structureConditions, infection, inventory, productionJobs, serviceWorks, physicalIntents, physicalObservations, sceneLeases, hiveColony, structureDamage, physicalDeltas, ambientLeases, routeConstructions,
                routeMaintenances, routeTopology, strategicPlans, humanPopulation, companies, resourceSites, replicaCustody, deferredAftermath, FencedRecoveryState.empty(), DiagnosticIncidentIndex.empty());
    }
    public FrontierWorldState(FrontierBootstrap bootstrap, Map<SubjectId, ActorLocation> actorLocations, Map<SubjectId, StructureCondition> structureConditions,
                       Map<InfectionCell, FixedRatio> infection, ExactInventory inventory, Map<SubjectId, ProductionJob> productionJobs,
                       Map<PhysicalIntentId, PhysicalIntent> physicalIntents, Map<PhysicalObservationId, PhysicalEffectObservation> physicalObservations,
                       Map<SceneLeaseId, SceneLease> sceneLeases, HiveColony hiveColony, Map<SubjectId, StructureDamage> structureDamage,
                       Map<BlockPosition, PhysicalDelta> physicalDeltas, Map<SubjectId, AmbientActorLease> ambientLeases,
                       Map<SubjectId, RouteConstruction> routeConstructions, RouteTopology routeTopology, StrategicPlanState strategicPlans,
                       HumanPopulation humanPopulation, ResourceSiteState resourceSites) {
        this(bootstrap, actorLocations, structureConditions, infection, inventory, productionJobs, Map.of(), physicalIntents, physicalObservations, sceneLeases, hiveColony, structureDamage, physicalDeltas, ambientLeases, routeConstructions, Map.of(),
                routeTopology, strategicPlans, humanPopulation, CompanyRegistry.empty(), resourceSites, PhysicalReplicaCustodyState.empty(), DeferredAftermathState.empty(), FencedRecoveryState.empty(), DiagnosticIncidentIndex.empty());
    }
    public FrontierWorldState(FrontierBootstrap bootstrap, Map<SubjectId, ActorLocation> actorLocations, Map<SubjectId, StructureCondition> structureConditions,
                              Map<InfectionCell, FixedRatio> infection, ExactInventory inventory, Map<SubjectId, ProductionJob> productionJobs,
                              Map<PhysicalIntentId, PhysicalIntent> physicalIntents, Map<PhysicalObservationId, PhysicalEffectObservation> physicalObservations,
                              Map<SceneLeaseId, SceneLease> sceneLeases, HiveColony hiveColony, Map<SubjectId, StructureDamage> structureDamage,
                              Map<BlockPosition, PhysicalDelta> physicalDeltas, Map<SubjectId, AmbientActorLease> ambientLeases,
                              Map<SubjectId, RouteConstruction> routeConstructions, RouteTopology routeTopology, StrategicPlanState strategicPlans,
                              HumanPopulation humanPopulation, CompanyRegistry companies, ResourceSiteState resourceSites) {
        this(bootstrap, actorLocations, structureConditions, infection, inventory, productionJobs, Map.of(), physicalIntents, physicalObservations, sceneLeases, hiveColony, structureDamage, physicalDeltas, ambientLeases, routeConstructions,
                Map.of(), routeTopology, strategicPlans, humanPopulation, companies, resourceSites, PhysicalReplicaCustodyState.empty(), DeferredAftermathState.empty(), FencedRecoveryState.empty(), DiagnosticIncidentIndex.empty());
    }
    public FrontierWorldState { Objects.requireNonNull(bootstrap, "bootstrap");
        actorLocations = FrontierWorldStateSupport.immutableMap(actorLocations, "actor locations"); structureConditions = FrontierWorldStateSupport.immutableMap(structureConditions, "structure conditions");
        infection = FrontierWorldStateSupport.immutableMap(infection, "infection"); Objects.requireNonNull(inventory, "inventory");
        productionJobs = FrontierWorldStateSupport.immutableMap(productionJobs, "production jobs");
        serviceWorks = FrontierWorldStateSupport.immutableMap(serviceWorks, "settlement service works"); physicalIntents = FrontierWorldStateSupport.immutableMap(physicalIntents, "physical intents");
        physicalObservations = FrontierWorldStateSupport.immutableMap(physicalObservations, "physical observations"); sceneLeases = FrontierWorldStateSupport.immutableMap(sceneLeases, "scene leases");
        structureDamage = FrontierWorldStateSupport.immutableMap(structureDamage, "structure damage"); physicalDeltas = FrontierWorldStateSupport.immutableMap(physicalDeltas, "physical deltas");
        ambientLeases = FrontierWorldStateSupport.immutableMap(ambientLeases, "ambient leases"); routeConstructions = FrontierWorldStateSupport.immutableMap(routeConstructions, "route constructions");
        routeMaintenances = FrontierWorldStateSupport.immutableMap(routeMaintenances, "route maintenances");
        Objects.requireNonNull(routeTopology, "route topology"); Objects.requireNonNull(strategicPlans, "strategic plans"); Objects.requireNonNull(humanPopulation, "human population");
        Objects.requireNonNull(companies, "company registry"); Objects.requireNonNull(resourceSites, "resource sites");
        Objects.requireNonNull(replicaCustody, "replica custody"); Objects.requireNonNull(deferredAftermath, "deferred aftermath");
        Objects.requireNonNull(fencedRecovery, "fenced recovery");
        Objects.requireNonNull(diagnosticIncidents, "diagnostic incidents"); actorMovements = FrontierWorldStateSupport.immutableMap(actorMovements, "actor movements");
        Objects.requireNonNull(actorExecutions, "actor executions");
        Objects.requireNonNull(shipments, "shipments");
        Objects.requireNonNull(unitGroups, "unit groups");
        Objects.requireNonNull(transportFleet, "transport fleet");
        Objects.requireNonNull(extractionSites, "extraction sites");
        extractionSites.validate(bootstrap, inventory);
        ExtractionWorkAuthority.validateReferences(extractionSites, actorExecutions);
        TransportFleetReferences.validate(bootstrap, transportFleet, actorLocations, inventory, shipments);
        GroupMemberActivityCapability.validateReferences(unitGroups, actorExecutions);
        ShipmentExecutionCapability.validateReferences(shipments, actorExecutions);
        ActorExecutionComposition.CAPABILITIES.validateKinds(actorExecutions);
        PresenceActivityCapability.validateReferences(actorLocations, actorExecutions);
        ScoutPatrolActivityCapability.validateReferences(bootstrap, hiveColony, strategicPlans, actorExecutions);
        RoutePatrolExecutionAuthority.validateReferences(strategicPlans.routePatrols(), actorExecutions);
        HiveAssemblyExecutionAuthority.validateReferences(hiveColony.mobilizations(), actorExecutions);
        HiveReturnExecutionAuthority.validateReferences(hiveColony.mobilizations(), actorExecutions);
        SettlementAssaultExecutionAuthority.validateReferences(strategicPlans.settlementAssaults(), actorExecutions);
        EngineeringExecutionAuthority.validateReferences(routeConstructions, routeMaintenances, actorExecutions);
        SettlementServiceExecutionAuthority.validateReferences(serviceWorks, actorExecutions);
        MedicalExecutionAuthority.validateReferences(humanPopulation, actorExecutions);
        ActorMovementStateSupport.validate(actorMovements, actorLocations, humanPopulation, inventory, actorExecutions, shipments, unitGroups, extractionSites);
        ResidentMealExecutionAuthority.validate(humanPopulation, actorExecutions);
        TransitActivityCapability.validateReferences(humanPopulation, actorExecutions);
        HarvestActivityCapability.validateReferences(resourceSites, actorExecutions);
        ProductionActivityCapability.validateReferences(productionJobs, actorExecutions);
        if (!actorLocations.keySet().containsAll(actorExecutions.actors().keySet())) throw new IllegalArgumentException("execution names unknown actor");
        if (!fullValidationDeferred()) {
            ContainerPhysicalReservations.validate(inventory, resourceSites, productionJobs, shipments, extractionSites);
            ActorBodyAuthority.validate(actorLocations, fencedRecovery);
            validateFullState(bootstrap, actorLocations, structureConditions, infection, inventory, productionJobs,
                serviceWorks, physicalIntents, physicalObservations, sceneLeases, hiveColony,
                structureDamage, physicalDeltas, ambientLeases, routeConstructions, routeMaintenances, routeTopology, strategicPlans,
                humanPopulation, companies, resourceSites, fencedRecovery, actorExecutions, transportFleet);
        }
    }
    /** Keeps the high-frequency immutable-state constructor below the JIT's large-method threshold. */
    private static void validateFullState(FrontierBootstrap bootstrap, Map<SubjectId, ActorLocation> actorLocations,
                                          Map<SubjectId, StructureCondition> structureConditions, Map<InfectionCell, FixedRatio> infection,
                                          ExactInventory inventory, Map<SubjectId, ProductionJob> productionJobs,
                                          Map<SubjectId, SettlementServiceWork> serviceWorks, Map<PhysicalIntentId, PhysicalIntent> physicalIntents,
                                          Map<PhysicalObservationId, PhysicalEffectObservation> physicalObservations,
                                          Map<SceneLeaseId, SceneLease> sceneLeases, HiveColony hiveColony,
                                          Map<SubjectId, StructureDamage> structureDamage, Map<BlockPosition, PhysicalDelta> physicalDeltas,
                                          Map<SubjectId, AmbientActorLease> ambientLeases,
                                          Map<SubjectId, RouteConstruction> routeConstructions,
                                          Map<SubjectId, RouteMaintenance> routeMaintenances, RouteTopology routeTopology,
                                          StrategicPlanState strategicPlans, HumanPopulation humanPopulation,
                                          CompanyRegistry companies, ResourceSiteState resourceSites, FencedRecoveryState fencedRecovery,
                                          io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionState actorExecutions,
                                          io.farfrontier.palemirror.frontier.v3.model.expedition.TransportFleet transportFleet) {
            resourceSites.validate(bootstrap); HarvestContainerReservations.validate(inventory, resourceSites);
            HarvestContainerReservations.validateAccounts(inventory, resourceSites);
            strategicPlans.validate(bootstrap, routeTopology, humanPopulation);
            strategicPlans.hiveSettlementKnowledge().validate(bootstrap, hiveColony, actorLocations);
            strategicPlans.hiveTerritoryKnowledge().validate(bootstrap, hiveColony, actorLocations, structureConditions);
        routeTopology.replacementRoutes().forEach((settlement, route) -> FrontierRouteNetwork.validateSettlementWaypoints(bootstrap, settlement, route));
        RouteConstructionStateSupport.validate(bootstrap, routeTopology, routeConstructions, actorLocations, humanPopulation, productionJobs, resourceSites, strategicPlans, ambientLeases);
        RouteMaintenanceStateSupport.validate(bootstrap, hiveColony, routeTopology, routeConstructions, routeMaintenances, physicalDeltas, actorLocations, humanPopulation, productionJobs, resourceSites, strategicPlans);
        RouteMaintenanceStateSupport.validateAmbientAssemblyLeases(routeMaintenances, ambientLeases);
        MedicalEvacuationStateSupport.validate(bootstrap, humanPopulation, actorLocations, structureConditions, inventory, physicalIntents); Objects.requireNonNull(hiveColony, "hive colony");
        hiveColony.validateAgainst(bootstrap); HiveNutrientTransferStateSupport.validate(bootstrap, inventory, hiveColony, strategicPlans);
        HiveMobilizationStateSupport.validateTaskCustody(bootstrap, hiveColony, strategicPlans);
        for (HiveGrowthJob job : hiveColony.growthJobs().values()) {
            requireJobTask(strategicPlans, job.taskId(), job.hiveId(), StrategicTaskKind.GROW_HIVE_ORGANISM, "hive growth");
        }
        FrontierWorldStateSupport.validateEconomicClaims(bootstrap, inventory);
        Set<SubjectId> expectedActors = ActorIdentityDeclarations.validate(bootstrap, hiveColony, humanPopulation, actorLocations, transportFleet);
        FrontierWorldStateSupport.validateActorItemCustody(bootstrap.worldId(), actorLocations, inventory, fencedRecovery);
        HiveLifecycleStateSupport.validateCocoonCustody(bootstrap, hiveColony, actorLocations, ambientLeases, physicalDeltas, actorExecutions);
        FrontierWorldStateSupport.validateSettlementPolicies(bootstrap, humanPopulation);
        for (Settlement settlement : bootstrap.settlements()) for (Resident bootstrapResident : settlement.residents()) {
            ResidentProfile profile = humanPopulation.resident(bootstrapResident.id());
            // Bootstrap defines an exact person's immutable identity, not their permanent home.
            // A completed v3 migration deliberately changes the profile's household/settlement.
            if (profile == null) throw new IllegalArgumentException("bootstrap resident must remain in the canonical population register"); }
        for (Company company : companies.companies().values()) {
            FrontierWorldStateSupport.settlement(bootstrap, company.settlementId());
            ResidentProfile founder = humanPopulation.resident(company.founderId());
            if (founder == null) throw new IllegalArgumentException("company founder must remain a canonical resident");
            EconomicAccount account = inventory.economics().require(company.id());
            if (account.ownerKind() != EconomicOwnerKind.COMPANY || account.status() != EconomicAccountStatus.ACTIVE && company.status() == CompanyStatus.ACTIVE) {
                throw new IllegalArgumentException("company legal state and account must agree");
            }
        }
        for (EmploymentContract contract : companies.employmentContracts().values()) {
            WorkEmployer employer = contract.employer();
            ResidentProfile resident = humanPopulation.resident(contract.residentId());
            FrontierWorldStateSupport.settlement(bootstrap, employer.settlementId());
            if (resident == null || inventory.economics().require(employer.id()).ownerKind() != employer.kind())
                throw new IllegalArgumentException("employment must bind known declared legal identities");
            if (contract.status() == EmploymentContractStatus.ACTIVE
                    && (!resident.settlementId().equals(employer.settlementId())
                    || humanPopulation.migrations().containsKey(resident.id())
                    || actorLocations.get(resident.id()).condition().status() != ActorLifeStatus.ALIVE)) {
                throw new IllegalArgumentException("active employment requires a settled living resident in its declared employer's home");
            }
            if (contract.status() == EmploymentContractStatus.ACTIVE) employer.validate(inventory.economics(), companies.companies());
        }
        for (EconomicAccount account : inventory.economics().accounts().values()) {
            if (account.ownerKind() == EconomicOwnerKind.COMPANY && !companies.companies().containsKey(account.ownerId())) {
                throw new IllegalArgumentException("company account must have one registered legal company");
            }
            if (account.ownerKind() == EconomicOwnerKind.RESIDENT && humanPopulation.resident(account.ownerId()) == null)
                throw new IllegalArgumentException("resident resource-title registration must name a known person");
        }
        for (ActorLocation location : actorLocations.values()) FrontierWorldStateSupport.requirePosition(bootstrap.bounds(), location.supportingSurface().support());
        StrategicPlanState validatedPlans = strategicPlans;
        for (ResidentMigrationJourney journey : humanPopulation.migrations().values()) {
            ActorLocation actor = actorLocations.get(journey.residentId());
            if (actor == null || actor.condition().status() != ActorLifeStatus.ALIVE
                    || !ActorBodyAuthority.retainsPhysicalCustody(fencedRecovery, journey.residentId())
                        && !actor.supportingSurface().support().equals(journey.currentPosition())) {
                throw new IllegalArgumentException("migration requires one living resident and a reconciled COLD checkpoint");
            }
            journey.route().forEach(position -> FrontierWorldStateSupport.requirePosition(bootstrap.bounds(), position));
            journey.rejoin().ifPresent(approach -> approach.path().forEach(surface ->
                    FrontierWorldStateSupport.requirePosition(bootstrap.bounds(), surface.support())));
            if (sceneLeases.values().stream().anyMatch(lease -> lease.status() != SceneLeaseStatus.CLOSED
                    && lease.members().stream().anyMatch(member -> member.actorId().equals(journey.residentId())))) {
                throw new IllegalArgumentException("migration journey resident may not retain a competing scene executor");
            }
            AmbientActorLease ambient = ambientLeases.get(journey.residentId());
            if (ambient != null && ambient.status() != AmbientLeaseStatus.CLOSED && ambient.goal() != AmbientGoalKind.TRANSIT) {
                throw new IllegalArgumentException("migration journey resident may retain only its exact HOT transit executor");
            }
            boolean patrolClaim = validatedPlans.routePatrols().values().stream().anyMatch(patrol -> patrol.active()
                    && patrol.memberIds().contains(journey.residentId()));
            if (patrolClaim) {
                throw new IllegalArgumentException("migration journey resident cannot retain a competing operation or patrol claim");
            }
        }
        if (ambientLeases.size() > MAX_AMBIENT_LEASES) throw new IllegalArgumentException("ambient lease retention limit exceeded"); Set<SubjectId> activelyAmbientLeased = new HashSet<>();
        for (Map.Entry<SubjectId, AmbientActorLease> entry : ambientLeases.entrySet()) {
            AmbientActorLease lease = entry.getValue();
            if (!entry.getKey().equals(lease.actorId()) || !expectedActors.contains(lease.actorId())) {
                throw new IllegalArgumentException("ambient lease must belong to one canonical actor");
            }
            if (actorLocations.get(lease.actorId()).condition().status() != ActorLifeStatus.ALIVE && lease.status() != AmbientLeaseStatus.CLOSED) {
                throw new IllegalArgumentException("dead actor cannot retain an active ambient lease");
            }
            FrontierWorldStateSupport.requirePosition(bootstrap.bounds(), lease.handoffBody().supportingSurface().support()); FrontierWorldStateSupport.requirePosition(bootstrap.bounds(), lease.goalBody().supportingSurface().support());
            if (lease.status() != AmbientLeaseStatus.CLOSED && !activelyAmbientLeased.add(lease.actorId())) {
                throw new IllegalArgumentException("actor cannot retain multiple active ambient leases");
            }
        }
        Set<SubjectId> expectedStructures = FrontierWorldStateSupport.structureIds(bootstrap);
        Set<SubjectId> expectedSettlements = bootstrap.settlements().stream()
                .map(Settlement::id).collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (!expectedStructures.equals(structureConditions.keySet())) throw new IllegalArgumentException("structure condition index must own every and only bootstrap structure");
        int damageCellCount = 0;
        for (Map.Entry<SubjectId, StructureDamage> entry : structureDamage.entrySet()) {
            StructureDamage damage = entry.getValue();
            if (!expectedStructures.contains(entry.getKey()) || !entry.getKey().equals(damage.structureId())) {
                throw new IllegalArgumentException("structure damage must belong to one bootstrap structure");
            }
            SettlementStructure structure = FrontierWorldStateSupport.structureById(bootstrap, entry.getKey());
            for (Map.Entry<BlockPosition, StructureDamage.DamageCell> cell : damage.cells().entrySet()) {
                GrayboxCell expected = FrontierGrayboxPlan.intactStructureCell(bootstrap.terrain(), structure, cell.getKey());
                if (expected == null || expected.semanticPart() != cell.getValue().semanticPart()) {
                    throw new IllegalArgumentException("structure damage must name one exact intact semantic cell");
                }
            }
            damageCellCount = Math.addExact(damageCellCount, damage.cells().size());
        }
        FrontierWorldPhysicalDeltaSupport.validate(bootstrap, hiveColony, routeTopology, routeConstructions, physicalDeltas);
        if (damageCellCount > MAX_STRUCTURE_DAMAGE_CELLS) {
            throw new IllegalArgumentException("structure damage retention limit exceeded");
        }
        inventory.surfaces().values().stream().filter(ContainerSurface::fixed)
                .forEach(surface -> FrontierWorldStateSupport.requirePosition(bootstrap.bounds(), surface.position()));
        for (Map.Entry<InfectionCell, FixedRatio> entry : infection.entrySet()) {
            if (entry.getValue().equals(ZERO_INFECTION)) throw new IllegalArgumentException("sparse infection index must not retain zero cells");
            FrontierWorldStateSupport.requirePosition(bootstrap.bounds(), entry.getKey().originAtY(0));
        }
        ProductionFacilityReservations.validate(productionJobs);
        for (Map.Entry<SubjectId, ProductionJob> entry : productionJobs.entrySet()) {
            ProductionJob job = entry.getValue();
            job.rights().validate(inventory, companies, job);
            if (!entry.getKey().equals(job.id())) throw new IllegalArgumentException("production job map key must match job identity");
            requireJobTask(strategicPlans, job.taskId(), job.settlementId(), StrategicTaskKind.PRODUCE_BREAD, "production");
            Settlement settlement = FrontierWorldStateSupport.settlement(bootstrap, job.settlementId());
            SettlementStructure facility = FrontierWorldStateSupport.structure(settlement, job.facilityId());
            if (facility.kind() != StructureKind.WORKSHOP) throw new IllegalArgumentException("production job facility must be a workshop");
            ResidentProfile worker = humanPopulation.resident(job.workerId());
            if (worker == null) throw new IllegalArgumentException("production job worker must be a canonical resident");
            if (!worker.settlementId().equals(settlement.id())) throw new IllegalArgumentException("bread production job worker must belong to its retained settlement");
            if (job.bakeryWork().isPresent()) {
                BakeryWorkValidation.validate(inventory, job, settlement);
                continue;
            }
            if (inventory.items().containsKey(job.outputItemId())) {
                throw new IllegalArgumentException("active production job must not retain its output stack");
            }
            switch (job.inputHold()) {
                case ProductionInputHold.Cold held -> {
                    ExactItemStack input = held.item();
                    if (inventory.items().containsKey(input.id()) || !input.id().equals(job.consumedItemId())
                            || !input.economicOwnerId().equals(settlement.id()) || !"minecraft:wheat".equals(input.itemKind())
                            || input.count() != job.outputCount() || !(input.custody() instanceof InventoryCustody.ContainerSlot slot)
                            || !slot.containerId().equals(depotId(settlement.id())) || inventory.itemAt(slot.containerId(), slot.slot()).isPresent()) {
                        throw new IllegalArgumentException("cold production job must be the sole exact holder of its depot wheat input");
                    }
                }
                case ProductionInputHold.Materialized ignored -> {
                    ExactItemStack input = inventory.items().get(job.consumedItemId());
                    boolean inOwnedDepot = input != null && input.custody() instanceof InventoryCustody.ContainerSlot slot
                            && slot.containerId().equals(depotId(settlement.id()));
                    boolean awaitingPhysicalReconciliation = physicalIntents.values().stream().anyMatch(intent -> intent.causeSubjectId().equals(job.id())
                            && intent.kind() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.PRODUCTION_TRANSFORMATION
                            && intent.status() != io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.CONFIRMED);
                    boolean exactInputMissingOrAltered = input == null || input.count() != job.outputCount();
                    if ((exactInputMissingOrAltered || !inOwnedDepot) && !awaitingPhysicalReconciliation
                            && !FrontierProductionWorkSceneSupport.awaitingBlockedRelease(sceneLeases, strategicPlans, companies, job)) {
                        throw new IllegalArgumentException("materialized production job input must remain in its exact settlement depot slot");
                    }
                }
                case ProductionInputHold.FungibleCold held -> FrontierProductionInputHoldValidation.validateFungibleProductionHold(job, settlement, inventory.fungibleResources(), held.accountId(),
                        held.claimId(), false, 0L);
                case ProductionInputHold.FungibleBound held -> FrontierProductionInputHoldValidation.validateFungibleProductionHold(job, settlement, inventory.fungibleResources(), held.accountId(),
                        held.claimId(), true, held.authorityEpoch());
            }
        }
        SettlementServiceWorkStateSupport.validate(bootstrap, humanPopulation, actorLocations, infection, serviceWorks, physicalIntents);
        FrontierSettlementAssaultSupport.validate(bootstrap, hiveColony, humanPopulation, actorLocations, strategicPlans);
        if (physicalIntents.size() > MAX_PHYSICAL_INTENTS) throw new IllegalArgumentException("physical intent retention limit exceeded");
        Set<SubjectId> fieldSubjects = ResourceSitePhysicalIntentStateSupport.nonterminalSubjects(resourceSites);
        for (Map.Entry<PhysicalIntentId, PhysicalIntent> entry : physicalIntents.entrySet()) {
            PhysicalIntent intent = entry.getValue();
            if (!entry.getKey().equals(intent.id())) throw new IllegalArgumentException("physical intent map key must match intent identity");
            boolean preparedConflictCustody = ResourceSitePhysicalIntentStateSupport.ownsPreparedConflictIntent(resourceSites, intent);
            if ((intent.status() != PhysicalIntentStatus.CONFIRMED && intent.status() != PhysicalIntentStatus.UNKNOWN_AFTER_RESTART && !preparedConflictCustody)
                    && !expectedActors.contains(intent.causeSubjectId()) && !inventory.cargo().containsKey(intent.causeSubjectId()) && !hiveColony.growthJobs().containsKey(intent.causeSubjectId())
                    && !humanPopulation.birthJobs().containsKey(intent.causeSubjectId())
                    && !humanPopulation.medicalOperations().containsKey(intent.causeSubjectId())
                    && !humanPopulation.provisions().containsKey(intent.causeSubjectId())
                    && !expectedStructures.contains(intent.causeSubjectId()) && !expectedSettlements.contains(intent.causeSubjectId())
                    && !productionJobs.containsKey(intent.causeSubjectId())
                    && !serviceWorks.containsKey(intent.causeSubjectId())
                    && !FrontierWorldStateSupport.isHiveOrgan(bootstrap, hiveColony, intent.causeSubjectId())
                    && !strategicPlans.settlementAssaults().containsKey(intent.causeSubjectId()) && !SceneStrikeStateSupport.isSettlementAssaultCause(strategicPlans, intent)
                    && !bootstrap.hive().id().equals(intent.causeSubjectId())
                    && !FrontierRouteNetwork.OWNER.equals(intent.causeSubjectId()) && !routeConstructions.containsKey(intent.causeSubjectId())
                    && !RouteMaintenanceStateSupport.ownsMaintenance(routeMaintenances, intent.causeSubjectId()) && !fieldSubjects.contains(intent.causeSubjectId())) {
                throw new IllegalArgumentException("physical intent cause must be a canonical subject");
            }
            for (SubjectId subject : intent.roles().namedRoles().values()) {
                if (intent.status() == PhysicalIntentStatus.CONFIRMED || intent.status() == PhysicalIntentStatus.UNKNOWN_AFTER_RESTART || preparedConflictCustody) continue;
                RouteConstruction routeConstruction = routeConstructions.get(intent.causeSubjectId());
                boolean hiveNutrientSubject = HiveNutrientTransferStateSupport.ownsIntentSubject(hiveColony, intent, subject);
                boolean reservedRouteConstructionCargo = intent.kind() == PhysicalIntentKind.ROUTE_CONSTRUCTION_MATERIAL_LOADING && routeConstruction != null && routeConstruction.cargoId().isEmpty()
                        && (subject.equals(routeConstruction.plannedCargoId())
                        || subject.equals(routeConstruction.plannedCargoItemId()));
                boolean reservedRouteMaintenanceCargo = RouteMaintenanceStateSupport.reservesSubject(routeMaintenances, intent, subject);
                boolean productionResourceSubject = intent.roles().schema() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentRoleSchema.PRODUCTION_RESOURCES
                        && (subject.equals(intent.roles().require(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentSubjectRole.CUSTODY_ACCOUNT))
                        && inventory.fungibleResources().accounts().containsKey(subject)
                        || subject.equals(intent.roles().require(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentSubjectRole.RESOURCE_CONTAINER))
                        && inventory.containers().containsKey(subject));
                if (!productionResourceSubject && !hiveNutrientSubject && !expectedActors.contains(subject) && !inventory.cargo().containsKey(subject) &&
                        !expectedStructures.contains(subject) && !expectedSettlements.contains(subject)
                        && !inventory.items().containsKey(subject)
                        && !hiveColony.growthJobs().containsKey(subject) && !humanPopulation.birthJobs().containsKey(subject) && !productionJobs.containsKey(subject)
                        && !inventory.fungibleResources().lots().containsKey(subject) && !inventory.fungibleResources().claims().containsKey(subject)
                        && !humanPopulation.provisions().containsKey(subject) && !humanPopulation.medicalOperations().containsKey(subject)
                        && !serviceWorks.containsKey(subject)
                        && productionJobs.values().stream().noneMatch(job -> job.outputItemId().equals(subject))
                        && !FrontierWorldStateSupport.isHiveOrgan(bootstrap, hiveColony, subject)
                        && !FrontierRouteNetwork.OWNER.equals(subject) && !routeConstructions.containsKey(subject) && !RouteMaintenanceStateSupport.ownsMaintenance(routeMaintenances, subject)
                        && !strategicPlans.settlementAssaults().containsKey(subject)
                        && !reservedRouteConstructionCargo && !reservedRouteMaintenanceCargo && !fieldSubjects.contains(subject)) {
                    throw new IllegalArgumentException("physical intent references an unknown canonical subject");
                }
            }
        }
        if (physicalObservations.size() > MAX_PHYSICAL_OBSERVATIONS) throw new IllegalArgumentException("physical observation retention limit exceeded");
        FrontierWorldPhysicalObservationValidation.validate(bootstrap, inventory, infection, physicalIntents, physicalObservations, sceneLeases, routeConstructions, routeMaintenances, routeTopology, serviceWorks);
        ResourceSitePhysicalIntentStateSupport.validateState(resourceSites, physicalIntents, physicalObservations);
        for (PhysicalIntent intent : physicalIntents.values()) {
            if (intent.status() == PhysicalIntentStatus.CONFIRMED
                    && !physicalObservations.containsKey(intent.postconditionObservationId().orElseThrow())) {
                throw new IllegalArgumentException("confirmed physical intent must retain its exact observation");
            }
        }
        if (sceneLeases.size() > MAX_SCENE_LEASES) throw new IllegalArgumentException("scene lease retention limit exceeded");
        FrontierSceneLeaseValidationSupport.validate(bootstrap, humanPopulation, actorLocations, structureConditions, routeConstructions,
                routeMaintenances, strategicPlans, resourceSites, productionJobs, sceneLeases, serviceWorks, activelyAmbientLeased);
        }
    public static FrontierWorldState initial(FrontierBootstrap bootstrap) { return FrontierWorldInitialState.create(bootstrap); }
    /**
     * Planning and reduction may build unpublished immutable candidates. Their local
     * declarations are still checked at construction; the complete cross-world audit
     * belongs to the enclosing transaction's mandatory pre-WAL publication barrier.
     * Bootstrap, hydration and that barrier never inherit this deferral.
     */
    public static <T> T duringUnpublishedTransition(Supplier<T> transition) {
        Objects.requireNonNull(transition, "transition");
        int depth = DEFERRED_FULL_VALIDATION_DEPTH.get();
        DEFERRED_FULL_VALIDATION_DEPTH.set(depth + 1);
        try { return transition.get(); }
        finally {
            if (depth == 0) DEFERRED_FULL_VALIDATION_DEPTH.remove();
            else DEFERRED_FULL_VALIDATION_DEPTH.set(depth);
        }
    }
    void validateComplete() {
        int depth = DEFERRED_FULL_VALIDATION_DEPTH.get();
        DEFERRED_FULL_VALIDATION_DEPTH.remove();
        try {
            new FrontierWorldState(bootstrap, actorLocations, structureConditions, infection, inventory, productionJobs, serviceWorks, physicalIntents, physicalObservations, sceneLeases, hiveColony, structureDamage, physicalDeltas,
                    ambientLeases, routeConstructions, routeMaintenances, routeTopology, strategicPlans, humanPopulation, companies, resourceSites, replicaCustody, deferredAftermath, fencedRecovery, diagnosticIncidents,
                            actorMovements, actorExecutions, shipments, unitGroups, transportFleet, extractionSites);
        } finally {
            if (depth != 0) DEFERRED_FULL_VALIDATION_DEPTH.set(depth);
        }
    }
    /** Pre-WAL sparse-field audit; unknown construction paths use the complete audit. */
    void validateTransitionFrom(FrontierWorldState previous) {
        Objects.requireNonNull(previous, "previous state");
        if (onlyInfectionPlannerAndHealthChangedFrom(previous)) {
            FrontierPlannerHealthValidation.validate(bootstrap, humanPopulation, strategicPlans, routeTopology, hiveColony, actorLocations, fencedRecovery);
            validateInfectionTransition(previous);
            return;
        }
        if (onlyPlannerAndHealthChangedFrom(previous)) {
            FrontierPlannerHealthValidation.validate(bootstrap, humanPopulation, strategicPlans, routeTopology, hiveColony, actorLocations, fencedRecovery);
            return;
        }
        if (!onlyInfectionChangedFrom(previous)) { validateComplete(); return; }
        validateInfectionTransition(previous);
    }
    /** Hive infection tasks may atomically change their cell and planner state. */
    private boolean onlyInfectionPlannerAndHealthChangedFrom(FrontierWorldState previous) {
        return infection != previous.infection && onlyPlannerAndHealthChangedFrom(previous, false);
    }
    private boolean onlyPlannerAndHealthChangedFrom(FrontierWorldState previous) {
        return onlyPlannerAndHealthChangedFrom(previous, true);
    }
    private boolean onlyPlannerAndHealthChangedFrom(FrontierWorldState previous, boolean requirePlannerOrHealthChange) {
        if (bootstrap != previous.bootstrap || actorLocations != previous.actorLocations || structureConditions != previous.structureConditions
                || (requirePlannerOrHealthChange && infection != previous.infection) || inventory != previous.inventory || productionJobs != previous.productionJobs
                || shipments != previous.shipments || transportFleet != previous.transportFleet || extractionSites != previous.extractionSites ||
                        unitGroups != previous.unitGroups || physicalIntents != previous.physicalIntents
                || physicalObservations != previous.physicalObservations || sceneLeases != previous.sceneLeases || hiveColony != previous.hiveColony
                || structureDamage != previous.structureDamage || physicalDeltas != previous.physicalDeltas || ambientLeases != previous.ambientLeases
                || routeConstructions != previous.routeConstructions || routeTopology != previous.routeTopology || companies != previous.companies
                || resourceSites != previous.resourceSites || replicaCustody != previous.replicaCustody || deferredAftermath != previous.deferredAftermath || fencedRecovery != previous.fencedRecovery ||
                        diagnosticIncidents != previous.diagnosticIncidents || actorMovements != previous.actorMovements || actorExecutions != previous.actorExecutions
                || (strategicPlans == previous.strategicPlans && humanPopulation == previous.humanPopulation)) return false;
        if (!sceneLeases.isEmpty() || !physicalIntents.isEmpty() || !physicalObservations.isEmpty()) return false;
        return humanPopulation.households() == previous.humanPopulation.households()
                && humanPopulation.residents() == previous.humanPopulation.residents()
                && humanPopulation.birthJobs() == previous.humanPopulation.birthJobs()
                && humanPopulation.migrations() == previous.humanPopulation.migrations();
    }
    private void validateInfectionTransition(FrontierWorldState previous) {
        FrontierInfectionFrontier.InfectionChange change = FrontierWorldStateSupport.infectionChange(infection, bootstrap.bounds()).orElse(null);
        PersistentInfectionMap before = FrontierWorldStateSupport.persistentInfection(previous.infection);
        PersistentInfectionMap after = FrontierWorldStateSupport.persistentInfection(infection);
        if (change == null || !after.directlyFollows(before, change.cell(), change.previousRaw(), change.nextRaw())) {
            validateComplete(); return;
        }
        FrontierWorldStateSupport.requirePosition(bootstrap.bounds(), change.cell().originAtY(0));
        FixedRatio current = infection.get(change.cell());
        if ((change.nextRaw() == 0L) != (current == null)
                || current != null && (current.value().raw() != change.nextRaw() || current.value().raw() == 0L)
                || raw(previous.infection.get(change.cell())) != change.previousRaw()) {
            throw new IllegalArgumentException("infection transition does not match its retained sparse-field delta");
        }
    }
    private boolean onlyInfectionChangedFrom(FrontierWorldState previous) {
        return bootstrap == previous.bootstrap && actorLocations == previous.actorLocations && structureConditions == previous.structureConditions
                && inventory == previous.inventory && productionJobs == previous.productionJobs && shipments == previous.shipments
                && unitGroups == previous.unitGroups && transportFleet == previous.transportFleet && extractionSites == previous.extractionSites &&
                        physicalIntents == previous.physicalIntents && physicalObservations == previous.physicalObservations
                && sceneLeases == previous.sceneLeases && hiveColony == previous.hiveColony && structureDamage == previous.structureDamage
                && physicalDeltas == previous.physicalDeltas && ambientLeases == previous.ambientLeases && routeConstructions == previous.routeConstructions
                && routeTopology == previous.routeTopology && strategicPlans == previous.strategicPlans && humanPopulation == previous.humanPopulation && companies == previous.companies
                && resourceSites == previous.resourceSites && replicaCustody == previous.replicaCustody && deferredAftermath == previous.deferredAftermath && fencedRecovery == previous.fencedRecovery &&
                        diagnosticIncidents == previous.diagnosticIncidents && actorMovements == previous.actorMovements && actorExecutions == previous.actorExecutions && infection != previous.infection;
    }
    private static long raw(FixedRatio ratio) { return ratio == null ? 0L : ratio.value().raw(); } private static boolean fullValidationDeferred() { return DEFERRED_FULL_VALIDATION_DEPTH.get() > 0; }
    FrontierWorldState next(Map<SubjectId, ActorLocation> actors, Map<SubjectId, StructureCondition> structures,
                            Map<InfectionCell, FixedRatio> nextInfection, ExactInventory nextInventory, Map<SubjectId, ProductionJob> jobs,
                            Map<PhysicalIntentId, PhysicalIntent> intents, Map<PhysicalObservationId, PhysicalEffectObservation> observations,
                            Map<SceneLeaseId, SceneLease> leases, HiveColony colony, Map<SubjectId, StructureDamage> damage,
                            Map<BlockPosition, PhysicalDelta> deltas, Map<SubjectId, AmbientActorLease> ambient) {
        return new FrontierWorldState(bootstrap, actors, structures, nextInfection, nextInventory, jobs, serviceWorks, intents, observations, leases, colony, damage, deltas, ambient, routeConstructions, routeMaintenances,
                routeTopology, strategicPlans, humanPopulation, companies, resourceSites, replicaCustody, deferredAftermath,
                fencedRecovery, diagnosticIncidents, actorMovements, actorExecutions, shipments, unitGroups, transportFleet, extractionSites);
    }
    public FrontierWorldState withChanges(FrontierWorldStateUpdate change) {
        change = Objects.requireNonNull(change, "state change");
        return new FrontierWorldState(bootstrap, change.actorLocations(this), change.structureConditions(this), change.infection(this),
                change.inventory(this), change.productionJobs(this), change.serviceWorks(this),
                change.physicalIntents(this), change.physicalObservations(this), change.sceneLeases(this), change.hiveColony(this),
                change.structureDamage(this), change.physicalDeltas(this), change.ambientLeases(this), change.routeConstructions(this), change.routeMaintenances(this),
                change.routeTopology(this), change.strategicPlans(this), change.humanPopulation(this), change.companies(this), change.resourceSites(this), change.replicaCustody(this), change.deferredAftermath(this),
                        change.fencedRecovery(this), change.diagnosticIncidents(this), change.actorMovements(this), change.actorExecutions(this),
                        change.shipments(this), change.unitGroups(this), change.transportFleet(this), change.extractionSites(this));
    }
    public FrontierWorldState withRouteTopology(RouteTopology nextTopology) { return withChanges(FrontierWorldStateUpdate.begin().routeTopology(nextTopology)); }
    public FrontierWorldState withStrategicPlans(StrategicPlanState nextPlans) { return withChanges(FrontierWorldStateUpdate.begin().strategicPlans(nextPlans)); }
    public FrontierWorldState withResourceSites(ResourceSiteState nextSites) { return withChanges(FrontierWorldStateUpdate.begin().resourceSites(nextSites)); }
    public FrontierWorldState withHumanPopulation(HumanPopulation nextPopulation) { return withChanges(FrontierWorldStateUpdate.begin().humanPopulation(nextPopulation)); }
    public FrontierWorldState withCompanies(CompanyRegistry nextCompanies) { return withChanges(FrontierWorldStateUpdate.begin().companies(nextCompanies)); }
    /** Reducer-only retention update; this index has no behavioral or recovery authority. */
    public FrontierWorldState withDiagnosticIncidents(DiagnosticIncidentIndex nextIndex) {
        return withChanges(FrontierWorldStateUpdate.begin().diagnosticIncidents(nextIndex));
    }
    public FrontierWorldState registerCompany(Company company) {
        Objects.requireNonNull(company, "company");
        EconomicLedger economics = inventory.economics().register(new EconomicAccount(company.id(), EconomicOwnerKind.COMPANY,
                EconomicAccountStatus.ACTIVE, io.farfrontier.palemirror.frontier.v3.api.FixedScalar.ZERO,
                io.farfrontier.palemirror.frontier.v3.api.FixedScalar.ZERO));
        var registered = companies.register(company);
        var participants = GoodsParticipantDeclarations.registerCompany(bootstrap, registered.goodsTrade().participants(), company);
        return withChanges(FrontierWorldStateUpdate.begin().inventory(inventory.withEconomics(economics))
                .companies(registered.withGoodsTrade(registered.goodsTrade().withParticipants(participants))));
    }
    public FrontierWorldState openEmployment(EmploymentContract contract) {
        Objects.requireNonNull(contract, "employment contract");
        WorkEmployer employer = contract.employer();
        employer.validate(inventory.economics(), companies.companies());
        FrontierWorldStateSupport.settlement(bootstrap, employer.settlementId());
        ResidentProfile resident = humanPopulation.resident(contract.residentId());
        if (resident == null || !resident.settlementId().equals(employer.settlementId())
                || humanPopulation.migrations().containsKey(resident.id())) {
            throw new IllegalArgumentException("employment must bind a resident in its declared employer's settlement");
        }
        return withCompanies(companies.openEmployment(contract));
    }
    public FrontierWorldState recordResidentMigration(ResidentMigrated migration) { return HumanPopulationStateSupport.recordMigration(this, migration); }
    public ResourceSite resourceSite(SubjectId id) { return resourceSites.descriptor(bootstrap, id); }
    public Map<SubjectId, ResourceSite> resourceSiteDescriptors() { return resourceSites.descriptors(bootstrap); }
    public FrontierWorldState startResidentBirth(ResidentBirthJob job) { return HumanPopulationStateSupport.startBirth(this, job); }
    public FrontierWorldState completeResidentBirth(ResidentBirthJob job) { return HumanPopulationStateSupport.completeBirth(this, job); }
    public FrontierWorldState cancelResidentBirth(SubjectId jobId) { return HumanPopulationStateSupport.cancelBirth(this, jobId); }
    public List<SettlementAssaultSceneCandidate> coldSettlementAssaultSceneCandidates() { return FrontierSettlementAssaultSceneSupport.candidates(this); }
    public FrontierWorldState withActorBody(SubjectId actor, BodyPosition body) { return withActorBody(actor, body, strategicPlans); }
    public FrontierWorldState withActorBody(SubjectId actor, BodyPosition body, StrategicPlanState nextPlans) {
        Objects.requireNonNull(actor, "actor"); Objects.requireNonNull(body, "actor body"); FrontierWorldStateSupport.requirePosition(bootstrap.bounds(), body.supportingSurface().support());
        if (!actorLocations.containsKey(actor)) throw new IllegalArgumentException("unknown actor: " + actor.value());
        Map<SubjectId, ActorLocation> next = new LinkedHashMap<>(actorLocations); next.put(actor, actorLocations.get(actor).withBody(body));
        return withChanges(FrontierWorldStateUpdate.begin().actorLocations(next).strategicPlans(nextPlans));
    }
    public FrontierWorldState withStructureCondition(SubjectId structure, StructureCondition condition) {
        Objects.requireNonNull(structure, "structure"); Objects.requireNonNull(condition, "structure condition");
        if (!structureConditions.containsKey(structure)) throw new IllegalArgumentException("unknown structure: " + structure.value());
        Map<SubjectId, StructureCondition> next = new LinkedHashMap<>(structureConditions); next.put(structure, condition);
        return withChanges(FrontierWorldStateUpdate.begin().structureConditions(next)
                .resourceSites(resourceSitesForCondition(structure, condition)));
    }
    ResourceSiteState resourceSitesForCondition(SubjectId facilityId, StructureCondition condition) {
        if (condition != StructureCondition.DESTROYED) return resourceSites;
        return resourceSiteDescriptors().values().stream().filter(site -> site.facilityId().equals(facilityId)).findFirst()
                .map(site -> resourceSites.replace(resourceSites.site(site.id()).destroyed())).orElse(resourceSites);
    }
    public FrontierWorldState recordStructureDamage(StructureDamaged damage) { return FrontierWorldPhysicalDeltaSupport.recordStructureDamage(this, damage); }
    public FrontierWorldState recordPhysicalDelta(PhysicalDelta delta) { return FrontierWorldPhysicalDeltaSupport.record(this, delta); }
    public boolean isHiveOrganOperational(SubjectId organId) { return FrontierWorldPhysicalDeltaSupport.organOperational(bootstrap, hiveColony, physicalDeltas, organId); }
    public FrontierWorldState withInfection(InfectionCell cell, FixedRatio intensity) {
        Objects.requireNonNull(cell, "infection cell"); Objects.requireNonNull(intensity, "infection intensity");
        FrontierWorldStateSupport.requirePosition(bootstrap.bounds(), cell.originAtY(0));
        PersistentInfectionMap next = FrontierWorldStateSupport.persistentInfection(infection)
                .changed(cell, intensity.equals(ZERO_INFECTION) ? null : intensity);
        FrontierInfectionFrontier frontier = FrontierWorldStateSupport.infectionFrontier(infection, bootstrap.bounds()).changed(infection, next, cell);
        return next(actorLocations, structureConditions, FrontierWorldStateSupport.infectionMap(next,
                frontier), inventory, productionJobs, physicalIntents, physicalObservations, sceneLeases, hiveColony,
                structureDamage, physicalDeltas, ambientLeases);
    }
    public FrontierWorldState withInventory(ExactInventory nextInventory) {
        return next(actorLocations, structureConditions, infection, nextInventory, productionJobs, physicalIntents, physicalObservations, sceneLeases, hiveColony, structureDamage, physicalDeltas, ambientLeases);
    }
    /** A COLD job reserves its former source slot even though its exact stack is held by the job. */
    public boolean productionHoldReserves(InventoryCustody.ContainerSlot slot) {
        Objects.requireNonNull(slot, "container slot");
        return productionJobs.values().stream().map(ProductionJob::inputHold).filter(ProductionInputHold.Cold.class::isInstance)
                .map(ProductionInputHold.Cold.class::cast).map(ProductionInputHold.Cold::item)
                .anyMatch(item -> item.custody().equals(slot));
    }
    /** An admitted harvest owns its declared output slot until terminal custody replaces the job. */
    public boolean harvestOutputReserves(InventoryCustody.ContainerSlot slot) {
        Objects.requireNonNull(slot, "container slot");
        return resourceSites.sites().values().stream()
                .flatMap(site -> site.harvestJobs().values().stream())
                .anyMatch(job -> job.reservesOutputCapacity() && job.outputSlot().equals(slot)
                        || job.batchSuccessorSlot().filter(slot::equals).isPresent());
    }
    /** Derived from durable jobs: no parallel storage ledger can drift from its owner. */
    public Set<Integer> reservedContainerSlots(SubjectId containerId) {
        return ContainerPhysicalReservations.slots(this, containerId);
    }
    public boolean canReceiveFungible(SubjectId containerId, String itemKind, int quantity) {
        return ContainerStorageAdmission.receive(this, containerId, itemKind, quantity, Optional.empty());
    }
    public boolean canReceiveFungibleOutput(SubjectId containerId, String itemKind, int quantity, SubjectId owner) {
        return ContainerStorageAdmission.receive(this, containerId, itemKind, quantity, Optional.of(owner));
    }
    public boolean canReceiveFungibleCargo(SubjectId cargoId, SubjectId containerId) {
        return ContainerStorageAdmission.receiveCargo(this, cargoId, containerId);
    }
    public boolean containerSlotAvailable(InventoryCustody.ContainerSlot slot) {
        return ContainerStorageAdmission.available(this, slot, Optional.empty(), this::productionHoldReserves);
    }
    public boolean containerSlotAvailableForOutput(InventoryCustody.ContainerSlot slot, SubjectId owner) {
        return ContainerStorageAdmission.available(this, slot, Optional.of(owner), this::productionHoldReserves);
    }
    public java.util.OptionalInt firstFreeContainerSlot(SubjectId containerId) {
        return ContainerStorageAdmission.first(this, containerId, Optional.empty(), this::productionHoldReserves);
    }
    public java.util.OptionalInt firstFreeContainerSlotForOutput(SubjectId containerId, SubjectId completingOwner) {
        return ContainerStorageAdmission.first(this, containerId, Optional.of(completingOwner), this::productionHoldReserves);
    }
    public Map<String, Long> pendingContainerInbound(SubjectId containerId) {
        return ContainerStorageAdmission.inbound(this, containerId, Optional.empty());
    }
    private static void requireJobTask(StrategicPlanState plans, SubjectId taskId, SubjectId ownerId,
                                       StrategicTaskKind kind, String jobKind) {
        StrategicTask task = plans.tasks().get(taskId);
        if (task == null || task.kind() != kind || !task.ownerId().equals(ownerId)
                || task.status() != StrategicTaskStatus.ACTIVE && task.status() != StrategicTaskStatus.BLOCKED) {
            throw new IllegalArgumentException(jobKind + " job must retain its exact active or blocked strategic task: " + taskId.value());
        }
    }

    public FrontierWorldState withProductionJob(ProductionJob job) { return ProductionJobStateSupport.startMaterialized(this, job); }
    public FrontierWorldState startProductionJob(ProductionJob job, SubjectId inputItemId) { return ProductionJobStateSupport.start(this, job, inputItemId); }
    public FrontierWorldState completeProductionJob(SubjectId jobId, ExactItemStack output) { return ProductionJobStateSupport.complete(this, jobId, output); }
    /** Commits a COLD recipe by replacing its reserved fungible lot inside the same account. */
    public FrontierWorldState completeFungibleProductionJob(SubjectId jobId, ResourceLot output) { return ProductionJobStateSupport.completeFungible(this, jobId, output); }
    /** Starts a fungible COLD/HOT job by recording only its allocation, never removing a stack identity. */
    public FrontierWorldState startFungibleProductionJob(ProductionJob job) { return ProductionJobStateSupport.startFungible(this, job); }
    public FrontierWorldState cancelProductionJob(SubjectId jobId) { return ProductionJobStateSupport.cancel(this, jobId); }
    public FrontierWorldState preparePhysicalIntent(PhysicalIntent intent) {
        Objects.requireNonNull(intent, "physical intent");
        if (physicalIntents.containsKey(intent.id())) throw new IllegalArgumentException("physical intent identity already exists: " + intent.id().value());
        Map<PhysicalIntentId, PhysicalIntent> next = new LinkedHashMap<>(physicalIntents);
        next.put(intent.id(), intent);
        return next(actorLocations, structureConditions, infection, inventory, productionJobs, next, physicalObservations, sceneLeases, hiveColony, structureDamage, physicalDeltas, ambientLeases);
    }
    /**
     * Package-local fixture seam. Production lifecycle reductions must reach
     * {@link PhysicalIntentTransitionStorage} through the composed owner capability.
     * It deliberately records only typed storage and never chooses a family reducer.
     */
    FrontierWorldState transitionPhysicalIntent(PhysicalIntentId intentId, PhysicalIntentStatus nextStatus,
                                                java.util.Optional<PhysicalEffectObservation> observation) {
        PhysicalIntent current = physicalIntents.get(Objects.requireNonNull(intentId, "physical intent id"));
        if (current == null) throw new IllegalArgumentException("unknown physical intent: " + intentId.value());
        return PhysicalIntentTransitionStorage.reduce(this, current,
                new PhysicalIntentTransition(intentId, nextStatus, observation),
                PhysicalIntentTransitionStorage::recordConfirmed,
                PhysicalIntentTransitionStorage::recordUnknown);
    }
    public FrontierWorldState prepareSceneLease(SceneLease lease) { return FrontierSceneLeaseStateSupport.prepare(this, lease); }
    /** Atomically records loaded-body evidence, closes ambient authority and prepares one scene. */ public FrontierWorldState handoffAmbientScene(SceneLeaseHandoff handoff) { return FrontierSceneLeaseStateSupport.handoff(this, handoff); }
    public FrontierWorldState transitionSceneLease(SceneLeaseId leaseId, SceneLeaseStatus nextStatus) { return FrontierSceneLeaseStateSupport.transition(this, Objects.requireNonNull(leaseId, "scene lease id"), nextStatus); }
    public FrontierWorldState releaseSceneLease(SceneLeaseId leaseId, java.util.List<SceneMemberPosition> positions) { return FrontierSceneLeaseStateSupport.release(this, Objects.requireNonNull(leaseId, "scene lease id"), positions); }
    public FrontierWorldState addHiveOrgan(HiveOrgan organ) {
        Objects.requireNonNull(organ, "hive organ");
        if (bootstrap.hive().organs().stream().anyMatch(existing -> existing.id().equals(organ.id()))) throw new IllegalArgumentException("added organ collides with bootstrap identity");
        if (organ.containerId().isPresent() && !inventory.containers().containsKey(organ.containerId().orElseThrow())) throw new IllegalArgumentException("added store organ needs an exact canonical container");
        return next(actorLocations, structureConditions, infection, inventory, productionJobs, physicalIntents, physicalObservations, sceneLeases, hiveColony.addOrgan(organ), structureDamage, physicalDeltas, ambientLeases);
    } public FrontierWorldState spawnBioform(Bioform bioform) {
        Objects.requireNonNull(bioform, "bioform"); if (actorLocations.containsKey(bioform.id())) throw new IllegalArgumentException("spawned bioform collides with actor identity");
        Map<SubjectId, ActorLocation> nextActors = new LinkedHashMap<>(actorLocations); nextActors.put(bioform.id(), ActorLocation.standingOn(new SurfaceAnchor(bioform.position()), ActorKind.BIOFORM));
        return next(nextActors, structureConditions, infection, inventory, productionJobs, physicalIntents, physicalObservations, sceneLeases, hiveColony.spawn(bioform), structureDamage, physicalDeltas, ambientLeases);
    }
    public FrontierWorldState startHiveGrowth(HiveGrowthJob job) { return HiveGrowthStateSupport.start(this, job); } public FrontierWorldState completeHiveGrowth(SubjectId jobId) { return HiveGrowthStateSupport.complete(this, jobId); }
    public FrontierWorldState consumeHiveGrowthBiomass(SubjectId jobId, SubjectId itemId) { return HiveGrowthStateSupport.consume(this, jobId, itemId); }
    public FrontierWorldState startFungibleHiveGrowth(HiveGrowthJob job) { return HiveGrowthStateSupport.startFungible(this, job); }
    public FrontierWorldState consumeFungibleHiveGrowthBiomass(SubjectId jobId) { return HiveGrowthStateSupport.consumeFungible(this, jobId); }
    public FrontierWorldState cancelHiveGrowth(SubjectId jobId) { return HiveGrowthStateSupport.cancel(this, jobId); }
    public boolean isHiveStore(SubjectId containerId) { return HiveStorageSupport.isOperationalStore(this, containerId); }
    public static SubjectId depotId(SubjectId settlementId) {
        Objects.requireNonNull(settlementId, "settlement id"); if (!settlementId.value().startsWith("settlement:")) throw new IllegalArgumentException("settlement id must use settlement: namespace");
        return new SubjectId("container:" + settlementId.value().substring("settlement:".length()) + "-depot");
    }
}
