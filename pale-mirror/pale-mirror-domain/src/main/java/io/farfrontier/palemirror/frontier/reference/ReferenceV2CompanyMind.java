package io.farfrontier.palemirror.frontier.reference;

/** Source V2 crisis-only wage policy; normal civic observation leaves wages untouched. */
final class ReferenceV2CompanyMind {
    private ReferenceV2CompanyMind() { }

    static double wageMultiplier(ReferenceCivicLedger civic, double threat) {
        return switch (civic.state()) {
            case SIEGE -> 1.18d + threat * 0.10d;
            case EMERGENCY -> 1.08d + threat * 0.08d;
            default -> 1.0d;
        };
    }
}
