package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.ImmutableReferenceIndex.Source;
import java.util.*;

/** Trade-owned exact reads; generic indexes select checks without owning commercial outcomes. */
final class GoodsTradeReferenceValidation {
    private record Context(FrontierWorldState state, Map<SubjectId, GoodsTradeParty> parties,
                           Map<SubjectId, EconomicOwnerKind> accountKinds, Map<SubjectId, List<GoodsTradeContract>> commitments) { }
    private static final ImmutableRegistryView<SubjectId, GoodsParticipant, GoodsTradeParty> PARTIES =
            new ImmutableRegistryView<>(GoodsParticipantState.MAX_PARTICIPANTS, Objects::equals);
    private static final ImmutableRegistryView<SubjectId, EconomicAccount, EconomicOwnerKind> KINDS =
            new ImmutableRegistryView<>(EconomicLedger.MAX_ACCOUNTS);
    private static final ImmutableGroupView<SubjectId, GoodsTradeContract, SubjectId> COMMITMENT_ROWS = new ImmutableGroupView<>();
    private static final Source<Context, SubjectId, List<GoodsTradeContract>> COMMITMENT = new Source<>(Context::commitments);
    private static final Source<Context, SubjectId, GoodsTradeParty> PARTY = new Source<>(Context::parties);
    private static final Source<Context, SubjectId, EconomicOwnerKind> KIND = new Source<>(Context::accountKinds);
    private static final Source<Context, SubjectId, ContainerRecord> CONTAINER = new Source<>(c -> c.state().inventory().containers());
    private static final Source<Context, SubjectId, GoodsTradeOrder> ORDER = new Source<>(c -> c.state().companies().goodsTrade().orders());
    private static final Source<Context, SubjectId, GoodsTradeContract> CONTRACT = new Source<>(c -> c.state().companies().goodsTrade().contracts());
    private static final Source<Context, SubjectId, FinancialReservation> HOLD = new Source<>(c -> c.state().inventory().economics().reservations());
    private static final Source<Context, SubjectId, ExactItemStack> ITEM = new Source<>(c -> c.state().inventory().items());
    private static final Source<Context, SubjectId, ClaimAllocation> CLAIM = new Source<>(c -> c.state().inventory().fungibleResources().claims());
    private static final ImmutableReferenceIndex<Context, SubjectId, GoodsTradeOrder> ORDERS = new ImmutableReferenceIndex<>(
            GoodsTradeState.MAX_ORDERS, 4, List.of(PARTY, KIND, CONTAINER, COMMITMENT), (c, order) -> List.of(
                    PARTY.at(order.party().id()), KIND.at(order.party().id()), CONTAINER.at(order.containerId()), COMMITMENT.at(order.id())),
            (c, id, order) -> {
                if (c.accountKinds().get(order.party().id()) != order.party().kind())
                    throw new IllegalArgumentException("goods order " + order.id().value() + " has a forged participant kind");
                if (!order.party().equals(c.parties().get(order.party().id())))
                    throw new IllegalArgumentException("public order lost its exact registered participant");
                requireContainer(c, order.containerId());
                int total = order.retiredCommittedQuantity();
                for (var contract : c.commitments().getOrDefault(order.id(), List.of())) total = Math.addExact(total, contract.quantity());
                if (order.committedQuantity() != total)
                    throw new IllegalArgumentException("goods order commitment is not accounted by retained contracts");
            });
    private static final ImmutableReferenceIndex<Context, SubjectId, GoodsTradeContract> CONTRACTS = new ImmutableReferenceIndex<>(
            GoodsTradeState.MAX_CONTRACTS, 5 + GoodsTradeContract.MAX_ALLOCATIONS,
            List.of(ORDER, CONTAINER, HOLD, ITEM, CLAIM), (c, contract) -> {
                var reads = new ArrayList<ImmutableReferenceIndex.Read<Context>>();
                reads.add(ORDER.at(contract.sellOrderId())); reads.add(ORDER.at(contract.buyOrderId()));
                reads.add(CONTAINER.at(contract.sourceContainerId())); reads.add(CONTAINER.at(contract.receiverContainerId()));
                reads.add(HOLD.at(contract.financialReservationId()));
                for (var claim : contract.outstandingClaims().keySet()) {
                    var individual = contract.individualAllocations().get(claim);
                    reads.add(individual != null ? ITEM.at(individual.itemId()) : CLAIM.at(claim));
                }
                return reads;
            }, GoodsTradeReferenceValidation::validateContract);
    private static final ImmutableReferenceIndex<Context, SubjectId, ClaimAllocation> CLAIMS = new ImmutableReferenceIndex<>(
            FungibleResourceLedger.MAX_CLAIMS, 1, List.of(CONTRACT), (c, claim) ->
                claim.purpose() == ClaimPurpose.GOODS_TRADE ? List.of(CONTRACT.at(claim.claimantId())) : List.of(),
            (c, id, claim) -> {
                if (claim.purpose() != ClaimPurpose.GOODS_TRADE) return;
                var contract = c.state().companies().goodsTrade().contracts().get(claim.claimantId());
                if (contract == null || !contract.outstandingClaims().containsKey(claim.id()))
                    throw new IllegalArgumentException("goods allocation lacks its exact commercial owner");
            });
    private static final ImmutableInvariantCheck<Void> ITEM_EXCLUSIVITY = new ImmutableInvariantCheck<>();
    private GoodsTradeReferenceValidation() { }

    static void validate(FrontierWorldState state) {
        GoodsTradeParticipantValidation.validate(state);
        var trade = state.companies().goodsTrade(); var epoch = state.bootstrap();
        var parties = PARTIES.project(epoch, trade.participants().participants(), (id, p) -> p.party());
        var kinds = KINDS.project(epoch, state.inventory().economics().accounts(), (id, account) -> account.ownerKind());
        var commitments = COMMITMENT_ROWS.groupedRows(epoch, trade.contracts(),
                contract -> Set.of(contract.sellOrderId(), contract.buyOrderId()));
        var context = new Context(state, parties, kinds, commitments);
        ORDERS.validate(epoch, trade.orders(), context);
        CONTRACTS.validate(epoch, trade.contracts(), context);
        CLAIMS.validate(epoch, state.inventory().fungibleResources().claims(), context);
        ITEM_EXCLUSIVITY.validate(() -> validateItemExclusivity(trade), epoch, trade.contracts());
    }
    private static void validateContract(Context context, SubjectId id, GoodsTradeContract contract) {
        var state = context.state(); var trade = state.companies().goodsTrade();
        GoodsTradeState.validateTerms(contract, trade.orders().get(contract.sellOrderId()), trade.orders().get(contract.buyOrderId()));
        requireContainer(context, contract.sourceContainerId()); requireContainer(context, contract.receiverContainerId());
        var hold = state.inventory().economics().reservations().get(contract.financialReservationId());
        if (contract.terminal() ? hold != null : hold == null || !hold.reasonId().equals(contract.id())
                || !hold.payerId().equals(contract.buyer().id()) || !hold.payeeId().equals(contract.seller().id())
                || !hold.amount().equals(contract.outstandingPrice()))
            throw new IllegalArgumentException("goods contract has a missing or mismatched financial hold");
        for (var allocation : contract.outstandingClaims().entrySet()) {
            var individual = contract.individualAllocations().get(allocation.getKey());
            if (individual != null) {
                if (!individual.matches(state.inventory().items().get(individual.itemId())))
                    throw new IllegalArgumentException("individual contract lost its allocated item or condition");
            } else {
                var claim = state.inventory().fungibleResources().claims().get(allocation.getKey());
                if (claim == null || claim.purpose() != ClaimPurpose.GOODS_TRADE || !claim.claimantId().equals(contract.id())
                        || !claim.economicOwnerId().equals(contract.seller().id()) || !claim.itemKind().equals(contract.itemKind())
                        || claim.quantity() != allocation.getValue())
                    throw new IllegalArgumentException("goods contract has a missing or mismatched resource allocation");
            }
        }
    }
    private static void validateItemExclusivity(GoodsTradeState trade) {
        var individualItems = new HashSet<SubjectId>();
        for (var contract : trade.contracts().values()) {
            for (var claim : contract.outstandingClaims().keySet()) {
                var individual = contract.individualAllocations().get(claim);
                if (individual != null && !individualItems.add(individual.itemId()))
                    throw new IllegalArgumentException("individual contract lost its allocated item or condition");
            }
        }
    }
    private static void requireContainer(Context context, SubjectId id) {
        if (!context.state().inventory().containers().containsKey(id))
            throw new IllegalArgumentException("goods endpoint has no declared container");
    }
}
