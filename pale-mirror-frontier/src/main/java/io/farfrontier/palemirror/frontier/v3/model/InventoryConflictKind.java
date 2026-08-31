package io.farfrontier.palemirror.frontier.v3.model;

/** Why one physical PM container slot cannot be reconciled to its exact canonical item. */
public enum InventoryConflictKind { MISSING, FOREIGN_OR_DUPLICATE ;

    public int wireTag() { return FrontierWireTags.tag(this); }
}
