package io.farfrontier.palemirror.domain;

import java.util.List;

/** One-shot repair for the legacy bug that ignored a player-prepared evacuation destination. */
final class PreparedShelterReconciliationRuntime {
    private final DomainEventFactory events;

    PreparedShelterReconciliationRuntime(DomainEventFactory events) {
        this.events = events;
    }

    List<DomainEvent> reconcile(WorldState state, DomainCommand.ReconcilePreparedShelterDestination command) {
        PopulationGroup group = state.populationGroup(command.populationGroupId()).orElse(null);
        WorldPath path = state.worldPath(command.pathId()).orElse(null);
        boolean eligible = state.site(command.shelterSiteId()).map(site ->
                        site.type() == WorldSiteType.SHELTER && site.operationalState() != OperationalState.OFFLINE)
                .orElse(false)
                && state.siteCapabilities().stream().anyMatch(capability ->
                        capability.siteId().equals(command.shelterSiteId())
                                && capability.type() == SiteCapabilityType.SHELTER
                                && capability.capacity() >= state.population(command.communityId()))
                && state.siteAffiliations(command.shelterSiteId(), SiteAffiliationRole.RECIPIENT).stream()
                .anyMatch(affiliation -> affiliation.objectId().equals(command.communityId()));
        WorldJourney completedWrongJourney = group == null ? null : state.journeys().stream()
                .filter(journey -> journey.subjectGroupId().equals(group.id()))
                .filter(journey -> journey.state() == JourneyState.ARRIVED)
                .filter(journey -> !journey.destinationSiteId().equals(command.shelterSiteId()))
                .filter(journey -> journey.riskPolicy().id().equals(JourneyRiskPolicy.evacuationDefault().id()))
                .filter(journey -> group.disposition() == PopulationDisposition.IN_TRANSIT
                        ? journey.id().equals(group.journeyId())
                        : group.disposition() == PopulationDisposition.RESETTLED
                                && journey.destinationSiteId().equals(group.hostSiteId()))
                .findFirst().orElse(null);
        boolean completedWrongEvacuation = group != null && group.communityId().equals(command.communityId())
                && completedWrongJourney != null;
        if (!eligible || !completedWrongEvacuation || path == null
                || !path.destinationSiteId().equals(command.shelterSiteId())) return List.of();
        boolean hosted = group.disposition() == PopulationDisposition.IN_TRANSIT
                ? group.hostAt(command.shelterSiteId()) : group.rehostAt(command.shelterSiteId());
        if (!hosted) return List.of();
        String journeyId = "pale_mirror:journey:prepared-correction:" + group.id() + ":"
                + Integer.toUnsignedString(command.shelterSiteId().value().hashCode(), 36);
        if (state.journey(journeyId).isEmpty()) {
            state.putJourney(new WorldJourney(journeyId, group.id(), path.id(), path.originSiteId(),
                    command.shelterSiteId(), JourneyMode.LAND_FOOT, JourneyRiskPolicy.evacuationDefault(),
                    0L, state.simulationStep(), 1L, JourneyState.ARRIVED, 1L, 0, 0, "", 0L));
        }
        DomainEvent event = events.create(state, DomainEventType.POPULATION_RESETTLED,
                command.communityId(), command.causationId());
        state.addEvent(event);
        return List.of(event);
    }
}
