package io.farfrontier.palemirror.internal.observation;

import java.util.List;
import java.util.Objects;

import io.farfrontier.palemirror.domain.DomainCommand;
import io.farfrontier.palemirror.domain.DomainCommandExecutor;
import io.farfrontier.palemirror.domain.DomainEvent;
import io.farfrontier.palemirror.internal.world.PaleMirrorSavedData;
import io.farfrontier.palemirror.internal.adapter.AdapterRegistry;

/** The sole bridge which turns observed physical facts into domain commands. */
public final class ObservationReconciler {
    private final DomainCommandExecutor commands;

    public ObservationReconciler(DomainCommandExecutor commands) {
        this.commands = Objects.requireNonNull(commands, "commands");
    }

    public List<DomainEvent> reconcile(PaleMirrorSavedData data, Observation observation) {
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(observation, "observation");
        if (!data.reconciliationLedger().recordIfNew(observation.id())) return List.of();
        List<DomainEvent> events = switch (observation) {
            case PlayerEnteredFacilityBounds entered -> commands.execute(data.worldState(),
                    new DomainCommand.PlayerEnteredFacility(entered.audience(), entered.facilityId()));
            case ThreatControllerDestroyed destroyed -> commands.execute(data.worldState(),
                    new DomainCommand.ThreatControllerDestroyed(destroyed.facilityId(), destroyed.causationId()));
            case MaterializationPostconditionObserved materialized -> commands.execute(data.worldState(),
                    new DomainCommand.MaterializationObserved(materialized.facilityId(), materialized.desiredRevision(),
                            materialized.causationId()));
            case EncounterActorDestroyed destroyed -> {
                var mine = data.testMines().get(destroyed.facilityId());
                boolean sourceMatches = data.worldState().facility(destroyed.facilityId())
                        .map(facility -> facility.infectionSource().equals(destroyed.source())).orElse(false);
                if (mine != null && sourceMatches) mine.encounter().defeated(destroyed.slotId(), destroyed.entityId());
                yield List.of();
            }
            case GatePartDestroyed destroyed -> {
                List<DomainEvent> produced = commands.execute(data.worldState(),
                        new DomainCommand.GatePartDestroyed(destroyed.facilityId(), destroyed.slotId(), destroyed.causationId()));
                if (!produced.isEmpty()) {
                    var mine = data.testMines().get(destroyed.facilityId());
                    var facility = data.worldState().facility(destroyed.facilityId()).orElse(null);
                    var part = mine == null ? null : mine.gate().part(destroyed.slotId()).orElse(null);
                    if (part != null) {
                        mine.gate().defeat(destroyed.slotId());
                        if (facility != null) AdapterRegistry.sourceAdapter(facility.infectionSource())
                                .onGatePartObservedDestroyed(mine, destroyed.slotId());
                    }
                }
                yield produced;
            }
        };
        data.setDirty();
        return events;
    }
}
