package io.farfrontier.palemirror.frontier.v3.model;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;

class ChunkSurfaceIndexTest {
    @Test void sparseNegativeChunksAndHighestSupportAgreeWithColumnIdentity() {
        var surfaces = new ArrayList<SurfaceAnchor>();
        for (int x = -33; x <= 33; x++) for (int z = -33; z <= 33; z++)
            surfaces.add(SurfaceAnchor.at(x, x - z, z));
        surfaces.add(SurfaceAnchor.at(-17, 100, -16));
        var index = ChunkSurfaceIndex.of(surfaces);
        surfaces.clear();
        for (int x = -33; x <= 33; x++) for (int z = -33; z <= 33; z++)
            assertEquals(SurfaceAnchor.at(x, x == -17 && z == -16 ? 100 : x - z, z), index.at(x, z));
        assertNull(index.at(34, 34));
        assertNull(index.at(2048, -2048));
        assertEquals(java.util.List.of(SurfaceAnchor.at(-17, -1, -16), SurfaceAnchor.at(-17, 100, -16)),
                index.surfacesAt(-17, -16), "a preference for the upper floor cannot discard the lower floor");
        assertThrows(UnsupportedOperationException.class, () -> index.surfacesAt(-17, -16).clear());
    }
}
