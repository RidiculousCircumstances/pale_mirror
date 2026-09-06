package io.farfrontier.palemirror.frontier;

import java.util.Objects;
import java.util.Locale;

/** One bounded in-transit physical shipment. Delivered cargo is compacted into the event history. */
public final class FrontierCargo {
    private final String id;
    private final String routeId;
    private final String sourceSettlementId;
    private final String destinationSettlementId;
    private final FrontierResource resource;
    private final long amount;
    private final long creditValue;
    private final long dispatchedDay;
    private final long revision;

    FrontierCargo(String id, String routeId, String sourceSettlementId, String destinationSettlementId,
                  FrontierResource resource, long amount, long creditValue, long dispatchedDay, long revision) {
        this.id = required(id, "id");
        this.routeId = required(routeId, "routeId");
        this.sourceSettlementId = required(sourceSettlementId, "sourceSettlementId");
        this.destinationSettlementId = required(destinationSettlementId, "destinationSettlementId");
        if (sourceSettlementId.equals(destinationSettlementId)) throw new IllegalArgumentException("cargo endpoints must differ");
        this.resource = Objects.requireNonNull(resource, "resource");
        if (amount < 1 || creditValue < 1 || dispatchedDay < 1 || revision < 0) throw new IllegalArgumentException("invalid cargo state");
        this.amount = amount;
        this.creditValue = creditValue;
        this.dispatchedDay = dispatchedDay;
        this.revision = revision;
    }
    FrontierCargo(String id, String routeId, String sourceSettlementId, String destinationSettlementId,
                  FrontierResource resource, long amount, long dispatchedDay, long revision) {
        this(id, routeId, sourceSettlementId, destinationSettlementId, resource, amount, amount, dispatchedDay, revision);
    }

    public String id() { return id; }
    public String routeId() { return routeId; }
    public String sourceSettlementId() { return sourceSettlementId; }
    public String destinationSettlementId() { return destinationSettlementId; }
    public FrontierResource resource() { return resource; }
    public long amount() { return amount; }
    public long creditValue() { return creditValue; }
    public long dispatchedDay() { return dispatchedDay; }
    public long revision() { return revision; }
    public String materializationId() { return "frontier:cargo:" + id; }

    static String idFor(String routeId, long dispatchedDay) {
        return "frontier:cargo:" + routeId.substring(routeId.lastIndexOf(':') + 1) + ":day:"
                + String.format(Locale.ROOT, "%08d", dispatchedDay);
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value;
    }
}
