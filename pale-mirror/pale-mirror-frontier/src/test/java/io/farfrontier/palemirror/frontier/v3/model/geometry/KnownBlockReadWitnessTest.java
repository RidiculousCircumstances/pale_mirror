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
    @Test void readLimitDisablesReuseWithoutTruncatingTheUnderlyingQuery() {
        KnownBlockGeometry<String> geometry = p -> Optional.of(new KnownBlockGeometry.Sample<>("block", 1,
                KnownBlockGeometry.Sample.Permission.PUBLIC));
        var reads = new KnownBlockReadWitness.Recorder<>(geometry, 1);
        assertTrue(reads.at(new BlockPosition(0, 0, 0)).isPresent());
        assertTrue(reads.at(new BlockPosition(1, 0, 0)).isPresent());
        assertTrue(reads.completed().isEmpty(), "an incomplete witness cannot authorize derived reuse");
    }
}
