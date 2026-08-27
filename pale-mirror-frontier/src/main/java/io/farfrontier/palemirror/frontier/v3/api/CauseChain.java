package io.farfrontier.palemirror.frontier.v3.api;

import java.util.List;
import java.util.Objects;

/** Bounded immutable chain of command identities that caused an accepted fact. */
public record CauseChain(List<CommandId> commands) {
    private static final int MAX_DEPTH = 32;

    public CauseChain {
        commands = List.copyOf(commands);
        if (commands.isEmpty() || commands.size() > MAX_DEPTH) {
            throw new IllegalArgumentException("cause chain must contain 1-" + MAX_DEPTH + " commands");
        }
        commands.forEach(command -> Objects.requireNonNull(command, "cause command"));
    }

    public static CauseChain root(CommandId command) {
        return new CauseChain(List.of(command));
    }
}
