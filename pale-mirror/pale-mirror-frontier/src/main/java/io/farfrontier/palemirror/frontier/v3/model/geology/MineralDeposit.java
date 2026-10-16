package io.farfrontier.palemirror.frontier.v3.model.geology;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.extraction.BlockExtraction;
import io.farfrontier.palemirror.frontier.v3.model.geometry.BlockVolume;
import java.util.*;

/** Initial natural distribution only. No settlement, container, task or remaining-stock authority. */
public record MineralDeposit(SubjectId id, long revision, List<Section> sections) {
    public static final int MAX_SECTIONS = 64;
    public record Section(BlockVolume volume, BlockExtraction.Block material) {
        public Section { Objects.requireNonNull(volume); Objects.requireNonNull(material); }
    }
    public MineralDeposit {
        Objects.requireNonNull(id); sections = List.copyOf(sections);
        if (revision < 1 || sections.isEmpty() || sections.size() > MAX_SECTIONS)
            throw new IllegalArgumentException("invalid geological declaration");
        for (int a = 0; a < sections.size(); a++) for (int b = 0; b < a; b++)
            if (sections.get(a).volume().overlaps(sections.get(b).volume()))
                throw new IllegalArgumentException("overlapping geological sections");
    }
}
