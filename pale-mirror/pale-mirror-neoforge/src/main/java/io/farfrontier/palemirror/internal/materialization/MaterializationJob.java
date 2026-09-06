package io.farfrontier.palemirror.internal.materialization;

import java.util.Objects;
import java.util.ArrayList;
import java.util.List;

/** Persisted before physical work starts; its idempotency key survives a process crash. */
public final class MaterializationJob {
    private final String jobId;
    private final String targetId;
    private final String channel;
    private final MaterializationJobClass jobClass;
    private final long desiredRevision;
    private final String policyId;
    private final String policyVersion;
    private JobState state;
    private final List<MaterializationOperation> operations;
    private int nextOperationIndex;
    private int attemptCount;
    private String lastError;

    public MaterializationJob(String jobId, long desiredRevision, String policyId, String policyVersion,
                              JobState state, List<MaterializationOperation> operations, int nextOperationIndex,
                              int attemptCount, String lastError) {
        this(jobId, legacyTarget(jobId), "legacy", MaterializationJobClass.CAPABILITY, desiredRevision, policyId,
                policyVersion, state, operations, nextOperationIndex, attemptCount, lastError);
    }

    public MaterializationJob(String jobId, String targetId, String channel, MaterializationJobClass jobClass,
                              long desiredRevision, String policyId, String policyVersion,
                              JobState state, List<MaterializationOperation> operations, int nextOperationIndex,
                              int attemptCount, String lastError) {
        this.jobId = required(jobId, "jobId");
        this.targetId = required(targetId, "targetId");
        this.channel = required(channel, "channel");
        this.jobClass = Objects.requireNonNull(jobClass, "jobClass");
        this.desiredRevision = desiredRevision;
        this.policyId = Objects.requireNonNull(policyId, "policyId");
        this.policyVersion = Objects.requireNonNull(policyVersion, "policyVersion");
        this.state = Objects.requireNonNull(state, "state");
        this.operations = new ArrayList<>(Objects.requireNonNull(operations, "operations"));
        this.nextOperationIndex = nextOperationIndex;
        this.attemptCount = attemptCount;
        this.lastError = lastError == null ? "" : lastError;
    }

    public String jobId() { return jobId; }
    public String targetId() { return targetId; }
    public String channel() { return channel; }
    public MaterializationJobClass jobClass() { return jobClass; }
    public long desiredRevision() { return desiredRevision; }
    public String policyId() { return policyId; }
    public String policyVersion() { return policyVersion; }
    public JobState state() { return state; }
    public List<MaterializationOperation> operations() { return List.copyOf(operations); }
    public int nextOperationIndex() { return nextOperationIndex; }
    public int attemptCount() { return attemptCount; }
    public String lastError() { return lastError; }
    public void start() { state = JobState.RUNNING; attemptCount++; }
    public void complete() { state = JobState.COMPLETED; lastError = ""; }
    public void block(String error) { state = JobState.BLOCKED; lastError = error; }
    public void cancel(String reason) { state = JobState.CANCELLED; lastError = reason == null ? "" : reason; }
    public boolean isFor(long revision, String expectedPolicyId, String expectedPolicyVersion) {
        return desiredRevision == revision && state != JobState.CANCELLED
                && policyId.equals(expectedPolicyId) && policyVersion.equals(expectedPolicyVersion);
    }
    public boolean matchesOperations(List<MaterializationOperation> expected) {
        if (operations.size() != expected.size()) return false;
        for (int index = 0; index < operations.size(); index++) {
            MaterializationOperation actual = operations.get(index);
            MaterializationOperation desired = expected.get(index);
            if (actual.type() != desired.type() || !actual.target().equals(desired.target())
                    || !actual.idempotencyKey().equals(desired.idempotencyKey())) return false;
        }
        return true;
    }
    public MaterializationOperation nextOperation() {
        return nextOperationIndex < operations.size() ? operations.get(nextOperationIndex) : null;
    }
    public void advanceOperation() { nextOperationIndex++; }

    private static String legacyTarget(String jobId) {
        String prefix = "pm:job:";
        if (jobId != null && jobId.startsWith(prefix)) {
            int revision = jobId.lastIndexOf(':');
            return revision > prefix.length() ? jobId.substring(prefix.length(), revision) : jobId;
        }
        return jobId == null ? "unknown" : jobId;
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value;
    }
}
