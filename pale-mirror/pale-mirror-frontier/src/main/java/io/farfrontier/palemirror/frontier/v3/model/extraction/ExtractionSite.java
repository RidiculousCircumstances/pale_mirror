package io.farfrontier.palemirror.frontier.v3.model.extraction;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** Operating land/work-front declaration. Storage belongs to independently selected inventory endpoints. */
public record ExtractionSite(SubjectId id, SubjectId settlementId, ExtractionLayout layout, Set<SubjectId> mineralDeposits) {
    public ExtractionSite(SubjectId id, SubjectId settlementId, ExtractionLayout layout) { this(id, settlementId, layout, Set.of()); }
    public ExtractionSite {
        Objects.requireNonNull(id); Objects.requireNonNull(settlementId);
        Objects.requireNonNull(layout);
        mineralDeposits = Set.copyOf(mineralDeposits);
        if (mineralDeposits.size() > io.farfrontier.palemirror.frontier.v3.model.geology.MineralGeology.MAX_DEPOSITS)
            throw new IllegalArgumentException("too many geological relationships for one worksite");
        if (id.equals(settlementId))
            throw new IllegalArgumentException("extraction identities must be distinct");
    }
}
