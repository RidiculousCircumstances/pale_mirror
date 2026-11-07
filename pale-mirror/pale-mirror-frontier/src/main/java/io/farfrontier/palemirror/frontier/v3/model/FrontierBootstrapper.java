package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.DecisionKey;
import io.farfrontier.palemirror.frontier.v3.kernel.KeyedRandom;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Deterministic generator for the single supported fresh-world Frontier v3 profile. */
public final class FrontierBootstrapper {
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
        return create(worldId,seed,ruleset,terrain,java.util.Optional.empty());
    }

    public static FrontierBootstrap restoreLayouts(WorldId worldId,long seed,FrontierRuleset ruleset,TerrainSurfacePlan terrain,
            java.util.Map<SubjectId,SettlementLayoutPlanner.Result> layouts) {
        if(layouts.size()!=ruleset.worldLayout().settlements().size())throw new IllegalArgumentException("incomplete retained settlement layout manifest");
        return create(worldId,seed,ruleset,terrain,java.util.Optional.of(java.util.Map.copyOf(layouts)));
    }
    private static FrontierBootstrap create(WorldId worldId,long seed,FrontierRuleset ruleset,TerrainSurfacePlan terrain,
            java.util.Optional<java.util.Map<SubjectId,SettlementLayoutPlanner.Result>> layouts) {
        java.util.Objects.requireNonNull(ruleset, "ruleset");
        java.util.Objects.requireNonNull(terrain, "terrain surface plan");
        List<Settlement> settlements = new ArrayList<>(ruleset.worldLayout().settlements().size());
        for (int index = 0; index < ruleset.worldLayout().settlements().size(); index++) {
            var id=new SubjectId("settlement:"+(index+1));
            settlements.add(settlement(seed,index,ruleset,terrain,layouts.map(values->java.util.Objects.requireNonNull(values.get(id),"missing retained settlement layout"))));
        }
        SubjectId hiveId = new SubjectId("hive:frontier");
        List<HiveNest> provisionalNests = List.of(
                new HiveNest(new SubjectId("nest:seed-west"), hiveId, new BlockPosition(-420, 0, 420)),
                new HiveNest(new SubjectId("nest:seed-east"), hiveId, new BlockPosition(420, 0, 420)));
        List<HiveOrgan> provisionalOrgans = new ArrayList<>();
        for (HiveNest nest : provisionalNests) {
            String suffix = nest.id().value().substring("nest:seed-".length());
            provisionalOrgans.add(new HiveOrgan(new SubjectId("organ:" + suffix + "-ganglion"), hiveId, nest.id(), HiveOrganKind.GANGLION,
                    nest.anchor(), java.util.Optional.empty()));
            provisionalOrgans.add(new HiveOrgan(new SubjectId("organ:" + suffix + "-brood"), hiveId, nest.id(), HiveOrganKind.BROOD,
                    nest.anchor().offset(8, 0, 0), java.util.Optional.empty()));
            provisionalOrgans.add(new HiveOrgan(new SubjectId("organ:" + suffix + "-store"), hiveId, nest.id(), HiveOrganKind.STORE,
                    nest.anchor().offset(-8, 0, 0), java.util.Optional.of(new SubjectId("container:hive-" + suffix + "-store"))));
            for (int index = 0; index < 3; index++) {
                provisionalOrgans.add(new HiveOrgan(new SubjectId("organ:" + suffix + "-hibernaculum-" + (index + 1)), hiveId, nest.id(),
                        // Seed organs expand toward the declared positive-X/positive-Z growth
                        // catalogue. Cocoon banks occupy the opposite, reserved side of the
                        // nest so the first (and every later checked) organ footprint has no
                        // hidden physical overlap.
                        HiveOrganKind.HIBERNACULUM, nest.anchor().offset(-8 + index * 8, 0, -10), java.util.Optional.empty()));
            }
        }
        List<HiveNest> nests = new ArrayList<>(provisionalNests.size());
        List<HiveOrgan> organs = new ArrayList<>(provisionalOrgans.size());
        for (HiveNest provisional : provisionalNests) {
            List<HiveOrgan> nestOrgans = provisionalOrgans.stream().filter(organ -> organ.nestId().equals(provisional.id())).toList();
            int deckY = HiveOrganSupportPlan.nestDeckY(terrain, nestOrgans);
            nests.add(new HiveNest(provisional.id(), hiveId, provisional.anchor().offset(0, deckY, 0)));
            nestOrgans.forEach(organ -> organs.add(new HiveOrgan(organ.id(), organ.hiveId(), organ.nestId(), organ.kind(),
                    organ.anchor().offset(0, deckY, 0), organ.containerId())));
        }
        List<Bioform> bioforms = new ArrayList<>();
        for (int nestIndex = 0; nestIndex < nests.size(); nestIndex++) {
            HiveNest nest = nests.get(nestIndex);
            List<BlockPosition> placements = FrontierHiveActorSlots.slots(ruleset.worldLayout().bounds(), terrain, nest, organs, 24);
            for (int ordinal = 0; ordinal < 24; ordinal++) {
                bioforms.add(new Bioform(new SubjectId("bioform:" + (nestIndex == 0 ? "west-" : "east-") + ordinal), hiveId,
                        nest.id(), chassis(ordinal), mutations(ordinal), assignment(ordinal), placements.get(ordinal)));
            }
        }
        return new FrontierBootstrap(worldId, seed, ruleset.worldLayout().bounds(), settlements, new Hive(hiveId, nests, organs, bioforms), ruleset, terrain);
    }

    private static BioformChassis chassis(int ordinal) {
        if (ordinal == 23) return BioformChassis.OVERSEER;
        return Math.floorMod(ordinal, 4) == 1 ? BioformChassis.SENTINEL : BioformChassis.RUNT;
    }

    private static java.util.Set<BioformMutation> mutations(int ordinal) {
        // The dormant command body is a distinct exact controller, not a recycled bomber
        // profile with a conflicting payload mutation.
        if (ordinal == 23) return java.util.Set.of();
        return switch (Math.floorMod(ordinal, 4)) {
            case 2 -> java.util.Set.of(BioformMutation.ARMORED);
            case 3 -> java.util.Set.of(BioformMutation.EXPLOSIVE);
            default -> java.util.Set.of();
        };
    }

    private static BioformAssignment assignment(int ordinal) {
        if (ordinal == 23) return BioformAssignment.WATCH;
        return switch (Math.floorMod(ordinal, 4)) {
            case 0 -> BioformAssignment.HARVEST;
            case 1 -> BioformAssignment.SCOUT;
            case 2 -> BioformAssignment.DEFEND;
            default -> BioformAssignment.ASSAULT;
        };
    }

    private static Settlement settlement(long seed, int index, FrontierRuleset ruleset, TerrainSurfacePlan terrain, java.util.Optional<SettlementLayoutPlanner.Result> retained) {
        SubjectId settlementId = new SubjectId("settlement:" + (index + 1));
        BlockPosition horizontalAnchor = ruleset.worldLayout().settlements().get(index);
        int residents = 20 + KeyedRandom.nextInt(new DecisionKey(seed, "bootstrap", settlementId, "resident-count", 0L), 21);
        var content=io.farfrontier.palemirror.frontier.v3.model.buildings.BuildingCatalog.standard();
        var style=retained.map(value -> value.layout().style())
                .orElseGet(() -> SettlementLayoutRules.standard().styleFor(settlementId));
        var requests=new ArrayList<SettlementLayoutPlanner.Request>();
        for(var kind:StructureKind.values()) {
            if(kind==StructureKind.HOUSING)continue;
            if(kind==StructureKind.SMELTERY && ruleset.production().recipes().stream().noneMatch(recipe->recipe.capability()==ProductionStationSpec.Capability.SMELTING))continue;
            if(kind==StructureKind.FORGE && ruleset.production().recipes().stream().noneMatch(recipe->recipe.capability()==ProductionStationSpec.Capability.FABRICATION))continue;
            if(kind==StructureKind.WOODSHOP && ruleset.production().recipes().stream().noneMatch(recipe->recipe.capability()==ProductionStationSpec.Capability.WOODWORKING))continue;
            if (kind == StructureKind.TEXTILE_WORKSHOP && ruleset.production().recipes().stream().noneMatch(recipe ->
                    recipe.capability() == ProductionStationSpec.Capability.SPINNING || recipe.capability() == ProductionStationSpec.Capability.WEAVING)) continue;
            requests.add(new SettlementLayoutPlanner.Request(new SubjectId("structure:"+(index+1)+"-"+kind.name().toLowerCase(Locale.ROOT)),kind,content.select(style,kind)));
        }
        int capacity=0,house=0;var variants=content.variants(style,StructureKind.HOUSING);
        while(capacity<residents+SettlementLayoutRules.standard().spareBeds()) {
            var selected=house<SettlementLayoutRules.standard().smallHouseCount()
                    ? variants.get(Math.floorMod(Objects.hash(seed,settlementId,house),variants.size()))
                    : variants.stream().max(java.util.Comparator.comparingInt(v->content.require(v,StructureKind.HOUSING).beds().size())).orElseThrow();
            int beds=content.require(selected,StructureKind.HOUSING).beds().size();
            if(beds==0)throw new IllegalArgumentException("housing template has no declared beds");
            requests.add(new SettlementLayoutPlanner.Request(new SubjectId("structure:"+(index+1)+"-housing-"+(++house)),StructureKind.HOUSING,selected));capacity+=beds;
        }
        var anchor=new BlockPosition(horizontalAnchor.x(),terrain.supportYAt(horizontalAnchor.x(),horizontalAnchor.z()),horizontalAnchor.z());
        var planned=retained.orElseGet(()->SettlementLayoutPlanner.plan(seed,settlementId,style,anchor,terrain,ruleset.worldLayout().bounds(),requests));
        SettlementLayoutValidation.validate(planned,terrain,ruleset.worldLayout().bounds());
        if(!planned.layout().settlementId().equals(settlementId))throw new IllegalArgumentException("retained layout belongs to another settlement");
        capacity=planned.buildings().stream().filter(b->b.kind()==StructureKind.HOUSING).mapToInt(b->b.definition().beds().size()).sum();
        if(capacity<residents)throw new IllegalArgumentException("retained layout lacks resident bed capacity");
        var structures=planned.buildings();
        var placements=SettlementResidentIngressPlan.compile(ruleset.worldLayout().bounds(),terrain,settlementId,anchor,structures,
                planned.layout(),Math.max(residents,capacity)).homeSlots().subList(0,residents);
        List<Resident> people = new ArrayList<>(residents);
        for (int ordinal = 0; ordinal < residents; ordinal++) {
            people.add(new Resident(new SubjectId("resident:" + (index + 1) + "-" + (ordinal + 1)), settlementId,
                    ResidentRole.values()[ordinal % ResidentRole.values().length], placements.get(ordinal)));
        }
        return new Settlement(settlementId, ruleset.worldLayout().settlementNames().get(index), anchor, people, structures, planned.layout());
    }
}
