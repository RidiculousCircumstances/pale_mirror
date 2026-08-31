package io.farfrontier.palemirror.frontier.v3.model;

/** Durable COLD/HOT-neutral lifecycle of an identified route operation. */
public enum OperationStage {
    ASSEMBLING(0), EN_ROUTE(1), ARRIVED(2), FAILED(3), INTERRUPTED(4), RETURNING(5), COMPLETED(6);

    private final int wireCode;

    OperationStage(int wireCode) { this.wireCode = wireCode; }

    /**
     * Stable persisted code.  This deliberately is not {@link #ordinal()}: v3
     * snapshots and WAL payloads written before return travel used 3/4 for
     * FAILED/INTERRUPTED, and enum declaration order is not a persistence API.
     */
    public int wireCode() { return wireCode; }

    public static OperationStage fromWireCode(int wireCode) {
        return switch (wireCode) {
            case 0 -> ASSEMBLING;
            case 1 -> EN_ROUTE;
            case 2 -> ARRIVED;
            case 3 -> FAILED;
            case 4 -> INTERRUPTED;
            case 5 -> RETURNING;
            case 6 -> COMPLETED;
            default -> throw new IllegalArgumentException("unknown route operation stage");
        };
    }
}
