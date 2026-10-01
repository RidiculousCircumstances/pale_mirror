package io.farfrontier.palemirror.frontier.v3.persistence;

/**
 * Exact schema-220 additive receipt registration, not historical execution-grammar compatibility.
 * No state layout or old payload changed. Pin both inventories so future descriptor edits fail closed.
 */
final class HarvestInspectionDescriptorUpgrade {
    static final String BEFORE = "569e65ce658ea3da7a888f8a0be2b8f0200d42166e688581156bf4b3c4f82a44";
    static final String AFTER = "811327abf6a0aec6708c93bd773ae1647d28bc9c60323a92c2bc5275c340072f";
    private HarvestInspectionDescriptorUpgrade() { }
    static boolean accepts(String expected, String retained) {
        return expected.equals(retained) || AFTER.equals(expected) && BEFORE.equals(retained);
    }
}
