package io.farfrontier.palemirror.frontier.v3.api;

/** Idempotency identity of a submitted command. */
public record CommandId(String value) implements Comparable<CommandId> {
    public CommandId {
        value = Identifier.require(value, "command id");
    }

    @Override
    public int compareTo(CommandId other) {
        return value.compareTo(other.value);
    }
}
