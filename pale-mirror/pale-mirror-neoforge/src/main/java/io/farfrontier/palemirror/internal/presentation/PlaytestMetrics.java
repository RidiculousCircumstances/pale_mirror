package io.farfrontier.palemirror.internal.presentation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.google.gson.JsonObject;
import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.domain.DomainEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/** Optional local JSONL evidence for closed-alpha usability sessions. Never canonical state. */
public final class PlaytestMetrics {
    private static final int MAX_SEEN_EVENTS = 2048;
    private final MinecraftServer server;
    private final boolean enabled;
    private final Set<String> seenEvents = new LinkedHashSet<>();
    private boolean failed;

    public PlaytestMetrics(MinecraftServer server) {
        this.server = server;
        this.enabled = Boolean.getBoolean("pale_mirror.playtest_metrics");
    }

    public void atlasOpened(UUID player, long step, int knownRegions) {
        if (!enabled) return;
        JsonObject value = base("ATLAS_OPENED", step);
        value.addProperty("player", anonymize(player));
        value.addProperty("knownRegions", knownRegions);
        append(value);
    }

    public void domainEvents(List<DomainEvent> events) {
        if (!enabled) return;
        for (DomainEvent event : events) {
            if (!seenEvents.add(event.eventId())) continue;
            while (seenEvents.size() > MAX_SEEN_EVENTS) seenEvents.remove(seenEvents.iterator().next());
            JsonObject value = base(event.type().name(), event.simulationStep());
            value.addProperty("subject", event.subject().value());
            append(value);
        }
    }

    private JsonObject base(String type, long step) {
        JsonObject value = new JsonObject();
        value.addProperty("type", type);
        value.addProperty("simulationStep", step);
        value.addProperty("gameTime", server.overworld().getGameTime());
        return value;
    }

    private void append(JsonObject value) {
        if (failed) return;
        try {
            var target = server.getWorldPath(LevelResource.ROOT).resolve("pale-mirror").resolve("playtest-events.jsonl");
            Files.createDirectories(target.getParent());
            Files.writeString(target, value + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
        } catch (IOException failure) {
            failed = true;
            PaleMirrorMod.LOGGER.error("Disabling Pale Mirror playtest metrics after a local write failure", failure);
        }
    }

    private static String anonymize(UUID player) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(player.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 8);
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
