package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.extraction.BlockExtraction;
import java.util.Objects;

/** The infrastructure owner retains physical geography without awarding mining or stock. */
public record ExtractionGeometryChanged(WorksiteBlock predecessor, BlockExtraction.Block actual) implements FrontierPayload {
    public ExtractionGeometryChanged {
        Objects.requireNonNull(predecessor); Objects.requireNonNull(actual);
        if (predecessor.key().family() != CellMutationKey.OwnerFamily.EXTRACTIVE_SITE
                || predecessor.key().role() == WorksiteBlock.Role.RESOURCE)
            throw new IllegalArgumentException("geometry observation has a foreign source role");
    }
    public FrontierWorldState apply(FrontierWorldState state, SubjectId subject) {
        var deposit = state.extractionSites().deposits().get(subject);
        if (deposit == null || !subject.equals(predecessor.key().owner())) throw new IllegalArgumentException("geometry change names a foreign worksite");
        var declaration = ExtractionWorksiteBlocks.declared(deposit).stream().filter(cell -> cell.key().equals(predecessor.key())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("geometry observation lacks its exact authored key"));
        if (!ExtractionWorksiteBlocks.current(deposit, declaration).equals(predecessor)) throw new IllegalArgumentException("stale/forged worksite geometry observation");
        var changed = state.withChanges(FrontierWorldStateUpdate.begin().extractionSites(state.extractionSites().replace(
                deposit.observeGeometry(predecessor.position(), predecessor.revision(), actual))));
        return ExtractionWorkTargets.reconcile(changed, subject);
    }
    @Override public String type() { return "frontier.extraction_geometry_changed"; }
    @Override public boolean requiresDurableBeforeEffect() { return true; }
}
