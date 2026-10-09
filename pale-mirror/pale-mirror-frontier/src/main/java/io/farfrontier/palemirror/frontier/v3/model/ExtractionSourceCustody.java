package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.extraction.*;
import java.util.*;

/** Mining's adapter to the common replica authority, without a second source/depletion ledger. */
public final class ExtractionSourceCustody {
    public static final SubjectId PROVIDER = new SubjectId("provider:extraction-source");
    private ExtractionSourceCustody() { }
    public static String fingerprint(ExtractionSiteState state, ExtractionRegion region) {
        var deposit = state.deposits().get(region.siteId());
        String text = region.cells(state).stream().sorted(Comparator.comparingLong(ExtractionLayout.Cell::id))
                .map(cell -> cell.id() + ":" + deposit.cells().get(cell.id()).revision() + ":" + block(deposit.cells().get(cell.id()).knownBlock()))
                .collect(java.util.stream.Collectors.joining("|"));
        try { return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static String block(BlockExtraction.Block value) {
        return value.kind() + value.properties().entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(entry -> ";" + entry.getKey() + "=" + entry.getValue()).collect(java.util.stream.Collectors.joining());
    }
    public static boolean blocksCold(FrontierWorldState state, ExtractionTarget target) {
        var position = state.extractionSites().deposits().get(target.key().owner()).site().layout().require(target.key().cell()).source();
        var region = new ExtractionRegion(target.key().owner(), Math.floorDiv(position.x(), 16), Math.floorDiv(position.z(), 16));
        var lease = state.replicaCustody().custodyByScope().get(region.scopeId());
        return lease != null && lease.live();
    }
    public static PhysicalReplicaCustodyState apply(FrontierWorldState state, SubjectId subject,
            ExtractionSourceBoundary event, long canonicalRevision) {
        var region = event.region(); region.cells(state.extractionSites());
        if (!subject.equals(region.objectId()) || !fingerprint(state.extractionSites(), region).equals(event.fingerprint()))
            throw new IllegalArgumentException("source boundary has foreign address or stale canonical image");
        var custody = state.replicaCustody(); var prior = custody.custodyByScope().get(region.scopeId());
        var replica = custody.replicas().get(region.objectId());
        if (event.expectedEpoch() != (prior == null ? 0 : prior.authorityEpoch())
                || event.expectedReplicaRevision() != (replica == null ? 0 : replica.replicaRevision())
                || prior != null && (!prior.objectId().equals(region.objectId()) || !prior.providerId().equals(PROVIDER)))
            throw new IllegalArgumentException("source boundary does not match its declared authority epoch");
        switch (event.operation()) {
            case PREPARE -> {
                if (replica == null) custody = custody.declare(PhysicalReplicaRecord.expected(region.objectId(),
                        "extraction.source-region", canonicalRevision, event.fingerprint(), region.provenance()));
                else {
                    if (prior != null && prior.live()) throw new IllegalArgumentException("source projection already has live custody");
                    custody = custody.emit(region.objectId(), replica.emittedCanonicalRevision(), replica.replicaRevision(),
                            canonicalRevision, event.fingerprint(), region.provenance());
                }
                var expected = custody.replicas().get(region.objectId());
                return custody.prepareProjection(new PhysicalCustodyLease(region.scopeId(), region.objectId(), PROVIDER,
                        event.expectedEpoch() + 1, expected.emittedCanonicalRevision(), expected.replicaRevision(), PhysicalCustodyLeaseStatus.PREPARING, null));
            }
            case CONFIRM -> {
                if (prior == null || prior.status() != PhysicalCustodyLeaseStatus.PREPARING)
                    throw new IllegalArgumentException("source confirmation lacks its before-write custody");
                return custody.confirmProjection(region.scopeId(), prior.authorityEpoch(), prior.expectedCanonicalRevision(),
                        prior.expectedReplicaRevision(), event.fingerprint(), region.provenance());
            }
            case RELEASE -> {
                if (prior == null || state.extractionSites().work().values().stream().anyMatch(job -> job.pending().isPresent()
                        && job.siteId().equals(region.siteId()) && job.target().filter(target -> region.contains(
                            state.extractionSites().deposits().get(region.siteId()).site().layout().require(target.key().cell()).source())).isPresent()))
                    throw new IllegalArgumentException("source release retains a pending non-replayable effect");
                return release(custody, prior);
            }
        }
        throw new IllegalArgumentException("undeclared extraction source transition");
    }
    public static PhysicalReplicaCustodyState release(PhysicalReplicaCustodyState custody, PhysicalCustodyLease lease) {
        if (lease.status() == PhysicalCustodyLeaseStatus.ACQUIRED)
            custody = custody.checkpoint(lease.scopeId(), lease.authorityEpoch(), lease.expectedCanonicalRevision(), lease.expectedReplicaRevision());
        return custody.release(lease.scopeId(), lease.authorityEpoch(), lease.expectedCanonicalRevision(), lease.expectedReplicaRevision());
    }
    /** Called by the source owner after a confirmed effect, in the SAME canonical transaction. */
    public static PhysicalReplicaCustodyState closeMutation(FrontierWorldState state, ExtractionSiteState successor,
            ExtractionTarget target, long canonicalRevision) {
        var position = state.extractionSites().deposits().get(target.key().owner()).site().layout().require(target.key().cell()).source();
        var region = new ExtractionRegion(target.key().owner(), Math.floorDiv(position.x(), 16), Math.floorDiv(position.z(), 16));
        var lease = state.replicaCustody().custodyByScope().get(region.scopeId());
        if (lease == null || lease.status() != PhysicalCustodyLeaseStatus.ACQUIRED || !lease.providerId().equals(PROVIDER))
            throw new IllegalArgumentException("physical mining effect lacks the current source custodian");
        var custody = release(state.replicaCustody(), lease); var replica = custody.replicas().get(region.objectId());
        var fingerprint = fingerprint(successor, region);
        custody = custody.emit(region.objectId(), replica.emittedCanonicalRevision(), replica.replicaRevision(), canonicalRevision, fingerprint, region.provenance());
        replica = custody.replicas().get(region.objectId());
        // One cell's receipt is not an observation of the entire region. A neighbour may
        // have changed meanwhile. The native owner confirms the complete successor image.
        return custody.prepareProjection(new PhysicalCustodyLease(region.scopeId(), region.objectId(), PROVIDER, lease.authorityEpoch() + 1,
                canonicalRevision, replica.replicaRevision(), PhysicalCustodyLeaseStatus.PREPARING, null));
    }
}
