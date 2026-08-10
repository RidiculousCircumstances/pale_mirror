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
        DomainCommand.PlayerEnteredFacility, DomainCommand.ThreatControllerDestroyed,
        DomainCommand.MaterializationObserved, DomainCommand.NoScenario,
        DomainCommand.SetScenarioBlocked, DomainCommand.ActivateGate,
        DomainCommand.BypassGate, DomainCommand.GatePartDestroyed,
        DomainCommand.ValidateRouteContract, DomainCommand.ObserveSettlementPlace,
        DomainCommand.RegisterLivingRegion, DomainCommand.DiscoverLivingRegion,
        DomainCommand.TriggerFacilityInfection, DomainCommand.DepositResource,
        DomainCommand.WithdrawResource, DomainCommand.BeginSettlementEvacuation,
        DomainCommand.StartDevelopmentIntent, DomainCommand.CompleteDevelopmentIntent,
        DomainCommand.CancelDevelopmentIntent {

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
                                List<RouteContract> routeContracts, List<PopulationGroup> populationGroups) implements DomainCommand {
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
        }
    }

    record DiscoverLivingRegion(String regionId, StoryAudienceId audience, String causationId) implements DomainCommand {
        public DiscoverLivingRegion {
            Objects.requireNonNull(regionId, "regionId");
            Objects.requireNonNull(audience, "audience");
            Objects.requireNonNull(causationId, "causationId");
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

    record StartDevelopmentIntent(String intentId) implements DomainCommand {
        public StartDevelopmentIntent { Objects.requireNonNull(intentId, "intentId"); }
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
}
