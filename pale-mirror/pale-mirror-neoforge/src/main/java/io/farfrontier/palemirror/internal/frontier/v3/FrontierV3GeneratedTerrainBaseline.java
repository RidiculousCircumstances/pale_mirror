package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.FrontierBootstrap;
import io.farfrontier.palemirror.internal.agriculture.AgriculturalFlatGenerator;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.FlatLevelSource;

/** No-load agreement between declared COLD terrain and the actual native generator. */
final class FrontierV3GeneratedTerrainBaseline {
    private FrontierV3GeneratedTerrainBaseline() { }
    static void requireAgriculture(
            java.util.Optional<io.farfrontier.palemirror.frontier.v3.model.agriculture.TerritoryAgriculture> declared,
            java.util.Optional<io.farfrontier.palemirror.frontier.v3.model.agriculture.TerritoryAgriculture> generated) {
        if (declared.isPresent() && !declared.equals(generated))
            throw new IllegalStateException("declared agricultural terrain disagrees with the native generator; install the matching companion-pack graybox datapack before world creation");
    }
    static void require(ServerLevel level, FrontierBootstrap bootstrap) {
        var generator = level.getChunkSource().getGenerator();
        requireAgriculture(bootstrap.ruleset().fields().agriculture(),
                generator instanceof AgriculturalFlatGenerator agricultural ? java.util.Optional.of(agricultural.agriculture()) : java.util.Optional.empty());
        var declaration = bootstrap.ruleset().extraction().geology();
        if (declaration.isEmpty()) return;
        // Both implementations retain uniform subsurface layers. Agricultural
        // surface decoration is above the declared stone stratum checked below.
        if (!(generator instanceof FlatLevelSource) && !(generator instanceof AgriculturalFlatGenerator))
            throw new IllegalStateException("declared uniform geology requires the actual flat graybox generator; arbitrary terrain needs surveyed knowledge");
        var geology = declaration.orElseThrow();
        var column = generator.getBaseColumn(bootstrap.bounds().minX(), bootstrap.bounds().minZ(), level,
                level.getChunkSource().randomState());
        for (int y = geology.minY(); y <= geology.maxY(); y++) {
            var actual = FrontierV3MinecraftBlockExtraction.describe(column.getBlock(y));
            if (!actual.kind().equals(geology.blockKind()) || !actual.properties().isEmpty())
                throw new IllegalStateException("declared geology disagrees with native generator at y=" + y + ": " + actual);
        }
    }
}
