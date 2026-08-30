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
public record EconomicLedger(Map<SubjectId, EconomicAccount> accounts) {
    public static final int MAX_ACCOUNTS = 4_096;

    public EconomicLedger {
        accounts = Map.copyOf(accounts);
        if (accounts.size() > MAX_ACCOUNTS) throw new IllegalArgumentException("economic account retention limit exceeded");
        for (Map.Entry<SubjectId, EconomicAccount> entry : accounts.entrySet()) {
            if (!entry.getKey().equals(entry.getValue().ownerId())) {
                throw new IllegalArgumentException("economic account key must match owner identity");
            }
        }
    }

    static EconomicLedger bootstrap(FrontierBootstrap bootstrap) {
        Objects.requireNonNull(bootstrap, "bootstrap");
        Map<SubjectId, EconomicAccount> accounts = new LinkedHashMap<>();
        for (Settlement settlement : bootstrap.settlements()) {
            accounts.put(settlement.id(), account(settlement.id(), EconomicOwnerKind.SETTLEMENT_TREASURY));
        }
        accounts.put(bootstrap.hive().id(), account(bootstrap.hive().id(), EconomicOwnerKind.HIVE_COLLECTIVE));
        accounts.put(FrontierRouteNetwork.OWNER, account(FrontierRouteNetwork.OWNER, EconomicOwnerKind.PUBLIC_INFRASTRUCTURE));
        return new EconomicLedger(accounts);
    }

    /** Compatibility construction for small pure fixtures; production bootstraps explicitly. */
    static EconomicLedger fromClaimHolders(Iterable<ContainerRecord> containers, Iterable<ExactItemStack> items,
                                           Iterable<CargoBatch> cargo) {
        Map<SubjectId, EconomicAccount> accounts = new LinkedHashMap<>();
        for (ContainerRecord container : containers) addClaimHolder(accounts, container.ownerId());
        for (ExactItemStack item : items) addClaimHolder(accounts, item.economicOwnerId());
        for (CargoBatch batch : cargo) addClaimHolder(accounts, batch.ownerId());
        return new EconomicLedger(accounts);
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
        return new EconomicLedger(next);
    }

    /** Atomically transfers a positive amount without minting money or bypassing either account. */
    public EconomicLedger transfer(SubjectId payerId, SubjectId payeeId, FixedScalar amount) {
        Objects.requireNonNull(amount, "transfer amount");
        if (amount.raw() <= 0L) throw new IllegalArgumentException("transfer amount must be positive");
        if (payerId.equals(payeeId)) throw new IllegalArgumentException("transfer requires distinct accounts");
        EconomicAccount payer = require(payerId); EconomicAccount payee = require(payeeId);
        Map<SubjectId, EconomicAccount> next = new LinkedHashMap<>(accounts);
        next.put(payerId, payer.debit(amount)); next.put(payeeId, payee.credit(amount));
        return new EconomicLedger(next);
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
