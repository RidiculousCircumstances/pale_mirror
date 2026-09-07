package io.farfrontier.palemirror.internal.frontier.v3.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Test-only local presentation cleanup; it never mutates server or canonical state. */
final class FrontierV3TestPilotPresentation {
    private static Boolean originalHideGui;

    private FrontierV3TestPilotPresentation() { }

    static void prepareCleanCapture(Minecraft minecraft) {
        if (originalHideGui == null) originalHideGui = minecraft.options.hideGui;
        minecraft.options.hideGui = true;
        minecraft.setScreen(null);
        clear(minecraft);
    }

    static void preparePlayerCapture(Minecraft minecraft) { clear(minecraft); }

    static void clear(Minecraft minecraft) {
        minecraft.gui.getChat().clearMessages(false);
        minecraft.getTutorial().stop();
        minecraft.getToasts().clear();
    }

    static void reset(Minecraft minecraft) {
        if (originalHideGui != null) {
            minecraft.options.hideGui = originalHideGui;
            originalHideGui = null;
        }
    }

    static Entity nearestVisibleNamedEntity(Minecraft minecraft, ResourceLocation expectedType, String expectedName, double maximum) {
        return minecraft.level.getEntitiesOfClass(Entity.class, minecraft.player.getBoundingBox().inflate(maximum), entity ->
                        !entity.isRemoved() && BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).equals(expectedType)
                                && (entity instanceof Display.TextDisplay || entity.isCustomNameVisible())
                                && entity.getCustomName() != null && entity.getCustomName().getString().contains(expectedName)
                                && minecraft.player.hasLineOfSight(entity))
                .stream().sorted(java.util.Comparator.comparingDouble((Entity entity) -> entity.distanceToSqr(minecraft.player))
                        .thenComparing(Entity::getUUID)).findFirst().orElse(null);
    }

    /** Bounded presentation evidence; it neither selects nor mutates a server entity. */
    static String localEntityEvidence(Minecraft minecraft, ResourceLocation expectedType, String expectedName, double maximum) {
        Vec3 eye = minecraft.player.getEyePosition();
        return minecraft.level.getEntitiesOfClass(Entity.class, minecraft.player.getBoundingBox().inflate(maximum), entity ->
                        !entity.isRemoved() && BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).equals(expectedType)
                                && entity.getCustomName() != null && entity.getCustomName().getString().contains(expectedName))
                .stream().sorted(java.util.Comparator.comparingDouble((Entity entity) -> entity.distanceToSqr(minecraft.player))
                        .thenComparing(Entity::getUUID)).limit(8).map(entity -> entity.getUUID() + "@"
                        + entity.getBlockX() + "," + entity.getBlockY() + "," + entity.getBlockZ() + "/los="
                        + minecraft.player.hasLineOfSight(entity) + "/distance=" + Math.round(eye.distanceTo(entity.getEyePosition())))
                .collect(java.util.stream.Collectors.joining(";"));
    }
}
