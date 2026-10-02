package io.farfrontier.palemirror.frontier.v3.persistence;

/** Exact schema-220 additive movement-start registration. Existing state and payload layouts are unchanged. */
final class ServiceTurnoverDescriptorUpgrade {
    static final String BEFORE = "811327abf6a0aec6708c93bd773ae1647d28bc9c60323a92c2bc5275c340072f";
    static final String AFTER = "64bf701b5c05bd35395721679324afee061b35cb408dd301ff47a8cdb4b8b677";
    private ServiceTurnoverDescriptorUpgrade() { }
    static boolean accepts(String expected, String retained) {
        return HarvestInspectionDescriptorUpgrade.accepts(expected, retained)
                || AFTER.equals(expected) && HarvestInspectionDescriptorUpgrade.accepts(BEFORE, retained);
    }
}
