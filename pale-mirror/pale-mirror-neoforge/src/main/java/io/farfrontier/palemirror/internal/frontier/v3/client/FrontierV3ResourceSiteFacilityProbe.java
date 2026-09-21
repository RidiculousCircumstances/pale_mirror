package io.farfrontier.palemirror.internal.frontier.v3.client;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;

/** Exhaustive local-client read of the current exact 8x8 resource-site facility. */
final class FrontierV3ResourceSiteFacilityProbe {
    record Result(boolean current, int farmland, int air, int wheat, int water) { }

    private FrontierV3ResourceSiteFacilityProbe() { }

    static Result inspect(Minecraft minecraft, JsonObject site, int completed, String siteId) {
        JsonObject first = anchor(site, "firstCrop");
        JsonObject last = anchor(site, "lastCrop");
        if (first == null || last == null) throw new IllegalStateException("site diagnostic lacks complete field anchors for " + siteId);
        int minX = Math.min(first.get("x").getAsInt(), last.get("x").getAsInt());
        int minZ = Math.min(first.get("z").getAsInt(), last.get("z").getAsInt());
        int y = first.get("y").getAsInt();
        if (Math.abs(first.get("x").getAsInt() - last.get("x").getAsInt()) != 7
                || first.get("z").getAsInt() != last.get("z").getAsInt() || last.get("y").getAsInt() != y) {
            throw new IllegalStateException("site diagnostic has non-8x8 field geometry for " + siteId);
        }
        int expectedStage = expectedCropStage(site, siteId);
        int farmland = 0, air = 0, wheat = 0, water = 0;
        for (int x = minX; x < minX + 8; x++) for (int z = minZ; z < minZ + 8; z++) {
            BlockPos crop = new BlockPos(x, y, z); BlockPos soil = crop.below();
            if (!minecraft.level.hasChunkAt(crop) || !minecraft.level.hasChunkAt(soil)) return new Result(false, farmland, air, wheat, water);
            if (minecraft.level.getBlockState(soil).is(Blocks.FARMLAND)) farmland++;
            int slot = (x - minX) * 8 + (((x - minX) & 1) == 0 ? z - minZ : minZ + 7 - z);
            if (minecraft.level.getBlockState(crop).isAir()) air++;
            if (minecraft.level.getBlockState(crop).equals(Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, expectedStage))) wheat++;
            if (minecraft.level.getBlockState(crop).isAir() != (slot < completed)) return new Result(false, farmland, air, wheat, water);
            if (slot >= completed && !minecraft.level.getBlockState(crop).equals(Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, expectedStage))) return new Result(false, farmland, air, wheat, water);
        }
        for (BlockPos waterSource : new BlockPos[] {new BlockPos(minX + 2, y - 1, minZ - 1), new BlockPos(minX + 6, y - 1, minZ - 1),
                new BlockPos(minX + 2, y - 1, minZ + 8), new BlockPos(minX + 6, y - 1, minZ + 8)}) {
            if (!minecraft.level.hasChunkAt(waterSource)) return new Result(false, farmland, air, wheat, water);
            if (minecraft.level.getBlockState(waterSource).equals(Blocks.WATER.defaultBlockState())) water++;
        }
        return new Result(farmland == 64 && air == completed && wheat == 64 - completed && water == 4, farmland, air, wheat, water);
    }

    /** The local block read must agree with the diagnostic's current lifecycle stage, not silently require maturity. */
    static int expectedCropStage(JsonObject site, String siteId) {
        if (!site.has("growthStage") || !site.get("growthStage").isJsonPrimitive()) {
            throw new IllegalStateException("site diagnostic lacks current growth stage for " + siteId);
        }
        int stage = site.get("growthStage").getAsInt();
        if (stage < 0 || stage > 7) throw new IllegalStateException("site diagnostic has invalid growth stage for " + siteId + ": " + stage);
        return stage;
    }

    /** Resolves the finite structural anchor vocabulary allowed by the pilot schema. */
    static JsonObject anchor(JsonObject diagnostic, String field) {
        JsonObject current = diagnostic;
        String[] segments = field.split("\\.", -1);
        if (segments.length == 0 || segments.length > 2) return null;
        for (String segment : segments) {
            if (segment.isEmpty() || !current.has(segment) || !current.get(segment).isJsonObject()) return null;
            current = current.getAsJsonObject(segment);
        }
        return current;
    }
}
