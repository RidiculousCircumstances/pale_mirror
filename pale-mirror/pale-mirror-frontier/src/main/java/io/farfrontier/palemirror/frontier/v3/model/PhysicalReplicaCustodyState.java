package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The sole canonical registry of emitted replica evidence and current physical custody.
 * It deliberately stores no stock, reservation, process progress, demand, or scene membership.
 */
public record PhysicalReplicaCustodyState(Map<SubjectId, PhysicalReplicaRecord> replicas,
                                          Map<SubjectId, PhysicalCustodyLease> custodyByScope,
                                          Map<SubjectId, DiagnosticTuple> diagnostics) {
    private static final int MAX_RECORDS = 4_096;

    public PhysicalReplicaCustodyState {
        replicas = Map.copyOf(Objects.requireNonNull(replicas, "replicas"));
        custodyByScope = Map.copyOf(Objects.requireNonNull(custodyByScope, "custody by scope"));
        diagnostics = Map.copyOf(Objects.requireNonNull(diagnostics, "replica custody diagnostics"));
        if (replicas.size() > MAX_RECORDS || custodyByScope.size() > MAX_RECORDS || diagnostics.size() > MAX_RECORDS) throw new IllegalArgumentException("replica custody retention limit exceeded");
        for (Map.Entry<SubjectId, PhysicalReplicaRecord> entry : replicas.entrySet()) {
            if (!entry.getKey().equals(entry.getValue().objectId())) throw new IllegalArgumentException("replica index must use its exact object identity");
        }
        for (Map.Entry<SubjectId, PhysicalCustodyLease> entry : custodyByScope.entrySet()) {
            if (!entry.getKey().equals(entry.getValue().scopeId()) || !replicas.containsKey(entry.getValue().objectId())) {
                throw new IllegalArgumentException("custody must retain one indexed known replica scope");
            }
            PhysicalReplicaRecord replica = replicas.get(entry.getValue().objectId());
            PhysicalReplicaState required = entry.getValue().status() == PhysicalCustodyLeaseStatus.PREPARING
                    ? PhysicalReplicaState.EXPECTED : PhysicalReplicaState.OBSERVED_CURRENT;
            if (entry.getValue().status() == PhysicalCustodyLeaseStatus.UNRESOLVED && replica.state() == PhysicalReplicaState.CONFLICT) {
                required = PhysicalReplicaState.CONFLICT;
            }
            if (entry.getValue().live() && (replica.state() != required
                    || entry.getValue().expectedCanonicalRevision() != replica.observedCanonicalRevision()
                    || entry.getValue().expectedReplicaRevision() != replica.replicaRevision())) {
                throw new IllegalArgumentException("live custody must exactly fence current replica evidence");
            }
        }
        for (PhysicalCustodyLease left : custodyByScope.values()) for (PhysicalCustodyLease right : custodyByScope.values()) {
            if (left == right || !left.live() || !right.live()) continue;
            if (left.scopeId().equals(right.scopeId()) || left.objectId().equals(right.objectId())) {
                throw new IllegalArgumentException("live custody scopes overlap");
            }
        }
    }

    public PhysicalReplicaCustodyState(Map<SubjectId, PhysicalReplicaRecord> replicas, Map<SubjectId, PhysicalCustodyLease> custodyByScope) {
        this(replicas, custodyByScope, Map.of());
    }

    public static PhysicalReplicaCustodyState empty() { return new PhysicalReplicaCustodyState(Map.of(), Map.of()); }
    public PhysicalReplicaCustodyState declare(PhysicalReplicaRecord replica) {
        Objects.requireNonNull(replica, "replica");
        if (replica.state() != PhysicalReplicaState.EXPECTED || replica.replicaRevision() != 1L
                || replica.observedCanonicalRevision() != replica.emittedCanonicalRevision() || replica.conflictReason().isPresent()) {
            throw new IllegalArgumentException("replica declaration must start as unobserved expected evidence");
        }
        if (replicas.containsKey(replica.objectId())) throw new IllegalArgumentException("replica identity already exists");
        Map<SubjectId, PhysicalReplicaRecord> next = new LinkedHashMap<>(replicas); next.put(replica.objectId(), replica);
        return new PhysicalReplicaCustodyState(next, custodyByScope, diagnostics);
    }
    public PhysicalReplicaCustodyState emit(SubjectId objectId, long expectedCanonicalRevision, long expectedReplicaRevision,
                                            long emittedCanonicalRevision, String fingerprint, String provenance) {
        PhysicalReplicaRecord current = requireReplica(objectId);
        if (current.state() != PhysicalReplicaState.OBSERVED_CURRENT || current.emittedCanonicalRevision() != expectedCanonicalRevision
                || current.replicaRevision() != expectedReplicaRevision
                || custodyByScope.values().stream().anyMatch(lease -> lease.live() && lease.objectId().equals(objectId))) {
            throw new IllegalArgumentException("replica emission fence is stale or custody is live");
        }
        Map<SubjectId, PhysicalReplicaRecord> next = new LinkedHashMap<>(replicas);
        next.put(objectId, current.reemit(emittedCanonicalRevision, fingerprint, provenance));
        return new PhysicalReplicaCustodyState(next, custodyByScope, withoutDiagnostic(objectId));
    }
    public PhysicalReplicaCustodyState observe(SubjectId objectId, long expectedCanonicalRevision, long expectedReplicaRevision,
                                               String fingerprint, String provenance, long observedRevision) {
        PhysicalReplicaRecord current = requireReplica(objectId);
        if (current.state() != PhysicalReplicaState.EXPECTED || current.replicaRevision() != expectedReplicaRevision || current.emittedCanonicalRevision() != expectedCanonicalRevision
                || custodyByScope.values().stream().anyMatch(lease -> lease.live() && lease.objectId().equals(objectId))) {
            throw new IllegalArgumentException("replica observation revision is stale or custody is live");
        }
        Map<SubjectId, PhysicalReplicaRecord> next = new LinkedHashMap<>(replicas);
        next.put(objectId, current.observe(expectedCanonicalRevision, fingerprint, provenance, observedRevision));
        return new PhysicalReplicaCustodyState(next, custodyByScope, withoutDiagnostic(objectId));
    }
    public PhysicalReplicaCustodyState conflict(SubjectId objectId, long expectedCanonicalRevision, long expectedReplicaRevision,
                                                String fingerprint, String provenance, DiagnosticTuple diagnostic) {
        PhysicalReplicaRecord current = requireReplica(objectId);
        if (current.state() != PhysicalReplicaState.OBSERVED_CURRENT || current.emittedCanonicalRevision() != expectedCanonicalRevision
                || current.replicaRevision() != expectedReplicaRevision
                || custodyByScope.values().stream().anyMatch(lease -> lease.live() && lease.objectId().equals(objectId))) {
            throw new IllegalArgumentException("released replica conflict fence is stale or custody is live");
        }
        requireConflictDiagnostic(objectId, diagnostic);
        Map<SubjectId, PhysicalReplicaRecord> next = new LinkedHashMap<>(replicas); Map<SubjectId, DiagnosticTuple> causes = new LinkedHashMap<>(diagnostics); causes.put(objectId, diagnostic);
        next.put(objectId, current.conflict(expectedCanonicalRevision, expectedReplicaRevision, fingerprint, provenance));
        return new PhysicalReplicaCustodyState(next, custodyByScope, causes);
    }
    public PhysicalReplicaCustodyState acquire(PhysicalCustodyLease requested) {
        Objects.requireNonNull(requested, "custody lease");
        if (requested.status() != PhysicalCustodyLeaseStatus.ACQUIRED || requested.unresolvedReason() != null) throw new IllegalArgumentException("custody must start acquired");
        PhysicalReplicaRecord replica = requireReplica(requested.objectId());
        if (replica.state() != PhysicalReplicaState.OBSERVED_CURRENT || replica.observedCanonicalRevision() != requested.expectedCanonicalRevision()
                || replica.replicaRevision() != requested.expectedReplicaRevision()) {
            throw new IllegalArgumentException("custody requires the exact current replica evidence");
        }
        PhysicalCustodyLease prior = custodyByScope.get(requested.scopeId());
        if (prior != null && (prior.live() || requested.authorityEpoch() <= prior.authorityEpoch())) throw new IllegalArgumentException("custody scope is already live or reuses its epoch");
        if (custodyByScope.values().stream().anyMatch(lease -> lease.objectId().equals(requested.objectId())
                && requested.authorityEpoch() <= lease.authorityEpoch())) throw new IllegalArgumentException("custody object reuses its epoch");
        if (custodyByScope.values().stream().anyMatch(lease -> lease.live() && lease.objectId().equals(requested.objectId()))) throw new IllegalArgumentException("custody scope overlaps a live object scope");
        Map<SubjectId, PhysicalCustodyLease> next = new LinkedHashMap<>(custodyByScope); next.put(requested.scopeId(), requested);
        return new PhysicalReplicaCustodyState(replicas, next, diagnostics);
    }

    /** Fences an already-declared exact expected write without pretending it was observed. */
    public PhysicalReplicaCustodyState prepareProjection(PhysicalCustodyLease requested) {
        Objects.requireNonNull(requested, "projection custody lease");
        PhysicalReplicaRecord replica = requireReplica(requested.objectId());
        if (requested.status() != PhysicalCustodyLeaseStatus.PREPARING || replica.state() != PhysicalReplicaState.EXPECTED
                || replica.emittedCanonicalRevision() != requested.expectedCanonicalRevision()
                || replica.replicaRevision() != requested.expectedReplicaRevision()) {
            throw new IllegalArgumentException("projection custody requires its exact unobserved expected write");
        }
        PhysicalCustodyLease prior = custodyByScope.get(requested.scopeId());
        if (prior != null && (prior.live() || requested.authorityEpoch() <= prior.authorityEpoch())) {
            throw new IllegalArgumentException("projection custody scope is live or reuses its epoch");
        }
        if (custodyByScope.values().stream().anyMatch(lease -> lease.objectId().equals(requested.objectId())
                && (lease.live() || requested.authorityEpoch() <= lease.authorityEpoch()))) {
            throw new IllegalArgumentException("projection custody overlaps an object or reuses its epoch");
        }
        Map<SubjectId, PhysicalCustodyLease> next = new LinkedHashMap<>(custodyByScope);
        next.put(requested.scopeId(), requested);
        return new PhysicalReplicaCustodyState(replicas, next, diagnostics);
    }

    /** Confirm only matching actual evidence; a mismatch retains the fence for explicit recovery. */
    public PhysicalReplicaCustodyState supersedeProjection(SubjectId scopeId, long epoch, long canonicalRevision,
            long replicaRevision, long successorRevision, String fingerprint, String provenance) {
        var lease = requireLive(scopeId, epoch); var replica = requireReplica(lease.objectId());
        if (lease.status() != PhysicalCustodyLeaseStatus.PREPARING || replica.state() != PhysicalReplicaState.EXPECTED
                || canonicalRevision != lease.expectedCanonicalRevision() || replicaRevision != lease.expectedReplicaRevision()
                || successorRevision <= canonicalRevision || fingerprint.isBlank() || provenance.isBlank())
            throw new IllegalArgumentException("projection supersession lacks its exact preparing fence and successor");
        // This changes the owner's expected projection. It does NOT assert observation,
        // release authority, or certify a partially written old projection as complete.
        var nextReplica = new PhysicalReplicaRecord(replica.objectId(), replica.semanticKind(), successorRevision,
                successorRevision, Math.addExact(replica.replicaRevision(), 1), fingerprint, provenance,
                PhysicalReplicaState.EXPECTED, java.util.Optional.empty(), java.util.Optional.empty(), java.util.Optional.empty());
        var nextReplicas = new LinkedHashMap<>(replicas); nextReplicas.put(replica.objectId(), nextReplica);
        var nextLeases = new LinkedHashMap<>(custodyByScope);
        nextLeases.put(scopeId, new PhysicalCustodyLease(scopeId, lease.objectId(), lease.providerId(), Math.addExact(epoch, 1),
                successorRevision, nextReplica.replicaRevision(), PhysicalCustodyLeaseStatus.PREPARING, null));
        return new PhysicalReplicaCustodyState(nextReplicas, nextLeases, withoutDiagnostic(scopeId));
    }

    /** Confirm only matching actual evidence; a mismatch retains the fence for explicit recovery. */
    public PhysicalReplicaCustodyState confirmProjection(SubjectId scopeId, long epoch, long canonicalRevision,
                                                         long replicaRevision, String fingerprint, String provenance) {
        PhysicalCustodyLease lease = requireLive(scopeId, epoch);
        PhysicalReplicaRecord replica = requireReplica(lease.objectId());
        boolean recoveringConflict = lease.status() == PhysicalCustodyLeaseStatus.UNRESOLVED
                && lease.unresolvedReason() == PhysicalCustodyUnresolvedReason.OBSERVATION_MISMATCH
                && replica.state() == PhysicalReplicaState.CONFLICT;
        if ((lease.status() != PhysicalCustodyLeaseStatus.PREPARING && !recoveringConflict)
                || canonicalRevision != lease.expectedCanonicalRevision() || replicaRevision != lease.expectedReplicaRevision()) {
            throw new IllegalArgumentException("projection confirmation fence is stale or not preparing");
        }
        if (recoveringConflict) requireUnresolvedDiagnostic(scopeId, diagnostics.get(scopeId));
        PhysicalReplicaRecord observed = recoveringConflict
                ? replica.confirmProjectionRecovery(canonicalRevision, replicaRevision, fingerprint, provenance)
                : replica.observe(canonicalRevision, fingerprint, provenance, canonicalRevision);
        if (observed.state() != PhysicalReplicaState.OBSERVED_CURRENT) {
            throw new IllegalArgumentException("projection confirmation does not match its expected physical result");
        }
        Map<SubjectId, PhysicalReplicaRecord> nextReplicas = new LinkedHashMap<>(replicas);
        nextReplicas.put(replica.objectId(), observed);
        Map<SubjectId, PhysicalCustodyLease> nextLeases = new LinkedHashMap<>(custodyByScope);
        nextLeases.put(scopeId, new PhysicalCustodyLease(scopeId, lease.objectId(), lease.providerId(), epoch,
                canonicalRevision, observed.replicaRevision(), PhysicalCustodyLeaseStatus.ACQUIRED, null));
        return new PhysicalReplicaCustodyState(nextReplicas, nextLeases, withoutDiagnostic(scopeId));
    }

    /** Retains actual contradictory evidence and its exact write fence as one local conflict. */
    public PhysicalReplicaCustodyState conflictProjection(SubjectId scopeId, long epoch, long canonicalRevision,
                                                          long replicaRevision, String fingerprint, String provenance,
                                                          DiagnosticTuple diagnostic) {
        PhysicalCustodyLease lease = requireLive(scopeId, epoch);
        PhysicalReplicaRecord replica = requireReplica(lease.objectId());
        if (lease.status() != PhysicalCustodyLeaseStatus.PREPARING || canonicalRevision != lease.expectedCanonicalRevision()
                || replicaRevision != lease.expectedReplicaRevision()) {
            throw new IllegalArgumentException("projection conflict fence is stale or not preparing");
        }
        requireUnresolvedDiagnostic(scopeId, diagnostic);
        PhysicalReplicaRecord observed = replica.observe(canonicalRevision, fingerprint, provenance, canonicalRevision);
        if (observed.state() != PhysicalReplicaState.CONFLICT) {
            throw new IllegalArgumentException("matching projection evidence is not a conflict");
        }
        Map<SubjectId, PhysicalReplicaRecord> nextReplicas = new LinkedHashMap<>(replicas);
        nextReplicas.put(replica.objectId(), observed);
        Map<SubjectId, PhysicalCustodyLease> nextLeases = new LinkedHashMap<>(custodyByScope);
        nextLeases.put(scopeId, new PhysicalCustodyLease(scopeId, lease.objectId(), lease.providerId(), epoch,
                canonicalRevision, observed.replicaRevision(), PhysicalCustodyLeaseStatus.UNRESOLVED,
                PhysicalCustodyUnresolvedReason.OBSERVATION_MISMATCH));
        Map<SubjectId, DiagnosticTuple> nextDiagnostics = new LinkedHashMap<>(diagnostics);
        nextDiagnostics.put(scopeId, diagnostic);
        return new PhysicalReplicaCustodyState(nextReplicas, nextLeases, nextDiagnostics);
    }
    public PhysicalReplicaCustodyState checkpoint(SubjectId scopeId, long expectedEpoch, long canonicalRevision, long replicaRevision) {
        PhysicalCustodyLease lease = requireLive(scopeId, expectedEpoch);
        requireExactEvidence(lease, canonicalRevision, replicaRevision);
        Map<SubjectId, PhysicalCustodyLease> next = new LinkedHashMap<>(custodyByScope); next.put(scopeId, lease.checkpoint(canonicalRevision, replicaRevision));
        return new PhysicalReplicaCustodyState(replicas, next, diagnostics);
    }
    public PhysicalReplicaCustodyState unresolved(SubjectId scopeId, long expectedEpoch, long expectedCanonicalRevision,
                                                  long expectedReplicaRevision, PhysicalCustodyUnresolvedReason reason, DiagnosticTuple diagnostic) {
        PhysicalCustodyLease lease = requireLive(scopeId, expectedEpoch);
        requireExactEvidence(lease, expectedCanonicalRevision, expectedReplicaRevision);
        requireUnresolvedDiagnostic(scopeId, diagnostic);
        Map<SubjectId, PhysicalCustodyLease> next = new LinkedHashMap<>(custodyByScope); Map<SubjectId, DiagnosticTuple> causes = new LinkedHashMap<>(diagnostics); causes.put(scopeId, diagnostic); next.put(scopeId, lease.unresolved(reason));
        return new PhysicalReplicaCustodyState(replicas, next, causes);
    }
    public PhysicalReplicaCustodyState release(SubjectId scopeId, long expectedEpoch, long canonicalRevision, long replicaRevision) {
        PhysicalCustodyLease lease = requireLive(scopeId, expectedEpoch);
        requireExactEvidence(lease, canonicalRevision, replicaRevision);
        Map<SubjectId, PhysicalCustodyLease> next = new LinkedHashMap<>(custodyByScope); next.put(scopeId, lease.release(canonicalRevision, replicaRevision));
        return new PhysicalReplicaCustodyState(replicas, next, withoutDiagnostic(scopeId));
    }
    private PhysicalReplicaRecord requireReplica(SubjectId objectId) {
        PhysicalReplicaRecord replica = replicas.get(Objects.requireNonNull(objectId, "object id"));
        if (replica == null) throw new IllegalArgumentException("replica is unknown");
        return replica;
    }
    private Map<SubjectId, DiagnosticTuple> withoutDiagnostic(SubjectId id) {
        Map<SubjectId, DiagnosticTuple> next = new LinkedHashMap<>(diagnostics); next.remove(id); return next;
    }
    private static void requireConflictDiagnostic(SubjectId id, DiagnosticTuple diagnostic) {
        if (diagnostic == null || diagnostic.reason() != DiagnosticReason.REPLICA_CUSTODY_CONFLICT
                || !diagnostic.owner().id().equals(id) || !diagnostic.subject().id().equals(id)) throw new IllegalArgumentException("replica conflict requires its exact diagnostic tuple");
    }
    private static void requireUnresolvedDiagnostic(SubjectId id, DiagnosticTuple diagnostic) {
        if (diagnostic == null || diagnostic.reason() != DiagnosticReason.PHYSICAL_CUSTODY_UNRESOLVED
                || !diagnostic.owner().id().equals(id) || !diagnostic.subject().id().equals(id)) throw new IllegalArgumentException("custody unresolved requires its exact diagnostic tuple");
    }
    private PhysicalCustodyLease requireLive(SubjectId scopeId, long expectedEpoch) {
        PhysicalCustodyLease lease = custodyByScope.get(Objects.requireNonNull(scopeId, "scope id"));
        if (lease == null || !lease.live() || lease.authorityEpoch() != expectedEpoch) throw new IllegalArgumentException("custody epoch is stale or unavailable");
        return lease;
    }
    private void requireExactEvidence(PhysicalCustodyLease lease, long canonicalRevision, long replicaRevision) {
        PhysicalReplicaRecord replica = requireReplica(lease.objectId());
        if (canonicalRevision != lease.expectedCanonicalRevision() || replicaRevision != lease.expectedReplicaRevision()
                || replica.state() != PhysicalReplicaState.OBSERVED_CURRENT || replica.observedCanonicalRevision() != canonicalRevision
                || replica.replicaRevision() != replicaRevision) {
            throw new IllegalArgumentException("custody replica fence is stale or forged");
        }
    }
}
