package io.farfrontier.palemirror.frontier.v3.model;

/**
 * Stable closed identity for a durable scene family.
 *
 * <p>This is deliberately separate from the Java class name: registries use this value to
 * prove that every sealed cause has exactly one owner before simulation starts.</p>
 */
public enum SceneCauseKind {
    LOGISTICS,
    SETTLEMENT_ASSAULT,
    ENGINEERING_WORKSITE,
    MEDICAL_TREATMENT,
    RESOURCE_SITE_HARVEST,
    PRODUCTION_WORK,
    SERVICE_WORK
}
