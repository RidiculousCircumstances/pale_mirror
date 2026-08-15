package io.farfrontier.palemirror.domain;

import java.util.Objects;
import java.util.List;

/**
 * The only inputs that may make durable changes to a {@link WorldState}.
 * Adapters and Minecraft observers supply facts; the NeoForge bridge translates
 * those facts into these commands before the domain model is touched.
 */
public sealed interface DomainCommand permits DomainCommand.AdvanceSimulation,
        DomainCommand.OfferScenario, DomainCommand.AcceptScenario,
        DomainCommand.DeclineScenario, DomainCommand.CancelScenario,
        DomainCommand.PlayerEnteredFacility, DomainCommand.ThreatControllerDestroyed,
        DomainCommand.MaterializationObserved, DomainCommand.NoScenario,
        DomainCommand.SetScenarioBlocked, DomainCommand.ActivateGate,
        DomainCommand.BypassGate, DomainCommand.GatePartDestroyed,
        DomainCommand.ValidateRouteContract, DomainCommand.ObserveSettlementPlace,
        DomainCommand.RegisterLivingRegion, DomainCommand.DiscoverLivingRegion,
        DomainCommand.DiscoverRegionalFeature,
        DomainCommand.ObserveAudienceRegionAccess,
        DomainCommand.TriggerFacilityInfection, DomainCommand.DepositResource,
        DomainCommand.WithdrawResource, DomainCommand.BeginSettlementEvacuation,
        DomainCommand.RegisterEvacuationShelter,
        DomainCommand.RegisterAutonomousRefugeeShelter, DomainCommand.SetWorldSiteOperational,
        DomainCommand.StartDevelopmentIntent, DomainCommand.CompleteDevelopmentIntent,
        DomainCommand.PlanAlternateDispatch,
        DomainCommand.ContributeDevelopmentIntent,
        DomainCommand.CancelDevelopmentIntent, DomainCommand.RegisterSettlementAuthorityProfile,
        DomainCommand.BlockDevelopmentIntent,
        DomainCommand.ReconcileSettlementPopulation,
        DomainCommand.ConfirmSettlementResidentDeath,
        DomainCommand.ObserveJourneyCheckpoint, DomainCommand.ObserveJourneyBlocked,
        DomainCommand.RegisterFacility, DomainCommand.RegisterWorldSite,
        DomainCommand.RegisterDevelopmentIntent, DomainCommand.ResetWorldState,
        DomainCommand.EvaluateNarrativeCandidates {

    record AdvanceSimulation(int steps) implements DomainCommand { }

    record OfferScenario(DomainEvent sourceEvent, StoryAudienceId audience, ScenarioDefinitionRef definition) implements DomainCommand {
        public OfferScenario {
            Objects.requireNonNull(sourceEvent, "sourceEvent");
            Objects.requireNonNull(audience, "audience");
            Objects.requireNonNull(definition, "definition");
        }
    }

    record AcceptScenario(String scenarioId) implements DomainCommand {
        public AcceptScenario { Objects.requireNonNull(scenarioId, "scenarioId"); }
    }

    record DeclineScenario(String scenarioId) implements DomainCommand {
        public DeclineScenario { Objects.requireNonNull(scenarioId, "scenarioId"); }
    }

    /** System-side retirement of an offer whose canonical eligibility no longer holds. */
    record CancelScenario(String scenarioId, String reason) implements DomainCommand {
        public CancelScenario {
            Objects.requireNonNull(scenarioId, "scenarioId");
            if (reason == null || reason.isBlank()) throw new IllegalArgumentException("reason is required");
        }
    }

    record PlayerEnteredFacility(StoryAudienceId audience, WorldObjectId facilityId) implements DomainCommand {
        public PlayerEnteredFacility {
            Objects.requireNonNull(audience, "audience");
            Objects.requireNonNull(facilityId, "facilityId");
        }
    }

    record ThreatControllerDestroyed(WorldObjectId facilityId, String causationId) implements DomainCommand {
        public ThreatControllerDestroyed {
            Objects.requireNonNull(facilityId, "facilityId");
            Objects.requireNonNull(causationId, "causationId");
        }
    }

    record MaterializationObserved(WorldObjectId facilityId, long desiredRevision, String causationId) implements DomainCommand {
        public MaterializationObserved {
            Objects.requireNonNull(facilityId, "facilityId");
            Objects.requireNonNull(causationId, "causationId");
        }
    }

    record NoScenario(DomainEvent sourceEvent, StoryAudienceId audience, String reason) implements DomainCommand {
        public NoScenario {
            Objects.requireNonNull(sourceEvent, "sourceEvent");
            Objects.requireNonNull(audience, "audience");
            Objects.requireNonNull(reason, "reason");
        }
    }

    record SetScenarioBlocked(String scenarioId, boolean blocked, String reason) implements DomainCommand {
        public SetScenarioBlocked {
            Objects.requireNonNull(scenarioId, "scenarioId");
            Objects.requireNonNull(reason, "reason");
        }
    }

    record ActivateGate(WorldObjectId facilityId, GatePlanRef plan, String causationId) implements DomainCommand {
        public ActivateGate {
            Objects.requireNonNull(facilityId, "facilityId");
            Objects.requireNonNull(plan, "plan");
            Objects.requireNonNull(causationId, "causationId");
        }
    }

    record BypassGate(WorldObjectId facilityId, String causationId) implements DomainCommand {
        public BypassGate {
            Objects.requireNonNull(facilityId, "facilityId");
            Objects.requireNonNull(causationId, "causationId");
        }
    }

    record GatePartDestroyed(WorldObjectId facilityId, String slotId, String causationId) implements DomainCommand {
        public GatePartDestroyed {
            Objects.requireNonNull(facilityId, "facilityId");
            Objects.requireNonNull(slotId, "slotId");
            Objects.requireNonNull(causationId, "causationId");
        }
    }

    record ValidateRouteContract(WorldObjectId contractId, int capacity, long observedStep,
                                 String observationId, String causationId) implements DomainCommand {
        public ValidateRouteContract {
            Objects.requireNonNull(contractId, "contractId");
            Objects.requireNonNull(observationId, "observationId");
            Objects.requireNonNull(causationId, "causationId");
            if (capacity < 0 || observedStep < 0) throw new IllegalArgumentException("Invalid route validation");
        }
    }

    record ObserveSettlementPlace(WorldObjectId placeId, ObservationFreshness freshness,
                                  EvidenceReliability reliability, int registeredGuards,
                                  StructuralIntegrity structuralIntegrity,
                                  String observationId, String causationId) implements DomainCommand {
        public ObserveSettlementPlace(WorldObjectId placeId, ObservationFreshness freshness,
                                      EvidenceReliability reliability, int registeredGuards,
                                      String observationId, String causationId) {
            this(placeId, freshness, reliability, registeredGuards, StructuralIntegrity.INTACT,
                    observationId, causationId);
        }
        public ObserveSettlementPlace {
            Objects.requireNonNull(placeId, "placeId");
            Objects.requireNonNull(freshness, "freshness");
            Objects.requireNonNull(reliability, "reliability");
            Objects.requireNonNull(structuralIntegrity, "structuralIntegrity");
            Objects.requireNonNull(observationId, "observationId");
            Objects.requireNonNull(causationId, "causationId");
            if (registeredGuards < 0) throw new IllegalArgumentException("Registered guards must not be negative");
        }
    }

    record RegisterLivingRegion(LivingRegionState region, List<FacilityState> facilities,
                                SettlementCommunity community, SettlementPlace place,
                                CommunityPlaceBinding binding, SettlementEconomy economy,
                                SettlementSecurity security, SettlementPolicy policy, List<WorldSite> sites,
                                List<SiteAffiliation> affiliations, List<SiteCapability> capabilities,
                                List<RouteContract> routeContracts, List<PopulationGroup> populationGroups,
                                List<WorldPath> worldPaths, SettlementDevelopment development,
                                SettlementDevelopmentPolicy developmentPolicy,
                                SettlementAuthorityProfile authorityProfile) implements DomainCommand {
        public RegisterLivingRegion(LivingRegionState region, List<FacilityState> facilities,
                                    SettlementCommunity community, SettlementPlace place,
                                    CommunityPlaceBinding binding, SettlementEconomy economy,
                                    SettlementSecurity security, SettlementPolicy policy, List<WorldSite> sites,
                                    List<SiteAffiliation> affiliations, List<SiteCapability> capabilities,
                                    List<RouteContract> routeContracts, List<PopulationGroup> populationGroups) {
            this(region, facilities, community, place, binding, economy, security, policy, sites, affiliations,
                    capabilities, routeContracts, populationGroups, List.of(), defaultDevelopment(community, populationGroups),
                    SettlementDevelopmentPolicy.defaults(community.id()), SettlementAuthorityProfile.pmManaged(community.id()));
        }
        public RegisterLivingRegion(LivingRegionState region, List<FacilityState> facilities,
                                    SettlementCommunity community, SettlementPlace place,
                                    CommunityPlaceBinding binding, SettlementEconomy economy,
                                    SettlementSecurity security, SettlementPolicy policy, List<WorldSite> sites,
                                    List<SiteAffiliation> affiliations, List<SiteCapability> capabilities,
                                    List<RouteContract> routeContracts, List<PopulationGroup> populationGroups,
                                    List<WorldPath> worldPaths) {
            this(region, facilities, community, place, binding, economy, security, policy, sites, affiliations,
                    capabilities, routeContracts, populationGroups, worldPaths, defaultDevelopment(community, populationGroups),
                    SettlementDevelopmentPolicy.defaults(community.id()), SettlementAuthorityProfile.pmManaged(community.id()));
        }
        public RegisterLivingRegion {
            Objects.requireNonNull(region, "region");
            facilities = List.copyOf(facilities);
            Objects.requireNonNull(community, "community");
            Objects.requireNonNull(place, "place");
            Objects.requireNonNull(binding, "binding");
            Objects.requireNonNull(economy, "economy");
            Objects.requireNonNull(security, "security");
            Objects.requireNonNull(policy, "policy");
            sites = List.copyOf(sites);
            affiliations = List.copyOf(affiliations);
            capabilities = List.copyOf(capabilities);
            routeContracts = List.copyOf(routeContracts);
            populationGroups = List.copyOf(populationGroups);
            worldPaths = List.copyOf(worldPaths);
            Objects.requireNonNull(development, "development");
            Objects.requireNonNull(developmentPolicy, "developmentPolicy");
            Objects.requireNonNull(authorityProfile, "authorityProfile");
        }
    }

    record RegisterFacility(FacilityState facility, String causationId) implements DomainCommand {
        public RegisterFacility {
            Objects.requireNonNull(facility, "facility");
            Objects.requireNonNull(causationId, "causationId");
        }
    }

    record RegisterWorldSite(WorldSite site, List<SiteAffiliation> affiliations,
                             List<SiteCapability> capabilities, String causationId) implements DomainCommand {
        public RegisterWorldSite {
            Objects.requireNonNull(site, "site");
            affiliations = List.copyOf(affiliations);
            capabilities = List.copyOf(capabilities);
            Objects.requireNonNull(causationId, "causationId");
        }
    }

    record RegisterDevelopmentIntent(DevelopmentIntent intent, String causationId) implements DomainCommand {
        public RegisterDevelopmentIntent {
            Objects.requireNonNull(intent, "intent");
            Objects.requireNonNull(causationId, "causationId");
        }
    }

    /** Player-approved use of canonical settlement reserves for an alternate freight works. */
    record PlanAlternateDispatch(WorldObjectId communityId, WorldObjectId dispatchSiteId,
                                 int requiredIron, String causationId) implements DomainCommand {
        public PlanAlternateDispatch {
            Objects.requireNonNull(communityId, "communityId");
            Objects.requireNonNull(dispatchSiteId, "dispatchSiteId");
            Objects.requireNonNull(causationId, "causationId");
            if (requiredIron <= 0) throw new IllegalArgumentException("requiredIron must be positive");
        }
    }

    /** Debug/test-only reset, guarded by NeoForge physical-state preconditions. */
    record ResetWorldState(String causationId) implements DomainCommand {
        public ResetWorldState { Objects.requireNonNull(causationId, "causationId"); }
    }

    record EvaluateNarrativeCandidates(StoryAudienceId audience,
                                       List<NarrativeCandidate> candidates) implements DomainCommand {
        public EvaluateNarrativeCandidates {
            Objects.requireNonNull(audience, "audience");
            candidates = List.copyOf(candidates);
        }
    }

    record DiscoverLivingRegion(String regionId, StoryAudienceId audience, String causationId) implements DomainCommand {
        public DiscoverLivingRegion {
            Objects.requireNonNull(regionId, "regionId");
            Objects.requireNonNull(audience, "audience");
            Objects.requireNonNull(causationId, "causationId");
        }
    }

    record DiscoverRegionalFeature(String regionId, StoryAudienceId audience,
                                   KnownRegionalFeature feature, String causationId) implements DomainCommand {
        public DiscoverRegionalFeature {
            Objects.requireNonNull(regionId, "regionId");
            Objects.requireNonNull(audience, "audience");
            Objects.requireNonNull(feature, "feature");
            Objects.requireNonNull(causationId, "causationId");
            if (regionId.isBlank() || causationId.isBlank()) throw new IllegalArgumentException("Blank discovery identity");
        }
    }

    record ObserveAudienceRegionAccess(String regionId, StoryAudienceId audience,
                                       AudienceRegionReachability reachability, boolean present,
                                       long observedStep, String observationId) implements DomainCommand {
        public ObserveAudienceRegionAccess {
            Objects.requireNonNull(regionId, "regionId");
            Objects.requireNonNull(audience, "audience");
            Objects.requireNonNull(reachability, "reachability");
            Objects.requireNonNull(observationId, "observationId");
            if (regionId.isBlank() || observationId.isBlank() || observedStep < 0) {
                throw new IllegalArgumentException("Invalid audience access observation");
            }
        }
    }

    record TriggerFacilityInfection(WorldObjectId facilityId, String causationId) implements DomainCommand {
        public TriggerFacilityInfection {
            Objects.requireNonNull(facilityId, "facilityId");
            Objects.requireNonNull(causationId, "causationId");
        }
    }

    record DepositResource(WorldObjectId communityId, ResourceKind resource, int amount,
                           String transferId) implements DomainCommand {
        public DepositResource {
            Objects.requireNonNull(communityId, "communityId");
            Objects.requireNonNull(resource, "resource");
            Objects.requireNonNull(transferId, "transferId");
            if (amount <= 0 || transferId.isBlank()) throw new IllegalArgumentException("Invalid resource deposit");
        }
    }

    record WithdrawResource(WorldObjectId communityId, ResourceKind resource, int amount,
                            int minimumRemaining, String transferId) implements DomainCommand {
        public WithdrawResource {
            Objects.requireNonNull(communityId, "communityId");
            Objects.requireNonNull(resource, "resource");
            Objects.requireNonNull(transferId, "transferId");
            if (amount <= 0 || minimumRemaining < 0 || transferId.isBlank()) {
                throw new IllegalArgumentException("Invalid resource withdrawal");
            }
        }
    }

    record BeginSettlementEvacuation(WorldObjectId communityId, StoryAudienceId audience,
                                     String causationId) implements DomainCommand {
        public BeginSettlementEvacuation {
            Objects.requireNonNull(communityId, "communityId");
            Objects.requireNonNull(audience, "audience");
            Objects.requireNonNull(causationId, "causationId");
        }
    }

    /** Registers a player-prepared physical shelter before any population moves. */
    record RegisterEvacuationShelter(WorldObjectId communityId, StoryAudienceId audience,
                                     WorldSite shelter, SiteCapability capacity, WorldPath path,
                                     String causationId) implements DomainCommand {
        public RegisterEvacuationShelter {
            Objects.requireNonNull(communityId, "communityId");
            Objects.requireNonNull(audience, "audience");
            Objects.requireNonNull(shelter, "shelter");
            Objects.requireNonNull(capacity, "capacity");
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(causationId, "causationId");
        }
    }

    /** Autonomous fallback used after an unprepared community has already displaced. */
    record RegisterAutonomousRefugeeShelter(WorldObjectId communityId, WorldSite shelter,
                                            SiteCapability capacity, String causationId) implements DomainCommand {
        public RegisterAutonomousRefugeeShelter {
            Objects.requireNonNull(communityId, "communityId");
            Objects.requireNonNull(shelter, "shelter");
            Objects.requireNonNull(capacity, "capacity");
            Objects.requireNonNull(causationId, "causationId");
        }
    }

    record ObserveJourneyCheckpoint(String journeyId, int checkpointIndex, String observationId) implements DomainCommand {
        public ObserveJourneyCheckpoint {
            Objects.requireNonNull(journeyId, "journeyId"); Objects.requireNonNull(observationId, "observationId");
            if (checkpointIndex < 0) throw new IllegalArgumentException("Checkpoint index must not be negative");
        }
    }

    record ObserveJourneyBlocked(String journeyId, String reason, String observationId) implements DomainCommand {
        public ObserveJourneyBlocked {
            Objects.requireNonNull(journeyId, "journeyId"); Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(observationId, "observationId");
        }
    }

    /** Reconciliation result of a persisted PM-owned physical site job. */
    record SetWorldSiteOperational(WorldObjectId siteId, OperationalState state,
                                   String causationId) implements DomainCommand {
        public SetWorldSiteOperational {
            Objects.requireNonNull(siteId, "siteId");
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(causationId, "causationId");
        }
    }

    record StartDevelopmentIntent(String intentId) implements DomainCommand {
        public StartDevelopmentIntent { Objects.requireNonNull(intentId, "intentId"); }
    }
    record ContributeDevelopmentIntent(String intentId, int amount, String transferId) implements DomainCommand {
        public ContributeDevelopmentIntent {
            Objects.requireNonNull(intentId, "intentId");
            Objects.requireNonNull(transferId, "transferId");
            if (intentId.isBlank() || transferId.isBlank() || amount <= 0) {
                throw new IllegalArgumentException("Invalid development contribution");
            }
        }
    }
    record CompleteDevelopmentIntent(String intentId) implements DomainCommand {
        public CompleteDevelopmentIntent { Objects.requireNonNull(intentId, "intentId"); }
    }
    record CancelDevelopmentIntent(String intentId, String reason) implements DomainCommand {
        public CancelDevelopmentIntent {
            Objects.requireNonNull(intentId, "intentId");
            Objects.requireNonNull(reason, "reason");
        }
    }
    record BlockDevelopmentIntent(String intentId, String reason) implements DomainCommand {
        public BlockDevelopmentIntent {
            Objects.requireNonNull(intentId, "intentId");
            Objects.requireNonNull(reason, "reason");
            if (intentId.isBlank() || reason.isBlank()) throw new IllegalArgumentException("Invalid development block");
        }
    }

    record RegisterSettlementAuthorityProfile(SettlementAuthorityProfile profile) implements DomainCommand {
        public RegisterSettlementAuthorityProfile { Objects.requireNonNull(profile, "profile"); }
    }

    record ReconcileSettlementPopulation(WorldObjectId communityId, String populationGroupId,
                                         java.util.Map<SettlementCohort, Integer> cohorts,
                                         String observationId, String causationId) implements DomainCommand {
        public ReconcileSettlementPopulation {
            Objects.requireNonNull(communityId, "communityId");
            Objects.requireNonNull(populationGroupId, "populationGroupId");
            cohorts = java.util.Map.copyOf(cohorts);
            Objects.requireNonNull(observationId, "observationId");
            Objects.requireNonNull(causationId, "causationId");
            if (populationGroupId.isBlank() || observationId.isBlank()) throw new IllegalArgumentException("Population reconciliation identity is blank");
        }
    }

    record ConfirmSettlementResidentDeath(WorldObjectId communityId, String populationGroupId,
                                          SettlementCohort cohort, String residentId,
                                          String observationId) implements DomainCommand {
        public ConfirmSettlementResidentDeath {
            Objects.requireNonNull(communityId, "communityId");
            Objects.requireNonNull(populationGroupId, "populationGroupId");
            Objects.requireNonNull(cohort, "cohort");
            Objects.requireNonNull(residentId, "residentId");
            Objects.requireNonNull(observationId, "observationId");
            if (populationGroupId.isBlank() || residentId.isBlank() || observationId.isBlank()) {
                throw new IllegalArgumentException("Confirmed resident death identity is blank");
            }
        }
    }

    private static SettlementDevelopment defaultDevelopment(SettlementCommunity community,
                                                             List<PopulationGroup> populationGroups) {
        int population = populationGroups.stream().mapToInt(PopulationGroup::size).sum();
        return new SettlementDevelopment(community.id(), 25, 0, population, Math.max(population, 56), 0);
    }
}
