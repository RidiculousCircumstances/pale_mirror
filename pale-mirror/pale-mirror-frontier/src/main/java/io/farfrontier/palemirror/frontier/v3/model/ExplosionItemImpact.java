package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Terminal physical outcome for one exact item that an explosion observed in a loaded surface. */
public record ExplosionItemImpact(SubjectId itemId, Outcome outcome) {
    public enum Outcome {
        RETAINED, TRANSFERRED, DESTROYED, CONFLICT;

        public int wireTag() { return FrontierWireTags.tag(this); }
    }

    public ExplosionItemImpact {
        Objects.requireNonNull(itemId, "item id");
        Objects.requireNonNull(outcome, "explosion item outcome");
    }
}
