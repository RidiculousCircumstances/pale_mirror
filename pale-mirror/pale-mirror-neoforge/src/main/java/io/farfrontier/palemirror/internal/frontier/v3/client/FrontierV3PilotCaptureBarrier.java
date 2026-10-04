package io.farfrontier.palemirror.internal.frontier.v3.client;

import io.farfrontier.palemirror.PaleMirrorMod;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/** Development-only X11 frame handshake. No camera, action-index or simulation authority. */
record FrontierV3PilotCaptureBarrier(int after, String name, String presentation,
                                     long readyAtTick, boolean announced) {
    private static final String CONTROL_PROPERTY =
            "pale_mirror.frontier_v3.test_pilot.capture_control_directory";

    /** Empty means the exact frame was acknowledged; all waiting states retain this barrier. */
    Optional<FrontierV3PilotCaptureBarrier> advance(long gameTick) {
        if (gameTick < readyAtTick) return Optional.of(this);
        String configured = System.getProperty(CONTROL_PROPERTY, "");
        if (configured.isBlank()) throw new IllegalStateException("visual frame declared without capture-control directory");
        Path control = Path.of(configured);
        Path ready = control.resolve("frame-" + after + ".ready");
        Path captured = control.resolve("frame-" + after + ".captured");
        try {
            if (!announced) {
                Files.createDirectories(control);
                Files.writeString(ready, name + "\n", StandardCharsets.UTF_8);
                PaleMirrorMod.LOGGER.info("PMV3_PILOT frame_ready after={} name={} presentation={}", after, name, presentation);
                return Optional.of(new FrontierV3PilotCaptureBarrier(after, name, presentation, readyAtTick, true));
            }
            if (Files.isRegularFile(captured)) {
                Files.deleteIfExists(ready);
                PaleMirrorMod.LOGGER.info("PMV3_PILOT frame_captured after={} name={}", after, name);
                return Optional.empty();
            }
            return Optional.of(this);
        } catch (IOException failure) {
            throw new IllegalStateException("visual frame handshake failed for " + name, failure);
        }
    }
}
