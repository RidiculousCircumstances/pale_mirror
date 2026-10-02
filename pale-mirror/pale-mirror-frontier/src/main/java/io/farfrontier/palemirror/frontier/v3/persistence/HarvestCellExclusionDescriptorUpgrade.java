package io.farfrontier.palemirror.frontier.v3.persistence;

/** Exact schema-220 additive immature-cell receipt; state and existing WAL layouts are unchanged. */
final class HarvestCellExclusionDescriptorUpgrade {
    static final String BEFORE = "64bf701b5c05bd35395721679324afee061b35cb408dd301ff47a8cdb4b8b677";
    static final String AFTER = "71e937250b8cdf0973ac4568dde9710f06817e1b6a3b24822f8439021069b7bf";
    private HarvestCellExclusionDescriptorUpgrade() { }
    static boolean accepts(String expected, String retained) {
        return ServiceTurnoverDescriptorUpgrade.accepts(expected, retained)
                || AFTER.equals(expected) && ServiceTurnoverDescriptorUpgrade.accepts(BEFORE, retained);
    }
}
