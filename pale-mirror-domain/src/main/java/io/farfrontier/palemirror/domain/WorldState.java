package io.farfrontier.palemirror.domain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Mutable aggregate used only on the server thread. Persistence adapters own serialization. */
public final class WorldState {
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
    private final Map<String, LivingRegionState> livingRegions = new LinkedHashMap<>();
    private final Map<String, AudienceRegionKnowledge> regionKnowledge = new LinkedHashMap<>();
    private final Map<String, AudienceRegionAccess> regionAccess = new LinkedHashMap<>();
    private final List<DomainEvent> history = new ArrayList<>();
    private final Map<StoryAudienceId, Long> narratorCooldowns = new LinkedHashMap<>();
    private long schemaVersion = 1;
    private long simulationStep;
    private long eventSequence;

    public long schemaVersion() { return schemaVersion; }
    public void setSchemaVersion(long schemaVersion) { this.schemaVersion = schemaVersion; }
    public long simulationStep() { return simulationStep; }
    public void setSimulationStep(long value) { simulationStep = value; }
    public long nextEventSequence() { return ++eventSequence; }
    public void setEventSequence(long value) { eventSequence = value; }
    public Collection<FacilityState> facilities() { return facilities.values(); }
    public Collection<ScenarioInstance> scenarios() { return scenarios.values(); }
    public Collection<SettlementCommunity> communities() { return communities.values(); }
    public Collection<SettlementPlace> places() { return places.values(); }
    public Collection<CommunityPlaceBinding> communityPlaceBindings() { return communityPlaceBindings.values(); }
    public Collection<SettlementEconomy> economies() { return economies.values(); }
    public Collection<SettlementSecurity> securities() { return securities.values(); }
    public Collection<SettlementPolicy> settlementPolicies() { return settlementPolicies.values(); }
    public Collection<PopulationGroup> populationGroups() { return populationGroups.values(); }
    public Collection<SettlementEmergencyWindow> emergencyWindows() { return emergencyWindows.values(); }
    public Collection<SettlementDevelopment> settlementDevelopments() { return settlementDevelopments.values(); }
    public Collection<SettlementDevelopmentPolicy> developmentPolicies() { return developmentPolicies.values(); }
    public Collection<SettlementAuthorityProfile> settlementAuthorityProfiles() { return settlementAuthorityProfiles.values(); }
    public Collection<DevelopmentIntent> developmentIntents() { return developmentIntents.values(); }
    public Collection<WorldSite> sites() { return sites.values(); }
    public Collection<SiteAffiliation> siteAffiliations() { return siteAffiliations.values(); }
    public Collection<SiteCapability> siteCapabilities() { return siteCapabilities.values(); }
    public Collection<RouteContract> routeContracts() { return routeContracts.values(); }
    public Collection<WorldPath> worldPaths() { return worldPaths.values(); }
    public Collection<WorldJourney> journeys() { return journeys.values(); }
    public Collection<LivingRegionState> livingRegions() { return livingRegions.values(); }
    public Collection<AudienceRegionKnowledge> regionKnowledge() { return regionKnowledge.values(); }
    public Collection<AudienceRegionAccess> regionAccess() { return regionAccess.values(); }
    public List<DomainEvent> history() { return history; }
    public Map<StoryAudienceId, Long> narratorCooldowns() { return narratorCooldowns; }
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
    public Optional<AudienceRegionAccess> regionAccess(StoryAudienceId audience, String regionId) {
        return Optional.ofNullable(regionAccess.get(knowledgeKey(audience, regionId)));
    }
    public void putFacility(FacilityState facility) { facilities.put(facility.id(), facility); }
    public void putScenario(ScenarioInstance scenario) { scenarios.put(scenario.id(), scenario); }
    public void putCommunity(SettlementCommunity community) { communities.put(community.id(), community); }
    public void putPlace(SettlementPlace place) { places.put(place.id(), place); }
    public void putCommunityPlaceBinding(CommunityPlaceBinding binding) { communityPlaceBindings.put(binding.communityId(), binding); }
    public void putEconomy(SettlementEconomy economy) { economies.put(economy.communityId(), economy); }
    public void putSecurity(SettlementSecurity security) { securities.put(security.communityId(), security); }
    public void putSettlementPolicy(SettlementPolicy policy) { settlementPolicies.put(policy.communityId(), policy); }
    public void putPopulationGroup(PopulationGroup group) {
        if (populationGroups.putIfAbsent(group.id(), group) != null) throw new IllegalStateException("Duplicate population group " + group.id());
    }
    public void putEmergencyWindow(SettlementEmergencyWindow window) { emergencyWindows.put(window.communityId(), window); }
    public void putSettlementDevelopment(SettlementDevelopment value) { settlementDevelopments.put(value.communityId(), value); }
    public void putDevelopmentPolicy(SettlementDevelopmentPolicy value) { developmentPolicies.put(value.communityId(), value); }
    public void putSettlementAuthorityProfile(SettlementAuthorityProfile value) { settlementAuthorityProfiles.put(value.communityId(), value); }
    public void putDevelopmentIntent(DevelopmentIntent value) {
        if (developmentIntents.putIfAbsent(value.id(), value) != null) throw new IllegalStateException("Duplicate development intent " + value.id());
    }
    public void putSite(WorldSite site) { sites.put(site.id(), site); }
    public void putSiteAffiliation(SiteAffiliation affiliation) {
        siteAffiliations.put(affiliationKey(affiliation.siteId(), affiliation.objectId(), affiliation.role()), affiliation);
    }
    public void putSiteCapability(SiteCapability capability) {
        siteCapabilities.put(capabilityKey(capability.siteId(), capability.type(), capability.resource()), capability);
    }
    public void putRouteContract(RouteContract contract) { routeContracts.put(contract.id(), contract); }
    public void putWorldPath(WorldPath path) {
        if (worldPaths.putIfAbsent(path.id(), path) != null) throw new IllegalStateException("Duplicate world path " + path.id());
    }
    public void putJourney(WorldJourney journey) {
        if (journeys.putIfAbsent(journey.id(), journey) != null) throw new IllegalStateException("Duplicate journey " + journey.id());
    }
    public void putLivingRegion(LivingRegionState region) {
        if (livingRegions.putIfAbsent(region.id(), region) != null) throw new IllegalStateException("Duplicate living region " + region.id());
    }
    public void putRegionKnowledge(AudienceRegionKnowledge knowledge) {
        String key = knowledgeKey(knowledge.audience(), knowledge.regionId());
        if (regionKnowledge.putIfAbsent(key, knowledge) != null) {
            throw new IllegalStateException("Duplicate audience region knowledge " + key);
        }
    }
    public AudienceRegionKnowledge requireOrCreateRegionKnowledge(StoryAudienceId audience, String regionId) {
        return regionKnowledge.computeIfAbsent(knowledgeKey(audience, regionId), ignored ->
                new AudienceRegionKnowledge(audience, regionId));
    }
    public void putRegionAccess(AudienceRegionAccess access) {
        regionAccess.put(knowledgeKey(access.audience(), access.regionId()), access);
    }
    public void clearRegionalState() {
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
        livingRegions.clear();
        regionKnowledge.clear();
        regionAccess.clear();
    }
    public void addEvent(DomainEvent event) { history.add(event); }
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
    public void setNarratorCooldown(StoryAudienceId audience, long availableAtStep) {
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
}
