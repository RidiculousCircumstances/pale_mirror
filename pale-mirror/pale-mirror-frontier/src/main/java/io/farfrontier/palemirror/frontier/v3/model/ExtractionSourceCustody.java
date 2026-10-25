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
    /** A region version cannot change while another exact block/hand receipt is unresolved.
     * This is admission availability, not permission to release or replay an existing intent. */
    public static boolean mutationAvailable(ExtractionSiteState sites, ExtractionWork job) {
        if (!job.equals(sites.work().get(job.id())) || job.phase() != ExtractionWork.Phase.EXTRACT)
            throw new IllegalArgumentException("source mutation availability lacks its current extraction owner");
        if (job.pending().isPresent()) return false;
        var layout = sites.deposits().get(job.siteId()).site().layout();
        var source = layout.require(job.target().orElseThrow().key().cell()).source();
        var region = new ExtractionRegion(job.siteId(), Math.floorDiv(source.x(), 16), Math.floorDiv(source.z(), 16));
        return sites.work().values().stream().noneMatch(other -> other.siteId().equals(job.siteId())
                && other.pending().orElse(null) instanceof ExtractionPhysicalStep.BlockWork
                && region.contains(layout.require(other.target().orElseThrow().key().cell()).source()));
    }
    public static PhysicalReplicaCustodyState apply(FrontierWorldState state, SubjectId subject,
            ExtractionSourceBoundary event, long canonicalRevision) {
        var region = event.region(); region.cells(state.extractionSites());
        if (!subject.equals(region.objectId()) || !fingerprint(state.extractionSites(), region).equals(event.fingerprint()))
            throw new IllegalArgumentException("source boundary has foreign address or stale canonical image");
        boolean pending = state.extractionSites().work().values().stream().anyMatch(job -> job.pending().isPresent()
                && job.siteId().equals(region.siteId()) && job.target().filter(target -> region.contains(
                    state.extractionSites().deposits().get(region.siteId()).site().layout().require(target.key().cell()).source())).isPresent());
        var operation = switch (event.operation()) {
            case PREPARE -> SourceReplicaProtocol.Operation.PREPARE;
            case CONFIRM -> SourceReplicaProtocol.Operation.CONFIRM;
            case RELEASE -> SourceReplicaProtocol.Operation.RELEASE;
            case WITHDRAW_PROJECTION -> SourceReplicaProtocol.Operation.WITHDRAW_PROJECTION;
        };
        return SourceReplicaProtocol.boundary(state.replicaCustody(), address(region), operation, event.expectedEpoch(),
                event.expectedReplicaRevision(), event.fingerprint(), canonicalRevision, pending);
    }
    private static SourceReplicaProtocol.Address address(ExtractionRegion region) {
        return new SourceReplicaProtocol.Address(region.objectId(), region.scopeId(), PROVIDER, "extraction.source-region", region.provenance());
    }
    public static PhysicalReplicaCustodyState release(PhysicalReplicaCustodyState custody, PhysicalCustodyLease lease) {
        return SourceReplicaProtocol.release(custody, lease);
    }
    /** Called by the source owner after a confirmed effect, in the SAME canonical transaction. */
    public static PhysicalReplicaCustodyState closeMutation(FrontierWorldState state, ExtractionSiteState successor,
            ExtractionTarget target, long canonicalRevision) {
        var position = state.extractionSites().deposits().get(target.key().owner()).site().layout().require(target.key().cell()).source();
        var region = new ExtractionRegion(target.key().owner(), Math.floorDiv(position.x(), 16), Math.floorDiv(position.z(), 16));
        return SourceReplicaProtocol.mutated(state.replicaCustody(), address(region), fingerprint(successor, region), canonicalRevision);
    }
}
