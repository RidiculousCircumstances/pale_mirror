package io.farfrontier.palemirror.frontier.v3.api;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Producer-stamped authoritative semantic subjects for one physical intent.
 *
 * <p>This nominal value replaces positional {@code subjectIds}.  Its factories name every
 * meaning at the producing owner, and its deterministic compatibility projection is expressly
 * read-only.</p>
 */
public final class PhysicalIntentRoleBinding {
    private final PhysicalIntentKind kind;
    private final Map<PhysicalIntentSubjectRole, SubjectId> roles;

    private PhysicalIntentRoleBinding(PhysicalIntentKind kind, Map<PhysicalIntentSubjectRole, SubjectId> roles) {
        this.kind = Objects.requireNonNull(kind, "physical intent role kind");
        EnumMap<PhysicalIntentSubjectRole, SubjectId> copy = new EnumMap<>(PhysicalIntentSubjectRole.class);
        roles.forEach((role, id) -> copy.put(Objects.requireNonNull(role, "physical intent subject role"), Objects.requireNonNull(id, "physical intent role subject")));
        if (copy.size() != roles.size() || copy.values().stream().distinct().count() != copy.size()) throw new IllegalArgumentException("physical intent role subjects must be distinct");
        if (!valid(kind, copy.keySet())) throw new IllegalArgumentException("physical intent role binding does not match " + kind);
        this.roles = Map.copyOf(copy);
    }

    public PhysicalIntentKind kind() { return kind; }
    public SubjectId require(PhysicalIntentSubjectRole role) {
        SubjectId value = roles.get(Objects.requireNonNull(role));
        if (value == null) throw new IllegalArgumentException("physical intent " + kind + " lacks role " + role);
        return value;
    }
    public Map<PhysicalIntentSubjectRole, SubjectId> namedRoles() { return roles; }
    /** Read-only compatibility/index projection. Never use for behavior, custody or retirement authority. */
    public List<SubjectId> subjectIds() { return roles.entrySet().stream().sorted(Map.Entry.comparingByKey(java.util.Comparator.comparingInt(PhysicalIntentSubjectRole::wireTag))).map(Map.Entry::getValue).toList(); }

    @Override public boolean equals(Object other) {
        return other instanceof PhysicalIntentRoleBinding binding && kind == binding.kind && roles.equals(binding.roles);
    }

    @Override public int hashCode() { return Objects.hash(kind, roles); }

    public static PhysicalIntentRoleBinding cargoHandoff(SubjectId operation, SubjectId cargo) { return bind(PhysicalIntentKind.CARGO_HANDOFF, map(PhysicalIntentSubjectRole.OPERATION, operation, PhysicalIntentSubjectRole.CARGO, cargo)); }
    /** Current-format persistence/recovery entrypoint. It validates every tag and exact role set. */
    public static PhysicalIntentRoleBinding decode(PhysicalIntentKind kind, Map<PhysicalIntentSubjectRole, SubjectId> values) { return bind(kind, values); }
    public static PhysicalIntentRoleBinding structuralRepair(SubjectId structure, SubjectId material) { return bind(PhysicalIntentKind.STRUCTURAL_REPAIR, map(PhysicalIntentSubjectRole.STRUCTURE, structure, PhysicalIntentSubjectRole.MATERIAL, material)); }
    public static PhysicalIntentRoleBinding routeConstruction(SubjectId route, SubjectId project, SubjectId cargo, SubjectId material) { return bind(PhysicalIntentKind.ROUTE_CONSTRUCTION, map(PhysicalIntentSubjectRole.ROUTE, route, PhysicalIntentSubjectRole.PROJECT, project, PhysicalIntentSubjectRole.CARGO, cargo, PhysicalIntentSubjectRole.MATERIAL, material)); }
    public static PhysicalIntentRoleBinding decontamination(SubjectId facility, SubjectId material) { return bind(PhysicalIntentKind.DECONTAMINATION, map(PhysicalIntentSubjectRole.FACILITY, facility, PhysicalIntentSubjectRole.MATERIAL, material)); }
    public static PhysicalIntentRoleBinding serviceDecontamination(SubjectId work, SubjectId worker, SubjectId item) { return bind(PhysicalIntentKind.DECONTAMINATION, map(PhysicalIntentSubjectRole.PROJECT, work, PhysicalIntentSubjectRole.WORKER, worker, PhysicalIntentSubjectRole.INPUT_ITEM, item)); }
    public static PhysicalIntentRoleBinding explosion(SubjectId bomber, SubjectId engagement) { return bind(PhysicalIntentKind.EXPLOSION, map(PhysicalIntentSubjectRole.BOMBER, bomber, PhysicalIntentSubjectRole.ENGAGEMENT, engagement)); }
    public static PhysicalIntentRoleBinding sceneStrike(SubjectId attacker, SubjectId target) { return bind(PhysicalIntentKind.SCENE_STRIKE, map(PhysicalIntentSubjectRole.ATTACKER, attacker, PhysicalIntentSubjectRole.TARGET, target)); }
    public static PhysicalIntentRoleBinding exactConsumption(SubjectId owner, SubjectId item) { return bind(PhysicalIntentKind.EXACT_ITEM_CONSUMPTION, map(PhysicalIntentSubjectRole.OWNER, owner, PhysicalIntentSubjectRole.ITEM, item)); }
    public static PhysicalIntentRoleBinding sitePreparation(SubjectId site, SubjectId job) { return bind(PhysicalIntentKind.RESOURCE_SITE_PREPARATION, map(PhysicalIntentSubjectRole.SITE, site, PhysicalIntentSubjectRole.JOB, job)); }
    public static PhysicalIntentRoleBinding siteHarvest(SubjectId site, SubjectId job, SubjectId worker, SubjectId output) { return bind(PhysicalIntentKind.RESOURCE_SITE_HARVEST, map(PhysicalIntentSubjectRole.SITE, site, PhysicalIntentSubjectRole.JOB, job, PhysicalIntentSubjectRole.WORKER, worker, PhysicalIntentSubjectRole.OUTPUT_ITEM, output)); }
    public static PhysicalIntentRoleBinding production(SubjectId job, SubjectId input, SubjectId output) { return bind(PhysicalIntentKind.PRODUCTION_TRANSFORMATION, map(PhysicalIntentSubjectRole.JOB, job, PhysicalIntentSubjectRole.INPUT_ITEM, input, PhysicalIntentSubjectRole.OUTPUT_ITEM, output)); }
    public static PhysicalIntentRoleBinding cargoLoading(SubjectId contract, SubjectId cargo, SubjectId sourceItem) { return bind(PhysicalIntentKind.CARGO_LOADING, map(PhysicalIntentSubjectRole.CONTRACT, contract, PhysicalIntentSubjectRole.CARGO, cargo, PhysicalIntentSubjectRole.SOURCE_ITEM, sourceItem)); }
    public static PhysicalIntentRoleBinding routeConstructionLoading(SubjectId route, SubjectId project, SubjectId cargo, SubjectId cargoItem, SubjectId sourceItem) { return bind(PhysicalIntentKind.ROUTE_CONSTRUCTION_MATERIAL_LOADING, map(PhysicalIntentSubjectRole.ROUTE, route, PhysicalIntentSubjectRole.PROJECT, project, PhysicalIntentSubjectRole.CARGO, cargo, PhysicalIntentSubjectRole.CARGO_ITEM, cargoItem, PhysicalIntentSubjectRole.SOURCE_ITEM, sourceItem)); }
    public static PhysicalIntentRoleBinding nutrientDeparture(SubjectId transfer, SubjectId cargo, SubjectId item) { return bind(PhysicalIntentKind.HIVE_NUTRIENT_DEPARTURE, map(PhysicalIntentSubjectRole.TRANSFER, transfer, PhysicalIntentSubjectRole.CARGO, cargo, PhysicalIntentSubjectRole.SOURCE_ITEM, item)); }
    public static PhysicalIntentRoleBinding nutrientArrival(SubjectId transfer, SubjectId cargo, SubjectId item) { return bind(PhysicalIntentKind.HIVE_NUTRIENT_ARRIVAL, map(PhysicalIntentSubjectRole.TRANSFER, transfer, PhysicalIntentSubjectRole.CARGO, cargo, PhysicalIntentSubjectRole.TARGET_ITEM, item)); }
    public static PhysicalIntentRoleBinding equipmentIssue(SubjectId projectOrAssault, SubjectId defender, SubjectId equipment) { return bind(PhysicalIntentKind.EQUIPMENT_ISSUE, map(PhysicalIntentSubjectRole.PROJECT, projectOrAssault, PhysicalIntentSubjectRole.DEFENDER, defender, PhysicalIntentSubjectRole.EQUIPMENT, equipment)); }
    public static PhysicalIntentRoleBinding equipmentReturn(SubjectId projectOrAssault, SubjectId defender, SubjectId equipment) { return bind(PhysicalIntentKind.EQUIPMENT_RETURN, map(PhysicalIntentSubjectRole.PROJECT, projectOrAssault, PhysicalIntentSubjectRole.DEFENDER, defender, PhysicalIntentSubjectRole.EQUIPMENT, equipment)); }
    public static PhysicalIntentRoleBinding routeMaintenance(SubjectId route, SubjectId operation, SubjectId cargo, SubjectId material) { return bind(PhysicalIntentKind.ROUTE_MAINTENANCE, map(PhysicalIntentSubjectRole.ROUTE, route, PhysicalIntentSubjectRole.OPERATION, operation, PhysicalIntentSubjectRole.CARGO, cargo, PhysicalIntentSubjectRole.MATERIAL, material)); }
    public static PhysicalIntentRoleBinding routeMaintenanceLoading(SubjectId route, SubjectId operation, SubjectId cargo, SubjectId cargoItem, SubjectId sourceItem) { return bind(PhysicalIntentKind.ROUTE_MAINTENANCE_MATERIAL_LOADING, map(PhysicalIntentSubjectRole.ROUTE, route, PhysicalIntentSubjectRole.OPERATION, operation, PhysicalIntentSubjectRole.CARGO, cargo, PhysicalIntentSubjectRole.CARGO_ITEM, cargoItem, PhysicalIntentSubjectRole.SOURCE_ITEM, sourceItem)); }
    public static PhysicalIntentRoleBinding serviceInputIssue(SubjectId work, SubjectId worker, SubjectId item) { return bind(PhysicalIntentKind.SETTLEMENT_SERVICE_INPUT_ISSUE, map(PhysicalIntentSubjectRole.PROJECT, work, PhysicalIntentSubjectRole.WORKER, worker, PhysicalIntentSubjectRole.INPUT_ITEM, item)); }

    private static PhysicalIntentRoleBinding bind(PhysicalIntentKind kind, Map<PhysicalIntentSubjectRole, SubjectId> values) { return new PhysicalIntentRoleBinding(kind, values); }
    private static Map<PhysicalIntentSubjectRole, SubjectId> map(Object... entries) { EnumMap<PhysicalIntentSubjectRole, SubjectId> result = new EnumMap<>(PhysicalIntentSubjectRole.class); for (int index = 0; index < entries.length; index += 2) { SubjectId prior = result.put((PhysicalIntentSubjectRole) entries[index], (SubjectId) entries[index + 1]); if (prior != null) throw new IllegalArgumentException("duplicate physical intent role"); } return result; }
    private static boolean valid(PhysicalIntentKind kind, Set<PhysicalIntentSubjectRole> actual) {
        if (kind == PhysicalIntentKind.DECONTAMINATION) {
            return actual.equals(Set.of(PhysicalIntentSubjectRole.FACILITY, PhysicalIntentSubjectRole.MATERIAL))
                    || actual.equals(Set.of(PhysicalIntentSubjectRole.PROJECT, PhysicalIntentSubjectRole.WORKER, PhysicalIntentSubjectRole.INPUT_ITEM));
        }
        return actual.equals(required(kind));
    }
    private static Set<PhysicalIntentSubjectRole> required(PhysicalIntentKind kind) { return switch (kind) {
        case CARGO_HANDOFF -> Set.of(PhysicalIntentSubjectRole.OPERATION, PhysicalIntentSubjectRole.CARGO);
        case STRUCTURAL_REPAIR -> Set.of(PhysicalIntentSubjectRole.STRUCTURE, PhysicalIntentSubjectRole.MATERIAL);
        case ROUTE_CONSTRUCTION -> Set.of(PhysicalIntentSubjectRole.ROUTE, PhysicalIntentSubjectRole.PROJECT, PhysicalIntentSubjectRole.CARGO, PhysicalIntentSubjectRole.MATERIAL);
        case DECONTAMINATION -> Set.of(PhysicalIntentSubjectRole.FACILITY, PhysicalIntentSubjectRole.MATERIAL);
        case EXPLOSION -> Set.of(PhysicalIntentSubjectRole.BOMBER, PhysicalIntentSubjectRole.ENGAGEMENT);
        case SCENE_STRIKE -> Set.of(PhysicalIntentSubjectRole.ATTACKER, PhysicalIntentSubjectRole.TARGET);
        case EXACT_ITEM_CONSUMPTION -> Set.of(PhysicalIntentSubjectRole.OWNER, PhysicalIntentSubjectRole.ITEM);
        case RESOURCE_SITE_PREPARATION -> Set.of(PhysicalIntentSubjectRole.SITE, PhysicalIntentSubjectRole.JOB);
        case RESOURCE_SITE_HARVEST -> Set.of(PhysicalIntentSubjectRole.SITE, PhysicalIntentSubjectRole.JOB, PhysicalIntentSubjectRole.WORKER, PhysicalIntentSubjectRole.OUTPUT_ITEM);
        case PRODUCTION_TRANSFORMATION -> Set.of(PhysicalIntentSubjectRole.JOB, PhysicalIntentSubjectRole.INPUT_ITEM, PhysicalIntentSubjectRole.OUTPUT_ITEM);
        case CARGO_LOADING -> Set.of(PhysicalIntentSubjectRole.CONTRACT, PhysicalIntentSubjectRole.CARGO, PhysicalIntentSubjectRole.SOURCE_ITEM);
        case ROUTE_CONSTRUCTION_MATERIAL_LOADING -> Set.of(PhysicalIntentSubjectRole.ROUTE, PhysicalIntentSubjectRole.PROJECT, PhysicalIntentSubjectRole.CARGO, PhysicalIntentSubjectRole.CARGO_ITEM, PhysicalIntentSubjectRole.SOURCE_ITEM);
        case HIVE_NUTRIENT_DEPARTURE -> Set.of(PhysicalIntentSubjectRole.TRANSFER, PhysicalIntentSubjectRole.CARGO, PhysicalIntentSubjectRole.SOURCE_ITEM);
        case HIVE_NUTRIENT_ARRIVAL -> Set.of(PhysicalIntentSubjectRole.TRANSFER, PhysicalIntentSubjectRole.CARGO, PhysicalIntentSubjectRole.TARGET_ITEM);
        case EQUIPMENT_ISSUE, EQUIPMENT_RETURN -> Set.of(PhysicalIntentSubjectRole.PROJECT, PhysicalIntentSubjectRole.DEFENDER, PhysicalIntentSubjectRole.EQUIPMENT);
        case ROUTE_MAINTENANCE -> Set.of(PhysicalIntentSubjectRole.ROUTE, PhysicalIntentSubjectRole.OPERATION, PhysicalIntentSubjectRole.CARGO, PhysicalIntentSubjectRole.MATERIAL);
        case ROUTE_MAINTENANCE_MATERIAL_LOADING -> Set.of(PhysicalIntentSubjectRole.ROUTE, PhysicalIntentSubjectRole.OPERATION, PhysicalIntentSubjectRole.CARGO, PhysicalIntentSubjectRole.CARGO_ITEM, PhysicalIntentSubjectRole.SOURCE_ITEM);
        case SETTLEMENT_SERVICE_INPUT_ISSUE -> Set.of(PhysicalIntentSubjectRole.PROJECT, PhysicalIntentSubjectRole.WORKER, PhysicalIntentSubjectRole.INPUT_ITEM);
    }; }
}
