package io.farfrontier.palemirror.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Applies explicit domain commands; it does not know about Minecraft or adapters. */
public final class DomainCommandProcessor implements DomainCommandExecutor {
    private final SimulationEngine simulation;
    private final ResourceFlowSimulation resources;
    private final SettlementDecisionEngine settlementDecisions;
    private final SettlementEmergencyRuntime settlementEmergencies;
    private final JourneySimulation journeys;
    private final SettlementDevelopmentEngine settlementDevelopment;
    private final ThreatLifecycle threats;
    private final Narrator narrator;
    private final ScenarioRuntime scenarios;
    private final SettlementCrisisRuntime settlementCrises;
    private final DomainEventFactory events;
    private final LivingRegionRegistrationRuntime regionRegistration;
    private final RegionalAudienceCommandRuntime regionalAudienceCommands;
    private final DevelopmentCommandRuntime developmentCommands;
    private final PreparedShelterReconciliationRuntime preparedShelterReconciliation;

    DomainCommandProcessor(SimulationEngine simulation, ResourceFlowSimulation resources,
                           SettlementDecisionEngine settlementDecisions,
                           SettlementEmergencyRuntime settlementEmergencies,
                           JourneySimulation journeys,
                           SettlementDevelopmentEngine settlementDevelopment,
                           ThreatLifecycle threats, Narrator narrator,
                           ScenarioRuntime scenarios, SettlementCrisisRuntime settlementCrises, DomainEventFactory events) {
        this.simulation = Objects.requireNonNull(simulation, "simulation");
        this.resources = Objects.requireNonNull(resources, "resources");
        this.settlementDecisions = Objects.requireNonNull(settlementDecisions, "settlementDecisions");
        this.settlementEmergencies = Objects.requireNonNull(settlementEmergencies, "settlementEmergencies");
        this.journeys = Objects.requireNonNull(journeys, "journeys");
        this.settlementDevelopment = Objects.requireNonNull(settlementDevelopment, "settlementDevelopment");
        this.threats = Objects.requireNonNull(threats, "threats");
        this.narrator = Objects.requireNonNull(narrator, "narrator");
        this.scenarios = Objects.requireNonNull(scenarios, "scenarios");
        this.settlementCrises = Objects.requireNonNull(settlementCrises, "settlementCrises");
        this.events = Objects.requireNonNull(events, "events");
        this.regionRegistration = new LivingRegionRegistrationRuntime(events);
        this.regionalAudienceCommands = new RegionalAudienceCommandRuntime(events);
        this.developmentCommands = new DevelopmentCommandRuntime(events, scenarios);
        this.preparedShelterReconciliation = new PreparedShelterReconciliationRuntime(events);
    }

    public List<DomainEvent> execute(WorldState state, DomainCommand command) {
        return executeOutcome(state, command).events();
    }

    public DomainCommandOutcome executeOutcome(WorldState state, DomainCommand command) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(command, "command");
        List<DomainEvent> produced;
        try {
            produced = switch (command) {
            case DomainCommand.AdvanceSimulation advance -> advanceSimulation(state, advance.steps());
            case DomainCommand.OfferScenario offer -> narrator.offerFor(state, offer.sourceEvent(), offer.audience(), offer.definition());
            case DomainCommand.AcceptScenario accept -> acceptScenario(state, accept.scenarioId());
            case DomainCommand.DeclineScenario decline -> declineScenario(state, decline.scenarioId());
            case DomainCommand.CancelScenario cancel -> scenarios.cancel(state, cancel.scenarioId(), cancel.reason());
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
            case DomainCommand.RegisterLivingRegion registered -> regionRegistration.register(state, registered);
            case DomainCommand.DiscoverLivingRegion discovered -> regionalAudienceCommands.discover(state, discovered);
            case DomainCommand.DiscoverRegionalFeature discovered -> regionalAudienceCommands.discoverFeature(state, discovered);
            case DomainCommand.ObserveAudienceRegionAccess observed -> regionalAudienceCommands.observeAccess(state, observed);
            case DomainCommand.TriggerFacilityInfection triggered -> triggerFacilityInfection(state, triggered);
            case DomainCommand.DepositResource deposited -> depositResource(state, deposited);
            case DomainCommand.WithdrawResource withdrawn -> withdrawResource(state, withdrawn);
            case DomainCommand.BeginSettlementEvacuation evacuation -> beginSettlementEvacuation(state, evacuation);
            case DomainCommand.RegisterEvacuationShelter shelter -> registerEvacuationShelter(state, shelter);
            case DomainCommand.ReconcilePreparedShelterDestination shelter ->
                    preparedShelterReconciliation.reconcile(state, shelter);
            case DomainCommand.RegisterAutonomousRefugeeShelter shelter -> registerAutonomousRefugeeShelter(state, shelter);
            case DomainCommand.SetWorldSiteOperational site -> setWorldSiteOperational(state, site);
            case DomainCommand.StartDevelopmentIntent intent -> developmentCommands.start(state, intent.intentId());
            case DomainCommand.PlanAlternateDispatch intent -> developmentCommands.planAlternateDispatch(state, intent);
            case DomainCommand.ContributeDevelopmentIntent contribution -> developmentCommands.contribute(state, contribution);
            case DomainCommand.CompleteDevelopmentIntent intent -> developmentCommands.complete(state, intent.intentId());
            case DomainCommand.CancelDevelopmentIntent intent -> developmentCommands.cancel(state, intent.intentId(), intent.reason());
            case DomainCommand.BlockDevelopmentIntent intent -> developmentCommands.block(state, intent.intentId(), intent.reason());
            case DomainCommand.RegisterSettlementAuthorityProfile authority -> registerAuthorityProfile(state, authority.profile());
            case DomainCommand.ReconcileSettlementPopulation population -> reconcileSettlementPopulation(state, population);
            case DomainCommand.ConfirmSettlementResidentDeath casualty -> SettlementCasualtyRuntime.confirm(state, casualty, events);
            case DomainCommand.ObserveJourneyCheckpoint observed -> observeJourneyCheckpoint(state, observed);
            case DomainCommand.ObserveJourneyBlocked observed -> observeJourneyBlocked(state, observed);
            case DomainCommand.RegisterFacility registered -> registerFacility(state, registered);
            case DomainCommand.RegisterWorldSite registered -> registerWorldSite(state, registered);
            case DomainCommand.RegisterDevelopmentIntent registered -> registerDevelopmentIntent(state, registered);
            case DomainCommand.ResetWorldState reset -> resetWorldState(state, reset);
            case DomainCommand.EvaluateNarrativeCandidates candidates ->
                    narrator.offerBest(state, candidates.audience(), candidates.candidates());
            };
        } catch (DomainCommandException failure) {
            throw failure;
        } catch (java.util.NoSuchElementException failure) {
            throw new DomainCommandException(DomainCommandException.Code.UNKNOWN_REFERENCE, command,
                    failure.getMessage() == null ? "Referenced domain object is absent" : failure.getMessage(), failure);
        } catch (IllegalStateException failure) {
            throw new DomainCommandException(DomainCommandException.Code.INVARIANT_VIOLATION, command,
                    failure.getMessage(), failure);
        } catch (IllegalArgumentException failure) {
            DomainCommandException.Code code = failure.getMessage() != null && failure.getMessage().startsWith("Unknown ")
                    ? DomainCommandException.Code.UNKNOWN_REFERENCE : DomainCommandException.Code.INVALID_INPUT;
            throw new DomainCommandException(code, command, failure.getMessage(), failure);
        }
        boolean changed = !produced.isEmpty()
                || command instanceof DomainCommand.AdvanceSimulation advance && advance.steps() > 0;
        return new DomainCommandOutcome(changed, produced);
    }

    private List<DomainEvent> registerFacility(WorldState state, DomainCommand.RegisterFacility command) {
        if (state.facility(command.facility().id()).isPresent()) {
            throw new IllegalStateException("Duplicate facility " + command.facility().id());
        }
        state.putFacility(command.facility());
        return record(state, DomainEventType.FACILITY_REGISTERED, command.facility().id(), command.causationId());
    }

    private List<DomainEvent> registerWorldSite(WorldState state, DomainCommand.RegisterWorldSite command) {
        if (state.site(command.site().id()).isPresent()) {
            throw new IllegalStateException("Duplicate world site " + command.site().id());
        }
        if (command.affiliations().stream().anyMatch(value -> !value.siteId().equals(command.site().id()))
                || command.capabilities().stream().anyMatch(value -> !value.siteId().equals(command.site().id()))) {
            throw new IllegalArgumentException("World site attachments must reference the registered site");
        }
        command.affiliations().forEach(affiliation -> {
            WorldObjectId objectId = affiliation.objectId();
            boolean knownObject = state.community(objectId).isPresent() || state.place(objectId).isPresent()
                    || state.facility(objectId).isPresent() || state.routeContract(objectId).isPresent();
            if (!knownObject) {
                throw new IllegalArgumentException("Unknown world site affiliation object " + objectId);
            }
        });
        state.putSite(command.site());
        command.affiliations().forEach(state::putSiteAffiliation);
        command.capabilities().forEach(state::putSiteCapability);
        return record(state, DomainEventType.WORLD_SITE_REGISTERED, command.site().id(), command.causationId());
    }

    private List<DomainEvent> registerDevelopmentIntent(WorldState state,
                                                        DomainCommand.RegisterDevelopmentIntent command) {
        if (state.community(command.intent().communityId()).isEmpty()) {
            throw new IllegalArgumentException("Unknown development community " + command.intent().communityId());
        }
        if (state.developmentIntent(command.intent().id()).isPresent()) {
            throw new IllegalStateException("Duplicate development intent " + command.intent().id());
        }
        if (command.intent().targetSiteId() != null
                && state.site(command.intent().targetSiteId()).isEmpty()
                && state.place(command.intent().targetSiteId()).isEmpty()) {
            throw new IllegalArgumentException("Unknown development target " + command.intent().targetSiteId());
        }
        if (command.intent().requiredResource() != null) {
            state.economy(command.intent().communityId()).orElseThrow(() ->
                    new IllegalArgumentException("Unknown development economy " + command.intent().communityId()))
                    .require(command.intent().requiredResource());
        }
        state.putDevelopmentIntent(command.intent());
        return record(state, DomainEventType.DEVELOPMENT_INTENT_REGISTERED,
                command.intent().communityId(), command.causationId());
    }

    private List<DomainEvent> resetWorldState(WorldState state, DomainCommand.ResetWorldState command) {
        state.clearAll();
        DomainEvent reset = events.create(state, DomainEventType.WORLD_STATE_RESET,
                new WorldObjectId("pale_mirror:world"), command.causationId());
        state.addEvent(reset);
        return List.of(reset);
    }

    private List<DomainEvent> observeJourneyCheckpoint(WorldState state, DomainCommand.ObserveJourneyCheckpoint command) {
        WorldJourney journey = state.journey(command.journeyId()).orElseThrow(() ->
                new IllegalArgumentException("Unknown journey " + command.journeyId()));
        if (!journey.observeCheckpoint(command.checkpointIndex())) return List.of();
        return record(state, DomainEventType.JOURNEY_CHECKPOINT_REACHED, journey.destinationSiteId(), command.observationId());
    }

    private List<DomainEvent> observeJourneyBlocked(WorldState state, DomainCommand.ObserveJourneyBlocked command) {
        WorldJourney journey = state.journey(command.journeyId()).orElseThrow(() ->
                new IllegalArgumentException("Unknown journey " + command.journeyId()));
        if (journey.terminal()) return List.of();
        journey.block(command.reason());
        return record(state, DomainEventType.JOURNEY_BLOCKED, journey.destinationSiteId(), command.observationId());
    }

    private List<DomainEvent> advanceSimulation(WorldState state, int steps) {
        if (steps < 0) throw new IllegalArgumentException("Simulation steps must not be negative");
        List<DomainEvent> produced = new ArrayList<>();
        for (int index = 0; index < steps; index++) {
            produced.addAll(simulation.advance(state, 1));
            produced.addAll(resources.reconcile(state));
            produced.addAll(settlementDecisions.reconcile(state));
            produced.addAll(settlementCrises.reconcile(state));
            produced.addAll(journeys.reconcile(state));
            state.compactTerminalJourneys();
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
        if (command.capacity() > 0) {
            WorldSite origin = state.site(contract.originEndpoint()).orElseThrow(() ->
                    new IllegalArgumentException("Unknown route origin " + contract.originEndpoint()));
            WorldSite destination = state.site(contract.destinationEndpoint()).orElseThrow(() ->
                    new IllegalArgumentException("Unknown route destination " + contract.destinationEndpoint()));
            if (origin.operationalState() != OperationalState.OPERATIONAL
                    || destination.operationalState() != OperationalState.OPERATIONAL) return List.of();
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
        if (!state.hasRespondingScenario(command.audience(), command.communityId(),
                ScenarioArchetype.SETTLEMENT_SUPPLY_CRISIS)) return List.of();
        List<DomainEvent> produced = settlementEmergencies.beginEvacuation(state, command.communityId(),
                command.shelterSiteId(), command.causationId());
        produced.forEach(state::addEvent);
        return produced;
    }

    private List<DomainEvent> registerEvacuationShelter(WorldState state,
            DomainCommand.RegisterEvacuationShelter command) {
        LivingRegionState region = state.livingRegions().stream()
                .filter(value -> value.communityId().equals(command.communityId())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown settlement region " + command.communityId()));
        if (!state.hasRespondingScenario(command.audience(), command.communityId(),
                ScenarioArchetype.SETTLEMENT_SUPPLY_CRISIS)) return List.of();
        if (!state.emergencyWindow(command.communityId()).map(window -> window.state() == EmergencyWindowState.OPEN).orElse(false)) {
            return List.of();
        }
        if (!command.path().destinationSiteId().equals(command.shelter().id())
                || state.worldPath(command.path().id()).isPresent()) return List.of();
        List<DomainEvent> events = registerShelter(state, command.communityId(), command.shelter(), command.capacity(), command.causationId());
        if (!events.isEmpty()) state.putWorldPath(command.path());
        return events;
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

    private List<DomainEvent> record(WorldState state, DomainEventType type, WorldObjectId subject, String causationId) {
        return List.of(recordEvent(state, type, subject, causationId));
    }

    private DomainEvent recordEvent(WorldState state, DomainEventType type, WorldObjectId subject, String causationId) {
        DomainEvent event = events.create(state, type, subject, causationId);
        state.addEvent(event);
        return event;
    }
}
