package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.List;
import java.util.Map;

/** Commercial owner composes stock, money and consent before one canonical update. */
public final class GoodsTradeStateSupport {
    private GoodsTradeStateSupport() { }
    public static boolean receivingCapacity(FrontierWorldState state, SubjectId container, String commodity, int quantity) {
        return GoodsStockSpecification.forKind(state.bootstrap().ruleset().goodsTrade(), commodity).representation()
                == GoodsStockSpecification.Representation.INDIVIDUAL
                ? ContainerStorageAdmission.receiveIndividuals(state, container, commodity, quantity, java.util.Optional.empty())
                : ContainerStorageAdmission.receive(state, container, commodity, quantity, java.util.Optional.empty());
    }

    public static FrontierWorldState place(FrontierWorldState state, SubjectId subject, GoodsTradeOrder order, long now) {
        if (!subject.equals(order.party().id()) || order.expiresAtTick() < now) {
            throw new IllegalArgumentException("only the current participant can authorize its goods order");
        }
        order.party().validate(state.inventory().economics());
        var participant = state.companies().goodsTrade().participants().participants().get(order.party().id());
        if (participant == null || !participant.party().equals(order.party()))
            throw new IllegalArgumentException("public order requires its exact registered participant");
        requireContainer(state, order.containerId());
        return state.withCompanies(state.companies().withGoodsTrade(state.companies().goodsTrade().place(order)));
    }

    public static FrontierWorldState reserve(FrontierWorldState state, SubjectId subject, GoodsTradeContract contract,
                                             List<GoodsTradeStockAllocation> allocations, long now) {
        if (!subject.equals(contract.seller().id()) || allocations.isEmpty() && contract.individualAllocations().isEmpty()
                || allocations.size() + contract.individualAllocations().size() > GoodsTradeContract.MAX_ALLOCATIONS) {
            throw new IllegalArgumentException("goods reservation lacks its declared seller or stock allocations");
        }
        var specification = GoodsStockSpecification.forKind(state.bootstrap().ruleset().goodsTrade(), contract.itemKind());
        if (specification.representation() == GoodsStockSpecification.Representation.FUNGIBLE && !contract.individualAllocations().isEmpty()
                || specification.representation() == GoodsStockSpecification.Representation.INDIVIDUAL && !allocations.isEmpty())
            throw new IllegalArgumentException("goods allocation contradicts its declared commodity representation");
        contract.seller().validate(state.inventory().economics()); contract.buyer().validate(state.inventory().economics());
        var registry = state.companies().goodsTrade().participants().participants();
        var declaredSeller = registry.get(contract.seller().id()); var declaredBuyer = registry.get(contract.buyer().id());
        if (declaredSeller == null || declaredBuyer == null || !declaredSeller.party().equals(contract.seller())
                || !declaredBuyer.party().equals(contract.buyer()) || !GoodsBuyerDemand.mutuallyKnown(declaredSeller, declaredBuyer))
            throw new IllegalArgumentException("contract needs two explicitly registered mutually known parties");
        requireContainer(state, contract.sourceContainerId()); requireContainer(state, contract.receiverContainerId());
        int protectedMinimum = GoodsTradeSourceAdmission.protectedMinimum(state, contract.seller(), contract.itemKind(), now);
        var seller = state.companies().goodsTrade().participants().participants().get(contract.seller().id());
        if (!GoodsParticipantStorage.eligible(state, seller, contract.sourceContainerId(), contract.itemKind()))
            throw new IllegalArgumentException("goods source is not an eligible participant warehouse");
        if (GoodsParticipantStorage.unclaimed(state, seller, contract.itemKind())
                - contract.quantity() < protectedMinimum)
            throw new IllegalArgumentException("goods reservation would spend the protected local reserve");
        if (!contract.sourceContainerId().equals(contract.receiverContainerId())
                && !receivingCapacity(state, contract.receiverContainerId(), contract.itemKind(), contract.quantity())) {
            throw new IllegalArgumentException("goods receiver has insufficient unreserved storage capacity");
        }
        GoodsTradeState trade = state.companies().goodsTrade().admit(contract, now);
        var declared = allocations.stream().collect(java.util.stream.Collectors.toMap(a -> a.claim().id(), a -> a.claim().quantity()));
        for (var exact : contract.individualAllocations().values()) {
            if (!GoodsStockSpecification.forKind(state.bootstrap().ruleset().goodsTrade(), contract.itemKind())
                        .accepts(state.inventory().items().get(exact.itemId()))
                    || ReferenceContainerCustody.blocksCanonicalUse(state, contract.sourceContainerId())
                    || ReferenceContainerCustody.hasLiveCustody(state, contract.sourceContainerId())
                        && !ReferenceContainerCustody.hasOperationalCustody(state, contract.sourceContainerId())
                    || declared.putIfAbsent(exact.id(), 1) != null || !exact.matches(state.inventory().items().get(exact.itemId()))
                    || !(state.inventory().items().get(exact.itemId()).custody() instanceof InventoryCustody.ContainerSlot slot)
                    || !slot.containerId().equals(contract.sourceContainerId())
                    || WorkEquipmentClaimComposition.reserved(state).contains(exact.itemId()))
                throw new IllegalArgumentException("individual sale lacks current unreserved source stock");
        }
        if (!declared.equals(contract.outstandingClaims())) {
            throw new IllegalArgumentException("goods reservation omits or duplicates exact contract allocations");
        }
        FungibleResourceLedger resources = state.inventory().fungibleResources();
        for (GoodsTradeStockAllocation allocation : allocations) {
            ClaimAllocation claim = allocation.claim(); CustodyAccount account = resources.accounts().get(allocation.accountId());
            if (account == null || !(account.custody() instanceof ResourceCustody.Container container)
                    || !container.containerId().equals(contract.sourceContainerId())
                    || !claim.claimantId().equals(contract.id()) || !claim.economicOwnerId().equals(contract.seller().id())
                    || !claim.itemKind().equals(contract.itemKind())) {
                throw new IllegalArgumentException("goods reservation has a foreign resource owner or source");
            }
            resources = switch (allocation.authority()) {
                case COLD -> resources.reserve(claim, account.id());
                case PHYSICAL -> resources.reserveBound(claim, account.id(), allocation.epoch());
            };
        }
        EconomicLedger economics = state.inventory().economics().reserve(new FinancialReservation(
                contract.financialReservationId(), contract.buyer().id(), contract.seller().id(), contract.id(), contract.outstandingPrice()));
        ExactInventory inventory = state.inventory().withFungibleResources(resources).withEconomics(economics);
        return state.withChanges(FrontierWorldStateUpdate.begin().inventory(inventory)
                .companies(state.companies().withGoodsTrade(trade)));
    }

    public static FrontierWorldState accept(FrontierWorldState state, SubjectId subject, GoodsTradeAcceptance receipt, long now) {
        GoodsTradeContract contract = state.companies().goodsTrade().contracts().get(receipt.contractId());
        if (contract == null || !subject.equals(contract.buyer().id()) || contract.fulfillment().mode() != GoodsTradeFulfillment.Mode.SELLER_DELIVERY) {
            throw new IllegalArgumentException("goods acceptance lacks its exact receiving buyer");
        }
        GoodsTradeState trade = state.companies().goodsTrade().accept(receipt, now);
        var title = receipt.title();
        var inventory = GoodsTitleAccounting.accept(state, contract, title, new ResourceCustody.Container(contract.receiverContainerId()));
        EconomicLedger economics = inventory.economics().settlePortion(contract.financialReservationId(),
                contract.deliveredUnitPrice().multiply(title.quantity()));
        inventory = inventory.withEconomics(economics);
        return state.withChanges(FrontierWorldStateUpdate.begin().inventory(inventory)
                .companies(state.companies().withGoodsTrade(trade)));
    }

    /** Full recovery and publication audit; receipts are historical, never live lot/account claims. */
    public static void validate(FrontierWorldState state) {
        GoodsTradeReferenceValidation.validate(state);
    }

    static void validateTransition(FrontierWorldState before, FrontierWorldState after) {
        var old = before.companies().goodsTrade(); var next = after.companies().goodsTrade();
        if (old == next || old.contracts() == next.contracts() && old.orders() == next.orders()) return;
        var retired = new java.util.HashMap<SubjectId, Integer>();
        ImmutableMapChanges.forEachIdentityChangedKey(old.contracts(), next.contracts(), id -> {
            var contract = old.contracts().get(id);
            if (contract == null || next.contracts().containsKey(id)) return;
            if (!contract.terminal()) throw new IllegalArgumentException("live commercial obligation cannot be erased: " + contract.id().value());
            retired.merge(contract.sellOrderId(), contract.quantity(), Math::addExact);
            retired.merge(contract.buyOrderId(), contract.quantity(), Math::addExact);
        });
        var affected = new java.util.HashSet<>(retired.keySet());
        ImmutableMapChanges.forEachIdentityChangedKey(old.orders(), next.orders(), affected::add);
        for (var id : affected) {
            var order = next.orders().get(id); var prior = old.orders().get(id);
            if (order != null && prior != null && order.retiredCommittedQuantity() != Math.addExact(prior.retiredCommittedQuantity(), retired.getOrDefault(id, 0)))
                throw new IllegalArgumentException("public order history must account exactly for retired terminal contracts");
        }
    }

    public static FrontierWorldState retire(FrontierWorldState state, SubjectId subject, GoodsTradeRetired retired, long now) {
        if (!subject.equals(GoodsTradeMarketIdentity.OWNER) || state.physicalIntents().values().stream()
                .anyMatch(i -> retired.contractIds().contains(i.causeSubjectId()) || retired.orderIds().contains(i.causeSubjectId()))) {
            throw new IllegalArgumentException("goods retirement lacks the process owner or retains a physical effect");
        }
        // No registered physical role schema carries an order/contract role.
        // Commercial effects retain shipment authorization below; typed relation
        // closure guards the transition. Never mine compatibility subject lists.
        var trade = state.companies().goodsTrade().retire(retired, now);
        if (state.shipments().shipments().values().stream().anyMatch(s -> (!s.terminal() || s.reception().isPresent())
                && retired.contractIds().contains(s.authorization().claimantId())))
            throw new IllegalArgumentException("commercial retirement retains a live shipment");
        return state.withCompanies(state.companies().withGoodsTrade(trade));
    }

    /** Source-side preparation for bounded shipments; no movement, payment or title change. */
    public static FrontierWorldState partition(FrontierWorldState state, SubjectId subject, SubjectId contractId,
                                               ResourceClaimPartition partition) {
        GoodsTradeContract contract = state.companies().goodsTrade().contracts().get(contractId);
        var resources = state.inventory().fungibleResources();
        CustodyAccount account = resources.accounts().get(partition.accountId());
        if (state.shipments().holds(partition.claimId()))
            throw new IllegalArgumentException("dispatched allocation cannot change beneath its shipment");
        if (contract == null || !subject.equals(contract.seller().id()) || account == null
                || !(account.custody() instanceof ResourceCustody.Container container)
                || !container.containerId().equals(contract.sourceContainerId())
                || state.physicalIntents().values().stream().anyMatch(i -> i.causeSubjectId().equals(contract.id()))) {
            throw new IllegalArgumentException("commercial partition lacks a source-side safe preparation boundary");
        }
        GoodsTradeState trade = state.companies().goodsTrade().partition(contractId, partition);
        resources = resources.partitionClaim(partition);
        return state.withChanges(FrontierWorldStateUpdate.begin().inventory(state.inventory().withFungibleResources(resources))
                .companies(state.companies().withGoodsTrade(trade)));
    }

    /** No cancellation crosses a delegated shipment or a possibly applied physical effect. */
    public static boolean canCancelBeforeLoading(FrontierWorldState state, GoodsTradeContract contract, SubjectId claimId) {
        if (!contract.equals(state.companies().goodsTrade().contracts().get(contract.id()))
                || !contract.outstandingClaims().containsKey(claimId) || state.shipments().holds(claimId)
                || ReferenceContainerCustody.blocksCanonicalUse(state, contract.sourceContainerId())
                || state.physicalIntents().values().stream().anyMatch(i -> i.causeSubjectId().equals(contract.id())
                    || i.roles().namedRoles().containsValue(claimId))) return false;
        var individual = contract.individualAllocations().get(claimId);
        if (individual != null) {
            var item = state.inventory().items().get(individual.itemId());
            return individual.matches(item) && item.custody() instanceof InventoryCustody.ContainerSlot slot
                    && slot.containerId().equals(contract.sourceContainerId())
                    && (!ReferenceContainerCustody.hasLiveCustody(state, slot.containerId())
                        || ReferenceContainerCustody.hasOperationalCustody(state, slot.containerId()));
        }
        var account = FungibleResourceCustodySupport.accountAtContainer(state, contract.sourceContainerId()).orElse(null);
        if (account == null || account.claimQuantities().getOrDefault(claimId, 0)
                != contract.outstandingClaims().get(claimId)) return false;
        if (ReferenceContainerCustody.hasLiveCustody(state, contract.sourceContainerId())
                && !ReferenceContainerCustody.hasOperationalCustody(state, contract.sourceContainerId())) return false;
        var bindings = state.inventory().fungibleResources().bindings().values().stream()
                .filter(binding -> binding.accountId().equals(account.id())).toList();
        if (bindings.isEmpty()) return true;
        var lease = state.replicaCustody().custodyByScope().get(ReferenceContainerCustody.scopeId(contract.sourceContainerId()));
        return lease != null && ReferenceContainerCustody.hasOperationalCustody(state, contract.sourceContainerId())
                && bindings.stream().allMatch(binding -> binding.authorityEpoch() == lease.authorityEpoch());
    }

    /** Only the seller can abandon an allocation still at the declared source; no stock changes. */
    public static FrontierWorldState cancelBeforeLoading(FrontierWorldState state, SubjectId subject,
                                                         GoodsTradeDisposition disposition, long now) {
        GoodsTradeContract contract = state.companies().goodsTrade().contracts().get(disposition.contractId());
        if (contract == null || !subject.equals(contract.seller().id())
                || disposition.reason() == GoodsTradeDisposition.Reason.OBSERVED_ALLOCATION_CHANGED
                || !canCancelBeforeLoading(state, contract, disposition.claimId())) {
            throw new IllegalArgumentException("goods cancellation lacks its declared source owner");
        }
        if (disposition.reason() != GoodsTradeDisposition.Reason.CANCELLED_BEFORE_LOADING
                && !GoodsTradeSourceAdmission.withdrawalReason(state, contract, now).equals(java.util.Optional.of(disposition.reason())))
            throw new IllegalArgumentException("goods withdrawal no longer has its declared current policy reason");
        GoodsTradeState trade = state.companies().goodsTrade().dispose(disposition);
        if (state.shipments().holds(disposition.claimId()))
            throw new IllegalArgumentException("cancellation must settle its dispatched shipment first");
        if (contract.individualAllocations().containsKey(disposition.claimId()))
            return state.withChanges(FrontierWorldStateUpdate.begin().companies(state.companies().withGoodsTrade(trade))
                    .inventory(state.inventory().withEconomics(state.inventory().economics().releasePortion(
                        contract.financialReservationId(), contract.deliveredUnitPrice().multiply(disposition.quantity())))));
        var resources = state.inventory().fungibleResources();
        CustodyAccount account = resources.accounts().values().stream()
                .filter(a -> a.claimQuantities().containsKey(disposition.claimId())).findFirst().orElseThrow(
                        () -> new IllegalArgumentException("goods cancellation lost its exact allocation"));
        if (!(account.custody() instanceof ResourceCustody.Container container)
                || !container.containerId().equals(contract.sourceContainerId())
                || state.physicalIntents().values().stream().anyMatch(i -> i.causeSubjectId().equals(contract.id()))) {
            throw new IllegalArgumentException("goods cancellation cannot erase a transport or physical obligation");
        }
        boolean bound = resources.bindings().values().stream().anyMatch(binding -> binding.accountId().equals(account.id()));
        resources = bound ? resources.releaseBoundClaim(account.id(), disposition.claimId(), state.replicaCustody()
                .custodyByScope().get(ReferenceContainerCustody.scopeId(contract.sourceContainerId())).authorityEpoch())
                : resources.releaseClaim(account.id(), disposition.claimId());
        var economics = state.inventory().economics().releasePortion(contract.financialReservationId(),
                contract.deliveredUnitPrice().multiply(disposition.quantity()));
        return state.withChanges(FrontierWorldStateUpdate.begin()
                .inventory(state.inventory().withFungibleResources(resources).withEconomics(economics))
                .companies(state.companies().withGoodsTrade(trade)));
    }

    private static void requireContainer(FrontierWorldState state, SubjectId id) {
        if (!state.inventory().containers().containsKey(id)) throw new IllegalArgumentException("goods endpoint has no declared container");
    }
}
