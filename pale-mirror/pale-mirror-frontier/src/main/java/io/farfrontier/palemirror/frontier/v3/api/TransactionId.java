package io.farfrontier.palemirror.frontier.v3.api;

/** Stable identity of one all-or-nothing canonical transition. */
public record TransactionId(String value) implements Comparable<TransactionId> {
    public TransactionId {
        value = Identifier.require(value, "transaction id");
    }

    @Override
    public int compareTo(TransactionId other) {
        return value.compareTo(other.value);
    }
}
