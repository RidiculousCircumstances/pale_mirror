package io.farfrontier.palemirror.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Applies explicit domain commands; it does not know about Minecraft or adapters. */
public final class DomainCommandProcessor {
    private final SimulationEngine simulation;
    private final SettlementSimulation settlements;
    private final ThreatLifecycle threats;
    private final Narrator narrator;
    private final ScenarioRuntime scenarios;
    private final DomainEventFactory events;

    DomainCommandProcessor(SimulationEngine simulation, SettlementSimulation settlements, ThreatLifecycle threats, Narrator narrator,
                           ScenarioRuntime scenarios, DomainEventFactory events) {
        this.simulation = Objects.requireNonNull(simulation, "simulation");
        this.settlements = Objects.requireNonNull(settlements, "settlements");
        this.threats = Objects.requireNonNull(threats, "threats");
        this.narrator = Objects.requireNonNull(narrator, "narrator");
        this.scenarios = Objects.requireNonNull(scenarios, "scenarios");
        this.events = Objects.requireNonNull(events, "events");
    }

    public List<DomainEvent> execute(WorldState state, DomainCommand command) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(command, "command");
        return switch (command) {
            case DomainCommand.AdvanceSimulation advance -> advanceSimulation(state, advance.steps());
            case DomainCommand.OfferScenario offer -> narrator.offerFor(state, offer.sourceEvent(), offer.audience(), offer.definition());
            case DomainCommand.AcceptScenario accept -> scenarios.accept(state, accept.scenarioId());
            case DomainCommand.PlayerEnteredFacility entered -> scenarios.playerEntered(state, entered.audience(), entered.facilityId());
            case DomainCommand.ThreatControllerDestroyed destroyed -> reconcileDestroyedController(state, destroyed);
            case DomainCommand.MaterializationObserved observed -> reconcileMaterialization(state, observed);
            case DomainCommand.NoScenario noScenario -> narrator.noScenario(state, noScenario.sourceEvent(), noScenario.audience(), noScenario.reason());
            case DomainCommand.SetScenarioBlocked capability -> reconcileScenarioCapability(state, capability);
            case DomainCommand.ActivateSiege activated -> activateSiege(state, activated);
            case DomainCommand.BypassSiege bypassed -> bypassSiege(state, bypassed);
            case DomainCommand.SiegeGateDestroyed destroyed -> siegeGateDestroyed(state, destroyed);
        };
    }

    private List<DomainEvent> advanceSimulation(WorldState state, int steps) {
        List<DomainEvent> produced = new ArrayList<>(simulation.advance(state, steps));
        produced.addAll(settlements.reconcile(state));
        return List.copyOf(produced);
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

    private List<DomainEvent> activateSiege(WorldState state, DomainCommand.ActivateSiege command) {
        FacilityState facility = state.facility(command.facilityId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown facility " + command.facilityId()));
        if (!facility.activateSiege(command.definitionId(), command.definitionVersion(), command.bossProfileId())) return List.of();
        return record(state, DomainEventType.SIEGE_ACTIVATED, command.facilityId(), command.causationId());
    }

    private List<DomainEvent> bypassSiege(WorldState state, DomainCommand.BypassSiege command) {
        FacilityState facility = state.facility(command.facilityId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown facility " + command.facilityId()));
        if (!facility.bypassSiege()) return List.of();
        return record(state, DomainEventType.SIEGE_BYPASSED, command.facilityId(), command.causationId());
    }

    private List<DomainEvent> siegeGateDestroyed(WorldState state, DomainCommand.SiegeGateDestroyed command) {
        FacilityState facility = state.facility(command.facilityId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown facility " + command.facilityId()));
        if (!facility.siegeGateDestroyed(command.slotId())) return List.of();
        List<DomainEvent> produced = new ArrayList<>(record(state, DomainEventType.SIEGE_GATE_DESTROYED,
                command.facilityId(), command.causationId()));
        if (facility.siege().stage() == SiegeStage.CONTROLLER_VULNERABLE) {
            produced.addAll(record(state, DomainEventType.CONTROLLER_UNSEALED, command.facilityId(), command.causationId()));
        }
        return List.copyOf(produced);
    }

    private List<DomainEvent> record(WorldState state, DomainEventType type, WorldObjectId subject, String causationId) {
        DomainEvent event = events.create(state, type, subject, causationId);
        state.addEvent(event);
        return List.of(event);
    }
}
