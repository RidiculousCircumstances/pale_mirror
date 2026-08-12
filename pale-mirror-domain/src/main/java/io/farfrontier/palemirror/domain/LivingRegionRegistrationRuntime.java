package io.farfrontier.palemirror.domain;

import java.util.List;

/** Validates and atomically installs one source-neutral causal-region aggregate. */
final class LivingRegionRegistrationRuntime {
    List<DomainEvent> register(WorldState state, DomainCommand.RegisterLivingRegion command) {
        if (state.livingRegion(command.region().id()).isPresent()) return List.of();
        validateActorIdentities(command);
        java.util.Set<WorldObjectId> siteIds = command.sites().stream().map(WorldSite::id)
                .collect(java.util.stream.Collectors.toSet());
        if (siteIds.size() != command.sites().size()
                || command.affiliations().stream().anyMatch(value -> !siteIds.contains(value.siteId()))
                || command.capabilities().stream().anyMatch(value -> !siteIds.contains(value.siteId()))
                || command.routeContracts().stream().anyMatch(value -> !siteIds.contains(value.originEndpoint())
                || !siteIds.contains(value.destinationEndpoint()))) {
            throw new IllegalArgumentException("Living region site graph contains duplicate or unknown endpoints");
        }
        if (command.affiliations().stream().anyMatch(value -> value.role() == SiteAffiliationRole.SUPPLIER
                && command.facilities().stream().noneMatch(facility -> facility.id().equals(value.objectId())))
                || command.affiliations().stream().anyMatch(value -> value.role() == SiteAffiliationRole.RECIPIENT
                && !value.objectId().equals(command.community().id()))) {
            throw new IllegalArgumentException("Living region site affiliations do not match canonical owners");
        }
        rejectDuplicates(state, command);
        command.facilities().forEach(state::putFacility);
        state.putCommunity(command.community()); state.putPlace(command.place());
        state.putCommunityPlaceBinding(command.binding()); state.putEconomy(command.economy());
        state.putSecurity(command.security()); state.putSettlementPolicy(command.policy());
        command.populationGroups().forEach(state::putPopulationGroup); command.sites().forEach(state::putSite);
        command.affiliations().forEach(state::putSiteAffiliation); command.capabilities().forEach(state::putSiteCapability);
        command.routeContracts().forEach(state::putRouteContract); command.worldPaths().forEach(state::putWorldPath);
        state.putLivingRegion(command.region());
        return List.of();
    }

    private static void validateActorIdentities(DomainCommand.RegisterLivingRegion command) {
        if (!command.community().id().equals(command.region().communityId())
                || !command.place().id().equals(command.region().placeId())
                || !command.binding().communityId().equals(command.community().id())
                || !command.binding().placeId().equals(command.place().id())
                || !command.economy().communityId().equals(command.community().id())
                || !command.security().communityId().equals(command.community().id())
                || !command.policy().communityId().equals(command.community().id())) {
            throw new IllegalArgumentException("Living region actor identities do not match its definition");
        }
        if (command.facilities().stream().noneMatch(value -> value.id().equals(command.region().primaryFacilityId()))
                || command.facilities().stream().noneMatch(value -> value.id().equals(command.region().alternateFacilityId()))) {
            throw new IllegalArgumentException("Living region facilities do not match its definition");
        }
        if (command.routeContracts().stream().noneMatch(value -> value.id().equals(command.region().primaryRouteId()))
                || command.routeContracts().stream().noneMatch(value -> value.id().equals(command.region().alternateRouteId()))) {
            throw new IllegalArgumentException("Living region contracts do not match its definition");
        }
        if (command.populationGroups().isEmpty() || command.populationGroups().stream()
                .anyMatch(group -> !group.communityId().equals(command.community().id())
                        || !group.originPlaceId().equals(command.place().id()))) {
            throw new IllegalArgumentException("Living region population groups do not match its community/place");
        }
    }

    private static void rejectDuplicates(WorldState state, DomainCommand.RegisterLivingRegion command) {
        command.facilities().forEach(value -> { if (state.facility(value.id()).isPresent()) throw duplicate("facility", value.id()); });
        command.routeContracts().forEach(value -> { if (state.routeContract(value.id()).isPresent()) throw duplicate("route", value.id()); });
        command.worldPaths().forEach(value -> { if (state.worldPath(value.id()).isPresent()) throw duplicate("path", value.id()); });
        command.sites().forEach(value -> { if (state.site(value.id()).isPresent()) throw duplicate("site", value.id()); });
        if (state.community(command.community().id()).isPresent() || state.place(command.place().id()).isPresent()) {
            throw new IllegalStateException("Duplicate settlement actor identity");
        }
    }
    private static IllegalStateException duplicate(String kind, Object id) {
        return new IllegalStateException("Duplicate " + kind + " " + id);
    }
}
