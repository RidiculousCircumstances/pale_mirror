package io.farfrontier.palemirror.domain;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

public final class SettlementEconomy {
    private final WorldObjectId communityId;
    private final Map<ResourceKind, ResourceAccount> accounts;

    public SettlementEconomy(WorldObjectId communityId, Map<ResourceKind, ResourceAccount> accounts) {
        this.communityId = Objects.requireNonNull(communityId, "communityId");
        this.accounts = new EnumMap<>(ResourceKind.class);
        Objects.requireNonNull(accounts, "accounts").forEach((kind, account) -> this.accounts.put(
                Objects.requireNonNull(kind), Objects.requireNonNull(account)));
    }

    public WorldObjectId communityId() { return communityId; }
    public Map<ResourceKind, ResourceAccount> accounts() { return Map.copyOf(accounts); }
    public ResourceAccount require(ResourceKind kind) {
        ResourceAccount account = accounts.get(kind);
        if (account == null) throw new IllegalStateException("Community " + communityId + " lacks " + kind + " account");
        return account;
    }
}
