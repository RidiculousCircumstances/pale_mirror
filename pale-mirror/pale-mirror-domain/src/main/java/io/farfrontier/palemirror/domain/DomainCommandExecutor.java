package io.farfrontier.palemirror.domain;

import java.util.List;

/** Single mutation port for canonical world state. */
@FunctionalInterface
public interface DomainCommandExecutor {
    List<DomainEvent> execute(WorldState state, DomainCommand command);
}
