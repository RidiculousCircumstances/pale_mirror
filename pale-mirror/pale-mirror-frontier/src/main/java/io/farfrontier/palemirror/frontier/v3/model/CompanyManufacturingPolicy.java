package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;

/** Own-account choice only; no facility grant, resident admission or ledger mutation. */
public final class CompanyManufacturingPolicy {
    private CompanyManufacturingPolicy() { }
    public record View(boolean active, boolean registeredGoodsParticipant, int ownedInput) {
        public View {
            if (ownedInput < 0) throw new IllegalArgumentException("invalid company manufacturing view");
        }
    }
    public static boolean requestsProduction(View view) {
        return view.active() && view.registeredGoodsParticipant() && view.ownedInput() > 0;
    }
}
