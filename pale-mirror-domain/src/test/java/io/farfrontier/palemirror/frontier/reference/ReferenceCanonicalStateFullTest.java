package io.farfrontier.palemirror.frontier.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ReferenceCanonicalStateFullTest {
    private static final Map<Integer, String> CONFORMANCE = Map.of(
            0, "d61daf2389b5122b99b0f3bb5ca7e2c1999e23a336a63814afea6fa0ba57981d",
            1, "b2b4acb9dc3e048c407584b429f3b3be24a076538020a29324906bbdddf2479b",
            2, "94489b8cf976cde312d167cae7afd64b06fac8dd0a657ad5928a6e8026321cb7",
            3, "6b89e30bcfc8694793eb22e0ffd34d4deabf57626b717409f40892c435253ae6",
            5, "9c5e9a7327b4c2f07a8fc8c19b1ba7b43c3e6dbb43f2e010088e54bda7108665",
            10, "cbf58039f9a3b727ddeeca84d0e4fbbe48a5aea0308922398169f8874c3a58ac",
            15, "b7f9fa92af4b7914bffc76dd42f24d6fcff76c7640ecbf1301c39f528092c8ab",
            20, "23e2049d6674e9af6627b3e9a09d816415d8c9444a3500858554bb55697cee80",
            25, "f529d65476c9f58a066bf122e658e037edf5648cc1a9a1e05eb43edcee3ae6ef",
            30, "cc7345941f42eeb4da35b51e3051ced65ac02d6d1ab2476c0414733654f6bb3c");

    @Test
    void fullCanonicalStateMatchesThePythonReferenceAtEveryPinnedCheckpoint() {
        ReferenceWorld world = new ReferenceWorld(ReferenceWorldConfig.sourceV2());

        for (int day = 0; day <= 30; day++) {
            String expected = CONFORMANCE.get(day);
            if (expected != null) assertEquals(expected, ReferenceCanonicalStateConformance.sha256(ReferenceCanonicalState.capture(world)),
                    "full canonical state conformance day " + day);
            if (day < 30) world.tick();
        }
    }

    @Test
    void fullCanonicalStateFailsClosedForDisabledV2() {
        ReferenceWorld legacy = new ReferenceWorld(new ReferenceWorldConfig(
                64, 44, 12, 42L, 2, false, ReferenceSimulationProfile.SOURCE_V2));

        assertEquals("canonical state requires V2-enabled reference world",
                assertThrows(IllegalStateException.class, () -> ReferenceCanonicalState.capture(legacy)).getMessage());
    }
}
