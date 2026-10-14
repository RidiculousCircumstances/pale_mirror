package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianGeometryRevision;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KnownPedestrianGeometryScopesTest {
    @Test void evictedUnchangedViewRetainsKnowledgeButActualLocalChangesInvalidateIt() {
        var bootstrap = new Object();
        var wall = new BlockPosition(2, 64, 2);
        var column = new TerrainColumn(20, 2);
        var supports = Map.of(column, List.of(SurfaceAnchor.at(20, 63, 2)));
        KnownPedestrianGeometryScopes.refresh(bootstrap, Set.of(wall), supports);
        var before = KnownPedestrianGeometryScopes.revision("test:retained", Set.of(wall), supports);
        var otherTask = KnownPedestrianGeometryScopes.revision("test:other-task", Set.of(), supports);
        assertNotEquals(before.scope(), otherTask.scope());
        assertEquals(before.geometryScope(), otherTask.geometryScope());
        assertNotEquals(before.at(0, 0), otherTask.at(0, 0), "passage restrictions stay in the local geometry proof");
        assertEquals(before.at(1, 0), otherTask.at(1, 0), "identical distant terrain can be shared across task policies");
        for (int index = 0; index < 300; index++)
            KnownPedestrianGeometryScopes.revision("test:eviction-" + index, Set.of(), Map.of());
        var reconstructed = KnownPedestrianGeometryScopes.revision("test:retained", Set.of(wall), supports);
        assertEquals(before.scope(), reconstructed.scope());
        assertSame(before.baseline(), reconstructed.baseline());
        assertTrue(reconstructed.changesSince(before).isEmpty());
        assertEquals(before.at(0, 0), reconstructed.at(0, 0));
        assertEquals(before.at(1, 0), reconstructed.at(1, 0));

        var changedSupports = Map.of(column, List.of(SurfaceAnchor.at(20, 62, 2)));
        KnownPedestrianGeometryScopes.refresh(bootstrap, Set.of(wall), changedSupports);
        var changed = KnownPedestrianGeometryScopes.revision("test:retained", Set.of(wall), changedSupports);
        assertEquals(Set.of(new PedestrianGeometryRevision.Region(1, 0)), changed.changesSince(reconstructed));
        assertEquals(before.at(0, 0), changed.at(0, 0));
        assertNotEquals(before.at(1, 0), changed.at(1, 0));
        var removedWall = KnownPedestrianGeometryScopes.revision("test:retained", Set.of(), changedSupports);
        assertEquals(Set.of(new PedestrianGeometryRevision.Region(0, 0)), removedWall.changesSince(changed));
        KnownPedestrianGeometryScopes.refresh(new Object(), Set.of(), Map.of());
        assertNotEquals(before.scope(), KnownPedestrianGeometryScopes.revision("test:retained", Set.of(), Map.of()).scope());
        assertNotEquals(before.geometryScope(), KnownPedestrianGeometryScopes.revision("test:retained", Set.of(), Map.of()).geometryScope());
    }
}
