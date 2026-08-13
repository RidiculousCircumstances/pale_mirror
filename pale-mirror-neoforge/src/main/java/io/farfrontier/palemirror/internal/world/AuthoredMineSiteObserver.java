package io.farfrontier.palemirror.internal.world;

import io.farfrontier.palemirror.api.AuthoredMineSitePlan;
import io.farfrontier.palemirror.api.SemanticVisualVolume;
import io.farfrontier.palemirror.domain.StoryAudienceId;
import io.farfrontier.palemirror.domain.WorldObjectId;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;

/** Adopts a stamped blueprint MineSite through semantic volumes, never through palette-specific block probes. */
final class AuthoredMineSiteObserver {
    private static final String VERSION = "mountain-blueprint-v1";
    private static final int MAX_MUTABLE_CELLS = 384;

    private AuthoredMineSiteObserver() { }

    static boolean isAreaLoaded(ServerLevel level, AuthoredMineSitePlan mine) {
        for (SemanticVisualVolume volume : mine.semanticVolumes()) {
            int minX = volume.bounds().min().x() >> 4; int maxX = volume.bounds().max().x() >> 4;
            int minZ = volume.bounds().min().z() >> 4; int maxZ = volume.bounds().max().z() >> 4;
            for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++) {
                if (!level.hasChunk(x, z)) return false;
            }
        }
        return true;
    }

    static TestMineRecord observe(ServerLevel level, AuthoredMineSitePlan mine, WorldObjectId id,
                                  StoryAudienceId audience) {
        if (!isAreaLoaded(level, mine)) throw new IllegalStateException("Blueprint MineSite is not fully loaded");
        List<MutableCell> cells = new ArrayList<>();
        for (SemanticVisualVolume volume : mine.semanticVolumes()) {
            if (!volume.purpose().equals("INFECTION")) continue;
            InfectionBiomeStage stage = volume.id().contains("controller")
                    ? InfectionBiomeStage.APEX : InfectionBiomeStage.INFESTED;
            sample(level, volume, stage, cells);
        }
        if (cells.isEmpty()) throw new IllegalStateException("Blueprint MineSite has no infection cells");
        var bounds = mine.bounds();
        WorldObjectRegistryEntry object = new WorldObjectRegistryEntry(id,
                level.dimension().location().toString(), block(mine.controllerAnchor()), block(bounds.min()),
                block(bounds.max()), "pale_mirror:mountain_mine", VERSION, WorldObjectLifecycle.REPRESENTED);
        return new TestMineRecord(object, audience, cells, null, EncounterRecord.none(), null);
    }

    private static void sample(ServerLevel level, SemanticVisualVolume volume, InfectionBiomeStage stage,
                               List<MutableCell> cells) {
        var min = volume.bounds().min(); var max = volume.bounds().max();
        for (int x = min.x(); x <= max.x() && cells.size() < MAX_MUTABLE_CELLS; x += 2) {
            for (int z = min.z(); z <= max.z() && cells.size() < MAX_MUTABLE_CELLS; z += 2) {
                for (int y : new int[]{min.y(), max.y()}) {
                    BlockPos position = new BlockPos(x, y, z);
                    var state = level.getBlockState(position);
                    String block = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
                    cells.add(new MutableCell(position, block, block, false, stage));
                }
            }
        }
    }

    private static BlockPos block(io.farfrontier.palemirror.api.VisualPoint point) {
        return new BlockPos(point.x(), point.y(), point.z());
    }
}
