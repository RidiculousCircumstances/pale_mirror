package io.farfrontier.palemirror.domain;

import java.util.ArrayList;
import java.util.List;

/** Command-side escrow transitions; positive-policy selection stays in SettlementDevelopmentEngine. */
final class DevelopmentCommandRuntime {
    private final DomainEventFactory events;
    private final ScenarioRuntime scenarios;
    DevelopmentCommandRuntime(DomainEventFactory events, ScenarioRuntime scenarios) { this.events = events; this.scenarios = scenarios; }

    List<DomainEvent> start(WorldState state, String id) {
        DevelopmentIntent intent = require(state, id);
        return intent.start() ? record(state, DomainEventType.SETTLEMENT_DEVELOPMENT_STARTED, intent, intent.id()) : List.of();
    }
    List<DomainEvent> contribute(WorldState state, DomainCommand.ContributeDevelopmentIntent command) {
        DevelopmentIntent intent = require(state, command.intentId());
        if (!intent.contribute(command.amount(), command.transferId())) return List.of();
        List<DomainEvent> produced = new ArrayList<>(record(state, DomainEventType.SETTLEMENT_DEVELOPMENT_CONTRIBUTED,
                intent, command.transferId()));
        if (intent.funded()) produced.add(event(state, DomainEventType.SETTLEMENT_DEVELOPMENT_FUNDED, intent, intent.id()));
        return List.copyOf(produced);
    }
    List<DomainEvent> complete(WorldState state, String id) {
        DevelopmentIntent intent = require(state, id);
        if (intent.type() == DevelopmentIntentType.RETURN_HOME || !intent.complete()) return List.of();
        ResourceAccount account = state.economy(intent.communityId()).orElseThrow().require(intent.requiredResource());
        if (intent.reservedAmount() > 0) account.consumeReservation(intent.reservedAmount());
        if (intent.type() == DevelopmentIntentType.RECONSTRUCT_PLACE) {
            state.communityPlaceBinding(intent.communityId()).flatMap(binding -> state.place(binding.placeId()))
                    .orElseThrow().setStructuralIntegrity(StructuralIntegrity.INTACT);
            return record(state, DomainEventType.SETTLEMENT_RECONSTRUCTED, intent, intent.id());
        }
        account.expandCapacity(Math.multiplyExact(account.capacity(), 2));
        SiteCapability current = state.siteCapability(intent.targetSiteId(), SiteCapabilityType.STORAGE, intent.requiredResource())
                .orElse(null);
        state.putSiteCapability(new SiteCapability(intent.targetSiteId(), SiteCapabilityType.STORAGE, intent.requiredResource(),
                current == null ? account.capacity() : Math.multiplyExact(current.capacity(), 2)));
        state.site(intent.targetSiteId()).orElseThrow().setOperationalState(OperationalState.OPERATIONAL);
        state.settlementDevelopment(intent.communityId()).orElseThrow().storehouseCompleted();
        List<DomainEvent> produced = new ArrayList<>(record(state, DomainEventType.SETTLEMENT_STOREHOUSE_UPGRADED, intent, intent.id()));
        produced.addAll(scenarios.reconcileOpportunity(state, intent.communityId(),
                ScenarioArchetype.DEVELOPMENT_OPPORTUNITY, "STOREHOUSE_UPGRADED"));
        return List.copyOf(produced);
    }
    List<DomainEvent> cancel(WorldState state, String id, String reason) {
        DevelopmentIntent intent = require(state, id);
        if (intent.contributedAmount() > 0) return block(state, id, "Player-funded project cannot be cancelled: " + reason);
        if (!intent.cancel(reason)) return List.of();
        if (intent.requiredResource() != null) {
            ResourceAccount account = state.economy(intent.communityId()).orElseThrow().require(intent.requiredResource());
            if (intent.reservedAmount() > 0) account.releaseReservation(intent.reservedAmount());
        }
        return record(state, DomainEventType.SETTLEMENT_DEVELOPMENT_CANCELLED, intent, intent.id());
    }
    List<DomainEvent> block(WorldState state, String id, String reason) {
        DevelopmentIntent intent = require(state, id);
        return intent.block(reason) ? record(state, DomainEventType.SETTLEMENT_DEVELOPMENT_BLOCKED, intent, intent.id()) : List.of();
    }
    private static DevelopmentIntent require(WorldState state, String id) {
        return state.developmentIntent(id).orElseThrow(() -> new IllegalArgumentException("Unknown development intent " + id));
    }
    private List<DomainEvent> record(WorldState state, DomainEventType type, DevelopmentIntent intent, String causation) {
        return List.of(event(state, type, intent, causation));
    }
    private DomainEvent event(WorldState state, DomainEventType type, DevelopmentIntent intent, String causation) {
        DomainEvent value = events.create(state, type, intent.communityId(), causation); state.addEvent(value); return value;
    }
}
