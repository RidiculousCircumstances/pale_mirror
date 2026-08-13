package io.farfrontier.palemirror.internal.settlement;

import io.farfrontier.palemirror.api.AuthoredRegionSeed;
import io.farfrontier.palemirror.api.ParcelKind;
import io.farfrontier.palemirror.api.SemanticSlotKey;
import io.farfrontier.palemirror.api.StagedVisualModule;
import io.farfrontier.palemirror.api.VisualBlockPlacement;
import io.farfrontier.palemirror.api.VisualProvider;
import io.farfrontier.palemirror.internal.materialization.ParcelRecord;
import io.farfrontier.palemirror.internal.materialization.SemanticCellRecord;
import io.farfrontier.palemirror.internal.materialization.SemanticSlotRegistration;
import io.farfrontier.palemirror.internal.world.PaleMirrorSavedData;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Captures immutable pre-player baselines for later Visuals-authored construction stages. */
public final class AuthoredBlueprintSlots {
    private static final int CELLS_PER_SLOT = 1_024;
    private AuthoredBlueprintSlots() { }

    public static boolean captureAvailable(ServerLevel level, PaleMirrorSavedData data, VisualProvider provider,
                                           AuthoredRegionSeed seed) {
        boolean changed = false;
        for (StagedVisualModule stage : seed.alternateMineSite().stagedModules()) {
            String binding = binding(seed, stage);
            ParcelRecord parcel = data.parcels().parcels().stream()
                    .filter(value -> value.bindingId().equals(binding)).findFirst().orElse(null);
            if (parcel == null || !allChunksLoaded(level, parcel.min(), parcel.max())) continue;
            var snapshot = provider.compileAuthoredModule(stage).orElse(null);
            if (snapshot == null) continue;
            List<VisualBlockPlacement> ordered = ordered(snapshot.blocks());
            for (int from = 0, part = 0; from < ordered.size(); from += CELLS_PER_SLOT, part++) {
                SemanticSlotKey key = key(seed, stage, part);
                if (data.semanticSlots().find(key).isPresent()) continue;
                List<SemanticCellRecord> cells = ordered.subList(from, Math.min(ordered.size(), from + CELLS_PER_SLOT))
                        .stream().map(value -> {
                            BlockPos position = block(value);
                            var observed = level.getBlockState(position);
                            return new SemanticCellRecord(position, observed, observed);
                        }).toList();
                SemanticSlotRegistration.register(data.semanticSlots(), data.parcels(), key, parcel.id(),
                        parcel.dimensionId(), ParcelKind.RESERVED, cells);
                changed = true;
            }
        }
        return changed;
    }

    static List<SemanticSlotKey> keys(PaleMirrorSavedData data, AuthoredRegionSeed seed, StagedVisualModule stage) {
        List<SemanticSlotKey> keys = new ArrayList<>();
        for (int part = 0; ; part++) {
            SemanticSlotKey key = key(seed, stage, part);
            if (data.semanticSlots().find(key).isEmpty()) break;
            keys.add(key);
        }
        return List.copyOf(keys);
    }

    static String binding(AuthoredRegionSeed seed, StagedVisualModule stage) {
        return seed.alternateMineSite().siteId() + ":stage:" + stage.stage();
    }

    private static SemanticSlotKey key(AuthoredRegionSeed seed, StagedVisualModule stage, int part) {
        return new SemanticSlotKey(seed.alternateMineSite().siteId(),
                "alternate_dispatch_" + stage.stage(), "part_" + part);
    }

    private static List<VisualBlockPlacement> ordered(List<VisualBlockPlacement> placements) {
        return placements.stream().sorted(Comparator
                .comparingInt((VisualBlockPlacement value) -> value.position().x() >> 4)
                .thenComparingInt(value -> value.position().z() >> 4)
                .thenComparingInt(value -> value.position().y())
                .thenComparingInt(value -> value.position().x())
                .thenComparingInt(value -> value.position().z())).toList();
    }

    private static boolean allChunksLoaded(ServerLevel level, BlockPos min, BlockPos max) {
        for (int x = min.getX() >> 4; x <= max.getX() >> 4; x++) for (int z = min.getZ() >> 4; z <= max.getZ() >> 4; z++) {
            if (!level.hasChunk(x, z)) return false;
        }
        return true;
    }

    private static BlockPos block(VisualBlockPlacement value) {
        return new BlockPos(value.position().x(), value.position().y(), value.position().z());
    }
}
