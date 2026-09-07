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
                                          Map<SubjectId, PhysicalCustodyLease> custodyByScope) {
    private static final int MAX_RECORDS = 4_096;

    public PhysicalReplicaCustodyState {
        replicas = Map.copyOf(Objects.requireNonNull(replicas, "replicas"));
        custodyByScope = Map.copyOf(Objects.requireNonNull(custodyByScope, "custody by scope"));
        if (replicas.size() > MAX_RECORDS || custodyByScope.size() > MAX_RECORDS) throw new IllegalArgumentException("replica custody retention limit exceeded");
        for (Map.Entry<SubjectId, PhysicalReplicaRecord> entry : replicas.entrySet()) {
            if (!entry.getKey().equals(entry.getValue().objectId())) throw new IllegalArgumentException("replica index must use its exact object identity");
        }
        for (Map.Entry<SubjectId, PhysicalCustodyLease> entry : custodyByScope.entrySet()) {
            if (!entry.getKey().equals(entry.getValue().scopeId()) || !replicas.containsKey(entry.getValue().objectId())) {
                throw new IllegalArgumentException("custody must retain one indexed known replica scope");
            }
            PhysicalReplicaRecord replica = replicas.get(entry.getValue().objectId());
            if (entry.getValue().live() && (replica.state() != PhysicalReplicaState.OBSERVED_CURRENT
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

    public static PhysicalReplicaCustodyState empty() { return new PhysicalReplicaCustodyState(Map.of(), Map.of()); }
    public PhysicalReplicaCustodyState declare(PhysicalReplicaRecord replica) {
        Objects.requireNonNull(replica, "replica");
        if (replica.state() != PhysicalReplicaState.EXPECTED || replica.replicaRevision() != 1L
                || replica.observedCanonicalRevision() != replica.emittedCanonicalRevision() || replica.conflictReason().isPresent()) {
            throw new IllegalArgumentException("replica declaration must start as unobserved expected evidence");
        }
        if (replicas.containsKey(replica.objectId())) throw new IllegalArgumentException("replica identity already exists");
        Map<SubjectId, PhysicalReplicaRecord> next = new LinkedHashMap<>(replicas); next.put(replica.objectId(), replica);
        return new PhysicalReplicaCustodyState(next, custodyByScope);
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
        return new PhysicalReplicaCustodyState(next, custodyByScope);
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
        return new PhysicalReplicaCustodyState(next, custodyByScope);
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
        return new PhysicalReplicaCustodyState(replicas, next);
    }
    public PhysicalReplicaCustodyState checkpoint(SubjectId scopeId, long expectedEpoch, long canonicalRevision, long replicaRevision) {
        PhysicalCustodyLease lease = requireLive(scopeId, expectedEpoch);
        requireExactEvidence(lease, canonicalRevision, replicaRevision);
        Map<SubjectId, PhysicalCustodyLease> next = new LinkedHashMap<>(custodyByScope); next.put(scopeId, lease.checkpoint(canonicalRevision, replicaRevision));
        return new PhysicalReplicaCustodyState(replicas, next);
    }
    public PhysicalReplicaCustodyState unresolved(SubjectId scopeId, long expectedEpoch, long expectedCanonicalRevision,
                                                  long expectedReplicaRevision, PhysicalCustodyUnresolvedReason reason) {
        PhysicalCustodyLease lease = requireLive(scopeId, expectedEpoch);
        requireExactEvidence(lease, expectedCanonicalRevision, expectedReplicaRevision);
        Map<SubjectId, PhysicalCustodyLease> next = new LinkedHashMap<>(custodyByScope); next.put(scopeId, lease.unresolved(reason));
        return new PhysicalReplicaCustodyState(replicas, next);
    }
    public PhysicalReplicaCustodyState release(SubjectId scopeId, long expectedEpoch, long canonicalRevision, long replicaRevision) {
        PhysicalCustodyLease lease = requireLive(scopeId, expectedEpoch);
        requireExactEvidence(lease, canonicalRevision, replicaRevision);
        Map<SubjectId, PhysicalCustodyLease> next = new LinkedHashMap<>(custodyByScope); next.put(scopeId, lease.release(canonicalRevision, replicaRevision));
        return new PhysicalReplicaCustodyState(replicas, next);
    }
    private PhysicalReplicaRecord requireReplica(SubjectId objectId) {
        PhysicalReplicaRecord replica = replicas.get(Objects.requireNonNull(objectId, "object id"));
        if (replica == null) throw new IllegalArgumentException("replica is unknown");
        return replica;
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
