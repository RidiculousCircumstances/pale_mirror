package io.farfrontier.palemirror.domain;

/** Source-neutral settlement fields whose authority can differ by integration. */
public enum SettlementAuthorityField {
    MACRO_POPULATION,
    PHYSICAL_NPCS,
    RESOURCE_FLOW,
    LOCAL_CONSTRUCTION,
    PHYSICAL_INTEGRITY
}
