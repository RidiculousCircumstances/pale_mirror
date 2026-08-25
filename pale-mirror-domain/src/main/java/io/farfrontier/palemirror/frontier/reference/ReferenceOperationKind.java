package io.farfrontier.palemirror.frontier.reference;

/** Exact Python {@code OperationKind} vocabulary. */
public enum ReferenceOperationKind {
    RECON("recon"), PATROL("patrol"), ESCORT("escort"), DEFEND("defend"), CLEANSE("cleanse"), RECLAIM("reclaim"),
    RAID_NEST("raid_nest"), EVACUATE("evacuate"), SCOUT_SPORES("scout_spores"), CORRUPT_SITE("corrupt_site"),
    DISRUPT_ROUTE("disrupt_route"), DEFEND_NEST("defend_nest"), ATTACK_SETTLEMENT("attack_settlement"), FEINT("feint"),
    FOUND_NEST("found_nest"), BUILD_POST("build_post"), BUILD_MODULE("build_module"), BUILD_LINE("build_line"),
    RESUPPLY("resupply"), REINFORCE("reinforce"), EVACUATE_WOUNDED("evacuate_wounded"), WITHDRAW("withdraw"),
    CLEANSE_PERIMETER("cleanse_perimeter");

    private final String id;

    ReferenceOperationKind(String id) { this.id = id; }
    public String id() { return id; }

    public boolean isFieldOperation() {
        return switch (this) {
            case BUILD_POST, BUILD_MODULE, BUILD_LINE, RESUPPLY, REINFORCE, EVACUATE_WOUNDED, WITHDRAW, CLEANSE_PERIMETER -> true;
            default -> false;
        };
    }
}
