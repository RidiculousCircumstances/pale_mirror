package io.farfrontier.palemirror.frontier.v3.api;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Producer-stamped exact semantic subjects for one physical intent. */
public final class PhysicalIntentRoleBinding {
    private final PhysicalIntentRoleSchema schema;
    private final Map<PhysicalIntentSubjectRole, SubjectId> roles;
    private final java.util.Optional<PhysicalSceneBinding> scene;

    private PhysicalIntentRoleBinding(PhysicalIntentRoleSchema schema, Map<PhysicalIntentSubjectRole, SubjectId> roles) {
        this(schema, roles, java.util.Optional.empty());
    }

    private PhysicalIntentRoleBinding(PhysicalIntentRoleSchema schema, Map<PhysicalIntentSubjectRole, SubjectId> roles,
                                      java.util.Optional<PhysicalSceneBinding> scene) {
        this.schema = Objects.requireNonNull(schema, "physical intent role schema");
        this.scene = Objects.requireNonNull(scene, "physical scene binding");
        if ((schema.kind() == PhysicalIntentKind.SCENE_STRIKE) != scene.isPresent()) {
            throw new IllegalArgumentException("scene strike requires its explicit scene binding; other roles cannot carry one");
        }
        EnumMap<PhysicalIntentSubjectRole, SubjectId> copy = new EnumMap<>(PhysicalIntentSubjectRole.class);
        roles.forEach((role, id) -> copy.put(Objects.requireNonNull(role, "physical intent subject role"), Objects.requireNonNull(id, "physical intent role subject")));
        if (copy.size() != roles.size() || copy.values().stream().distinct().count() != copy.size()) throw new IllegalArgumentException("physical intent role subjects must be distinct");
        if (!copy.keySet().equals(schema.roles())) throw new IllegalArgumentException("physical intent role binding does not match " + schema);
        this.roles = Map.copyOf(copy);
    }

    public PhysicalIntentRoleSchema schema() { return schema; }
    public java.util.Optional<PhysicalSceneBinding> scene() { return scene; }
    public PhysicalIntentKind kind() { return schema.kind(); }
    public SubjectId require(PhysicalIntentSubjectRole role) { SubjectId value = roles.get(Objects.requireNonNull(role)); if (value == null) throw new IllegalArgumentException("physical intent " + schema + " lacks role " + role); return value; }
    public Map<PhysicalIntentSubjectRole, SubjectId> namedRoles() { return roles; }
    /** Read-only compatibility/index projection. Never use for behavior, custody or retirement authority. */
    public List<SubjectId> subjectIds() { return roles.entrySet().stream().sorted(Map.Entry.comparingByKey(java.util.Comparator.comparingInt(PhysicalIntentSubjectRole::wireTag))).map(Map.Entry::getValue).toList(); }
    public void validate(PhysicalIntentLifecycleOwner owner, PhysicalIntentKind kind) { if (schema.owner() != owner || schema.kind() != kind) throw new IllegalArgumentException("physical intent owner/kind/role-schema mismatch: " + schema); }
    @Override public boolean equals(Object other) { return other instanceof PhysicalIntentRoleBinding binding && schema == binding.schema && roles.equals(binding.roles) && scene.equals(binding.scene); }
    @Override public int hashCode() { return Objects.hash(schema, roles, scene); }

    public static PhysicalIntentRoleBinding cargoHandoff(SubjectId operation, SubjectId cargo) { return bind(PhysicalIntentRoleSchema.CARGO_HANDOFF, map(PhysicalIntentSubjectRole.LOGISTICS_OPERATION, operation, PhysicalIntentSubjectRole.CARGO, cargo)); }
    public static PhysicalIntentRoleBinding structuralRepair(SubjectId structure, SubjectId material) { return bind(PhysicalIntentRoleSchema.STRUCTURAL_REPAIR, map(PhysicalIntentSubjectRole.STRUCTURE, structure, PhysicalIntentSubjectRole.MATERIAL, material)); }
    public static PhysicalIntentRoleBinding routeConstruction(SubjectId route, SubjectId project, SubjectId cargo, SubjectId material) { return bind(PhysicalIntentRoleSchema.ROUTE_CONSTRUCTION, map(PhysicalIntentSubjectRole.ROUTE, route, PhysicalIntentSubjectRole.ROUTE_CONSTRUCTION_PROJECT, project, PhysicalIntentSubjectRole.CARGO, cargo, PhysicalIntentSubjectRole.MATERIAL, material)); }
    public static PhysicalIntentRoleBinding decontamination(SubjectId facility, SubjectId material) { return bind(PhysicalIntentRoleSchema.DECONTAMINATION, map(PhysicalIntentSubjectRole.FACILITY, facility, PhysicalIntentSubjectRole.MATERIAL, material)); }
    public static PhysicalIntentRoleBinding serviceDecontamination(SubjectId work, SubjectId worker, SubjectId item) { return bind(PhysicalIntentRoleSchema.SERVICE_DECONTAMINATION, map(PhysicalIntentSubjectRole.SETTLEMENT_SERVICE_WORK, work, PhysicalIntentSubjectRole.WORKER, worker, PhysicalIntentSubjectRole.INPUT_ITEM, item)); }
    public static PhysicalIntentRoleBinding explosion(SubjectId bomber, SubjectId engagement) { return bind(PhysicalIntentRoleSchema.EXPLOSION, map(PhysicalIntentSubjectRole.BOMBER, bomber, PhysicalIntentSubjectRole.ENGAGEMENT, engagement)); }
    public static PhysicalIntentRoleBinding routeSceneStrike(SubjectId attacker, SubjectId target, SceneLeaseId lease, long revision) { return new PhysicalIntentRoleBinding(PhysicalIntentRoleSchema.ROUTE_SCENE_STRIKE, map(PhysicalIntentSubjectRole.ATTACKER, attacker, PhysicalIntentSubjectRole.TARGET, target), java.util.Optional.of(new PhysicalSceneBinding(lease, revision))); }
    public static PhysicalIntentRoleBinding assaultSceneStrike(SubjectId attacker, SubjectId target, SceneLeaseId lease, long revision) { return new PhysicalIntentRoleBinding(PhysicalIntentRoleSchema.ASSAULT_SCENE_STRIKE, map(PhysicalIntentSubjectRole.ATTACKER, attacker, PhysicalIntentSubjectRole.TARGET, target), java.util.Optional.of(new PhysicalSceneBinding(lease, revision))); }
    public static PhysicalIntentRoleBinding hiveGrowthConsumption(SubjectId job, SubjectId item) { return bind(PhysicalIntentRoleSchema.HIVE_GROWTH_CONSUMPTION, map(PhysicalIntentSubjectRole.HIVE_GROWTH_JOB, job, PhysicalIntentSubjectRole.ITEM, item)); }
    public static PhysicalIntentRoleBinding medicalTreatmentConsumption(SubjectId operation, SubjectId item) { return bind(PhysicalIntentRoleSchema.MEDICAL_TREATMENT_CONSUMPTION, map(PhysicalIntentSubjectRole.MEDICAL_TREATMENT_OPERATION, operation, PhysicalIntentSubjectRole.ITEM, item)); }
    public static PhysicalIntentRoleBinding settlementProvisionConsumption(SubjectId provision, SubjectId item) { return bind(PhysicalIntentRoleSchema.SETTLEMENT_PROVISION_CONSUMPTION, map(PhysicalIntentSubjectRole.SETTLEMENT_PROVISION, provision, PhysicalIntentSubjectRole.ITEM, item)); }
    public static PhysicalIntentRoleBinding sitePreparation(SubjectId site, SubjectId job) { return bind(PhysicalIntentRoleSchema.RESOURCE_SITE_PREPARATION, map(PhysicalIntentSubjectRole.SITE, site, PhysicalIntentSubjectRole.RESOURCE_SITE_JOB, job)); }
    public static PhysicalIntentRoleBinding siteHarvest(SubjectId site, SubjectId job, SubjectId worker, SubjectId output) { return bind(PhysicalIntentRoleSchema.RESOURCE_SITE_HARVEST, map(PhysicalIntentSubjectRole.SITE, site, PhysicalIntentSubjectRole.RESOURCE_SITE_JOB, job, PhysicalIntentSubjectRole.WORKER, worker, PhysicalIntentSubjectRole.OUTPUT_ITEM, output)); }
    public static PhysicalIntentRoleBinding production(SubjectId job, SubjectId input, SubjectId output) { return bind(PhysicalIntentRoleSchema.PRODUCTION, map(PhysicalIntentSubjectRole.PRODUCTION_JOB, job, PhysicalIntentSubjectRole.INPUT_ITEM, input, PhysicalIntentSubjectRole.OUTPUT_ITEM, output)); }
    public static PhysicalIntentRoleBinding cargoLoading(SubjectId contract, SubjectId cargo, SubjectId sourceItem) { return bind(PhysicalIntentRoleSchema.CARGO_LOADING, map(PhysicalIntentSubjectRole.CONTRACT, contract, PhysicalIntentSubjectRole.CARGO, cargo, PhysicalIntentSubjectRole.SOURCE_ITEM, sourceItem)); }
    public static PhysicalIntentRoleBinding routeConstructionLoading(SubjectId route, SubjectId project, SubjectId cargo, SubjectId cargoItem, SubjectId sourceItem) { return bind(PhysicalIntentRoleSchema.ROUTE_CONSTRUCTION_LOADING, map(PhysicalIntentSubjectRole.ROUTE, route, PhysicalIntentSubjectRole.ROUTE_CONSTRUCTION_PROJECT, project, PhysicalIntentSubjectRole.CARGO, cargo, PhysicalIntentSubjectRole.CARGO_ITEM, cargoItem, PhysicalIntentSubjectRole.SOURCE_ITEM, sourceItem)); }
    public static PhysicalIntentRoleBinding nutrientDeparture(SubjectId transfer, SubjectId cargo, SubjectId item) { return bind(PhysicalIntentRoleSchema.NUTRIENT_DEPARTURE, map(PhysicalIntentSubjectRole.TRANSFER, transfer, PhysicalIntentSubjectRole.CARGO, cargo, PhysicalIntentSubjectRole.SOURCE_ITEM, item)); }
    public static PhysicalIntentRoleBinding nutrientArrival(SubjectId transfer, SubjectId cargo, SubjectId item) { return bind(PhysicalIntentRoleSchema.NUTRIENT_ARRIVAL, map(PhysicalIntentSubjectRole.TRANSFER, transfer, PhysicalIntentSubjectRole.CARGO, cargo, PhysicalIntentSubjectRole.TARGET_ITEM, item)); }
    public static PhysicalIntentRoleBinding engineeringEquipmentIssue(SubjectId workOrder, SubjectId worker, SubjectId equipment) { return bind(PhysicalIntentRoleSchema.ENGINEERING_EQUIPMENT_ISSUE, map(PhysicalIntentSubjectRole.ENGINEERING_WORK_ORDER, workOrder, PhysicalIntentSubjectRole.ENGINEERING_WORKER, worker, PhysicalIntentSubjectRole.EQUIPMENT, equipment)); }
    public static PhysicalIntentRoleBinding engineeringEquipmentReturn(SubjectId workOrder, SubjectId worker, SubjectId equipment) { return bind(PhysicalIntentRoleSchema.ENGINEERING_EQUIPMENT_RETURN, map(PhysicalIntentSubjectRole.ENGINEERING_WORK_ORDER, workOrder, PhysicalIntentSubjectRole.ENGINEERING_WORKER, worker, PhysicalIntentSubjectRole.EQUIPMENT, equipment)); }
    public static PhysicalIntentRoleBinding assaultEquipmentIssue(SubjectId assault, SubjectId defender, SubjectId equipment) { return bind(PhysicalIntentRoleSchema.ASSAULT_EQUIPMENT_ISSUE, map(PhysicalIntentSubjectRole.SETTLEMENT_ASSAULT, assault, PhysicalIntentSubjectRole.ASSAULT_DEFENDER, defender, PhysicalIntentSubjectRole.EQUIPMENT, equipment)); }
    public static PhysicalIntentRoleBinding assaultEquipmentReturn(SubjectId assault, SubjectId defender, SubjectId equipment) { return bind(PhysicalIntentRoleSchema.ASSAULT_EQUIPMENT_RETURN, map(PhysicalIntentSubjectRole.SETTLEMENT_ASSAULT, assault, PhysicalIntentSubjectRole.ASSAULT_DEFENDER, defender, PhysicalIntentSubjectRole.EQUIPMENT, equipment)); }
    public static PhysicalIntentRoleBinding routeMaintenance(SubjectId route, SubjectId operation, SubjectId cargo, SubjectId material) { return bind(PhysicalIntentRoleSchema.ROUTE_MAINTENANCE, map(PhysicalIntentSubjectRole.ROUTE, route, PhysicalIntentSubjectRole.ROUTE_MAINTENANCE, operation, PhysicalIntentSubjectRole.CARGO, cargo, PhysicalIntentSubjectRole.MATERIAL, material)); }
    public static PhysicalIntentRoleBinding routeMaintenanceLoading(SubjectId route, SubjectId operation, SubjectId cargo, SubjectId cargoItem, SubjectId sourceItem) { return bind(PhysicalIntentRoleSchema.ROUTE_MAINTENANCE_LOADING, map(PhysicalIntentSubjectRole.ROUTE, route, PhysicalIntentSubjectRole.ROUTE_MAINTENANCE, operation, PhysicalIntentSubjectRole.CARGO, cargo, PhysicalIntentSubjectRole.CARGO_ITEM, cargoItem, PhysicalIntentSubjectRole.SOURCE_ITEM, sourceItem)); }
    public static PhysicalIntentRoleBinding serviceInputIssue(SubjectId work, SubjectId worker, SubjectId item) { return bind(PhysicalIntentRoleSchema.SERVICE_INPUT_ISSUE, map(PhysicalIntentSubjectRole.SETTLEMENT_SERVICE_WORK, work, PhysicalIntentSubjectRole.WORKER, worker, PhysicalIntentSubjectRole.INPUT_ITEM, item)); }
    public static PhysicalIntentRoleBinding productionResources(SubjectId job, SubjectId input, SubjectId output,
                                                               SubjectId claim, SubjectId account, SubjectId container) {
        return bind(PhysicalIntentRoleSchema.PRODUCTION_RESOURCES, map(PhysicalIntentSubjectRole.PRODUCTION_JOB, job,
                PhysicalIntentSubjectRole.INPUT_RESOURCE_LOT, input, PhysicalIntentSubjectRole.OUTPUT_RESOURCE_LOT, output,
                PhysicalIntentSubjectRole.RESOURCE_CLAIM, claim, PhysicalIntentSubjectRole.CUSTODY_ACCOUNT, account,
                PhysicalIntentSubjectRole.RESOURCE_CONTAINER, container));
    }
    public static PhysicalIntentRoleBinding decode(PhysicalIntentRoleSchema schema, Map<PhysicalIntentSubjectRole, SubjectId> values) { return bind(schema, values); }
    public static PhysicalIntentRoleBinding decode(PhysicalIntentRoleSchema schema, Map<PhysicalIntentSubjectRole, SubjectId> values,
                                                   java.util.Optional<PhysicalSceneBinding> scene) { return new PhysicalIntentRoleBinding(schema, values, scene); }

    private static PhysicalIntentRoleBinding bind(PhysicalIntentRoleSchema schema, Map<PhysicalIntentSubjectRole, SubjectId> values) { return new PhysicalIntentRoleBinding(schema, values); }
    private static Map<PhysicalIntentSubjectRole, SubjectId> map(Object... entries) { EnumMap<PhysicalIntentSubjectRole, SubjectId> result = new EnumMap<>(PhysicalIntentSubjectRole.class); for (int index = 0; index < entries.length; index += 2) { SubjectId prior = result.put((PhysicalIntentSubjectRole) entries[index], (SubjectId) entries[index + 1]); if (prior != null) throw new IllegalArgumentException("duplicate physical intent role"); } return result; }
}
