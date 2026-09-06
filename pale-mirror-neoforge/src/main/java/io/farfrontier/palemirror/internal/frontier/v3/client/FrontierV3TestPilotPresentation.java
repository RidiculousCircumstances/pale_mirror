package io.farfrontier.palemirror.internal.frontier.v3.client;

import net.minecraft.client.Minecraft;

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
}
