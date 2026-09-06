package io.farfrontier.palemirror.internal.settlement;

import java.util.Objects;
import java.util.UUID;

import io.farfrontier.palemirror.domain.StoryAudienceId;
import io.farfrontier.palemirror.domain.WorldObjectId;

/** Persisted, player-bound authority to place one evacuation shelter marker. */
public final class RefugeeAnchorPermit {
    private final String id;
    private final UUID playerId;
    private final WorldObjectId communityId;
    private final StoryAudienceId audience;
    private final long expiresAtStep;
    private boolean consumed;

    public RefugeeAnchorPermit(String id, UUID playerId, WorldObjectId communityId, StoryAudienceId audience,
                               long expiresAtStep, boolean consumed) {
        this.id = require(id, "id");
        this.playerId = Objects.requireNonNull(playerId, "playerId");
        this.communityId = Objects.requireNonNull(communityId, "communityId");
        this.audience = Objects.requireNonNull(audience, "audience");
        if (expiresAtStep < 0) throw new IllegalArgumentException("Invalid permit expiry");
        this.expiresAtStep = expiresAtStep;
        this.consumed = consumed;
    }

    public String id() { return id; }
    public UUID playerId() { return playerId; }
    public WorldObjectId communityId() { return communityId; }
    public StoryAudienceId audience() { return audience; }
    public long expiresAtStep() { return expiresAtStep; }
    public boolean consumed() { return consumed; }
    public boolean usable(UUID player, WorldObjectId community, StoryAudienceId expectedAudience, long step) {
        return !consumed && step <= expiresAtStep && playerId.equals(player) && communityId.equals(community)
                && audience.equals(expectedAudience);
    }
    public void consume() { if (consumed) throw new IllegalStateException("Refugee anchor permit is already consumed"); consumed = true; }
    private static String require(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }
}
