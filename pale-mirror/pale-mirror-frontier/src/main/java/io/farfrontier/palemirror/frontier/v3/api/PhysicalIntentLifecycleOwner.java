package io.farfrontier.palemirror.frontier.v3.api;

import java.util.Arrays;
import java.util.Objects;

/**
 * Stable capability identity stamped by the canonical producer of a durable physical intent.
 *
 * <p>This is deliberately an identity-only vocabulary.  It does not select behavior: ARC-001B
 * will bind this key to owner-supplied lifecycle capabilities.  Keeping the wire version beside
 * the identity makes a recovered intent fail before any family reducer can infer ownership.</p>
 */
public enum PhysicalIntentLifecycleOwner {
    RESOURCE_SITE_HARVEST("frontier.resource-site-harvest"),
    PRODUCTION_WORK("frontier.production-work"),
    SETTLEMENT_SERVICE_WORK("frontier.settlement-service-work"),
    ROUTE_OPERATION("frontier.route-operation"),
    ROUTE_PATROL("frontier.route-patrol"),
    HIVE_MOBILIZATION("frontier.hive-mobilization"),
    ROUTE_ENGAGEMENT("frontier.route-engagement"),
    SETTLEMENT_ASSAULT("frontier.settlement-assault"),
    POPULATION_MIGRATION("frontier.population-migration"),
    MEDICAL_TREATMENT("frontier.medical-treatment"),
    ENGINEERING_WORKSITE("frontier.engineering-worksite"),
    HIVE_GROWTH("frontier.hive-growth"),
    HIVE_NUTRIENT_TRANSFER("frontier.hive-nutrient-transfer"),
    SETTLEMENT_PROVISION("frontier.settlement-provision"),
    RESOURCE_SITE_PREPARATION("frontier.resource-site-preparation"),
    AMBIENT_ACTOR_CUSTODY("frontier.ambient-actor-custody");

    /** The first durable owner-capability codec.  Future incompatible meanings must bump it. */
    public static final int CODEC_VERSION = 1;

    private final String stableId;

    PhysicalIntentLifecycleOwner(String stableId) { this.stableId = stableId; }

    public String stableId() { return stableId; }

    public static PhysicalIntentLifecycleOwner fromWire(int codecVersion, String stableId) {
        if (codecVersion != CODEC_VERSION) {
            throw new IllegalArgumentException("unsupported physical intent lifecycle-owner codec version: " + codecVersion);
        }
        Objects.requireNonNull(stableId, "physical intent lifecycle owner id");
        return Arrays.stream(values()).filter(owner -> owner.stableId.equals(stableId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown physical intent lifecycle owner: " + stableId));
    }

    /** Compatibility constructor only for direct unit fixtures; production producers must stamp explicitly. */
    public static PhysicalIntentLifecycleOwner fixtureDefault(PhysicalIntentKind kind) {
        return switch (Objects.requireNonNull(kind, "physical intent kind")) {
            case RESOURCE_SITE_PREPARATION -> RESOURCE_SITE_PREPARATION;
            case RESOURCE_SITE_HARVEST -> RESOURCE_SITE_HARVEST;
            case PRODUCTION_TRANSFORMATION -> PRODUCTION_WORK;
            case CARGO_LOADING, CARGO_HANDOFF -> ROUTE_OPERATION;
            case ROUTE_CONSTRUCTION, ROUTE_CONSTRUCTION_MATERIAL_LOADING, ROUTE_MAINTENANCE,
                    ROUTE_MAINTENANCE_MATERIAL_LOADING, STRUCTURAL_REPAIR -> ENGINEERING_WORKSITE;
            case HIVE_NUTRIENT_DEPARTURE, HIVE_NUTRIENT_ARRIVAL -> HIVE_NUTRIENT_TRANSFER;
            case EQUIPMENT_ISSUE, EQUIPMENT_RETURN -> SETTLEMENT_ASSAULT;
            case SETTLEMENT_SERVICE_INPUT_ISSUE, DECONTAMINATION -> SETTLEMENT_SERVICE_WORK;
            case EXPLOSION -> HIVE_MOBILIZATION;
            case SCENE_STRIKE -> ROUTE_ENGAGEMENT;
            case EXACT_ITEM_CONSUMPTION -> SETTLEMENT_PROVISION;
        };
    }

    public boolean supports(PhysicalIntentKind kind) {
        return switch (this) {
            case RESOURCE_SITE_PREPARATION -> kind == PhysicalIntentKind.RESOURCE_SITE_PREPARATION;
            case RESOURCE_SITE_HARVEST -> kind == PhysicalIntentKind.RESOURCE_SITE_HARVEST;
            case PRODUCTION_WORK -> kind == PhysicalIntentKind.PRODUCTION_TRANSFORMATION;
            case ROUTE_OPERATION -> kind == PhysicalIntentKind.CARGO_LOADING || kind == PhysicalIntentKind.CARGO_HANDOFF;
            case ENGINEERING_WORKSITE -> kind == PhysicalIntentKind.ROUTE_CONSTRUCTION || kind == PhysicalIntentKind.ROUTE_CONSTRUCTION_MATERIAL_LOADING
                    || kind == PhysicalIntentKind.ROUTE_MAINTENANCE || kind == PhysicalIntentKind.ROUTE_MAINTENANCE_MATERIAL_LOADING
                    || kind == PhysicalIntentKind.STRUCTURAL_REPAIR || kind == PhysicalIntentKind.EQUIPMENT_ISSUE || kind == PhysicalIntentKind.EQUIPMENT_RETURN;
            case HIVE_NUTRIENT_TRANSFER -> kind == PhysicalIntentKind.HIVE_NUTRIENT_DEPARTURE || kind == PhysicalIntentKind.HIVE_NUTRIENT_ARRIVAL;
            case SETTLEMENT_SERVICE_WORK -> kind == PhysicalIntentKind.SETTLEMENT_SERVICE_INPUT_ISSUE || kind == PhysicalIntentKind.DECONTAMINATION;
            case HIVE_MOBILIZATION -> kind == PhysicalIntentKind.EXPLOSION;
            case ROUTE_ENGAGEMENT -> kind == PhysicalIntentKind.SCENE_STRIKE;
            case SETTLEMENT_ASSAULT -> kind == PhysicalIntentKind.SCENE_STRIKE || kind == PhysicalIntentKind.EQUIPMENT_ISSUE
                    || kind == PhysicalIntentKind.EQUIPMENT_RETURN;
            case POPULATION_MIGRATION, MEDICAL_TREATMENT, HIVE_GROWTH, SETTLEMENT_PROVISION -> kind == PhysicalIntentKind.EXACT_ITEM_CONSUMPTION;
            case ROUTE_PATROL, AMBIENT_ACTOR_CUSTODY -> false;
        };
    }
}
