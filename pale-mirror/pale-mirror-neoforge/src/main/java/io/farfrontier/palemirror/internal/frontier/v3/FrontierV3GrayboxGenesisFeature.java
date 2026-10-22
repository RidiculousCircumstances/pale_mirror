package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.geometry.RegionalBlockMap;
import io.farfrontier.palemirror.frontier.v3.model.extraction.BlockExtraction;
import io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianGeometryRevision.Region;
import io.farfrontier.palemirror.internal.agriculture.AgriculturalFlatGenerator;
import io.farfrontier.palemirror.internal.world.GeneratedGeology;
import io.farfrontier.palemirror.internal.world.GeologyRegistration;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import java.util.*;

/** Initial graybox geology and settlement fabric, written once through native
 * chunk generation. Runtime construction, inventories and fields are not genesis. */
public final class FrontierV3GrayboxGenesisFeature extends Feature<NoneFeatureConfiguration> {
    static final String PROVIDER = "pale_mirror:graybox_geology_v1";
    private record Plan(long seed, FrontierRuleset rules, String configuration, RegionalBlockMap<BlockExtraction.Block> blocks,
                        RegionalBlockMap<GrayboxCell> structures, RegionalBlockMap<WorksiteBlock> worksites) { }
    // Generator lifetime owns the immutable plan. Values contain neither worlds nor generators.
    private final Map<ChunkGenerator, Plan> plans = new WeakHashMap<>();
    public FrontierV3GrayboxGenesisFeature() { super(NoneFeatureConfiguration.CODEC); }

    private synchronized Plan plan(ChunkGenerator generator, long seed) {
        var rules = FrontierV3WorldgenSettings.ruleset();
        var retained = plans.get(generator);
        if (retained != null) {
            if (retained.seed() != seed || retained.rules() != rules)
                throw new IllegalStateException("generation configuration changed within one generator lifetime");
            return retained;
        }
        var bootstrap = FrontierBootstrapper.create(FrontierV3PhysicalWorld.WORLD_ID, seed, rules);
        var value = new Plan(seed, rules, rules.contentSha256(), GrayboxNaturalGeology.compile(bootstrap),
                RegionalBlockMap.copyOf(FrontierGrayboxPlan.compileInitialSettlementFabric(bootstrap).cells()),
                GrayboxInitialWorksites.compile(bootstrap));
        plans.put(generator, value); return value;
    }
    @Override public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        var level = context.level();
        if (!FrontierV3PhysicalWorld.isPhysical(level.getLevel())) return false;
        // This provider deliberately authors a graybox rock rise. Never apply that policy
        // to arbitrary mod terrain, caves or liquids under the guise of generic ore placement.
        if (!(context.chunkGenerator() instanceof AgriculturalFlatGenerator)) return false;
        var plan = plan(context.chunkGenerator(), level.getSeed());
        var origin = context.origin();
        var region = new Region(origin.getX() >> 4, origin.getZ() >> 4);
        var cells = plan.blocks().in(region);
        var structures = plan.structures().in(region);
        var worksites = plan.worksites().in(region);
        if (cells.isEmpty() && structures.isEmpty() && worksites.isEmpty()) return false;
        var chunk = level.getChunk(region.x(), region.z());
        if (chunk.hasData(GeologyRegistration.BASELINE))
            throw new IllegalStateException("geological baseline cannot be generated twice for " + chunk.getPos());
        var placed = new HashMap<BlockPos, BlockState>();
        for (var entry : cells.entrySet()) {
            var p = entry.getKey(); var position = new BlockPos(p.x(), p.y(), p.z());
            var actual = chunk.getBlockState(position);
            var expected = FrontierV3MinecraftBlockExtraction.block(entry.getValue());
            if (!actual.is(Blocks.STONE) && !actual.isAir() && !actual.equals(expected)
                    && !actual.equals(context.chunkGenerator().getBaseColumn(p.x(), p.z(), level,
                            level.getLevel().getChunkSource().randomState()).getBlock(p.y())))
                throw new IllegalStateException("graybox geology encountered an undeclared host at " + position + ": " + actual);
            if (!level.setBlock(position, expected, 2) && !chunk.getBlockState(position).equals(expected))
                throw new IllegalStateException("initial geology placement failed at " + position);
            placed.put(position, expected);
        }
        chunk.setData(GeologyRegistration.BASELINE, Optional.of(new GeneratedGeology(PROVIDER, plan.configuration(), plan.seed(), placed)));
        var authored = new HashMap<BlockPos, GrayboxCell>();
        for (var cell : structures.values()) {
            var p = cell.position(); var position = new BlockPos(p.x(), p.y(), p.z());
            // The selected graybox provider declares empty space for its initial fabric.
            // It cannot silently bulldoze natural geology or another generator's structure.
            if (!chunk.getBlockState(position).isAir())
                throw new IllegalStateException("initial structure overlaps undeclared host at " + position);
            var expected = FrontierV3GrayboxExecutor.material(cell.material());
            if (!level.setBlock(position, expected, 2) && !chunk.getBlockState(position).equals(expected))
                throw new IllegalStateException("initial structure placement failed at " + position);
            authored.put(position, cell);
        }
        chunk.setData(GeologyRegistration.STRUCTURES, Optional.of(new FrontierV3GeneratedStructures(
                "pale_mirror:graybox_structures_v1", plan.configuration(), plan.seed(), authored)));
        var infrastructure = new HashMap<BlockPos, WorksiteBlock>();
        for (var cell : worksites.values()) {
            var position = FrontierV3WorksiteProjection.position(cell);
            if (authored.containsKey(position)) throw new IllegalStateException("initial worksite overlaps structure at " + position);
            var host = placed.getOrDefault(position, context.chunkGenerator().getBaseColumn(position.getX(), position.getZ(), level,
                    level.getLevel().getChunkSource().randomState()).getBlock(position.getY()));
            if (!chunk.getBlockState(position).equals(host))
                throw new IllegalStateException("initial worksite encountered an undeclared host at " + position);
            var expected = FrontierV3MinecraftBlockExtraction.block(cell.block());
            if (!level.setBlock(position, expected, 2) && !chunk.getBlockState(position).equals(expected))
                throw new IllegalStateException("initial worksite placement failed at " + position);
            infrastructure.put(position, cell);
        }
        chunk.setData(GeologyRegistration.WORKSITES, Optional.of(new FrontierV3GeneratedWorksites(
                plan.configuration(), plan.seed(), infrastructure)));
        return true;
    }
}
