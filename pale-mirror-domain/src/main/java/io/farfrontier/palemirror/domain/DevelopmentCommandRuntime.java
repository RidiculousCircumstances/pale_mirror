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
    List<DomainEvent> planAlternateDispatch(WorldState state, DomainCommand.PlanAlternateDispatch command) {
        LivingRegionState region = state.livingRegions().stream()
                .filter(value -> value.communityId().equals(command.communityId())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown settlement region " + command.communityId()));
        RouteContract route = state.routeContract(region.alternateRouteId()).orElseThrow(() ->
                new IllegalArgumentException("Unknown alternate route " + region.alternateRouteId()));
        if (!route.originEndpoint().equals(command.dispatchSiteId())) return List.of();
        if (state.developmentIntents().stream().anyMatch(value -> value.communityId().equals(command.communityId())
                && value.type() == DevelopmentIntentType.COMMISSION_ALTERNATE_DISPATCH
                && value.state() != DevelopmentIntentState.CANCELLED)) return List.of();
        WorldSite site = state.site(command.dispatchSiteId()).orElseThrow(() ->
                new IllegalArgumentException("Unknown alternate dispatch site " + command.dispatchSiteId()));
        if (site.type() != WorldSiteType.LOGISTICS_ENDPOINT || site.operationalState() == OperationalState.OPERATIONAL
                || state.siteCapability(site.id(), SiteCapabilityType.LOGISTICS, ResourceKind.IRON).isEmpty()) return List.of();
        ResourceAccount iron = state.economy(command.communityId()).orElseThrow(() ->
                new IllegalArgumentException("Unknown development economy " + command.communityId())).require(ResourceKind.IRON);
        if (!iron.reserve(command.requiredIron())) return List.of();
        String id = "pm:development:" + command.communityId().value() + ":alternate_dispatch";
        DevelopmentIntent intent = new DevelopmentIntent(id, command.communityId(),
                DevelopmentIntentType.COMMISSION_ALTERNATE_DISPATCH, command.dispatchSiteId(), ResourceKind.IRON,
                command.requiredIron(), 0, command.requiredIron(), 0, 1, java.util.Set.of(),
                "alternate-dispatch-v1", DevelopmentIntentState.PLANNED, "");
        state.putDevelopmentIntent(intent);
        List<DomainEvent> produced = new ArrayList<>(record(state, DomainEventType.SETTLEMENT_DEVELOPMENT_PLANNED,
                intent, command.causationId()));
        produced.add(event(state, DomainEventType.SETTLEMENT_DEVELOPMENT_FUNDED, intent, command.causationId()));
        return List.copyOf(produced);
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
        if (intent.type() == DevelopmentIntentType.RETURN_HOME
                || intent.state() != DevelopmentIntentState.MATERIALIZING
                && intent.state() != DevelopmentIntentState.PLANNED) return List.of();
        CompletionPreflight preflight = preflightCompletion(state, intent);
        intent.complete();
        if (intent.reservedAmount() > 0) preflight.account().consumeReservation(intent.reservedAmount());
        if (intent.type() == DevelopmentIntentType.RECONSTRUCT_PLACE) {
            preflight.place().setStructuralIntegrity(StructuralIntegrity.INTACT);
            return record(state, DomainEventType.SETTLEMENT_RECONSTRUCTED, intent, intent.id());
        }
        if (intent.type() == DevelopmentIntentType.COMMISSION_ALTERNATE_DISPATCH) {
            preflight.site().setOperationalState(OperationalState.OPERATIONAL);
            return record(state, DomainEventType.ALTERNATE_DISPATCH_COMMISSIONED, intent, intent.id());
        }
        preflight.account().expandCapacity(preflight.newAccountCapacity());
        state.putSiteCapability(new SiteCapability(intent.targetSiteId(), SiteCapabilityType.STORAGE, intent.requiredResource(),
                preflight.newSiteCapacity()));
        preflight.site().setOperationalState(OperationalState.OPERATIONAL);
        preflight.development().storehouseCompleted();
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
    private static CompletionPreflight preflightCompletion(WorldState state, DevelopmentIntent intent) {
        ResourceAccount account = state.economy(intent.communityId()).orElseThrow(() ->
                new IllegalArgumentException("Unknown development economy " + intent.communityId()))
                .require(intent.requiredResource());
        if (intent.reservedAmount() > account.reserved()) {
            throw new IllegalStateException("Development reservation is no longer available for " + intent.id());
        }
        if (intent.type() == DevelopmentIntentType.RECONSTRUCT_PLACE) {
            SettlementPlace place = state.communityPlaceBinding(intent.communityId())
                    .flatMap(binding -> state.place(binding.placeId()))
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Unknown development place for " + intent.communityId()));
            return new CompletionPreflight(account, place, null, null, 0, 0);
        }
        WorldSite site = state.site(intent.targetSiteId()).orElseThrow(() ->
                new IllegalArgumentException("Unknown development site " + intent.targetSiteId()));
        if (intent.type() == DevelopmentIntentType.COMMISSION_ALTERNATE_DISPATCH) {
            return new CompletionPreflight(account, null, site, null, 0, 0);
        }
        SettlementDevelopment development = state.settlementDevelopment(intent.communityId()).orElseThrow(() ->
                new IllegalArgumentException("Unknown settlement development " + intent.communityId()));
        int newAccountCapacity = Math.multiplyExact(account.capacity(), 2);
        SiteCapability current = state.siteCapability(intent.targetSiteId(), SiteCapabilityType.STORAGE,
                intent.requiredResource()).orElse(null);
        int newSiteCapacity = current == null ? newAccountCapacity : Math.multiplyExact(current.capacity(), 2);
        return new CompletionPreflight(account, null, site, development, newAccountCapacity, newSiteCapacity);
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

    private record CompletionPreflight(ResourceAccount account, SettlementPlace place, WorldSite site,
                                       SettlementDevelopment development, int newAccountCapacity,
                                       int newSiteCapacity) { }
}
