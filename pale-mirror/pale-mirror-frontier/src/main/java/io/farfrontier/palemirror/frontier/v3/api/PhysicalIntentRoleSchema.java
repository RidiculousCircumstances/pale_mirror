package io.farfrontier.palemirror.frontier.v3.api;

import java.util.Arrays;
import java.util.Set;

/**
 * Durable closed semantic-role contract for one lifecycle owner and intent kind.
 *
 * <p>Its tag is persisted beside the named role tags.  A role set alone is not a contract:
 * multiple owners can legally emit the same physical kind, so recovery must retain the exact
 * producer-stamped schema rather than reconstruct it from kind or aggregate membership.</p>
 */
public enum PhysicalIntentRoleSchema {
    CARGO_HANDOFF(1, PhysicalIntentLifecycleOwner.ROUTE_OPERATION, PhysicalIntentKind.CARGO_HANDOFF,
            PhysicalIntentSubjectRole.LOGISTICS_OPERATION, PhysicalIntentSubjectRole.CARGO),
    STRUCTURAL_REPAIR(2, PhysicalIntentLifecycleOwner.ENGINEERING_WORKSITE, PhysicalIntentKind.STRUCTURAL_REPAIR,
            PhysicalIntentSubjectRole.STRUCTURE, PhysicalIntentSubjectRole.MATERIAL),
    ROUTE_CONSTRUCTION(3, PhysicalIntentLifecycleOwner.ENGINEERING_WORKSITE, PhysicalIntentKind.ROUTE_CONSTRUCTION,
            PhysicalIntentSubjectRole.ROUTE, PhysicalIntentSubjectRole.ROUTE_CONSTRUCTION_PROJECT, PhysicalIntentSubjectRole.CARGO, PhysicalIntentSubjectRole.MATERIAL),
    DECONTAMINATION(4, PhysicalIntentLifecycleOwner.DECONTAMINATION, PhysicalIntentKind.DECONTAMINATION,
            PhysicalIntentSubjectRole.FACILITY, PhysicalIntentSubjectRole.MATERIAL),
    SERVICE_DECONTAMINATION(5, PhysicalIntentLifecycleOwner.SETTLEMENT_SERVICE_DECONTAMINATION, PhysicalIntentKind.DECONTAMINATION,
            PhysicalIntentSubjectRole.SETTLEMENT_SERVICE_WORK, PhysicalIntentSubjectRole.WORKER, PhysicalIntentSubjectRole.INPUT_ITEM),
    EXPLOSION(6, PhysicalIntentLifecycleOwner.HIVE_MOBILIZATION, PhysicalIntentKind.EXPLOSION,
            PhysicalIntentSubjectRole.BOMBER, PhysicalIntentSubjectRole.ENGAGEMENT),
    // Tags 7/8 are retired: they omitted the mandatory exact scene binding.
    ROUTE_SCENE_STRIKE(28, PhysicalIntentLifecycleOwner.ROUTE_ENGAGEMENT, PhysicalIntentKind.SCENE_STRIKE,
            PhysicalIntentSubjectRole.ATTACKER, PhysicalIntentSubjectRole.TARGET),
    ASSAULT_SCENE_STRIKE(29, PhysicalIntentLifecycleOwner.SETTLEMENT_ASSAULT, PhysicalIntentKind.SCENE_STRIKE,
            PhysicalIntentSubjectRole.ATTACKER, PhysicalIntentSubjectRole.TARGET),
    HIVE_GROWTH_CONSUMPTION(9, PhysicalIntentLifecycleOwner.HIVE_GROWTH, PhysicalIntentKind.EXACT_ITEM_CONSUMPTION,
            PhysicalIntentSubjectRole.HIVE_GROWTH_JOB, PhysicalIntentSubjectRole.ITEM),
    MEDICAL_TREATMENT_CONSUMPTION(11, PhysicalIntentLifecycleOwner.MEDICAL_TREATMENT, PhysicalIntentKind.EXACT_ITEM_CONSUMPTION,
            PhysicalIntentSubjectRole.MEDICAL_TREATMENT_OPERATION, PhysicalIntentSubjectRole.ITEM),
    SETTLEMENT_PROVISION_CONSUMPTION(12, PhysicalIntentLifecycleOwner.SETTLEMENT_PROVISION, PhysicalIntentKind.EXACT_ITEM_CONSUMPTION,
            PhysicalIntentSubjectRole.SETTLEMENT_PROVISION, PhysicalIntentSubjectRole.ITEM),
    RESOURCE_SITE_PREPARATION(13, PhysicalIntentLifecycleOwner.RESOURCE_SITE_PREPARATION, PhysicalIntentKind.RESOURCE_SITE_PREPARATION,
            PhysicalIntentSubjectRole.SITE, PhysicalIntentSubjectRole.RESOURCE_SITE_JOB),
    RESOURCE_SITE_HARVEST(14, PhysicalIntentLifecycleOwner.RESOURCE_SITE_HARVEST, PhysicalIntentKind.RESOURCE_SITE_HARVEST,
            PhysicalIntentSubjectRole.SITE, PhysicalIntentSubjectRole.RESOURCE_SITE_JOB, PhysicalIntentSubjectRole.WORKER, PhysicalIntentSubjectRole.OUTPUT_ITEM),
    PRODUCTION(15, PhysicalIntentLifecycleOwner.PRODUCTION_WORK, PhysicalIntentKind.PRODUCTION_TRANSFORMATION,
            PhysicalIntentSubjectRole.PRODUCTION_JOB, PhysicalIntentSubjectRole.INPUT_ITEM, PhysicalIntentSubjectRole.OUTPUT_ITEM),
    CARGO_LOADING(16, PhysicalIntentLifecycleOwner.ROUTE_OPERATION, PhysicalIntentKind.CARGO_LOADING,
            PhysicalIntentSubjectRole.CONTRACT, PhysicalIntentSubjectRole.CARGO, PhysicalIntentSubjectRole.SOURCE_ITEM),
    ROUTE_CONSTRUCTION_LOADING(17, PhysicalIntentLifecycleOwner.ENGINEERING_WORKSITE, PhysicalIntentKind.ROUTE_CONSTRUCTION_MATERIAL_LOADING,
            PhysicalIntentSubjectRole.ROUTE, PhysicalIntentSubjectRole.ROUTE_CONSTRUCTION_PROJECT, PhysicalIntentSubjectRole.CARGO, PhysicalIntentSubjectRole.CARGO_ITEM, PhysicalIntentSubjectRole.SOURCE_ITEM),
    NUTRIENT_DEPARTURE(18, PhysicalIntentLifecycleOwner.HIVE_NUTRIENT_TRANSFER, PhysicalIntentKind.HIVE_NUTRIENT_DEPARTURE,
            PhysicalIntentSubjectRole.TRANSFER, PhysicalIntentSubjectRole.CARGO, PhysicalIntentSubjectRole.SOURCE_ITEM),
    NUTRIENT_ARRIVAL(19, PhysicalIntentLifecycleOwner.HIVE_NUTRIENT_TRANSFER, PhysicalIntentKind.HIVE_NUTRIENT_ARRIVAL,
            PhysicalIntentSubjectRole.TRANSFER, PhysicalIntentSubjectRole.CARGO, PhysicalIntentSubjectRole.TARGET_ITEM),
    ENGINEERING_EQUIPMENT_ISSUE(20, PhysicalIntentLifecycleOwner.ENGINEERING_WORKSITE, PhysicalIntentKind.EQUIPMENT_ISSUE,
            PhysicalIntentSubjectRole.ENGINEERING_WORK_ORDER, PhysicalIntentSubjectRole.ENGINEERING_WORKER, PhysicalIntentSubjectRole.EQUIPMENT),
    ENGINEERING_EQUIPMENT_RETURN(21, PhysicalIntentLifecycleOwner.ENGINEERING_WORKSITE, PhysicalIntentKind.EQUIPMENT_RETURN,
            PhysicalIntentSubjectRole.ENGINEERING_WORK_ORDER, PhysicalIntentSubjectRole.ENGINEERING_WORKER, PhysicalIntentSubjectRole.EQUIPMENT),
    ASSAULT_EQUIPMENT_ISSUE(22, PhysicalIntentLifecycleOwner.SETTLEMENT_ASSAULT, PhysicalIntentKind.EQUIPMENT_ISSUE,
            PhysicalIntentSubjectRole.SETTLEMENT_ASSAULT, PhysicalIntentSubjectRole.ASSAULT_DEFENDER, PhysicalIntentSubjectRole.EQUIPMENT),
    ASSAULT_EQUIPMENT_RETURN(23, PhysicalIntentLifecycleOwner.SETTLEMENT_ASSAULT, PhysicalIntentKind.EQUIPMENT_RETURN,
            PhysicalIntentSubjectRole.SETTLEMENT_ASSAULT, PhysicalIntentSubjectRole.ASSAULT_DEFENDER, PhysicalIntentSubjectRole.EQUIPMENT),
    ROUTE_MAINTENANCE(24, PhysicalIntentLifecycleOwner.ENGINEERING_WORKSITE, PhysicalIntentKind.ROUTE_MAINTENANCE,
            PhysicalIntentSubjectRole.ROUTE, PhysicalIntentSubjectRole.ROUTE_MAINTENANCE, PhysicalIntentSubjectRole.CARGO, PhysicalIntentSubjectRole.MATERIAL),
    ROUTE_MAINTENANCE_LOADING(25, PhysicalIntentLifecycleOwner.ENGINEERING_WORKSITE, PhysicalIntentKind.ROUTE_MAINTENANCE_MATERIAL_LOADING,
            PhysicalIntentSubjectRole.ROUTE, PhysicalIntentSubjectRole.ROUTE_MAINTENANCE, PhysicalIntentSubjectRole.CARGO, PhysicalIntentSubjectRole.CARGO_ITEM, PhysicalIntentSubjectRole.SOURCE_ITEM),
    SERVICE_INPUT_ISSUE(26, PhysicalIntentLifecycleOwner.SETTLEMENT_SERVICE_WORK, PhysicalIntentKind.SETTLEMENT_SERVICE_INPUT_ISSUE,
            PhysicalIntentSubjectRole.SETTLEMENT_SERVICE_WORK, PhysicalIntentSubjectRole.WORKER, PhysicalIntentSubjectRole.INPUT_ITEM),
    PRODUCTION_RESOURCES(27, PhysicalIntentLifecycleOwner.PRODUCTION_WORK, PhysicalIntentKind.PRODUCTION_TRANSFORMATION,
            PhysicalIntentSubjectRole.PRODUCTION_JOB, PhysicalIntentSubjectRole.INPUT_RESOURCE_LOT, PhysicalIntentSubjectRole.OUTPUT_RESOURCE_LOT,
            PhysicalIntentSubjectRole.RESOURCE_CLAIM, PhysicalIntentSubjectRole.CUSTODY_ACCOUNT, PhysicalIntentSubjectRole.RESOURCE_CONTAINER);

    private final int wireTag;
    private final PhysicalIntentLifecycleOwner owner;
    private final PhysicalIntentKind kind;
    private final Set<PhysicalIntentSubjectRole> roles;

    PhysicalIntentRoleSchema(int wireTag, PhysicalIntentLifecycleOwner owner, PhysicalIntentKind kind,
                             PhysicalIntentSubjectRole... roles) {
        this.wireTag = wireTag;
        this.owner = owner;
        this.kind = kind;
        this.roles = Set.of(roles);
    }

    public int wireTag() { return wireTag; }
    public PhysicalIntentLifecycleOwner owner() { return owner; }
    public PhysicalIntentKind kind() { return kind; }
    public Set<PhysicalIntentSubjectRole> roles() { return roles; }

    public static PhysicalIntentRoleSchema fromWire(int wireTag) {
        return Arrays.stream(values()).filter(schema -> schema.wireTag == wireTag).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown physical intent role-schema tag: " + wireTag));
    }
}
