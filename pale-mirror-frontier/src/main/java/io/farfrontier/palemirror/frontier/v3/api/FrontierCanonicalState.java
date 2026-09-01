package io.farfrontier.palemirror.frontier.v3.api;

import java.util.Objects;

/**
 * Immutable canonical facts visible to an owning-thread adapter at one exact revision.
 *
 * <p>This is deliberately not a mutation API. Implementations must expose only immutable
 * state, and every attempted change still enters the engine through a typed command. It avoids
 * turning an ordinary physical-world read into a complete persistence snapshot encode/decode.</p>
 */
public record FrontierCanonicalState<S>(WorldId worldId, Revision revision, SimInstant instant, S state) {
    public FrontierCanonicalState {
        Objects.requireNonNull(worldId, "world id");
        Objects.requireNonNull(revision, "revision");
        Objects.requireNonNull(instant, "instant");
        Objects.requireNonNull(state, "state");
    }
}
