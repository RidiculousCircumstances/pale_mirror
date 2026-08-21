package io.farfrontier.palemirror.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Deterministic fairness and displacement policy. Narrator only presents its facts. */
public final class SettlementEmergencyRuntime {
    private final DomainEventFactory events;

    SettlementEmergencyRuntime(DomainEventFactory events) { this.events = events; }

    public List<DomainEvent> reconcile(WorldState state) {
        List<DomainEvent> produced = new ArrayList<>();
        state.communities().stream().sorted(Comparator.comparing(SettlementCommunity::id)).forEach(community -> {
            if (state.settlementAuthorityProfile(community.id()).map(profile -> !profile.relocationAllowed()).orElse(false)) return;
            SettlementPolicy policy = state.settlementPolicy(community.id()).orElseThrow();
            SettlementSecurity security = state.security(community.id()).orElseThrow();
            SettlementEmergencyWindow window = state.emergencyWindow(community.id()).orElse(null);
            boolean objectiveEmergency = community.crisisState() == CrisisState.CRITICAL
                    && activeThreat(state, community.id())
                    && security.defenceReadiness() <= policy.evacuationDefenceThreshold();
            if (window == null && objectiveEmergency) {
                LivingRegionState region = state.livingRegions().stream()
                        .filter(value -> value.communityId().equals(community.id())).findFirst().orElse(null);
                AudienceRegionReachability reachability = region == null ? AudienceRegionReachability.REMOTE
                        : bestOnlineReachability(state, region.id());
                long grace = adjustedGrace(policy.emergencyGraceSteps(), reachability);
                window = new SettlementEmergencyWindow(community.id(), state.simulationStep(), grace, grace,
                        reachability, EmergencyWindowState.OPEN);
                state.putEmergencyWindow(window);
                produced.add(event(state, DomainEventType.SETTLEMENT_EMERGENCY_WINDOW_OPENED, community.id(), "policy:emergency"));
            } else if (window != null && window.state() == EmergencyWindowState.OPEN && !objectiveEmergency && window.close()) {
                produced.add(event(state, DomainEventType.SETTLEMENT_EMERGENCY_WINDOW_CLOSED, community.id(), "policy:recovered"));
            } else if (window != null && window.state() == EmergencyWindowState.OPEN
                    && window.elapse(anyAudienceOnline(state, community.id()))) {
                produced.addAll(beginEvacuation(state, community.id(), null, "policy:grace-expired"));
            }
            SettlementEmergencyWindow current = state.emergencyWindow(community.id()).orElse(null);
            if (current != null && current.state() == EmergencyWindowState.EVACUATING) {
                boolean complete = state.populationGroups(community.id()).stream().allMatch(group ->
                        group.journeyId() != null && state.journey(group.journeyId()).map(journey ->
                                journey.state() == JourneyState.LOST || journey.state() == JourneyState.ARRIVED
                                        && state.site(journey.destinationSiteId()).map(site ->
                                        site.operationalState() == OperationalState.OPERATIONAL).orElse(false)).orElse(false));
                if (complete) produced.addAll(completeEvacuation(state, community.id()));
            }
        });
        produced.forEach(state::addEvent);
        return List.copyOf(produced);
    }

    public List<DomainEvent> beginEvacuation(WorldState state, WorldObjectId communityId,
                                             WorldObjectId selectedShelterId, String causationId) {
        if (state.settlementAuthorityProfile(communityId).map(profile -> !profile.relocationAllowed()).orElse(false)) {
            return List.of();
        }
        SettlementEmergencyWindow window = state.emergencyWindow(communityId).orElse(null);
        SettlementPolicy policy = state.settlementPolicy(communityId).orElseThrow();
        if (window == null || window.state() != EmergencyWindowState.OPEN) return List.of();
        WorldObjectId destination = selectedShelterId == null ? shelter(state, communityId, false)
                : eligibleShelter(state, communityId, selectedShelterId, false) ? selectedShelterId : null;
        WorldPath path = destination == null ? null : state.worldPaths().stream()
                .filter(candidate -> candidate.destinationSiteId().equals(destination)).sorted(Comparator.comparing(WorldPath::id))
                .findFirst().orElse(null);
        if (destination == null || path == null) return List.of();
        if (!window.beginEvacuation()) return List.of();
        boolean changed = false;
        for (PopulationGroup group : state.populationGroups(communityId)) {
            String journeyId = "pale_mirror:journey:" + group.id() + ":" + state.simulationStep();
            if (group.beginEvacuation(state.simulationStep() + policy.evacuationDurationSteps())
                    && group.beginJourney(journeyId)) {
                state.putJourney(WorldJourney.evacuation(journeyId, group.id(), path.id(), path.originSiteId(), destination,
                        state.simulationStep(), policy.evacuationDurationSteps(), stableSeed(journeyId)));
                changed = true;
            }
        }
        state.communityPlaceBinding(communityId).flatMap(binding -> state.place(binding.placeId()))
                .ifPresent(place -> place.setOccupancy(OccupancyState.EVACUATING));
        if (!changed) return List.of();
        return List.of(event(state, DomainEventType.SETTLEMENT_EVACUATION_STARTED, communityId, causationId),
                event(state, DomainEventType.JOURNEY_STARTED, communityId, causationId));
    }

    private List<DomainEvent> completeEvacuation(WorldState state, WorldObjectId communityId) {
        List<DomainEvent> produced = new ArrayList<>();
        for (PopulationGroup group : state.populationGroups(communityId)) {
            WorldJourney journey = group.journeyId() == null ? null : state.journey(group.journeyId()).orElse(null);
            WorldObjectId shelter = journey == null || journey.state() == JourneyState.LOST ? null : journey.destinationSiteId();
            boolean changed = shelter == null ? group.displace() : group.hostAt(shelter);
            if (changed) produced.add(event(state, shelter == null ? DomainEventType.POPULATION_DISPLACED
                    : DomainEventType.POPULATION_RESETTLED, communityId, "policy:evacuation-complete"));
        }
        state.communityPlaceBinding(communityId).flatMap(binding -> state.place(binding.placeId())).ifPresent(place -> {
            place.setOccupancy(OccupancyState.EMPTY);
            if (activeThreat(state, communityId)
                    && state.settlementAuthorityProfile(communityId).map(SettlementAuthorityProfile::pmRuinAllowed).orElse(true)) {
                place.setStructuralIntegrity(StructuralIntegrity.RUINED);
                produced.add(event(state, DomainEventType.SETTLEMENT_PLACE_RUINED, place.id(), "policy:unopposed-threat"));
            }
        });
        state.emergencyWindow(communityId).ifPresent(SettlementEmergencyWindow::resolve);
        produced.add(event(state, DomainEventType.SETTLEMENT_EVACUATION_COMPLETED, communityId, "policy:evacuation-complete"));
        return produced;
    }

    private static WorldObjectId shelter(WorldState state, WorldObjectId communityId, boolean operationalOnly) {
        return state.siteCapabilities().stream().filter(value -> value.type() == SiteCapabilityType.SHELTER)
                .filter(value -> state.site(value.siteId()).map(site -> operationalOnly
                        ? site.operationalState() == OperationalState.OPERATIONAL
                        : site.operationalState() != OperationalState.OFFLINE).orElse(false))
                .filter(value -> value.capacity() >= state.population(communityId))
                .filter(value -> state.siteAffiliations(value.siteId(), SiteAffiliationRole.RECIPIENT).stream()
                        .anyMatch(affiliation -> affiliation.objectId().equals(communityId)))
                .map(SiteCapability::siteId).sorted().findFirst().orElse(null);
    }

    private static boolean eligibleShelter(WorldState state, WorldObjectId communityId,
                                           WorldObjectId siteId, boolean operationalOnly) {
        return state.siteCapabilities().stream().filter(value -> value.siteId().equals(siteId))
                .filter(value -> value.type() == SiteCapabilityType.SHELTER)
                .filter(value -> value.capacity() >= state.population(communityId))
                .anyMatch(value -> state.site(siteId).map(site -> (operationalOnly
                                ? site.operationalState() == OperationalState.OPERATIONAL
                                : site.operationalState() != OperationalState.OFFLINE)
                        && state.siteAffiliations(siteId, SiteAffiliationRole.RECIPIENT).stream()
                        .anyMatch(affiliation -> affiliation.objectId().equals(communityId))).orElse(false));
    }

    private static long stableSeed(String value) {
        long result = 1125899906842597L;
        for (int index = 0; index < value.length(); index++) result = 31 * result + value.charAt(index);
        return result;
    }

    private static boolean activeThreat(WorldState state, WorldObjectId communityId) {
        return state.livingRegions().stream().filter(region -> region.communityId().equals(communityId))
                .map(region -> state.facility(region.primaryFacilityId()).orElse(null))
                .anyMatch(facility -> facility != null && facility.status() != FacilityStatus.OPERATIONAL);
    }

    static long adjustedGrace(long base, AudienceRegionReachability reachability) {
        return switch (reachability) {
            case LOCAL -> base;
            case REGIONAL -> base + Math.max(1, base / 2);
            case REMOTE -> base * 2;
            case CONNECTED -> Math.max(1, base * 2 / 3);
        };
    }

    private static boolean anyAudienceOnline(WorldState state, WorldObjectId communityId) {
        LivingRegionState region = state.livingRegions().stream()
                .filter(value -> value.communityId().equals(communityId)).findFirst().orElse(null);
        return region != null && state.audiencesKnowing(region.id(), KnownRegionalFeature.SETTLEMENT).stream()
                .anyMatch(audience -> state.regionAccess(audience, region.id())
                        .map(AudienceRegionAccess::online).orElse(false));
    }

    private static AudienceRegionReachability bestOnlineReachability(WorldState state, String regionId) {
        return state.audiencesKnowing(regionId, KnownRegionalFeature.SETTLEMENT).stream()
                .map(audience -> state.regionAccess(audience, regionId).orElse(null))
                .filter(java.util.Objects::nonNull).filter(AudienceRegionAccess::online)
                .map(AudienceRegionAccess::reachability)
                .min(Comparator.comparingInt(SettlementEmergencyRuntime::reachabilityRank))
                .orElse(AudienceRegionReachability.REMOTE);
    }

    private static int reachabilityRank(AudienceRegionReachability reachability) {
        return switch (reachability) {
            case CONNECTED -> 0;
            case LOCAL -> 1;
            case REGIONAL -> 2;
            case REMOTE -> 3;
        };
    }

    private DomainEvent event(WorldState state, DomainEventType type, WorldObjectId subject, String causation) {
        return events.create(state, type, subject, causation);
    }
}
