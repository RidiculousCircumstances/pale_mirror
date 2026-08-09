package io.farfrontier.palemirror.internal.observation;

import java.util.List;
import java.util.Objects;

import io.farfrontier.palemirror.domain.DomainCommand;
import io.farfrontier.palemirror.domain.DomainCommandProcessor;
import io.farfrontier.palemirror.domain.DomainEvent;
import io.farfrontier.palemirror.internal.world.PaleMirrorSavedData;

/** The sole bridge which turns observed physical facts into domain commands. */
public final class ObservationReconciler {
    private final DomainCommandProcessor commands;

    public ObservationReconciler(DomainCommandProcessor commands) {
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
            case CrimsonEncounterActorDestroyed destroyed -> {
                var mine = data.testMines().get(destroyed.facilityId());
                if (mine != null) mine.encounter().defeated(destroyed.slotId(), destroyed.entityId());
                yield List.of();
            }
        };
        data.setDirty();
        return events;
    }
}
