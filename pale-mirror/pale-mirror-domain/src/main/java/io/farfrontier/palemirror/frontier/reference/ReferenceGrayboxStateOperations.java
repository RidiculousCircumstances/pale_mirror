package io.farfrontier.palemirror.frontier.reference;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Strict all-or-nothing hydration of the source OperationManager owner. */
final class ReferenceGrayboxStateOperations {
    private ReferenceGrayboxStateOperations() { }

    static State read(Object encoded) {
        Map<String, Object> fields = ReferenceGrayboxStateReader.typed(encoded, "simulation.operations.OperationManager", "fields");
        ReferenceGrayboxStateReader.exactKeys(fields, "operation manager", "active", "completed", "next_id");
        List<ReferenceOperation> active = operations(fields.get("active"), "active operations");
        List<ReferenceOperation> completed = operations(fields.get("completed"), "completed operations");
        int nextId = ReferenceGrayboxStateReader.integer(fields.get("next_id"), "operation next id");
        LinkedHashSet<Integer> ids = new LinkedHashSet<>();
        active.forEach(operation -> ids.add(operation.id())); completed.forEach(operation -> ids.add(operation.id()));
        if (nextId < 1 || ids.size() != active.size() + completed.size() || ids.stream().anyMatch(id -> id >= nextId)) {
            throw new IllegalArgumentException("operation identity sequence is invalid");
        }
        return new State(active, completed, nextId);
    }

    private static List<ReferenceOperation> operations(Object encoded, String label) {
        List<ReferenceOperation> result = new ArrayList<>();
        for (Object item : ReferenceGrayboxStateReader.sequence(encoded, "list", label)) result.add(operation(item));
        return List.copyOf(result);
    }

    private static ReferenceOperation operation(Object encoded) {
        Map<String, Object> fields = ReferenceGrayboxStateReader.typed(encoded, "simulation.operations.Operation", "fields");
        ReferenceGrayboxStateReader.exactKeys(fields, "operation", "id", "side", "kind", "owner", "target", "started_day", "x", "y", "origin_x",
                "origin_y", "personnel", "committed_personnel", "power", "supplies", "contributors", "personnel_by_settlement",
                "unit_composition_by_settlement", "unit_losses", "phase", "objective_id", "status", "station_days", "unsupplied_days", "detected_by",
                "linked_swarm_id", "outcome", "resolved", "finished_day", "details", "waypoints", "return_waypoints", "waypoint_index", "cargo",
                "evacuated_wounded_by_settlement", "resident_ids_by_settlement", "wounded_resident_ids_by_settlement", "origin", "engagement_id");
        ReferenceAgentKind side = agentKind(fields.get("side"), "operation side");
        ReferenceAgentRef owner = agent(fields.get("owner"));
        if (side != owner.kind()) throw new IllegalArgumentException("operation side differs from owner");
        ReferenceOperation result = new ReferenceOperation(ReferenceGrayboxStateReader.integer(fields.get("id"), "operation id"), side,
                operationKind(fields.get("kind"), "operation kind"), owner, target(fields.get("target"), "operation target"),
                ReferenceGrayboxStateReader.integer(fields.get("started_day"), "operation started day"), number(fields.get("x"), "operation x"),
                number(fields.get("y"), "operation y"), number(fields.get("origin_x"), "operation origin x"), number(fields.get("origin_y"), "operation origin y"));
        if (result.id() < 1) throw new IllegalArgumentException("operation id is invalid");
        result.personnel(number(fields.get("personnel"), "operation personnel")); result.committedPersonnel(number(fields.get("committed_personnel"), "operation committed personnel"));
        result.power(number(fields.get("power"), "operation power")); result.supplies(resources(fields.get("supplies"), "operation supplies"));
        result.contributors(resourceMatrix(fields.get("contributors"), "operation contributors"));
        result.personnelBySettlement(doubleMap(fields.get("personnel_by_settlement"), "operation personnel by settlement"));
        result.unitCompositionBySettlement(roleMatrix(fields.get("unit_composition_by_settlement"), "operation roles by settlement"));
        EnumMap<ReferenceHumanUnitKind, Double> losses = roles(fields.get("unit_losses"), "operation losses");
        result.mutableUnitLosses().clear(); result.mutableUnitLosses().putAll(losses);
        result.phase(phase(fields.get("phase"), "operation phase")); result.objectiveId(ReferenceGrayboxStateReader.nullableInteger(fields.get("objective_id"), "operation objective"));
        result.status(status(fields.get("status"), "operation status")); result.stationDays(ReferenceGrayboxStateReader.integer(fields.get("station_days"), "operation station days"));
        result.unsuppliedDays(ReferenceGrayboxStateReader.integer(fields.get("unsupplied_days"), "operation unsupplied days"));
        result.mutableDetectedBy().addAll(integerSet(fields.get("detected_by"), "operation detections"));
        result.linkedSwarmId(ReferenceGrayboxStateReader.nullableInteger(fields.get("linked_swarm_id"), "operation swarm"));
        result.outcome(nullableString(fields.get("outcome"), "operation outcome")); result.resolved(ReferenceGrayboxStateReader.bool(fields.get("resolved"), "operation resolved"));
        result.finishedDay(ReferenceGrayboxStateReader.nullableInteger(fields.get("finished_day"), "operation finished day"));
        result.details(details(fields.get("details"))); result.waypoints(points(fields.get("waypoints"), "operation waypoints"));
        result.returnWaypoints(points(fields.get("return_waypoints"), "operation return waypoints"));
        result.waypointIndex(ReferenceGrayboxStateReader.integer(fields.get("waypoint_index"), "operation waypoint index"));
        result.cargo(resources(fields.get("cargo"), "operation cargo"));
        result.mutableEvacuatedWoundedBySettlement().putAll(doubleMap(fields.get("evacuated_wounded_by_settlement"), "operation wounded evacuation"));
        result.residentIdsBySettlement(residents(fields.get("resident_ids_by_settlement"), "operation residents"));
        result.woundedResidentIdsBySettlement(residents(fields.get("wounded_resident_ids_by_settlement"), "operation wounded residents"));
        Object encodedOrigin = fields.get("origin"); result.origin(encodedOrigin == null ? null : target(encodedOrigin, "operation origin"));
        result.engagementId(ReferenceGrayboxStateReader.nullableInteger(fields.get("engagement_id"), "operation engagement"));
        return result;
    }

    private static ReferenceAgentRef agent(Object encoded) {
        Map<String, Object> fields = ReferenceGrayboxStateReader.typed(encoded, "simulation.operations.AgentRef", "fields");
        ReferenceGrayboxStateReader.exactKeys(fields, "operation owner", "kind", "id");
        return new ReferenceAgentRef(agentKind(fields.get("kind"), "operation owner kind"), ReferenceGrayboxStateReader.integer(fields.get("id"), "operation owner id"));
    }

    private static ReferenceTargetRef target(Object encoded, String label) {
        Map<String, Object> fields = ReferenceGrayboxStateReader.typed(encoded, "simulation.operations.TargetRef", "fields");
        ReferenceGrayboxStateReader.exactKeys(fields, label, "kind", "id", "x", "y");
        Integer id = ReferenceGrayboxStateReader.nullableInteger(fields.get("id"), label + " id");
        Integer x = ReferenceGrayboxStateReader.nullableInteger(fields.get("x"), label + " x");
        Integer y = ReferenceGrayboxStateReader.nullableInteger(fields.get("y"), label + " y");
        if (id == null && (x == null || y == null)) throw new IllegalArgumentException(label + " lacks an identity and coordinate");
        return new ReferenceTargetRef(targetKind(fields.get("kind"), label + " kind"), id, x, y);
    }

    private static EnumMap<ReferenceResource, Double> resources(Object encoded, String label) {
        EnumMap<ReferenceResource, Double> result = new EnumMap<>(ReferenceResource.class);
        for (ReferenceGrayboxStateReader.Entry entry : ReferenceGrayboxStateReader.mapEntries(encoded, label)) {
            ReferenceResource resource = resource(entry.key(), label + " resource");
            double amount = number(entry.value(), label + " amount");
            if (result.putIfAbsent(resource, amount) != null) throw new IllegalArgumentException(label + " has duplicate resource");
        }
        return result;
    }

    private static LinkedHashMap<Integer, Map<ReferenceResource, Double>> resourceMatrix(Object encoded, String label) {
        LinkedHashMap<Integer, Map<ReferenceResource, Double>> result = new LinkedHashMap<>();
        for (ReferenceGrayboxStateReader.Entry entry : ReferenceGrayboxStateReader.mapEntries(encoded, label)) {
            int id = ReferenceGrayboxStateReader.integer(entry.key(), label + " settlement");
            if (result.putIfAbsent(id, resources(entry.value(), label + " resources")) != null) throw new IllegalArgumentException(label + " has duplicate settlement");
        }
        return result;
    }

    private static LinkedHashMap<Integer, Double> doubleMap(Object encoded, String label) {
        LinkedHashMap<Integer, Double> result = new LinkedHashMap<>();
        for (ReferenceGrayboxStateReader.Entry entry : ReferenceGrayboxStateReader.mapEntries(encoded, label)) {
            int id = ReferenceGrayboxStateReader.integer(entry.key(), label + " settlement");
            if (result.putIfAbsent(id, number(entry.value(), label + " value")) != null) throw new IllegalArgumentException(label + " has duplicate settlement");
        }
        return result;
    }

    private static LinkedHashMap<Integer, Map<ReferenceHumanUnitKind, Double>> roleMatrix(Object encoded, String label) {
        LinkedHashMap<Integer, Map<ReferenceHumanUnitKind, Double>> result = new LinkedHashMap<>();
        for (ReferenceGrayboxStateReader.Entry entry : ReferenceGrayboxStateReader.mapEntries(encoded, label)) {
            int id = ReferenceGrayboxStateReader.integer(entry.key(), label + " settlement");
            if (result.putIfAbsent(id, roles(entry.value(), label + " roles")) != null) throw new IllegalArgumentException(label + " has duplicate settlement");
        }
        return result;
    }

    private static EnumMap<ReferenceHumanUnitKind, Double> roles(Object encoded, String label) {
        EnumMap<ReferenceHumanUnitKind, Double> result = new EnumMap<>(ReferenceHumanUnitKind.class);
        for (ReferenceGrayboxStateReader.Entry entry : ReferenceGrayboxStateReader.mapEntries(encoded, label)) {
            ReferenceHumanUnitKind role = role(entry.key(), label + " role");
            if (result.putIfAbsent(role, number(entry.value(), label + " value")) != null) throw new IllegalArgumentException(label + " has duplicate role");
        }
        return result;
    }

    private static LinkedHashMap<Integer, List<String>> residents(Object encoded, String label) {
        LinkedHashMap<Integer, List<String>> result = new LinkedHashMap<>();
        for (ReferenceGrayboxStateReader.Entry entry : ReferenceGrayboxStateReader.mapEntries(encoded, label)) {
            int id = ReferenceGrayboxStateReader.integer(entry.key(), label + " settlement");
            List<String> residents = new ArrayList<>();
            for (Object value : ReferenceGrayboxStateReader.sequence(entry.value(), "tuple", label + " ids")) residents.add(ReferenceGrayboxStateReader.string(value, label + " resident"));
            if (residents.stream().distinct().count() != residents.size() || result.putIfAbsent(id, List.copyOf(residents)) != null) {
                throw new IllegalArgumentException(label + " has duplicate identity");
            }
        }
        return result;
    }

    private static Set<Integer> integerSet(Object encoded, String label) {
        LinkedHashSet<Integer> result = new LinkedHashSet<>();
        for (Object value : ReferenceGrayboxStateReader.sequence(encoded, "set", label)) {
            if (!result.add(ReferenceGrayboxStateReader.integer(value, label + " value"))) throw new IllegalArgumentException(label + " has duplicate value");
        }
        return result;
    }

    private static Map<String, Object> details(Object encoded) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        for (ReferenceGrayboxStateReader.Entry entry : ReferenceGrayboxStateReader.mapEntries(encoded, "operation details")) {
            String key = ReferenceGrayboxStateReader.string(entry.key(), "operation detail key");
            Object value = entry.value();
            if (!(value == null || value instanceof String || value instanceof Boolean || value instanceof Integer || value instanceof Double)
                    || result.putIfAbsent(key, value) != null) throw new IllegalArgumentException("operation detail is invalid");
        }
        return result;
    }

    private static List<ReferencePoint> points(Object encoded, String label) {
        List<ReferencePoint> result = new ArrayList<>();
        for (Object item : ReferenceGrayboxStateReader.sequence(encoded, "tuple", label)) {
            List<Object> pair = ReferenceGrayboxStateReader.sequence(item, "tuple", label + " point");
            if (pair.size() != 2) throw new IllegalArgumentException(label + " point is malformed");
            result.add(new ReferencePoint(number(pair.get(0), label + " x"), number(pair.get(1), label + " y")));
        }
        return List.copyOf(result);
    }

    private static double number(Object value, String label) { return ReferenceGrayboxStateReader.number(value, label); }
    private static String nullableString(Object value, String label) { return value == null ? null : ReferenceGrayboxStateReader.string(value, label); }
    private static ReferenceResource resource(Object value, String label) { return byName(ReferenceGrayboxStateReader.enumValue(value, "simulation.economy.Resource", label), ReferenceResource.class, label); }
    private static ReferenceHumanUnitKind role(Object value, String label) { return byId(ReferenceGrayboxStateReader.enumValue(value, "simulation.formations.HumanUnitKind", label), ReferenceHumanUnitKind.values(), label); }
    private static ReferenceAgentKind agentKind(Object value, String label) { return byId(ReferenceGrayboxStateReader.enumValue(value, "simulation.operations.AgentKind", label), ReferenceAgentKind.values(), label); }
    private static ReferenceOperationKind operationKind(Object value, String label) { return byId(ReferenceGrayboxStateReader.enumValue(value, "simulation.operations.OperationKind", label), ReferenceOperationKind.values(), label); }
    private static ReferenceTargetKind targetKind(Object value, String label) { return byId(ReferenceGrayboxStateReader.enumValue(value, "simulation.operations.TargetKind", label), ReferenceTargetKind.values(), label); }
    private static ReferenceOperationStatus status(Object value, String label) { return byId(ReferenceGrayboxStateReader.enumValue(value, "simulation.operations.OperationStatus", label), ReferenceOperationStatus.values(), label); }
    private static ReferenceFormationPhase phase(Object value, String label) { return byId(ReferenceGrayboxStateReader.enumValue(value, "simulation.formations.FormationPhase", label), ReferenceFormationPhase.values(), label); }

    private static ReferenceResource byName(String value, Class<ReferenceResource> type, String label) {
        try { return Enum.valueOf(type, value.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException error) { throw new IllegalArgumentException(label + " is unsupported", error); }
    }

    private static <T extends Enum<T>> T byId(String value, T[] values, String label) {
        for (T candidate : values) if (id(candidate).equals(value)) return candidate;
        throw new IllegalArgumentException(label + " is unsupported");
    }

    private static String id(Enum<?> value) {
        return switch (value) {
            case ReferenceAgentKind typed -> typed.id(); case ReferenceOperationKind typed -> typed.id(); case ReferenceTargetKind typed -> typed.id();
            case ReferenceOperationStatus typed -> typed.id(); case ReferenceFormationPhase typed -> typed.id(); case ReferenceHumanUnitKind typed -> typed.id();
            default -> throw new IllegalArgumentException("unsupported operation enum");
        };
    }

    record State(List<ReferenceOperation> active, List<ReferenceOperation> completed, int nextId) {
        State { active = List.copyOf(active); completed = List.copyOf(completed); }

        void applyTo(ReferenceOperationManager manager) {
            manager.mutableActive().clear(); manager.mutableActive().addAll(active);
            manager.mutableCompleted().clear(); manager.mutableCompleted().addAll(completed); manager.nextId = nextId;
        }
    }
}
