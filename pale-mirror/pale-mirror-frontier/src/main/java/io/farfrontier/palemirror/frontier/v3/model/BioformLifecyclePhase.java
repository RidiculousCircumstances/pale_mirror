package io.farfrontier.palemirror.frontier.v3.model;

/**
 * Canonical physiological custody phase of one exact bioform.
 *
 * <p>This is deliberately not a health or body-position record. {@link ActorLocation}
 * remains the sole authority for vitality and exact body position, while this phase says
 * whether that identity is physically retained by an owned cocoon or may be represented by
 * one ambient/scene body.</p>
 */
public enum BioformLifecyclePhase {
    DORMANT,
    WAKING,
    ASSEMBLING,
    ACTIVE,
    RETURNING,
    RECOVERING;

    public int wireTag() { return FrontierWireTags.tag(this); }

    public boolean occupiesCocoon() { return this == DORMANT || this == RECOVERING; }

    public boolean permitsAmbientBody() {
        return this == WAKING || this == ASSEMBLING || this == ACTIVE || this == RETURNING;
    }
}
