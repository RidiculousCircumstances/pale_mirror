package io.farfrontier.palemirror.frontier.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ReferenceGrayboxCanonicalStateTest {
    private static final Map<Integer, String> CONFORMANCE = Map.of(
            0, "f441b42c16303ad2037d0ec39501fd0e50fc4c2d211ae196e78ce39d430c4a63",
            1, "f460e1c91756096d647b75750b086e98ae59e0473412d189628b00e681937694",
            2, "7333445e96193c2ab4c834aa93da72742fe559eb267e14cc9ba142fac0c1db1f",
            3, "d502e6718a6e1974f78d465c6881f43048a1ddfb76da68cdc0ff9557bd908bf6",
            5, "67935a48498c55659c150e07044405d865c82a4d9fae3180001fb63f2a1eabbd",
            10, "a8664e8e2345c9a4cdcfe0a6ffff2232310960b38b78f05087e855449c2861cb",
            15, "9647bb8b136c32242e3995814f9e00204c239251ef7b0aa126f69935c26f0e9b",
            20, "a21113d787299ba76ef7e8550322b6b153de3521d2b24816d7872c0b14b81700",
            25, "a88224dae8ae7c0674ea81b6558ec6a81a2fbc027349bfcbe6092ae4423a1428",
            30, "b9f686f5a8081d28ab630596a6c70b5fd10e8a68dbad504eb04fefc0cec49243");

    @Test
    void stateMatchesTheGrayboxPythonReferenceAtEveryPinnedCheckpoint() {
        ReferenceWorld world = new ReferenceWorld(ReferenceWorldConfig.graybox1To40(42L));

        for (int day = 0; day <= 30; day++) {
            String expected = CONFORMANCE.get(day);
            if (expected != null) {
                assertEquals(expected, ReferenceCanonicalStateConformance.sha256(ReferenceGrayboxCanonicalState.capture(world)),
                        "graybox canonical state conformance day " + day);
            }
            if (day < 30) world.tick();
        }
    }

    @Test
    void identityExtensionRetainsOnlyTheSurvivingNamedBioforms() {
        ReferenceWorld world = new ReferenceWorld(ReferenceWorldConfig.graybox1To40(7L));
        ReferenceSwarm swarm = new ReferenceSwarm(901, 12.0d, 13.0d, 90.0d, -1, .9d,
                ReferenceBioformKind.RAIDER, Map.of(ReferenceBioformKind.RAIDER, 2.0d, ReferenceBioformKind.BREAKER, 1.0d),
                ReferenceFormationPhase.SCREEN, 1.0d, null, null, null, false);
        world.infection().swarms.add(swarm);
        String killed = "bioform:901:raider:1";
        Map<String, Object> before = ReferenceGrayboxCanonicalState.capture(world);
        assertEquals("865c5490ace96dab5a14ac03e66a0a09cbc5ec02afc243e42d13f3b85a1fa05c",
                ReferenceCanonicalStateConformance.sha256(before));
        assertEquals("0f4b82de17eb945626ca21f346c995e5744effb7472848c3f58b605c96c3daaa",
                ReferenceV2PublicSnapshot.sha256(before.get("bioform_identities")));
        assertTrue(swarm.killExactBioform(killed));

        Map<String, Object> after = ReferenceGrayboxCanonicalState.capture(world);
        assertEquals("ce6767e0390220a3a6405a8d0407dfc593bd8683ddf75c2e4c8a9c1af9f86f92",
                ReferenceCanonicalStateConformance.sha256(after));
        assertEquals("b2cafd8a2e4f07babf8c0ec7478fe4fbff033f8c2d7d3aa7fad206f2dc05327f",
                ReferenceV2PublicSnapshot.sha256(after.get("bioform_identities")));
        String state = ReferenceV2PublicSnapshot.canonicalJson(after);
        assertFalse(state.contains(killed));
        assertTrue(state.contains("bioform:901:raider:2"));
        assertTrue(state.contains("bioform:901:breaker:1"));
        world.assertProfileInvariants();
    }

    @Test
    void sourceProfileCannotBeSavedAsAGrayboxSnapshot() {
        ReferenceWorld source = new ReferenceWorld(ReferenceWorldConfig.sourceV2());

        assertEquals("graybox materialization requires the complete graybox_1_40 V2 source profile",
                assertThrows(IllegalStateException.class, () -> ReferenceGrayboxCanonicalState.capture(source)).getMessage());
    }
}
