package io.farfrontier.palemirror.frontier.v3.model.geometry;

import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class LocalBlockExclusionsTest {
    @Test void remoteEditsRetainLocalViewButLocalEditsBranchingAndExpandedQueriesCannotReuseStaleFacts() {
        var local = new BlockPosition(1, 64, 1);
        var remote = new BlockPosition(65, 64, 1);
        var footprint = Set.of(local);
        var view = new LocalBlockExclusions();
        var before = KnownBlockExclusions.empty();
        var original = view.read(footprint, before);
        var changedRemote = before.invalidate(remote);
        assertSame(original, view.read(footprint, changedRemote));
        var changedLocal = changedRemote.invalidate(local);
        assertEquals(Set.of(local), view.read(footprint, changedLocal));
        assertEquals(Set.of(local, remote), view.read(Set.of(local, remote), changedLocal));
        assertEquals(Set.of(), view.read(footprint, before), "a retained earlier branch is not an append");
        assertEquals(Set.of(local), view.read(footprint, new KnownBlockExclusions(changedLocal.positions())),
                "recovered values need no parent cache or revision guess");
        assertEquals(Set.of(), original, "published snapshots remain immutable");
    }
}
