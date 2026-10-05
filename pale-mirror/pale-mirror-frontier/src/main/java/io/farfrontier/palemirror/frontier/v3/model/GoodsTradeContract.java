package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Commercial terms and exact remaining obligations. Stock and balances remain in their ledgers. */
public record GoodsTradeContract(SubjectId id, SubjectId sellOrderId, SubjectId buyOrderId,
                                GoodsTradeParty seller, GoodsTradeParty buyer,
                                SubjectId sourceContainerId, SubjectId receiverContainerId,
                                String itemKind, int quantity, FixedScalar deliveredUnitPrice,
                                SubjectId financialReservationId, long revision,
                                Map<SubjectId, Integer> outstandingClaims,
                                Map<SubjectId, GoodsTradeAcceptance> acceptances,
                                Map<SubjectId, GoodsTradeDisposition> dispositions,
                                int acceptedQuantity, int disposedQuantity) {
    public static final int MAX_RECEIPTS = 128;
    public GoodsTradeContract(SubjectId id, SubjectId sellOrderId, SubjectId buyOrderId,
                              GoodsTradeParty seller, GoodsTradeParty buyer, SubjectId sourceContainerId,
                              SubjectId receiverContainerId, String itemKind, int quantity, FixedScalar deliveredUnitPrice,
                              SubjectId financialReservationId, long revision, Map<SubjectId, Integer> outstandingClaims,
                              Map<SubjectId, GoodsTradeAcceptance> acceptances) {
        this(id, sellOrderId, buyOrderId, seller, buyer, sourceContainerId, receiverContainerId, itemKind, quantity,
                deliveredUnitPrice, financialReservationId, revision, outstandingClaims, acceptances, Map.of(),
                acceptances.values().stream().mapToInt(r -> r.title().quantity()).reduce(0, Math::addExact), 0);
    }
    public GoodsTradeContract {
        Objects.requireNonNull(id); Objects.requireNonNull(sellOrderId); Objects.requireNonNull(buyOrderId);
        Objects.requireNonNull(seller); Objects.requireNonNull(buyer);
        Objects.requireNonNull(sourceContainerId); Objects.requireNonNull(receiverContainerId);
        Objects.requireNonNull(deliveredUnitPrice); Objects.requireNonNull(financialReservationId);
        outstandingClaims = Map.copyOf(outstandingClaims); acceptances = Map.copyOf(acceptances); dispositions = Map.copyOf(dispositions);
        if (sellOrderId.equals(buyOrderId) || seller.id().equals(buyer.id()) || quantity < 1 || quantity > ResourceLot.MAX_QUANTITY
                || itemKind == null || !itemKind.matches("[a-z][a-z0-9_-]{0,31}:[a-z0-9][a-z0-9_./-]{0,127}")
                || deliveredUnitPrice.raw() <= 0 || revision < 0 || revision > quantity
                || acceptedQuantity < 0 || disposedQuantity < 0 || acceptedQuantity > quantity || disposedQuantity > quantity
                || outstandingClaims.size() > 64 || acceptances.size() + dispositions.size() != Math.min(revision, MAX_RECEIPTS)
                || outstandingClaims.values().stream().anyMatch(q -> q < 1 || q > quantity)) {
            throw new IllegalArgumentException("invalid goods contract terms or bounded obligations");
        }
        int accepted = 0;
        for (var entry : acceptances.entrySet()) {
            GoodsTradeAcceptance receipt = entry.getValue();
            if (!entry.getKey().equals(receipt.id()) || !receipt.contractId().equals(id)
                    || !receipt.title().sourceOwnerId().equals(seller.id())
                    || !receipt.title().destinationOwnerId().equals(buyer.id())
                    || receipt.expectedContractRevision() >= revision || receipt.expectedContractRevision() < revision - MAX_RECEIPTS) {
                throw new IllegalArgumentException("goods contract has a foreign acceptance receipt");
            }
            accepted = Math.addExact(accepted, receipt.title().quantity());
        }
        int disposed = 0;
        for (var entry : dispositions.entrySet()) {
            GoodsTradeDisposition disposition = entry.getValue();
            if (!entry.getKey().equals(disposition.id()) || acceptances.containsKey(disposition.id())
                    || !disposition.contractId().equals(id) || disposition.expectedContractRevision() >= revision
                    || disposition.expectedContractRevision() < revision - MAX_RECEIPTS) {
                throw new IllegalArgumentException("goods contract has a foreign disposition");
            }
            disposed = Math.addExact(disposed, disposition.quantity());
        }
        if (java.util.stream.Stream.concat(acceptances.values().stream().map(GoodsTradeAcceptance::expectedContractRevision),
                dispositions.values().stream().map(GoodsTradeDisposition::expectedContractRevision)).distinct().count() != Math.min(revision, MAX_RECEIPTS)
                || accepted > acceptedQuantity || disposed > disposedQuantity
                || revision <= MAX_RECEIPTS && (accepted != acceptedQuantity || disposed != disposedQuantity)
                || Math.addExact(acceptedQuantity, disposedQuantity) < revision
                || Math.addExact(sum(outstandingClaims), Math.addExact(acceptedQuantity, disposedQuantity)) != quantity) {
            throw new IllegalArgumentException("goods contract quantity or receipt history is not closed");
        }
        deliveredUnitPrice.multiply(quantity); // Overflow fails at admission/hydration, not during payment.
    }
    public int remainingQuantity() { return sum(outstandingClaims); }
    public boolean terminal() { return remainingQuantity() == 0; }
    public boolean fulfilled() { return terminal() && disposedQuantity == 0; }
    public FixedScalar outstandingPrice() { return deliveredUnitPrice.multiply(remainingQuantity()); }
    public GoodsTradeContract accept(GoodsTradeAcceptance receipt) {
        if (!receipt.contractId().equals(id) || receipt.expectedContractRevision() != revision
                || acceptances.containsKey(receipt.id()) || dispositions.containsKey(receipt.id()) || terminal()) {
            throw new IllegalArgumentException("stale, duplicate or foreign goods acceptance");
        }
        int remaining = outstandingClaims.getOrDefault(receipt.title().claimId(), 0) - receipt.title().quantity();
        if (remaining < 0) throw new IllegalArgumentException("acceptance exceeds the retained contract allocation");
        Map<SubjectId, Integer> claims = new HashMap<>(outstandingClaims);
        if (remaining == 0) claims.remove(receipt.title().claimId()); else claims.put(receipt.title().claimId(), remaining);
        Map<SubjectId, GoodsTradeAcceptance> receipts = new HashMap<>(acceptances); receipts.put(receipt.id(), receipt);
        Map<SubjectId, GoodsTradeDisposition> dispositionWindow = new HashMap<>(dispositions); trim(receipts, dispositionWindow);
        return new GoodsTradeContract(id, sellOrderId, buyOrderId, seller, buyer, sourceContainerId, receiverContainerId, itemKind,
                quantity, deliveredUnitPrice, financialReservationId, Math.addExact(revision, 1), claims, receipts, dispositionWindow,
                Math.addExact(acceptedQuantity, receipt.title().quantity()), disposedQuantity);
    }
    public GoodsTradeContract dispose(GoodsTradeDisposition disposition) {
        if (!disposition.contractId().equals(id) || disposition.expectedContractRevision() != revision
                || acceptances.containsKey(disposition.id()) || dispositions.containsKey(disposition.id())
                || outstandingClaims.getOrDefault(disposition.claimId(), 0) != disposition.quantity()) {
            throw new IllegalArgumentException("stale, duplicate or incomplete goods allocation disposition");
        }
        Map<SubjectId, Integer> claims = new HashMap<>(outstandingClaims); claims.remove(disposition.claimId());
        Map<SubjectId, GoodsTradeDisposition> next = new HashMap<>(dispositions); next.put(disposition.id(), disposition);
        Map<SubjectId, GoodsTradeAcceptance> receipts = new HashMap<>(acceptances); trim(receipts, next);
        return new GoodsTradeContract(id, sellOrderId, buyOrderId, seller, buyer, sourceContainerId, receiverContainerId, itemKind,
                quantity, deliveredUnitPrice, financialReservationId, Math.addExact(revision, 1), claims, receipts, next,
                acceptedQuantity, Math.addExact(disposedQuantity, disposition.quantity()));
    }
    /** Shipment preparation divides obligations, not receipts or commercial terms. */
    public GoodsTradeContract partition(ResourceClaimPartition partition) {
        int quantity = partition.quantity(), remaining = outstandingClaims.getOrDefault(partition.claimId(), 0) - quantity;
        if (remaining < 1 || outstandingClaims.containsKey(partition.childClaimId())) {
            throw new IllegalArgumentException("commercial partition exceeds its current allocation or reuses a child");
        }
        Map<SubjectId, Integer> claims = new HashMap<>(outstandingClaims);
        claims.put(partition.claimId(), remaining); claims.put(partition.childClaimId(), quantity);
        return new GoodsTradeContract(id, sellOrderId, buyOrderId, seller, buyer, sourceContainerId, receiverContainerId, itemKind,
                this.quantity, deliveredUnitPrice, financialReservationId, revision, claims, acceptances, dispositions, acceptedQuantity, disposedQuantity);
    }
    private static void trim(Map<SubjectId, GoodsTradeAcceptance> receipts, Map<SubjectId, GoodsTradeDisposition> dispositions) {
        if (receipts.size() + dispositions.size() <= MAX_RECEIPTS) return;
        GoodsTradeAcceptance firstReceipt = receipts.values().stream().min(java.util.Comparator.comparingLong(
                GoodsTradeAcceptance::expectedContractRevision)).orElse(null);
        GoodsTradeDisposition firstDisposition = dispositions.values().stream().min(java.util.Comparator.comparingLong(
                GoodsTradeDisposition::expectedContractRevision)).orElse(null);
        if (firstReceipt != null && (firstDisposition == null || firstReceipt.expectedContractRevision() < firstDisposition.expectedContractRevision())) {
            receipts.remove(firstReceipt.id());
        } else dispositions.remove(java.util.Objects.requireNonNull(firstDisposition).id());
    }
    private static int sum(Map<SubjectId, Integer> values) { return values.values().stream().reduce(0, Math::addExact); }
}
