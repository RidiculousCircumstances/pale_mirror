package io.farfrontier.palemirror.domain;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Persistence-only reconstruction boundary. Runtime code must use
 * {@link DomainCommandProcessor}; the NeoForge architecture gate restricts
 * this builder to the snapshot codecs.
 */
public final class WorldStateHydration {
    private WorldStateHydration() {}

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final List<FacilityState> facilities = new ArrayList<>();
        private final List<ScenarioInstance> scenarios = new ArrayList<>();
        private final List<SettlementCommunity> communities = new ArrayList<>();
        private final List<SettlementPlace> places = new ArrayList<>();
        private final List<CommunityPlaceBinding> bindings = new ArrayList<>();
        private final List<SettlementEconomy> economies = new ArrayList<>();
        private final List<SettlementSecurity> securities = new ArrayList<>();
        private final List<SettlementPolicy> policies = new ArrayList<>();
        private final List<PopulationGroup> populationGroups = new ArrayList<>();
        private final List<SettlementEmergencyWindow> emergencyWindows = new ArrayList<>();
        private final List<SettlementDevelopment> developments = new ArrayList<>();
        private final List<SettlementDevelopmentPolicy> developmentPolicies = new ArrayList<>();
        private final List<SettlementAuthorityProfile> authorityProfiles = new ArrayList<>();
        private final List<DevelopmentIntent> developmentIntents = new ArrayList<>();
        private final List<WorldSite> sites = new ArrayList<>();
        private final List<SiteAffiliation> affiliations = new ArrayList<>();
        private final List<SiteCapability> capabilities = new ArrayList<>();
        private final List<RouteContract> routes = new ArrayList<>();
        private final List<WorldPath> paths = new ArrayList<>();
        private final List<WorldJourney> journeys = new ArrayList<>();
        private final List<WorldJourneySummary> journeySummaries = new ArrayList<>();
        private final List<LivingRegionState> regions = new ArrayList<>();
        private final List<AudienceRegionKnowledge> knowledge = new ArrayList<>();
        private final List<AudienceRegionAccess> access = new ArrayList<>();
        private final List<DomainEvent> events = new ArrayList<>();
        private final List<DomainEventSummary> eventSummaries = new ArrayList<>();
        private final Map<StoryAudienceId, Long> cooldowns = new LinkedHashMap<>();
        private long schemaVersion = 1;
        private long simulationStep;
        private long eventSequence;

        public Builder schemaVersion(long value) { schemaVersion = value; return this; }
        public Builder simulationStep(long value) { simulationStep = value; return this; }
        public Builder eventSequence(long value) { eventSequence = value; return this; }
        public Builder facility(FacilityState value) { facilities.add(value); return this; }
        public Builder scenario(ScenarioInstance value) { scenarios.add(value); return this; }
        public Builder community(SettlementCommunity value) { communities.add(value); return this; }
        public Builder place(SettlementPlace value) { places.add(value); return this; }
        public Builder binding(CommunityPlaceBinding value) { bindings.add(value); return this; }
        public Builder economy(SettlementEconomy value) { economies.add(value); return this; }
        public Builder security(SettlementSecurity value) { securities.add(value); return this; }
        public Builder policy(SettlementPolicy value) { policies.add(value); return this; }
        public Builder populationGroup(PopulationGroup value) { populationGroups.add(value); return this; }
        public Builder emergencyWindow(SettlementEmergencyWindow value) { emergencyWindows.add(value); return this; }
        public Builder development(SettlementDevelopment value) { developments.add(value); return this; }
        public Builder developmentPolicy(SettlementDevelopmentPolicy value) { developmentPolicies.add(value); return this; }
        public Builder authorityProfile(SettlementAuthorityProfile value) { authorityProfiles.add(value); return this; }
        public Builder developmentIntent(DevelopmentIntent value) { developmentIntents.add(value); return this; }
        public Builder site(WorldSite value) { sites.add(value); return this; }
        public Builder affiliation(SiteAffiliation value) { affiliations.add(value); return this; }
        public Builder capability(SiteCapability value) { capabilities.add(value); return this; }
        public Builder route(RouteContract value) { routes.add(value); return this; }
        public Builder path(WorldPath value) { paths.add(value); return this; }
        public Builder journey(WorldJourney value) { journeys.add(value); return this; }
        public Builder journeySummary(WorldJourneySummary value) { journeySummaries.add(value); return this; }
        public Builder region(LivingRegionState value) { regions.add(value); return this; }
        public Builder knowledge(AudienceRegionKnowledge value) { knowledge.add(value); return this; }
        public Builder access(AudienceRegionAccess value) { access.add(value); return this; }
        public Builder event(DomainEvent value) { events.add(value); return this; }
        public Builder eventSummary(DomainEventSummary value) { eventSummaries.add(value); return this; }
        public Builder cooldown(StoryAudienceId audience, long availableAt) {
            if (cooldowns.putIfAbsent(audience, availableAt) != null) {
                throw new IllegalStateException("Duplicate narrator cooldown " + audience);
            }
            return this;
        }

        public WorldState build() {
            if (schemaVersion < 1 || simulationStep < 0 || eventSequence < 0) {
                throw new IllegalStateException("Invalid persisted world clocks");
            }
            validateUniqueIdentities();
            WorldState state = new WorldState();
            state.setSchemaVersion(schemaVersion);
            state.setSimulationStep(simulationStep);
            state.setEventSequence(eventSequence);
            facilities.forEach(state::putFacility);
            scenarios.forEach(state::putScenario);
            communities.forEach(state::putCommunity);
            places.forEach(state::putPlace);
            bindings.forEach(state::putCommunityPlaceBinding);
            economies.forEach(state::putEconomy);
            securities.forEach(state::putSecurity);
            policies.forEach(state::putSettlementPolicy);
            populationGroups.forEach(state::putPopulationGroup);
            emergencyWindows.forEach(state::putEmergencyWindow);
            developments.forEach(state::putSettlementDevelopment);
            developmentPolicies.forEach(state::putDevelopmentPolicy);
            authorityProfiles.forEach(state::putSettlementAuthorityProfile);
            developmentIntents.forEach(state::putDevelopmentIntent);
            sites.forEach(state::putSite);
            affiliations.forEach(state::putSiteAffiliation);
            capabilities.forEach(state::putSiteCapability);
            routes.forEach(state::putRouteContract);
            paths.forEach(state::putWorldPath);
            journeys.forEach(state::putJourney);
            journeySummaries.forEach(state::putJourneySummary);
            regions.forEach(state::putLivingRegion);
            knowledge.forEach(state::putRegionKnowledge);
            access.forEach(state::putRegionAccess);
            eventSummaries.forEach(state::putEventSummary);
            events.forEach(state::addEvent);
            cooldowns.forEach(state::setNarratorCooldown);
            return state;
        }

        private void validateUniqueIdentities() {
            unique("facility", facilities, value -> value.id().value());
            unique("scenario", scenarios, ScenarioInstance::id);
            unique("community", communities, value -> value.id().value());
            unique("place", places, value -> value.id().value());
            unique("community binding", bindings, value -> value.communityId().value());
            unique("economy", economies, value -> value.communityId().value());
            unique("security", securities, value -> value.communityId().value());
            unique("settlement policy", policies, value -> value.communityId().value());
            unique("population group", populationGroups, PopulationGroup::id);
            unique("emergency window", emergencyWindows, value -> value.communityId().value());
            unique("development", developments, value -> value.communityId().value());
            unique("development policy", developmentPolicies, value -> value.communityId().value());
            unique("authority profile", authorityProfiles, value -> value.communityId().value());
            unique("development intent", developmentIntents, DevelopmentIntent::id);
            unique("site", sites, value -> value.id().value());
            unique("site affiliation", affiliations, value -> value.siteId().value() + "|"
                    + value.objectId().value() + "|" + value.role().name());
            unique("site capability", capabilities, value -> value.siteId().value() + "|"
                    + value.type().name() + "|" + (value.resource() == null ? "" : value.resource().name()));
            unique("route", routes, value -> value.id().value());
            unique("world path", paths, WorldPath::id);
            unique("journey", journeys, WorldJourney::id);
            unique("living region", regions, LivingRegionState::id);
            unique("region knowledge", knowledge, value -> value.audience().value() + "|" + value.regionId());
            unique("region access", access, value -> value.audience().value() + "|" + value.regionId());
            unique("domain event", events, DomainEvent::eventId);
            unique("domain event summary", eventSummaries, value -> value.subject().value() + "|" + value.type().name());
        }

        private static <T> void unique(String label, List<T> values, Function<T, String> identity) {
            var identities = new HashSet<String>();
            for (T value : values) {
                String id = identity.apply(value);
                if (!identities.add(id)) throw new IllegalStateException("Duplicate persisted " + label + " " + id);
            }
        }
    }
}
