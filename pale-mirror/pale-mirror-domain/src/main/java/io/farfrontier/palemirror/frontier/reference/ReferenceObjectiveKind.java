package io.farfrontier.palemirror.frontier.reference;

/** Exact Python {@code ObjectiveKind} vocabulary. */
public enum ReferenceObjectiveKind {
    DEFEND_SETTLEMENT("defend_settlement"), PROTECT_SITE("protect_site"), RESTORE_TRADE("restore_trade"),
    CLEANSE_SITE("cleanse_site"), RECLAIM_SITE("reclaim_site"), RECON("recon"), RAID_NEST("raid_nest"),
    EVACUATE("evacuate"), HARVEST_SITE("harvest_site"), GROW_NETWORK("grow_network"), FOUND_NEST("found_nest"),
    DEFEND_NEST("defend_nest"), DISRUPT_ROUTE("disrupt_route"), ATTACK_SETTLEMENT("attack_settlement"),
    MUTATE("mutate"), RECOVER("recover");

    private final String id;

    ReferenceObjectiveKind(String id) { this.id = id; }
    public String id() { return id; }
}
