package io.farfrontier.palemirror.internal.materialization;

import java.util.Objects;

/** Persisted unit of physical work. Its key is deterministic across a restart. */
public final class MaterializationOperation {
    private final String operationId;
    private final String idempotencyKey;
    private final MaterializationOperationType type;
    private OperationState state;
    private int attemptCount;
    private String lastError;

    public MaterializationOperation(String operationId, String idempotencyKey, MaterializationOperationType type,
                                    OperationState state, int attemptCount, String lastError) {
        this.operationId = Objects.requireNonNull(operationId, "operationId");
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        this.type = Objects.requireNonNull(type, "type");
        this.state = Objects.requireNonNull(state, "state");
        this.attemptCount = attemptCount;
        this.lastError = lastError == null ? "" : lastError;
    }

    public String operationId() { return operationId; }
    public String idempotencyKey() { return idempotencyKey; }
    public MaterializationOperationType type() { return type; }
    public OperationState state() { return state; }
    public int attemptCount() { return attemptCount; }
    public String lastError() { return lastError; }
    public void start() { state = OperationState.RUNNING; attemptCount++; }
    public void complete() { state = OperationState.COMPLETED; lastError = ""; }
    public void block(String error) { state = OperationState.BLOCKED; lastError = Objects.requireNonNull(error, "error"); }
}
