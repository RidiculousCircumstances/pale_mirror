package io.farfrontier.palemirror.frontier.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ReferenceCanonicalStateV2Test {
    private static final Map<Integer, String> CONFORMANCE = Map.of(
            0, "714d1cea16959535e7f27698bc98f63d7930a1eb2199fdd975e0533b52d5e593",
            1, "9d827ff1421c86587e3c427f0a2a4ab8143fb361f93cafe6bb23808eab6adcac",
            2, "4173d5637b5bf6de28f8600068987568813e0d558a7edebb7385159ae57a0e1e",
            3, "1363a49764bc8397ac6f1ce8b389847d950d672981473c5881d9aff8414c7742",
            5, "8af5b0aef274db1aabb96061e643bba4f57573c30157389a49b9fd07c6af256f",
            10, "8c5b3f55e0d656f6c593403b1f538e43cc629f0ef5c1f283292a1aaf6d1eb8fc",
            15, "536cb8e3138e606008350e7045e536b4a98b69f9aab412a1aceadeeacfeef5e4",
            20, "7b5ab98a7ad279eff95acf59f4b3bf9c08ebddd6a67b93a72191c9a604bc5083",
            25, "098a05a5b4ae101c73797d877af41bc7644a9b84d94e6f236553db432e3eed1f",
            30, "c95e7a4a1c160970c9d1917d2e35a9a21c9a5aea2e2d9c50335ae01969748fe5");

    @Test
    void completeV2OwnerMatchesPythonWithBoundedBinary64Tolerance() {
        ReferenceWorld world = new ReferenceWorld(ReferenceWorldConfig.sourceV2());

        for (int day = 0; day <= 30; day++) {
            String expected = CONFORMANCE.get(day);
            if (expected != null) assertEquals(expected, ReferenceCanonicalStateConformance.sha256(ReferenceCanonicalStateV2.capture(world)),
                    "V2 owner conformance day " + day);
            if (day < 30) world.tick();
        }
    }

    @Test
    void completeV2OwnerFailsClosedWhenV2IsDisabled() {
        ReferenceWorld legacy = new ReferenceWorld(new ReferenceWorldConfig(
                64, 44, 12, 42L, 2, false, ReferenceSimulationProfile.SOURCE_V2));

        assertEquals("canonical state requires V2-enabled reference world",
                assertThrows(IllegalStateException.class, () -> ReferenceCanonicalStateV2.capture(legacy)).getMessage());
    }
}
