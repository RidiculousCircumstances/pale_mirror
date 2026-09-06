package io.farfrontier.palemirror.internal.world;

import io.farfrontier.palemirror.api.AuthoredMineSitePlan;
import io.farfrontier.palemirror.api.SemanticVisualVolume;
import io.farfrontier.palemirror.domain.StoryAudienceId;
import io.farfrontier.palemirror.domain.WorldObjectId;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Plans sparse PM ownership from semantic volumes; physical baselines are observed chunk by chunk later. */
final class AuthoredMineSiteObserver {
    private static final String VERSION = "mountain-blueprint-v1";
    private AuthoredMineSiteObserver() { }

    static TestMineRecord plan(ServerLevel level, AuthoredMineSitePlan mine, WorldObjectId id,
                               StoryAudienceId audience) {
        List<MutableCell> cells = new ArrayList<>();
        for (SemanticVisualVolume volume : mine.semanticVolumes()) {
            if (!volume.purpose().equals("INFECTION")) continue;
            InfectionBiomeStage stage = volume.id().contains("controller")
                    ? InfectionBiomeStage.APEX : InfectionBiomeStage.FOOTHOLD;
            sample(volume, stage, cells);
        }
        if (cells.isEmpty()) throw new IllegalStateException("Blueprint MineSite has no infection cells");
        var bounds = mine.bounds();
        WorldObjectRegistryEntry object = new WorldObjectRegistryEntry(id,
                level.dimension().location().toString(), block(mine.controllerAnchor()), block(bounds.min()),
                block(bounds.max()), "pale_mirror:mountain_mine", VERSION, WorldObjectLifecycle.REPRESENTED);
        return new TestMineRecord(object, audience, cells, null, EncounterRecord.none(), null);
    }

    private static void sample(SemanticVisualVolume volume, InfectionBiomeStage stage, List<MutableCell> cells) {
        var min = volume.bounds().min(); var max = volume.bounds().max();
        for (int x = min.x(); x <= max.x() && cells.size() < TestMineRecord.MAX_MUTABLE_CELLS; x += 2) {
            for (int z = min.z(); z <= max.z() && cells.size() < TestMineRecord.MAX_MUTABLE_CELLS; z += 2) {
                for (int y : new int[]{min.y(), max.y()}) {
                    if (cells.size() >= TestMineRecord.MAX_MUTABLE_CELLS) return;
                    BlockPos position = new BlockPos(x, y, z);
                    cells.add(new MutableCell(position, MutableCell.UNOBSERVED_BASELINE,
                            MutableCell.UNOBSERVED_BASELINE, false, stage));
                }
            }
        }
    }

    private static BlockPos block(io.farfrontier.palemirror.api.VisualPoint point) {
        return new BlockPos(point.x(), point.y(), point.z());
    }
}
