package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import java.util.Objects;

/** Physical ingress revokes baseline knowledge BEFORE an unowned native mutation can occur. */
public record ExtractionGeologyInvalidated(BlockPosition position, long stratumRevision) implements FrontierPayload {
    public static final SubjectId OWNER = new SubjectId("geometry:extraction-geology");
    public ExtractionGeologyInvalidated {
        Objects.requireNonNull(position);
        if (stratumRevision < 1) throw new IllegalArgumentException("geological observation lacks its baseline version");
    }
    @Override public String type() { return "frontier.extraction_geology_invalidated"; }
    public FrontierWorldState apply(FrontierWorldState state, SubjectId subject) {
        var geology = state.bootstrap().ruleset().extraction().geology().orElseThrow(
                () -> new IllegalArgumentException("no declared geological knowledge"));
        if (!subject.equals(OWNER) || geology.revision() != stratumRevision
                || geology.at(state.bootstrap().bounds(), position).isEmpty()
                || state.extractionSites().deposits().values().stream().anyMatch(deposit ->
                    deposit.site().layout().fixedBlocks().containsKey(position)
                    || deposit.site().layout().cells().stream().anyMatch(cell -> cell.source().equals(position))))
            throw new IllegalArgumentException("foreign/stale observation or geology already has a worksite owner");
        return state.withChanges(FrontierWorldStateUpdate.begin().extractionSites(state.extractionSites().invalidateGeology(position)));
    }
}
