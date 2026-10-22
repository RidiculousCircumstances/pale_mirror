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

/** Graybox content adapter on the normal worldgen feature seam. Future terrain
 * providers may use the same native baseline without depending on this flat plan. */
public final class FrontierV3GrayboxGeologyFeature extends Feature<NoneFeatureConfiguration> {
    static final String PROVIDER = "pale_mirror:graybox_geology_v1";
    private record Plan(long seed, FrontierRuleset rules, String configuration, RegionalBlockMap<BlockExtraction.Block> blocks) { }
    // Generator lifetime owns the immutable plan. Values contain neither worlds nor generators.
    private final Map<ChunkGenerator, Plan> plans = new WeakHashMap<>();
    public FrontierV3GrayboxGeologyFeature() { super(NoneFeatureConfiguration.CODEC); }

    private synchronized Plan plan(ChunkGenerator generator, long seed) {
        var rules = FrontierV3WorldgenSettings.ruleset();
        var retained = plans.get(generator);
        if (retained != null) {
            if (retained.seed() != seed || retained.rules() != rules)
                throw new IllegalStateException("generation configuration changed within one generator lifetime");
            return retained;
        }
        var bootstrap = FrontierBootstrapper.create(FrontierV3PhysicalWorld.WORLD_ID, seed, rules);
        var value = new Plan(seed, rules, rules.contentSha256(), GrayboxNaturalGeology.compile(bootstrap));
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
        if (cells.isEmpty()) return false;
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
        return true;
    }
}
