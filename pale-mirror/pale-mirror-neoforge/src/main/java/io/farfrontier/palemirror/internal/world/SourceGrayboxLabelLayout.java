package io.farfrontier.palemirror.internal.world;

import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxSnapshot;
import java.util.Comparator;
import java.util.List;

/** Bounded, deterministic selection of sector nameplates for the visual adapter. */
final class SourceGrayboxLabelLayout {
    private static final int MAX_SECTOR_LABELS = 24;

    private SourceGrayboxLabelLayout() { }

    /**
     * A source day can contain every one of 176 V2 sectors. Their geometry and
     * four metric towers are always projected; only operationally important
     * sectors receive a floating nameplate so the map remains readable.
     */
    static List<ReferenceGrayboxSnapshot.Sector> labelledSectors(ReferenceGrayboxSnapshot snapshot) {
        return snapshot.sectors().stream().filter(sector -> sector.supplied() || sector.control().equals("human")
                        || sector.control().equals("hive"))
                .sorted(Comparator.comparingInt((ReferenceGrayboxSnapshot.Sector sector) -> sector.supplied() ? 0
                                : sector.control().equals("human") ? 1 : 2)
                        .thenComparing(Comparator.comparingDouble((ReferenceGrayboxSnapshot.Sector sector) -> sector.infection()
                                + sector.hiveInfluence()).reversed())
                        .thenComparing(ReferenceGrayboxSnapshot.Sector::key))
                .limit(MAX_SECTOR_LABELS).toList();
    }
}
