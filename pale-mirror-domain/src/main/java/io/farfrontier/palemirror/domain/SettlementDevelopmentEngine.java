package io.farfrontier.palemirror.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Deterministic positive policy; it emits physical intents but never materializes them. */
public final class SettlementDevelopmentEngine {
    private final DomainEventFactory events;

    SettlementDevelopmentEngine(DomainEventFactory events) { this.events = events; }

    public List<DomainEvent> reconcile(WorldState state) {
        List<DomainEvent> produced = new ArrayList<>();
        state.settlementDevelopments().stream().sorted(Comparator.comparing(SettlementDevelopment::communityId))
                .forEach(development -> reconcileCommunity(state, development, produced));
        produced.forEach(state::addEvent);
        return List.copyOf(produced);
    }

    private void reconcileCommunity(WorldState state, SettlementDevelopment development, List<DomainEvent> produced) {
        WorldObjectId communityId = development.communityId();
        SettlementDevelopmentPolicy policy = state.developmentPolicy(communityId).orElseThrow();
        SettlementCommunity community = state.community(communityId).orElseThrow();
        SettlementPlace place = state.communityPlaceBinding(communityId).flatMap(binding -> state.place(binding.placeId())).orElseThrow();
        ResourceAccount iron = state.economy(communityId).orElseThrow().require(ResourceKind.IRON);
        SettlementSecurity security = state.security(communityId).orElseThrow();
        boolean qualifies = place.occupancy() == OccupancyState.INHABITED
                && place.structuralIntegrity() != StructuralIntegrity.RUINED
                && place.observationFreshness() == ObservationFreshness.CURRENT
                && community.crisisState() == CrisisState.NONE
                && iron.availability() == ResourceAvailability.AVAILABLE && iron.netFlow() > 0
                && iron.stock() * 100L >= iron.capacity() * (long) policy.stockPercent()
                && security.defenceReadiness() >= policy.minimumDefence();
        DevelopmentIntent storehouse = intent(state, communityId, DevelopmentIntentType.UPGRADE_STOREHOUSE);
        if (qualifies) development.qualify(); else development.decay();
        if (storehouse == null && development.developmentPressure() >= policy.pressureSteps()) {
            WorldObjectId target = storageSite(state, communityId);
            if (target != null && iron.reserve(policy.investmentIron())) {
                DevelopmentIntent created = new DevelopmentIntent("pm:development:" + communityId.value() + ":storehouse",
                        communityId, DevelopmentIntentType.UPGRADE_STOREHOUSE, target, ResourceKind.IRON,
                        policy.investmentIron(), policy.version(), DevelopmentIntentState.PLANNED, "");
                state.putDevelopmentIntent(created);
                development.resetPressure();
                produced.add(event(state, DomainEventType.SETTLEMENT_DEVELOPMENT_PLANNED, communityId, created.id()));
                storehouse = created;
            }
        }
        boolean pmPopulationGrowth = state.settlementAuthorityProfile(communityId)
                .map(SettlementAuthorityProfile::pmPopulationGrowthAllowed).orElse(true);
        if (storehouse != null && storehouse.state() == DevelopmentIntentState.ACTIVE && qualifies && pmPopulationGrowth) {
            development.advanceGrowth();
            if (development.stableGrowthSteps() >= policy.growthSteps()
                    && state.population(communityId) < development.housingCapacity()) {
                state.populationGroups(communityId).stream()
                        .filter(group -> group.disposition() == PopulationDisposition.RESIDENT).findFirst()
                        .ifPresent(group -> group.grow(SettlementCohort.CIVILIANS, 1));
                development.resetGrowth();
                produced.add(event(state, DomainEventType.SETTLEMENT_POPULATION_GREW, communityId, "policy:stable-prosperity"));
            }
        }
        reconcileReturnHome(state, development, place, iron, produced);
    }

    private void reconcileReturnHome(WorldState state, SettlementDevelopment development, SettlementPlace place,
                                     ResourceAccount iron, List<DomainEvent> produced) {
        if (state.settlementAuthorityProfile(development.communityId())
                .map(profile -> !profile.relocationAllowed()).orElse(false)) return;
        PopulationGroup displaced = state.populationGroups(development.communityId()).stream()
                .filter(group -> group.disposition() == PopulationDisposition.DISPLACED
                        || group.disposition() == PopulationDisposition.RESETTLED).findFirst().orElse(null);
        DevelopmentIntent existing = intent(state, development.communityId(), DevelopmentIntentType.RETURN_HOME);
        boolean threatCleared = state.livingRegions().stream().filter(region -> region.communityId().equals(development.communityId()))
                .allMatch(region -> state.facility(region.primaryFacilityId())
                        .map(facility -> facility.status() == FacilityStatus.OPERATIONAL).orElse(false));
        boolean eligible = displaced != null && threatCleared && iron.availability() == ResourceAvailability.AVAILABLE
                && place.observationFreshness() == ObservationFreshness.CURRENT
                && place.lastReliability() != EvidenceReliability.TENTATIVE
                && place.structuralIntegrity() != StructuralIntegrity.RUINED;
        if (eligible && existing == null) {
            existing = new DevelopmentIntent("pm:development:" + development.communityId().value() + ":return",
                    development.communityId(), DevelopmentIntentType.RETURN_HOME, null, null, 0,
                    state.developmentPolicy(development.communityId()).orElseThrow().version(),
                    DevelopmentIntentState.PLANNED, "");
            state.putDevelopmentIntent(existing);
            produced.add(event(state, DomainEventType.SETTLEMENT_RETURN_PLANNED, development.communityId(), existing.id()));
        } else if (eligible && existing.state() == DevelopmentIntentState.PLANNED
                && !awaitingResettlementAudience(state, development.communityId()) && existing.complete()) {
            displaced.returnHome();
            place.setOccupancy(OccupancyState.INHABITED);
            development.resetGrowth();
            produced.add(event(state, DomainEventType.SETTLEMENT_RETURNED_HOME, development.communityId(), existing.id()));
        }
    }

    private static DevelopmentIntent intent(WorldState state, WorldObjectId communityId, DevelopmentIntentType type) {
        return state.developmentIntents().stream().filter(value -> value.communityId().equals(communityId)
                && value.type() == type && value.state() != DevelopmentIntentState.CANCELLED).findFirst().orElse(null);
    }

    private static boolean awaitingResettlementAudience(WorldState state, WorldObjectId communityId) {
        return state.scenarios().stream().filter(scenario -> scenario.target().equals(communityId))
                .filter(scenario -> scenario.archetype() == ScenarioArchetype.RESETTLEMENT_OPPORTUNITY)
                .anyMatch(scenario -> scenario.status() == ScenarioStatus.OFFERED);
    }

    private static WorldObjectId storageSite(WorldState state, WorldObjectId communityId) {
        return state.siteCapabilities().stream().filter(value -> value.type() == SiteCapabilityType.STORAGE)
                .filter(value -> state.siteAffiliations(value.siteId(), SiteAffiliationRole.RECIPIENT).stream()
                        .anyMatch(affiliation -> affiliation.objectId().equals(communityId)))
                .map(SiteCapability::siteId).sorted().findFirst().orElse(null);
    }

    private DomainEvent event(WorldState state, DomainEventType type, WorldObjectId subject, String causation) {
        return events.create(state, type, subject, causation);
    }
}
