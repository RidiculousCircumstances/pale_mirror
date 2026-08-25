package io.farfrontier.palemirror.frontier.reference;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Source-shaped owner mapper for Python's {@code OperationManager}. */
final class ReferenceCanonicalStateOperations {
    private ReferenceCanonicalStateOperations() { }

    static Map<String, Object> capture(ReferenceWorld world) {
        ReferenceWorld required = Objects.requireNonNull(world, "world");
        if (!required.v2Enabled()) throw new IllegalStateException("canonical state requires V2-enabled reference world");
        ReferenceOperationManager operations = required.operations();
        return typed("simulation.operations.OperationManager", "fields", object(
                "active", sequence("list", operations.active().stream().map(ReferenceCanonicalStateOperations::operation).toList()),
                "completed", sequence("list", operations.completed().stream().map(ReferenceCanonicalStateOperations::operation).toList()),
                "next_id", operations.nextId()));
    }

    private static Map<String, Object> operation(ReferenceOperation value) {
        return typed("simulation.operations.Operation", "fields", object(
                "id", value.id(), "side", enumValue("simulation.operations.AgentKind", value.side().id()),
                "kind", enumValue("simulation.operations.OperationKind", value.kind().id()), "owner", agent(value.owner()),
                "target", target(value.target()), "started_day", value.startedDay(), "x", value.x(), "y", value.y(),
                "origin_x", value.originX(), "origin_y", value.originY(), "personnel", value.personnel(),
                "committed_personnel", value.committedPersonnel(), "power", value.power(), "supplies", resourceMap(value.supplies()),
                "contributors", resourceMatrix(value.contributors()), "personnel_by_settlement", mapping(value.personnelBySettlement()),
                "unit_composition_by_settlement", roleMatrix(value.unitCompositionBySettlement()), "unit_losses", roleMap(value.unitLosses()),
                "phase", enumValue("simulation.formations.FormationPhase", value.phase().id()), "objective_id", value.objectiveId(),
                "status", enumValue("simulation.operations.OperationStatus", value.status().id()), "station_days", value.stationDays(),
                "unsupplied_days", value.unsuppliedDays(), "detected_by", intSet(value.detectedBy()), "linked_swarm_id", value.linkedSwarmId(),
                "outcome", value.outcome(), "resolved", value.resolved(), "finished_day", value.finishedDay(), "details", detailMap(value.details()),
                "waypoints", points(value.waypoints()), "return_waypoints", points(value.returnWaypoints()), "waypoint_index", value.waypointIndex(),
                "cargo", resourceMap(value.cargo()), "evacuated_wounded_by_settlement", mapping(value.evacuatedWoundedBySettlement()),
                "resident_ids_by_settlement", residentMap(value.residentIdsBySettlement()),
                "wounded_resident_ids_by_settlement", residentMap(value.woundedResidentIdsBySettlement()),
                "origin", value.origin() == null ? null : target(value.origin()), "engagement_id", value.engagementId()));
    }

    private static Map<String, Object> agent(ReferenceAgentRef value) {
        return typed("simulation.operations.AgentRef", "fields", object(
                "kind", enumValue("simulation.operations.AgentKind", value.kind().id()), "id", value.id()));
    }

    private static Map<String, Object> target(ReferenceTargetRef value) {
        return typed("simulation.operations.TargetRef", "fields", object(
                "kind", enumValue("simulation.operations.TargetKind", value.kind().id()), "id", value.id(), "x", value.x(), "y", value.y()));
    }

    private static Map<String, Object> resourceMap(Map<ReferenceResource, Double> values) {
        List<List<Object>> pairs = new ArrayList<>();
        values.forEach((resource, amount) -> { if (amount != 0.0d) pairs.add(pair(resource(resource), amount)); });
        return map(pairs);
    }

    private static Map<String, Object> roleMap(Map<ReferenceHumanUnitKind, Double> values) {
        List<List<Object>> pairs = new ArrayList<>();
        values.forEach((role, amount) -> { if (amount != 0.0d) pairs.add(pair(role(role), amount)); });
        return map(pairs);
    }

    private static Map<String, Object> resourceMatrix(Map<Integer, Map<ReferenceResource, Double>> values) {
        List<List<Object>> pairs = new ArrayList<>();
        values.forEach((settlementId, resources) -> pairs.add(pair(settlementId, resourceMap(resources))));
        return map(pairs);
    }

    private static Map<String, Object> roleMatrix(Map<Integer, Map<ReferenceHumanUnitKind, Double>> values) {
        List<List<Object>> pairs = new ArrayList<>();
        values.forEach((settlementId, roles) -> pairs.add(pair(settlementId, roleMap(roles))));
        return map(pairs);
    }

    private static Map<String, Object> mapping(Map<Integer, Double> values) {
        List<List<Object>> pairs = new ArrayList<>();
        values.forEach((key, value) -> pairs.add(pair(key, value)));
        return map(pairs);
    }

    private static Map<String, Object> residentMap(Map<Integer, List<String>> values) {
        List<List<Object>> pairs = new ArrayList<>();
        values.forEach((settlementId, residentIds) -> pairs.add(pair(settlementId, sequence("tuple", residentIds))));
        return map(pairs);
    }

    private static Map<String, Object> detailMap(Map<String, Object> values) {
        List<List<Object>> pairs = new ArrayList<>();
        values.forEach((key, value) -> pairs.add(pair(key, detail(value))));
        return map(pairs);
    }

    private static Object detail(Object value) {
        if (value == null || value instanceof String || value instanceof Boolean || value instanceof Integer || value instanceof Double) return value;
        throw new IllegalStateException("operation detail has unsupported source value: " + value.getClass().getName());
    }

    private static Map<String, Object> points(List<ReferencePoint> values) {
        return sequence("tuple", values.stream().map(point -> sequence("tuple", List.of(point.x(), point.y()))).toList());
    }

    private static Map<String, Object> intSet(Set<Integer> values) {
        return sequence("set", values.stream().sorted().toList());
    }

    private static Map<String, Object> resource(ReferenceResource value) {
        return enumValue("simulation.economy.Resource", value.name().toLowerCase(Locale.ROOT));
    }

    private static Map<String, Object> role(ReferenceHumanUnitKind value) {
        return enumValue("simulation.formations.HumanUnitKind", value.id());
    }

    private static Map<String, Object> enumValue(String type, String value) { return object("$enum", type, "value", value); }
    private static Map<String, Object> typed(String type, String fieldName, Map<String, Object> values) { return object("$type", type, fieldName, values); }
    private static Map<String, Object> sequence(String kind, List<?> items) { return object("$sequence", kind, "items", List.copyOf(items)); }

    private static Map<String, Object> map(List<List<Object>> pairs) {
        pairs.sort(Comparator.comparing(pair -> ReferenceV2PublicSnapshot.canonicalJson(pair.getFirst())));
        return object("$map", List.copyOf(pairs));
    }

    private static List<Object> pair(Object key, Object value) {
        ArrayList<Object> result = new ArrayList<>(2);
        result.add(key);
        result.add(value);
        return Collections.unmodifiableList(result);
    }

    private static Map<String, Object> object(Object... entries) {
        if (entries.length % 2 != 0) throw new IllegalArgumentException("object entries must be pairs");
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < entries.length; index += 2) result.put((String) entries[index], entries[index + 1]);
        return Collections.unmodifiableMap(result);
    }
}
