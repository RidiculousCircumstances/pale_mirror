package io.farfrontier.palemirror.internal.frontier.v3.client;

import io.farfrontier.palemirror.PaleMirrorMod;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Exact spawned-client X11/GLFW admission; it has no server or scenario authority. */
final class FrontierV3PilotGlfwAdmission {
    private static final String PROPERTY = "pale_mirror.frontier_v3.test_pilot.x11_admission_receipt";
    private static boolean published;

    private FrontierV3PilotGlfwAdmission() { }

    static boolean publishIfRequested(Minecraft minecraft) {
        String requested = System.getProperty(PROPERTY, "");
        if (requested.isBlank()) return false;
        if (published) return true;
        long window = minecraft.getWindow().getWindow();
        if (window == 0L) return true;
        try {
            Path receipt = Path.of(requested);
            if (!receipt.isAbsolute() || receipt.getParent() == null) throw new IllegalArgumentException("pilot X11 admission receipt path is invalid");
            Files.createDirectories(receipt.getParent());
            String display = System.getenv("DISPLAY");
            if (display == null || display.isBlank()) throw new IllegalStateException("pilot GLFW admission has no display");
            Files.writeString(receipt, receipt(ProcessHandle.current().pid(), display, window), StandardCharsets.UTF_8);
            published = true;
            PaleMirrorMod.LOGGER.info("PMV3_PILOT_GLFW_ADMITTED display={} window={}", display, window);
            minecraft.execute(minecraft::stop);
            return true;
        } catch (IOException | RuntimeException failure) {
            throw new IllegalStateException("pilot GLFW admission receipt failed", failure);
        }
    }

    private static String receipt(long pid, String display, long window) {
        return "{\"kind\":\"frontier-v3-pilot-glfw-admission\",\"pid\":" + pid + ",\"display\":\""
                + display.replace("\\", "\\\\").replace("\"", "\\\"") + "\",\"window\":" + window + "}\n";
    }
}
