package io.farfrontier.palemirror.frontier.v3.model;

/** Nominal participant strategy, supplied by its producer rather than inferred from an ID. */
public enum GoodsPolicyKind {
    PUBLIC_SETTLEMENT(1, EconomicOwnerKind.SETTLEMENT_TREASURY), OWN_ACCOUNT_COMPANY(2, EconomicOwnerKind.COMPANY);
    private final int tag;
    private final EconomicOwnerKind ownerKind;
    GoodsPolicyKind(int tag, EconomicOwnerKind ownerKind) { this.tag = tag; this.ownerKind = ownerKind; }
    public int wireTag() { return tag; }
    public void validate(GoodsTradeParty party) {
        if (party.kind() != ownerKind) throw new IllegalArgumentException("goods policy has a forged nominal participant");
    }
    public static GoodsPolicyKind fromWireTag(int tag) {
        for (var value : values()) if (value.tag == tag) return value;
        throw new IllegalArgumentException("unknown participant policy tag");
    }
}
