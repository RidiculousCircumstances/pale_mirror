package io.farfrontier.palemirror.domain;

import java.util.List;

/** Applies only confirmed deaths of stable PM-managed physical resident identities. */
final class SettlementCasualtyRuntime {
    private SettlementCasualtyRuntime() { }

    static List<DomainEvent> confirm(WorldState state, DomainCommand.ConfirmSettlementResidentDeath command,
                                     DomainEventFactory events) {
        SettlementAuthorityProfile profile = state.settlementAuthorityProfile(command.communityId())
                .orElseThrow(() -> new IllegalStateException("Settlement casualty has no authority profile"));
        if (profile.authority(SettlementAuthorityField.MACRO_POPULATION) != FieldAuthority.PM_OWNED) return List.of();
        PopulationGroup group = state.populationGroup(command.populationGroupId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown population group " + command.populationGroupId()));
        if (!group.communityId().equals(command.communityId()) || group.disposition() != PopulationDisposition.RESIDENT
                || !group.lose(command.cohort(), 1)) return List.of();
        state.communityPlaceBinding(command.communityId()).flatMap(binding -> state.place(binding.placeId()))
                .ifPresent(place -> place.setOccupancy(group.size() == 0 ? OccupancyState.EMPTY : OccupancyState.INHABITED));
        DomainEvent event = events.create(state, DomainEventType.SETTLEMENT_RESIDENT_DEATH_CONFIRMED,
                command.communityId(), command.observationId() + ":" + command.residentId());
        state.addEvent(event);
        return List.of(event);
    }
}
