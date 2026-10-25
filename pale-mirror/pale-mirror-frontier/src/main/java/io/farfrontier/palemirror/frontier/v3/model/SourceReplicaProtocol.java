package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** Source-neutral adapter to the existing replica lifecycle. Families supply an exact
 * address/image and prove their own pending-effect fence before release. No source discovery here. */
public final class SourceReplicaProtocol {
    public record Address(SubjectId object, SubjectId scope, SubjectId provider, String semanticKind, String provenance) {
        public Address { Objects.requireNonNull(object); Objects.requireNonNull(scope); Objects.requireNonNull(provider);
            if (semanticKind == null || semanticKind.isBlank() || provenance == null || provenance.isBlank())
                throw new IllegalArgumentException("physical source needs its complete declared replica address"); }
    }
    public enum Operation { PREPARE, CONFIRM, RELEASE, WITHDRAW_PROJECTION }
    private SourceReplicaProtocol() { }
    public static PhysicalReplicaCustodyState boundary(PhysicalReplicaCustodyState custody, Address address, Operation operation,
            long epoch, long replicaRevision, String fingerprint, long canonicalRevision, boolean pendingEffect) {
        var prior = custody.custodyByScope().get(address.scope()); var replica = custody.replicas().get(address.object());
        if (epoch != (prior == null ? 0 : prior.authorityEpoch()) || replicaRevision != (replica == null ? 0 : replica.replicaRevision())
                || prior != null && (!prior.objectId().equals(address.object()) || !prior.providerId().equals(address.provider()))
                || replica != null && !replica.semanticKind().equals(address.semanticKind()))
            throw new IllegalArgumentException("source boundary changed its producer, scope or exact epoch");
        return switch (operation) {
            case PREPARE -> {
                if (replica == null) custody = custody.declare(PhysicalReplicaRecord.expected(address.object(), address.semanticKind(),
                        canonicalRevision, fingerprint, address.provenance()));
                else {
                    if (prior != null && prior.live()) throw new IllegalArgumentException("source already has live custody");
                    custody = replica.state() == PhysicalReplicaState.EXPECTED
                            ? custody.reissueProjection(address.object(), replica.emittedCanonicalRevision(), replica.replicaRevision(),
                                canonicalRevision, fingerprint, address.provenance())
                            : custody.emit(address.object(), replica.emittedCanonicalRevision(), replica.replicaRevision(),
                                canonicalRevision, fingerprint, address.provenance());
                }
                var expected = custody.replicas().get(address.object());
                yield custody.prepareProjection(new PhysicalCustodyLease(address.scope(), address.object(), address.provider(),
                        Math.addExact(epoch, 1), expected.emittedCanonicalRevision(), expected.replicaRevision(), PhysicalCustodyLeaseStatus.PREPARING, null));
            }
            case CONFIRM -> {
                if (prior == null || prior.status() != PhysicalCustodyLeaseStatus.PREPARING)
                    throw new IllegalArgumentException("source confirmation has no prepared custody");
                yield custody.confirmProjection(address.scope(), prior.authorityEpoch(), prior.expectedCanonicalRevision(),
                        prior.expectedReplicaRevision(), fingerprint, address.provenance());
            }
            case RELEASE, WITHDRAW_PROJECTION -> {
                if (prior == null || pendingEffect) throw new IllegalArgumentException("source release retains an unresolved effect");
                yield operation == Operation.WITHDRAW_PROJECTION
                        ? custody.withdrawProjection(address.scope(), prior.authorityEpoch(), prior.expectedCanonicalRevision(), prior.expectedReplicaRevision())
                        : release(custody, prior);
            }
        };
    }
    public static PhysicalReplicaCustodyState release(PhysicalReplicaCustodyState custody, PhysicalCustodyLease lease) {
        if (lease.status() == PhysicalCustodyLeaseStatus.ACQUIRED)
            custody = custody.checkpoint(lease.scopeId(), lease.authorityEpoch(), lease.expectedCanonicalRevision(), lease.expectedReplicaRevision());
        return custody.release(lease.scopeId(), lease.authorityEpoch(), lease.expectedCanonicalRevision(), lease.expectedReplicaRevision());
    }
    /** A settled child is not a complete source-image observation: successor starts PREPARING. */
    public static PhysicalReplicaCustodyState mutated(PhysicalReplicaCustodyState custody, Address address, String fingerprint, long canonicalRevision) {
        var lease = custody.custodyByScope().get(address.scope());
        if (lease == null || lease.status() != PhysicalCustodyLeaseStatus.ACQUIRED || !lease.objectId().equals(address.object())
                || !lease.providerId().equals(address.provider())) throw new IllegalArgumentException("source mutation lacks its acquired producer custody");
        custody = release(custody, lease); var replica = custody.replicas().get(address.object());
        custody = custody.emit(address.object(), replica.emittedCanonicalRevision(), replica.replicaRevision(), canonicalRevision, fingerprint, address.provenance());
        replica = custody.replicas().get(address.object());
        return custody.prepareProjection(new PhysicalCustodyLease(address.scope(), address.object(), address.provider(),
                Math.addExact(lease.authorityEpoch(), 1), canonicalRevision, replica.replicaRevision(), PhysicalCustodyLeaseStatus.PREPARING, null));
    }
}
