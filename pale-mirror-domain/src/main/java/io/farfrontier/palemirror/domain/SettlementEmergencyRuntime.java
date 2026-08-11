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
                AudienceRegionAccess access = region == null || region.primaryAudience() == null ? null
                        : state.regionAccess(region.primaryAudience(), region.id()).orElse(null);
                AudienceRegionReachability reachability = access == null
                        ? AudienceRegionReachability.LOCAL : access.reachability();
                long grace = adjustedGrace(policy.emergencyGraceSteps(), reachability);
                window = new SettlementEmergencyWindow(community.id(), state.simulationStep(), grace, grace,
                        reachability, EmergencyWindowState.OPEN);
                state.putEmergencyWindow(window);
                produced.add(event(state, DomainEventType.SETTLEMENT_EMERGENCY_WINDOW_OPENED, community.id(), "policy:emergency"));
            } else if (window != null && window.state() == EmergencyWindowState.OPEN && !objectiveEmergency && window.close()) {
                produced.add(event(state, DomainEventType.SETTLEMENT_EMERGENCY_WINDOW_CLOSED, community.id(), "policy:recovered"));
            } else if (window != null && window.state() == EmergencyWindowState.OPEN
                    && window.elapse(audiencePresent(state, community.id()))) {
                produced.addAll(beginEvacuation(state, community.id(), "policy:grace-expired"));
            }
            SettlementEmergencyWindow current = state.emergencyWindow(community.id()).orElse(null);
            if (current != null && current.state() == EmergencyWindowState.EVACUATING) {
                boolean due = state.populationGroups(community.id()).stream()
                        .filter(value -> value.disposition() == PopulationDisposition.EVACUATING)
                        .allMatch(value -> state.simulationStep() >= value.transitionDueStep());
                if (due) produced.addAll(completeEvacuation(state, community.id()));
            }
        });
        produced.forEach(state::addEvent);
        return List.copyOf(produced);
    }

    public List<DomainEvent> beginEvacuation(WorldState state, WorldObjectId communityId, String causationId) {
        if (state.settlementAuthorityProfile(communityId).map(profile -> !profile.relocationAllowed()).orElse(false)) {
            return List.of();
        }
        SettlementEmergencyWindow window = state.emergencyWindow(communityId).orElse(null);
        SettlementPolicy policy = state.settlementPolicy(communityId).orElseThrow();
        if (window == null || !window.beginEvacuation()) return List.of();
        boolean changed = false;
        for (PopulationGroup group : state.populationGroups(communityId)) {
            changed |= group.beginEvacuation(state.simulationStep() + policy.evacuationDurationSteps());
        }
        state.communityPlaceBinding(communityId).flatMap(binding -> state.place(binding.placeId()))
                .ifPresent(place -> place.setOccupancy(OccupancyState.EVACUATING));
        return changed ? List.of(event(state, DomainEventType.SETTLEMENT_EVACUATION_STARTED, communityId, causationId)) : List.of();
    }

    private List<DomainEvent> completeEvacuation(WorldState state, WorldObjectId communityId) {
        List<DomainEvent> produced = new ArrayList<>();
        WorldObjectId shelter = shelter(state, communityId);
        for (PopulationGroup group : state.populationGroups(communityId)) {
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

    private static WorldObjectId shelter(WorldState state, WorldObjectId communityId) {
        return state.siteCapabilities().stream().filter(value -> value.type() == SiteCapabilityType.SHELTER)
                .filter(value -> state.site(value.siteId()).map(site -> site.operationalState() == OperationalState.OPERATIONAL).orElse(false))
                .filter(value -> value.capacity() >= state.population(communityId))
                .filter(value -> state.siteAffiliations(value.siteId(), SiteAffiliationRole.RECIPIENT).stream()
                        .anyMatch(affiliation -> affiliation.objectId().equals(communityId)))
                .map(SiteCapability::siteId).sorted().findFirst().orElse(null);
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

    private static boolean audiencePresent(WorldState state, WorldObjectId communityId) {
        LivingRegionState region = state.livingRegions().stream()
                .filter(value -> value.communityId().equals(communityId)).findFirst().orElse(null);
        return region != null && region.primaryAudience() != null
                && state.regionAccess(region.primaryAudience(), region.id()).map(AudienceRegionAccess::present).orElse(false);
    }

    private DomainEvent event(WorldState state, DomainEventType type, WorldObjectId subject, String causation) {
        return events.create(state, type, subject, causation);
    }
}
