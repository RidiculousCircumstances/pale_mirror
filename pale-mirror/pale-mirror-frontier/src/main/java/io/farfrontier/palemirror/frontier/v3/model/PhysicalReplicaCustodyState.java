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
        if (replica.state() != PhysicalReplicaState.EXPECTED || replica.observedCanonicalRevision() != replica.emittedCanonicalRevision()) {
            throw new IllegalArgumentException("replica declaration must start as unobserved expected evidence");
        }
        if (replicas.containsKey(replica.objectId())) throw new IllegalArgumentException("replica identity already exists");
        Map<SubjectId, PhysicalReplicaRecord> next = new LinkedHashMap<>(replicas); next.put(replica.objectId(), replica);
        return new PhysicalReplicaCustodyState(next, custodyByScope);
    }
    public PhysicalReplicaCustodyState observe(SubjectId objectId, long expectedReplicaRevision, String fingerprint, String provenance, long observedRevision) {
        PhysicalReplicaRecord current = requireReplica(objectId);
        if (current.observedCanonicalRevision() != expectedReplicaRevision) throw new IllegalArgumentException("replica observation revision is stale");
        Map<SubjectId, PhysicalReplicaRecord> next = new LinkedHashMap<>(replicas); next.put(objectId, current.observe(fingerprint, provenance, observedRevision));
        return new PhysicalReplicaCustodyState(next, custodyByScope);
    }
    public PhysicalReplicaCustodyState acquire(PhysicalCustodyLease requested) {
        Objects.requireNonNull(requested, "custody lease");
        if (requested.status() != PhysicalCustodyLeaseStatus.ACQUIRED || requested.unresolvedReason() != null) throw new IllegalArgumentException("custody must start acquired");
        PhysicalReplicaRecord replica = requireReplica(requested.objectId());
        if (replica.state() != PhysicalReplicaState.OBSERVED_CURRENT || replica.observedCanonicalRevision() != requested.expectedReplicaRevision()) {
            throw new IllegalArgumentException("custody requires the exact current replica evidence");
        }
        PhysicalCustodyLease prior = custodyByScope.get(requested.scopeId());
        if (prior != null && (prior.live() || requested.authorityEpoch() <= prior.authorityEpoch())) throw new IllegalArgumentException("custody scope is already live or reuses its epoch");
        if (custodyByScope.values().stream().anyMatch(lease -> lease.live() && lease.objectId().equals(requested.objectId()))) throw new IllegalArgumentException("custody scope overlaps a live object scope");
        Map<SubjectId, PhysicalCustodyLease> next = new LinkedHashMap<>(custodyByScope); next.put(requested.scopeId(), requested);
        return new PhysicalReplicaCustodyState(replicas, next);
    }
    public PhysicalReplicaCustodyState checkpoint(SubjectId scopeId, long expectedEpoch, long canonicalRevision, long replicaRevision) {
        PhysicalCustodyLease lease = requireLive(scopeId, expectedEpoch);
        Map<SubjectId, PhysicalCustodyLease> next = new LinkedHashMap<>(custodyByScope); next.put(scopeId, lease.checkpoint(canonicalRevision, replicaRevision));
        return new PhysicalReplicaCustodyState(replicas, next);
    }
    public PhysicalReplicaCustodyState unresolved(SubjectId scopeId, long expectedEpoch, PhysicalCustodyUnresolvedReason reason) {
        PhysicalCustodyLease lease = requireLive(scopeId, expectedEpoch);
        Map<SubjectId, PhysicalCustodyLease> next = new LinkedHashMap<>(custodyByScope); next.put(scopeId, lease.unresolved(reason));
        return new PhysicalReplicaCustodyState(replicas, next);
    }
    public PhysicalReplicaCustodyState release(SubjectId scopeId, long expectedEpoch, long canonicalRevision, long replicaRevision) {
        PhysicalCustodyLease lease = requireLive(scopeId, expectedEpoch);
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
}
