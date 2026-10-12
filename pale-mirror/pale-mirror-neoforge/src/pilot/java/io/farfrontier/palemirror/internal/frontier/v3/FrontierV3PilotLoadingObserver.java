package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Development-only read-only distinction between terrain receipt, renderer readiness and loading UI. */
@EventBusSubscriber(modid = PaleMirrorMod.MOD_ID, value = Dist.CLIENT)
public final class FrontierV3PilotLoadingObserver {
    private static final int MAX_RECORDS = 64;
    private static View previous;
    private static int records;
    private record View(String dimension, String screen, BlockPos feet, BlockPos camera,
            boolean chunkReceived, boolean sectionCompiled, int receivedNeighbours, int compiledNeighbours) { }
    private FrontierV3PilotLoadingObserver() { }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (System.getProperty("pale_mirror.frontier_v3.test_pilot.scenario", "").isBlank() || records >= MAX_RECORDS) return;
        var minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) return;
        var feet = minecraft.player.blockPosition();
        int receivedNeighbours = 0, compiledNeighbours = 0, bit = 0;
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++, bit++) {
            var neighbour = feet.offset(dx * 16, 0, dz * 16);
            if (minecraft.level.getChunkSource().hasChunk(neighbour.getX() >> 4, neighbour.getZ() >> 4)) receivedNeighbours |= 1 << bit;
            if (minecraft.levelRenderer.isSectionCompiled(neighbour)) compiledNeighbours |= 1 << bit;
        }
        var view = new View(minecraft.level.dimension().location().toString(),
                minecraft.screen == null ? "NONE" : minecraft.screen.getClass().getSimpleName(), feet,
                minecraft.gameRenderer.getMainCamera().getBlockPosition().immutable(),
                minecraft.level.getChunkSource().hasChunk(feet.getX() >> 4, feet.getZ() >> 4),
                minecraft.levelRenderer.isSectionCompiled(feet), receivedNeighbours, compiledNeighbours);
        if (view.equals(previous)) return;
        previous = view; records++;
        PaleMirrorMod.LOGGER.info("PMV3_PILOT_LOADING dimension={} screen={} feet={} camera={} chunkReceived={} sectionCompiled={} receivedNeighbours={} compiledNeighbours={}",
                view.dimension(), view.screen(), view.feet(), view.camera(), view.chunkReceived(), view.sectionCompiled(),
                view.receivedNeighbours(), view.compiledNeighbours());
    }
}
