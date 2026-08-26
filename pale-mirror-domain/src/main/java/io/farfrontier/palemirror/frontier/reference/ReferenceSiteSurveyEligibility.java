package io.farfrontier.palemirror.frontier.reference;

/** Python {@code World._place_site_position} territorial eligibility check. */
final class ReferenceSiteSurveyEligibility {
    private static final double MINIMUM_HUMAN_ACCESS = .18d;
    private static final double MAXIMUM_INFECTION = .44d;

    private ReferenceSiteSurveyEligibility() { }

    static boolean allows(ReferenceV2State v2, int x, int y) {
        if (v2 == null) return true; // World generation precedes V2 creation.
        ReferenceV2OperationalSector sector = v2.sectorAt(x, y);
        return sector.humanAccess() >= MINIMUM_HUMAN_ACCESS && sector.infection() <= MAXIMUM_INFECTION;
    }
}
