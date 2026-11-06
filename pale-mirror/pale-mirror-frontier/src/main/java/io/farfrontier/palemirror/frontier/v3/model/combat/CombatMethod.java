package io.farfrontier.palemirror.frontier.v3.model.combat;
/** Explicit mechanical dispatch identity, supplied by the appointing owner. */
public enum CombatMethod {
    MELEE(1), CROSSBOW(2);
    private final int tag;
    CombatMethod(int tag) { this.tag = tag; }
    public int wireTag() { return tag; }
    public static CombatMethod require(int tag) {
        for (var value : values()) if (value.tag == tag) return value;
        throw new IllegalArgumentException("unknown combat method: " + tag);
    }
}
