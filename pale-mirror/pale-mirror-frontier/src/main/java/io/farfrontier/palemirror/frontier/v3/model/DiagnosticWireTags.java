package io.farfrontier.palemirror.frontier.v3.model;

import java.util.Arrays;

/** Stable non-ordinal wire identities for the diagnostic tuple dimensions. */
public final class DiagnosticWireTags {
    private DiagnosticWireTags() { }
    public static DiagnosticReason reason(int tag) { return byTag(DiagnosticReason.values(), tag, DiagnosticReason::wireTag, "diagnostic reason"); }
    public static DiagnosticCategory category(int tag) { return byTag(DiagnosticCategory.values(), tag, DiagnosticCategory::wireTag, "diagnostic category"); }
    public static DiagnosticDisposition disposition(int tag) { return byTag(DiagnosticDisposition.values(), tag, DiagnosticDisposition::wireTag, "diagnostic disposition"); }
    public static DiagnosticOwnerKind ownerKind(int tag) { return byTag(DiagnosticOwnerKind.values(), tag, DiagnosticWireTags::ownerTag, "diagnostic owner kind"); }
    public static DiagnosticSubjectKind subjectKind(int tag) { return byTag(DiagnosticSubjectKind.values(), tag, DiagnosticWireTags::subjectTag, "diagnostic subject kind"); }
    public static int ownerTag(DiagnosticOwnerKind kind) { return switch (kind) {
        case RESOURCE_SITE -> 1; case HARVEST_JOB -> 2; case PHYSICAL_INTENT -> 3; case SCENE_LEASE -> 4;
        case HIVE_MOBILIZATION -> 5; case ROUTE_PATROL -> 6; case PRODUCTION_JOB -> 7; case SETTLEMENT_SERVICE_WORK -> 8;
        case ROUTE_MAINTENANCE -> 9; case ROUTE_CONSTRUCTION -> 10; case MEDICAL_EVACUATION -> 11;
        case INVENTORY_CUSTODY -> 12; case REPLICA_CUSTODY -> 13; case FRONTIER_INSTANCE -> 14; case ADAPTER -> 15; }; }
    public static int subjectTag(DiagnosticSubjectKind kind) { return switch (kind) {
        case RESOURCE_SITE_CELL -> 1; case HARVEST_WORKER -> 2; case PHYSICAL_EFFECT -> 3; case SCENE_CARRIER -> 4;
        case HIVE_COCOON -> 5; case HIVE_TRANSFER -> 6; case ROUTE_MEMBER -> 7; case ROUTE_CELL -> 8;
        case PRODUCTION_INPUT -> 9; case FACILITY -> 10; case RESIDENT_ASSIGNMENT -> 11; case MEDICAL_PATIENT -> 12;
        case INVENTORY_SLOT -> 13; case REPLICA -> 14; case FRONTIER_INSTANCE -> 15; case ADAPTER_BOUNDARY -> 16; }; }
    private static <T> T byTag(T[] values, int tag, java.util.function.ToIntFunction<T> tags, String label) {
        return Arrays.stream(values).filter(value -> tags.applyAsInt(value) == tag).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown " + label + " tag: " + tag));
    }
}
