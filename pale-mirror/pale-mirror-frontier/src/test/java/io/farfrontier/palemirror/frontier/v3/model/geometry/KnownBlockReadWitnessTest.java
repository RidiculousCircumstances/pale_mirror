package io.farfrontier.palemirror.frontier.v3.model.geometry;

import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KnownBlockReadWitnessTest {
    @Test void unknownAndPermissionChangesInvalidateFactsButObservationMetadataAloneDoesNot() {
        var a = new BlockPosition(1, 2, 3); var b = a.offset(1, 0, 0);
        var values = new HashMap<BlockPosition, KnownBlockGeometry.Sample<String>>();
        values.put(a, new KnownBlockGeometry.Sample<>("soil", 1, KnownBlockGeometry.Sample.Permission.PUBLIC));
        KnownBlockGeometry<String> geometry = p -> Optional.ofNullable(values.get(p));
        var reads = new KnownBlockReadWitness.Recorder<>(geometry, 3);
        reads.at(a); reads.at(b);
        var witness = reads.completed().orElseThrow();
        values.put(a, new KnownBlockGeometry.Sample<>("soil", 2, KnownBlockGeometry.Sample.Permission.PUBLIC));
        assertTrue(witness.matches(geometry), "a derived selection may retain facts while admission refreshes revisions");
        values.put(b, new KnownBlockGeometry.Sample<>("air", 2, KnownBlockGeometry.Sample.Permission.PUBLIC));
        assertFalse(witness.matches(geometry), "unknown becoming known may expose a previously absent candidate");
        values.remove(b);
        values.put(a, new KnownBlockGeometry.Sample<>("soil", 2, KnownBlockGeometry.Sample.Permission.PROTECTED));
        assertFalse(witness.matches(geometry));
    }
    @Test void completeRegionalVersionReusesReadsButLocalUnknownOrPermissionChangeInvalidates() {
        var position = new BlockPosition(1, 2, 3);
        var region = io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianGeometryRevision.Region.at(position);
        Object epoch = new Object();
        class View implements KnownBlockGeometry<String> {
            final Object local; final String value; int reads;
            View(Object local, String value) { this.local = local; this.value = value; }
            @Override public Optional<Sample<String>> at(BlockPosition p) {
                reads++; return Optional.of(new Sample<>(value, 1, Sample.Permission.PUBLIC));
            }
            @Override public Optional<Object> regionalReadVersion(Set<io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianGeometryRevision.Region> regions) {
                assertEquals(Set.of(region), regions);
                return Optional.of(new RegionalReadVersion(List.of(epoch), Map.of(region, List.of(local))));
            }
        }
        var initial = new View(new Object(), "soil");
        var recorder = new KnownBlockReadWitness.Recorder<String>(initial, 4); recorder.at(position);
        var witness = recorder.completed().orElseThrow();
        var unchanged = new View(initial.local, "soil");
        assertTrue(witness.matches(unchanged)); assertEquals(0, unchanged.reads);
        var changed = new View(new Object(), "unknown");
        assertFalse(witness.matches(changed), "a changed complete dependency never reuses a compact witness");
        assertTrue(witness.matches(initial), "independent immutable snapshots do not mutate the original proof");
    }

    @Test void readLimitDisablesReuseWithoutTruncatingTheUnderlyingQuery() {
        KnownBlockGeometry<String> geometry = p -> Optional.of(new KnownBlockGeometry.Sample<>("block", 1,
                KnownBlockGeometry.Sample.Permission.PUBLIC));
        var reads = new KnownBlockReadWitness.Recorder<>(geometry, 1);
        assertTrue(reads.at(new BlockPosition(0, 0, 0)).isPresent());
        assertTrue(reads.at(new BlockPosition(1, 0, 0)).isPresent());
        assertTrue(reads.completed().isEmpty(), "an incomplete witness cannot authorize derived reuse");
    }
}
