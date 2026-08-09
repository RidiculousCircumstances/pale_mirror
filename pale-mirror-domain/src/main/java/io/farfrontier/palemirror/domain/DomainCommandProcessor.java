package io.farfrontier.palemirror.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Applies explicit domain commands; it does not know about Minecraft or adapters. */
public final class DomainCommandProcessor {
    private final SimulationEngine simulation;
    private final ThreatLifecycle threats;
    private final Narrator narrator;
    private final ScenarioRuntime scenarios;
    private final DomainEventFactory events;

    DomainCommandProcessor(SimulationEngine simulation, ThreatLifecycle threats, Narrator narrator,
                           ScenarioRuntime scenarios, DomainEventFactory events) {
        this.simulation = Objects.requireNonNull(simulation, "simulation");
        this.threats = Objects.requireNonNull(threats, "threats");
        this.narrator = Objects.requireNonNull(narrator, "narrator");
        this.scenarios = Objects.requireNonNull(scenarios, "scenarios");
        this.events = Objects.requireNonNull(events, "events");
    }

    public List<DomainEvent> execute(WorldState state, DomainCommand command) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(command, "command");
        return switch (command) {
            case DomainCommand.AdvanceSimulation advance -> simulation.advance(state, advance.steps());
            case DomainCommand.OfferScenario offer -> narrator.offerFor(state, offer.sourceEvent(), offer.audience());
            case DomainCommand.AcceptScenario accept -> scenarios.accept(state, accept.scenarioId());
            case DomainCommand.PlayerEnteredFacility entered -> scenarios.playerEntered(state, entered.audience(), entered.facilityId());
            case DomainCommand.ThreatControllerDestroyed destroyed -> reconcileDestroyedController(state, destroyed);
            case DomainCommand.MaterializationObserved observed -> reconcileMaterialization(state, observed);
        };
    }

    private List<DomainEvent> reconcileDestroyedController(WorldState state, DomainCommand.ThreatControllerDestroyed command) {
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
}
