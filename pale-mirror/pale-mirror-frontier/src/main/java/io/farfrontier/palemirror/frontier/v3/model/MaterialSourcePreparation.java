package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.List;
import java.util.Objects;

/** Read-only resource start boundary. Custody acquisition does not publish a slot layout. */
public final class MaterialSourcePreparation {
    private MaterialSourcePreparation() { }

    public enum Status { READY, WAITING_FOR_CUSTODY, WAITING_FOR_LAYOUT }
    public record Review(Status status, SubjectId accountId, long expectedEpoch,
                         List<MaterialSourceSelection.Slice> source) {
        public Review {
            Objects.requireNonNull(status); Objects.requireNonNull(accountId);
            source = List.copyOf(source);
            if (expectedEpoch < 0 || (status == Status.READY) != !source.isEmpty())
                throw new IllegalArgumentException("invalid material preparation review");
        }
        public List<MaterialSourceSelection.Slice> requireReady() {
            if (status != Status.READY) throw new IllegalArgumentException("material source preparation: "
                    + status + " account=" + accountId.value() + " expectedEpoch=" + expectedEpoch);
            return source;
        }
    }

    public static Review review(FrontierWorldState state, ActorContainerItemOrder order) {
        var ledger = state.inventory().fungibleResources();
        // Validate the actual obligation first. A lost account/claim is not an unpublished layout.
        var account = requireAccount(ledger, order);
        long epoch;
        if (account.custody() instanceof ResourceCustody.Container container) {
            var lease = state.replicaCustody().custodyByScope().get(ReferenceContainerCustody.scopeId(container.containerId()));
            if (!ReferenceContainerCustody.hasOperationalCustody(state, container.containerId())
                    || ReferenceContainerCustody.blocksCanonicalUse(state, container.containerId()))
                return new Review(Status.WAITING_FOR_CUSTODY, account.id(), lease == null ? 0 : lease.authorityEpoch(), List.of());
            epoch = lease.authorityEpoch();
        } else if (account.custody() instanceof ResourceCustody.Actor actor) {
            epoch = ActorBodyAuthority.current(state, actor.actorId()).physicalEpoch();
        } else throw new IllegalStateException("material order has unsupported physical source: " + account.custody());
        return review(ledger, order, epoch);
    }

    /** The observer owns publication; preparation must neither invent slots nor repair a layout. */
    public static Review review(FungibleResourceLedger ledger, ActorContainerItemOrder order, long expectedEpoch) {
        if (expectedEpoch < 1) throw new IllegalArgumentException("material preparation requires a current authority epoch");
        var account = requireAccount(ledger, order);
        var bindings = ledger.bindings().values().stream().filter(binding -> binding.accountId().equals(account.id())).toList();
        if (bindings.isEmpty()) return new Review(Status.WAITING_FOR_LAYOUT, account.id(), expectedEpoch, List.of());
        if (bindings.stream().anyMatch(binding -> binding.authorityEpoch() != expectedEpoch))
            throw new IllegalStateException("material source layout has a foreign authority epoch;"
                    + context(order, expectedEpoch, bindings));
        try {
            return new Review(Status.READY, account.id(), expectedEpoch, MaterialSourceSelection.select(ledger, order));
        } catch (IllegalArgumentException contradiction) {
            // This is contradictory canonical evidence, not proof that a physical chest drifted.
            throw new IllegalStateException("material source layout contradicts its current obligation;"
                    + context(order, expectedEpoch, bindings), contradiction);
        }
    }

    private static CustodyAccount requireAccount(FungibleResourceLedger ledger, ActorContainerItemOrder order) {
        try { return FungibleActorOrderTransfer.accounts(ledger, order).source(); }
        catch (IllegalArgumentException contradiction) {
            throw new IllegalStateException("material preparation lost its canonical obligation: order=" + order, contradiction);
        }
    }

    private static String context(ActorContainerItemOrder order, long epoch, List<PhysicalStackBinding> bindings) {
        var portion = (ActorContainerItemOrder.Portion.Fungible) order.portion();
        return " account=" + portion.sourceAccountId().value() + " owner=" + order.ownerId().value()
                + " kind=" + portion.itemKind() + " quantity=" + portion.quantity()
                + " lots=" + portion.lotQuantities() + " claim=" + portion.claimId()
                + " expectedEpoch=" + epoch + " bindings=" + bindings;
    }
}
