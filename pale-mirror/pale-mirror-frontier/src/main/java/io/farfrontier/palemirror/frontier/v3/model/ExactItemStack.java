package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** One named exact stack with separate economic claim and physical custody. */
public record ExactItemStack(SubjectId id, SubjectId economicOwnerId, String itemKind, int count, InventoryCustody custody,
                             ItemCondition condition) {
    public ExactItemStack(SubjectId id, SubjectId economicOwnerId, String itemKind, int count, InventoryCustody custody) {
        this(id, economicOwnerId, itemKind, count, custody, ItemCondition.NONE);
    }
    public ExactItemStack {
        Objects.requireNonNull(id, "item stack id");
        Objects.requireNonNull(economicOwnerId, "economic owner id");
        if (itemKind == null || !itemKind.matches("[a-z][a-z0-9_-]{0,31}:[a-z0-9][a-z0-9_./-]{0,127}")) throw new IllegalArgumentException("item kind must be namespace:path");
        if (count <= 0 || count > 64) throw new IllegalArgumentException("item stack count must be 1..64");
        Objects.requireNonNull(custody, "item custody");
        Objects.requireNonNull(condition, "individual item condition");
        if (condition.durable() && count != 1) throw new IllegalArgumentException("durable items are individual instances");
    }
    public ExactItemStack withCount(int nextCount) {
        return new ExactItemStack(id, economicOwnerId, itemKind, nextCount, custody, condition);
    }
    public ExactItemStack withCustody(InventoryCustody nextCustody) {
        return new ExactItemStack(id, economicOwnerId, itemKind, count, nextCustody, condition);
    }
    public ExactItemStack withOwnerAndCustody(SubjectId nextOwner, InventoryCustody nextCustody) {
        return new ExactItemStack(id, nextOwner, itemKind, count, nextCustody, condition);
    }
    public ExactItemStack withCondition(ItemCondition nextCondition) {
        return new ExactItemStack(id, economicOwnerId, itemKind, count, custody, nextCondition);
    }
}
