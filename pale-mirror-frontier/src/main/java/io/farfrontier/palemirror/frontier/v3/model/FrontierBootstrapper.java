package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.DecisionKey;
import io.farfrontier.palemirror.frontier.v3.kernel.KeyedRandom;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Deterministic generator for the single supported fresh-world Frontier v3 profile. */
public final class FrontierBootstrapper {
    private static final WorldBounds BOUNDS = new WorldBounds(-512, -512, 1024, 1024);
    private static final String[] NAMES = {
            "Northwatch", "Stonefield", "Dawnbridge", "Redwillow",
            "Ashcross", "Hearthvale", "Clearwater", "Ironmeadow",
            "Southgate", "Mossbrook", "Westhaven", "Sunreach"
    };
    private static final int[][] ANCHORS = {
            {-360, -340}, {-120, -340}, {120, -340}, {360, -340},
            {-360, 0}, {-120, 0}, {120, 0}, {360, 0},
            {-360, 340}, {-120, 340}, {120, 340}, {360, 340}
    };

    private FrontierBootstrapper() { }

    public static FrontierBootstrap create(WorldId worldId, long seed) {
        return create(worldId, seed, FrontierRulesets.production());
    }

    /** Creates a fresh world from an explicitly selected immutable balance contract. */
    public static FrontierBootstrap create(WorldId worldId, long seed, FrontierRuleset ruleset) {
        return create(worldId, seed, ruleset, TerrainSurfacePlan.uniform(63));
    }

    /** Creates a fresh world from explicitly surveyed immutable support data. */
    public static FrontierBootstrap create(WorldId worldId, long seed, FrontierRuleset ruleset, TerrainSurfacePlan terrain) {
        java.util.Objects.requireNonNull(ruleset, "ruleset");
        java.util.Objects.requireNonNull(terrain, "terrain surface plan");
        List<Settlement> settlements = new ArrayList<>(12);
        for (int index = 0; index < NAMES.length; index++) settlements.add(settlement(seed, index, terrain));
        SubjectId hiveId = new SubjectId("hive:frontier");
        List<HiveNest> nests = List.of(
                new HiveNest(new SubjectId("nest:seed-west"), hiveId, new BlockPosition(-420, 64, 420)),
                new HiveNest(new SubjectId("nest:seed-east"), hiveId, new BlockPosition(420, 64, 420)));
        List<HiveOrgan> organs = new ArrayList<>();
        for (HiveNest nest : nests) {
            String suffix = nest.id().value().substring("nest:seed-".length());
            organs.add(new HiveOrgan(new SubjectId("organ:" + suffix + "-heart"), hiveId, nest.id(), HiveOrganKind.HEART,
                    nest.anchor(), java.util.Optional.empty()));
            organs.add(new HiveOrgan(new SubjectId("organ:" + suffix + "-brood"), hiveId, nest.id(), HiveOrganKind.BROOD,
                    nest.anchor().offset(8, 0, 0), java.util.Optional.empty()));
            organs.add(new HiveOrgan(new SubjectId("organ:" + suffix + "-store"), hiveId, nest.id(), HiveOrganKind.STORE,
                    nest.anchor().offset(-8, 0, 0), java.util.Optional.of(new SubjectId("container:hive-" + suffix + "-store"))));
        }
        List<Bioform> bioforms = new ArrayList<>();
        for (int nestIndex = 0; nestIndex < nests.size(); nestIndex++) {
            HiveNest nest = nests.get(nestIndex);
            List<BlockPosition> placements = FrontierHiveActorSlots.slots(BOUNDS, nest, organs, 24);
            for (int ordinal = 0; ordinal < 24; ordinal++) {
                BioformRole role = BioformRole.values()[ordinal % BioformRole.values().length];
                bioforms.add(new Bioform(new SubjectId("bioform:" + (nestIndex == 0 ? "west-" : "east-") + ordinal), hiveId,
                        nest.id(), role, placements.get(ordinal)));
            }
        }
        return new FrontierBootstrap(worldId, seed, BOUNDS, settlements, new Hive(hiveId, nests, organs, bioforms), ruleset, terrain);
    }

    private static Settlement settlement(long seed, int index, TerrainSurfacePlan terrain) {
        SubjectId settlementId = new SubjectId("settlement:" + (index + 1));
        BlockPosition horizontalAnchor = new BlockPosition(ANCHORS[index][0], 0, ANCHORS[index][1]);
        List<SettlementStructure> structures = new ArrayList<>();
        int[][] offsets = {{0, 0}, {-20, -12}, {20, -12}, {-20, 14}, {20, 14}, {0, 22}};
        for (StructureKind kind : StructureKind.values()) {
            int[] offset = offsets[kind.ordinal()];
            structures.add(new SettlementStructure(new SubjectId("structure:" + (index + 1) + "-" + kind.name().toLowerCase(Locale.ROOT)),
                    settlementId, kind, horizontalAnchor.offset(offset[0], 0, offset[1]), FacilityFacing.WEST));
        }
        int deckY = SettlementStructureFootprint.settlementDeckY(terrain, structures);
        BlockPosition anchor = horizontalAnchor.offset(0, deckY, 0);
        structures.replaceAll(structure -> new SettlementStructure(structure.id(), structure.settlementId(), structure.kind(),
                structure.anchor().offset(0, deckY, 0), structure.facing()));
        int residents = 20 + KeyedRandom.nextInt(new DecisionKey(seed, "bootstrap", settlementId, "resident-count", 0L), 21);
        List<BlockPosition> placements = FrontierSettlementActorSlots.slots(BOUNDS, terrain, anchor, structures, residents);
        List<Resident> people = new ArrayList<>(residents);
        for (int ordinal = 0; ordinal < residents; ordinal++) {
            people.add(new Resident(new SubjectId("resident:" + (index + 1) + "-" + (ordinal + 1)), settlementId,
                    ResidentRole.values()[ordinal % ResidentRole.values().length], placements.get(ordinal)));
        }
        return new Settlement(settlementId, NAMES[index], anchor, people, structures);
    }
}
