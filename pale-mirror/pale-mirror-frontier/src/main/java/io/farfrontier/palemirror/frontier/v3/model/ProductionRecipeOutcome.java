package io.farfrontier.palemirror.frontier.v3.model;

/** Producer-declared output semantics; a station must not infer identity handling from an item name. */
public sealed interface ProductionRecipeOutcome permits ProductionRecipeOutcome.Fungible,
        ProductionRecipeOutcome.Individual, ProductionRecipeOutcome.Repair {
    String canonicalText();
    /** Ordinary divisible resource output, including food and smelted metal. */
    record Fungible() implements ProductionRecipeOutcome {
        @Override public String canonicalText() { return "fungible:1"; }
    }
    /** New equipment instance with catalog-defined properties. */
    record Individual() implements ProductionRecipeOutcome {
        @Override public String canonicalText() { return "individual:2"; }
    }
    /** Preserve the target's identity/title/lifetime; material recipe restores bounded actual damage. */
    record Repair(int restoredDurability) implements ProductionRecipeOutcome {
        public Repair { if (restoredDurability < 1) throw new IllegalArgumentException("repair must restore positive useful life"); }
        @Override public String canonicalText() { return "repair:3:" + restoredDurability; }
        public ItemCondition repaired(ItemCondition before) {
            if (!before.durable() || before.damage() == 0) throw new IllegalArgumentException("repair target is not damaged equipment");
            return before.restore(Math.min(before.damage(), restoredDurability));
        }
    }
}
