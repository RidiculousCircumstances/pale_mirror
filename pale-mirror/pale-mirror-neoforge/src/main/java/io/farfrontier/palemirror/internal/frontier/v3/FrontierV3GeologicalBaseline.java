package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.FrontierBootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.FlatLevelSource;

/** No-load validation of the explicitly configured uniform geological content provider. */
final class FrontierV3GeologicalBaseline {
    private FrontierV3GeologicalBaseline() { }
    static void require(ServerLevel level, FrontierBootstrap bootstrap) {
        var declaration = bootstrap.ruleset().extraction().geology();
        if (declaration.isEmpty()) return;
        var generator = level.getChunkSource().getGenerator();
        if (!(generator instanceof FlatLevelSource))
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
