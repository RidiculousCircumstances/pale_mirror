package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import java.util.*;

/** Physical ingress revokes baseline knowledge BEFORE an unowned native mutation can occur. */
public record ExtractionGeologyInvalidated(List<Observation> observations) implements FrontierPayload {
    public static final SubjectId OWNER = new SubjectId("geometry:extraction-geology");
    public static final int MAX_OBSERVATIONS = 256;
    public record Observation(BlockPosition position, long stratumRevision) {
        public Observation {
            Objects.requireNonNull(position);
            if (stratumRevision < 1) throw new IllegalArgumentException("geological observation lacks its baseline version");
        }
    }
    public ExtractionGeologyInvalidated {
        observations = List.copyOf(observations);
        if (observations.isEmpty() || observations.size() > MAX_OBSERVATIONS)
            throw new IllegalArgumentException("geological observation batch exceeds its bounded declaration");
        var positions = new HashSet<BlockPosition>();
        for (var observation : observations) if (!positions.add(observation.position()))
            throw new IllegalArgumentException("duplicate geological observation");
    }
    public ExtractionGeologyInvalidated(BlockPosition position, long stratumRevision) {
        this(List.of(new Observation(position, stratumRevision)));
    }
    @Override public String type() { return "frontier.extraction_geology_invalidated.v2"; }
    @Override public boolean requiresDurableBeforeEffect() { return true; }
    public FrontierWorldState apply(FrontierWorldState state, SubjectId subject) {
        if (!subject.equals(OWNER)) throw new IllegalArgumentException("foreign geological observation owner");
        var positions = observations.stream().map(Observation::position).collect(java.util.stream.Collectors.toSet());
        for (var observation : observations) {
            var fact = ExtractionTerrainFacts.at(state.bootstrap(), observation.position()).orElseThrow(
                    () -> new IllegalArgumentException("no declared geological knowledge"));
            if (fact.revision() != observation.stratumRevision())
                throw new IllegalArgumentException("stale geological observation");
        }
        var ownership = io.farfrontier.palemirror.frontier.v3.model.extraction.ExtractionGeometryIndex.forDeposits(state.extractionSites().deposits());
        if (positions.stream().anyMatch(ownership::owns))
            throw new IllegalArgumentException("geology already has a worksite owner");
        return state.withChanges(FrontierWorldStateUpdate.begin().extractionSites(state.extractionSites().invalidateGeology(positions)));
    }
}
