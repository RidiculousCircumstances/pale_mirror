package io.farfrontier.palemirror.frontier;

import java.util.LinkedHashSet;
import java.util.Set;

/** Deterministic initial world generator. Layout is fixed by profile and seed, not worker order. */
public final class FrontierWorldFactory {
    private static final String[] PREFIXES = { "Ash", "Birch", "Cinder", "Dawn", "Ember", "Flint", "Grove", "Hearth", "Iron", "Juniper" };
    private FrontierWorldFactory() { }

    public static FrontierWorldState create(FrontierProfile profile, long seed) {
        FrontierWorldState state = new FrontierWorldState(profile, seed);
        for (int index = 0; index < profile.settlementCount(); index++) createSettlement(state, index);
        for (int index = 0; index < profile.settlementCount(); index++) {
            String left = settlementId(index);
            String right = settlementId((index + 1) % profile.settlementCount());
            state.putRoute(new FrontierRoute("frontier:route:" + String.format("%02d", index + 1), left, right, 8));
        }
        for (int index = 0; index < profile.infectionSeeds(); index++) createHive(state, index);
        state.event(FrontierEvent.Type.WORLD_CREATED, profile.id(), "frontier:genesis:" + seed);
        return state;
    }

    private static void createSettlement(FrontierWorldState state, int index) {
        FrontierProfile profile = state.profile();
        int columns = profile.equals(FrontierProfile.GRAYBOX_10) ? 5 : (int) Math.ceil(Math.sqrt(profile.settlementCount()));
        int rows = (int) Math.ceil((double) profile.settlementCount() / columns);
        int row = index / columns;
        int column = index % columns;
        int x = profile.equals(FrontierProfile.GRAYBOX_10) ? 6 + column * 13
                : (column + 1) * profile.widthCells() / (columns + 1);
        int z = profile.equals(FrontierProfile.GRAYBOX_10) ? (row == 0 ? 16 : 48)
                : (row + 1) * profile.heightCells() / (rows + 1);
        if (x >= profile.widthCells() || z >= profile.heightCells()) throw new IllegalStateException("profile cannot fit settlement layout");
        String id = settlementId(index);
        FrontierSettlementFocus focus = FrontierSettlementFocus.forIndex(index);
        FrontierSettlement settlement = new FrontierSettlement(id, PREFIXES[index % PREFIXES.length] + "stead-" + String.format("%02d", index + 1),
                focus, new FrontierPoint(x, z));
        long entropy = mix(state.seed() + index * 0x9E3779B97F4A7C15L);
        int population = state.profile().minimumPopulation() + (int) Math.floorMod(entropy,
                state.profile().maximumPopulation() - state.profile().minimumPopulation() + 1L);
        state.putSettlement(settlement);
        settlement.addStock(FrontierResource.FOOD, focus == FrontierSettlementFocus.AGRARIAN ? population * 3L : population);
        settlement.addStock(FrontierResource.ORE, 16);
        settlement.addStock(FrontierResource.WOOD, 16);
        settlement.addStock(FrontierResource.MEDICINE, 8);
        settlement.addStock(FrontierResource.WEAPONS, 4);
        for (int resident = 0; resident < population; resident++) {
            FrontierResident value = new FrontierResident(id + ":resident:" + String.format("%02d", resident + 1), id,
                    roleFor(focus, resident));
            state.putResident(value);
            settlement.addResident(value.id());
        }
        int facility = 0;
        for (FrontierFacilityKind kind : FrontierFacilityKind.values()) {
            int dx = (facility % 5) - 2;
            int dz = (facility / 5) * 3 + 3;
            FrontierFacility value = new FrontierFacility(id + ":facility:" + kind.name().toLowerCase(), id, kind,
                    new FrontierPoint(x + dx, z + dz));
            state.putFacility(value);
            settlement.addFacility(value.id());
            state.putOperation(new FrontierOperation(id + ":operation:" + kind.name().toLowerCase(), id, value.id(),
                    FrontierOperationKind.forFacility(kind)));
            facility++;
        }
    }

    private static String settlementId(int index) { return "frontier:settlement:" + String.format("%02d", index + 1); }

    private static void createHive(FrontierWorldState state, int index) {
        FrontierProfile profile = state.profile();
        int x = profile.equals(FrontierProfile.GRAYBOX_10) ? (index == 0 ? 12 : 52)
                : (index + 1) * profile.widthCells() / (profile.infectionSeeds() + 1);
        int z = profile.equals(FrontierProfile.GRAYBOX_10) ? 32 : profile.heightCells() / 2;
        String id = "frontier:hive:" + String.format("%02d", index + 1);
        FrontierHive hive = new FrontierHive(id, new FrontierPoint(x, z), 72);
        state.putHive(hive);
        Set<FrontierPoint> tissue = new LinkedHashSet<>();
        for (FrontierHiveOrganKind kind : FrontierHiveOrganKind.values()) {
            FrontierPoint position = switch (kind) {
                case CORE -> new FrontierPoint(x, z);
                case SYNAPSE -> new FrontierPoint(x - 2, z);
                case DIGESTIVE_POOL -> new FrontierPoint(x + 2, z);
                case BROOD_SAC -> new FrontierPoint(x, z + 2);
                case SPORULATOR -> new FrontierPoint(x, z - 2);
            };
            state.putHiveOrgan(new FrontierHiveOrgan(id + ":organ:" + kind.name().toLowerCase(), id, kind, position));
            seedTissue(tissue, new FrontierPoint(x, z), position);
        }
        tissue.forEach(position -> state.putHiveTissue(new FrontierHiveTissueCell(id, position, FrontierHiveTissueCell.MAX_STRENGTH)));
        FrontierBioformKind[] forms = FrontierBioformKind.values();
        for (int ordinal = 1; ordinal <= 6; ordinal++) {
            FrontierBioformKind kind = forms[(index + ordinal - 1) % forms.length];
            state.putBioform(new FrontierBioform(FrontierBioform.idFor(id, 0, ordinal), id, kind, 0, ordinal));
        }
    }

    private static void seedTissue(Set<FrontierPoint> tissue, FrontierPoint start, FrontierPoint end) {
        int x = start.x();
        int z = start.z();
        tissue.add(start);
        while (x != end.x()) {
            x += Integer.compare(end.x(), x);
            tissue.add(new FrontierPoint(x, z));
        }
        while (z != end.z()) {
            z += Integer.compare(end.z(), z);
            tissue.add(new FrontierPoint(x, z));
        }
    }

    private static FrontierResidentRole roleFor(FrontierSettlementFocus focus, int resident) {
        FrontierResidentRole[] roles = switch (focus) {
            case AGRARIAN -> new FrontierResidentRole[] { FrontierResidentRole.FARMER, FrontierResidentRole.FARMER,
                    FrontierResidentRole.FARMER, FrontierResidentRole.FARMER, FrontierResidentRole.MINER,
                    FrontierResidentRole.FORESTER, FrontierResidentRole.ENGINEER, FrontierResidentRole.MEDIC,
                    FrontierResidentRole.MERCHANT, FrontierResidentRole.GUARD, FrontierResidentRole.CIVILIAN };
            case INDUSTRIAL -> new FrontierResidentRole[] { FrontierResidentRole.MINER, FrontierResidentRole.MINER,
                    FrontierResidentRole.MINER, FrontierResidentRole.MINER, FrontierResidentRole.ENGINEER,
                    FrontierResidentRole.ENGINEER, FrontierResidentRole.FARMER, FrontierResidentRole.MEDIC,
                    FrontierResidentRole.MERCHANT, FrontierResidentRole.GUARD, FrontierResidentRole.CIVILIAN };
            case FORESTRY -> new FrontierResidentRole[] { FrontierResidentRole.FORESTER, FrontierResidentRole.FORESTER,
                    FrontierResidentRole.FORESTER, FrontierResidentRole.FORESTER, FrontierResidentRole.ENGINEER,
                    FrontierResidentRole.MINER, FrontierResidentRole.FARMER, FrontierResidentRole.MEDIC,
                    FrontierResidentRole.MERCHANT, FrontierResidentRole.GUARD, FrontierResidentRole.CIVILIAN };
        };
        return roles[Math.floorMod(resident, roles.length)];
    }

    private static long mix(long value) {
        value ^= value >>> 30;
        value *= 0xbf58476d1ce4e5b9L;
        value ^= value >>> 27;
        value *= 0x94d049bb133111ebL;
        return value ^ value >>> 31;
    }
}
