package io.farfrontier.palemirror.internal.domain;

import java.util.List;
import java.util.Objects;

import io.farfrontier.palemirror.domain.DomainCommand;
import io.farfrontier.palemirror.domain.DomainCommandExecutor;
import io.farfrontier.palemirror.domain.DomainCommandProcessor;
import io.farfrontier.palemirror.domain.DomainEvent;
import io.farfrontier.palemirror.domain.WorldState;
import io.farfrontier.palemirror.internal.world.PaleMirrorSavedData;

/**
 * NeoForge transaction boundary. Every production domain mutation crosses this
 * object so eventless clock changes are persisted and emitted events are
 * returned exactly once to the caller-owned dispatcher.
 */
public final class DomainTransaction implements DomainCommandExecutor {
    private final PaleMirrorSavedData data;
    private final DomainCommandProcessor processor;

    public DomainTransaction(PaleMirrorSavedData data, DomainCommandProcessor processor) {
        this.data = Objects.requireNonNull(data, "data");
        this.processor = Objects.requireNonNull(processor, "processor");
    }

    @Override
    public List<DomainEvent> execute(WorldState state, DomainCommand command) {
        if (state != data.worldState()) {
            throw new IllegalArgumentException("Domain transaction received a foreign WorldState");
        }
        var outcome = processor.executeOutcome(state, command);
        if (outcome.changed()) {
            data.setDirty();
        }
        return outcome.events();
    }
}
