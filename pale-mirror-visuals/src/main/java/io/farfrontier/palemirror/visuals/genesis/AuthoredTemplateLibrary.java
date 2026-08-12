package io.farfrontier.palemirror.visuals.genesis;

import io.farfrontier.palemirror.api.AuthoredRegionSeed;
import io.farfrontier.palemirror.api.VisualModulePlacement;
import io.farfrontier.palemirror.visuals.PaleMirrorVisualsMod;
import io.farfrontier.palemirror.visuals.runtime.VisualGenesisSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.JigsawReplacementProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;

/** Curated copied modules only; Integrated Villages is not a runtime dependency. */
final class AuthoredTemplateLibrary {
    boolean placeReady(ServerLevel level, AuthoredRegionSeed region, VisualModulePlacement module,
                       VisualGenesisSavedData ledger) {
        String key = region.planId() + "@" + module.templateId() + "@" + module.origin();
        if (ledger.moduleCompleted(key)) return true;
        ResourceLocation id = ResourceLocation.parse(module.templateId());
        var optional = level.getStructureManager().get(id);
        if (optional.isEmpty()) return false;
        var template = optional.orElseThrow();
        if (template.getSize().getX() > 32 || template.getSize().getY() > 32 || template.getSize().getZ() > 32) {
            PaleMirrorVisualsMod.LOGGER.warn("Curated module {} is outside the 32-block contract; using grammar fallback", id);
            ledger.completeModule(key);
            return true;
        }
        Rotation rotation = switch (module.quarterTurns()) {
            case 1 -> Rotation.CLOCKWISE_90; case 2 -> Rotation.CLOCKWISE_180;
            case 3 -> Rotation.COUNTERCLOCKWISE_90; default -> Rotation.NONE;
        };
        StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(rotation).setMirror(Mirror.NONE)
                .setIgnoreEntities(true).addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK)
                .addProcessor(JigsawReplacementProcessor.INSTANCE);
        var size = template.getSize(rotation);
        BlockPos origin = new BlockPos(module.origin().x() - size.getX() / 2, module.origin().y() + 1,
                module.origin().z() - size.getZ() / 2);
        BoundingBox bounds = template.getBoundingBox(settings, origin);
        if (!allChunksLoaded(level, bounds)) return false;
        boolean placed = template.placeInWorld(level, origin, origin, settings,
                RandomSource.create((long) key.hashCode() * 31L), 18);
        if (!placed) throw new IllegalStateException("Curated module postcondition failed for " + key);
        ledger.completeModule(key);
        return true;
    }

    private static boolean allChunksLoaded(ServerLevel level, BoundingBox bounds) {
        int minX = bounds.minX() >> 4; int maxX = bounds.maxX() >> 4;
        int minZ = bounds.minZ() >> 4; int maxZ = bounds.maxZ() >> 4;
        for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++) {
            if (!level.hasChunk(x, z)) return false;
        }
        return true;
    }
}
