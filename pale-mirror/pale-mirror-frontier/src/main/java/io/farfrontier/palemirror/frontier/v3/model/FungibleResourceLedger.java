package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable source of truth for ordinary resources.  Lots carry economic lineage, allocations
 * carry stable reservations, accounts carry custody, and bindings only describe a current HOT
 * Vanilla layout.  No map here is a derived stock counter.
 */
public record FungibleResourceLedger(Map<SubjectId, ResourceLot> lots, Map<SubjectId, ClaimAllocation> claims,
                                    Map<SubjectId, CustodyAccount> accounts, Map<SubjectId, PhysicalStackBinding> bindings) {
    public static final int MAX_LOTS = 16_384;
    public static final int MAX_CLAIMS = 16_384;
    public static final int MAX_ACCOUNTS = 16_384;
    public static final int MAX_BINDINGS = 16_384;

    public FungibleResourceLedger {
        lots = Map.copyOf(lots); claims = Map.copyOf(claims); accounts = Map.copyOf(accounts); bindings = Map.copyOf(bindings);
        if (lots.size() > MAX_LOTS || claims.size() > MAX_CLAIMS || accounts.size() > MAX_ACCOUNTS || bindings.size() > MAX_BINDINGS) {
            throw new IllegalArgumentException("fungible resource retention limit exceeded");
        }
        requireKeys(lots, ResourceLot::id, "resource lot"); requireKeys(claims, ClaimAllocation::id, "claim allocation");
        requireKeys(accounts, CustodyAccount::id, "custody account"); requireKeys(bindings, PhysicalStackBinding::id, "physical stack binding");
        validateAccounts(lots, claims, accounts); validateBindings(lots, claims, accounts, bindings);
    }

    public static FungibleResourceLedger empty() { return new FungibleResourceLedger(Map.of(), Map.of(), Map.of(), Map.of()); }

    /** Creates one initial lot/account pair for an authorized resource producer. */
    public FungibleResourceLedger issue(ResourceLot lot, CustodyAccount account) {
        Objects.requireNonNull(lot, "issued lot"); Objects.requireNonNull(account, "issued account");
        if (lots.containsKey(lot.id()) || accounts.containsKey(account.id()) || !account.lotQuantities().equals(Map.of(lot.id(), lot.quantity()))
                || !account.claimQuantities().isEmpty()) throw new IllegalArgumentException("fungible resource issue does not establish one fresh exact account");
        Map<SubjectId, ResourceLot> nextLots = new HashMap<>(lots); nextLots.put(lot.id(), lot);
        Map<SubjectId, CustodyAccount> nextAccounts = new HashMap<>(accounts); nextAccounts.put(account.id(), account);
        return new FungibleResourceLedger(nextLots, claims, nextAccounts, bindings);
    }

    /**
     * Accounts one new COLD field yield in the same bounded actor-held part.
     * The resource-site reducer must derive {@code next} from its exact cell
     * receipt; this owner rejects metadata changes, skipped quantities and a
     * second live cargo lot instead of converting them into new stock.
     */
    public FungibleResourceLedger accrueColdActorHarvestPart(ResourceLot next, SubjectId accountId, SubjectId actorId) {
        Objects.requireNonNull(next, "next actor-held harvest part");
        Objects.requireNonNull(accountId, "actor harvest account");
        Objects.requireNonNull(actorId, "harvest actor");
        if (!next.itemKind().equals("minecraft:wheat") || next.quantity() > 64 || !next.lineage().isEmpty()) {
            throw new IllegalArgumentException("field harvest part must be one bounded original wheat lot");
        }
        CustodyAccount account = accounts.get(accountId);
        if (account == null) {
            if (next.quantity() != 1 || lots.containsKey(next.id()) || accounts.values().stream().anyMatch(existing ->
                    existing.custody() instanceof ResourceCustody.Actor actor && actor.actorId().equals(actorId))) {
                throw new IllegalArgumentException("first actor harvest yield must create one new unit and account");
            }
            return issue(next, new CustodyAccount(accountId, new ResourceCustody.Actor(actorId),
                    Map.of(next.id(), 1), Map.of()));
        }
        ResourceLot prior = lots.get(next.id());
        if (!(account.custody() instanceof ResourceCustody.Actor actor) || !actor.actorId().equals(actorId)
                || accounts.values().stream().anyMatch(other -> !other.id().equals(accountId)
                        && other.custody() instanceof ResourceCustody.Actor owner && owner.actorId().equals(actorId))
                || prior == null || !account.lotQuantities().equals(Map.of(next.id(), prior.quantity()))
                || !account.claimQuantities().isEmpty() || bindings.values().stream().anyMatch(binding -> binding.accountId().equals(accountId))
                || prior.quantity() >= 64 || next.quantity() != prior.quantity() + 1
                || !next.withQuantity(prior.quantity()).equals(prior)) {
            throw new IllegalArgumentException("actor harvest accrual lacks its exact unbound predecessor part");
        }
        Map<SubjectId, ResourceLot> nextLots = new HashMap<>(lots);
        nextLots.put(next.id(), next);
        Map<SubjectId, CustodyAccount> nextAccounts = new HashMap<>(accounts);
        nextAccounts.put(accountId, new CustodyAccount(accountId, account.custody(), Map.of(next.id(), next.quantity()), Map.of()));
        return new FungibleResourceLedger(nextLots, claims, nextAccounts, bindings);
    }

    /**
     * The HOT counterpart retains the same actor hand, entity and authority
     * epoch while an actually observed stack grows by one. The physical
     * adapter must prove the Vanilla postcondition before submitting this
     * trusted observation; this transition never fabricates that proof.
     */
    public FungibleResourceLedger accrueObservedActorHarvestPart(ResourceLot next, SubjectId accountId,
                                                                 SubjectId actorId, long authorityEpoch,
                                                                 FungiblePhysicalObservation.Stack observed) {
        Objects.requireNonNull(next, "next observed harvest part");
        Objects.requireNonNull(accountId, "observed actor harvest account");
        Objects.requireNonNull(actorId, "observed harvest actor");
        Objects.requireNonNull(observed, "observed actor hand stack");
        if (authorityEpoch < 1 || !(observed.address() instanceof PhysicalStackAddress.ActorHand hand)
                || !hand.actorId().equals(actorId) || !observed.itemKind().equals("minecraft:wheat")
                || observed.quantity() != next.quantity() || next.quantity() > 64 || !next.lineage().isEmpty()
                || !next.itemKind().equals("minecraft:wheat")) {
            throw new IllegalArgumentException("observed harvest part lacks its exact actor hand or bounded wheat quantity");
        }
        CustodyAccount account = accounts.get(accountId);
        if (account == null) {
            FungibleResourceLedger first = accrueColdActorHarvestPart(next, accountId, actorId);
            return first.rebind(accountId, authorityEpoch,
                    FungiblePhysicalObservation.bind(first, accountId, authorityEpoch, List.of(observed)));
        }
        ResourceLot prior = lots.get(next.id());
        List<PhysicalStackBinding> current = bindings.values().stream()
                .filter(binding -> binding.accountId().equals(accountId)).toList();
        if (!(account.custody() instanceof ResourceCustody.Actor actor) || !actor.actorId().equals(actorId)
                || accounts.values().stream().anyMatch(other -> !other.id().equals(accountId)
                        && other.custody() instanceof ResourceCustody.Actor owner && owner.actorId().equals(actorId))
                || prior == null || !account.lotQuantities().equals(Map.of(next.id(), prior.quantity()))
                || !account.claimQuantities().isEmpty() || prior.quantity() >= 64
                || next.quantity() != prior.quantity() + 1 || !next.withQuantity(prior.quantity()).equals(prior)
                || current.size() != 1 || current.getFirst().authorityEpoch() != authorityEpoch
                || !current.getFirst().address().equals(observed.address())
                || !current.getFirst().lotQuantities().equals(Map.of(next.id(), prior.quantity()))
                || !current.getFirst().claimQuantities().isEmpty()) {
            throw new IllegalArgumentException("observed harvest accrual lacks its exact bound predecessor part");
        }
        PhysicalStackBinding old = current.getFirst();
        PhysicalStackBinding replacement = new PhysicalStackBinding(old.id(), accountId, old.address(), authorityEpoch,
                "minecraft:wheat", Map.of(next.id(), next.quantity()), Map.of());
        Map<SubjectId, ResourceLot> nextLots = new HashMap<>(lots);
        nextLots.put(next.id(), next);
        Map<SubjectId, CustodyAccount> nextAccounts = new HashMap<>(accounts);
        nextAccounts.put(accountId, new CustodyAccount(accountId, account.custody(), Map.of(next.id(), next.quantity()), Map.of()));
        Map<SubjectId, PhysicalStackBinding> nextBindings = new HashMap<>(bindings);
        nextBindings.put(old.id(), replacement);
        return new FungibleResourceLedger(nextLots, claims, nextAccounts, nextBindings);
    }

    /** Delivers one completed COLD field part only after its exact cell prefix made that part ready. */
    public FungibleResourceLedger deliverColdActorHarvestPart(ResourceFieldCycle cycle, SubjectId economicOwnerId,
                                                              int issuedQuantityBefore, SubjectId actorAccountId,
                                                              SubjectId actorId, SubjectId depotAccountId) {
        ResourceLot part = readyActorHarvestPart(cycle, economicOwnerId, issuedQuantityBefore, actorAccountId, actorId);
        CustodyAccount source = requireAccount(actorAccountId);
        CustodyAccount destination = fieldDepotAccount(economicOwnerId, depotAccountId, part);
        requireNoPhysicalBinding(source.id(), "COLD actor harvest delivery");
        if (accounts.containsKey(destination.id())) requireNoPhysicalBinding(destination.id(), "COLD field depot delivery");
        Map<SubjectId, CustodyAccount> next = new HashMap<>(accounts);
        next.remove(source.id());
        CustodyAccount existing = accounts.get(destination.id());
        next.put(destination.id(), existing == null ? destination : accountWithAdded(existing, Map.of(part.id(), part.quantity()), Map.of()));
        return new FungibleResourceLedger(lots, claims, next, bindings);
    }

    /** HOT counterpart: one exact actor hand becomes a fenced depot layout in the same transaction. */
    public FungibleResourceLedger deliverObservedActorHarvestPart(ResourceFieldCycle cycle, SubjectId economicOwnerId,
                                                                  int issuedQuantityBefore, SubjectId actorAccountId,
                                                                  SubjectId actorId, java.util.UUID entityId,
                                                                  SubjectId depotAccountId, long actorEpoch, long depotEpoch,
                                                                  List<PhysicalStackBinding> observedDepotBindings) {
        ResourceLot part = readyActorHarvestPart(cycle, economicOwnerId, issuedQuantityBefore, actorAccountId, actorId);
        Objects.requireNonNull(entityId, "field delivery actor body");
        List<PhysicalStackBinding> hand = bindings.values().stream()
                .filter(binding -> binding.accountId().equals(actorAccountId)).toList();
        if (actorEpoch < 1 || hand.size() != 1 || hand.getFirst().authorityEpoch() != actorEpoch
                || !hand.getFirst().address().equals(new PhysicalStackAddress.ActorHand(actorId, entityId))
                || !hand.getFirst().lotQuantities().equals(Map.of(part.id(), part.quantity()))
                || !hand.getFirst().claimQuantities().isEmpty())
            throw new IllegalArgumentException("field delivery lacks its exact bound farmer hand");
        CustodyAccount destination = fieldDepotAccount(economicOwnerId, depotAccountId, part);
        return transferObserved(actorAccountId, destination, accounts.containsKey(depotAccountId),
                actorEpoch, depotEpoch, Map.of(part.id(), part.quantity()), Map.of(), List.of(), observedDepotBindings);
    }

    /**
     * Builds the exact successor binding from actual chest slots, then commits the actor-hand
     * transfer. The temporary ledger has the lot at the depot only for layout validation; it
     * is never published before the real observed handoff clears the farmer's bound hand.
     */
    public FungibleResourceLedger deliverObservedActorHarvestStacks(ResourceFieldCycle cycle, SubjectId economicOwnerId,
                                                                    int issuedQuantityBefore, SubjectId actorAccountId,
                                                                    SubjectId actorId, java.util.UUID entityId,
                                                                    SubjectId depotAccountId, long actorEpoch, long depotEpoch,
                                                                    List<FungiblePhysicalObservation.Stack> observedDepotStacks) {
        ResourceLot part = readyActorHarvestPart(cycle, economicOwnerId, issuedQuantityBefore, actorAccountId, actorId);
        CustodyAccount destination = fieldDepotAccount(economicOwnerId, depotAccountId, part);
        CustodyAccount current = accounts.get(depotAccountId);
        CustodyAccount projected = current == null ? destination
                : accountWithAdded(current, Map.of(part.id(), part.quantity()), Map.of());
        Map<SubjectId, CustodyAccount> projectedAccounts = new HashMap<>(accounts);
        projectedAccounts.remove(actorAccountId);
        projectedAccounts.put(depotAccountId, projected);
        Map<SubjectId, PhysicalStackBinding> projectedBindings = withoutBindingsFor(actorAccountId);
        projectedBindings = withoutBindingsFor(projectedBindings, depotAccountId);
        FungibleResourceLedger projectedLedger = new FungibleResourceLedger(lots, claims, projectedAccounts, projectedBindings);
        List<PhysicalStackBinding> destinationBindings = FungiblePhysicalObservation.bind(projectedLedger,
                depotAccountId, depotEpoch, Objects.requireNonNull(observedDepotStacks, "observed harvest chest layout"));
        return deliverObservedActorHarvestPart(cycle, economicOwnerId, issuedQuantityBefore, actorAccountId,
                actorId, entityId, depotAccountId, actorEpoch, depotEpoch, destinationBindings);
    }

    private ResourceLot readyActorHarvestPart(ResourceFieldCycle cycle, SubjectId economicOwnerId,
                                              int issuedQuantityBefore, SubjectId actorAccountId, SubjectId actorId) {
        Objects.requireNonNull(cycle, "field delivery cycle");
        Objects.requireNonNull(economicOwnerId, "field delivery economic owner");
        Objects.requireNonNull(actorId, "field delivery actor");
        ResourceLot part = ResourceFieldYield.nextReadyLot(cycle.siteId(), economicOwnerId, cycle,
                cycle.accountedCount(), issuedQuantityBefore)
                .orElseThrow(() -> new IllegalArgumentException("field delivery has no completed positive part"));
        CustodyAccount source = requireAccount(actorAccountId);
        if (!(source.custody() instanceof ResourceCustody.Actor actor) || !actor.actorId().equals(actorId)
                || !source.lotQuantities().equals(Map.of(part.id(), part.quantity()))
                || !source.claimQuantities().isEmpty() || !part.equals(lots.get(part.id())))
            throw new IllegalArgumentException("field delivery lacks its exact actor-held part");
        return part;
    }

    private CustodyAccount fieldDepotAccount(SubjectId economicOwnerId, SubjectId depotAccountId, ResourceLot part) {
        Objects.requireNonNull(depotAccountId, "field delivery depot account");
        CustodyAccount existing = accounts.get(depotAccountId);
        ResourceCustody.Container expected = new ResourceCustody.Container(FrontierWorldState.depotId(economicOwnerId));
        if (existing != null) {
            if (!existing.custody().equals(expected))
                throw new IllegalArgumentException("field delivery has a foreign depot account");
            return existing;
        }
        return new CustodyAccount(depotAccountId, expected, Map.of(part.id(), part.quantity()), Map.of());
    }

    /** Reserves part of the already-accounted stock without creating a second resource balance. */
    public FungibleResourceLedger reserve(ClaimAllocation claim, SubjectId accountId) {
        Objects.requireNonNull(claim, "claim allocation"); CustodyAccount account = requireAccount(accountId);
        requireNoPhysicalBinding(account.id(), "reserve");
        Reservation reservation = reserve(account, claim);
        return withAccount(reservation.account(), reservation.claims(), bindings);
    }

    /**
     * Reserves a portion already held under one live physical authority without dropping it to
     * COLD custody.  The physical stacks do not change; their retained binding allocations are
     * deterministically extended at the same authority epoch.
     */
    public FungibleResourceLedger reserveBound(ClaimAllocation claim, SubjectId accountId, long authorityEpoch) {
        Objects.requireNonNull(claim, "claim allocation"); CustodyAccount account = requireAccount(accountId);
        if (authorityEpoch < 1) throw new IllegalArgumentException("bound claim authority epoch must be positive");
        List<PhysicalStackBinding> current = bindings.values().stream().filter(binding -> binding.accountId().equals(account.id())).toList();
        if (current.isEmpty() || current.stream().anyMatch(binding -> binding.authorityEpoch() != authorityEpoch)) {
            throw new IllegalArgumentException("bound claim does not own the current physical custody");
        }
        Reservation reservation = reserve(account, claim);
        FungibleResourceLedger unbound = withAccount(reservation.account(), reservation.claims(), withoutBindingsFor(account.id()));
        return unbound.rebind(account.id(), authorityEpoch,
                FungiblePhysicalObservation.allocateClaims(unbound, account.id(), current));
    }

    /** Releases an unspent COLD allocation when its owning work is cancelled before effect. */
    public FungibleResourceLedger releaseClaim(SubjectId accountId, SubjectId claimId) {
        CustodyAccount account = requireAccount(accountId); requireNoPhysicalBinding(account.id(), "claim release");
        ClaimAllocation claim = claims.get(Objects.requireNonNull(claimId, "released claim"));
        if (claim == null || account.claimQuantities().getOrDefault(claimId, 0) != claim.quantity()) {
            throw new IllegalArgumentException("claim release does not own one current complete allocation");
        }
        Map<SubjectId, ClaimAllocation> nextClaims = new HashMap<>(claims); nextClaims.remove(claimId);
        Map<SubjectId, Integer> nextQuantities = new HashMap<>(account.claimQuantities()); nextQuantities.remove(claimId);
        return withAccount(new CustodyAccount(account.id(), account.custody(), account.lotQuantities(), nextQuantities), nextClaims, bindings);
    }

    /** Releases named allocations across their current HOT layout after an observed physical loss. */
    public FungibleResourceLedger releaseClaims(java.util.Set<SubjectId> claimIds) {
        Objects.requireNonNull(claimIds, "released claim ids");
        if (claimIds.isEmpty() || claimIds.stream().anyMatch(id -> !claims.containsKey(id))) {
            throw new IllegalArgumentException("claim forfeiture must name current allocations");
        }
        Map<SubjectId, ClaimAllocation> nextClaims = new HashMap<>(claims); claimIds.forEach(nextClaims::remove);
        Map<SubjectId, CustodyAccount> nextAccounts = new HashMap<>();
        accounts.forEach((id, account) -> {
            Map<SubjectId, Integer> accountClaims = new HashMap<>(account.claimQuantities()); claimIds.forEach(accountClaims::remove);
            nextAccounts.put(id, new CustodyAccount(account.id(), account.custody(), account.lotQuantities(), accountClaims));
        });
        Map<SubjectId, PhysicalStackBinding> nextBindings = new HashMap<>();
        bindings.forEach((id, binding) -> {
            Map<SubjectId, Integer> bindingClaims = new HashMap<>(binding.claimQuantities()); claimIds.forEach(bindingClaims::remove);
            nextBindings.put(id, new PhysicalStackBinding(binding.id(), binding.accountId(), binding.address(), binding.authorityEpoch(),
                    binding.itemKind(), binding.lotQuantities(), bindingClaims, binding.playerSaveFence()));
        });
        return new FungibleResourceLedger(lots, nextClaims, nextAccounts, nextBindings);
    }

    /** Exact zero-sum custody transfer; the caller names both lot and claim portions. */
    public FungibleResourceLedger transfer(SubjectId fromId, SubjectId toId, Map<SubjectId, Integer> lotQuantities,
                                            Map<SubjectId, Integer> claimQuantities) {
        CustodyAccount from = requireAccount(fromId); CustodyAccount to = requireAccount(toId);
        requireNonActorTransfer(from, to);
        requireNoPhysicalBinding(from.id(), "transfer"); requireNoPhysicalBinding(to.id(), "transfer");
        if (from.id().equals(to.id())) throw new IllegalArgumentException("fungible transfer requires distinct custody accounts");
        requireSubset(from.lotQuantities(), lotQuantities, "lot transfer"); requireOptionalSubset(from.claimQuantities(), claimQuantities, "claim transfer");
        if (sum(lotQuantities) != sum(claimQuantities) && !claimQuantities.isEmpty()) throw new IllegalArgumentException("claimed transfer must preserve exact resource quantity");
        Map<SubjectId, CustodyAccount> next = new HashMap<>(accounts);
        Map<SubjectId, Integer> remainingLots = subtract(from.lotQuantities(), lotQuantities);
        Map<SubjectId, Integer> remainingClaims = subtract(from.claimQuantities(), claimQuantities);
        if (remainingLots.isEmpty()) next.remove(from.id());
        else next.put(from.id(), new CustodyAccount(from.id(), from.custody(), remainingLots, remainingClaims));
        next.put(to.id(), accountWithAdded(to, lotQuantities, claimQuantities));
        return new FungibleResourceLedger(lots, claims, next, withoutBindingsFor(from.id()));
    }

    /** Opens a newly observed player/container/carrier account by moving exact extant quantities into it. */
    public FungibleResourceLedger transferToNewAccount(SubjectId fromId, CustodyAccount destination) {
        CustodyAccount from = requireAccount(fromId); Objects.requireNonNull(destination, "new custody account");
        requireNonActorTransfer(from, destination);
        return transferToNewAccountChecked(from, destination);
    }

    /**
     * One COLD work-owned container/actor handoff. The caller must have proved arrival at the
     * order's station; this ledger proves exact account/claim/custody conservation, never motion.
     * A loaded source or destination cannot use this operation while its physical bindings live.
     */
    public FungibleResourceLedger transferActorOrderCold(ActorContainerItemOrder order) {
        FungibleActorOrderTransfer.Accounts transfer = FungibleActorOrderTransfer.accounts(this, order);
        requireNoPhysicalBinding(transfer.source().id(), "actor item transfer");
        if (transfer.destinationExists()) {
            requireNoPhysicalBinding(transfer.destination().id(), "actor item transfer");
            return transferBetweenAccounts(transfer.source(), transfer.destination(), transfer.lots(), transfer.claims());
        }
        return transferToNewAccountChecked(transfer.source(), transfer.destination());
    }

    /**
     * The loaded counterpart consumes only an actual source-and-hand observation under both
     * explicit epochs. A job/intent owner must establish durable-before-effect and confirm the
     * physical postcondition before publishing the returned ledger.
     */
    public FungibleResourceLedger transferActorOrderObserved(ActorContainerItemOrder order,
                                                              long sourceEpoch, long destinationEpoch,
                                                              List<PhysicalStackBinding> remainingSource,
                                                              List<PhysicalStackBinding> destinationBindings) {
        FungibleActorOrderTransfer.Accounts transfer = FungibleActorOrderTransfer.accounts(this, order);
        FungibleActorOrderTransfer.requireDeclaredStationPort(this, order, transfer, destinationBindings);
        return transferObserved(transfer.source().id(), transfer.destination(), transfer.destinationExists(),
                sourceEpoch, destinationEpoch, transfer.lots(), transfer.claims(), remainingSource, destinationBindings);
    }

    /**
     * Turns the two *observed post-effect* physical layouts into exact lot/claim bindings.
     * The caller still owns durable-before-effect and must prove that the same witnessed
     * actor and container performed the handoff. This helper does not infer a resource owner
     * or accept an arbitrary third-party stack as the destination.
     */
    public FungibleResourceLedger transferActorOrderObservedStacks(ActorContainerItemOrder order,
                                                                    long sourceEpoch, long destinationEpoch,
                                                                    List<FungiblePhysicalObservation.Stack> remainingSource,
                                                                    List<FungiblePhysicalObservation.Stack> destination) {
        return FungibleActorOrderTransfer.observedStacks(this, order, sourceEpoch, destinationEpoch,
                remainingSource, destination);
    }

    private FungibleResourceLedger transferToNewAccountChecked(CustodyAccount from, CustodyAccount destination) {
        requireNoPhysicalBinding(from.id(), "transfer");
        if (accounts.containsKey(destination.id()) || from.id().equals(destination.id())) throw new IllegalArgumentException("new custody account identity is already live");
        requireSubset(from.lotQuantities(), destination.lotQuantities(), "new custody account lots");
        requireOptionalSubset(from.claimQuantities(), destination.claimQuantities(), "new custody account claims");
        Map<SubjectId, CustodyAccount> next = new HashMap<>(accounts);
        Map<SubjectId, Integer> remainingLots = subtract(from.lotQuantities(), destination.lotQuantities());
        Map<SubjectId, Integer> remainingClaims = subtract(from.claimQuantities(), destination.claimQuantities());
        if (remainingLots.isEmpty()) next.remove(from.id());
        else next.put(from.id(), new CustodyAccount(from.id(), from.custody(), remainingLots, remainingClaims));
        next.put(destination.id(), destination);
        return new FungibleResourceLedger(lots, claims, next, withoutBindingsFor(from.id()));
    }

    private FungibleResourceLedger transferBetweenAccounts(CustodyAccount from, CustodyAccount to,
                                                            Map<SubjectId, Integer> lotQuantities,
                                                            Map<SubjectId, Integer> claimQuantities) {
        if (from.id().equals(to.id())) throw new IllegalArgumentException("resource transfer requires distinct accounts");
        requireSubset(from.lotQuantities(), lotQuantities, "actor transfer lots");
        requireOptionalSubset(from.claimQuantities(), claimQuantities, "actor transfer claims");
        if (!claimQuantities.isEmpty() && sum(lotQuantities) != sum(claimQuantities))
            throw new IllegalArgumentException("actor transfer claims must preserve exact resource quantity");
        Map<SubjectId, CustodyAccount> next = new HashMap<>(accounts);
        Map<SubjectId, Integer> remainingLots = subtract(from.lotQuantities(), lotQuantities);
        Map<SubjectId, Integer> remainingClaims = subtract(from.claimQuantities(), claimQuantities);
        if (remainingLots.isEmpty()) next.remove(from.id());
        else next.put(from.id(), new CustodyAccount(from.id(), from.custody(), remainingLots, remainingClaims));
        next.put(to.id(), accountWithAdded(to, lotQuantities, claimQuantities));
        return new FungibleResourceLedger(lots, claims, next, bindings);
    }

    /** Reserves one COLD contract portion and immediately gives that same portion to a new owner. */
    public FungibleResourceLedger reserveThenTransferToNewAccount(ClaimAllocation claim, SubjectId fromId, CustodyAccount destination) {
        Objects.requireNonNull(claim, "cargo claim");
        return reserve(claim, fromId).transferToNewAccount(fromId, destination);
    }

    /**
     * Delivers one complete COLD cargo account into a container and discharges its shipment
     * claims.  A lot may change economic owner only after it is isolated in the cargo account:
     * otherwise a partial shipment could silently retitle the sender's retained portion.
     */
    public FungibleResourceLedger deliverCargoToContainer(SubjectId cargoAccountId, CustodyAccount destination, SubjectId destinationOwner) {
        CustodyAccount cargo = requireAccount(cargoAccountId); Objects.requireNonNull(destination, "cargo destination");
        if (!(cargo.custody() instanceof ResourceCustody.Cargo) || !(destination.custody() instanceof ResourceCustody.Container))
            throw new IllegalArgumentException("cargo delivery requires cargo and container custody");
        Objects.requireNonNull(destinationOwner, "cargo destination owner");
        requireNoPhysicalBinding(cargo.id(), "cargo delivery");
        CustodyAccount currentDestination = accounts.get(destination.id());
        if (cargo.id().equals(destination.id()) || currentDestination != null && !currentDestination.equals(destination)) {
            throw new IllegalArgumentException("cargo delivery has an invalid destination account");
        }
        if (currentDestination == null && (!destination.lotQuantities().equals(cargo.lotQuantities()) || !destination.claimQuantities().isEmpty())) {
            throw new IllegalArgumentException("new cargo destination does not retain exactly the delivered lots");
        }
        if (currentDestination != null) requireNoPhysicalBinding(currentDestination.id(), "cargo delivery");
        for (Map.Entry<SubjectId, Integer> entry : cargo.lotQuantities().entrySet()) {
            ResourceLot lot = requireLot(entry.getKey());
            boolean retitled = !lot.economicOwnerId().equals(destinationOwner);
            boolean elsewhere = accounts.values().stream().filter(account -> !account.id().equals(cargo.id()))
                    .anyMatch(account -> account.lotQuantities().containsKey(lot.id()));
            if (retitled && (entry.getValue() != lot.quantity() || elsewhere)) {
                throw new IllegalArgumentException("cargo delivery cannot retitle an unisolated lot");
            }
        }
        Map<SubjectId, ResourceLot> nextLots = new HashMap<>(lots);
        cargo.lotQuantities().keySet().forEach(lotId -> {
            ResourceLot lot = requireLot(lotId);
            if (!lot.economicOwnerId().equals(destinationOwner)) nextLots.put(lotId, lot.withEconomicOwner(destinationOwner));
        });
        Map<SubjectId, ClaimAllocation> nextClaims = new HashMap<>(claims);
        cargo.claimQuantities().keySet().forEach(nextClaims::remove);
        Map<SubjectId, CustodyAccount> nextAccounts = new HashMap<>(accounts);
        nextAccounts.remove(cargo.id());
        nextAccounts.put(destination.id(), currentDestination == null ? destination : accountWithAdded(currentDestination, cargo.lotQuantities(), Map.of()));
        return new FungibleResourceLedger(nextLots, nextClaims, nextAccounts, bindings);
    }

    /**
     * Folds a COLD cargo account into a receiver that is already HOT, replacing only its current
     * fenced layout. The receipt must cover both the retained receiver stock and the arrival, so
     * there is no interval in which either side becomes spendable COLD.
     */
    public FungibleResourceLedger deliverObservedCargoToBoundContainer(SubjectId cargoAccountId, SubjectId destinationAccountId,
                                                                        SubjectId destinationOwner, long authorityEpoch,
                                                                        List<FungiblePhysicalObservation.Stack> observed) {
        CustodyAccount cargo = requireAccount(cargoAccountId); CustodyAccount destination = requireAccount(destinationAccountId);
        if (!(cargo.custody() instanceof ResourceCustody.Cargo) || !(destination.custody() instanceof ResourceCustody.Container))
            throw new IllegalArgumentException("observed cargo delivery requires cargo and container custody");
        Objects.requireNonNull(destinationOwner, "observed cargo destination owner");
        requireNoPhysicalBinding(cargo.id(), "observed cargo delivery");
        List<PhysicalStackBinding> current = bindings.values().stream().filter(binding -> binding.accountId().equals(destination.id())).toList();
        if (authorityEpoch < 1 || current.isEmpty() || current.stream().anyMatch(binding -> binding.authorityEpoch() != authorityEpoch)) {
            throw new IllegalArgumentException("observed cargo delivery has no current receiver authority");
        }
        for (Map.Entry<SubjectId, Integer> entry : cargo.lotQuantities().entrySet()) {
            ResourceLot lot = requireLot(entry.getKey());
            boolean retitled = !lot.economicOwnerId().equals(destinationOwner);
            boolean elsewhere = accounts.values().stream().filter(account -> !account.id().equals(cargo.id()))
                    .anyMatch(account -> account.lotQuantities().containsKey(lot.id()));
            if (retitled && (entry.getValue() != lot.quantity() || elsewhere)) {
                throw new IllegalArgumentException("observed cargo delivery cannot retitle an unisolated lot");
            }
        }
        Map<SubjectId, ResourceLot> nextLots = new HashMap<>(lots);
        cargo.lotQuantities().keySet().forEach(lotId -> {
            ResourceLot lot = requireLot(lotId);
            if (!lot.economicOwnerId().equals(destinationOwner)) nextLots.put(lotId, lot.withEconomicOwner(destinationOwner));
        });
        Map<SubjectId, ClaimAllocation> nextClaims = new HashMap<>(claims); cargo.claimQuantities().keySet().forEach(nextClaims::remove);
        Map<SubjectId, CustodyAccount> nextAccounts = new HashMap<>(accounts); nextAccounts.remove(cargo.id());
        nextAccounts.put(destination.id(), accountWithAdded(destination, cargo.lotQuantities(), Map.of()));
        FungibleResourceLedger combined = new FungibleResourceLedger(nextLots, nextClaims, nextAccounts, withoutBindingsFor(bindings, destination.id()));
        return combined.rebind(destination.id(), authorityEpoch, FungiblePhysicalObservation.bind(combined, destination.id(), authorityEpoch, observed));
    }

    /**
     * Commits one observed partial physical handoff as one canonical transaction. The source
     * fence and both replacement layouts are checked together, so releasing a visible stack
     * cannot open an interim COLD-spending window before its player/hopper/drop custody exists.
     */
    public FungibleResourceLedger transferObservedToNewAccount(SubjectId fromId, CustodyAccount destination, long sourceEpoch,
                                                               long destinationEpoch, Map<SubjectId, Integer> lotQuantities,
                                                               Map<SubjectId, Integer> claimQuantities,
                                                               List<PhysicalStackBinding> remainingSource,
                                                               List<PhysicalStackBinding> destinationBindings) {
        requireNonActorTransfer(requireAccount(fromId), destination);
        return transferObserved(fromId, destination, false, sourceEpoch, destinationEpoch, lotQuantities, claimQuantities,
                remainingSource, destinationBindings);
    }

    /**
     * Commits a loaded-source removal into an unbound cargo account.  The source's replacement
     * HOT layout is fenced in the same transaction; cargo is deliberately COLD only after that
     * observed physical removal has become durable.
     */
    public FungibleResourceLedger transferObservedToColdNewAccount(SubjectId fromId, CustodyAccount destination, long sourceEpoch,
                                                                   Map<SubjectId, Integer> lotQuantities,
                                                                   Map<SubjectId, Integer> claimQuantities,
                                                                   List<PhysicalStackBinding> remainingSource) {
        CustodyAccount from = requireAccount(fromId); Objects.requireNonNull(destination, "observed cold destination");
        requireNonActorTransfer(from, destination);
        Objects.requireNonNull(remainingSource, "observed cold source layout");
        if (from.id().equals(destination.id()) || accounts.containsKey(destination.id()) || sourceEpoch < 1
                || !(destination.custody() instanceof ResourceCustody.Cargo)) {
            throw new IllegalArgumentException("observed cold transfer has an invalid destination or epoch");
        }
        List<PhysicalStackBinding> current = bindings.values().stream().filter(binding -> binding.accountId().equals(from.id())).toList();
        if (current.isEmpty() || current.stream().anyMatch(binding -> binding.authorityEpoch() != sourceEpoch)) {
            throw new IllegalArgumentException("observed cold transfer does not own the current source binding");
        }
        requireSubset(from.lotQuantities(), lotQuantities, "observed cold transfer lots");
        requireOptionalSubset(from.claimQuantities(), claimQuantities, "observed cold transfer claims");
        if (!claimQuantities.isEmpty() && sum(lotQuantities) != sum(claimQuantities)) {
            throw new IllegalArgumentException("observed cold claimed transfer must preserve exact quantity");
        }
        if (!destination.lotQuantities().equals(lotQuantities) || !destination.claimQuantities().equals(claimQuantities)) {
            throw new IllegalArgumentException("observed cold destination does not retain exactly the transferred quantities");
        }
        Map<SubjectId, Integer> remainingLots = subtract(from.lotQuantities(), lotQuantities);
        Map<SubjectId, Integer> remainingClaims = subtract(from.claimQuantities(), claimQuantities);
        if (remainingLots.isEmpty() != remainingSource.isEmpty()) {
            throw new IllegalArgumentException("observed cold source layout does not match remaining custody");
        }
        requireBindings(remainingSource, from.id(), sourceEpoch, "observed cold source layout");
        if (!boundQuantities(remainingSource, true).equals(remainingLots)
                || !boundQuantities(remainingSource, false).equals(remainingClaims)) {
            throw new IllegalArgumentException("observed cold source layout does not exactly account for its custody");
        }
        Map<SubjectId, CustodyAccount> nextAccounts = new HashMap<>(accounts);
        if (remainingLots.isEmpty()) nextAccounts.remove(from.id());
        else nextAccounts.put(from.id(), new CustodyAccount(from.id(), from.custody(), remainingLots, remainingClaims));
        nextAccounts.put(destination.id(), destination);
        Map<SubjectId, PhysicalStackBinding> nextBindings = withoutBindingsFor(from.id());
        for (PhysicalStackBinding binding : remainingSource) {
            if (nextBindings.put(binding.id(), binding) != null) throw new IllegalArgumentException("observed cold source binding identity is already live");
        }
        return new FungibleResourceLedger(lots, claims, nextAccounts, nextBindings);
    }

    /**
     * Reconciles a physical transfer into an already-known custody account without releasing
     * either side to COLD ownership between the source and destination observations.
     */
    public FungibleResourceLedger transferObservedToExistingAccount(SubjectId fromId, SubjectId destinationId, long sourceEpoch,
                                                                    long destinationEpoch, Map<SubjectId, Integer> lotQuantities,
                                                                    Map<SubjectId, Integer> claimQuantities,
                                                                    List<PhysicalStackBinding> remainingSource,
                                                                    List<PhysicalStackBinding> destinationBindings) {
        CustodyAccount destination = requireAccount(destinationId);
        requireNonActorTransfer(requireAccount(fromId), destination);
        return transferObserved(fromId, destination, true, sourceEpoch, destinationEpoch, lotQuantities,
                claimQuantities, remainingSource, destinationBindings);
    }

    private static void requireNonActorTransfer(CustodyAccount source, CustodyAccount destination) {
        if (source.custody() instanceof ResourceCustody.Actor || destination.custody() instanceof ResourceCustody.Actor)
            throw new IllegalArgumentException("actor-held resources require their typed work and handoff owner");
    }

    private FungibleResourceLedger transferObserved(SubjectId fromId, CustodyAccount destination, boolean destinationExists,
                                                     long sourceEpoch, long destinationEpoch,
                                                     Map<SubjectId, Integer> lotQuantities,
                                                     Map<SubjectId, Integer> claimQuantities,
                                                     List<PhysicalStackBinding> remainingSource,
                                                     List<PhysicalStackBinding> destinationBindings) {
        CustodyAccount from = requireAccount(fromId); Objects.requireNonNull(destination, "observed destination account");
        Objects.requireNonNull(remainingSource, "observed remaining source layout"); Objects.requireNonNull(destinationBindings, "observed destination layout");
        if (from.id().equals(destination.id()) || accounts.containsKey(destination.id()) != destinationExists
                || sourceEpoch < 1 || destinationEpoch < 1) {
            throw new IllegalArgumentException("observed handoff has an invalid destination account or epoch");
        }
        List<PhysicalStackBinding> current = bindings.values().stream().filter(binding -> binding.accountId().equals(from.id())).toList();
        if (current.isEmpty() || current.stream().anyMatch(binding -> binding.authorityEpoch() != sourceEpoch)) {
            throw new IllegalArgumentException("observed handoff does not own the current source binding");
        }
        List<PhysicalStackBinding> destinationCurrent = bindings.values().stream()
                .filter(binding -> binding.accountId().equals(destination.id())).toList();
        if (destinationCurrent.stream().anyMatch(binding -> binding.authorityEpoch() != destinationEpoch)) {
            throw new IllegalArgumentException("observed handoff does not own the current destination binding");
        }
        requireSubset(from.lotQuantities(), lotQuantities, "observed transfer lots"); requireOptionalSubset(from.claimQuantities(), claimQuantities, "observed transfer claims");
        if (!claimQuantities.isEmpty() && sum(lotQuantities) != sum(claimQuantities)) throw new IllegalArgumentException("observed claimed transfer must preserve exact quantity");
        CustodyAccount finalDestination = destinationExists
                ? accountWithAdded(destination, lotQuantities, claimQuantities)
                : destination;
        if (!destinationExists && (!destination.lotQuantities().equals(lotQuantities)
                || !destination.claimQuantities().equals(claimQuantities))) {
            throw new IllegalArgumentException("observed destination does not retain exactly the transferred quantities");
        }
        Map<SubjectId, Integer> remainingLots = subtract(from.lotQuantities(), lotQuantities);
        Map<SubjectId, Integer> remainingClaims = subtract(from.claimQuantities(), claimQuantities);
        if (remainingLots.isEmpty() != remainingSource.isEmpty()) throw new IllegalArgumentException("observed source layout does not match its remaining custody");
        requireBindings(remainingSource, from.id(), sourceEpoch, "observed source layout");
        requireBindings(destinationBindings, destination.id(), destinationEpoch, "observed destination layout");
        if (!boundQuantities(remainingSource, true).equals(remainingLots) || !boundQuantities(remainingSource, false).equals(remainingClaims)
                || !boundQuantities(destinationBindings, true).equals(finalDestination.lotQuantities())
                || !boundQuantities(destinationBindings, false).equals(finalDestination.claimQuantities())) {
            throw new IllegalArgumentException("observed handoff layout does not exactly account for its custody");
        }
        Map<SubjectId, CustodyAccount> nextAccounts = new HashMap<>(accounts);
        if (remainingLots.isEmpty()) nextAccounts.remove(from.id());
        else nextAccounts.put(from.id(), new CustodyAccount(from.id(), from.custody(), remainingLots, remainingClaims));
        nextAccounts.put(destination.id(), finalDestination);
        Map<SubjectId, PhysicalStackBinding> nextBindings = withoutBindingsFor(from.id());
        if (destinationExists) nextBindings = withoutBindingsFor(nextBindings, destination.id());
        for (PhysicalStackBinding binding : remainingSource) if (nextBindings.put(binding.id(), binding) != null) throw new IllegalArgumentException("observed source binding identity is already live");
        for (PhysicalStackBinding binding : destinationBindings) if (nextBindings.put(binding.id(), binding) != null) throw new IllegalArgumentException("observed destination binding identity is already live");
        return new FungibleResourceLedger(lots, claims, nextAccounts, nextBindings);
    }

    /** Changes canonical lot lineage while retaining total resource quantity and all reservations. */
    public FungibleResourceLedger split(SubjectId accountId, SubjectId sourceLotId, ResourceLot child, int quantity) {
        CustodyAccount account = requireAccount(accountId); ResourceLot source = requireLot(sourceLotId);
        requireNoPhysicalBinding(account.id(), "split");
        if (lots.containsKey(child.id()) || !source.economicOwnerId().equals(child.economicOwnerId()) || !source.itemKind().equals(child.itemKind())
                || !source.provenance().equals(child.provenance()) || child.quantity() != quantity || quantity < 1 || quantity >= source.quantity()
                || !source.splitChild(child.id(), quantity).equals(child) || account.lotQuantities().getOrDefault(sourceLotId, 0) < quantity) {
            throw new IllegalArgumentException("fungible lot split is not an exact lineage-preserving account mutation");
        }
        Map<SubjectId, ResourceLot> nextLots = new HashMap<>(lots); nextLots.put(sourceLotId, source.withQuantity(source.quantity() - quantity)); nextLots.put(child.id(), child);
        Map<SubjectId, Integer> nextQuantities = new HashMap<>(account.lotQuantities()); nextQuantities.merge(sourceLotId, -quantity, Integer::sum); nextQuantities.put(child.id(), quantity);
        return withAccount(new CustodyAccount(account.id(), account.custody(), nextQuantities, account.claimQuantities()), claims, bindings, nextLots);
    }

    /** Merges two co-located interchangeable lots into fresh bounded lineage without changing any allocation. */
    public FungibleResourceLedger merge(SubjectId accountId, SubjectId leftId, SubjectId rightId, ResourceLot merged) {
        CustodyAccount account = requireAccount(accountId); ResourceLot left = requireLot(leftId); ResourceLot right = requireLot(rightId);
        requireNoPhysicalBinding(account.id(), "merge");
        if (leftId.equals(rightId) || lots.containsKey(merged.id()) || !left.economicOwnerId().equals(right.economicOwnerId())
                || !left.itemKind().equals(right.itemKind()) || !left.provenance().equals(right.provenance())
                || !merged.economicOwnerId().equals(left.economicOwnerId()) || !merged.itemKind().equals(left.itemKind())
                || !merged.provenance().equals(left.provenance()) || merged.quantity() != left.quantity() + right.quantity()
                || account.lotQuantities().getOrDefault(leftId, 0) != left.quantity() || account.lotQuantities().getOrDefault(rightId, 0) != right.quantity()) {
            throw new IllegalArgumentException("fungible lot merge is not an exact co-located mutation");
        }
        Map<SubjectId, ResourceLot> nextLots = new HashMap<>(lots); nextLots.remove(leftId); nextLots.remove(rightId); nextLots.put(merged.id(), merged);
        Map<SubjectId, Integer> nextQuantities = new HashMap<>(account.lotQuantities()); nextQuantities.remove(leftId); nextQuantities.remove(rightId); nextQuantities.put(merged.id(), merged.quantity());
        return withAccount(new CustodyAccount(account.id(), account.custody(), nextQuantities, account.claimQuantities()), claims, bindings, nextLots);
    }

    /** Replaces only one account's current HOT stack layout after a matching fenced observation. */
    public FungibleResourceLedger rebind(SubjectId accountId, long authorityEpoch, List<PhysicalStackBinding> replacements) {
        CustodyAccount account = requireAccount(accountId); Objects.requireNonNull(replacements, "physical binding replacements");
        if (replacements.isEmpty() || replacements.stream().anyMatch(binding -> !binding.accountId().equals(accountId) || binding.authorityEpoch() != authorityEpoch)) {
            throw new IllegalArgumentException("physical bindings do not match their current custody authority");
        }
        if (bindings.values().stream().anyMatch(binding -> binding.accountId().equals(accountId) && binding.authorityEpoch() != authorityEpoch)) {
            throw new IllegalArgumentException("physical bindings are stale for the current custody authority");
        }
        Map<SubjectId, PhysicalStackBinding> next = new HashMap<>(bindings);
        bindings.values().stream().filter(binding -> binding.accountId().equals(account.id())).map(PhysicalStackBinding::id).forEach(next::remove);
        for (PhysicalStackBinding binding : replacements) {
            if (next.put(binding.id(), binding) != null) throw new IllegalArgumentException("physical stack binding identity is already live");
        }
        return new FungibleResourceLedger(lots, claims, accounts, next);
    }

    /** Releases only the current epoch after its durable physical checkpoint; a stale lease cannot reopen COLD spending. */
    public FungibleResourceLedger releaseBindings(SubjectId accountId, long authorityEpoch) {
        requireAccount(accountId);
        List<PhysicalStackBinding> current = bindings.values().stream().filter(binding -> binding.accountId().equals(accountId)).toList();
        if (current.isEmpty() || current.stream().anyMatch(binding -> binding.authorityEpoch() != authorityEpoch)) {
            throw new IllegalArgumentException("physical custody release does not match the current authority epoch");
        }
        Map<SubjectId, PhysicalStackBinding> next = withoutBindingsFor(accountId);
        return new FungibleResourceLedger(lots, claims, accounts, next);
    }

    /**
     * Records the one durable canonical-first player-save gap for a freshly observed player
     * handoff.  This token is not a generic restart repair permission: it is meaningful only
     * while this exact HOT binding still owns the exact player slot.
     */
    public FungibleResourceLedger fenceUnresolvedPlayerSave(SubjectId accountId, String fence) {
        CustodyAccount account = requireAccount(accountId);
        if (!(account.custody() instanceof ResourceCustody.Player player) || fence == null || fence.isEmpty()) {
            throw new IllegalArgumentException("player save fence requires one player custody account");
        }
        List<PhysicalStackBinding> current = bindings.values().stream().filter(binding -> binding.accountId().equals(accountId)).toList();
        if (current.size() != 1 || !(current.getFirst().address() instanceof PhysicalStackAddress.PlayerSlot slot)
                || !slot.playerId().equals(player.playerId()) || !current.getFirst().playerSaveFence().isEmpty()) {
            throw new IllegalArgumentException("player save fence requires one unfenced current player binding");
        }
        PhysicalStackBinding prior = current.getFirst();
        PhysicalStackBinding fenced = new PhysicalStackBinding(prior.id(), prior.accountId(), prior.address(), prior.authorityEpoch(),
                prior.itemKind(), prior.lotQuantities(), prior.claimQuantities(), fence);
        Map<SubjectId, PhysicalStackBinding> next = new HashMap<>(bindings); next.put(fenced.id(), fenced);
        return new FungibleResourceLedger(lots, claims, accounts, next);
    }

    /** A typed destruction sink removes only the named custody portions; nothing is rolled back or minted. */
    public FungibleResourceLedger destroy(SubjectId accountId, Map<SubjectId, Integer> lotQuantities, Map<SubjectId, Integer> claimQuantities) {
        CustodyAccount account = requireAccount(accountId); requireSubset(account.lotQuantities(), lotQuantities, "destruction lots");
        requireNoPhysicalBinding(account.id(), "destroy");
        requireOptionalSubset(account.claimQuantities(), claimQuantities, "destruction claims");
        if (!claimQuantities.isEmpty() && sum(lotQuantities) != sum(claimQuantities)) throw new IllegalArgumentException("claimed destruction must consume the same exact quantity");
        Map<SubjectId, ResourceLot> nextLots = new HashMap<>(lots); Map<SubjectId, ClaimAllocation> nextClaims = new HashMap<>(claims);
        lotQuantities.forEach((id, quantity) -> { ResourceLot lot = requireLot(id); int remaining = lot.quantity() - quantity; if (remaining == 0) nextLots.remove(id); else nextLots.put(id, lot.withQuantity(remaining)); });
        claimQuantities.forEach((id, quantity) -> {
            ClaimAllocation claim = claims.get(id); int remaining = claim.quantity() - quantity;
            if (!claim.lotQuantities().isEmpty() && remaining != 0) throw new IllegalArgumentException("pinned claim cannot be partly destroyed");
            if (remaining == 0) nextClaims.remove(id);
            else nextClaims.put(id, claim.withQuantity(remaining));
        });
        Map<SubjectId, Integer> nextLotsAtAccount = subtract(account.lotQuantities(), lotQuantities); Map<SubjectId, Integer> nextClaimsAtAccount = subtract(account.claimQuantities(), claimQuantities);
        Map<SubjectId, CustodyAccount> nextAccounts = new HashMap<>(accounts);
        if (nextLotsAtAccount.isEmpty()) nextAccounts.remove(account.id());
        else nextAccounts.put(account.id(), new CustodyAccount(account.id(), account.custody(), nextLotsAtAccount, nextClaimsAtAccount));
        Map<SubjectId, PhysicalStackBinding> nextBindings = new HashMap<>(bindings);
        bindings.values().stream().filter(binding -> binding.accountId().equals(account.id())).map(PhysicalStackBinding::id).forEach(nextBindings::remove);
        return new FungibleResourceLedger(nextLots, nextClaims, nextAccounts, nextBindings);
    }

    /** Consumes a claimed HOT portion and atomically replaces its observed remaining layout. */
    public FungibleResourceLedger destroyObserved(SubjectId accountId, long authorityEpoch, Map<SubjectId, Integer> lotQuantities,
                                                  Map<SubjectId, Integer> claimQuantities, List<FungiblePhysicalObservation.Stack> remaining) {
        return FungibleObservedStockTransitions.destroy(this, accountId, authorityEpoch,
                lotQuantities, claimQuantities, remaining);
    }

    /**
     * Accounts an observed external stock exit after the owning process has released every
     * affected claim. Unlike recipe consumption, no claim or player-inventory binding is
     * required: the physically witnessed container remainder is the postcondition. This
     * method never guesses that an unexplained layout delta was caused by a player.
     */
    public FungibleResourceLedger departObserved(SubjectId accountId, long authorityEpoch,
                                                 Map<SubjectId, Integer> departedLots,
                                                 List<FungiblePhysicalObservation.Stack> remaining) {
        return FungibleObservedStockTransitions.depart(this, accountId, authorityEpoch,
                departedLots, remaining);
    }

    /**
     * Accepts an observed player contribution into one already-owned HOT depot. The new
     * lot is a settlement gift, not a resurrection of a previously withdrawn lot; its fresh
     * identity and economic owner are checked before the complete post-click layout is bound.
     */
    public FungibleResourceLedger contributeObserved(SubjectId accountId, long authorityEpoch,
                                                     ResourceLot contribution,
                                                     List<FungiblePhysicalObservation.Stack> observed) {
        return FungibleObservedStockTransitions.contribute(this, accountId, authorityEpoch,
                contribution, observed);
    }

    /**
     * Applies one declared COLD recipe inside its sole account.  The caller owns recipe
     * eligibility; this ledger only proves that the named input custody and allocations are
     * consumed exactly before the one new output lot exists.
     */
    public FungibleResourceLedger transformCold(SubjectId accountId, Map<SubjectId, Integer> inputLots,
                                                Map<SubjectId, Integer> inputClaims, ResourceLot output) {
        CustodyAccount account = requireAccount(accountId); Objects.requireNonNull(output, "recipe output lot");
        requireNoPhysicalBinding(account.id(), "recipe transformation");
        requireSubset(account.lotQuantities(), inputLots, "recipe input lots");
        requireOptionalSubset(account.claimQuantities(), inputClaims, "recipe input claims");
        requireClaimsCoveredByLots(inputLots, inputClaims, "recipe transformation");
        if (lots.containsKey(output.id()) || sum(inputLots) != output.quantity()) {
            throw new IllegalArgumentException("recipe transformation does not preserve its exact quantity");
        }
        if (inputLots.entrySet().stream().map(entry -> requireLot(entry.getKey()).economicOwnerId()).distinct().count() != 1
                || !requireLot(inputLots.keySet().iterator().next()).economicOwnerId().equals(output.economicOwnerId())) {
            throw new IllegalArgumentException("recipe transformation crosses economic ownership");
        }
        if (!inputClaims.isEmpty() && sum(inputClaims) != sum(inputLots)) {
            throw new IllegalArgumentException("recipe transformation leaves a claimed input portion behind");
        }
        Map<SubjectId, ResourceLot> nextLots = new HashMap<>(lots);
        inputLots.forEach((id, quantity) -> {
            ResourceLot lot = requireLot(id); int remaining = lot.quantity() - quantity;
            if (remaining == 0) nextLots.remove(id); else nextLots.put(id, lot.withQuantity(remaining));
        });
        nextLots.put(output.id(), output);
        Map<SubjectId, ClaimAllocation> nextClaims = new HashMap<>(claims);
        inputClaims.forEach((id, quantity) -> {
            ClaimAllocation claim = claims.get(id); int remaining = claim.quantity() - quantity;
            if (!claim.lotQuantities().isEmpty() && (remaining != 0 || !claim.lotQuantities().equals(inputLots))) {
                throw new IllegalArgumentException("pinned recipe claim must consume its complete exact lot map");
            }
            if (remaining == 0) nextClaims.remove(id);
            else nextClaims.put(id, claim.withQuantity(remaining));
        });
        Map<SubjectId, Integer> nextAccountLots = subtract(account.lotQuantities(), inputLots);
        nextAccountLots.put(output.id(), output.quantity());
        Map<SubjectId, Integer> nextAccountClaims = subtract(account.claimQuantities(), inputClaims);
        Map<SubjectId, CustodyAccount> nextAccounts = new HashMap<>(accounts);
        nextAccounts.put(account.id(), new CustodyAccount(account.id(), account.custody(), nextAccountLots, nextAccountClaims));
        return new FungibleResourceLedger(nextLots, nextClaims, nextAccounts, bindings);
    }

    /**
     * Records an observed recipe result under the same physical epoch. Intermediate immutable
     * values are private to this transition: no released account is published and neither the
     * recipe nor the adapter may spend its stock between consumption and rebinding.
     * The caller supplies the complete actual post-effect layout, not a projected replacement.
     */
    public FungibleResourceLedger transformObserved(SubjectId accountId, long authorityEpoch,
                                                     Map<SubjectId, Integer> inputLots, Map<SubjectId, Integer> inputClaims,
                                                     ResourceLot output, List<FungiblePhysicalObservation.Stack> observed) {
        if (inputClaims.isEmpty() || sum(inputClaims) != sum(inputLots)) {
            throw new IllegalArgumentException("observed recipe requires its complete claimed input portion");
        }
        FungibleResourceLedger transformed = releaseBindings(accountId, authorityEpoch)
                .transformCold(accountId, inputLots, inputClaims, output);
        return transformed.rebind(accountId, authorityEpoch,
                FungiblePhysicalObservation.bind(transformed, accountId, authorityEpoch, observed));
    }

    public int totalQuantity(SubjectId owner, String kind) {
        return lots.values().stream().filter(lot -> lot.economicOwnerId().equals(owner) && lot.itemKind().equals(kind)).mapToInt(ResourceLot::quantity).sum();
    }

    private static void validateAccounts(Map<SubjectId, ResourceLot> lots, Map<SubjectId, ClaimAllocation> claims,
                                         Map<SubjectId, CustodyAccount> accounts) {
        Map<SubjectId, Integer> lotTotals = new HashMap<>(); Map<SubjectId, Integer> claimTotals = new HashMap<>();
        for (CustodyAccount account : accounts.values()) {
            account.lotQuantities().forEach((id, quantity) -> { if (!lots.containsKey(id)) throw new IllegalArgumentException("custody account references an unknown lot"); lotTotals.merge(id, quantity, Integer::sum); });
            account.claimQuantities().forEach((id, quantity) -> {
                ClaimAllocation claim = claims.get(id);
                if (claim == null) throw new IllegalArgumentException("custody account references an unknown claim");
                claimTotals.merge(id, quantity, Integer::sum);
                int compatible = account.lotQuantities().entrySet().stream().filter(entry -> {
                    ResourceLot lot = lots.get(entry.getKey());
                    return lot.economicOwnerId().equals(claim.economicOwnerId()) && lot.itemKind().equals(claim.itemKind());
                }).mapToInt(Map.Entry::getValue).sum();
                if (quantity > compatible) throw new IllegalArgumentException("claim allocation exceeds compatible account stock");
            });
        }
        lots.forEach((id, lot) -> { if (lotTotals.getOrDefault(id, 0) != lot.quantity()) throw new IllegalArgumentException("resource lot must have exact canonical custody"); });
        claims.forEach((id, claim) -> { if (claimTotals.getOrDefault(id, 0) != claim.quantity()) throw new IllegalArgumentException("claim allocation must have exact canonical custody"); });
        for (CustodyAccount account : accounts.values()) {
            Map<String, Integer> claimedByKind = new HashMap<>(); Map<String, Integer> stockByKind = new HashMap<>();
            account.claimQuantities().forEach((id, quantity) -> { ClaimAllocation claim = claims.get(id); claimedByKind.merge(claim.economicOwnerId().value() + "|" + claim.itemKind(), quantity, Integer::sum); });
            account.lotQuantities().forEach((id, quantity) -> { ResourceLot lot = lots.get(id); stockByKind.merge(lot.economicOwnerId().value() + "|" + lot.itemKind(), quantity, Integer::sum); });
            claimedByKind.forEach((key, quantity) -> { if (quantity > stockByKind.getOrDefault(key, 0)) throw new IllegalArgumentException("claim allocations exceed exact account stock"); });
            Map<SubjectId, Integer> pinnedByLot = new HashMap<>();
            account.claimQuantities().forEach((claimId, accountQuantity) -> {
                ClaimAllocation claim = claims.get(claimId);
                if (claim.lotQuantities().isEmpty()) return;
                if (accountQuantity != claim.quantity()) throw new IllegalArgumentException("pinned claim cannot split across custody accounts");
                claim.lotQuantities().forEach((lotId, quantity) -> {
                    ResourceLot lot = lots.get(lotId);
                    if (lot == null || !lot.economicOwnerId().equals(claim.economicOwnerId()) || !lot.itemKind().equals(claim.itemKind())) {
                        throw new IllegalArgumentException("pinned claim references a foreign lot");
                    }
                    pinnedByLot.merge(lotId, quantity, Integer::sum);
                });
            });
            pinnedByLot.forEach((lotId, quantity) -> {
                if (quantity > account.lotQuantities().getOrDefault(lotId, 0)) {
                    throw new IllegalArgumentException("pinned claims exceed their exact account lot stock");
                }
            });
        }
    }

    private static void validateBindings(Map<SubjectId, ResourceLot> lots, Map<SubjectId, ClaimAllocation> claims,
                                         Map<SubjectId, CustodyAccount> accounts,
                                         Map<SubjectId, PhysicalStackBinding> bindings) {
        Map<PhysicalStackAddress, SubjectId> addresses = new HashMap<>(); Map<SubjectId, Map<SubjectId, Integer>> boundLots = new HashMap<>();
        Map<SubjectId, Map<SubjectId, Integer>> boundClaims = new HashMap<>();
        for (PhysicalStackBinding binding : bindings.values()) {
            CustodyAccount account = accounts.get(binding.accountId());
            if (account == null || addresses.put(binding.address(), binding.id()) != null || binding.quantity() > 64) throw new IllegalArgumentException("physical stack binding is not one unique current stack");
            if (!addressMatchesCustody(binding.address(), account.custody())) {
                throw new IllegalArgumentException("physical stack address disagrees with its canonical custody owner");
            }
            binding.lotQuantities().forEach((id, quantity) -> { ResourceLot lot = lots.get(id); if (lot == null || !lot.itemKind().equals(binding.itemKind())) throw new IllegalArgumentException("physical stack binding has incompatible lot evidence");
                boundLots.computeIfAbsent(account.id(), ignored -> new HashMap<>()).merge(id, quantity, Integer::sum); });
            binding.claimQuantities().forEach((id, quantity) -> {
                ClaimAllocation claim = claims.get(id);
                if (claim == null || !claim.itemKind().equals(binding.itemKind())) {
                    throw new IllegalArgumentException("physical stack binding has incompatible claim evidence");
                }
                boundClaims.computeIfAbsent(account.id(), ignored -> new HashMap<>()).merge(id, quantity, Integer::sum);
            });
            int boundClaimsAtStack = binding.claimQuantities().values().stream().mapToInt(Integer::intValue).sum();
            if (boundClaimsAtStack > binding.quantity()) {
                throw new IllegalArgumentException("physical stack binding claims exceed its exact stack quantity");
            }
        }
        boundLots.forEach((account, quantities) -> {
            if (!accounts.get(account).lotQuantities().equals(quantities)) {
                throw new IllegalArgumentException("physical stack bindings must cover their complete active account");
            }
            if (!accounts.get(account).claimQuantities().equals(boundClaims.getOrDefault(account, Map.of()))) {
                throw new IllegalArgumentException("physical stack bindings must cover their complete active claim allocation");
            }
        });
    }

    private static boolean addressMatchesCustody(PhysicalStackAddress address, ResourceCustody custody) {
        return switch (address) {
            case PhysicalStackAddress.ContainerSlot slot -> custody instanceof ResourceCustody.Container owner
                    && owner.containerId().equals(slot.slot().containerId());
            case PhysicalStackAddress.PlayerSlot slot -> custody instanceof ResourceCustody.Player owner
                    && owner.playerId().equals(slot.playerId());
            // The hopper carrier UUID is a separate physical admission claim;
            // its block position alone cannot establish that UUID here.
            case PhysicalStackAddress.HopperSlot ignored -> custody instanceof ResourceCustody.WorldCarrier;
            case PhysicalStackAddress.WorldEntity entity -> custody instanceof ResourceCustody.WorldCarrier owner
                    && owner.carrierId().equals(entity.entityId());
            case PhysicalStackAddress.ActorHand hand -> custody instanceof ResourceCustody.Actor owner
                    && owner.actorId().equals(hand.actorId());
        };
    }

    private ResourceLot requireLot(SubjectId id) { ResourceLot lot = lots.get(Objects.requireNonNull(id, "resource lot")); if (lot == null) throw new IllegalArgumentException("unknown resource lot"); return lot; }
    private CustodyAccount requireAccount(SubjectId id) { CustodyAccount account = accounts.get(Objects.requireNonNull(id, "custody account")); if (account == null) throw new IllegalArgumentException("unknown custody account"); return account; }

    /** Current unclaimed balance under the same owner/kind rule used by reservation. */
    public int unclaimedQuantity(SubjectId accountId, SubjectId economicOwnerId, String itemKind) {
        return unclaimedQuantity(requireAccount(accountId), economicOwnerId, itemKind);
    }

    private int unclaimedQuantity(CustodyAccount account, SubjectId economicOwnerId, String itemKind) {
        Objects.requireNonNull(economicOwnerId, "resource economic owner");
        Objects.requireNonNull(itemKind, "resource item kind");
        int available = account.lotQuantities().entrySet().stream().filter(entry -> {
            ResourceLot lot = requireLot(entry.getKey());
            return lot.economicOwnerId().equals(economicOwnerId) && lot.itemKind().equals(itemKind);
        }).mapToInt(Map.Entry::getValue).sum();
        int claimed = account.claimQuantities().entrySet().stream().filter(entry -> {
            ClaimAllocation current = claims.get(entry.getKey());
            return current.economicOwnerId().equals(economicOwnerId) && current.itemKind().equals(itemKind);
        }).mapToInt(Map.Entry::getValue).sum();
        return Math.subtractExact(available, claimed);
    }

    private Reservation reserve(CustodyAccount account, ClaimAllocation claim) {
        if (claims.containsKey(claim.id()) || account.claimQuantities().containsKey(claim.id())
                || unclaimedQuantity(account, claim.economicOwnerId(), claim.itemKind()) < claim.quantity()) {
            throw new IllegalArgumentException("claim allocation is not backed by one exact account balance");
        }
        Map<SubjectId, ClaimAllocation> nextClaims = new HashMap<>(claims); nextClaims.put(claim.id(), claim);
        Map<SubjectId, Integer> quantities = new HashMap<>(account.claimQuantities()); quantities.put(claim.id(), claim.quantity());
        return new Reservation(new CustodyAccount(account.id(), account.custody(), account.lotQuantities(), quantities), nextClaims);
    }
    private FungibleResourceLedger withAccount(CustodyAccount account, Map<SubjectId, ClaimAllocation> nextClaims, Map<SubjectId, PhysicalStackBinding> nextBindings) { return withAccount(account, nextClaims, nextBindings, lots); }
    private FungibleResourceLedger withAccount(CustodyAccount account, Map<SubjectId, ClaimAllocation> nextClaims, Map<SubjectId, PhysicalStackBinding> nextBindings, Map<SubjectId, ResourceLot> nextLots) {
        Map<SubjectId, CustodyAccount> next = new HashMap<>(accounts); next.put(account.id(), account); return new FungibleResourceLedger(nextLots, nextClaims, next, nextBindings);
    }
    private Map<SubjectId, PhysicalStackBinding> withoutBindingsFor(SubjectId accountId) {
        return withoutBindingsFor(bindings, accountId);
    }
    private static Map<SubjectId, PhysicalStackBinding> withoutBindingsFor(Map<SubjectId, PhysicalStackBinding> source,
                                                                            SubjectId accountId) {
        Map<SubjectId, PhysicalStackBinding> next = new HashMap<>(source);
        source.values().stream().filter(binding -> binding.accountId().equals(accountId)).map(PhysicalStackBinding::id).forEach(next::remove);
        return next;
    }
    private void requireNoPhysicalBinding(SubjectId accountId, String operation) {
        if (bindings.values().stream().anyMatch(binding -> binding.accountId().equals(accountId))) {
            throw new IllegalStateException("fungible " + operation + " is fenced by active physical custody");
        }
    }
    private static void requireBindings(List<PhysicalStackBinding> bindings, SubjectId accountId, long epoch, String label) {
        if (bindings == null || bindings.stream().anyMatch(binding -> !binding.accountId().equals(accountId) || binding.authorityEpoch() != epoch)) {
            throw new IllegalArgumentException(label + " does not match its custody authority");
        }
    }
    private static Map<SubjectId, Integer> boundQuantities(List<PhysicalStackBinding> bindings, boolean lots) {
        Map<SubjectId, Integer> result = new HashMap<>();
        bindings.forEach(binding -> (lots ? binding.lotQuantities() : binding.claimQuantities())
                .forEach((id, quantity) -> result.merge(id, quantity, Integer::sum)));
        return Map.copyOf(result);
    }
    private static CustodyAccount accountWithAdded(CustodyAccount account, Map<SubjectId, Integer> lots, Map<SubjectId, Integer> claims) {
        Map<SubjectId, Integer> nextLots = new HashMap<>(account.lotQuantities()); lots.forEach((id, quantity) -> nextLots.merge(id, quantity, Integer::sum));
        Map<SubjectId, Integer> nextClaims = new HashMap<>(account.claimQuantities()); claims.forEach((id, quantity) -> nextClaims.merge(id, quantity, Integer::sum));
        return new CustodyAccount(account.id(), account.custody(), nextLots, nextClaims);
    }
    private void requireClaimsCoveredByLots(Map<SubjectId, Integer> lotQuantities, Map<SubjectId, Integer> claimQuantities,
                                            String operation) {
        claimQuantities.forEach((claimId, claimQuantity) -> {
            ClaimAllocation claim = claims.get(claimId);
            int compatible = lotQuantities.entrySet().stream().filter(entry -> {
                ResourceLot lot = requireLot(entry.getKey());
                return lot.economicOwnerId().equals(claim.economicOwnerId()) && lot.itemKind().equals(claim.itemKind());
            }).mapToInt(Map.Entry::getValue).sum();
            if (compatible < claimQuantity) throw new IllegalArgumentException(operation + " has a claim without matching resource lots");
        });
    }
    private static Map<SubjectId, Integer> subtract(Map<SubjectId, Integer> source, Map<SubjectId, Integer> removed) {
        Map<SubjectId, Integer> next = new HashMap<>(source); removed.forEach((id, quantity) -> { int remaining = next.get(id) - quantity; if (remaining == 0) next.remove(id); else next.put(id, remaining); }); return next;
    }
    static void requireSubset(Map<SubjectId, Integer> source, Map<SubjectId, Integer> requested, String label) {
        if (requested == null || requested.isEmpty() || requested.values().stream().anyMatch(value -> value == null || value < 1)) throw new IllegalArgumentException(label + " must name a positive exact quantity");
        requested.forEach((id, quantity) -> { if (source.getOrDefault(id, 0) < quantity) throw new IllegalArgumentException(label + " exceeds its current custody"); });
    }
    static void requireOptionalSubset(Map<SubjectId, Integer> source, Map<SubjectId, Integer> requested, String label) {
        if (requested == null) throw new IllegalArgumentException(label + " is required");
        if (!requested.isEmpty()) requireSubset(source, requested, label);
    }
    private static int sum(Map<SubjectId, Integer> quantities) { return quantities.values().stream().mapToInt(Integer::intValue).sum(); }
    private record Reservation(CustodyAccount account, Map<SubjectId, ClaimAllocation> claims) { }
    private static <T> void requireKeys(Map<SubjectId, T> values, java.util.function.Function<T, SubjectId> id, String label) {
        values.forEach((key, value) -> { if (value == null || !key.equals(id.apply(value))) throw new IllegalArgumentException(label + " map key does not match identity"); });
    }
}
