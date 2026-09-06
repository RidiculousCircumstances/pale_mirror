package io.farfrontier.palemirror.domain;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Referential and aggregate validation run after hydration and before save. */
public final class DomainStateValidator {
    private DomainStateValidator() { }

    public static void validate(WorldState state, long expectedSchema) {
        List<String> errors = new ArrayList<>();
        if (state.schemaVersion() != expectedSchema) {
            errors.add("schema expected=" + expectedSchema + " actual=" + state.schemaVersion());
        }
        Set<WorldObjectId> communities = ids(state.communities().stream().map(SettlementCommunity::id).toList());
        Set<WorldObjectId> places = ids(state.places().stream().map(SettlementPlace::id).toList());
        Set<WorldObjectId> facilities = ids(state.facilities().stream().map(FacilityState::id).toList());
        Set<WorldObjectId> sites = ids(state.sites().stream().map(WorldSite::id).toList());
        Set<WorldObjectId> routes = ids(state.routeContracts().stream().map(RouteContract::id).toList());
        Set<String> regions = new HashSet<>();
        state.livingRegions().forEach(region -> {
            if (!regions.add(region.id())) errors.add("duplicate region " + region.id());
            require(errors, communities, region.communityId(), "region " + region.id() + " community");
            require(errors, places, region.placeId(), "region " + region.id() + " place");
            require(errors, facilities, region.primaryFacilityId(), "region " + region.id() + " primary facility");
            require(errors, facilities, region.alternateFacilityId(), "region " + region.id() + " alternate facility");
            require(errors, routes, region.primaryRouteId(), "region " + region.id() + " primary route");
            require(errors, routes, region.alternateRouteId(), "region " + region.id() + " alternate route");
        });
        state.communityPlaceBindings().forEach(binding -> {
            require(errors, communities, binding.communityId(), "binding community");
            require(errors, places, binding.placeId(), "binding place");
        });
        state.economies().forEach(value -> require(errors, communities, value.communityId(), "economy community"));
        state.securities().forEach(value -> require(errors, communities, value.communityId(), "security community"));
        state.settlementPolicies().forEach(value -> require(errors, communities, value.communityId(), "policy community"));
        state.emergencyWindows().forEach(value -> require(errors, communities, value.communityId(), "emergency community"));
        state.settlementDevelopments().forEach(value -> require(errors, communities, value.communityId(), "development community"));
        state.developmentPolicies().forEach(value -> require(errors, communities, value.communityId(), "development policy community"));
        state.settlementAuthorityProfiles().forEach(value -> require(errors, communities, value.communityId(), "authority community"));
        communities.forEach(id -> {
            required(errors, state.communityPlaceBinding(id).isPresent(), "community " + id + " binding");
            required(errors, state.economy(id).isPresent(), "community " + id + " economy");
            required(errors, state.security(id).isPresent(), "community " + id + " security");
            required(errors, state.settlementPolicy(id).isPresent(), "community " + id + " policy");
            required(errors, state.settlementDevelopment(id).isPresent(), "community " + id + " development");
            required(errors, state.developmentPolicy(id).isPresent(), "community " + id + " development policy");
            required(errors, state.settlementAuthorityProfile(id).isPresent(), "community " + id + " authority profile");
        });
        state.populationGroups().forEach(group -> {
            require(errors, communities, group.communityId(), "population group " + group.id() + " community");
            require(errors, places, group.originPlaceId(), "population group " + group.id() + " origin place");
            if (group.currentPlaceId() != null) {
                require(errors, places, group.currentPlaceId(), "population group " + group.id() + " current place");
            }
            if (group.hostSiteId() != null) {
                require(errors, sites, group.hostSiteId(), "population group " + group.id() + " host site");
            }
            if (group.journeyId() != null) {
                required(errors, state.journey(group.journeyId()).isPresent(),
                        "population group " + group.id() + " journey missing " + group.journeyId());
            }
        });
        state.siteAffiliations().forEach(affiliation -> {
            require(errors, sites, affiliation.siteId(), "site affiliation site");
            boolean knownObject = communities.contains(affiliation.objectId()) || places.contains(affiliation.objectId())
                    || facilities.contains(affiliation.objectId()) || routes.contains(affiliation.objectId());
            required(errors, knownObject, "site affiliation object " + affiliation.objectId());
        });
        state.siteCapabilities().forEach(capability -> require(errors, sites, capability.siteId(), "site capability site"));
        state.routeContracts().forEach(route -> {
            require(errors, sites, route.originEndpoint(), "route " + route.id() + " origin");
            require(errors, sites, route.destinationEndpoint(), "route " + route.id() + " destination");
        });
        state.worldPaths().forEach(path -> {
            require(errors, sites, path.originSiteId(), "path " + path.id() + " origin");
            require(errors, sites, path.destinationSiteId(), "path " + path.id() + " destination");
        });
        state.journeys().forEach(journey -> {
            required(errors, state.populationGroup(journey.subjectGroupId()).isPresent(),
                    "journey " + journey.id() + " population group");
            require(errors, sites, journey.originSiteId(), "journey " + journey.id() + " origin");
            require(errors, sites, journey.destinationSiteId(), "journey " + journey.id() + " destination");
            required(errors, state.worldPath(journey.pathId()).isPresent(), "journey " + journey.id() + " path");
            state.worldPath(journey.pathId()).ifPresent(path -> {
                required(errors, path.originSiteId().equals(journey.originSiteId()),
                        "journey " + journey.id() + " origin differs from path");
                required(errors, path.destinationSiteId().equals(journey.destinationSiteId()),
                        "journey " + journey.id() + " destination differs from path");
            });
        });
        state.regionKnowledge().forEach(knowledge -> {
            required(errors, regions.contains(knowledge.regionId()), "knowledge region " + knowledge.regionId());
            required(errors, knowledge.supplyChainDiscoveredAtStep() < 0 || knowledge.introductorySupplyChainKnown(),
                    "knowledge supply-chain timestamp without complete introduction " + knowledge.regionId()
                            + " audience=" + knowledge.audience());
        });
        state.regionAccess().forEach(access -> {
            required(errors, regions.contains(access.regionId()), "access region " + access.regionId());
            required(errors, state.regionKnowledge(access.audience(), access.regionId())
                            .filter(value -> value.knows(KnownRegionalFeature.SETTLEMENT)).isPresent(),
                    "access without settlement knowledge " + access.regionId() + " audience=" + access.audience());
        });
        state.developmentIntents().forEach(intent -> {
            require(errors, communities, intent.communityId(), "development intent " + intent.id() + " community");
            if (intent.targetSiteId() != null) {
                required(errors, sites.contains(intent.targetSiteId()) || places.contains(intent.targetSiteId()),
                        "development intent " + intent.id() + " target missing " + intent.targetSiteId());
            }
        });
        state.scenarios().forEach(scenario -> required(errors,
                facilities.contains(scenario.target()) || communities.contains(scenario.target()),
                "scenario " + scenario.id() + " target " + scenario.target()));
        long maxSequence = state.history().stream().map(DomainEvent::eventId).map(DomainStateValidator::sequence)
                .max(Long::compareTo).orElse(0L);
        required(errors, state.eventSequence() >= maxSequence,
                "event sequence " + state.eventSequence() + " below history maximum " + maxSequence);
        if (!errors.isEmpty()) throw new DomainStateValidationException(errors);
    }

    private static Set<WorldObjectId> ids(List<WorldObjectId> values) {
        return new HashSet<>(values);
    }

    private static void require(List<String> errors, Set<WorldObjectId> values, WorldObjectId id, String label) {
        required(errors, values.contains(id), label + " missing " + id);
    }

    private static void required(List<String> errors, boolean condition, String message) {
        if (!condition) errors.add(message);
    }

    private static long sequence(String eventId) {
        if (!eventId.startsWith("pm:event:")) return 0L;
        try {
            return Long.parseLong(eventId.substring("pm:event:".length()));
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }
}
