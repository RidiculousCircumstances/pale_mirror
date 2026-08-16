package io.farfrontier.palemirror.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Applies audience-private discovery and reachability facts to one global region. */
final class RegionalAudienceCommandRuntime {
    private final DomainEventFactory events;

    RegionalAudienceCommandRuntime(DomainEventFactory events) {
        this.events = Objects.requireNonNull(events, "events");
    }

    List<DomainEvent> discover(WorldState state, DomainCommand.DiscoverLivingRegion command) {
        LivingRegionState region = region(state, command.regionId());
        AudienceRegionKnowledge knowledge = state.requireOrCreateRegionKnowledge(command.audience(), region.id());
        if (!knowledge.discover(KnownRegionalFeature.SETTLEMENT, state.simulationStep())) return List.of();
        List<DomainEvent> produced = new ArrayList<>();
        if (region.discover(state.simulationStep())) {
            state.place(region.placeId()).orElseThrow().recognize();
            produced.add(record(state, DomainEventType.REGION_DISCOVERED,
                    region.communityId(), command.causationId()));
        }
        produced.add(record(state, DomainEventType.REGIONAL_FEATURE_DISCOVERED,
                region.communityId(), KnownRegionalFeature.SETTLEMENT.name() + ":" + command.causationId()));
        return List.copyOf(produced);
    }

    List<DomainEvent> discoverFeature(WorldState state, DomainCommand.DiscoverRegionalFeature command) {
        LivingRegionState region = region(state, command.regionId());
        AudienceRegionKnowledge knowledge = state.regionKnowledge(command.audience(), region.id()).orElse(null);
        if (knowledge == null || !knowledge.knows(KnownRegionalFeature.SETTLEMENT)) return List.of();
        if (!knowledge.discover(command.feature(), state.simulationStep())) return List.of();
        if (region.knowledgeGatedIncident() && knowledge.introductorySupplyChainKnown()) {
            region.armIncident(knowledge.supplyChainDiscoveredAtStep());
        }
        return List.of(record(state, DomainEventType.REGIONAL_FEATURE_DISCOVERED, region.communityId(),
                command.feature().name() + ":" + command.causationId()));
    }

    List<DomainEvent> observeAccess(WorldState state, DomainCommand.ObserveAudienceRegionAccess command) {
        LivingRegionState region = region(state, command.regionId());
        if (state.regionKnowledge(command.audience(), region.id())
                .filter(value -> value.knows(KnownRegionalFeature.SETTLEMENT)).isEmpty()) return List.of();
        if (command.observedStep() > state.simulationStep()) {
            throw new IllegalArgumentException("Audience access observation cannot come from the future");
        }
        AudienceRegionAccess access = state.regionAccess(command.audience(), command.regionId()).orElse(null);
        if (access == null) {
            state.putRegionAccess(new AudienceRegionAccess(command.audience(), command.regionId(),
                    command.reachability(), command.online(), command.observedStep(), command.observationId()));
        } else if (!access.observe(command.reachability(), command.online(), command.observedStep(),
                command.observationId())) {
            return List.of();
        }
        return List.of(record(state, DomainEventType.AUDIENCE_REGION_ACCESS_CHANGED,
                region.communityId(), command.observationId()));
    }

    private LivingRegionState region(WorldState state, String regionId) {
        return state.livingRegion(regionId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown living region " + regionId));
    }

    private DomainEvent record(WorldState state, DomainEventType type, WorldObjectId subject, String causationId) {
        DomainEvent event = events.create(state, type, subject, causationId);
        state.addEvent(event);
        return event;
    }
}
