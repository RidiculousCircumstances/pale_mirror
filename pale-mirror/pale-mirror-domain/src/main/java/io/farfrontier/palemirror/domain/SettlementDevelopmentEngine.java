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
        reconcileReconstruction(state, development, place, iron, produced);
        if (qualifies) development.qualify(); else development.decay();
        if (storehouse == null && development.developmentPressure() >= policy.pressureSteps()) {
            WorldObjectId target = developmentSite(state, communityId);
            if (target != null) {
                DevelopmentIntent created = new DevelopmentIntent("pm:development:" + communityId.value() + ":storehouse",
                        communityId, DevelopmentIntentType.UPGRADE_STOREHOUSE, target, ResourceKind.IRON,
                        policy.investmentIron(), 0, 0, 0, policy.autonomousInvestmentSteps(), java.util.Set.of(),
                        policy.version(), DevelopmentIntentState.PLANNED, "");
                state.putDevelopmentIntent(created);
                development.resetPressure();
                produced.add(event(state, DomainEventType.SETTLEMENT_DEVELOPMENT_PLANNED, communityId, created.id()));
                storehouse = created;
            }
        }
        if (storehouse != null && storehouse.state() == DevelopmentIntentState.PLANNED && qualifies
                && !storehouse.funded()) {
            storehouse.waitQualifiedStep();
            if (storehouse.autonomousFundingDue() && iron.reserve(storehouse.remainingAmount())) {
                storehouse.reserveAutonomously(storehouse.remainingAmount());
                produced.add(event(state, DomainEventType.SETTLEMENT_DEVELOPMENT_FUNDED,
                        communityId, "policy:autonomous-investment"));
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

    private void reconcileReconstruction(WorldState state, SettlementDevelopment development, SettlementPlace place,
                                         ResourceAccount iron, List<DomainEvent> produced) {
        WorldObjectId communityId = development.communityId();
        DevelopmentIntent reconstruction = intent(state, communityId, DevelopmentIntentType.RECONSTRUCT_PLACE);
        boolean threatCleared = state.livingRegions().stream().filter(region -> region.communityId().equals(communityId))
                .allMatch(region -> state.facility(region.primaryFacilityId())
                        .map(facility -> facility.status() == FacilityStatus.OPERATIONAL).orElse(false));
        SettlementDevelopmentPolicy policy = state.developmentPolicy(communityId).orElseThrow();
        if (place.structuralIntegrity() != StructuralIntegrity.INTACT && threatCleared && reconstruction == null) {
            int required = Math.multiplyExact(policy.investmentIron(), 2);
            reconstruction = new DevelopmentIntent("pm:development:" + communityId.value() + ":reconstruction",
                    communityId, DevelopmentIntentType.RECONSTRUCT_PLACE, place.id(), ResourceKind.IRON,
                    required, 0, 0, 0, policy.autonomousInvestmentSteps(), java.util.Set.of(), policy.version(),
                    DevelopmentIntentState.PLANNED, "");
            state.putDevelopmentIntent(reconstruction);
            produced.add(event(state, DomainEventType.SETTLEMENT_RECONSTRUCTION_PLANNED, communityId, reconstruction.id()));
        }
        if (reconstruction != null && reconstruction.state() == DevelopmentIntentState.PLANNED && threatCleared
                && !reconstruction.funded() && iron.availability() != ResourceAvailability.UNAVAILABLE) {
            reconstruction.waitQualifiedStep();
            if (reconstruction.autonomousFundingDue() && iron.reserve(reconstruction.remainingAmount())) {
                reconstruction.reserveAutonomously(reconstruction.remainingAmount());
                produced.add(event(state, DomainEventType.SETTLEMENT_DEVELOPMENT_FUNDED,
                        communityId, "policy:reconstruction-investment"));
            }
        }
    }

    private void reconcileReturnHome(WorldState state, SettlementDevelopment development, SettlementPlace place,
                                     ResourceAccount iron, List<DomainEvent> produced) {
        if (state.settlementAuthorityProfile(development.communityId())
                .map(profile -> !profile.relocationAllowed()).orElse(false)) return;
        PopulationGroup returning = state.populationGroups(development.communityId()).stream()
                .filter(group -> group.disposition() == PopulationDisposition.IN_TRANSIT && group.journeyId() != null)
                .filter(group -> state.journey(group.journeyId()).map(journey -> journey.riskPolicy().id()
                        .equals("pale_mirror:community_return")).orElse(false)).findFirst().orElse(null);
        DevelopmentIntent existing = intent(state, development.communityId(), DevelopmentIntentType.RETURN_HOME);
        if (returning != null && existing != null && existing.state() == DevelopmentIntentState.ACTIVE) {
            WorldJourney journey = state.journey(returning.journeyId()).orElseThrow();
            if (journey.state() == JourneyState.ARRIVED && returning.arriveHome(journey.id())) {
                place.setOccupancy(OccupancyState.INHABITED); development.resetGrowth();
                produced.add(event(state, DomainEventType.SETTLEMENT_RETURNED_HOME, development.communityId(), existing.id()));
            } else if (journey.state() == JourneyState.LOST) returning.displace();
            return;
        }
        PopulationGroup displaced = state.populationGroups(development.communityId()).stream()
                .filter(group -> group.disposition() == PopulationDisposition.DISPLACED
                        || group.disposition() == PopulationDisposition.RESETTLED).findFirst().orElse(null);
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
            WorldPath path = displaced.hostSiteId() == null ? null : state.worldPaths().stream()
                    .filter(value -> value.originSiteId().equals(displaced.hostSiteId()))
                    .sorted(Comparator.comparing(WorldPath::id)).findFirst().orElse(null);
            if (path == null) { existing.block("No canonical return path"); return; }
            String journeyId = "pale_mirror:journey:return:" + displaced.id() + ":" + state.simulationStep();
            if (!displaced.beginReturnJourney(journeyId)) { existing.block("Population cannot begin return journey"); return; }
            state.putJourney(WorldJourney.communityReturn(journeyId, displaced.id(), path.id(), path.originSiteId(),
                    path.destinationSiteId(), state.simulationStep(), Math.max(4, path.nodes().size() * 2L), stableSeed(journeyId)));
            produced.add(event(state, DomainEventType.JOURNEY_STARTED, development.communityId(), existing.id()));
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

    private static WorldObjectId developmentSite(WorldState state, WorldObjectId communityId) {
        return state.sites().stream().filter(value -> value.type() == WorldSiteType.DEVELOPMENT)
                .filter(value -> value.operationalState() != OperationalState.OPERATIONAL)
                .filter(value -> state.siteAffiliations(value.id(), SiteAffiliationRole.RECIPIENT).stream()
                        .anyMatch(affiliation -> affiliation.objectId().equals(communityId)))
                .map(WorldSite::id).sorted().findFirst().orElse(null);
    }

    private DomainEvent event(WorldState state, DomainEventType type, WorldObjectId subject, String causation) {
        return events.create(state, type, subject, causation);
    }
    private static long stableSeed(String value) {
        long result = 1125899906842597L;
        for (int index = 0; index < value.length(); index++) result = 31 * result + value.charAt(index);
        return result;
    }
}
