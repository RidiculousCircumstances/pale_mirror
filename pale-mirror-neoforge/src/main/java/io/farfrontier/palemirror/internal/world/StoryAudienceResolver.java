package io.farfrontier.palemirror.internal.world;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import io.farfrontier.palemirror.domain.StoryAudienceId;
import net.minecraft.server.level.ServerPlayer;

/** Resolves stable personal or scoreboard-team audience identities without merging their histories. */
public final class StoryAudienceResolver {
    private StoryAudienceResolver() { }

    public static StoryAudienceId resolve(PaleMirrorSavedData data, ServerPlayer player) {
        String binding = player.getTeam() == null ? "player:" + player.getUUID()
                : "team:" + player.getTeam().getName();
        StoryAudienceId current = data.audienceMappings().get(binding);
        if (current != null) return current;
        StoryAudienceId created = new StoryAudienceId("pm:audience:"
                + UUID.nameUUIDFromBytes(binding.getBytes(StandardCharsets.UTF_8)));
        data.audienceMappings().put(binding, created);
        data.setDirty();
        return created;
    }
}
