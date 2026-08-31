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
public record EconomicLedger(Map<SubjectId, EconomicAccount> accounts, Map<SubjectId, FinancialReservation> reservations) {
    public static final int MAX_ACCOUNTS = 4_096;
    public static final int MAX_RESERVATIONS = 4_096;
    /** Finite fresh-world currency issue per settlement; all later mutations are zero-sum transfers. */
    public static final FixedScalar INITIAL_SETTLEMENT_TREASURY = FixedScalar.whole(100L);

    public EconomicLedger {
        accounts = Map.copyOf(accounts);
        reservations = Map.copyOf(reservations);
        if (accounts.size() > MAX_ACCOUNTS) throw new IllegalArgumentException("economic account retention limit exceeded");
        if (reservations.size() > MAX_RESERVATIONS) throw new IllegalArgumentException("financial reservation retention limit exceeded");
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
        }
    }

    public EconomicLedger(Map<SubjectId, EconomicAccount> accounts) { this(accounts, Map.of()); }

    public static EconomicLedger bootstrap(FrontierBootstrap bootstrap) {
        Objects.requireNonNull(bootstrap, "bootstrap");
        Map<SubjectId, EconomicAccount> accounts = new LinkedHashMap<>();
        for (Settlement settlement : bootstrap.settlements()) {
            accounts.put(settlement.id(), new EconomicAccount(settlement.id(), EconomicOwnerKind.SETTLEMENT_TREASURY,
                    EconomicAccountStatus.ACTIVE, INITIAL_SETTLEMENT_TREASURY, FixedScalar.ZERO));
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
    EconomicLedger register(EconomicAccount account) {
        Objects.requireNonNull(account, "economic account");
        if (accounts.containsKey(account.ownerId())) {
            throw new IllegalArgumentException("economic account identity already exists: " + account.ownerId().value());
        }
        Map<SubjectId, EconomicAccount> next = new LinkedHashMap<>(accounts); next.put(account.ownerId(), account);
        return new EconomicLedger(next, reservations);
    }

    /** Atomically transfers a positive amount without minting money or bypassing either account. */
    public EconomicLedger transfer(SubjectId payerId, SubjectId payeeId, FixedScalar amount) {
        if (availableToReserve(payerId).compareTo(Objects.requireNonNull(amount, "transfer amount")) < 0) {
            throw new IllegalArgumentException("economic transfer exceeds available unreserved funds");
        }
        return transfer(accounts, reservations, payerId, payeeId, amount);
    }

    /** Reserves funds without moving them; another reservation cannot spend the same available balance or credit. */
    EconomicLedger reserve(FinancialReservation reservation) {
        Objects.requireNonNull(reservation, "financial reservation");
        if (reservations.containsKey(reservation.id())) throw new IllegalArgumentException("financial reservation identity already exists: " + reservation.id().value());
        EconomicAccount payer = require(reservation.payerId()); EconomicAccount payee = require(reservation.payeeId());
        if (payer.status() != EconomicAccountStatus.ACTIVE || payee.status() != EconomicAccountStatus.ACTIVE
                || availableToReserve(reservation.payerId()).compareTo(reservation.amount()) < 0) {
            throw new IllegalArgumentException("financial reservation exceeds available funds");
        }
        Map<SubjectId, FinancialReservation> next = new LinkedHashMap<>(reservations); next.put(reservation.id(), reservation);
        return new EconomicLedger(accounts, next);
    }

    /** Releases an unspent named hold; release is never an implicit transfer or mint. */
    EconomicLedger release(SubjectId reservationId) {
        if (!reservations.containsKey(Objects.requireNonNull(reservationId, "financial reservation id"))) {
            throw new IllegalArgumentException("unknown financial reservation: " + reservationId.value());
        }
        Map<SubjectId, FinancialReservation> next = new LinkedHashMap<>(reservations); next.remove(reservationId);
        return new EconomicLedger(accounts, next);
    }

    /** Settles exactly one named hold, atomically removing it and transferring its held amount. */
    EconomicLedger settle(SubjectId reservationId) {
        FinancialReservation reservation = reservations.get(Objects.requireNonNull(reservationId, "financial reservation id"));
        if (reservation == null) throw new IllegalArgumentException("unknown financial reservation: " + reservationId.value());
        Map<SubjectId, FinancialReservation> remaining = new LinkedHashMap<>(reservations); remaining.remove(reservationId);
        return transfer(accounts, remaining, reservation.payerId(), reservation.payeeId(), reservation.amount());
    }

    FixedScalar availableToReserve(SubjectId payerId) {
        EconomicAccount payer = require(payerId);
        FixedScalar held = reservations.values().stream().filter(reservation -> reservation.payerId().equals(payerId))
                .map(FinancialReservation::amount).reduce(FixedScalar.ZERO, FixedScalar::plus);
        return payer.balance().plus(payer.creditLimit()).minus(held);
    }

    private static EconomicLedger transfer(Map<SubjectId, EconomicAccount> current, Map<SubjectId, FinancialReservation> reservations,
                                           SubjectId payerId, SubjectId payeeId, FixedScalar amount) {
        Objects.requireNonNull(amount, "transfer amount");
        if (amount.raw() <= 0L) throw new IllegalArgumentException("transfer amount must be positive");
        if (payerId.equals(payeeId)) throw new IllegalArgumentException("transfer requires distinct accounts");
        EconomicAccount payer = current.get(payerId); EconomicAccount payee = current.get(payeeId);
        if (payer == null || payee == null) throw new IllegalArgumentException("economic transfer requires registered accounts");
        Map<SubjectId, EconomicAccount> next = new LinkedHashMap<>(current);
        next.put(payerId, payer.debit(amount)); next.put(payeeId, payee.credit(amount));
        return new EconomicLedger(next, reservations);
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
