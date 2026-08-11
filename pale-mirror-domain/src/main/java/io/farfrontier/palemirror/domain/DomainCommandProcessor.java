package io.farfrontier.palemirror.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Applies explicit domain commands; it does not know about Minecraft or adapters. */
public final class DomainCommandProcessor {
    private final SimulationEngine simulation;
    private final ResourceFlowSimulation resources;
    private final SettlementDecisionEngine settlementDecisions;
    private final SettlementEmergencyRuntime settlementEmergencies;
    private final SettlementDevelopmentEngine settlementDevelopment;
    private final ThreatLifecycle threats;
    private final Narrator narrator;
    private final ScenarioRuntime scenarios;
    private final SettlementCrisisRuntime settlementCrises;
    private final DomainEventFactory events;

    DomainCommandProcessor(SimulationEngine simulation, ResourceFlowSimulation resources,
                           SettlementDecisionEngine settlementDecisions,
                           SettlementEmergencyRuntime settlementEmergencies,
                           SettlementDevelopmentEngine settlementDevelopment,
                           ThreatLifecycle threats, Narrator narrator,
                           ScenarioRuntime scenarios, SettlementCrisisRuntime settlementCrises, DomainEventFactory events) {
        this.simulation = Objects.requireNonNull(simulation, "simulation");
        this.resources = Objects.requireNonNull(resources, "resources");
        this.settlementDecisions = Objects.requireNonNull(settlementDecisions, "settlementDecisions");
        this.settlementEmergencies = Objects.requireNonNull(settlementEmergencies, "settlementEmergencies");
        this.settlementDevelopment = Objects.requireNonNull(settlementDevelopment, "settlementDevelopment");
        this.threats = Objects.requireNonNull(threats, "threats");
        this.narrator = Objects.requireNonNull(narrator, "narrator");
        this.scenarios = Objects.requireNonNull(scenarios, "scenarios");
        this.settlementCrises = Objects.requireNonNull(settlementCrises, "settlementCrises");
        this.events = Objects.requireNonNull(events, "events");
    }

    public List<DomainEvent> execute(WorldState state, DomainCommand command) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(command, "command");
        return switch (command) {
            case DomainCommand.AdvanceSimulation advance -> advanceSimulation(state, advance.steps());
            case DomainCommand.OfferScenario offer -> narrator.offerFor(state, offer.sourceEvent(), offer.audience(), offer.definition());
            case DomainCommand.AcceptScenario accept -> acceptScenario(state, accept.scenarioId());
            case DomainCommand.DeclineScenario decline -> declineScenario(state, decline.scenarioId());
            case DomainCommand.PlayerEnteredFacility entered -> scenarios.playerEntered(state, entered.audience(), entered.facilityId());
            case DomainCommand.ThreatControllerDestroyed destroyed -> reconcileDestroyedController(state, destroyed);
            case DomainCommand.MaterializationObserved observed -> reconcileMaterialization(state, observed);
            case DomainCommand.NoScenario noScenario -> narrator.noScenario(state, noScenario.sourceEvent(), noScenario.audience(), noScenario.reason());
            case DomainCommand.SetScenarioBlocked capability -> reconcileScenarioCapability(state, capability);
            case DomainCommand.ActivateGate activated -> activateGate(state, activated);
            case DomainCommand.BypassGate bypassed -> bypassGate(state, bypassed);
            case DomainCommand.GatePartDestroyed destroyed -> gatePartDestroyed(state, destroyed);
            case DomainCommand.ValidateRouteContract observed -> validateRouteContract(state, observed);
            case DomainCommand.ObserveSettlementPlace observed -> observeSettlementPlace(state, observed);
            case DomainCommand.RegisterLivingRegion registered -> registerLivingRegion(state, registered);
            case DomainCommand.DiscoverLivingRegion discovered -> discoverLivingRegion(state, discovered);
            case DomainCommand.DiscoverRegionalFeature discovered -> discoverRegionalFeature(state, discovered);
            case DomainCommand.ObserveAudienceRegionAccess observed -> observeAudienceRegionAccess(state, observed);
            case DomainCommand.TriggerFacilityInfection triggered -> triggerFacilityInfection(state, triggered);
            case DomainCommand.DepositResource deposited -> depositResource(state, deposited);
            case DomainCommand.WithdrawResource withdrawn -> withdrawResource(state, withdrawn);
            case DomainCommand.BeginSettlementEvacuation evacuation -> beginSettlementEvacuation(state, evacuation);
            case DomainCommand.RegisterEvacuationShelter shelter -> registerEvacuationShelter(state, shelter);
            case DomainCommand.RegisterAutonomousRefugeeShelter shelter -> registerAutonomousRefugeeShelter(state, shelter);
            case DomainCommand.SetWorldSiteOperational site -> setWorldSiteOperational(state, site);
            case DomainCommand.StartDevelopmentIntent intent -> startDevelopmentIntent(state, intent.intentId());
            case DomainCommand.ContributeDevelopmentIntent contribution -> contributeDevelopmentIntent(state, contribution);
            case DomainCommand.CompleteDevelopmentIntent intent -> completeDevelopmentIntent(state, intent.intentId());
            case DomainCommand.CancelDevelopmentIntent intent -> cancelDevelopmentIntent(state, intent.intentId(), intent.reason());
            case DomainCommand.BlockDevelopmentIntent intent -> blockDevelopmentIntent(state, intent.intentId(), intent.reason());
            case DomainCommand.RegisterSettlementAuthorityProfile authority -> registerAuthorityProfile(state, authority.profile());
            case DomainCommand.ReconcileSettlementPopulation population -> reconcileSettlementPopulation(state, population);
        };
    }

    private List<DomainEvent> advanceSimulation(WorldState state, int steps) {
        if (steps < 0) throw new IllegalArgumentException("Simulation steps must not be negative");
        List<DomainEvent> produced = new ArrayList<>();
        for (int index = 0; index < steps; index++) {
            produced.addAll(simulation.advance(state, 1));
            produced.addAll(resources.reconcile(state));
            produced.addAll(settlementDecisions.reconcile(state));
            produced.addAll(settlementCrises.reconcile(state));
            produced.addAll(settlementEmergencies.reconcile(state));
            List<DomainEvent> developmentEvents = settlementDevelopment.reconcile(state);
            produced.addAll(developmentEvents);
            developmentEvents.stream().filter(event -> event.type() == DomainEventType.SETTLEMENT_RETURNED_HOME)
                    .forEach(event -> produced.addAll(scenarios.reconcileOpportunity(state, event.subject(),
                            ScenarioArchetype.RESETTLEMENT_OPPORTUNITY, "COMMUNITY_RETURNED_HOME")));
        }
        return List.copyOf(produced);
    }

    private List<DomainEvent> acceptScenario(WorldState state, String scenarioId) {
        ScenarioInstance scenario = state.scenario(scenarioId).orElseThrow(() -> new IllegalArgumentException("Unknown scenario " + scenarioId));
        return scenario.archetype() == ScenarioArchetype.SETTLEMENT_SUPPLY_CRISIS
                ? settlementCrises.accept(state, scenario) : scenarios.accept(state, scenarioId);
    }

    private List<DomainEvent> declineScenario(WorldState state, String scenarioId) {
        ScenarioInstance scenario = state.scenario(scenarioId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown scenario " + scenarioId));
        if (!scenario.decline()) return List.of();
        return record(state, DomainEventType.SCENARIO_DECLINED, scenario.target(), scenario.sourceEventId());
    }

    private List<DomainEvent> reconcileScenarioCapability(WorldState state, DomainCommand.SetScenarioBlocked command) {
        ScenarioInstance scenario = state.scenario(command.scenarioId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown scenario " + command.scenarioId()));
        if (scenario.status().isTerminal()) return List.of();
        if (command.blocked() && scenario.block(command.reason())) {
            DomainEvent event = events.create(state, DomainEventType.SCENARIO_BLOCKED, scenario.target(), scenario.sourceEventId());
            state.addEvent(event);
            return List.of(event);
        }
        if (!command.blocked() && scenario.resume()) {
            DomainEvent event = events.create(state, DomainEventType.SCENARIO_RESUMED, scenario.target(), scenario.sourceEventId());
            state.addEvent(event);
            return List.of(event);
        }
        return List.of();
    }

    private List<DomainEvent> reconcileDestroyedController(WorldState state, DomainCommand.ThreatControllerDestroyed command) {
        FacilityState facility = state.facility(command.facilityId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown facility " + command.facilityId()));
        if (!facility.controllerVulnerable()) return List.of();
        List<DomainEvent> produced = new ArrayList<>(threats.controllerDestroyed(state, command.facilityId(), command.causationId()));
        if (!produced.isEmpty()) produced.addAll(scenarios.reconcileRecovery(state, command.facilityId()));
        return List.copyOf(produced);
    }

    private List<DomainEvent> reconcileMaterialization(WorldState state, DomainCommand.MaterializationObserved command) {
        FacilityState facility = state.facility(command.facilityId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown facility " + command.facilityId()));
        if (facility.desiredRevision() != command.desiredRevision()
                || facility.observedRevision() >= command.desiredRevision()) return List.of();
        facility.setObservedRevision(command.desiredRevision());
        DomainEvent event = events.create(state, DomainEventType.MATERIALIZATION_CONFIRMED,
                command.facilityId(), command.causationId());
        state.addEvent(event);
        return List.of(event);
    }

    private List<DomainEvent> activateGate(WorldState state, DomainCommand.ActivateGate command) {
        FacilityState facility = state.facility(command.facilityId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown facility " + command.facilityId()));
        if (!facility.activateGate(command.plan())) return List.of();
        return record(state, DomainEventType.GATE_ACTIVATED, command.facilityId(), command.causationId());
    }

    private List<DomainEvent> bypassGate(WorldState state, DomainCommand.BypassGate command) {
        FacilityState facility = state.facility(command.facilityId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown facility " + command.facilityId()));
        if (!facility.bypassGate()) return List.of();
        return record(state, DomainEventType.GATE_BYPASSED, command.facilityId(), command.causationId());
    }

    private List<DomainEvent> gatePartDestroyed(WorldState state, DomainCommand.GatePartDestroyed command) {
        FacilityState facility = state.facility(command.facilityId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown facility " + command.facilityId()));
        if (!facility.gatePartDestroyed(command.slotId())) return List.of();
        List<DomainEvent> produced = new ArrayList<>(record(state, DomainEventType.GATE_PART_DESTROYED,
                command.facilityId(), command.causationId()));
        if (facility.gate().status() == SourceGateStatus.UNSEALED) {
            produced.addAll(record(state, DomainEventType.CONTROLLER_UNSEALED, command.facilityId(), command.causationId()));
        }
        return List.copyOf(produced);
    }

    private List<DomainEvent> validateRouteContract(WorldState state, DomainCommand.ValidateRouteContract command) {
        RouteContract contract = state.routeContract(command.contractId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown route contract " + command.contractId()));
        if (command.observedStep() > state.simulationStep()) {
            throw new IllegalArgumentException("Route observation cannot come from a future simulation step");
        }
        if (!contract.validate(command.capacity(), command.observedStep(), command.observationId())) return List.of();
        return record(state, command.capacity() > 0 ? DomainEventType.ROUTE_CONTRACT_VALIDATED
                : DomainEventType.ROUTE_CONTRACT_BLOCKED, command.contractId(), command.causationId());
    }

    private List<DomainEvent> observeSettlementPlace(WorldState state, DomainCommand.ObserveSettlementPlace command) {
        SettlementPlace place = state.place(command.placeId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown settlement place " + command.placeId()));
        if (!place.observe(command.observationId(), command.freshness(), command.reliability())) return List.of();
        List<DomainEvent> produced = new ArrayList<>(record(state, DomainEventType.SETTLEMENT_OBSERVATION_RECONCILED,
                command.placeId(), command.causationId()));
        if (place.observeStructuralIntegrity(command.structuralIntegrity())) {
            produced.add(recordEvent(state, command.structuralIntegrity() == StructuralIntegrity.RUINED
                    ? DomainEventType.SETTLEMENT_PLACE_RUINED : DomainEventType.SETTLEMENT_STRUCTURE_DAMAGED,
                    command.placeId(), command.causationId()));
        }
        state.bindingForPlace(command.placeId()).flatMap(binding -> state.security(binding.communityId())).ifPresent(security -> {
            if (security.observeGuards(command.registeredGuards())) {
                produced.add(recordEvent(state, DomainEventType.SETTLEMENT_GUARD_CAPABILITY_CHANGED,
                        security.communityId(), command.causationId()));
            }
        });
        return List.copyOf(produced);
    }

    private List<DomainEvent> registerLivingRegion(WorldState state, DomainCommand.RegisterLivingRegion command) {
        if (state.livingRegion(command.region().id()).isPresent()) return List.of();
        if (!command.community().id().equals(command.region().communityId())
                || !command.place().id().equals(command.region().placeId())
                || !command.binding().communityId().equals(command.community().id())
                || !command.binding().placeId().equals(command.place().id())
                || !command.economy().communityId().equals(command.community().id())
                || !command.security().communityId().equals(command.community().id())
                || !command.policy().communityId().equals(command.community().id())) {
            throw new IllegalArgumentException("Living region actor identities do not match its definition");
        }
        if (command.facilities().stream().noneMatch(facility -> facility.id().equals(command.region().primaryFacilityId()))
                || command.facilities().stream().noneMatch(facility -> facility.id().equals(command.region().alternateFacilityId()))) {
            throw new IllegalArgumentException("Living region facilities do not match its definition");
        }
        if (command.routeContracts().stream().noneMatch(route -> route.id().equals(command.region().primaryRouteId()))
                || command.routeContracts().stream().noneMatch(route -> route.id().equals(command.region().alternateRouteId()))) {
            throw new IllegalArgumentException("Living region contracts do not match its definition");
        }
        java.util.Set<WorldObjectId> siteIds = command.sites().stream().map(WorldSite::id)
                .collect(java.util.stream.Collectors.toSet());
        if (siteIds.size() != command.sites().size()
                || command.affiliations().stream().anyMatch(value -> !siteIds.contains(value.siteId()))
                || command.capabilities().stream().anyMatch(value -> !siteIds.contains(value.siteId()))
                || command.routeContracts().stream().anyMatch(value -> !siteIds.contains(value.originEndpoint())
                || !siteIds.contains(value.destinationEndpoint()))) {
            throw new IllegalArgumentException("Living region site graph contains duplicate or unknown endpoints");
        }
        if (command.affiliations().stream().anyMatch(value -> value.role() == SiteAffiliationRole.SUPPLIER
                && command.facilities().stream().noneMatch(facility -> facility.id().equals(value.objectId())))
                || command.affiliations().stream().anyMatch(value -> value.role() == SiteAffiliationRole.RECIPIENT
                && !value.objectId().equals(command.community().id()))) {
            throw new IllegalArgumentException("Living region site affiliations do not match canonical owners");
        }
        command.facilities().forEach(facility -> {
            if (state.facility(facility.id()).isPresent()) throw new IllegalStateException("Duplicate facility " + facility.id());
        });
        command.routeContracts().forEach(route -> {
            if (state.routeContract(route.id()).isPresent()) throw new IllegalStateException("Duplicate route contract " + route.id());
        });
        command.sites().forEach(site -> {
            if (state.site(site.id()).isPresent()) throw new IllegalStateException("Duplicate world site " + site.id());
        });
        if (state.community(command.community().id()).isPresent() || state.place(command.place().id()).isPresent()) {
            throw new IllegalStateException("Duplicate settlement actor identity");
        }
        if (command.populationGroups().isEmpty() || command.populationGroups().stream()
                .anyMatch(group -> !group.communityId().equals(command.community().id())
                        || !group.originPlaceId().equals(command.place().id()))) {
            throw new IllegalArgumentException("Living region population groups do not match its community/place");
        }
        command.facilities().forEach(state::putFacility);
        state.putCommunity(command.community());
        state.putPlace(command.place());
        state.putCommunityPlaceBinding(command.binding());
        state.putEconomy(command.economy());
        state.putSecurity(command.security());
        state.putSettlementPolicy(command.policy());
        command.populationGroups().forEach(state::putPopulationGroup);
        command.sites().forEach(state::putSite);
        command.affiliations().forEach(state::putSiteAffiliation);
        command.capabilities().forEach(state::putSiteCapability);
        command.routeContracts().forEach(state::putRouteContract);
        state.putLivingRegion(command.region());
        return List.of();
    }

    private List<DomainEvent> discoverLivingRegion(WorldState state, DomainCommand.DiscoverLivingRegion command) {
        LivingRegionState region = state.livingRegion(command.regionId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown living region " + command.regionId()));
        if (!region.recognize(command.audience(), state.simulationStep())) return List.of();
        state.place(region.placeId()).orElseThrow().recognize();
        state.requireOrCreateRegionKnowledge(command.audience(), region.id())
                .discover(KnownRegionalFeature.SETTLEMENT, state.simulationStep());
        return record(state, DomainEventType.REGION_DISCOVERED, region.communityId(), command.causationId());
    }

    private List<DomainEvent> discoverRegionalFeature(WorldState state,
            DomainCommand.DiscoverRegionalFeature command) {
        LivingRegionState region = state.livingRegion(command.regionId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown living region " + command.regionId()));
        if (region.primaryAudience() == null || !region.primaryAudience().equals(command.audience())) return List.of();
        AudienceRegionKnowledge knowledge = state.requireOrCreateRegionKnowledge(command.audience(), region.id());
        if (!knowledge.discover(command.feature(), state.simulationStep())) return List.of();
        return record(state, DomainEventType.REGIONAL_FEATURE_DISCOVERED, region.communityId(),
                command.feature().name() + ":" + command.causationId());
    }

    private List<DomainEvent> observeAudienceRegionAccess(WorldState state,
            DomainCommand.ObserveAudienceRegionAccess command) {
        LivingRegionState region = state.livingRegion(command.regionId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown living region " + command.regionId()));
        if (region.primaryAudience() == null || !region.primaryAudience().equals(command.audience())) return List.of();
        if (command.observedStep() > state.simulationStep()) {
            throw new IllegalArgumentException("Audience access observation cannot come from the future");
        }
        AudienceRegionAccess access = state.regionAccess(command.audience(), command.regionId()).orElse(null);
        if (access == null) {
            state.putRegionAccess(new AudienceRegionAccess(command.audience(), command.regionId(),
                    command.reachability(), command.present(), command.observedStep(), command.observationId()));
            return record(state, DomainEventType.AUDIENCE_REGION_ACCESS_CHANGED, region.communityId(),
                    command.observationId());
        }
        if (!access.observe(command.reachability(), command.present(), command.observedStep(), command.observationId())) {
            return List.of();
        }
        return record(state, DomainEventType.AUDIENCE_REGION_ACCESS_CHANGED, region.communityId(),
                command.observationId());
    }

    private List<DomainEvent> triggerFacilityInfection(WorldState state, DomainCommand.TriggerFacilityInfection command) {
        FacilityState facility = state.facility(command.facilityId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown facility " + command.facilityId()));
        if (facility.status() != FacilityStatus.OPERATIONAL) return List.of();
        facility.infect(state.simulationStep());
        List<DomainEvent> produced = new ArrayList<>();
        produced.add(recordEvent(state, DomainEventType.MINE_INFECTED, facility.id(), command.causationId()));
        produced.add(recordEvent(state, DomainEventType.FACILITY_DISABLED, facility.id(), command.causationId()));
        return List.copyOf(produced);
    }

    private List<DomainEvent> depositResource(WorldState state, DomainCommand.DepositResource command) {
        ResourceAccount account = state.economy(command.communityId()).orElseThrow(() ->
                new IllegalArgumentException("Unknown settlement economy " + command.communityId()))
                .require(command.resource());
        int accepted = account.credit(command.amount());
        if (accepted != command.amount()) throw new IllegalStateException("Resource deposit exceeds canonical capacity");
        return record(state, DomainEventType.RESOURCE_DEPOSITED, command.communityId(), command.transferId());
    }

    private List<DomainEvent> withdrawResource(WorldState state, DomainCommand.WithdrawResource command) {
        ResourceAccount account = state.economy(command.communityId()).orElseThrow(() ->
                new IllegalArgumentException("Unknown settlement economy " + command.communityId()))
                .require(command.resource());
        if (!account.debit(command.amount(), command.minimumRemaining())) return List.of();
        return record(state, DomainEventType.RESOURCE_WITHDRAWN, command.communityId(), command.transferId());
    }

    private List<DomainEvent> beginSettlementEvacuation(WorldState state,
                                                         DomainCommand.BeginSettlementEvacuation command) {
        LivingRegionState region = state.livingRegions().stream()
                .filter(value -> value.communityId().equals(command.communityId())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown settlement region " + command.communityId()));
        if (region.primaryAudience() == null || !region.primaryAudience().equals(command.audience())) return List.of();
        List<DomainEvent> produced = settlementEmergencies.beginEvacuation(state, command.communityId(), command.causationId());
        produced.forEach(state::addEvent);
        return produced;
    }

    private List<DomainEvent> registerEvacuationShelter(WorldState state,
            DomainCommand.RegisterEvacuationShelter command) {
        LivingRegionState region = state.livingRegions().stream()
                .filter(value -> value.communityId().equals(command.communityId())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown settlement region " + command.communityId()));
        if (region.primaryAudience() == null || !region.primaryAudience().equals(command.audience())) return List.of();
        if (!state.emergencyWindow(command.communityId()).map(window -> window.state() == EmergencyWindowState.OPEN).orElse(false)) {
            return List.of();
        }
        return registerShelter(state, command.communityId(), command.shelter(), command.capacity(), command.causationId());
    }

    private List<DomainEvent> registerAutonomousRefugeeShelter(WorldState state,
            DomainCommand.RegisterAutonomousRefugeeShelter command) {
        boolean displaced = state.populationGroups(command.communityId()).stream()
                .anyMatch(group -> group.disposition() == PopulationDisposition.DISPLACED);
        if (!displaced) return List.of();
        return registerShelter(state, command.communityId(), command.shelter(), command.capacity(), command.causationId());
    }

    private List<DomainEvent> registerShelter(WorldState state, WorldObjectId communityId, WorldSite shelter,
                                               SiteCapability capacity, String causationId) {
        if (shelter.type() != WorldSiteType.SHELTER || !capacity.siteId().equals(shelter.id())
                || capacity.type() != SiteCapabilityType.SHELTER || capacity.capacity() < state.population(communityId)
                || state.site(shelter.id()).isPresent()) return List.of();
        state.putSite(shelter);
        state.putSiteAffiliation(new SiteAffiliation(shelter.id(), communityId, SiteAffiliationRole.RECIPIENT));
        state.putSiteCapability(capacity);
        return record(state, DomainEventType.REFUGEE_SHELTER_PREPARED, communityId, causationId);
    }

    private List<DomainEvent> setWorldSiteOperational(WorldState state, DomainCommand.SetWorldSiteOperational command) {
        WorldSite site = state.site(command.siteId()).orElseThrow(() -> new IllegalArgumentException("Unknown world site " + command.siteId()));
        if (site.operationalState() == command.state()) return List.of();
        site.setOperationalState(command.state());
        return record(state, command.state() == OperationalState.OPERATIONAL
                ? DomainEventType.WORLD_SITE_OPERATIONAL : DomainEventType.WORLD_SITE_OFFLINE,
                command.siteId(), command.causationId());
    }

    private List<DomainEvent> registerAuthorityProfile(WorldState state, SettlementAuthorityProfile profile) {
        if (state.community(profile.communityId()).isEmpty()) {
            throw new IllegalArgumentException("Unknown settlement community " + profile.communityId());
        }
        SettlementAuthorityProfile current = state.settlementAuthorityProfile(profile.communityId()).orElse(null);
        if (current != null) {
            if (!current.profileId().equals(profile.profileId()) || !current.fields().equals(profile.fields())
                    || current.relocationAllowed() != profile.relocationAllowed()
                    || current.pmRuinAllowed() != profile.pmRuinAllowed()
                    || current.pmPopulationGrowthAllowed() != profile.pmPopulationGrowthAllowed()) {
                throw new IllegalStateException("Settlement authority profile is immutable once registered");
            }
            return List.of();
        }
        state.putSettlementAuthorityProfile(profile);
        return record(state, DomainEventType.SETTLEMENT_AUTHORITY_PROFILE_REGISTERED,
                profile.communityId(), profile.profileId());
    }

    private List<DomainEvent> reconcileSettlementPopulation(WorldState state,
            DomainCommand.ReconcileSettlementPopulation command) {
        SettlementAuthorityProfile profile = state.settlementAuthorityProfile(command.communityId())
                .orElseThrow(() -> new IllegalStateException("Settlement population has no authority profile"));
        if (profile.authority(SettlementAuthorityField.MACRO_POPULATION) != FieldAuthority.RECONCILED) return List.of();
        PopulationGroup group = state.populationGroup(command.populationGroupId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown population group " + command.populationGroupId()));
        if (!group.communityId().equals(command.communityId()) || group.disposition() != PopulationDisposition.RESIDENT) {
            return List.of();
        }
        if (!group.reconcileCohorts(command.cohorts())) return List.of();
        state.communityPlaceBinding(command.communityId()).flatMap(binding -> state.place(binding.placeId()))
                .ifPresent(place -> place.setOccupancy(group.size() == 0 ? OccupancyState.EMPTY : OccupancyState.INHABITED));
        return record(state, DomainEventType.SETTLEMENT_POPULATION_RECONCILED,
                command.communityId(), command.observationId() + ":" + command.causationId());
    }

    private List<DomainEvent> startDevelopmentIntent(WorldState state, String intentId) {
        DevelopmentIntent intent = state.developmentIntent(intentId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown development intent " + intentId));
        if (!intent.start()) return List.of();
        return record(state, DomainEventType.SETTLEMENT_DEVELOPMENT_STARTED, intent.communityId(), intent.id());
    }

    private List<DomainEvent> contributeDevelopmentIntent(WorldState state,
            DomainCommand.ContributeDevelopmentIntent command) {
        DevelopmentIntent intent = state.developmentIntent(command.intentId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown development intent " + command.intentId()));
        if (!intent.contribute(command.amount(), command.transferId())) return List.of();
        List<DomainEvent> produced = new ArrayList<>(record(state, DomainEventType.SETTLEMENT_DEVELOPMENT_CONTRIBUTED,
                intent.communityId(), command.transferId()));
        if (intent.funded()) produced.add(recordEvent(state, DomainEventType.SETTLEMENT_DEVELOPMENT_FUNDED,
                intent.communityId(), intent.id()));
        return List.copyOf(produced);
    }

    private List<DomainEvent> completeDevelopmentIntent(WorldState state, String intentId) {
        DevelopmentIntent intent = state.developmentIntent(intentId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown development intent " + intentId));
        if (intent.type() != DevelopmentIntentType.UPGRADE_STOREHOUSE || !intent.complete()) return List.of();
        ResourceAccount account = state.economy(intent.communityId()).orElseThrow().require(intent.requiredResource());
        if (intent.reservedAmount() > 0) account.consumeReservation(intent.reservedAmount());
        account.expandCapacity(Math.multiplyExact(account.capacity(), 2));
        SiteCapability current = state.siteCapability(intent.targetSiteId(), SiteCapabilityType.STORAGE, intent.requiredResource())
                .orElseThrow(() -> new IllegalStateException("Storehouse intent lost its target capability"));
        state.putSiteCapability(new SiteCapability(current.siteId(), current.type(), current.resource(),
                Math.multiplyExact(current.capacity(), 2)));
        state.settlementDevelopment(intent.communityId()).orElseThrow().storehouseCompleted();
        List<DomainEvent> produced = new ArrayList<>(record(state, DomainEventType.SETTLEMENT_STOREHOUSE_UPGRADED,
                intent.communityId(), intent.id()));
        produced.addAll(scenarios.reconcileOpportunity(state, intent.communityId(),
                ScenarioArchetype.DEVELOPMENT_OPPORTUNITY, "STOREHOUSE_UPGRADED"));
        return List.copyOf(produced);
    }

    private List<DomainEvent> cancelDevelopmentIntent(WorldState state, String intentId, String reason) {
        DevelopmentIntent intent = state.developmentIntent(intentId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown development intent " + intentId));
        if (intent.contributedAmount() > 0) return blockDevelopmentIntent(state, intentId,
                "Player-funded project cannot be cancelled: " + reason);
        if (!intent.cancel(reason)) return List.of();
        if (intent.requiredResource() != null) {
            ResourceAccount account = state.economy(intent.communityId()).orElseThrow().require(intent.requiredResource());
            if (intent.reservedAmount() > 0) account.releaseReservation(intent.reservedAmount());
            if (intent.contributedAmount() > 0 && account.credit(intent.contributedAmount()) != intent.contributedAmount()) {
                throw new IllegalStateException("Cancelled project escrow no longer fits canonical settlement stock");
            }
        }
        return record(state, DomainEventType.SETTLEMENT_DEVELOPMENT_CANCELLED, intent.communityId(), intent.id());
    }

    private List<DomainEvent> blockDevelopmentIntent(WorldState state, String intentId, String reason) {
        DevelopmentIntent intent = state.developmentIntent(intentId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown development intent " + intentId));
        if (!intent.block(reason)) return List.of();
        return record(state, DomainEventType.SETTLEMENT_DEVELOPMENT_BLOCKED, intent.communityId(), intent.id());
    }

    private List<DomainEvent> record(WorldState state, DomainEventType type, WorldObjectId subject, String causationId) {
        return List.of(recordEvent(state, type, subject, causationId));
    }

    private DomainEvent recordEvent(WorldState state, DomainEventType type, WorldObjectId subject, String causationId) {
        DomainEvent event = events.create(state, type, subject, causationId);
        state.addEvent(event);
        return event;
    }
}
