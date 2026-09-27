package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Exact positive lots derived from accounted cell work; a completed zero-yield epoch has no lot. */
public final class ResourceFieldYield {
    private final SubjectId siteId;
    private final SubjectId economicOwnerId;
    private final long epoch;
    private final long layoutRevision;
    private final String layoutFingerprint;
    private final int workedCells;
    private final int quantity;
    private final List<ResourceLot> lots;

    private ResourceFieldYield(SubjectId siteId, SubjectId economicOwnerId, long epoch,
                               long layoutRevision, String layoutFingerprint, int workedCells,
                               int quantity, List<ResourceLot> lots) {
        Objects.requireNonNull(siteId, "yield site");
        Objects.requireNonNull(economicOwnerId, "yield owner");
        Objects.requireNonNull(layoutFingerprint, "yield layout fingerprint");
        lots = List.copyOf(Objects.requireNonNull(lots, "yield lots"));
        if (!siteId.value().startsWith("site:") || !economicOwnerId.value().startsWith("settlement:")
                || epoch < 0 || layoutRevision < 1 || !layoutFingerprint.matches("[0-9a-f]{64}")
                || workedCells < 0 || workedCells > ResourceFieldLayout.MAX_CELLS
                || quantity < 0 || quantity > workedCells || lots.size() != (quantity + 63) / 64) {
            throw new IllegalArgumentException("field yield has invalid identity, epoch or quantity");
        }
        int remaining = quantity;
        for (int index = 0; index < lots.size(); index++) {
            int part = Math.min(64, remaining);
            ResourceLot lot = lots.get(index);
            if (!lot.id().equals(lotId(siteId, epoch, index))
                    || !lot.economicOwnerId().equals(economicOwnerId)
                    || !lot.itemKind().equals("minecraft:wheat") || lot.quantity() != part
                    || !lot.provenance().equals(provenance(siteId, epoch, layoutFingerprint, index))
                    || !lot.lineage().isEmpty()) {
                throw new IllegalArgumentException("field yield lot disagrees with its exact cycle output");
            }
            remaining -= part;
        }
        if (remaining != 0) throw new IllegalArgumentException("field yield omits a positive quantity");
        this.siteId = siteId;
        this.economicOwnerId = economicOwnerId;
        this.epoch = epoch;
        this.layoutRevision = layoutRevision;
        this.layoutFingerprint = layoutFingerprint;
        this.workedCells = workedCells;
        this.quantity = quantity;
        this.lots = lots;
    }

    public SubjectId siteId() { return siteId; }
    public SubjectId economicOwnerId() { return economicOwnerId; }
    public long epoch() { return epoch; }
    public long layoutRevision() { return layoutRevision; }
    public String layoutFingerprint() { return layoutFingerprint; }
    public int workedCells() { return workedCells; }
    public int quantity() { return quantity; }
    public List<ResourceLot> lots() { return lots; }

    /** Stable custody for one job's current bounded hand-carried part, never a slot identity. */
    public static SubjectId actorAccountId(SubjectId jobId) {
        Objects.requireNonNull(jobId, "field harvest job");
        if (!jobId.value().startsWith("job:site-harvest-"))
            throw new IllegalArgumentException("field actor account requires a harvest job");
        return new SubjectId("custody:field-actor-" + jobId.value().substring("job:".length()));
    }

    public static ResourceFieldYield fromCompletedCycle(SubjectId siteId, SubjectId economicOwnerId,
                                                        ResourceFieldCycle cycle) {
        Objects.requireNonNull(cycle, "completed field cycle");
        if (!cycle.siteId().equals(Objects.requireNonNull(siteId, "field yield site"))
                || !cycle.cycleAccounted() || !cycle.pendingPlayerBreaks().isEmpty()) {
            throw new IllegalArgumentException("field yield needs every admitted cell accounted and no unresolved player action");
        }
        int quantity = cycle.harvestedCount();
        String fingerprint = cycle.layout().fingerprint();
        var lots = new ArrayList<ResourceLot>((quantity + 63) / 64);
        for (int remaining = quantity, index = 0; remaining > 0; index++) {
            int part = Math.min(64, remaining);
            lots.add(lot(siteId, economicOwnerId, cycle.epoch(), fingerprint, index, part));
            remaining -= part;
        }
        return new ResourceFieldYield(siteId, economicOwnerId, cycle.epoch(), cycle.layout().revision(), fingerprint,
                cycle.accountedCount(), quantity, lots);
    }

    /**
     * One full 64-yield batch may close before the field is finished; only the
     * final accounted area may close a smaller batch. The caller retains the
     * previously issued quantity in its durable work/receipt owner and must
     * rederive this value before crediting it, never trust a client-supplied lot.
     */
    public static Optional<ResourceLot> nextReadyLot(SubjectId siteId, SubjectId economicOwnerId,
                                                      ResourceFieldCycle cycle, int accountedCount,
                                                      int issuedQuantityBefore) {
        Optional<ResourceLot> carried = currentCarriedLot(siteId, economicOwnerId, cycle,
                accountedCount, issuedQuantityBefore);
        return carried.filter(lot -> lot.quantity() == 64 || cycle.cycleAccounted());
    }

    /**
     * The exact current actor-held part after the last accounted cell. It can
     * grow one observed unit at a time without changing its part identity.
     * No lot exists before the first actual yield in this part.
     */
    public static Optional<ResourceLot> currentCarriedLot(SubjectId siteId, SubjectId economicOwnerId,
                                                          ResourceFieldCycle cycle, int accountedCount,
                                                          int issuedQuantityBefore) {
        Objects.requireNonNull(siteId, "yield site");
        Objects.requireNonNull(economicOwnerId, "yield owner");
        Objects.requireNonNull(cycle, "field cycle");
        if (!siteId.equals(cycle.siteId()) || !economicOwnerId.value().startsWith("settlement:")
                || accountedCount < 0 || accountedCount > cycle.layout().cells().size()
                || cycle.accountedCount() != accountedCount
                || issuedQuantityBefore < 0
                || cycle.pendingPlayerBreaks().keySet().stream().anyMatch(id -> cycle.cell(id).accounted())
                || (issuedQuantityBefore % 64 != 0
                        && !(cycle.cycleAccounted() && issuedQuantityBefore == cycle.harvestedCount()))
                || issuedQuantityBefore > cycle.harvestedCount()) {
            throw new IllegalArgumentException("field output batch has a foreign identity or work count");
        }
        int unissued = cycle.harvestedCount() - issuedQuantityBefore;
        if (unissued > 64) throw new IllegalArgumentException("field output batch skipped an earlier full lot");
        if (unissued > 0) {
            return Optional.of(lot(siteId, economicOwnerId, cycle.epoch(), cycle.layout().fingerprint(),
                    issuedQuantityBefore / 64, unissued));
        }
        return Optional.empty();
    }

    private static ResourceLot lot(SubjectId siteId, SubjectId ownerId, long epoch,
                                   String layoutFingerprint, int partIndex, int quantity) {
        return new ResourceLot(lotId(siteId, epoch, partIndex), ownerId, "minecraft:wheat", quantity,
                provenance(siteId, epoch, layoutFingerprint, partIndex), List.of());
    }

    private static SubjectId lotId(SubjectId siteId, long epoch, int part) {
        return new SubjectId("lot:field-" + digest(siteId.value() + ":" + epoch + ":" + part));
    }

    private static String provenance(SubjectId siteId, long epoch, String layoutFingerprint,
                                     int partIndex) {
        String readable = "field:" + siteId.value() + ":epoch-" + epoch
                + ":layout-" + layoutFingerprint.substring(0, 12) + ":part-" + partIndex;
        return readable.length() <= 128 ? readable : "field:sha256:"
                + digest(siteId.value() + ":" + epoch + ":" + layoutFingerprint + ":" + partIndex);
    }

    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 field-yield identity is unavailable", unavailable);
        }
    }

    @Override public boolean equals(Object other) {
        return other instanceof ResourceFieldYield yield && siteId.equals(yield.siteId)
                && economicOwnerId.equals(yield.economicOwnerId) && epoch == yield.epoch
                && layoutRevision == yield.layoutRevision && layoutFingerprint.equals(yield.layoutFingerprint)
                && workedCells == yield.workedCells && quantity == yield.quantity && lots.equals(yield.lots);
    }

    @Override public int hashCode() {
        return Objects.hash(siteId, economicOwnerId, epoch, layoutRevision, layoutFingerprint,
                workedCells, quantity, lots);
    }
}
