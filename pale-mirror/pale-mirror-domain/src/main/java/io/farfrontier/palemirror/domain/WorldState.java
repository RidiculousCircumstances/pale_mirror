package io.farfrontier.palemirror.domain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Mutable aggregate used only on the server thread. Persistence adapters own serialization. */
public final class WorldState {
    public static final int MAX_DETAILED_HISTORY = 2_048;
    public static final int MAX_DETAILED_TERMINAL_JOURNEYS = 256;
    private final Map<WorldObjectId, FacilityState> facilities = new LinkedHashMap<>();
    private final Map<String, ScenarioInstance> scenarios = new LinkedHashMap<>();
    private final Map<WorldObjectId, SettlementCommunity> communities = new LinkedHashMap<>();
    private final Map<WorldObjectId, SettlementPlace> places = new LinkedHashMap<>();
    private final Map<WorldObjectId, CommunityPlaceBinding> communityPlaceBindings = new LinkedHashMap<>();
    private final Map<WorldObjectId, SettlementEconomy> economies = new LinkedHashMap<>();
    private final Map<WorldObjectId, SettlementSecurity> securities = new LinkedHashMap<>();
    private final Map<WorldObjectId, SettlementPolicy> settlementPolicies = new LinkedHashMap<>();
    private final Map<String, PopulationGroup> populationGroups = new LinkedHashMap<>();
    private final Map<WorldObjectId, SettlementEmergencyWindow> emergencyWindows = new LinkedHashMap<>();
    private final Map<WorldObjectId, SettlementDevelopment> settlementDevelopments = new LinkedHashMap<>();
    private final Map<WorldObjectId, SettlementDevelopmentPolicy> developmentPolicies = new LinkedHashMap<>();
    private final Map<WorldObjectId, SettlementAuthorityProfile> settlementAuthorityProfiles = new LinkedHashMap<>();
    private final Map<String, DevelopmentIntent> developmentIntents = new LinkedHashMap<>();
    private final Map<WorldObjectId, WorldSite> sites = new LinkedHashMap<>();
    private final Map<String, SiteAffiliation> siteAffiliations = new LinkedHashMap<>();
    private final Map<String, SiteCapability> siteCapabilities = new LinkedHashMap<>();
    private final Map<WorldObjectId, RouteContract> routeContracts = new LinkedHashMap<>();
    private final Map<String, WorldPath> worldPaths = new LinkedHashMap<>();
    private final Map<String, WorldJourney> journeys = new LinkedHashMap<>();
    private final Map<JourneyState, WorldJourneySummary> journeySummaries = new LinkedHashMap<>();
    private final Map<String, LivingRegionState> livingRegions = new LinkedHashMap<>();
    private final Map<String, AudienceRegionKnowledge> regionKnowledge = new LinkedHashMap<>();
    private final Map<String, AudienceRegionAccess> regionAccess = new LinkedHashMap<>();
    private final List<DomainEvent> history = new ArrayList<>();
    private final Map<String, DomainEventSummary> historySummaries = new LinkedHashMap<>();
    private final Map<StoryAudienceId, Long> narratorCooldowns = new LinkedHashMap<>();
    private long schemaVersion = 1;
    private long simulationStep;
    private long eventSequence;

    public long schemaVersion() { return schemaVersion; }
    void setSchemaVersion(long schemaVersion) { this.schemaVersion = schemaVersion; }
    public long simulationStep() { return simulationStep; }
    void setSimulationStep(long value) { simulationStep = value; }
    long nextEventSequence() { return ++eventSequence; }
    public long eventSequence() { return eventSequence; }
    void setEventSequence(long value) { eventSequence = value; }
    public Collection<FacilityState> facilities() { return List.copyOf(facilities.values()); }
    public Collection<ScenarioInstance> scenarios() { return List.copyOf(scenarios.values()); }
    public Collection<SettlementCommunity> communities() { return List.copyOf(communities.values()); }
    public Collection<SettlementPlace> places() { return List.copyOf(places.values()); }
    public Collection<CommunityPlaceBinding> communityPlaceBindings() { return List.copyOf(communityPlaceBindings.values()); }
    public Collection<SettlementEconomy> economies() { return List.copyOf(economies.values()); }
    public Collection<SettlementSecurity> securities() { return List.copyOf(securities.values()); }
    public Collection<SettlementPolicy> settlementPolicies() { return List.copyOf(settlementPolicies.values()); }
    public Collection<PopulationGroup> populationGroups() { return List.copyOf(populationGroups.values()); }
    public Collection<SettlementEmergencyWindow> emergencyWindows() { return List.copyOf(emergencyWindows.values()); }
    public Collection<SettlementDevelopment> settlementDevelopments() { return List.copyOf(settlementDevelopments.values()); }
    public Collection<SettlementDevelopmentPolicy> developmentPolicies() { return List.copyOf(developmentPolicies.values()); }
    public Collection<SettlementAuthorityProfile> settlementAuthorityProfiles() { return List.copyOf(settlementAuthorityProfiles.values()); }
    public Collection<DevelopmentIntent> developmentIntents() { return List.copyOf(developmentIntents.values()); }
    public Collection<WorldSite> sites() { return List.copyOf(sites.values()); }
    public Collection<SiteAffiliation> siteAffiliations() { return List.copyOf(siteAffiliations.values()); }
    public Collection<SiteCapability> siteCapabilities() { return List.copyOf(siteCapabilities.values()); }
    public Collection<RouteContract> routeContracts() { return List.copyOf(routeContracts.values()); }
    public Collection<WorldPath> worldPaths() { return List.copyOf(worldPaths.values()); }
    public Collection<WorldJourney> journeys() { return List.copyOf(journeys.values()); }
    public Collection<WorldJourneySummary> journeySummaries() { return List.copyOf(journeySummaries.values()); }
    public Collection<LivingRegionState> livingRegions() { return List.copyOf(livingRegions.values()); }
    public Collection<AudienceRegionKnowledge> regionKnowledge() { return List.copyOf(regionKnowledge.values()); }
    public Collection<AudienceRegionAccess> regionAccess() { return List.copyOf(regionAccess.values()); }
    public List<DomainEvent> history() { return List.copyOf(history); }
    public Collection<DomainEventSummary> historySummaries() { return List.copyOf(historySummaries.values()); }
    public Map<StoryAudienceId, Long> narratorCooldowns() { return Map.copyOf(narratorCooldowns); }
    public Optional<FacilityState> facility(WorldObjectId id) { return Optional.ofNullable(facilities.get(id)); }
    public Optional<ScenarioInstance> scenario(String id) { return Optional.ofNullable(scenarios.get(id)); }
    public Optional<SettlementCommunity> community(WorldObjectId id) { return Optional.ofNullable(communities.get(id)); }
    public Optional<SettlementPlace> place(WorldObjectId id) { return Optional.ofNullable(places.get(id)); }
    public Optional<CommunityPlaceBinding> communityPlaceBinding(WorldObjectId communityId) { return Optional.ofNullable(communityPlaceBindings.get(communityId)); }
    public Optional<CommunityPlaceBinding> bindingForPlace(WorldObjectId placeId) {
        return communityPlaceBindings.values().stream().filter(binding -> binding.placeId().equals(placeId)).findFirst();
    }
    public Optional<SettlementEconomy> economy(WorldObjectId communityId) { return Optional.ofNullable(economies.get(communityId)); }
    public Optional<SettlementSecurity> security(WorldObjectId communityId) { return Optional.ofNullable(securities.get(communityId)); }
    public Optional<SettlementPolicy> settlementPolicy(WorldObjectId communityId) { return Optional.ofNullable(settlementPolicies.get(communityId)); }
    public Optional<PopulationGroup> populationGroup(String id) { return Optional.ofNullable(populationGroups.get(id)); }
    public List<PopulationGroup> populationGroups(WorldObjectId communityId) {
        return populationGroups.values().stream().filter(value -> value.communityId().equals(communityId))
                .sorted(java.util.Comparator.comparing(PopulationGroup::id)).toList();
    }
    public int population(WorldObjectId communityId) { return populationGroups(communityId).stream().mapToInt(PopulationGroup::size).sum(); }
    public Optional<SettlementEmergencyWindow> emergencyWindow(WorldObjectId communityId) { return Optional.ofNullable(emergencyWindows.get(communityId)); }
    public Optional<SettlementDevelopment> settlementDevelopment(WorldObjectId communityId) { return Optional.ofNullable(settlementDevelopments.get(communityId)); }
    public Optional<SettlementDevelopmentPolicy> developmentPolicy(WorldObjectId communityId) { return Optional.ofNullable(developmentPolicies.get(communityId)); }
    public Optional<SettlementAuthorityProfile> settlementAuthorityProfile(WorldObjectId communityId) { return Optional.ofNullable(settlementAuthorityProfiles.get(communityId)); }
    public Optional<DevelopmentIntent> developmentIntent(String id) { return Optional.ofNullable(developmentIntents.get(id)); }
    public Optional<WorldSite> site(WorldObjectId id) { return Optional.ofNullable(sites.get(id)); }
    public Optional<SiteAffiliation> siteAffiliation(WorldObjectId siteId, SiteAffiliationRole role) {
        List<SiteAffiliation> matches = siteAffiliations(siteId, role);
        return matches.size() == 1 ? Optional.of(matches.getFirst()) : Optional.empty();
    }
    public List<SiteAffiliation> siteAffiliations(WorldObjectId siteId, SiteAffiliationRole role) {
        return siteAffiliations.values().stream()
                .filter(value -> value.siteId().equals(siteId) && value.role() == role)
                .sorted(java.util.Comparator.comparing(SiteAffiliation::objectId))
                .toList();
    }
    public Optional<SiteCapability> siteCapability(WorldObjectId siteId, SiteCapabilityType type, ResourceKind resource) {
        return Optional.ofNullable(siteCapabilities.get(capabilityKey(siteId, type, resource)));
    }
    public Optional<RouteContract> routeContract(WorldObjectId id) { return Optional.ofNullable(routeContracts.get(id)); }
    public Optional<WorldPath> worldPath(String id) { return Optional.ofNullable(worldPaths.get(id)); }
    public Optional<WorldJourney> journey(String id) { return Optional.ofNullable(journeys.get(id)); }
    public Optional<LivingRegionState> livingRegion(String id) { return Optional.ofNullable(livingRegions.get(id)); }
    public Optional<AudienceRegionKnowledge> regionKnowledge(StoryAudienceId audience, String regionId) {
        return Optional.ofNullable(regionKnowledge.get(knowledgeKey(audience, regionId)));
    }
    public List<AudienceRegionKnowledge> regionKnowledge(String regionId) {
        return regionKnowledge.values().stream().filter(value -> value.regionId().equals(regionId))
                .sorted(java.util.Comparator.comparing(value -> value.audience().value())).toList();
    }
    public List<StoryAudienceId> audiencesKnowing(String regionId, KnownRegionalFeature feature) {
        return regionKnowledge(regionId).stream().filter(value -> value.knows(feature))
                .map(AudienceRegionKnowledge::audience).toList();
    }
    public Optional<AudienceRegionAccess> regionAccess(StoryAudienceId audience, String regionId) {
        return Optional.ofNullable(regionAccess.get(knowledgeKey(audience, regionId)));
    }
    void putFacility(FacilityState facility) { facilities.put(facility.id(), facility); }
    void putScenario(ScenarioInstance scenario) { scenarios.put(scenario.id(), scenario); }
    void putCommunity(SettlementCommunity community) { communities.put(community.id(), community); }
    void putPlace(SettlementPlace place) { places.put(place.id(), place); }
    void putCommunityPlaceBinding(CommunityPlaceBinding binding) { communityPlaceBindings.put(binding.communityId(), binding); }
    void putEconomy(SettlementEconomy economy) { economies.put(economy.communityId(), economy); }
    void putSecurity(SettlementSecurity security) { securities.put(security.communityId(), security); }
    void putSettlementPolicy(SettlementPolicy policy) { settlementPolicies.put(policy.communityId(), policy); }
    void putPopulationGroup(PopulationGroup group) {
        if (populationGroups.putIfAbsent(group.id(), group) != null) throw new IllegalStateException("Duplicate population group " + group.id());
    }
    void putEmergencyWindow(SettlementEmergencyWindow window) { emergencyWindows.put(window.communityId(), window); }
    void putSettlementDevelopment(SettlementDevelopment value) { settlementDevelopments.put(value.communityId(), value); }
    void putDevelopmentPolicy(SettlementDevelopmentPolicy value) { developmentPolicies.put(value.communityId(), value); }
    void putSettlementAuthorityProfile(SettlementAuthorityProfile value) { settlementAuthorityProfiles.put(value.communityId(), value); }
    void putDevelopmentIntent(DevelopmentIntent value) {
        if (developmentIntents.putIfAbsent(value.id(), value) != null) throw new IllegalStateException("Duplicate development intent " + value.id());
    }
    void putSite(WorldSite site) { sites.put(site.id(), site); }
    void putSiteAffiliation(SiteAffiliation affiliation) {
        siteAffiliations.put(affiliationKey(affiliation.siteId(), affiliation.objectId(), affiliation.role()), affiliation);
    }
    void putSiteCapability(SiteCapability capability) {
        siteCapabilities.put(capabilityKey(capability.siteId(), capability.type(), capability.resource()), capability);
    }
    void putRouteContract(RouteContract contract) { routeContracts.put(contract.id(), contract); }
    void putWorldPath(WorldPath path) {
        if (worldPaths.putIfAbsent(path.id(), path) != null) throw new IllegalStateException("Duplicate world path " + path.id());
    }
    void putJourney(WorldJourney journey) {
        if (journeys.putIfAbsent(journey.id(), journey) != null) throw new IllegalStateException("Duplicate journey " + journey.id());
    }
    void putJourneySummary(WorldJourneySummary summary) {
        journeySummaries.merge(summary.outcome(), summary, (left, right) -> new WorldJourneySummary(left.outcome(),
                Math.addExact(left.count(), right.count()), Math.addExact(left.confirmedLosses(), right.confirmedLosses()),
                Math.max(left.lastCompletedStep(), right.lastCompletedStep())));
    }
    void compactTerminalJourneys() {
        List<WorldJourney> terminal = journeys.values().stream().filter(WorldJourney::terminal)
                .sorted(java.util.Comparator.comparingLong((WorldJourney value) -> value.startedAtStep() + value.elapsedSteps())
                        .thenComparing(WorldJourney::id)).toList();
        int remove = terminal.size() - MAX_DETAILED_TERMINAL_JOURNEYS;
        for (int index = 0; index < remove; index++) {
            WorldJourney journey = terminal.get(index);
            journeys.remove(journey.id());
            putJourneySummary(new WorldJourneySummary(journey.state(), 1, journey.confirmedLosses(),
                    journey.startedAtStep() + journey.elapsedSteps()));
        }
    }
    void putLivingRegion(LivingRegionState region) {
        if (livingRegions.putIfAbsent(region.id(), region) != null) throw new IllegalStateException("Duplicate living region " + region.id());
    }
    void putRegionKnowledge(AudienceRegionKnowledge knowledge) {
        String key = knowledgeKey(knowledge.audience(), knowledge.regionId());
        if (regionKnowledge.putIfAbsent(key, knowledge) != null) {
            throw new IllegalStateException("Duplicate audience region knowledge " + key);
        }
    }
    AudienceRegionKnowledge requireOrCreateRegionKnowledge(StoryAudienceId audience, String regionId) {
        return regionKnowledge.computeIfAbsent(knowledgeKey(audience, regionId), ignored ->
                new AudienceRegionKnowledge(audience, regionId));
    }
    void putRegionAccess(AudienceRegionAccess access) {
        regionAccess.put(knowledgeKey(access.audience(), access.regionId()), access);
    }
    void clearRegionalState() {
        communities.clear();
        places.clear();
        communityPlaceBindings.clear();
        economies.clear();
        securities.clear();
        settlementPolicies.clear();
        populationGroups.clear();
        emergencyWindows.clear();
        settlementDevelopments.clear();
        developmentPolicies.clear();
        settlementAuthorityProfiles.clear();
        developmentIntents.clear();
        sites.clear();
        siteAffiliations.clear();
        siteCapabilities.clear();
        routeContracts.clear();
        worldPaths.clear();
        journeys.clear();
        journeySummaries.clear();
        livingRegions.clear();
        regionKnowledge.clear();
        regionAccess.clear();
    }
    void clearAll() {
        facilities.clear();
        scenarios.clear();
        clearRegionalState();
        history.clear();
        historySummaries.clear();
        narratorCooldowns.clear();
        simulationStep = 0;
        eventSequence = 0;
    }
    void addEvent(DomainEvent event) {
        history.add(event);
        while (history.size() > MAX_DETAILED_HISTORY) summarize(history.removeFirst());
    }
    void putEventSummary(DomainEventSummary summary) {
        String key = summaryKey(summary.subject(), summary.type());
        DomainEventSummary previous = historySummaries.get(key);
        historySummaries.put(key, previous == null ? summary : new DomainEventSummary(summary.subject(), summary.type(),
                Math.addExact(previous.count(), summary.count()), Math.min(previous.firstStep(), summary.firstStep()),
                Math.max(previous.lastStep(), summary.lastStep())));
    }
    public boolean hasScenarioForSource(String sourceEventId, StoryAudienceId audience) {
        return scenarios.values().stream().anyMatch(value -> value.sourceEventId().equals(sourceEventId) && value.audience().equals(audience));
    }
    public boolean hasNarratorDecisionForSource(String sourceEventId, StoryAudienceId audience) {
        return hasScenarioForSource(sourceEventId, audience) || history.stream().anyMatch(event ->
                event.type() == DomainEventType.NO_SCENARIO && sourceEventId.equals(event.correlationId())
                        && audience.value().equals(event.causationId()));
    }
    public boolean narratorReady(StoryAudienceId audience) {
        return simulationStep >= narratorCooldowns.getOrDefault(audience, 0L);
    }
    public boolean hasRespondingScenario(StoryAudienceId audience, WorldObjectId target,
                                         ScenarioArchetype archetype) {
        return scenarios.values().stream().anyMatch(value -> value.audience().equals(audience)
                && value.target().equals(target) && value.archetype() == archetype
                && value.status() == ScenarioStatus.RESPOND);
    }
    void setNarratorCooldown(StoryAudienceId audience, long availableAtStep) {
        narratorCooldowns.put(audience, availableAtStep);
    }

    private static String capabilityKey(WorldObjectId siteId, SiteCapabilityType type, ResourceKind resource) {
        return siteId.value() + "|" + type + "|" + (resource == null ? "" : resource.name());
    }

    private static String affiliationKey(WorldObjectId siteId, WorldObjectId objectId, SiteAffiliationRole role) {
        return siteId.value() + "|" + objectId.value() + "|" + role.name();
    }

    private static String knowledgeKey(StoryAudienceId audience, String regionId) {
        return audience.value() + "|" + regionId;
    }

    private void summarize(DomainEvent event) {
        String key = summaryKey(event.subject(), event.type());
        historySummaries.compute(key, (ignored, existing) -> existing == null
                ? new DomainEventSummary(event.subject(), event.type(), 1, event.simulationStep(), event.simulationStep())
                : existing.include(event));
    }

    private static String summaryKey(WorldObjectId subject, DomainEventType type) {
        return subject.value() + "|" + type.name();
    }
}
