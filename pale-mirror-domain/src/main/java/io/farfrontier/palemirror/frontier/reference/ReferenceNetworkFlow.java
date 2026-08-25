package io.farfrontier.palemirror.frontier.reference;

/** One exact, inspectable material transfer from Python's {@code NetworkFlow}. */
public record ReferenceNetworkFlow(
        int day,
        String sourceKind,
        int sourceId,
        double sourceX,
        double sourceY,
        int organId,
        double amount,
        double retained,
        double loss,
        double distance) { }
