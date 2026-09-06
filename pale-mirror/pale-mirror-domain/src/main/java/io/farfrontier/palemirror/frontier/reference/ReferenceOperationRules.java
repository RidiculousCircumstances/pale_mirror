package io.farfrontier.palemirror.frontier.reference;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Pure balance and doctrine functions extracted from Python {@code operations.py}. */
final class ReferenceOperationRules {
    static final int MAX_ACTIVE_PER_SETTLEMENT = 2;
    static final int MAX_ACTIVE_FIELD_PER_SETTLEMENT = 1;
    static final double COALITION_PERSONNEL_MULTIPLIER = 0.55d;
    static final double HUMAN_SPEED_ROAD = 2.8d;
    static final double HUMAN_SPEED_OFFROAD = 1.55d;
    static final double ARRIVAL_DISTANCE = 0.75d;
    static final double FOOD_PER_PERSON_DAY = 0.075d;
    static final double MEDICINE_PER_PERSON_DAY = 0.0008d;
    static final int MINIMUM_SUPPLY_DAYS = 6;
    static final double EXHAUSTION_POWER_LOSS = 0.10d;
    static final double ROLE_LOGISTICS_SUPPLY_MULTIPLIER = 0.65d;

    private ReferenceOperationRules() { }

    static LinkedHashMap<Integer, Double> personnelByContributor(
            ReferenceWorld world,
            ReferenceOperationKind kind,
            int leaderId,
            Map<Integer, Map<ReferenceResource, Double>> contributors
    ) {
        LinkedHashMap<Integer, Double> result = new LinkedHashMap<>();
        for (int settlementId : contributors.keySet()) {
            ReferenceSettlement settlement = world.settlements().get(settlementId);
            if (settlement == null || !settlement.alive()) return null;
            double committed = personnelFor(settlement, kind);
            if (settlementId != leaderId) committed *= COALITION_PERSONNEL_MULTIPLIER;
            if (committed <= 0.0d) return null;
            result.put(settlementId, committed);
        }
        return result;
    }

    static LinkedHashMap<Integer, EnumMap<ReferenceHumanUnitKind, Double>> compositions(
            ReferenceWorld world,
            ReferenceOperationKind kind,
            Map<Integer, Double> personnel
    ) {
        LinkedHashMap<Integer, EnumMap<ReferenceHumanUnitKind, Double>> result = new LinkedHashMap<>();
        for (Map.Entry<Integer, Double> entry : personnel.entrySet()) {
            EnumMap<ReferenceHumanUnitKind, Double> composition = compositionFor(
                    world.settlements().get(entry.getKey()), kind, entry.getValue());
            if (composition == null) return null;
            result.put(entry.getKey(), composition);
        }
        return result;
    }

    static double personnelFor(ReferenceSettlement settlement, ReferenceOperationKind kind) {
        PersonnelRule rule = personnelRule(kind);
        if (rule == null) return 0.0d;
        double available = Math.max(0.0d, settlement.population() - settlement.mobilizedPersonnel());
        if (settlement.discretePeople()) {
            int intended = Math.max(grayboxPersonnelMinimum(kind),
                    (int) (settlement.population() * rule.populationRatio() + 0.5d));
            return Math.min((int) available, intended);
        }
        return Math.min(available, Math.max(rule.minimum(), settlement.population() * rule.populationRatio()));
    }

    static int grayboxPersonnelMinimum(ReferenceOperationKind kind) {
        return switch (kind) {
            case RAID_NEST -> 4;
            case DEFEND, CLEANSE, RECLAIM, BUILD_POST -> 3;
            case RECON, PATROL, ESCORT, EVACUATE -> 2;
            default -> 2;
        };
    }

    static EnumMap<ReferenceHumanUnitKind, Double> compositionFor(
            ReferenceSettlement settlement,
            ReferenceOperationKind kind,
            double personnel
    ) {
        EnumMap<ReferenceHumanUnitKind, Double> roles = new EnumMap<>(ReferenceHumanUnitKind.class);
        double displaced = 0.0d;
        for (Map.Entry<ReferenceHumanUnitKind, Double> entry : profileFor(kind).entrySet()) {
            double amount = personnel * entry.getValue();
            if (roleAvailable(settlement, entry.getKey())) roles.merge(entry.getKey(), amount, Double::sum);
            else displaced += amount;
        }
        if (kind == ReferenceOperationKind.RAID_NEST
                && roles.getOrDefault(ReferenceHumanUnitKind.ASSAULT, 0.0d) <= 0.01d) return null;
        roles.merge(ReferenceHumanUnitKind.LINE, displaced, Double::sum);
        if (settlement.discretePeople()) {
            Map<String, Integer> integral = ReferenceFormations.integerComposition((int) personnel, stringRoles(roles));
            roles.clear();
            integral.forEach((role, amount) -> roles.put(roleFromId(role), amount.doubleValue()));
        }
        roles.entrySet().removeIf(entry -> entry.getValue() <= 0.01d);
        return roles;
    }

    static EnumMap<ReferenceResource, Double> baseRequirements(ReferenceOperationKind kind, double humanScale) {
        EnumMap<ReferenceResource, Double> result = enumResources(requirementsForKind(kind));
        result.replaceAll((resource, amount) -> amount / humanScale);
        return result;
    }

    static PersonnelRule personnelRule(ReferenceOperationKind kind) {
        return switch (kind) {
            case RAID_NEST -> new PersonnelRule(18, .028); case RECON -> new PersonnelRule(6, .008);
            case PATROL -> new PersonnelRule(10, .014); case ESCORT -> new PersonnelRule(8, .010);
            case DEFEND -> new PersonnelRule(12, .018); case CLEANSE -> new PersonnelRule(8, .010);
            case RECLAIM -> new PersonnelRule(10, .014); case EVACUATE -> new PersonnelRule(5, .006);
            case BUILD_POST -> new PersonnelRule(6, .012); case BUILD_MODULE -> new PersonnelRule(4, .008);
            case BUILD_LINE -> new PersonnelRule(5, .010); case RESUPPLY -> new PersonnelRule(4, .007);
            case REINFORCE -> new PersonnelRule(6, .012); case EVACUATE_WOUNDED -> new PersonnelRule(4, .007);
            case WITHDRAW -> new PersonnelRule(4, .007); case CLEANSE_PERIMETER -> new PersonnelRule(7, .010);
            default -> null;
        };
    }

    static Map<ReferenceHumanUnitKind, Double> combinedRoles(
            Map<Integer, ? extends Map<ReferenceHumanUnitKind, Double>> compositions
    ) {
        EnumMap<ReferenceHumanUnitKind, Double> result = new EnumMap<>(ReferenceHumanUnitKind.class);
        compositions.values().forEach(values -> values.forEach((role, amount) -> result.merge(role, amount, Double::sum)));
        return result;
    }

    static Map<ReferenceHumanUnitKind, Double> rolesFor(ReferenceOperation operation) {
        return combinedRoles(operation.unitCompositionBySettlement());
    }

    static double powerFor(Map<ReferenceHumanUnitKind, Double> composition, Map<ReferenceResource, Double> supplies) {
        double roles = composition.entrySet().stream()
                .mapToDouble(entry -> entry.getValue() * rolePower(entry.getKey())).sum();
        return roles + supplies.getOrDefault(ReferenceResource.WEAPONS, 0.0d) * 3.0d
                + supplies.getOrDefault(ReferenceResource.AMMO, 0.0d) * .28d
                + supplies.getOrDefault(ReferenceResource.MEDICINE, 0.0d) * .35d;
    }

    static boolean stationKind(ReferenceOperationKind kind) {
        return kind == ReferenceOperationKind.DEFEND || kind == ReferenceOperationKind.PATROL
                || kind == ReferenceOperationKind.ESCORT;
    }

    static Map<String, Integer> stringComposition(Map<ReferenceHumanUnitKind, Double> roles) {
        LinkedHashMap<String, Integer> result = new LinkedHashMap<>();
        roles.forEach((role, amount) -> result.put(role.id(), (int) Math.round(amount)));
        return result;
    }

    static List<String> flatten(Map<String, List<String>> deployed) {
        ArrayList<String> result = new ArrayList<>();
        deployed.keySet().stream().sorted().forEach(role -> result.addAll(deployed.get(role)));
        return List.copyOf(result);
    }

    static double sum(Map<Integer, Double> values) {
        return values.values().stream().mapToDouble(Double::doubleValue).sum();
    }

    static EnumMap<ReferenceResource, Double> enumResources(Map<ReferenceResource, Double> values) {
        EnumMap<ReferenceResource, Double> result = new EnumMap<>(ReferenceResource.class);
        result.putAll(values);
        return result;
    }

    static Map<ReferenceResource, Double> immutableResources(Map<ReferenceResource, Double> values) {
        return Collections.unmodifiableMap(new EnumMap<>(values));
    }

    static Map<Integer, Map<ReferenceResource, Double>> copyResourceMatrix(
            Map<Integer, ? extends Map<ReferenceResource, Double>> values
    ) {
        LinkedHashMap<Integer, Map<ReferenceResource, Double>> result = new LinkedHashMap<>();
        values.forEach((id, value) -> result.put(id, enumResources(value)));
        return result;
    }

    private static boolean roleAvailable(ReferenceSettlement settlement, ReferenceHumanUnitKind role) {
        double scale = settlement.profile().humanAmountFromSource(1.0d);
        return switch (role) {
            case LINE, SCOUT -> true;
            case ASSAULT -> settlement.facilities().armory() >= 0.20d * scale;
            case ENGINEER, LOGISTICS -> settlement.facilities().workshop() >= 0.50d * scale;
            case MEDIC -> settlement.facilities().clinic() >= 0.35d * scale;
        };
    }

    private static Map<ReferenceResource, Double> requirementsForKind(ReferenceOperationKind kind) {
        return switch (kind) {
            case RAID_NEST -> resources(42, 1.2, 14, 54); case RECON -> resources(12, .2, 2, 8);
            case PATROL -> resources(20, .4, 5, 20); case ESCORT -> resources(18, .4, 4, 16);
            case DEFEND -> resources(24, .6, 7, 32); case CLEANSE -> resources(16, 1, 2, 14);
            case RECLAIM -> resources(22, .5, 3, 12); case EVACUATE -> resources(10, .4, 1, 4);
            case BUILD_POST -> resources(12, .3, 2, 8); case BUILD_MODULE -> resources(8, .2, 1, 4);
            case BUILD_LINE -> resources(10, .2, 2, 6); case RESUPPLY -> resources(8, .2, 2, 8);
            case REINFORCE -> resources(12, .3, 4, 14); case EVACUATE_WOUNDED -> resources(8, .6, 2, 6);
            case WITHDRAW -> resources(8, .4, 2, 6); case CLEANSE_PERIMETER -> resources(12, 1, 2, 14);
            default -> Map.of();
        };
    }

    private static Map<ReferenceHumanUnitKind, Double> profileFor(ReferenceOperationKind kind) {
        return switch (kind) {
            case RECON -> roles(.30, .55, 0, 0, 0, .15); case PATROL -> roles(.55, .25, 0, 0, .10, .10);
            case ESCORT -> roles(.60, .15, 0, .10, .05, .10); case DEFEND -> roles(.55, .15, 0, .15, .10, .05);
            case RAID_NEST -> roles(.25, .15, .35, .15, .05, .05);
            case CLEANSE, CLEANSE_PERIMETER -> roles(.35, .15, .15, .20, .10, .05);
            case BUILD_POST, BUILD_MODULE, BUILD_LINE -> roles(.20, .15, 0, .45, .05, .15);
            case RESUPPLY -> roles(.20, .10, 0, .20, .05, .45);
            case REINFORCE -> roles(.50, .20, 0, .15, .05, .10); case EVACUATE -> roles(.35, .25, 0, 0, .20, .20);
            default -> roles(.70, .30, 0, 0, 0, 0);
        };
    }

    private static double rolePower(ReferenceHumanUnitKind role) {
        return switch (role) {
            case LINE -> .75; case SCOUT -> .50; case ASSAULT -> 1.25;
            case ENGINEER -> .65; case MEDIC -> .30; case LOGISTICS -> .35;
        };
    }

    private static Map<ReferenceResource, Double> resources(double food, double medicine, double weapons, double ammo) {
        return Map.of(ReferenceResource.FOOD, food, ReferenceResource.MEDICINE, medicine,
                ReferenceResource.WEAPONS, weapons, ReferenceResource.AMMO, ammo);
    }

    private static Map<ReferenceHumanUnitKind, Double> roles(
            double line, double scout, double assault, double engineer, double medic, double logistics
    ) {
        EnumMap<ReferenceHumanUnitKind, Double> result = new EnumMap<>(ReferenceHumanUnitKind.class);
        result.put(ReferenceHumanUnitKind.LINE, line); result.put(ReferenceHumanUnitKind.SCOUT, scout);
        result.put(ReferenceHumanUnitKind.ASSAULT, assault); result.put(ReferenceHumanUnitKind.ENGINEER, engineer);
        result.put(ReferenceHumanUnitKind.MEDIC, medic); result.put(ReferenceHumanUnitKind.LOGISTICS, logistics);
        return result;
    }

    private static Map<String, Double> stringRoles(Map<ReferenceHumanUnitKind, Double> roles) {
        LinkedHashMap<String, Double> result = new LinkedHashMap<>();
        roles.forEach((role, amount) -> result.put(role.id(), amount));
        return result;
    }

    private static ReferenceHumanUnitKind roleFromId(String id) {
        for (ReferenceHumanUnitKind role : ReferenceHumanUnitKind.values()) if (role.id().equals(id)) return role;
        throw new IllegalArgumentException("unknown human role " + id);
    }

    record PersonnelRule(double minimum, double populationRatio) { }
}
