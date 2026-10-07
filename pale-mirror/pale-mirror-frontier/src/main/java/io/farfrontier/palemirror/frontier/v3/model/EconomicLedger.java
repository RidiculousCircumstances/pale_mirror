package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Bounded authoritative registry of economic claim holders and their monetary accounts.
 * It deliberately contains no item quantities: exact resources remain in {@link ExactInventory}.
 */
public record EconomicLedger(Map<SubjectId, EconomicAccount> accounts, Map<SubjectId, FinancialReservation> reservations,
                             Map<SubjectId, FinancialBudget> budgets) {
    public static final int MAX_ACCOUNTS = 4_096;
    public static final int MAX_RESERVATIONS = 4_096;

    public EconomicLedger {
        accounts = Map.copyOf(accounts);
        reservations = Map.copyOf(reservations);
        budgets = Map.copyOf(budgets);
        if (accounts.size() > MAX_ACCOUNTS) throw new IllegalArgumentException("economic account retention limit exceeded");
        if (reservations.size() > MAX_RESERVATIONS) throw new IllegalArgumentException("financial reservation retention limit exceeded");
        if (budgets.size() > MAX_RESERVATIONS) throw new IllegalArgumentException("financial budget retention limit exceeded");
        for (Map.Entry<SubjectId, EconomicAccount> entry : accounts.entrySet()) {
            if (!entry.getKey().equals(entry.getValue().ownerId())) {
                throw new IllegalArgumentException("economic account key must match owner identity");
            }
        }
        for (Map.Entry<SubjectId, FinancialReservation> entry : reservations.entrySet()) {
            FinancialReservation reservation = entry.getValue();
            if (!entry.getKey().equals(reservation.id()) || !accounts.containsKey(reservation.payerId()) || !accounts.containsKey(reservation.payeeId())) {
                throw new IllegalArgumentException("financial reservation must retain its identity and registered counterparties");
            }
            if (accounts.get(reservation.payerId()).ownerKind() == EconomicOwnerKind.RESIDENT
                    || accounts.get(reservation.payeeId()).ownerKind() == EconomicOwnerKind.RESIDENT)
                throw new IllegalArgumentException("resident resource-title registration cannot participate in monetary reservations");
            if (reservation.budgetId().isPresent()) {
                var budget = budgets.get(reservation.budgetId().orElseThrow());
                if (budget == null || !budget.payerId().equals(reservation.payerId()))
                    throw new IllegalArgumentException("purchase lost its declared source budget");
            }
        }
        for (var entry : budgets.entrySet()) {
            var budget = entry.getValue(); var payer = accounts.get(budget.payerId());
            if (!entry.getKey().equals(budget.id()) || reservations.containsKey(entry.getKey())
                    || payer == null || payer.ownerKind() == EconomicOwnerKind.RESIDENT)
                throw new IllegalArgumentException("financial budget requires a registered non-resident treasury");
        }
        var heldByPayer = new LinkedHashMap<SubjectId, FixedScalar>();
        reservations.values().forEach(hold -> heldByPayer.merge(hold.payerId(), hold.amount(), FixedScalar::plus));
        budgets.values().forEach(budget -> heldByPayer.merge(budget.payerId(), budget.remaining(), FixedScalar::plus));
        for (var held : heldByPayer.entrySet()) {
            var payer = accounts.get(held.getKey());
            if (held.getValue().raw() > 0 && held.getValue().compareTo(payer.balance().plus(payer.creditLimit())) > 0)
                throw new IllegalArgumentException("retained financial commitments exceed their treasury: " + held.getKey().value());
        }
    }

    public EconomicLedger(Map<SubjectId, EconomicAccount> accounts, Map<SubjectId, FinancialReservation> reservations) {
        this(accounts, reservations, Map.of());
    }
    public EconomicLedger(Map<SubjectId, EconomicAccount> accounts) { this(accounts, Map.of()); }

    public static EconomicLedger bootstrap(FrontierBootstrap bootstrap) {
        Objects.requireNonNull(bootstrap, "bootstrap");
        Map<SubjectId, EconomicAccount> accounts = new LinkedHashMap<>();
        for (Settlement settlement : bootstrap.settlements()) {
            accounts.put(settlement.id(), new EconomicAccount(settlement.id(), EconomicOwnerKind.SETTLEMENT_TREASURY,
                    EconomicAccountStatus.ACTIVE, bootstrap.ruleset().rates().initialSettlementTreasury(), FixedScalar.ZERO));
        }
        accounts.put(bootstrap.hive().id(), account(bootstrap.hive().id(), EconomicOwnerKind.HIVE_COLLECTIVE));
        accounts.put(FrontierRouteNetwork.OWNER, account(FrontierRouteNetwork.OWNER, EconomicOwnerKind.PUBLIC_INFRASTRUCTURE));
        return new EconomicLedger(accounts, Map.of());
    }

    /** Compatibility construction for small pure fixtures; production bootstraps explicitly. */
    static EconomicLedger fromClaimHolders(Iterable<ContainerRecord> containers, Iterable<ExactItemStack> items,
                                           Iterable<CargoBatch> cargo) {
        Map<SubjectId, EconomicAccount> accounts = new LinkedHashMap<>();
        for (ContainerRecord container : containers) addClaimHolder(accounts, container.ownerId());
        for (ExactItemStack item : items) addClaimHolder(accounts, item.economicOwnerId());
        for (CargoBatch batch : cargo) addClaimHolder(accounts, batch.ownerId());
        return new EconomicLedger(accounts, Map.of());
    }

    public EconomicAccount require(SubjectId ownerId) {
        EconomicAccount account = accounts.get(Objects.requireNonNull(ownerId, "economic owner"));
        if (account == null) throw new IllegalArgumentException("economic claim owner has no registered account: " + ownerId.value());
        return account;
    }

    /** Opens one explicitly authorized account; no money or resource claim is created by registration. */
    public EconomicLedger register(EconomicAccount account) {
        Objects.requireNonNull(account, "economic account");
        if (accounts.containsKey(account.ownerId())) {
            throw new IllegalArgumentException("economic account identity already exists: " + account.ownerId().value());
        }
        Map<SubjectId, EconomicAccount> next = new LinkedHashMap<>(accounts); next.put(account.ownerId(), account);
        return new EconomicLedger(next, reservations, budgets);
    }

    /** Atomically transfers a positive amount without minting money or bypassing either account. */
    public EconomicLedger transfer(SubjectId payerId, SubjectId payeeId, FixedScalar amount) {
        if (availableToReserve(payerId).compareTo(Objects.requireNonNull(amount, "transfer amount")) < 0) {
            throw new IllegalArgumentException("economic transfer exceeds available unreserved funds");
        }
        return transfer(accounts, reservations, budgets, payerId, payeeId, amount);
    }

    /** Reserves funds without moving them; another reservation cannot spend the same available balance or credit. */
    public EconomicLedger reserve(FinancialReservation reservation) {
        Objects.requireNonNull(reservation, "financial reservation");
        if (reservation.budgetId().isPresent())
            throw new IllegalArgumentException("budget-backed purchase must allocate its exact budget atomically");
        if (reservations.containsKey(reservation.id()) || budgets.containsKey(reservation.id())) throw new IllegalArgumentException("financial reservation identity already exists: " + reservation.id().value());
        EconomicAccount payer = require(reservation.payerId()); EconomicAccount payee = require(reservation.payeeId());
        if (payer.status() != EconomicAccountStatus.ACTIVE || payee.status() != EconomicAccountStatus.ACTIVE
                || availableToReserve(reservation.payerId()).compareTo(reservation.amount()) < 0) {
            throw new IllegalArgumentException("financial reservation exceeds available funds");
        }
        Map<SubjectId, FinancialReservation> next = new LinkedHashMap<>(reservations); next.put(reservation.id(), reservation);
        return new EconomicLedger(accounts, next, budgets);
    }

    public EconomicLedger reserveBudget(FinancialBudget budget) {
        Objects.requireNonNull(budget);
        var payer = require(budget.payerId());
        if (budget.remaining().raw() <= 0 || budgets.containsKey(budget.id())
                || reservations.containsKey(budget.id()) || payer.status() != EconomicAccountStatus.ACTIVE
                || payer.ownerKind() == EconomicOwnerKind.RESIDENT
                || availableToReserve(budget.payerId()).compareTo(budget.remaining()) < 0)
            throw new IllegalArgumentException("financial budget exceeds available treasury or repeats identity");
        var next = new LinkedHashMap<>(budgets); next.put(budget.id(), budget);
        return new EconomicLedger(accounts, reservations, next);
    }

    /** Move a budget slice into an exact purchase hold, without changing balances or total held funds. */
    public EconomicLedger reserveFromBudget(SubjectId budgetId, FinancialReservation purchase) {
        var budget = budgets.get(Objects.requireNonNull(budgetId)); Objects.requireNonNull(purchase);
        if (budget == null || !purchase.budgetId().equals(java.util.Optional.of(budgetId))
                || !purchase.payerId().equals(budget.payerId()) || reservations.containsKey(purchase.id())
                || budgets.containsKey(purchase.id()) || purchase.amount().compareTo(budget.remaining()) > 0
                || require(purchase.payeeId()).status() != EconomicAccountStatus.ACTIVE
                || require(purchase.payerId()).status() != EconomicAccountStatus.ACTIVE)
            throw new IllegalArgumentException("purchase does not fit its exact available budget");
        var nextBudgets = new LinkedHashMap<>(budgets);
        nextBudgets.put(budgetId, budget.withRemaining(budget.remaining().minus(purchase.amount())));
        var nextPurchases = new LinkedHashMap<>(reservations); nextPurchases.put(purchase.id(), purchase);
        return new EconomicLedger(accounts, nextPurchases, nextBudgets);
    }

    public EconomicLedger releaseBudget(SubjectId budgetId) {
        if (!budgets.containsKey(Objects.requireNonNull(budgetId)) || reservations.values().stream()
                .anyMatch(purchase -> purchase.budgetId().equals(java.util.Optional.of(budgetId))))
            throw new IllegalArgumentException("budget is unknown or retains unsettled purchases");
        var next = new LinkedHashMap<>(budgets); next.remove(budgetId);
        return new EconomicLedger(accounts, reservations, next);
    }

    /** Release unused money now while retaining a zero-valued owner reference until causal retirement. */
    public EconomicLedger closeBudget(SubjectId budgetId) {
        if (!budgets.containsKey(Objects.requireNonNull(budgetId)) || reservations.values().stream()
                .anyMatch(purchase -> purchase.budgetId().equals(java.util.Optional.of(budgetId))))
            throw new IllegalArgumentException("budget cannot close with an unsettled purchase");
        var next = new LinkedHashMap<>(budgets);
        next.put(budgetId, next.get(budgetId).withRemaining(FixedScalar.ZERO));
        return new EconomicLedger(accounts, reservations, next);
    }

    /** Releases an unspent named hold; release is never an implicit transfer or mint. */
    public EconomicLedger release(SubjectId reservationId) {
        if (!reservations.containsKey(Objects.requireNonNull(reservationId, "financial reservation id"))) {
            throw new IllegalArgumentException("unknown financial reservation: " + reservationId.value());
        }
        return releasePortion(reservationId, reservations.get(reservationId).amount());
    }

    /** Releases only the cancelled portion; no balance changes or implicit settlement. */
    public EconomicLedger releasePortion(SubjectId reservationId, FixedScalar amount) {
        FinancialReservation reservation = reservations.get(Objects.requireNonNull(reservationId, "financial reservation id"));
        Objects.requireNonNull(amount, "released portion");
        if (reservation == null || amount.raw() <= 0 || amount.compareTo(reservation.amount()) > 0) {
            throw new IllegalArgumentException("partial release must fit its exact financial reservation");
        }
        Map<SubjectId, FinancialReservation> next = new LinkedHashMap<>(reservations);
        if (amount.equals(reservation.amount())) next.remove(reservationId);
        else next.put(reservationId, new FinancialReservation(reservation.id(), reservation.payerId(),
                reservation.payeeId(), reservation.reasonId(), reservation.amount().minus(amount), reservation.budgetId()));
        var nextBudgets = new LinkedHashMap<>(budgets);
        reservation.budgetId().ifPresent(id -> {
            var budget = nextBudgets.get(id);
            nextBudgets.put(id, budget.withRemaining(budget.remaining().plus(amount)));
        });
        return new EconomicLedger(accounts, next, nextBudgets);
    }

    /** Settles exactly one named hold, atomically removing it and transferring its held amount. */
    public EconomicLedger settle(SubjectId reservationId) {
        FinancialReservation reservation = reservations.get(Objects.requireNonNull(reservationId, "financial reservation id"));
        if (reservation == null) throw new IllegalArgumentException("unknown financial reservation: " + reservationId.value());
        return settlePortion(reservationId, reservation.amount());
    }

    /** Pays only the authorized portion, retaining the same hold for the unpaid remainder. */
    public EconomicLedger settlePortion(SubjectId reservationId, FixedScalar amount) {
        FinancialReservation reservation = reservations.get(Objects.requireNonNull(reservationId, "financial reservation id"));
        Objects.requireNonNull(amount, "settled portion");
        if (reservation == null || amount.raw() <= 0L || amount.compareTo(reservation.amount()) > 0) {
            throw new IllegalArgumentException("partial settlement must fit its exact financial reservation");
        }
        Map<SubjectId, FinancialReservation> remaining = new LinkedHashMap<>(reservations);
        if (amount.equals(reservation.amount())) remaining.remove(reservationId);
        else remaining.put(reservationId, new FinancialReservation(reservation.id(), reservation.payerId(),
                reservation.payeeId(), reservation.reasonId(), reservation.amount().minus(amount), reservation.budgetId()));
        return transfer(accounts, remaining, budgets, reservation.payerId(), reservation.payeeId(), amount);
    }

    public FixedScalar availableToReserve(SubjectId payerId) {
        EconomicAccount payer = require(payerId);
        FixedScalar held = reservations.values().stream().filter(reservation -> reservation.payerId().equals(payerId))
                .map(FinancialReservation::amount).reduce(FixedScalar.ZERO, FixedScalar::plus);
        held = held.plus(budgets.values().stream().filter(budget -> budget.payerId().equals(payerId))
                .map(FinancialBudget::remaining).reduce(FixedScalar.ZERO, FixedScalar::plus));
        return payer.balance().plus(payer.creditLimit()).minus(held);
    }

    private static EconomicLedger transfer(Map<SubjectId, EconomicAccount> current, Map<SubjectId, FinancialReservation> reservations,
                                           Map<SubjectId, FinancialBudget> budgets,
                                           SubjectId payerId, SubjectId payeeId, FixedScalar amount) {
        Objects.requireNonNull(amount, "transfer amount");
        if (amount.raw() <= 0L) throw new IllegalArgumentException("transfer amount must be positive");
        if (payerId.equals(payeeId)) throw new IllegalArgumentException("transfer requires distinct accounts");
        EconomicAccount payer = current.get(payerId); EconomicAccount payee = current.get(payeeId);
        if (payer == null || payee == null) throw new IllegalArgumentException("economic transfer requires registered accounts");
        Map<SubjectId, EconomicAccount> next = new LinkedHashMap<>(current);
        next.put(payerId, payer.debit(amount)); next.put(payeeId, payee.credit(amount));
        return new EconomicLedger(next, reservations, budgets);
    }

    private static EconomicAccount account(SubjectId owner, EconomicOwnerKind kind) {
        return new EconomicAccount(owner, kind, EconomicAccountStatus.ACTIVE, FixedScalar.ZERO, FixedScalar.ZERO);
    }

    private static void addClaimHolder(Map<SubjectId, EconomicAccount> accounts, SubjectId owner) {
        accounts.computeIfAbsent(owner, key -> account(key, switch (key.value().substring(0, key.value().indexOf(':'))) {
            case "settlement" -> EconomicOwnerKind.SETTLEMENT_TREASURY;
            case "hive" -> EconomicOwnerKind.HIVE_COLLECTIVE;
            case "route" -> EconomicOwnerKind.PUBLIC_INFRASTRUCTURE;
            case "company" -> EconomicOwnerKind.COMPANY;
            case "resident" -> EconomicOwnerKind.RESIDENT;
            default -> throw new IllegalArgumentException("economic claim holder needs an explicit registered account: " + key.value());
        }));
    }
}
