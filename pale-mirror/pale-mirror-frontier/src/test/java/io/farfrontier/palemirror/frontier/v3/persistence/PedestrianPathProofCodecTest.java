package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import io.farfrontier.palemirror.frontier.v3.model.expedition.ExpeditionJourneyProof;
import io.farfrontier.palemirror.frontier.v3.model.navigation.*;
import java.io.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PedestrianPathProofCodecTest {
    @Test void longItineraryRetainsEveryStepAndExactForecastWithoutRaisingMovementLegLimit() throws Exception {
        var route = new ArrayList<SurfaceAnchor>();
        for (int x = 0; x <= 24000; x++) route.add(SurfaceAnchor.at(x, 63 + Math.min(x, 2), 0));
        var proof = PedestrianPathProof.from(route);
        assertEquals(2, proof.runs().size()); assertEquals(24000, proof.edges());
        assertEquals(route, proof.surfaces());
        assertEquals(route.getLast(), proof.destination());
        assertThrows(UnsupportedOperationException.class, () -> proof.surfaces().set(1, route.getFirst()));
        assertThrows(IllegalArgumentException.class, () -> new PedestrianRouteReceipt(route));
        var journey = new ExpeditionJourneyProof(proof, PedestrianPathProof.from(route.reversed()));
        assertEquals(journey.outward().edges(), journey.returning().edges());
        var bytes = new ByteArrayOutputStream();
        PedestrianPathProofCodec.write(new DataOutputStream(bytes), proof);
        assertTrue(bytes.size() < 64, "straight distance must not persist one coordinate per cell");
        assertEquals(proof, PedestrianPathProofCodec.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))));
    }
    @Test void malformedOrOversizedRunsCannotBeAcceptedAsConnectedGeometry() {
        var origin = SurfaceAnchor.at(0, 63, 0);
        assertThrows(IllegalArgumentException.class, () -> new PedestrianPathProof.Run(1, 0, 1, 2));
        // Serialized anchors name support blocks, not integer feet heights.
        // Fractional/protruding shapes can connect more distant support Y values;
        // current profile geometry, not this codec, grants traversal permission.
        assertEquals(2, new PedestrianPathProof.Run(1, 2, 0, 2).dy());
        assertThrows(IllegalArgumentException.class, () -> new PedestrianPathProof.Run(1, 7, 0, 2));
        assertThrows(IllegalArgumentException.class, () -> new PedestrianPathProof(origin, List.of(
                new PedestrianPathProof.Run(1, 0, 0, 40000), new PedestrianPathProof.Run(0, 0, 1, 40000))));
        assertThrows(IllegalArgumentException.class, () -> new PedestrianPathProof(origin, List.of(
                new PedestrianPathProof.Run(1, 0, 0, 2), new PedestrianPathProof.Run(1, 0, 0, 2))));
        assertThrows(IllegalArgumentException.class, () -> PedestrianPathProof.from(List.of(origin, SurfaceAnchor.at(2, 63, 0))));
        var singleton = PedestrianPathProof.from(List.of(origin));
        assertEquals(List.of(origin), singleton.surfaces()); assertEquals(0, singleton.edges());
    }
}
