package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import java.util.Objects;

/** The extraction owner settles a retained effect, never grants a dead actor new work. */
public record ExtractionRetiredEffectObserved(ExtractionHotObserved effect, boolean applied) implements FrontierPayload {
    public ExtractionRetiredEffectObserved {
        Objects.requireNonNull(effect);
        if (!applied && (!effect.remainingSource().isEmpty() || !effect.destination().isEmpty() || effect.externalSuccessor().isPresent()))
            throw new IllegalArgumentException("an unused extraction operation cannot declare physical output");
    }
    @Override public String type() { return "frontier.extraction_retired_effect_observed"; }
    @Override public boolean requiresDurableBeforeEffect() { return true; }
}
